package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey val id: String, // e.g. "STU_001"
    val studentId: String, // Official Index Number e.g. "SHS/2024/0481"
    val fullName: String,
    val gender: String, // "MALE" or "FEMALE"
    val classForm: String, // "SHS 1", "SHS 2", "SHS 3"
    val program: String, // "General Science", "General Arts", "Business", etc.
    val houseId: String,
    val roomId: String,
    val bedNumber: Int,
    val guardianName: String,
    val guardianPhone: String,
    val guardianRelationship: String,
    val status: String = "IN_DORM", // "IN_DORM", "ON_EXEAT", "SICK_BAY", "ABSENT"
    val qrCodeKey: String, // Verification token embedded in QR
    val emergencyNotes: String = "None"
)
