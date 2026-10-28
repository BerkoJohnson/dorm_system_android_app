package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.IncidentRecord
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
        IncidentRecord::class,
        RollCallOutboxItem::class,
        RemoteMutationOutboxItem::class
    ],
    version = 10,
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
    abstract fun incidentDao(): IncidentDao
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

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS incident_records (
                        id TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        tenantUrl TEXT NOT NULL,
                        houseId TEXT NOT NULL,
                        studentId TEXT NOT NULL,
                        studentName TEXT NOT NULL,
                        studentIndexNumber TEXT NOT NULL,
                        category TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        location TEXT NOT NULL,
                        description TEXT NOT NULL,
                        immediateAction TEXT NOT NULL,
                        guardianContacted INTEGER NOT NULL,
                        guardianContactPhone TEXT NOT NULL,
                        guardianContactTime TEXT NOT NULL,
                        medicalReferral INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        resolutionNotes TEXT NOT NULL,
                        reportedByName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL,
                        syncError TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val LEGACY_PLAINTEXT_DB = "shs_boarding_database"
        private const val SECURE_DB = "shs_boarding_secure"

        @Volatile
        private var sqlCipherLibraryLoaded = false

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val secureFile = appContext.getDatabasePath(SECURE_DB)
                val legacyFile = appContext.getDatabasePath(LEGACY_PLAINTEXT_DB)
                val keyResult = DatabaseKeyManager.getOrCreatePassphrase(appContext)
                if (keyResult.regenerated) {
                    // The keystore lost its master key, so the previous encrypted file can
                    // never be opened again. Start fresh instead of crashing on first query.
                    SQLiteDatabase.deleteDatabase(secureFile)
                }
                loadSqlCipherLibrary()
                when {
                    !secureFile.exists() ->
                        PlaintextDatabaseMigrator.migrateIfNeeded(
                            appContext,
                            SECURE_DB,
                            LEGACY_PLAINTEXT_DB,
                            keyResult.passphrase
                        )
                    legacyFile.exists() ->
                        // A previous migration completed but left plaintext sidecars
                        // behind; remove them now.
                        PlaintextDatabaseMigrator.legacyFiles(
                            appContext,
                            LEGACY_PLAINTEXT_DB
                        ).forEach { it.delete() }
                }

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    SECURE_DB
                )
                    .openHelperFactory(SupportOpenHelperFactory(keyResult.passphrase))
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun loadSqlCipherLibrary() {
            if (sqlCipherLibraryLoaded) return
            synchronized(this) {
                if (!sqlCipherLibraryLoaded) {
                    // Must run before any net.zetetic.database call in the process.
                    System.loadLibrary("sqlcipher")
                    sqlCipherLibraryLoaded = true
                }
            }
        }
    }
}
