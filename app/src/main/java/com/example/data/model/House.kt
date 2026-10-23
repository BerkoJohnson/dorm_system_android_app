package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "houses")
data class House(
    @PrimaryKey val id: String, // e.g. "H1", "H2", "H3", "H4"
    val name: String, // e.g. "Aggrey House", "Guggisberg House"
    val gender: String, // "MALE", "FEMALE", "CO-ED"
    val housemasterName: String,
    val housemasterPhone: String,
    val capacity: Int,
    val colorHex: String,
    val motto: String
)
