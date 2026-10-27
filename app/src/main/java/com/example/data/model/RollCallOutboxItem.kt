package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "roll_call_outbox")
data class RollCallOutboxItem(
    @PrimaryKey val id: String,
    val ownerId: String,
    val tenantUrl: String,
    val houseId: String,
    val dormitoryId: String,
    val sessionType: String,
    val inspectionDate: String,
    val statusesJson: String,
    @ColumnInfo(defaultValue = "''")
    val remarksJson: String = "",
    val remoteSessionId: String?,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'PENDING'")
    val deliveryStatus: String = "PENDING"
)
