package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RollCallOutboxItem

@Dao
interface RollCallOutboxDao {
    @Query("SELECT * FROM roll_call_outbox ORDER BY createdAt")
    suspend fun getAll(): List<RollCallOutboxItem>

    @Query(
        "SELECT COUNT(*) FROM roll_call_outbox " +
            "WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl"
    )
    suspend fun countFor(ownerId: String, tenantUrl: String): Int

    @Insert
    suspend fun insert(item: RollCallOutboxItem)

    @Update
    suspend fun update(item: RollCallOutboxItem)

    @Query("DELETE FROM roll_call_outbox WHERE id = :id")
    suspend fun delete(id: String)
}
