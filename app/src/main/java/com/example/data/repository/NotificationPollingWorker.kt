package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoApiException
import com.example.ui.viewmodel.AppScreen
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

class NotificationPollingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val expectedOwnerId = inputData.getString(KEY_OWNER_ID)
            ?.takeIf(String::isNotBlank) ?: return Result.failure()
        val expectedTenantUrl = inputData.getString(KEY_TENANT_URL)
            ?.takeIf(String::isNotBlank) ?: return Result.failure()
        val api = DjangoApiClient(applicationContext)
        if (!api.hasSession || !api.tenantConfigured || api.baseUrl != expectedTenantUrl) {
            return Result.success()
        }

        if (!belongsToCurrentSession(api, expectedOwnerId, expectedTenantUrl)) return Result.success()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        return try {
            val response = api.get(
                "notifications/",
                mapOf("page" to "1", "page_size" to "100")
            )
            if (!belongsToCurrentSession(api, expectedOwnerId, expectedTenantUrl)) {
                return Result.success()
            }
            val items = response.optJSONArray("items") ?: JSONArray()
            val current = (0 until items.length()).mapNotNull { index ->
                items.optJSONObject(index)?.let { NotificationPayload.from(it) }
            }.filter { it.id.isNotBlank() }
            val prefs = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val stateKey = stateKey(expectedTenantUrl, expectedOwnerId)
            val previousIds = prefs.getStringSet(stateKey, null)?.toSet()
            val newNotifications = if (previousIds == null) {
                emptyList()
            } else {
                current.filter { it.id !in previousIds && !it.isRead }
            }
            val pendingDigestKey = "${stateKey}_pending_digest"
            val pendingDigest = parsePendingDigest(prefs.getString(pendingDigestKey, null))
            val now = LocalTime.now()
            if (NotificationQuietHours.isQuiet(now)) {
                val combined = (pendingDigest + newNotifications)
                    .distinctBy(NotificationPayload::id)
                    .takeLast(MAX_PENDING_DIGEST_ITEMS)
                prefs.edit().putString(pendingDigestKey, JSONArray().apply {
                    combined.forEach { notification ->
                        put(
                            JSONObject()
                                .put("id", notification.id)
                                .put("title", notification.title)
                                .put("message", notification.message)
                                .put("kind", notification.kind)
                        )
                    }
                }.toString()).apply()
            } else {
                if (pendingDigest.isNotEmpty()) {
                    SchoolNotificationCenter.show(
                        applicationContext,
                        NotificationPollingPayload(
                            id = "daily-digest-${LocalDate.now()}",
                            title = "Your morning school digest",
                            message = buildString {
                                append("${pendingDigest.size} updates arrived during quiet hours.")
                                pendingDigest.take(MAX_DIGEST_PREVIEW_ITEMS).forEach { notification ->
                                    append("\n- ")
                                    append(notification.title)
                                    if (notification.message.isNotBlank()) {
                                        append(": ")
                                        append(notification.message)
                                    }
                                }
                            }.take(MAX_NOTIFICATION_TEXT_LENGTH),
                            kind = "general"
                        )
                    )
                    prefs.edit().remove(pendingDigestKey).apply()
                }
                newNotifications.forEach { notification ->
                    SchoolNotificationCenter.show(
                        applicationContext,
                        NotificationPollingPayload(
                            notification.id,
                            notification.title,
                            notification.message,
                            notification.kind
                        )
                    )
                }
            }
            prefs.edit()
                .putStringSet(stateKey, (current.map(NotificationPayload::id) + previousIds.orEmpty())
                    .take(300)
                    .toSet())
                .apply()
            Result.success()
        } catch (error: DjangoApiException) {
            if (error.statusCode in 500..599 || error.statusCode in setOf(408, 429)) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (error: IllegalStateException) {
            Result.retry()
        }
    }

    private fun belongsToCurrentSession(
        api: DjangoApiClient,
        ownerId: String,
        tenantUrl: String
    ): Boolean {
        if (!api.hasSession || !api.tenantConfigured || api.baseUrl != tenantUrl) return false
        val session = try {
            api.cachedSessionForCurrentTenant()
        } catch (_: IllegalStateException) {
            null
        } ?: return false
        return session.optJSONObject("user")?.optString("id") == ownerId
    }

    private fun parsePendingDigest(encoded: String?): List<NotificationPayload> {
        if (encoded.isNullOrBlank()) return emptyList()
        val items = try {
            JSONArray(encoded)
        } catch (_: org.json.JSONException) {
            return emptyList()
        }
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { json ->
                NotificationPayload(
                    id = json.optString("id"),
                    title = json.optString("title"),
                    message = json.optString("message"),
                    kind = json.optString("kind"),
                    isRead = false
                )
            }
        }.filter { it.id.isNotBlank() }
    }

    private data class NotificationPayload(
        val id: String,
        val title: String,
        val message: String,
        val kind: String,
        val isRead: Boolean
    ) {
        companion object {
            fun from(json: JSONObject) = NotificationPayload(
                id = json.optString("id"),
                title = json.optString("title", "School update"),
                message = json.optString("message"),
                kind = json.optString("kind").takeIf(String::isNotBlank)
                    ?: json.optString("kind_display"),
                isRead = json.optBoolean("is_read")
            )
        }
    }

    companion object {
        const val KEY_OWNER_ID = "notification_owner_id"
        const val KEY_TENANT_URL = "notification_tenant_url"
        private const val PREFERENCES = "notification_delivery_state"
        private const val MAX_PENDING_DIGEST_ITEMS = 300
        private const val MAX_DIGEST_PREVIEW_ITEMS = 8
        private const val MAX_NOTIFICATION_TEXT_LENGTH = 2_500

        fun stateKey(tenantUrl: String, ownerId: String): String =
            "seen_" + java.security.MessageDigest.getInstance("SHA-256")
                .digest("$tenantUrl|$ownerId".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

object NotificationQuietHours {
    private val quietStart = LocalTime.of(22, 0)
    private val quietEnd = LocalTime.of(7, 0)

    fun isQuiet(time: LocalTime): Boolean =
        time >= quietStart || time < quietEnd
}

object NotificationWorkScheduler {
    private const val UNIQUE_WORK_NAME = "school-notification-polling"

    fun schedule(context: Context, ownerId: String, tenantUrl: String) {
        val request = PeriodicWorkRequestBuilder<NotificationPollingWorker>(15, TimeUnit.MINUTES)
            .setInputData(inputData(ownerId, tenantUrl))
            .setConstraints(networkConstraints())
            .addTag(UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(context: Context, ownerId: String, tenantUrl: String) {
        val request = OneTimeWorkRequestBuilder<NotificationPollingWorker>()
            .setInputData(inputData(ownerId, tenantUrl))
            .setConstraints(networkConstraints())
            .addTag(UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "$UNIQUE_WORK_NAME-immediate-${NotificationPollingWorker.stateKey(tenantUrl, ownerId)}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelAllWorkByTag(UNIQUE_WORK_NAME)
    }

    private fun inputData(ownerId: String, tenantUrl: String) =
        androidx.work.Data.Builder()
            .putString(NotificationPollingWorker.KEY_OWNER_ID, ownerId)
            .putString(NotificationPollingWorker.KEY_TENANT_URL, tenantUrl)
            .build()

    private fun networkConstraints() =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}

enum class SchoolNotificationCategory(val channelId: String, val title: String) {
    EXEATS("school_exeats", "Exeat updates"),
    INCIDENTS("school_incidents", "Incidents"),
    ROLL_CALL("school_roll_call", "Roll call"),
    SYNC_FAILURES("school_sync_failures", "Sync failures"),
    GENERAL("school_updates", "School updates");

    val destination: AppScreen
        get() = when (this) {
            EXEATS -> AppScreen.EXEATS
            INCIDENTS, GENERAL -> AppScreen.NOTIFICATIONS
            ROLL_CALL -> AppScreen.ROLL_CALL_HISTORY
            SYNC_FAILURES -> AppScreen.OUTBOX
        }

    companion object {
        fun fromKind(kind: String): SchoolNotificationCategory {
            val normalized = kind.lowercase().replace('-', '_').replace(' ', '_')
            return when {
                "exeat" in normalized -> EXEATS
                listOf("incident", "emergency", "discipline").any(normalized::contains) -> INCIDENTS
                listOf("roll_call", "rollcall", "attendance", "muster").any(normalized::contains) ->
                    ROLL_CALL
                listOf("sync", "outbox", "delivery", "rejected").any(normalized::contains) ->
                    SYNC_FAILURES
                else -> GENERAL
            }
        }
    }
}
