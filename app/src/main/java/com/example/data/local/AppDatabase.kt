package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.RoomEntity
import com.example.data.model.RollCallOutboxItem
import com.example.data.model.RemoteMutationOutboxItem
import com.example.data.model.Student

@Database(
    entities = [
        House::class,
        RoomEntity::class,
        Student::class,
        ExeatRequest::class,
        AttendanceRecord::class,
        GateMovement::class,
        AuditLog::class,
        RollCallOutboxItem::class,
        RemoteMutationOutboxItem::class
    ],
    version = 9,
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
    abstract fun rollCallOutboxDao(): RollCallOutboxDao
    abstract fun remoteMutationOutboxDao(): RemoteMutationOutboxDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE students ADD COLUMN assignedRoomId TEXT DEFAULT NULL"
                )
                db.execSQL(
                    "ALTER TABLE students ADD COLUMN assignedRoomName TEXT NOT NULL DEFAULT ''"
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS roll_call_outbox (
                        id TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        tenantUrl TEXT NOT NULL,
                        houseId TEXT NOT NULL,
                        dormitoryId TEXT NOT NULL,
                        sessionType TEXT NOT NULL,
                        inspectionDate TEXT NOT NULL,
                        statusesJson TEXT NOT NULL,
                        remoteSessionId TEXT,
                        attempts INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS remote_mutation_outbox (
                        id TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        tenantUrl TEXT NOT NULL,
                        path TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE gate_movements ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'"
                )
                db.execSQL("ALTER TABLE gate_movements ADD COLUMN syncError TEXT")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE remote_mutation_outbox " +
                        "ADD COLUMN deliveryStatus TEXT NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL(
                    "ALTER TABLE roll_call_outbox " +
                        "ADD COLUMN deliveryStatus TEXT NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL(
                    "UPDATE remote_mutation_outbox SET deliveryStatus = 'FAILED' " +
                        "WHERE lastError IS NOT NULL"
                )
                db.execSQL(
                    "UPDATE roll_call_outbox SET deliveryStatus = 'FAILED' " +
                        "WHERE lastError LIKE 'REJECTED: %'"
                )
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE roll_call_outbox ADD COLUMN remarksJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE students ADD COLUMN photoUrl TEXT NOT NULL DEFAULT ''"
                )
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE audit_logs ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE audit_logs ADD COLUMN tenantUrl TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shs_boarding_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
