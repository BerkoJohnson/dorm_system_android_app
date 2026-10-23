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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: GateMovement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<GateMovement>)
}
