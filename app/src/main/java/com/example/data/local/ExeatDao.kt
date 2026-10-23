package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ExeatRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface ExeatDao {
    @Query("SELECT * FROM exeat_requests ORDER BY createdAt DESC")
    fun getAllExeats(): Flow<List<ExeatRequest>>

    @Query("SELECT * FROM exeat_requests WHERE houseId = :houseId ORDER BY createdAt DESC")
    fun getExeatsByHouse(houseId: String): Flow<List<ExeatRequest>>

    @Query("SELECT * FROM exeat_requests WHERE studentId = :studentId AND status IN ('PENDING', 'APPROVED', 'DEPARTED') ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveExeatForStudent(studentId: String): ExeatRequest?

    @Query("SELECT * FROM exeat_requests WHERE id = :id LIMIT 1")
    fun getExeatById(id: String): Flow<ExeatRequest?>

    @Query("SELECT * FROM exeat_requests WHERE id = :id LIMIT 1")
    suspend fun getExeatByIdDirect(id: String): ExeatRequest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExeat(exeat: ExeatRequest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExeats(exeats: List<ExeatRequest>)

    @Update
    suspend fun updateExeat(exeat: ExeatRequest)
}
