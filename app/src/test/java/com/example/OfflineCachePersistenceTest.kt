package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.ExeatRequest
import com.example.data.model.House
import com.example.data.model.RemoteMutationOutboxItem
import com.example.data.model.RollCallIdempotencyKey
import com.example.data.model.RollCallOutboxItem
import com.example.data.model.Student
import com.example.data.repository.BoardingRepository
import com.example.data.repository.GateVerificationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineCachePersistenceTest {
    private lateinit var context: Context
    private val databaseName = "offline-cache-${UUID.randomUUID()}.db"
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun cachedRecordsGateVerificationAndOutboxSurviveDatabaseRestart() = runBlocking {
        val repository = BoardingRepository(database)
        val now = System.currentTimeMillis()
        database.houseDao().insertHouse(
            House(
                id = "house-1",
                name = "North House",
                gender = "CO-ED",
                housemasterName = "Staff Member",
                housemasterPhone = "",
                capacity = 40,
                colorHex = "#123456",
                motto = ""
            )
        )
        database.studentDao().insertStudent(
            Student(
                id = "student-1",
                studentId = "IDX-1",
                fullName = "Student One",
                gender = "FEMALE",
                classForm = "SHS 2",
                program = "Science",
                houseId = "house-1",
                roomId = "room-1",
                bedNumber = 1,
                guardianName = "",
                guardianPhone = "",
                guardianRelationship = "",
                qrCodeKey = "qr-1"
            )
        )
        database.exeatDao().insertExeat(
            ExeatRequest(
                id = "exeat-1",
                studentId = "student-1",
                studentName = "Student One",
                studentIndexNumber = "IDX-1",
                houseId = "house-1",
                category = "FAMILY",
                reason = "Home visit",
                destination = "Home",
                departureTime = now - 60_000,
                expectedReturnTime = now + 60_000,
                status = "APPROVED"
            )
        )
        val actionId = RollCallIdempotencyKey.create(
            "staff-1", "https://school.example/api/v1",
            "house-1", "room-1", "EVENING_ROLL_CALL", "2026-10-27"
        )
        val rollCall = RollCallOutboxItem(
            id = actionId,
            ownerId = "staff-1",
            tenantUrl = "https://school.example/api/v1",
            houseId = "house-1",
            dormitoryId = "room-1",
            sessionType = "EVENING_ROLL_CALL",
            inspectionDate = "2026-10-27",
            statusesJson = """{"student-1":"PRESENT"}""",
            remarksJson = """{"student-1":"Present at the dorm"}""",
            remoteSessionId = null,
            attempts = 0,
            lastError = null,
            createdAt = now
        )
        database.rollCallOutboxDao().insert(rollCall)
        assertEquals(
            -1L,
            database.rollCallOutboxDao().insert(rollCall.copy(statusesJson = """{"student-1":"ABSENT"}"""))
        )
        database.remoteMutationOutboxDao().insert(
            RemoteMutationOutboxItem(
                id = "mutation-1",
                ownerId = "staff-1",
                tenantUrl = "https://school.example/api/v1",
                path = "gate/depart/",
                payloadJson = """{"exeat_id":"exeat-1"}""",
                attempts = 0,
                lastError = null,
                createdAt = now
            )
        )

        assertTrue(repository.hasUsableSchoolCache())
        val verificationStartedAt = System.nanoTime()
        val verified = repository.verifyGatePass("qr-1")
        val verificationDurationMs = (System.nanoTime() - verificationStartedAt) / 1_000_000
        assertTrue("Local pass verification exceeded five seconds.", verificationDurationMs < 5_000)
        assertTrue(verified is GateVerificationResult.ClearedForDeparture)
        val departure = verified as GateVerificationResult.ClearedForDeparture
        val departureMovement = repository.recordGateMovement(
            student = departure.student,
            exeatId = departure.exeat.id,
            movementType = "DEPARTURE",
            officerName = "Gate Officer",
            gateName = "Main Gate",
            remarks = "",
            ownerId = "staff-1",
            tenantUrl = "https://school.example/api/v1"
        )
        assertEquals("PENDING_SYNC", departureMovement.syncStatus)
        assertEquals("ON_EXEAT", database.studentDao().getStudentByIdDirect("student-1")?.status)

        var duplicateDepartureBlocked = false
        try {
            repository.recordGateMovement(
                student = departure.student,
                exeatId = departure.exeat.id,
                movementType = "DEPARTURE",
                officerName = "Gate Officer",
                gateName = "Main Gate",
                remarks = "",
                ownerId = "staff-1",
                tenantUrl = "https://school.example/api/v1"
            )
        } catch (_: IllegalArgumentException) {
            duplicateDepartureBlocked = true
        }
        assertTrue("A duplicate departure must not be recorded.", duplicateDepartureBlocked)

        val returnVerification = repository.verifyGatePass("IDX-1")
        assertTrue(returnVerification is GateVerificationResult.ClearedForReturn)
        val returnResult = returnVerification as GateVerificationResult.ClearedForReturn
        repository.recordGateMovement(
            student = returnResult.student,
            exeatId = checkNotNull(returnResult.exeat).id,
            movementType = "ARRIVAL",
            officerName = "Gate Officer",
            gateName = "Main Gate",
            remarks = "",
            ownerId = "staff-1",
            tenantUrl = "https://school.example/api/v1"
        )

        database.close()
        database = openDatabase()
        val restartedRepository = BoardingRepository(database)

        assertTrue(restartedRepository.hasUsableSchoolCache())
        assertEquals("Student One", database.studentDao().getStudentByQrKey("qr-1")?.fullName)
        val storedMovements = restartedRepository.allMovements.first()
        assertTrue(storedMovements.any {
            it.studentId == "student-1" && it.movementType == "DEPARTURE"
        })
        assertTrue(storedMovements.any {
            it.studentId == "student-1" &&
                it.movementType == "ARRIVAL" &&
                it.syncStatus == "PENDING_SYNC"
        })
        val arrivalMovement = storedMovements.first { it.movementType == "ARRIVAL" }
        database.gateMovementDao().updateSyncState(
            arrivalMovement.id,
            "REVIEW_REQUIRED",
            "409 conflict: movement already recorded by another gate device."
        )
        val rejectedMovement = restartedRepository.allMovements.first()
            .first { it.id == arrivalMovement.id }
        assertEquals("REVIEW_REQUIRED", rejectedMovement.syncStatus)
        assertTrue(rejectedMovement.syncError.orEmpty().contains("409 conflict"))
        assertEquals(1, database.rollCallOutboxDao().countFor(
            "staff-1", "https://school.example/api/v1"
        ))
        assertEquals(3, database.remoteMutationOutboxDao().countFor(
            "staff-1", "https://school.example/api/v1"
        ))
        assertTrue(database.remoteMutationOutboxDao()
            .getFor("staff-1", "https://school.example/api/v1")
            .any { it.id == storedMovements.first { movement -> movement.movementType == "DEPARTURE" }.id })
        assertEquals(actionId, database.rollCallOutboxDao().getAll().single().id)
        assertEquals(
            """{"student-1":"Present at the dorm"}""",
            database.rollCallOutboxDao().getById(
                actionId,
                "staff-1",
                "https://school.example/api/v1"
            )?.remarksJson
        )
        assertEquals("North House", restartedRepository.allHouses.first().single().name)
    }

    @Test
    fun repeatedRollCallSubmissionsUseTheSameIdempotencyIdentity() {
        val first = RollCallIdempotencyKey.create(
            "staff-1", "https://school.example/api/v1",
            "house-1", "room-1", "EVENING_ROLL_CALL", "2026-10-27"
        )
        val duplicate = RollCallIdempotencyKey.create(
            "staff-1", "https://school.example/api/v1",
            "house-1", "room-1", "EVENING_ROLL_CALL", "2026-10-27"
        )
        val anotherAccount = RollCallIdempotencyKey.create(
            "staff-2", "https://school.example/api/v1",
            "house-1", "room-1", "EVENING_ROLL_CALL", "2026-10-27"
        )

        assertEquals(first, duplicate)
        assertTrue(first != anotherAccount)
    }

    @Test
    fun clearingSensitiveSchoolCacheDoesNotDeletePendingWork() = runBlocking {
        val repository = BoardingRepository(database)
        database.houseDao().insertHouse(
            House(
                id = "house-1",
                name = "North House",
                gender = "CO-ED",
                housemasterName = "",
                housemasterPhone = "",
                capacity = 40,
                colorHex = "#123456",
                motto = ""
            )
        )
        database.studentDao().insertStudent(
            Student(
                id = "student-1",
                studentId = "IDX-1",
                fullName = "Student One",
                gender = "FEMALE",
                classForm = "SHS 2",
                program = "Science",
                houseId = "house-1",
                roomId = "room-1",
                bedNumber = 1,
                guardianName = "",
                guardianPhone = "",
                guardianRelationship = "",
                qrCodeKey = "qr-1"
            )
        )
        val rollCallId = RollCallIdempotencyKey.create(
            "staff-1", "https://school.example/api/v1",
            "house-1", "room-1", "EVENING_ROLL_CALL", "2026-10-27"
        )
        database.rollCallOutboxDao().insert(
            RollCallOutboxItem(
                id = rollCallId,
                ownerId = "staff-1",
                tenantUrl = "https://school.example/api/v1",
                houseId = "house-1",
                dormitoryId = "room-1",
                sessionType = "EVENING_ROLL_CALL",
                inspectionDate = "2026-10-27",
                statusesJson = """{"student-1":"PRESENT"}""",
                remoteSessionId = null,
                attempts = 0,
                lastError = null,
                createdAt = 1L
            )
        )
        database.remoteMutationOutboxDao().insert(
            RemoteMutationOutboxItem(
                id = "mutation-1",
                ownerId = "staff-1",
                tenantUrl = "https://school.example/api/v1",
                path = "gate/depart/",
                payloadJson = """{"exeat_id":"exeat-1"}""",
                attempts = 0,
                lastError = null,
                createdAt = 1L
            )
        )

        repository.clearSessionData()

        assertEquals(0, database.houseDao().countHouses())
        assertEquals(0, database.studentDao().countStudents())
        assertEquals(
            1,
            database.rollCallOutboxDao().countFor(
                "staff-1", "https://school.example/api/v1"
            )
        )
        assertEquals(
            1,
            database.remoteMutationOutboxDao().countFor(
                "staff-1", "https://school.example/api/v1"
            )
        )
        val storedRollCall = checkNotNull(
            database.rollCallOutboxDao().getById(
                rollCallId,
                "staff-1",
                "https://school.example/api/v1"
            )
        )
        database.rollCallOutboxDao().update(
            storedRollCall.copy(
                attempts = 2,
                deliveryStatus = "FAILED",
                lastError = "The school server rejected this session."
            )
        )
        assertEquals(
            0,
            database.rollCallOutboxDao().countFor(
                "staff-1", "https://school.example/api/v1"
            )
        )
        assertEquals(
            1,
            database.rollCallOutboxDao().countFailedFor(
                "staff-1", "https://school.example/api/v1"
            )
        )
        assertEquals(1, database.rollCallOutboxDao().getAll().size)
    }

    @Test
    fun localAuditEventsSurviveCacheClearAndRemainAccountTenantScoped() = runBlocking {
        val repository = BoardingRepository(database)
        val tenantUrl = "https://school.example/api/v1"
        repository.setAuditScope("staff-1", tenantUrl)
        repository.logAudit(
            action = "ROLL_CALL_SAVED",
            actorRole = "Housemaster",
            actorName = "Staff One",
            details = "Saved a roll call."
        )

        repository.clearSessionData()

        assertEquals(1, repository.auditLogs("staff-1", tenantUrl).first().size)
        assertTrue(repository.auditLogs("staff-2", tenantUrl).first().isEmpty())
        assertTrue(repository.auditLogs("staff-1", "https://other.example/api/v1").first().isEmpty())
    }

    private fun openDatabase(): AppDatabase {
        context.getDatabasePath(databaseName).parentFile?.mkdirs()
        return Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
    }
}
