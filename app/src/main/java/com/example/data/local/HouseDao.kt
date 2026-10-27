package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.House
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseDao {
    @Query("SELECT * FROM houses ORDER BY name ASC")
    fun getAllHouses(): Flow<List<House>>

    @Query("SELECT COUNT(*) FROM houses")
    suspend fun countHouses(): Int

    @Query("SELECT * FROM houses WHERE id = :id LIMIT 1")
    fun getHouseById(id: String): Flow<House?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHouses(houses: List<House>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHouse(house: House)

    @Query("DELETE FROM houses")
    suspend fun clear()
}
