package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val actorRole: String,
    val actorName: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ownerId: String = "",
    val tenantUrl: String = ""
)
