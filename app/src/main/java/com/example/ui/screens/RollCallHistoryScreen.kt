package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.RollCallEntryItem
import com.example.ui.viewmodel.RollCallSessionItem

@Composable
fun RollCallHistoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.rollCallSessions.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedRollCallSession.collectAsStateWithLifecycle()
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val message by viewModel.rollCallHistoryMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingRollCallHistory.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMoreRollCallHistory.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadRollCallHistory()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Roll-call history",
            color = NavyPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (message != null) {
            Text(
                text = message.orEmpty(),
                color = Color(0xFFB91C1C),
                fontSize = 12.sp
            )
        }
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else if (message == null) {
                    Text(
                        text = "No roll-call sessions are available.",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                } else {
                    Button(onClick = viewModel::loadRollCallHistory) {
                        Text("Retry")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openRollCallSession(session) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = NavyPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.sessionType,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = listOf(session.date, session.dormitory, session.status)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${session.present} present · ${session.absent} absent · ${session.unmarked} unmarked",
                                fontSize = 11.sp,
                                color = Color(0xFF334155)
                            )
                        }
                    }
                    }
                    if (sessions.isNotEmpty() && (hasMore || isLoading)) {
                    Button(
                        onClick = viewModel::loadMoreRollCallHistory,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Loading...")
                        } else {
                            Text("Load more sessions")
                        }
                    }
                    }
                }
            }
        }
    }

    if (selectedSession != null) {
        RollCallSessionDetailDialog(
            session = selectedSession!!,
            houses = houses,
            viewModel = viewModel,
            onDismiss = viewModel::closeRollCallSession
        )
    }
}

@Composable
private fun RollCallSessionDetailDialog(
    session: RollCallSessionItem,
    houses: List<com.example.data.model.House>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val entries by viewModel.rollCallEntries.collectAsStateWithLifecycle()
    val editable = session.status.equals("In Progress", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(session.sessionType, color = NavyPrimary, fontWeight = FontWeight.Bold)
                Text(
                    text = "${session.date} · ${session.status}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    RollCallHistoryEntry(
                        entry = entry,
                        houseName = houses.firstOrNull { it.id == entry.houseId }?.name
                            ?: entry.houseName.ifBlank { "House not recorded" },
                        editable = editable,
                        onStatusSelected = { status ->
                            viewModel.updateRollCallEntry(
                                session = session,
                                entry = entry,
                                status = status,
                                remarks = entry.remarks
                            )
                        },
                        onCorrectionRequested = { status, reason ->
                            viewModel.requestRollCallCorrection(
                                session = session,
                                entry = entry,
                                requestedStatus = status,
                                reason = reason
                            )
                        }
                    )
                }
                if (!editable) {
                    Text(
                        text = "This session is finalized. Changes are submitted as correction requests and do not alter the original record.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun RollCallHistoryEntry(
    entry: RollCallEntryItem,
    houseName: String,
    editable: Boolean,
    onStatusSelected: (String) -> Unit,
    onCorrectionRequested: (String, String) -> Unit
) {
    var statusMenuExpanded by remember(entry.id) { mutableStateOf(false) }
    var correctionMenuExpanded by remember(entry.id) { mutableStateOf(false) }
    var requestedStatus by remember(entry.id) { mutableStateOf(entry.status) }
    var correctionReason by remember(entry.id) { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.studentName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = NavyPrimary
            )
            Text(
                text = entry.admissionNumber,
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
            Text(
                text = houseName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF475569)
            )
            BoxStatusSelector(
                status = entry.status,
                enabled = editable,
                expanded = statusMenuExpanded,
                onExpandedChange = { statusMenuExpanded = it },
                onStatusSelected = onStatusSelected
            )
            if (!editable) {
                BoxStatusSelector(
                    status = requestedStatus,
                    enabled = true,
                    expanded = correctionMenuExpanded,
                    onExpandedChange = { correctionMenuExpanded = it },
                    onStatusSelected = { requestedStatus = it },
                    actionLabel = "Request"
                )
                OutlinedTextField(
                    value = correctionReason,
                    onValueChange = { correctionReason = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correction reason (required)") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        onCorrectionRequested(requestedStatus, correctionReason)
                    },
                    enabled = correctionReason.isNotBlank()
                ) {
                    Text("Submit correction request")
                }
            }
            if (!entry.gateConflict.isNullOrBlank()) {
                Text(
                    text = "Gate conflict: ${entry.gateConflict}",
                    color = Color(0xFFB45309),
                    fontSize = 10.sp
                )
            }
            if (entry.remarks.isNotBlank()) {
                Text(
                    text = "Note: ${entry.remarks}",
                    color = Color(0xFF475569),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun BoxStatusSelector(
    status: String,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onStatusSelected: (String) -> Unit,
    actionLabel: String = "Correct"
) {
    androidx.compose.foundation.layout.Box {
        val (statusBackground, statusForeground) = rollCallStatusColors(status)
        Surface(
            modifier = Modifier.clickable(enabled = enabled) { onExpandedChange(true) },
            shape = RoundedCornerShape(6.dp),
            color = statusBackground
        ) {
            Text(
                text = if (enabled) "$status  ·  $actionLabel" else status,
                color = statusForeground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            listOf("Present", "Absent", "On Exeat", "Infirmary", "Activity", "Unmarked")
                .forEach { nextStatus ->
                    DropdownMenuItem(
                        text = { Text(nextStatus) },
                        onClick = {
                            onExpandedChange(false)
                            onStatusSelected(nextStatus)
                        }
                    )
                }
        }
    }
}

private fun rollCallStatusColors(status: String): Pair<Color, Color> =
        when (status.trim().uppercase().replace(' ', '_')) {
                "PRESENT" -> Color(0xFFDCFCE7) to Color(0xFF166534)
                "ABSENT" -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
                "ON_EXEAT" -> Color(0xFFFEF3C7) to Color(0xFF92400E)
                "INFIRMARY" -> Color(0xFFEDE9FE) to Color(0xFF5B21B6)
                "ACTIVITY" -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
                else -> Color(0xFFE2E8F0) to Color(0xFF334155)
        }
