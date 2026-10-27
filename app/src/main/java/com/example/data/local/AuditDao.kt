package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Query(
        "SELECT * FROM audit_logs WHERE ownerId = :ownerId AND tenantUrl = :tenantUrl " +
            "ORDER BY timestamp DESC LIMIT 200"
    )
    fun getAuditLogs(ownerId: String, tenantUrl: String): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<AuditLog>)

    @Query("DELETE FROM audit_logs")
    suspend fun clear()
}
