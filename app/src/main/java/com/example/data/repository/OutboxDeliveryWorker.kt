package com.example.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.model.OutboxKind
import com.example.data.model.RemoteMutationOutboxItem
import com.example.data.model.RollCallOutboxItem
import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoApiException
import java.io.IOException
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class OutboxDeliveryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID)
            ?: return Result.failure()
        val kind = OutboxKind.fromValue(inputData.getString(KEY_KIND))
            ?: return Result.failure()
        val expectedOwnerId = inputData.getString(KEY_OWNER_ID)
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val expectedTenantUrl = inputData.getString(KEY_TENANT_URL)
            ?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val api = DjangoApiClient(applicationContext)
        if (!api.hasSession || !api.tenantConfigured || api.baseUrl != expectedTenantUrl) {
            return Result.failure()
        }

        val cachedSession = try {
            api.cachedSessionForCurrentTenant()
        } catch (_: IllegalStateException) {
            null
        } ?: return Result.failure()
        val ownerId = cachedSession.optJSONObject("user")
            ?.optString("id")
            ?.takeIf(String::isNotBlank)
            ?.takeIf { it == expectedOwnerId }
            ?: return Result.failure()

        val database = AppDatabase.getDatabase(applicationContext)
        val remoteDao = database.remoteMutationOutboxDao()
        val rollCallDao = database.rollCallOutboxDao()
        val remoteItem = if (kind == OutboxKind.REMOTE_MUTATION) {
            remoteDao.getById(itemId, ownerId, api.baseUrl)
        } else {
            null
        }
        val rollCallItem = if (kind == OutboxKind.ROLL_CALL) {
            rollCallDao.getById(itemId, ownerId, api.baseUrl)
        } else {
            null
        }
        if (remoteItem == null && rollCallItem == null) return Result.failure()
        if (remoteItem?.deliveryStatus != null && remoteItem.deliveryStatus != PENDING) {
            return Result.success()
        }
        if (rollCallItem?.deliveryStatus != null && rollCallItem.deliveryStatus != PENDING) {
            return Result.success()
        }

        return try {
            val me = api.get("auth/me/")
            val user = me.optJSONObject("user")
            val capabilities = user?.optJSONObject("capabilities")
            val currentOwnerId = user?.optString("id").orEmpty()
            if (
                currentOwnerId != ownerId ||
                user?.optBoolean("is_active") != true ||
                capabilities == null
            ) {
                api.restrictOfflineStartup("outbox_session_invalid")
                markFailed(
                    database,
                    remoteItem,
                    rollCallItem,
                    "The server no longer validates this account. Sign in again before retrying."
                )
                return Result.failure()
            }
            val features = me.optJSONObject("features") ?: JSONObject()
            api.recordSuccessfulValidation(me)
            if (remoteItem != null) {
                if (!hasPermission(remoteItem.path, capabilities, features)) {
                    markFailed(
                        database,
                        remoteItem,
                        null,
                        "The server account no longer has permission for this action."
                    )
                    return Result.failure()
                }
                api.post(
                    remoteItem.path,
                    JSONObject(remoteItem.payloadJson),
                    idempotencyKey = remoteItem.id
                )
                remoteDao.update(
                    remoteItem.copy(
                        attempts = remoteItem.attempts + 1,
                        deliveryStatus = DELIVERED,
                        lastError = null
                    )
                )
                if (remoteItem.path == "gate/depart/" || remoteItem.path == "gate/return/") {
                    database.gateMovementDao().updateSyncState(remoteItem.id, "SYNCED", null)
                }
            } else if (rollCallItem != null) {
                if (!capabilities.optBoolean("roll_call") ||
                    !features.optBoolean("roll_call", true)
                ) {
                    markFailed(
                        database,
                        null,
                        rollCallItem,
                        "The server account no longer has roll-call permission."
                    )
                    return Result.failure()
                }
                deliverRollCall(api, rollCallDao, rollCallItem)
            }
            Result.success()
        } catch (error: DjangoApiException) {
            val message = error.message ?: "The school server rejected this queued action."
            if (error.statusCode in 500..599 || error.statusCode in setOf(408, 429)) {
                recordTransientFailure(database, remoteItem, rollCallItem, message)
                Result.retry()
            } else {
                markFailed(database, remoteItem, rollCallItem, message)
                Result.failure()
            }
        } catch (error: IOException) {
            recordTransientFailure(
                database,
                remoteItem,
                rollCallItem,
                error.message ?: "Network unavailable."
            )
            Result.retry()
        } catch (error: IllegalStateException) {
            markFailed(
                database,
                remoteItem,
                rollCallItem,
                error.message ?: "The queued action could not be validated."
            )
            Result.failure()
        } catch (error: IllegalArgumentException) {
            markFailed(
                database,
                remoteItem,
                rollCallItem,
                error.message ?: "The queued action contains invalid data."
            )
            Result.failure()
        }
    }

    private suspend fun deliverRollCall(
        api: DjangoApiClient,
        dao: com.example.data.local.RollCallOutboxDao,
        action: RollCallOutboxItem
    ) {
        val serverType = when (action.sessionType) {
            "EVENING_ROLL_CALL" -> "Evening"
            "MORNING_INSPECTION" -> "Morning"
            else -> "Special"
        }
        var sessionId = action.remoteSessionId
        val entries: JSONArray
        if (sessionId == null) {
            val response = api.post(
                "roll-call/sessions/",
                JSONObject()
                    .put("house_id", action.houseId)
                    .put("dormitory_id", action.dormitoryId)
                    .put("session_type", serverType)
                    .put("inspection_date", action.inspectionDate),
                idempotencyKey = "${action.id}-start"
            )
            sessionId = response.optString("id").takeIf(String::isNotBlank)
                ?: throw IllegalStateException("The server did not create a roll-call session.")
            entries = response.optJSONArray("entries") ?: JSONArray()
            dao.update(action.copy(remoteSessionId = sessionId, lastError = null))
        } else {
            val detail = api.get("roll-call/sessions/$sessionId/")
            entries = detail.optJSONArray("entries") ?: JSONArray()
        }

        val statuses = JSONObject(action.statusesJson)
        val apiStatuses = mapOf(
            "PRESENT" to "Present",
            "ABSENT" to "Absent",
            "ON_EXEAT" to "On Exeat",
            "SICK_BAY" to "Infirmary",
            "ACTIVITY" to "Activity"
        )
        val remarks = if (action.remarksJson.isBlank()) {
            JSONObject()
        } else {
            JSONObject(action.remarksJson)
        }
        val entriesToSubmit = (0 until entries.length()).mapNotNull(entries::optJSONObject)
        require(entriesToSubmit.isNotEmpty()) {
            "The server returned no students for this roll-call session."
        }
        require(entriesToSubmit.none { entry ->
            statuses.optString(entry.optJSONObject("student")?.optString("id").orEmpty()).isBlank()
        }) {
            "The server roster has students without saved roll-call marks."
        }
        for (entry in entriesToSubmit) {
            val studentId = entry.optJSONObject("student")?.optString("id").orEmpty()
            val apiStatus = apiStatuses[statuses.optString(studentId)]
                ?: throw IllegalArgumentException("Unsupported attendance status.")
            val payload = JSONObject()
                .put("status", apiStatus)
            remarks.optString(studentId).takeIf(String::isNotBlank)?.let {
                payload.put("remarks", it)
            }
            api.post(
                "roll-call/sessions/$sessionId/entries/${entry.optString("id")}/",
                payload,
                idempotencyKey = UUID.nameUUIDFromBytes(
                    "${action.id}:${entry.optString("id")}".toByteArray()
                ).toString()
            )
        }
        api.post(
            "roll-call/sessions/$sessionId/complete/",
            idempotencyKey = "${action.id}-complete"
        )
        dao.update(
            action.copy(
                attempts = action.attempts + 1,
                deliveryStatus = DELIVERED,
                lastError = null
            )
        )
    }

    private suspend fun markFailed(
        database: AppDatabase,
        remote: RemoteMutationOutboxItem?,
        rollCall: RollCallOutboxItem?,
        message: String
    ) {
        val failedId = remote?.id ?: rollCall?.id
        remote?.let {
            database.remoteMutationOutboxDao().update(
                it.copy(attempts = it.attempts + 1, lastError = message, deliveryStatus = FAILED)
            )
            if (it.path == "gate/depart/" || it.path == "gate/return/") {
                database.gateMovementDao().updateSyncState(it.id, "REVIEW_REQUIRED", message)
            }
        }
        rollCall?.let {
            database.rollCallOutboxDao().update(
                it.copy(attempts = it.attempts + 1, lastError = message, deliveryStatus = FAILED)
            )
        }
        if (failedId != null) {
            SchoolNotificationCenter.show(
                applicationContext,
                NotificationPollingPayload(
                    id = "sync-$failedId",
                    title = "Queued action needs attention",
                    message = message,
                    kind = "sync_failure"
                )
            )
        }
    }

    private suspend fun recordTransientFailure(
        database: AppDatabase,
        remote: RemoteMutationOutboxItem?,
        rollCall: RollCallOutboxItem?,
        message: String
    ) {
        remote?.let {
            database.remoteMutationOutboxDao().update(
                it.copy(attempts = it.attempts + 1, lastError = message)
            )
        }
        rollCall?.let {
            database.rollCallOutboxDao().update(
                it.copy(attempts = it.attempts + 1, lastError = message)
            )
        }
    }

    private fun hasPermission(
        path: String,
        capabilities: JSONObject,
        features: JSONObject
    ): Boolean = when {
        path.startsWith("roll-call/") ->
            capabilities.optBoolean("roll_call") && features.optBoolean("roll_call", true)
        path.startsWith("gate/") ->
            capabilities.optBoolean("gate_operate") && features.optBoolean("exeat_gate", true)
        path.endsWith("/approve/") ->
            capabilities.optBoolean("exeats_approve") && features.optBoolean("exeat_gate", true)
        path.endsWith("/review/") ->
            (capabilities.optBoolean("is_admin") ||
                capabilities.optBoolean("is_senior_housemaster")) &&
                features.optBoolean("exeat_gate", true)
        path == "exeats/" ->
            capabilities.optBoolean("exeats_create") && features.optBoolean("exeat_gate", true)
        else -> true
    }

    companion object {
        const val KEY_ITEM_ID = "outbox_item_id"
        const val KEY_KIND = "outbox_kind"
        const val KEY_OWNER_ID = "outbox_owner_id"
        const val KEY_TENANT_URL = "outbox_tenant_url"
        private const val PENDING = "PENDING"
        private const val DELIVERED = "DELIVERED"
        private const val FAILED = "FAILED"
    }
}
