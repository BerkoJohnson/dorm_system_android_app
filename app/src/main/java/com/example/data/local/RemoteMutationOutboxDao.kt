package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RemoteMutationOutboxItem

@Dao
interface RemoteMutationOutboxDao {
    @Query(
        "SELECT * FROM remote_mutation_outbox " +
            "WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl ORDER BY createdAt"
    )
    suspend fun getFor(ownerId: String, tenantUrl: String): List<RemoteMutationOutboxItem>

    @Query(
        "SELECT COUNT(*) FROM remote_mutation_outbox " +
            "WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl"
    )
    suspend fun countFor(ownerId: String, tenantUrl: String): Int

    @Insert
    suspend fun insert(item: RemoteMutationOutboxItem)

    @Update
    suspend fun update(item: RemoteMutationOutboxItem)

    @Query("DELETE FROM remote_mutation_outbox WHERE id = :id")
    suspend fun delete(id: String)
}
