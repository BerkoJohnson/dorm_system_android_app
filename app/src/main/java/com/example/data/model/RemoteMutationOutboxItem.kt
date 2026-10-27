package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "remote_mutation_outbox")
data class RemoteMutationOutboxItem(
    @PrimaryKey val id: String,
    val ownerId: String,
    val tenantUrl: String,
    val path: String,
    val payloadJson: String,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "'PENDING'")
    val deliveryStatus: String = "PENDING"
)
