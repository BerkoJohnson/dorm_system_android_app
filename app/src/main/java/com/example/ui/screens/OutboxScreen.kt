package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.OutboxDisplayItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OutboxScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val items by viewModel.outboxItems.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<OutboxDisplayItem?>(null) }

    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.refreshOutboxItems()
            delay(3_000)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "DELIVERY OUTBOX",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Queued work retries in the background when a network is available. Failed work remains saved for review.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        if (items.isEmpty()) {
            item {
                Text(
                    text = "No queued, failed, or recently delivered actions.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(items, key = { "${it.kind.value}:${it.id}" }) { item ->
                OutboxItemCard(
                    item = item,
                    onRetry = { viewModel.retryOutboxItem(item) },
                    onDelete = { pendingDelete = item }
                )
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove queued action?") },
            text = {
                Text(
                    "This removes “${item.title}” from this account's delivery queue. " +
                        "A request already in progress may have reached the school server."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteOutboxItem(item)
                        pendingDelete = null
                    }
                ) { Text("Remove action") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep it") }
            }
        )
    }
}

@Composable
private fun OutboxItemCard(
    item: OutboxDisplayItem,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (item.status) {
        "FAILED" -> Color(0xFFB91C1C)
        "DELIVERED" -> Color(0xFF15803D)
        else -> Color(0xFFB45309)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    item.status.replace('_', ' '),
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Text(
                text = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                    .format(Date(item.createdAt)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                text = "Attempts: ${item.attempts}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            item.lastError?.let {
                Text(
                    text = "Last error: $it",
                    color = Color(0xFFB91C1C),
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (
                    item.status != "DELIVERED" &&
                    (item.status == "FAILED" || !item.lastError.isNullOrBlank())
                ) {
                    Button(onClick = onRetry) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Refresh,
                            contentDescription = null
                        )
                        Text("Retry", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                OutlinedButton(onClick = onDelete) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Delete,
                        contentDescription = null
                    )
                    Text(
                        if (item.status == "DELIVERED") "Remove history" else "Delete",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
