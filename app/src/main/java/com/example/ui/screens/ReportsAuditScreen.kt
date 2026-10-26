package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsAuditScreen(
    viewModel: MainViewModel,
    canViewReports: Boolean,
    canViewAudit: Boolean,
    modifier: Modifier = Modifier
) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val report by viewModel.mobileReport.collectAsStateWithLifecycle()
    val isLoadingReport by viewModel.isLoadingReport.collectAsStateWithLifecycle()
    val hasMoreReportRows by viewModel.hasMoreReportRows.collectAsStateWithLifecycle()
    val auditEvents by viewModel.auditEvents.collectAsStateWithLifecycle()
    val isLoadingAudit by viewModel.isLoadingAudit.collectAsStateWithLifecycle()
    val hasMoreAudit by viewModel.hasMoreAuditEvents.collectAsStateWithLifecycle()
    val operationsMessage by viewModel.operationsMessage.collectAsStateWithLifecycle()
    var selectedReportType by remember { mutableStateOf("housing") }
    var exportFormat by remember { mutableStateOf("csv") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = viewModel.downloadReport(exportFormat)
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content)
                    } ?: throw IOException("The selected file could not be opened.")
                } catch (error: IOException) {
                    viewModel.reportOperationsError("Could not export report: ${error.message}")
                } catch (error: IllegalStateException) {
                    viewModel.reportOperationsError("Could not export report: ${error.message}")
                }
            }
        }
    }
    LaunchedEffect(canViewReports) {
        if (canViewReports) viewModel.loadMobileReport(selectedReportType)
    }
    LaunchedEffect(canViewAudit) {
        if (canViewAudit) viewModel.loadAuditEvents()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (operationsMessage != null) {
            item {
                Text(
                    text = operationsMessage.orEmpty(),
                    color = Color(0xFFB91C1C),
                    fontSize = 12.sp
                )
            }
        }
        // Section: Configurable Django REST API Sync
        if (syncState.apiBaseUrl.isNotBlank()) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CENTRAL DJANGO BACKEND SYNC",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (syncState.isOnline) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = if (syncState.isOnline) "ONLINE" else "OFFLINE",
                                color = if (syncState.isOnline) EmeraldGreen else Color(0xFFB45309),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = syncState.apiBaseUrl,
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val timeFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                    Text(
                        text = "Last Synchronized: ${
                            syncState.lastSyncTime?.let { timeFormat.format(Date(it)) } ?: "Not synchronized yet"
                        }",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Status: ${syncState.syncStatusMessage}",
                        fontSize = 11.sp,
                        color = NavyPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (syncState.pendingOutboxCount > 0) {
                        Text(
                            text = "${syncState.pendingOutboxCount} change(s) waiting for delivery or attention",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.triggerSync()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh school data", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: Operational Reports & Export
        if (canViewReports) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OFFICIAL BOARDING REPORTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "View server-generated report data and save a CSV export. Results respect your assigned data scope.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "housing" to "Housing",
                            "attendance" to "Roll call",
                            "exeats" to "Exeats",
                            "discipline" to "Discipline",
                            "placement_exceptions" to "Placement exceptions",
                            "attendance_league" to "Attendance league",
                            "overdue_register" to "Overdue register",
                            "gate_conflicts" to "Gate conflicts",
                            "approval_turnaround" to "Approval turnaround",
                            "exeat_analytics" to "Exeat analytics",
                            "room_register" to "Room register",
                            "staff_activity" to "Staff activity",
                            "welfare_infirmary" to "Welfare & infirmary",
                            "data_completeness" to "Data completeness",
                            "movement_concerns" to "Movement concerns",
                            "conduct_normalized" to "Conduct trends",
                            "intervention_effectiveness" to "Intervention results"
                        ).chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { (type, label) ->
                                    Button(
                                        onClick = {
                                            selectedReportType = type
                                            viewModel.loadMobileReport(type)
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isLoadingReport
                                    ) { Text(label, fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isLoadingReport) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        report?.let { current ->
                            Text(
                                text = "${current.title} · ${current.recordCount} records",
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary,
                                fontSize = 13.sp
                            )
                            current.metrics.forEach { (label, value) ->
                                Text("$label: $value", fontSize = 12.sp, color = Color(0xFF475569))
                            }
                            if (current.headers.isNotEmpty()) {
                                Text(
                                    text = current.headers.joinToString("  •  "),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = NavyPrimary
                                )
                                current.rows.take(5).forEach { row ->
                                    Text(
                                        text = row.joinToString("  •  "),
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            current.sections.forEach { section ->
                                Text(
                                    section.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = NavyPrimary
                                )
                                if (section.headers.isNotEmpty()) {
                                    Text(
                                        section.headers.joinToString("  •  "),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp
                                    )
                                }
                                section.rows.take(5).forEach { row ->
                                    Text(
                                        row.joinToString("  •  "),
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (hasMoreReportRows) {
                                Button(
                                    onClick = {
                                        viewModel.loadMobileReport(
                                            selectedReportType,
                                            loadMore = true
                                        )
                                    },
                                    enabled = !isLoadingReport,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isLoadingReport) "Loading…" else "Load more report rows")
                                }
                            }
                            Button(
                                onClick = {
                                    exportFormat = "csv"
                                    exportLauncher.launch(
                                        "${current.title.lowercase().replace(' ', '-')}.csv"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save CSV export") }
                        }
                    }
                }
            }
        }

        if (canViewAudit) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SERVER AUDIT TRAIL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recent server audit events", fontSize = 12.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isLoadingAudit && auditEvents.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                    auditEvents.forEach { event ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(event.summary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${event.action} · ${event.actor} · ${event.targetType} · ${event.createdAt}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    if (hasMoreAudit || isLoadingAudit) {
                        Button(
                            onClick = { viewModel.loadAuditEvents(loadMore = true) },
                            enabled = hasMoreAudit && !isLoadingAudit,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isLoadingAudit) "Loading…" else "Load more audit events")
                        }
                    }
                }
            }
        }
    }
}
