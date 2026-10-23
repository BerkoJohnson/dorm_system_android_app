package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.RoomEntity
import com.example.data.model.Student

@Database(
    entities = [
        House::class,
        RoomEntity::class,
        Student::class,
        ExeatRequest::class,
        AttendanceRecord::class,
        GateMovement::class,
        AuditLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun houseDao(): HouseDao
    abstract fun roomDao(): RoomDao
    abstract fun studentDao(): StudentDao
    abstract fun exeatDao(): ExeatDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun gateMovementDao(): GateMovementDao
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shs_boarding_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
