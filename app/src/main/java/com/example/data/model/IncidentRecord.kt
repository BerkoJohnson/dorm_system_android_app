package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A boarding incident: injury, illness, misconduct, bullying, property damage, security
 * event, or any safety concern raised by a housemaster, tutor, matron, medical lead, or
 * the gate. Designed to be recorded even with no connection: the row is saved locally
 * first and delivered to the school server through the delivery outbox.
 */
@Entity(tableName = "incident_records")
data class IncidentRecord(
    @PrimaryKey val id: String,
    /** Staff account that created the record; scopes it to this account on this device. */
    val ownerId: String,
    val tenantUrl: String,
    val houseId: String,
    /** Primary student involved; blank for campus-wide events (drill, power failure, etc.). */
    val studentId: String,
    val studentName: String,
    val studentIndexNumber: String,
    // "INJURY", "ILLNESS", "MISCONDUCT", "BULLYING", "PROPERTY", "SECURITY", "OTHER"
    val category: String,
    // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val severity: String,
    val location: String,
    val description: String,
    val immediateAction: String,
    val guardianContacted: Boolean,
    val guardianContactPhone: String,
    val guardianContactTime: String,
    val medicalReferral: Boolean,
    // "OPEN", "UNDER_REVIEW", "CLOSED"
    val status: String = "OPEN",
    val resolutionNotes: String = "",
    val reportedByName: String,
    val createdAt: Long = System.currentTimeMillis(),
    // "PENDING_SYNC", "SYNCED", "FAILED"
    val syncStatus: String = "PENDING_SYNC",
    val syncError: String? = null
)
