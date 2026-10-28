package com.example.data.local

import android.content.Context
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * One-time migration from the unencrypted Room database ("shs_boarding_database") to the
 * encrypted one ("shs_boarding_secure").
 *
 * It follows the documented SQLCipher export recipe: open the legacy plaintext file with an
 * empty password, attach the new encrypted file, run sqlcipher_export() to copy the whole
 * schema and data set inside one transaction, then detach and delete the plaintext files.
 * Copying at the SQLite layer preserves queued outbox items and the Room schema version
 * (room_master_table and PRAGMA user_version), so Room opens the exported file without a
 * destructive re-creation.
 *
 * If anything fails, the partial encrypted file is deleted and the error is logged but not
 * rethrown: Room then builds a fresh database, and the school data re-syncs from the server.
 */
object PlaintextDatabaseMigrator {
    fun migrateIfNeeded(
        context: Context,
        secureDatabaseName: String,
        legacyDatabaseName: String,
        passphrase: ByteArray
    ): Boolean {
        val legacyFile = context.getDatabasePath(legacyDatabaseName)
        if (!legacyFile.exists()) return false
        val secureFile = context.getDatabasePath(secureDatabaseName)
        try {
            val keyHex = passphrase.joinToString("") { "%02x".format(it) }
            val legacy = SQLiteDatabase.openDatabase(
                legacyFile.absolutePath,
                "", // empty key: opens an unencrypted database
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null
            )
            try {
                val schemaVersion = legacy.version
                legacy.rawExecSQL(
                    "ATTACH DATABASE '${secureFile.absolutePath}' AS secure KEY \"x'$keyHex'\""
                )
                try {
                    legacy.rawExecSQL("SELECT sqlcipher_export('secure')")
                    // sqlcipher_export() does not copy user_version; Room stores the schema
                    // version there, so carry it over explicitly.
                    legacy.rawExecSQL("PRAGMA secure.user_version = $schemaVersion")
                } finally {
                    legacy.rawExecSQL("DETACH DATABASE secure")
                }
            } finally {
                legacy.close()
            }
            // Leave no readable plaintext or journal sidecars behind.
            deleteWithSidecars(legacyFile)
            return true
        } catch (error: Exception) {
            android.util.Log.w(
                TAG,
                "Plaintext database migration failed; starting from an empty encrypted database.",
                error
            )
            deleteWithSidecars(secureFile)
            return false
        }
    }

    fun legacyFiles(context: Context, legacyDatabaseName: String): List<File> = listOf(
        context.getDatabasePath(legacyDatabaseName),
        File(context.getDatabasePath(legacyDatabaseName).path + "-wal"),
        File(context.getDatabasePath(legacyDatabaseName).path + "-shm"),
        File(context.getDatabasePath(legacyDatabaseName).path + "-journal")
    ).filter { it.exists() }

    private fun deleteWithSidecars(file: File) {
        SQLiteDatabase.deleteDatabase(file)
    }

    private const val TAG = "PlaintextDbMigrator"
}
