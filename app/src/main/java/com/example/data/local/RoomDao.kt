package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.RoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY houseId, dormName ASC")
    fun getAllRooms(): Flow<List<RoomEntity>>

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun countRooms(): Int

    @Query("SELECT * FROM rooms WHERE houseId = :houseId ORDER BY dormName ASC")
    fun getRoomsByHouse(houseId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE id = :id LIMIT 1")
    fun getRoomById(id: String): Flow<RoomEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(rooms: List<RoomEntity>)

    @Query("DELETE FROM rooms")
    suspend fun clear()
}
