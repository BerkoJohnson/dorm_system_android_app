package com.example.data.repository

import com.example.data.local.AppDatabase
import androidx.room.withTransaction
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.RoomEntity
import com.example.data.model.Student
import kotlinx.coroutines.flow.Flow
import java.util.UUID

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

    val allHouses: Flow<List<House>> = db.houseDao().getAllHouses()
    val allRooms: Flow<List<RoomEntity>> = db.roomDao().getAllRooms()
    val allStudents: Flow<List<Student>> = db.studentDao().getAllStudents()
    val allExeats: Flow<List<ExeatRequest>> = db.exeatDao().getAllExeats()
    val allMovements: Flow<List<GateMovement>> = db.gateMovementDao().getAllMovements()
    val allAuditLogs: Flow<List<AuditLog>> = db.auditDao().getAllAuditLogs()

    suspend fun clearSessionData() {
        db.withTransaction {
            db.houseDao().clear()
            db.roomDao().clear()
            db.studentDao().clear()
            db.exeatDao().clear()
            db.attendanceDao().clear()
            db.gateMovementDao().clear()
            db.auditDao().clear()
        }
    }

    val rollCallOutboxDao get() = db.rollCallOutboxDao()
    val remoteMutationOutboxDao get() = db.remoteMutationOutboxDao()

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

        // If currently IN_DORM, check if cleared to leave
        if (student.status == "IN_DORM") {
            if (activeExeat != null && activeExeat.status == "APPROVED") {
                return GateVerificationResult.ClearedForDeparture(
                    student = student,
                    exeat = activeExeat,
                    validUntilMs = activeExeat.expectedReturnTime
                )
            } else {
                return GateVerificationResult.BlockedNoExeat(
                    student = student,
                    reason = "No approved exeat pass found. Gate exit prohibited."
                )
            }
        }

        // If currently ON_EXEAT, returning to campus
        if (student.status == "ON_EXEAT") {
            val now = System.currentTimeMillis()
            val isOverdue = activeExeat != null && now > activeExeat.expectedReturnTime
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
        exeatId: String?,
        movementType: String, // "DEPARTURE" or "ARRIVAL"
        officerName: String,
        gateName: String,
        remarks: String
    ) {
        val movementId = "MOV_" + System.currentTimeMillis().toString().takeLast(6)
        val movement = GateMovement(
            id = movementId,
            studentId = student.id,
            studentName = student.fullName,
            studentIndexNumber = student.studentId,
            houseName = student.houseId,
            exeatId = exeatId,
            movementType = movementType,
            timestamp = System.currentTimeMillis(),
            officerName = officerName,
            gateName = gateName,
            isAuthorized = true,
            remarks = remarks
        )
        db.gateMovementDao().insertMovement(movement)

        // Update student status & exeat state
        if (movementType == "DEPARTURE") {
            db.studentDao().updateStudentStatus(student.id, "ON_EXEAT")
            if (exeatId != null) {
                val exeat = db.exeatDao().getExeatByIdDirect(exeatId)
                if (exeat != null) {
                    db.exeatDao().updateExeat(
                        exeat.copy(
                            status = "DEPARTED",
                            actualDepartureTime = System.currentTimeMillis()
                        )
                    )
                }
            }
            logAudit(
                action = "GATE_DEPARTURE_LOGGED",
                actorRole = "Gate Officer",
                actorName = officerName,
                details = "Student ${student.fullName} (${student.studentId}) departed via $gateName."
            )
        } else if (movementType == "ARRIVAL") {
            db.studentDao().updateStudentStatus(student.id, "IN_DORM")
            if (exeatId != null) {
                val exeat = db.exeatDao().getExeatByIdDirect(exeatId)
                if (exeat != null) {
                    db.exeatDao().updateExeat(
                        exeat.copy(
                            status = "RETURNED",
                            actualReturnTime = System.currentTimeMillis()
                        )
                    )
                }
            }
            logAudit(
                action = "GATE_RETURN_LOGGED",
                actorRole = "Gate Officer",
                actorName = officerName,
                details = "Student ${student.fullName} (${student.studentId}) returned through $gateName."
            )
        }
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
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
