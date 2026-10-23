package com.example

import com.example.data.model.ExeatRequest
import com.example.data.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardingDomainUnitTest {

    @Test
    fun testExeatOverdueCalculation() {
        val now = System.currentTimeMillis()
        val expiredExeat = ExeatRequest(
            id = "EX_TEST_01",
            studentId = "STU_001",
            studentName = "Kwame Asante",
            studentIndexNumber = "SHS/2024/0481",
            houseId = "H_AGG",
            category = "MEDICAL",
            reason = "Clinic visit",
            destination = "Hospital",
            departureTime = now - 100000L,
            expectedReturnTime = now - 50000L,
            status = "DEPARTED"
        )

        val isOverdue = expiredExeat.status == "DEPARTED" && now > expiredExeat.expectedReturnTime
        assertTrue("Departed student past return time must be detected as overdue", isOverdue)
    }

    @Test
    fun testStudentInitialsExtraction() {
        val student = Student(
            id = "STU_001",
            studentId = "SHS/2024/0481",
            fullName = "Kwame Asante",
            gender = "MALE",
            classForm = "SHS 2",
            program = "General Science",
            houseId = "H_AGG",
            roomId = "R_AGG_101",
            bedNumber = 1,
            guardianName = "Dr. Asante",
            guardianPhone = "+233 24 555 1201",
            guardianRelationship = "Father",
            status = "IN_DORM",
            qrCodeKey = "SHS-0481-ASANTE-2024"
        )

        val initials = student.fullName.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")

        assertEquals("KA", initials)
    }

    @Test
    fun testRollCallTallying() {
        val statuses = listOf("PRESENT", "PRESENT", "ON_EXEAT", "ABSENT", "SICK_BAY", "PRESENT")
        val present = statuses.count { it == "PRESENT" }
        val absent = statuses.count { it == "ABSENT" }
        val exeat = statuses.count { it == "ON_EXEAT" }
        val sick = statuses.count { it == "SICK_BAY" }

        assertEquals(3, present)
        assertEquals(1, absent)
        assertEquals(1, exeat)
        assertEquals(1, sick)
    }
}
