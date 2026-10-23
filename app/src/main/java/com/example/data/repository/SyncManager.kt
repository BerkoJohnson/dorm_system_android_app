package com.example.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncState(
    val apiBaseUrl: String = "https://shs-boarding.school.edu.gh/api/v1",
    val apiToken: String = "Bearer shs_token_demo_9823",
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = System.currentTimeMillis() - 15 * 60 * 1000L, // 15 mins ago
    val pendingOutboxCount: Int = 0,
    val syncStatusMessage: String = "Synchronized with school backend"
)

class SyncManager {
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun updateConfig(url: String, token: String) {
        _syncState.value = _syncState.value.copy(
            apiBaseUrl = url.trim(),
            apiToken = token.trim(),
            syncStatusMessage = "Configuration saved. Ready to sync."
        )
    }

    suspend fun performSync(): Result<String> {
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            syncStatusMessage = "Connecting to ${_syncState.value.apiBaseUrl}..."
        )

        // Simulate network handshake and delta sync
        delay(1200)

        val success = true
        return if (success) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTime = System.currentTimeMillis(),
                pendingOutboxCount = 0,
                syncStatusMessage = "Sync complete. All records up to date."
            )
            Result.success("Sync successful")
        } else {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                syncStatusMessage = "Sync failed: Server unreachable. Using local cache."
            )
            Result.failure(Exception("Server unreachable"))
        }
    }
}
