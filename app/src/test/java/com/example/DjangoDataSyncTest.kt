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
    fun `student details map identifiers guardian placement medical details and photo`() {
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
                  "class_group": {"name": "SHS 2", "program": "General Science"},
                  "room": {"id": "room-1", "name": "A1", "bed_number": 12},
                  "guardian": {
                    "full_name": "Guardian One",
                    "phone_number": "0200000000",
                    "relationship": "Mother"
                  },
                  "medical_notes": "Severe peanut allergy",
                  "photo_url": "/media/students/student-1.jpg",
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
        assertEquals("Mother", student?.guardianRelationship)
        assertEquals("General Science", student?.program)
        assertEquals(12, student?.bedNumber)
        assertEquals("Severe peanut allergy", student?.emergencyNotes)
        assertEquals(
            "${DjangoApiClient(ApplicationProvider.getApplicationContext()).baseUrl.removeSuffix("/api/v1")}/media/students/student-1.jpg",
            student?.photoUrl
        )
        assertEquals("public-token-1", student?.qrCodeKey)
    }

    @Test
    fun `student details preserve genuinely missing fields as unknown rather than blank`() {
        val student = dataSync.studentFrom(
            JSONObject("""{"id":"student-2","full_name":"B Student"}""")
        )

        assertEquals("", student?.program)
        assertEquals("Unknown", student?.emergencyNotes)
    }

    @Test
    fun `partial student sync response does not erase cached guardian and medical fields`() {
        val cached = dataSync.studentFrom(
            JSONObject(
                """
                {
                  "id":"student-3",
                  "full_name":"C Student",
                  "program":"General Arts",
                  "bed_number":8,
                  "guardian_name":"Guardian Three",
                  "guardian_phone":"0240000000",
                  "guardian_relationship":"Father",
                  "emergency_notes":"Asthma",
                  "photo_url":"https://school.example/student.jpg"
                }
                """.trimIndent()
            )
        )!!
        val listResponse = JSONObject(
            """{"id":"student-3","full_name":"C Student","status":"ENROLLED"}"""
        )
        val mappedListResponse = dataSync.studentFrom(listResponse)!!

        val merged = dataSync.preserveUnprovidedStudentDetails(
            listResponse,
            mappedListResponse,
            cached
        )

        assertEquals("General Arts", merged.program)
        assertEquals(8, merged.bedNumber)
        assertEquals("Guardian Three", merged.guardianName)
        assertEquals("0240000000", merged.guardianPhone)
        assertEquals("Father", merged.guardianRelationship)
        assertEquals("Asthma", merged.emergencyNotes)
        assertEquals("https://school.example/student.jpg", merged.photoUrl)
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
