package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RollCallOutboxItem

@Dao
interface RollCallOutboxDao {
    @Query("SELECT * FROM roll_call_outbox ORDER BY createdAt")
    suspend fun getAll(): List<RollCallOutboxItem>

    @Query(
        "SELECT * FROM roll_call_outbox WHERE id = :id " +
            "AND ownerId = :ownerId AND tenantUrl = :tenantUrl LIMIT 1"
    )
    suspend fun getById(id: String, ownerId: String, tenantUrl: String): RollCallOutboxItem?

    @Query(
        "SELECT * FROM roll_call_outbox WHERE ownerId = :ownerId " +
            "AND tenantUrl = :tenantUrl AND deliveryStatus = 'PENDING' ORDER BY createdAt"
    )
    suspend fun getPendingFor(ownerId: String, tenantUrl: String): List<RollCallOutboxItem>

    @Query(
        "SELECT COUNT(*) FROM roll_call_outbox " +
            "WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl AND deliveryStatus = 'PENDING'"
    )
    suspend fun countFor(ownerId: String, tenantUrl: String): Int

    @Query(
        "SELECT COUNT(*) FROM roll_call_outbox " +
            "WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl AND deliveryStatus = 'FAILED'"
    )
    suspend fun countFailedFor(ownerId: String, tenantUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: RollCallOutboxItem): Long

    @Update
    suspend fun update(item: RollCallOutboxItem)

    @Query("DELETE FROM roll_call_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "DELETE FROM roll_call_outbox WHERE id = :id " +
            "AND ownerId = :ownerId AND tenantUrl = :tenantUrl"
    )
    suspend fun deleteFor(id: String, ownerId: String, tenantUrl: String): Int
}
