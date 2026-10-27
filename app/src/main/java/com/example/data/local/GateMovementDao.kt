package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GateMovement
import kotlinx.coroutines.flow.Flow

@Dao
interface GateMovementDao {
    @Query("SELECT * FROM gate_movements ORDER BY timestamp DESC")
    fun getAllMovements(): Flow<List<GateMovement>>

    @Query("SELECT * FROM gate_movements ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMovements(limit: Int = 100): Flow<List<GateMovement>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMovement(movement: GateMovement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<GateMovement>)

    @Query("UPDATE gate_movements SET syncStatus = :status, syncError = :error WHERE id = :id")
    suspend fun updateSyncState(id: String, status: String, error: String?)

    @Query("DELETE FROM gate_movements")
    suspend fun clear()
}
