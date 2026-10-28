package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.data.model.IncidentRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incident_records ORDER BY createdAt DESC, id ASC")
    fun getAllIncidents(): Flow<List<IncidentRecord>>

    @Query("SELECT * FROM incident_records WHERE id = :id")
    suspend fun getById(id: String): IncidentRecord?

    @Query("UPDATE incident_records SET status = :status, resolutionNotes = :notes WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, notes: String)

    @Query("UPDATE incident_records SET syncStatus = :status, syncError = :error WHERE id = :id")
    suspend fun updateSyncState(id: String, status: String, error: String? = null)

    @Upsert
    suspend fun upsert(incident: IncidentRecord)

    @Query("DELETE FROM incident_records WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl")
    suspend fun clearFor(ownerId: String, tenantUrl: String)

    @Query("DELETE FROM incident_records")
    suspend fun clear()
}
