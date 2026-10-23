package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exeat_requests")
data class ExeatRequest(
    @PrimaryKey val id: String,
    val studentId: String,
    val studentName: String,
    val studentIndexNumber: String,
    val houseId: String,
    val category: String, // "MEDICAL", "ACADEMIC", "FAMILY_EMERGENCY", "TOWN_PASS", "SPECIAL_DUTY"
    val reason: String,
    val destination: String,
    val departureTime: Long,
    val expectedReturnTime: Long,
    val actualDepartureTime: Long? = null,
    val actualReturnTime: Long? = null,
    val status: String = "PENDING", // "PENDING", "APPROVED", "REJECTED", "DEPARTED", "RETURNED", "OVERDUE", "CANCELLED"
    val approvedBy: String? = null,
    val rejectionReason: String? = null,
    val conditionNotes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
