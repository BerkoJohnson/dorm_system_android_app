package com.example.data.local

import com.example.data.model.AuditLog
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.RoomEntity
import com.example.data.model.Student

object InitialDataSeeder {

    suspend fun seedIfEmpty(database: AppDatabase) {
        val count = database.studentDao().getStudentCount()
        if (count > 0) return

        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour

        // 1. Houses
        val houses = listOf(
            House(
                id = "H_AGG",
                name = "Aggrey House",
                gender = "MALE",
                housemasterName = "Mr. Kwabena Boateng",
                housemasterPhone = "+233 24 412 8901",
                capacity = 80,
                colorHex = "#1E3A8A",
                motto = "Semper Fidelis (Always Faithful)"
            ),
            House(
                id = "H_GUG",
                name = "Guggisberg House",
                gender = "MALE",
                housemasterName = "Mr. Ebenezer Osei",
                housemasterPhone = "+233 20 819 2234",
                capacity = 80,
                colorHex = "#B45309",
                motto = "Service and Integrity"
            ),
            House(
                id = "H_YAA",
                name = "Yaa Asantewaa House",
                gender = "FEMALE",
                housemasterName = "Mrs. Grace Mensah",
                housemasterPhone = "+233 24 330 9182",
                capacity = 80,
                colorHex = "#047857",
                motto = "Courage in Leadership"
            ),
            House(
                id = "H_JAK",
                name = "Ndewura Jakpa House",
                gender = "MALE",
                housemasterName = "Mr. Alhaji Haruna",
                housemasterPhone = "+233 27 751 0492",
                capacity = 80,
                colorHex = "#6D28D9",
                motto = "Honor, Wisdom, and Valor"
            )
        )
        database.houseDao().insertHouses(houses)

        // 2. Rooms
        val rooms = listOf(
            RoomEntity("R_AGG_101", "H_AGG", "Block A - Room 101", 12, 1),
            RoomEntity("R_AGG_102", "H_AGG", "Block A - Room 102", 12, 1),
            RoomEntity("R_AGG_201", "H_AGG", "Block B - Room 201", 14, 2),
            RoomEntity("R_GUG_101", "H_GUG", "Nelson Dorm - Ground", 14, 1),
            RoomEntity("R_GUG_102", "H_GUG", "Nelson Dorm - Upper", 14, 2),
            RoomEntity("R_YAA_101", "H_YAA", "Queens Hall - Room 1", 12, 1),
            RoomEntity("R_YAA_102", "H_YAA", "Queens Hall - Room 2", 12, 1),
            RoomEntity("R_JAK_101", "H_JAK", "Savanna Dorm - Room 1", 12, 1)
        )
        database.roomDao().insertRooms(rooms)

        // 3. Students
        val students = listOf(
            Student(
                id = "STU_001",
                studentId = "SHS/2024/0481",
                fullName = "Kwame Asante",
                gender = "MALE",
                classForm = "SHS 2",
                program = "General Science",
                houseId = "H_AGG",
                roomId = "R_AGG_101",
                bedNumber = 1,
                guardianName = "Dr. Kofi Asante",
                guardianPhone = "+233 24 555 1201",
                guardianRelationship = "Father",
                status = "IN_DORM",
                qrCodeKey = "SHS-0481-ASANTE-2024",
                emergencyNotes = "Mild asthma, has inhaler"
            ),
            Student(
                id = "STU_002",
                studentId = "SHS/2024/0482",
                fullName = "Kofi Osei-Tutu",
                gender = "MALE",
                classForm = "SHS 3",
                program = "Business",
                houseId = "H_AGG",
                roomId = "R_AGG_101",
                bedNumber = 2,
                guardianName = "Mrs. Yaa Osei",
                guardianPhone = "+233 20 444 9831",
                guardianRelationship = "Mother",
                status = "ON_EXEAT",
                qrCodeKey = "SHS-0482-TUTU-2024",
                emergencyNotes = "None"
            ),
            Student(
                id = "STU_003",
                studentId = "SHS/2024/0483",
                fullName = "Emmanuel Kyeremeh",
                gender = "MALE",
                classForm = "SHS 1",
                program = "General Arts",
                houseId = "H_AGG",
                roomId = "R_AGG_101",
                bedNumber = 3,
                guardianName = "Mr. Paul Kyeremeh",
                guardianPhone = "+233 27 333 4455",
                guardianRelationship = "Father",
                status = "IN_DORM",
                qrCodeKey = "SHS-0483-KYEREMEH-2024"
            ),
            Student(
                id = "STU_004",
                studentId = "SHS/2024/0484",
                fullName = "Yaw Frimpong",
                gender = "MALE",
                classForm = "SHS 2",
                program = "Visual Arts",
                houseId = "H_AGG",
                roomId = "R_AGG_101",
                bedNumber = 4,
                guardianName = "Rev. Stephen Frimpong",
                guardianPhone = "+233 24 888 7766",
                guardianRelationship = "Father",
                status = "SICK_BAY",
                qrCodeKey = "SHS-0484-FRIMPONG-2024",
                emergencyNotes = "Admitted to school infirmary with fever"
            ),
            Student(
                id = "STU_005",
                studentId = "SHS/2024/0485",
                fullName = "Samuel Kwesi Addo",
                gender = "MALE",
                classForm = "SHS 3",
                program = "General Science",
                houseId = "H_AGG",
                roomId = "R_AGG_102",
                bedNumber = 1,
                guardianName = "Mrs. Rebecca Addo",
                guardianPhone = "+233 20 111 2233",
                guardianRelationship = "Mother",
                status = "IN_DORM",
                qrCodeKey = "SHS-0485-ADDO-2024"
            ),
            Student(
                id = "STU_006",
                studentId = "SHS/2024/0486",
                fullName = "Justice Boakye-Yiadom",
                gender = "MALE",
                classForm = "SHS 2",
                program = "Business",
                houseId = "H_AGG",
                roomId = "R_AGG_102",
                bedNumber = 2,
                guardianName = "Mr. Albert Boakye",
                guardianPhone = "+233 24 777 9900",
                guardianRelationship = "Guardian",
                status = "ABSENT",
                qrCodeKey = "SHS-0486-BOAKYE-2024",
                emergencyNotes = "Unexcused absence during roll call"
            ),
            Student(
                id = "STU_007",
                studentId = "SHS/2024/0491",
                fullName = "Bright Oppong Darko",
                gender = "MALE",
                classForm = "SHS 3",
                program = "General Science",
                houseId = "H_GUG",
                roomId = "R_GUG_101",
                bedNumber = 1,
                guardianName = "Mr. Isaac Darko",
                guardianPhone = "+233 26 555 4411",
                guardianRelationship = "Father",
                status = "IN_DORM",
                qrCodeKey = "SHS-0491-DARKO-2024"
            ),
            Student(
                id = "STU_008",
                studentId = "SHS/2024/0492",
                fullName = "Daniel Nana Prempeh",
                gender = "MALE",
                classForm = "SHS 2",
                program = "General Arts",
                houseId = "H_GUG",
                roomId = "R_GUG_101",
                bedNumber = 2,
                guardianName = "Nana Prempeh II",
                guardianPhone = "+233 24 333 1188",
                guardianRelationship = "Uncle",
                status = "ON_EXEAT",
                qrCodeKey = "SHS-0492-PREMPEH-2024"
            ),
            Student(
                id = "STU_009",
                studentId = "SHS/2024/0501",
                fullName = "Abena Serwaa Mensah",
                gender = "FEMALE",
                classForm = "SHS 3",
                program = "General Science",
                houseId = "H_YAA",
                roomId = "R_YAA_101",
                bedNumber = 1,
                guardianName = "Mrs. Faustina Mensah",
                guardianPhone = "+233 24 666 8822",
                guardianRelationship = "Mother",
                status = "IN_DORM",
                qrCodeKey = "SHS-0501-MENSAH-2024"
            ),
            Student(
                id = "STU_010",
                studentId = "SHS/2024/0502",
                fullName = "Ama Darkoa Agyeman",
                gender = "FEMALE",
                classForm = "SHS 2",
                program = "Home Economics",
                houseId = "H_YAA",
                roomId = "R_YAA_101",
                bedNumber = 2,
                guardianName = "Mr. Bernard Agyeman",
                guardianPhone = "+233 20 999 4433",
                guardianRelationship = "Father",
                status = "IN_DORM",
                qrCodeKey = "SHS-0502-AGYEMAN-2024"
            ),
            Student(
                id = "STU_011",
                studentId = "SHS/2024/0503",
                fullName = "Deborah Afia Appiah",
                gender = "FEMALE",
                classForm = "SHS 1",
                program = "General Arts",
                houseId = "H_YAA",
                roomId = "R_YAA_101",
                bedNumber = 3,
                guardianName = "Rev. Eric Appiah",
                guardianPhone = "+233 24 222 6677",
                guardianRelationship = "Father",
                status = "IN_DORM",
                qrCodeKey = "SHS-0503-APPIAH-2024"
            ),
            Student(
                id = "STU_012",
                studentId = "SHS/2024/0511",
                fullName = "Haruna Baba Abdulai",
                gender = "MALE",
                classForm = "SHS 3",
                program = "General Arts",
                houseId = "H_JAK",
                roomId = "R_JAK_101",
                bedNumber = 1,
                guardianName = "Mallam Abdulai",
                guardianPhone = "+233 27 888 1234",
                guardianRelationship = "Father",
                status = "IN_DORM",
                qrCodeKey = "SHS-0511-ABDULAI-2024"
            )
        )
        database.studentDao().insertStudents(students)

        // 4. Exeats
        val exeats = listOf(
            ExeatRequest(
                id = "EX_2024_001",
                studentId = "STU_002",
                studentName = "Kofi Osei-Tutu",
                studentIndexNumber = "SHS/2024/0482",
                houseId = "H_AGG",
                category = "MEDICAL",
                reason = "Specialist ophthalmology appointment at Regional Hospital",
                destination = "Kumasi Regional Hospital",
                departureTime = now - (3 * oneHour),
                expectedReturnTime = now + (4 * oneHour),
                actualDepartureTime = now - (3 * oneHour),
                status = "DEPARTED",
                approvedBy = "Mr. Kwabena Boateng (Housemaster)",
                conditionNotes = "Must present doctor's certified medical slip upon return"
            ),
            ExeatRequest(
                id = "EX_2024_002",
                studentId = "STU_008",
                studentName = "Daniel Nana Prempeh",
                studentIndexNumber = "SHS/2024/0492",
                houseId = "H_GUG",
                category = "FAMILY_EMERGENCY",
                reason = "Family bereavement service in Obuasi",
                destination = "Obuasi Central",
                departureTime = now - (5 * oneHour),
                expectedReturnTime = now + (20 * oneHour),
                actualDepartureTime = now - (5 * oneHour),
                status = "DEPARTED",
                approvedBy = "Mr. Ebenezer Osei (Housemaster)",
                conditionNotes = "Guardian verified via phone"
            ),
            ExeatRequest(
                id = "EX_2024_003",
                studentId = "STU_001",
                studentName = "Kwame Asante",
                studentIndexNumber = "SHS/2024/0481",
                houseId = "H_AGG",
                category = "ACADEMIC",
                reason = "Representing school at National Science & Maths Quiz regional mock",
                destination = "KNUST Campus, Kumasi",
                departureTime = now + (12 * oneHour),
                expectedReturnTime = now + (36 * oneHour),
                status = "PENDING",
                conditionNotes = "Science Dept coordinator escort required"
            )
        )
        database.exeatDao().insertExeats(exeats)

        // 5. Gate Movements
        val movements = listOf(
            GateMovement(
                id = "MOV_001",
                studentId = "STU_002",
                studentName = "Kofi Osei-Tutu",
                studentIndexNumber = "SHS/2024/0482",
                houseName = "Aggrey House",
                exeatId = "EX_2024_001",
                movementType = "DEPARTURE",
                timestamp = now - (3 * oneHour),
                officerName = "Officer J. Mensah",
                gateName = "Main School Gate",
                isAuthorized = true,
                remarks = "Departed via approved medical pass. Accompanied by guardian."
            ),
            GateMovement(
                id = "MOV_002",
                studentId = "STU_008",
                studentName = "Daniel Nana Prempeh",
                studentIndexNumber = "SHS/2024/0492",
                houseName = "Guggisberg House",
                exeatId = "EX_2024_002",
                movementType = "DEPARTURE",
                timestamp = now - (5 * oneHour),
                officerName = "Officer K. Donkor",
                gateName = "Main School Gate",
                isAuthorized = true,
                remarks = "Exeat verified via QR code scan."
            )
        )
        database.gateMovementDao().insertMovements(movements)

        // 6. Audit Logs
        val auditLogs = listOf(
            AuditLog(
                action = "SYSTEM_INITIALIZATION",
                actorRole = "Administrator",
                actorName = "Senior Housemaster",
                details = "SHS Boarding system seeded with 4 houses, 8 rooms, and 12 active student records.",
                timestamp = now - oneDay
            ),
            AuditLog(
                action = "EXEAT_APPROVED",
                actorRole = "Housemaster",
                actorName = "Mr. Kwabena Boateng",
                details = "Approved Medical Exeat EX_2024_001 for student Kofi Osei-Tutu (SHS/2024/0482).",
                timestamp = now - (4 * oneHour)
            ),
            AuditLog(
                action = "GATE_EXIT_VERIFIED",
                actorRole = "Gate Officer",
                actorName = "Officer J. Mensah",
                details = "Student Kofi Osei-Tutu scanned and cleared through Main Gate with valid Medical pass.",
                timestamp = now - (3 * oneHour)
            )
        )
        database.auditDao().insertAuditLogs(auditLogs)
    }
}
