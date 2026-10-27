package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.MainViewModel

@Composable
fun HelpScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Help & school data", style = MaterialTheme.typography.headlineSmall)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (syncState.isOnline) "Connected to the school" else "You are offline",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (syncState.isOnline) {
                        "School records can be refreshed. Changes waiting to upload: " +
                            "${syncState.pendingOutboxCount}."
                    } else {
                        "Previously synced records remain available. Roll calls and supported " +
                            "school actions can be saved for later upload. Changes waiting to " +
                            "upload: ${syncState.pendingOutboxCount}."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                if (syncState.failedOutboxCount > 0) {
                    Text(
                        "${syncState.failedOutboxCount} saved change(s) need attention. Open " +
                            "Delivery outbox from Operations to review the reason and retry.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "Last successful sync: " +
                        (syncState.lastSyncTime?.let {
                            java.text.SimpleDateFormat(
                                "EEE, d MMM yyyy · HH:mm",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(it))
                        } ?: "Not available on this device"),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        HelpTopic(
            title = "Start a roll call",
            body = "Open Roll Call, choose the house and dorm, mark each student, then submit. " +
                "A saved offline roll call stays in Delivery outbox until it can be sent."
        )
        HelpTopic(
            title = "Review an exeat",
            body = "Open Exeats, select a request to review the student and pass details, then " +
                "approve or reject it with the required notes."
        )
        HelpTopic(
            title = "A change is pending",
            body = "Pending means the device has saved the change but the school server has not " +
                "confirmed it yet. Do not treat a pending approval as final."
        )
        HelpTopic(
            title = "Need access or setup help?",
            body = "Ask your school administrator to confirm your role, house assignment and " +
                "school access. Do not change the school setup address unless you are the " +
                "administrator responsible for this device."
        )
    }
}

@Composable
private fun HelpTopic(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
