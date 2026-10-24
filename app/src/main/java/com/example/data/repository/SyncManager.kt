package com.example.data.repository

import com.example.data.remote.DjangoApiClient
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncState(
    val apiBaseUrl: String,
    val isOnline: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val pendingOutboxCount: Int = 0,
    val syncStatusMessage: String = "Sign in to connect to the school server."
)

class SyncManager(private val apiClient: DjangoApiClient) {
    private val _syncState = MutableStateFlow(SyncState(apiBaseUrl = apiClient.baseUrl))
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun updateConfig(url: String) {
        _syncState.value = _syncState.value.copy(
            apiBaseUrl = url,
            syncStatusMessage = "Using the configured school server."
        )
    }

    fun reportActionError(message: String) {
        _syncState.value = _syncState.value.copy(
            isOnline = false,
            syncStatusMessage = message
        )
    }

    fun reportActionSuccess(message: String) {
        _syncState.value = _syncState.value.copy(
            isOnline = true,
            syncStatusMessage = message
        )
    }

    suspend fun performSync(sync: suspend () -> Unit) {
        _syncState.value = _syncState.value.copy(
            isSyncing = true,
            isOnline = false,
            syncStatusMessage = "Connecting to ${_syncState.value.apiBaseUrl}..."
        )
        try {
            sync()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isOnline = true,
                lastSyncTime = System.currentTimeMillis(),
                syncStatusMessage = "Connected. School data refreshed."
            )
        } catch (error: IOException) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isOnline = false,
                syncStatusMessage = "Sync failed: ${error.message ?: "School server unavailable."}"
            )
            throw error
        } catch (error: IllegalStateException) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isOnline = false,
                syncStatusMessage = "Sync failed: ${error.message ?: "Invalid server data."}"
            )
            throw error
        }
    }
}
