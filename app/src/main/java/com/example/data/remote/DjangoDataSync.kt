package com.example.data.remote

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.ExeatRequest
import com.example.data.model.House
import com.example.data.model.RoomEntity
import com.example.data.model.Student
import com.example.data.model.userRoleFromServerRole
import com.example.ui.viewmodel.DashboardMetrics
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class DjangoSyncSnapshot(
    val metrics: DashboardMetrics,
    val capabilities: JSONObject,
    val features: JSONObject,
    val defaultHouseId: String?
)

class DjangoDataSync(
    private val database: AppDatabase,
    private val api: DjangoApiClient
) {
    suspend fun refresh(
        onSessionVerified: suspend (JSONObject, JSONObject) -> Unit = { _, _ -> }
    ): DjangoSyncSnapshot {
        val me = api.get("auth/me/")
        val user = me.optJSONObject("user")
        val capabilities = user?.optJSONObject("capabilities")
        val role = user?.optString("role").orEmpty().uppercase()
        val supportedRole = userRoleFromServerRole(role) != null
        val roleMatchesCapabilities =
            role.isNotBlank() && role.equals(capabilities?.optString("role"), ignoreCase = true)
        val houseRequired = role == "HOUSEMASTER" || role == "HOUSE_TUTOR"
        val assignedHouseId = capabilities?.optString("house_id")
            ?.takeUnless { it.isBlank() || it == "null" }
        if (
            user?.optBoolean("is_active") != true ||
            capabilities == null ||
            !supportedRole ||
            !roleMatchesCapabilities ||
            (role == "ADMIN" && !capabilities.optBoolean("is_admin")) ||
            (houseRequired && assignedHouseId == null)
        ) {
            api.expireSession()
            throw IllegalStateException(
                "This account no longer has an active, supported school staff role. Sign in again or contact the administrator."
            )
        }
        val features = me.optJSONObject("features") ?: JSONObject()
        onSessionVerified(capabilities, features)

        val dashboard = api.get("dashboard/")
        val reference = api.get("reference/")
        val schoolWide = capabilities.optBoolean("is_admin") ||
            capabilities.optBoolean("is_senior_housemaster")
        val medicalOnly = capabilities.optBoolean("is_medical") && !schoolWide
        val housesJson = scopedArray(reference.optJSONArray("houses"), assignedHouseId, schoolWide) {
            it.optString("id")
        }
        val dormitories = scopedArray(reference.optJSONArray("dormitories"), assignedHouseId, schoolWide) {
            it.optString("house_id")
        }
        val staff = scopedArray(reference.optJSONArray("staff"), assignedHouseId, schoolWide) {
            it.optString("house_id")
        }

        val students = if (capabilities.optBoolean("students_view")) {
            fetchAll("students/")
                .filter { isInHouseScope(it.optJSONObject("house")?.optString("id"), assignedHouseId, schoolWide) }
                .mapNotNull(::studentFrom)
        } else {
            emptyList()
        }
        val exeats = if (
            capabilities.optBoolean("exeats_view") &&
            features.optBoolean("exeat_gate", true)
        ) {
            fetchAll("exeats/")
                .filter {
                    if (medicalOnly) {
                        it.optString("exeat_type").equals("Medical", ignoreCase = true)
                    } else {
                        isInHouseScope(
                            it.optJSONObject("student")?.optString("house_id"),
                            assignedHouseId,
                            schoolWide
                        )
                    }
                }
                .mapNotNull(::exeatFrom)
        } else {
            emptyList()
        }

        val houseMasterByHouse = buildMap<String, JSONObject> {
            for (index in 0 until staff.length()) {
                val member = staff.optJSONObject(index) ?: continue
                val houseId = member.optString("house_id")
                    .takeUnless { it.isBlank() || it == "null" } ?: continue
                put(houseId, member)
            }
        }
        val houseByDormitoryId = buildMap<String, String> {
            for (index in 0 until dormitories.length()) {
                val dormitory = dormitories.optJSONObject(index) ?: continue
                val id = dormitory.optString("id").takeIf(String::isNotBlank) ?: continue
                put(id, dormitory.optString("house_id"))
            }
        }
        val dormsByHouse = buildMap<String, MutableList<JSONObject>> {
            for (index in 0 until dormitories.length()) {
                val dorm = dormitories.optJSONObject(index) ?: continue
                getOrPut(dorm.optString("house_id").takeUnless { it == "null" }.orEmpty()) {
                    mutableListOf()
                }.add(dorm)
            }
        }
        val houses = (0 until housesJson.length()).mapNotNull { index ->
            val json = housesJson.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val houseDorms = dormsByHouse[id].orEmpty()
            val staffMember = houseMasterByHouse[id]
            val genders = houseDorms.map { it.optString("gender") }.distinct()
            House(
                id = id,
                name = json.optString("name", "House"),
                gender = genders.singleOrNull() ?: "CO-ED",
                housemasterName = staffMember?.optString("full_name").orEmpty(),
                housemasterPhone = "",
                capacity = houseDorms.sumOf { it.optInt("capacity") },
                colorHex = houseColor(index),
                motto = json.optString("motto")
            )
        }
        val rooms = (0 until dormitories.length()).mapNotNull { index ->
            val json = dormitories.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            RoomEntity(
                id = id,
                houseId = houseByDormitoryId[id].orEmpty(),
                dormName = json.optString("name", "Dormitory"),
                capacity = json.optInt("capacity"),
                floor = 0
            )
        }

        database.withTransaction {
            database.houseDao().clear()
            database.roomDao().clear()
            database.studentDao().clear()
            database.exeatDao().clear()
            if (houses.isNotEmpty()) database.houseDao().insertHouses(houses)
            if (rooms.isNotEmpty()) database.roomDao().insertRooms(rooms)
            if (students.isNotEmpty()) database.studentDao().insertStudents(students)
            if (exeats.isNotEmpty()) database.exeatDao().insertExeats(exeats)
        }
        val totalCapacity = (0 until dormitories.length()).sumOf {
            dormitories.optJSONObject(it)?.optInt("capacity") ?: 0
        }
        val scopedHouseId = assignedHouseId
        val scopedCapacity = if (scopedHouseId == null) {
            totalCapacity
        } else {
            (0 until dormitories.length()).sumOf { index ->
                val dormitory = dormitories.optJSONObject(index)
                if (dormitory?.optString("house_id") == scopedHouseId) {
                    dormitory.optInt("capacity")
                } else {
                    0
                }
            }
        }
        return DjangoSyncSnapshot(
            metricsFrom(
                dashboard,
                if (scopedHouseId == null) housesJson.length() else 1,
                scopedCapacity
            ),
            capabilities,
            features,
            scopedHouseId ?: houses.firstOrNull()?.id
        )
    }

    private fun scopedArray(
        source: JSONArray?,
        houseId: String?,
        schoolWide: Boolean,
        rowHouseId: (JSONObject) -> String
    ): JSONArray {
        val result = JSONArray()
        if (source == null) return result
        for (index in 0 until source.length()) {
            val row = source.optJSONObject(index) ?: continue
            if (isInHouseScope(rowHouseId(row), houseId, schoolWide)) result.put(row)
        }
        return result
    }

    private fun isInHouseScope(rowHouseId: String?, houseId: String?, schoolWide: Boolean): Boolean {
        if (schoolWide) return true
        if (houseId.isNullOrBlank()) return false
        return rowHouseId == houseId
    }

    private suspend fun fetchAll(path: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var page = 1
        while (true) {
            val response = api.get(path, mapOf("page" to page.toString(), "page_size" to "100"))
            val items = response.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.let(result::add)
            }
            if (items.length() < 100) break
            page += 1
        }
        return result
    }

    fun studentFrom(json: JSONObject): Student? {
        val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
        val house = json.optJSONObject("house")
        val dormitory = json.optJSONObject("dormitory")
        val classGroup = json.optJSONObject("class_group")
        val guardian = json.optJSONObject("guardian")
        val isActiveExeat = json.optBoolean("is_active_exeat")
        val enrollmentStatus = json.optString("status")
        return Student(
            id = id,
            studentId = json.optString("index_number")
                .takeUnless { it.isBlank() || it == "null" }
                ?: json.optString("admission_no").takeUnless { it == "null" }.orEmpty(),
            fullName = json.optString("full_name", "Student"),
            gender = json.optString("gender"),
            classForm = classGroup?.optString("name").orEmpty(),
            program = "",
            houseId = house?.optString("id").takeUnless { it.isNullOrBlank() || it == "null" }.orEmpty(),
            roomId = dormitory?.optString("id").takeUnless { it.isNullOrBlank() || it == "null" }.orEmpty(),
            bedNumber = 0,
            guardianName = guardian?.optString("full_name").takeUnless { it.isNullOrBlank() || it == "null" }.orEmpty(),
            guardianPhone = guardian?.optString("phone_number").takeUnless { it.isNullOrBlank() || it == "null" }.orEmpty(),
            guardianRelationship = "",
            status = when {
                isActiveExeat -> "ON_EXEAT"
                enrollmentStatus == "ENROLLED" -> "IN_DORM"
                else -> enrollmentStatus
            },
            qrCodeKey = json.optString("public_token").takeUnless {
                it.isBlank() || it == "null"
            }.orEmpty(),
            emergencyNotes = ""
        )
    }

    fun exeatFrom(json: JSONObject): ExeatRequest? {
        val student = json.optJSONObject("student")
        val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
        val movement = json.optJSONObject("movement")
        val departure = movement?.optString("actual_departure")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let(::parseTimestamp)
        val returned = movement?.optString("actual_return")
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let(::parseTimestamp)
        val serverStatus = json.optString("status")
        val mappedStatus = when {
            returned != null -> "RETURNED"
            departure != null -> "DEPARTED"
            serverStatus.equals("pending", ignoreCase = true) -> "PENDING"
            serverStatus.equals("approved", ignoreCase = true) -> "APPROVED"
            serverStatus.equals("rejected", ignoreCase = true) -> "REJECTED"
            serverStatus.equals("cancelled", ignoreCase = true) -> "CANCELLED"
            else -> serverStatus.uppercase()
        }
        return ExeatRequest(
            id = id,
            studentId = student?.optString("id").orEmpty(),
            studentName = student?.optString("full_name").orEmpty(),
            studentIndexNumber = student?.optString("admission_no")
                .takeUnless { it.isNullOrBlank() || it == "null" }
                ?: student?.optString("index_number").takeUnless { it.isNullOrBlank() || it == "null" }.orEmpty(),
            houseId = student?.optString("house_id")
                .takeUnless { it.isNullOrBlank() || it == "null" }.orEmpty(),
            category = json.optString("exeat_type").uppercase(),
            reason = json.optString("reason").takeUnless { it == "null" }.orEmpty(),
            destination = json.optString("destination").takeUnless { it == "null" }.orEmpty(),
            departureTime = parseTimestamp(json.optString("expected_departure")),
            expectedReturnTime = parseTimestamp(json.optString("expected_return")),
            actualDepartureTime = departure,
            actualReturnTime = returned,
            status = mappedStatus,
            approvedBy = json.optString("approved_by_name")
                .takeUnless { it.isBlank() || it == "null" },
            rejectionReason = if (mappedStatus == "REJECTED") {
                json.optString("approval_remarks").takeUnless { it.isBlank() || it == "null" }
            } else {
                null
            },
            conditionNotes = json.optString("approval_remarks")
                .takeUnless { it.isBlank() || it == "null" },
            createdAt = parseTimestamp(json.optString("created_at"))
        )
    }

    internal fun metricsFrom(
        data: JSONObject,
        totalHouses: Int,
        totalCapacity: Int
    ): DashboardMetrics {
        val studentCounts = data.optJSONObject("students")
        val exeatCounts = data.optJSONObject("exeats")
        val rollCall = data.optJSONObject("roll_call")
        val latest = rollCall?.optJSONObject("latest_session")
        val latestCounts = latest?.optJSONObject("counts")
        val total = studentCounts?.optInt("boarding") ?: 0
        val onExeat = exeatCounts?.optInt("on_pass") ?: studentCounts?.optInt("on_exeat") ?: 0
        return DashboardMetrics(
            totalBoarders = total,
            presentInDorm = latestCounts?.optInt("present") ?: 0,
            onExeat = onExeat,
            sickBay = latestCounts?.optInt("infirmary") ?: 0,
            unexcusedAbsent = latestCounts?.optInt("absent") ?: 0,
            pendingExeats = exeatCounts?.optInt("pending_approvals") ?: 0,
            totalHouses = totalHouses,
            occupancyPercent = if (totalCapacity > 0) {
                (total * 100 / totalCapacity).coerceAtMost(100)
            } else {
                0
            }
        )
    }

    private fun parseTimestamp(value: String): Long {
        if (value.isBlank() || value == "null") return 0L
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant().toEpochMilli()
            } catch (error: DateTimeParseException) {
                throw IllegalStateException("The school API returned an invalid date-time: $value", error)
            }
        }
    }

    private fun houseColor(index: Int): String = listOf(
        "#072B61",
        "#0066D6",
        "#388E3C",
        "#D97706",
        "#7C3AED",
        "#DC2626"
    )[index % 6]
}
