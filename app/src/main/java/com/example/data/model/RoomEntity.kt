package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String, // e.g. "R_AGG_101"
    val houseId: String,
    val dormName: String, // e.g. "Block A - Room 1"
    val capacity: Int,
    val floor: Int
)
