package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "gate_movements")
data class GateMovement(
    @PrimaryKey val id: String,
    val studentId: String,
    val studentName: String,
    val studentIndexNumber: String,
    val houseName: String,
    val exeatId: String?,
    val movementType: String, // "DEPARTURE", "ARRIVAL"
    val timestamp: Long = System.currentTimeMillis(),
    val officerName: String,
    val gateName: String = "Main School Gate",
    val isAuthorized: Boolean = true,
    val remarks: String = "",
    @ColumnInfo(defaultValue = "'SYNCED'")
    val syncStatus: String = "SYNCED",
    val syncError: String? = null
)
