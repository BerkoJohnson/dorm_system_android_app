package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY fullName ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE houseId = :houseId ORDER BY fullName ASC")
    fun getStudentsByHouse(houseId: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE roomId = :roomId ORDER BY bedNumber ASC")
    fun getStudentsByRoom(roomId: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    fun getStudentById(id: String): Flow<Student?>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentByIdDirect(id: String): Student?

    @Query("SELECT * FROM students WHERE qrCodeKey = :qrKey LIMIT 1")
    suspend fun getStudentByQrKey(qrKey: String): Student?

    @Query("SELECT * FROM students WHERE studentId = :indexNumber LIMIT 1")
    suspend fun getStudentByIndexNumber(indexNumber: String): Student?

    @Query("UPDATE students SET status = :status WHERE id = :id")
    suspend fun updateStudentStatus(id: String, status: String)

    @Query("UPDATE students SET roomId = :newRoomId, bedNumber = :newBedNumber WHERE id = :id")
    suspend fun updateStudentRoomAllocation(id: String, newRoomId: String, newBedNumber: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student)

    @Update
    suspend fun updateStudent(student: Student)

    @Query("SELECT COUNT(*) FROM students")
    suspend fun getStudentCount(): Int

    @Query("DELETE FROM students")
    suspend fun clear()
}
