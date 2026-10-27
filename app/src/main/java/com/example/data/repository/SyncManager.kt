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
    val failedOutboxCount: Int = 0,
    val syncStatusMessage: String = "Sign in to connect to the school server."
)

class SyncManager(private val apiClient: DjangoApiClient) {
    private val _syncState = MutableStateFlow(
        SyncState(
            apiBaseUrl = apiClient.baseUrl,
            lastSyncTime = apiClient.lastSuccessfulSyncAt
        )
    )
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

    fun reportOffline() {
        _syncState.value = _syncState.value.copy(
            isOnline = false,
            syncStatusMessage =
                "Offline. Cached data is available; queued changes will retry when connected."
        )
    }

    fun reportOfflineGraceExpired() {
        _syncState.value = _syncState.value.copy(
            isOnline = false,
            syncStatusMessage =
                "Offline access expired. Reconnect to revalidate before using protected workflows."
        )
    }

    fun clearSessionStatus() {
        apiClient.clearLastSuccessfulSync()
        _syncState.value = SyncState(
            apiBaseUrl = apiClient.baseUrl,
            syncStatusMessage = "Sign in to connect to the school server."
        )
    }

    fun reportActionSuccess(message: String) {
        _syncState.value = _syncState.value.copy(
            isOnline = true,
            syncStatusMessage = message
        )
    }

    fun reportActionQueued(message: String, pendingCount: Int) {
        _syncState.value = _syncState.value.copy(
            isOnline = false,
            pendingOutboxCount = pendingCount,
            syncStatusMessage = message
        )
    }

    fun reportActionWarning(message: String) {
        _syncState.value = _syncState.value.copy(syncStatusMessage = message)
    }

    fun updatePendingOutboxCount(pendingCount: Int) {
        _syncState.value = _syncState.value.copy(pendingOutboxCount = pendingCount)
    }

    fun updateOutboxCounts(pendingCount: Int, failedCount: Int) {
        _syncState.value = _syncState.value.copy(
            pendingOutboxCount = pendingCount,
            failedOutboxCount = failedCount
        )
    }

    suspend fun performSync(sync: suspend () -> Unit) {
        _syncState.value = _syncState.value.copy(isSyncing = true)
        try {
            sync()
            val syncedAt = System.currentTimeMillis()
            apiClient.recordSuccessfulSync(syncedAt)
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isOnline = true,
                lastSyncTime = syncedAt,
                syncStatusMessage = "Connected. School data refreshed."
            )
        } catch (error: IOException) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isOnline = false,
                syncStatusMessage = "Connection unavailable. Changes will retry when the server is reachable."
            )
            throw error
        } catch (error: IllegalStateException) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                isOnline = false,
                syncStatusMessage = "Could not refresh school data: ${error.message ?: "Invalid server data."}"
            )
            throw error
        }
    }
}
