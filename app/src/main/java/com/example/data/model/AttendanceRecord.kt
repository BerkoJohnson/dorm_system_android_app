package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey val id: String,
    val dateStr: String,
    val sessionType: String, // "EVENING_ROLL_CALL", "MORNING_INSPECTION", "CURFEW_CHECK"
    val houseId: String,
    val roomId: String,
    val studentId: String,
    val studentName: String,
    val status: String, // "PRESENT", "ABSENT", "ON_EXEAT", "SICK_BAY", "PERMITTED"
    val markedBy: String,
    val markedAt: Long = System.currentTimeMillis(),
    val remarks: String = ""
)
