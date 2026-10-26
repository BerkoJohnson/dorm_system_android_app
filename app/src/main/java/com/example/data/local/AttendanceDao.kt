package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE dateStr = :dateStr AND houseId = :houseId AND sessionType = :sessionType")
    fun getAttendanceByDateAndHouse(dateStr: String, houseId: String, sessionType: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE dateStr = :dateStr AND roomId = :roomId AND sessionType = :sessionType")
    fun getAttendanceByRoom(dateStr: String, roomId: String, sessionType: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records ORDER BY markedAt DESC LIMIT 200")
    fun getRecentAttendance(): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_records WHERE dateStr = :dateStr AND roomId = :roomId AND sessionType = :sessionType")
    suspend fun deleteAttendanceForRoomSession(dateStr: String, roomId: String, sessionType: String)

    @Query("DELETE FROM attendance_records")
    suspend fun clear()
}
