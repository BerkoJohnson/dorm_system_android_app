package com.example.data.repository

import com.example.data.local.AppDatabase
import androidx.room.withTransaction
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.RemoteMutationOutboxItem
import com.example.data.model.RoomEntity
import com.example.data.model.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID
import org.json.JSONObject

sealed class GateVerificationResult {
    data class ClearedForDeparture(
        val student: Student,
        val exeat: ExeatRequest,
        val validUntilMs: Long
    ) : GateVerificationResult()

    data class ClearedForReturn(
        val student: Student,
        val exeat: ExeatRequest?,
        val isOverdue: Boolean
    ) : GateVerificationResult()

    data class BlockedNoExeat(
        val student: Student,
        val reason: String
    ) : GateVerificationResult()

    data class StudentNotFound(
        val query: String
    ) : GateVerificationResult()

    data class LookupMessage(
        val message: String
    ) : GateVerificationResult()
}

class BoardingRepository(private val db: AppDatabase) {
    private var auditOwnerId: String = ""
    private var auditTenantUrl: String = ""

    val allHouses: Flow<List<House>> = db.houseDao().getAllHouses()
    val allRooms: Flow<List<RoomEntity>> = db.roomDao().getAllRooms()
    val allStudents: Flow<List<Student>> = db.studentDao().getAllStudents()
    val allExeats: Flow<List<ExeatRequest>> = db.exeatDao().getAllExeats()
    val allMovements: Flow<List<GateMovement>> = db.gateMovementDao().getAllMovements()

    fun setAuditScope(ownerId: String, tenantUrl: String) {
        auditOwnerId = ownerId.takeIf(String::isNotBlank).orEmpty()
        auditTenantUrl = tenantUrl.takeIf(String::isNotBlank).orEmpty()
    }

    fun auditLogs(ownerId: String, tenantUrl: String): Flow<List<AuditLog>> =
        db.auditDao().getAuditLogs(ownerId, tenantUrl)

    suspend fun clearSessionData() {
        db.withTransaction {
            db.houseDao().clear()
            db.roomDao().clear()
            db.studentDao().clear()
            db.exeatDao().clear()
            db.attendanceDao().clear()
            db.gateMovementDao().clear()
        }
    }

    suspend fun hasUsableSchoolCache(): Boolean =
        db.houseDao().countHouses() > 0 && db.studentDao().countStudents() > 0

    suspend fun hasCachedSchoolData(): Boolean =
        db.houseDao().countHouses() > 0 ||
            db.roomDao().countRooms() > 0 ||
            db.studentDao().countStudents() > 0

    suspend fun saveStudent(student: Student) {
        db.studentDao().insertStudent(student)
    }

    suspend fun studentIndexExists(indexNumber: String, excludingId: String? = null): Boolean =
        db.studentDao().getStudentByIndexNumber(indexNumber)
            ?.let { it.id != excludingId } == true

    val rollCallOutboxDao get() = db.rollCallOutboxDao()
    val remoteMutationOutboxDao get() = db.remoteMutationOutboxDao()

    suspend fun updateGateMovementSyncState(id: String, status: String, error: String? = null) {
        db.gateMovementDao().updateSyncState(id, status, error)
    }

    fun getRoomsByHouse(houseId: String): Flow<List<RoomEntity>> =
        db.roomDao().getRoomsByHouse(houseId)

    fun getStudentsByHouse(houseId: String): Flow<List<Student>> =
        db.studentDao().getStudentsByHouse(houseId)

    fun getStudentsByRoom(roomId: String): Flow<List<Student>> =
        db.studentDao().getStudentsByRoom(roomId)

    fun getExeatsByHouse(houseId: String): Flow<List<ExeatRequest>> =
        db.exeatDao().getExeatsByHouse(houseId)

    suspend fun getStudentByQrOrIndex(query: String): Student? {
        val trimmed = query.trim()
        val byQr = db.studentDao().getStudentByQrKey(trimmed)
        if (byQr != null) return byQr
        val byIndex = db.studentDao().getStudentByIndexNumber(trimmed)
        if (byIndex != null) return byIndex
        // Also check by ID
        return db.studentDao().getStudentByIdDirect(trimmed)
    }

    suspend fun verifyGatePass(query: String): GateVerificationResult {
        val student = getStudentByQrOrIndex(query) ?: return GateVerificationResult.StudentNotFound(query)
        val activeExeat = db.exeatDao().getActiveExeatForStudent(student.id)

        if (
            activeExeat?.status == "APPROVED" &&
            student.status in setOf("IN_DORM", "ON_EXEAT")
        ) {
            return GateVerificationResult.ClearedForDeparture(
                student = student,
                exeat = activeExeat,
                validUntilMs = activeExeat.expectedReturnTime
            )
        }

        if (student.status == "ON_EXEAT" && activeExeat?.status == "DEPARTED") {
            val now = System.currentTimeMillis()
            val isOverdue = now > activeExeat.expectedReturnTime
            return GateVerificationResult.ClearedForReturn(
                student = student,
                exeat = activeExeat,
                isOverdue = isOverdue
            )
        }

        // In SICK_BAY or ABSENT
        return GateVerificationResult.BlockedNoExeat(
            student = student,
            reason = "Student is recorded as '${student.status}'. Requires Housemaster/Senior Housemaster clearance."
        )
    }

    suspend fun recordGateMovement(
        student: Student,
        exeatId: String,
        movementType: String, // "DEPARTURE" or "ARRIVAL"
        officerName: String,
        gateName: String,
        remarks: String,
        ownerId: String,
        tenantUrl: String
    ): GateMovement = db.withTransaction {
        require(movementType == "DEPARTURE" || movementType == "ARRIVAL") {
            "Unsupported gate movement."
        }
        require(ownerId.isNotBlank() && tenantUrl.isNotBlank()) {
            "A signed-in account and school are required to record gate movements."
        }
        val currentStudent = db.studentDao().getStudentByIdDirect(student.id)
            ?: throw IllegalStateException("The student is no longer in the synced roster.")
        val exeat = db.exeatDao().getExeatByIdDirect(exeatId)
            ?: throw IllegalStateException("The approved exeat is no longer in the synced records.")
        require(exeat.studentId == currentStudent.id) {
            "The exeat does not belong to this student."
        }
        when (movementType) {
            "DEPARTURE" -> require(
                currentStudent.status in setOf("IN_DORM", "ON_EXEAT") &&
                    exeat.status == "APPROVED"
            ) {
                "The student is not currently cleared to depart. Verify the latest synced pass."
            }
            "ARRIVAL" -> require(
                currentStudent.status == "ON_EXEAT" && exeat.status == "DEPARTED"
            ) {
                "The student does not have an active departure to return from."
            }
        }

        val timestamp = System.currentTimeMillis()
        val movementId = UUID.randomUUID().toString()
        val movement = GateMovement(
            id = movementId,
            studentId = currentStudent.id,
            studentName = currentStudent.fullName,
            studentIndexNumber = currentStudent.studentId,
            houseName = currentStudent.houseId,
            exeatId = exeatId,
            movementType = movementType,
            timestamp = timestamp,
            officerName = officerName,
            gateName = gateName,
            isAuthorized = true,
            remarks = remarks,
            syncStatus = "PENDING_SYNC"
        )
        check(db.gateMovementDao().insertMovement(movement) != -1L) {
            "This gate movement was already recorded."
        }

        val route = if (movementType == "DEPARTURE") "gate/depart/" else "gate/return/"
        val outboxId = db.remoteMutationOutboxDao().insert(
            RemoteMutationOutboxItem(
                id = movementId,
                ownerId = ownerId,
                tenantUrl = tenantUrl,
                path = route,
                payloadJson = JSONObject().put("exeat_id", exeatId).toString(),
                attempts = 0,
                lastError = null,
                createdAt = timestamp
            )
        )
        check(outboxId != -1L) { "This gate movement is already queued for synchronization." }

        when (movementType) {
            "DEPARTURE" -> {
                db.studentDao().updateStudentStatus(currentStudent.id, "ON_EXEAT")
                db.exeatDao().updateExeat(
                    exeat.copy(status = "DEPARTED", actualDepartureTime = timestamp)
                )
                logAudit(
                    action = "GATE_DEPARTURE_LOGGED",
                    actorRole = "Gate Officer",
                    actorName = officerName,
                    details = "Student ${currentStudent.fullName} (${currentStudent.studentId}) departed via $gateName."
                )
            }
            "ARRIVAL" -> {
                db.studentDao().updateStudentStatus(currentStudent.id, "IN_DORM")
                db.exeatDao().updateExeat(
                    exeat.copy(status = "RETURNED", actualReturnTime = timestamp)
                )
                logAudit(
                    action = "GATE_RETURN_LOGGED",
                    actorRole = "Gate Officer",
                    actorName = officerName,
                    details = "Student ${currentStudent.fullName} (${currentStudent.studentId}) returned through $gateName."
                )
            }
        }
        movement
    }

    suspend fun approveExeat(
        exeatId: String,
        approverName: String,
        conditionNotes: String?
    ) {
        val exeat = db.exeatDao().getExeatByIdDirect(exeatId) ?: return
        val updated = exeat.copy(
            status = "APPROVED",
            approvedBy = approverName,
            conditionNotes = conditionNotes
        )
        db.exeatDao().updateExeat(updated)
        logAudit(
            action = "EXEAT_APPROVED",
            actorRole = "Housemaster",
            actorName = approverName,
            details = "Exeat $exeatId approved for ${exeat.studentName} (${exeat.category})."
        )
    }

    suspend fun rejectExeat(
        exeatId: String,
        approverName: String,
        reason: String
    ) {
        val exeat = db.exeatDao().getExeatByIdDirect(exeatId) ?: return
        val updated = exeat.copy(
            status = "REJECTED",
            approvedBy = approverName,
            rejectionReason = reason
        )
        db.exeatDao().updateExeat(updated)
        logAudit(
            action = "EXEAT_REJECTED",
            actorRole = "Housemaster",
            actorName = approverName,
            details = "Exeat $exeatId rejected for ${exeat.studentName}. Reason: $reason"
        )
    }

    suspend fun submitExeat(
        exeat: ExeatRequest,
        actorRole: String,
        actorName: String
    ) {
        db.exeatDao().insertExeat(exeat)
        logAudit(
            action = "EXEAT_REQUEST_SUBMITTED",
            actorRole = actorRole,
            actorName = actorName,
            details = "New exeat request ${exeat.id} created for ${exeat.studentName} (${exeat.category})."
        )
    }

    suspend fun submitRollCall(
        records: List<AttendanceRecord>,
        housemasterName: String,
        roomId: String,
        sessionType: String,
        dateStr: String
    ) {
        // Clear previous session for that room if re-submitting
        db.attendanceDao().deleteAttendanceForRoomSession(dateStr, roomId, sessionType)
        db.attendanceDao().insertAttendanceRecords(records)

        // Also sync students status if marked SICK_BAY or ABSENT
        for (rec in records) {
            if (rec.status == "SICK_BAY") {
                db.studentDao().updateStudentStatus(rec.studentId, "SICK_BAY")
            } else if (rec.status == "ABSENT") {
                db.studentDao().updateStudentStatus(rec.studentId, "ABSENT")
            } else if (rec.status == "PRESENT") {
                db.studentDao().updateStudentStatus(rec.studentId, "IN_DORM")
            }
        }

        logAudit(
            action = "ROLL_CALL_LOCKED",
            actorRole = "Housemaster",
            actorName = housemasterName,
            details = "Completed $sessionType for room $roomId with ${records.size} students."
        )
    }

    suspend fun addStudent(
        student: Student,
        actorRole: String,
        actorName: String
    ) {
        db.studentDao().insertStudent(student)
        logAudit(
            action = "STUDENT_ENROLLED",
            actorRole = actorRole,
            actorName = actorName,
            details = "Enrolled ${student.fullName} (${student.studentId}) into ${student.houseId}."
        )
    }

    suspend fun reassignRoom(
        studentId: String,
        newRoomId: String,
        newBedNumber: Int,
        actorRole: String,
        actorName: String
    ) {
        db.studentDao().updateStudentRoomAllocation(studentId, newRoomId, newBedNumber)
        logAudit(
            action = "ROOM_REALLOCATION",
            actorRole = actorRole,
            actorName = actorName,
            details = "Reassigned student $studentId to room $newRoomId (Bed #$newBedNumber)."
        )
    }

    suspend fun logAudit(
        action: String,
        actorRole: String,
        actorName: String,
        details: String
    ) {
        db.auditDao().insertAuditLog(
            AuditLog(
                action = action,
                actorRole = actorRole,
                actorName = actorName,
                details = details,
                timestamp = System.currentTimeMillis(),
                ownerId = auditOwnerId,
                tenantUrl = auditTenantUrl
            )
        )
    }
}
