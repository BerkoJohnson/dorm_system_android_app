package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
        "SELECT * FROM remote_mutation_outbox WHERE id = :id " +
            "AND ownerId = :ownerId AND tenantUrl = :tenantUrl LIMIT 1"
    )
    suspend fun getById(id: String, ownerId: String, tenantUrl: String): RemoteMutationOutboxItem?

    @Query(
        "SELECT * FROM remote_mutation_outbox WHERE ownerId = :ownerId " +
            "AND tenantUrl = :tenantUrl AND deliveryStatus = 'PENDING' ORDER BY createdAt"
    )
    suspend fun getPendingFor(ownerId: String, tenantUrl: String): List<RemoteMutationOutboxItem>

    @Query(
        "SELECT COUNT(*) FROM remote_mutation_outbox " +
            "WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl AND deliveryStatus = 'PENDING'"
    )
    suspend fun countFor(ownerId: String, tenantUrl: String): Int

    @Query(
        "SELECT COUNT(*) FROM remote_mutation_outbox " +
            "WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl AND deliveryStatus = 'FAILED'"
    )
    suspend fun countFailedFor(ownerId: String, tenantUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: RemoteMutationOutboxItem): Long

    @Update
    suspend fun update(item: RemoteMutationOutboxItem)

    @Query("DELETE FROM remote_mutation_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "DELETE FROM remote_mutation_outbox WHERE id = :id " +
            "AND ownerId = :ownerId AND tenantUrl = :tenantUrl"
    )
    suspend fun deleteFor(id: String, ownerId: String, tenantUrl: String): Int
}
