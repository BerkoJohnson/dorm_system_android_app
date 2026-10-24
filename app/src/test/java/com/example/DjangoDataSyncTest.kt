package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoDataSync
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DjangoDataSyncTest {
    private lateinit var database: AppDatabase
    private lateinit var dataSync: DjangoDataSync

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataSync = DjangoDataSync(database, DjangoApiClient(context))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `student details map server identifiers guardian and QR token`() {
        val student = dataSync.studentFrom(
            JSONObject(
                """
                {
                  "id": "student-1",
                  "admission_no": "ADM-001",
                  "index_number": "IDX-001",
                  "full_name": "A Student",
                  "gender": "FEMALE",
                  "status": "ENROLLED",
                  "is_active_exeat": false,
                  "house": {"id": "house-1"},
                  "dormitory": {"id": "dorm-1"},
                  "class_group": {"name": "SHS 2"},
                  "guardian": {"full_name": "Guardian One", "phone_number": "0200000000"},
                  "public_token": "public-token-1"
                }
                """.trimIndent()
            )
        )

        assertNotNull(student)
        assertEquals("IDX-001", student?.studentId)
        assertEquals("house-1", student?.houseId)
        assertEquals("dorm-1", student?.roomId)
        assertEquals("Guardian One", student?.guardianName)
        assertEquals("0200000000", student?.guardianPhone)
        assertEquals("public-token-1", student?.qrCodeKey)
    }

    @Test
    fun `exeat details map movement and rejection reason`() {
        val exeat = dataSync.exeatFrom(
            JSONObject(
                """
                {
                  "id": "exeat-1",
                  "status": "REJECTED",
                  "exeat_type": "Medical",
                  "reason": "Clinic visit",
                  "destination": "Hospital",
                  "expected_departure": "2026-05-14T09:00:00Z",
                  "expected_return": "2026-05-14T12:00:00Z",
                  "approval_remarks": "Please resubmit with a guardian note.",
                  "student": {
                    "id": "student-1",
                    "full_name": "A Student",
                    "admission_no": "ADM-001",
                    "house_id": "house-1"
                  },
                  "movement": {
                    "actual_departure": null,
                    "actual_return": null
                  }
                }
                """.trimIndent()
            )
        )

        assertNotNull(exeat)
        assertEquals("REJECTED", exeat?.status)
        assertEquals("MEDICAL", exeat?.category)
        assertEquals("Hospital", exeat?.destination)
        assertEquals("Please resubmit with a guardian note.", exeat?.rejectionReason)
    }

    @Test
    fun `dashboard attendance uses latest roll call instead of inferred boarding count`() {
        val metrics = dataSync.metricsFrom(
            JSONObject(
                """
                {
                  "students": {"boarding": 20, "on_exeat": 3},
                  "exeats": {"on_pass": 3, "pending_approvals": 2},
                  "roll_call": {
                    "latest_session": {
                      "counts": {
                        "present": 11,
                        "absent": 4,
                        "infirmary": 2
                      }
                    }
                  }
                }
                """.trimIndent()
            ),
            totalHouses = 2,
            totalCapacity = 40
        )

        assertEquals(11, metrics.presentInDorm)
        assertEquals(4, metrics.unexcusedAbsent)
        assertEquals(2, metrics.sickBay)
        assertEquals(2, metrics.pendingExeats)
    }
}
