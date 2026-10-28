package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.model.House
import com.example.data.model.IncidentRecord
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.IncidentReportInput
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val incidentCategories = listOf(
    "INJURY", "ILLNESS", "MISCONDUCT", "BULLYING", "PROPERTY", "SECURITY", "OTHER"
)
private val incidentSeverities = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")

private fun categoryLabel(value: String): String =
    value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun statusColor(status: String): Color = when (status) {
    "OPEN" -> CrimsonAlert
    "UNDER_REVIEW" -> AmberWarning
    "CLOSED" -> EmeraldGreen
    else -> NavyPrimary
}

private fun severityColor(severity: String): Color = when (severity) {
    "CRITICAL" -> CrimsonAlert
    "HIGH" -> AmberWarning
    "MEDIUM" -> Color(0xFFD97706)
    else -> EmeraldGreen
}

/**
 * Incident & welfare log. House staff record injuries, illness, misconduct, bullying,
 * property damage, security events and welfare concerns here; records are saved on this
 * device immediately and delivered to the school server through the delivery outbox.
 */
@Composable
fun IncidentLogScreen(
    viewModel: MainViewModel,
    canManage: Boolean,
    modifier: Modifier = Modifier
) {
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val currentUserDisplayName by viewModel.currentUserDisplayName.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()

    var incidentFormVisible by remember { mutableStateOf(false) }
    var detailIncident by remember { mutableStateOf<IncidentRecord?>(null) }
    var statusFilter by remember { mutableStateOf("OPEN_FOCUS") }
    var severityFilter by remember { mutableStateOf("ALL") }

    val visibleIncidents = incidents.filter { incident ->
        val matchesStatus = when (statusFilter) {
            "OPEN_FOCUS" -> incident.status in setOf("OPEN", "UNDER_REVIEW")
            else -> statusFilter == "ALL" || incident.status == statusFilter
        }
        matchesStatus && (severityFilter == "ALL" || incident.severity == severityFilter)
    }
    val openCount = incidents.count { it.status == "OPEN" || it.status == "UNDER_REVIEW" }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "INCIDENT & WELFARE LOG",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Record every injury, illness, misconduct and welfare concern. " +
                        "$openCount open right now. Records are kept on this device and " +
                        "delivered to the school server automatically.",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var statusExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { statusExpanded = true }
                        ) {
                            Text(
                                text = when (statusFilter) {
                                    "OPEN_FOCUS" -> "All open"
                                    "ALL" -> "All statuses"
                                    "OPEN" -> "Open only"
                                    "UNDER_REVIEW" -> "Under review"
                                    "CLOSED" -> "Closed"
                                    else -> statusFilter
                                },
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            listOf(
                                "OPEN_FOCUS" to "All open",
                                "ALL" to "All statuses",
                                "OPEN" to "Open only",
                                "UNDER_REVIEW" to "Under review",
                                "CLOSED" to "Closed"
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        statusFilter = value
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    var severityExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { severityExpanded = true }
                        ) {
                            Text(
                                text = "Severity: ${severityFilter.lowercase()}",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = severityExpanded,
                            onDismissRequest = { severityExpanded = false }
                        ) {
                            (listOf("ALL") + incidentSeverities).forEach { value ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            value.lowercase().replaceFirstChar { it.uppercase() }
                                        )
                                    },
                                    onClick = {
                                        severityFilter = value
                                        severityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (visibleIncidents.isEmpty()) {
                item {
                    Text(
                        text = if (statusFilter == "OPEN_FOCUS" && openCount == 0) {
                            "No open incidents. Log anything that happens, including small welfare concerns."
                        } else {
                            "No incidents match these filters."
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            items(visibleIncidents, key = { it.id }) { incident ->
                IncidentCard(
                    incident = incident,
                    houseName = houses.firstOrNull { it.id == incident.houseId }?.name,
                    onClick = { detailIncident = incident }
                )
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }

        if (canManage) {
            FloatingActionButton(
                onClick = { incidentFormVisible = true },
                containerColor = CrimsonAlert,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 6.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report incident")
                }
            }
        }

        if (incidentFormVisible) {
            IncidentFormDialog(
                students = students,
                excludeCampusWide = currentRole.isHouseScoped,
                actorName = currentUserDisplayName,
                onDismiss = { incidentFormVisible = false },
                onSubmit = { input ->
                    viewModel.reportIncident(input)
                    incidentFormVisible = false
                }
            )
        }

        detailIncident?.let { incident ->
            IncidentDetailDialog(
                incident = incident,
                houseName = houses.firstOrNull { it.id == incident.houseId }?.name,
                canManage = canManage,
                onStatus = { status, notes ->
                    viewModel.updateIncidentStatus(
                        incident,
                        status,
                        notes,
                        currentUserDisplayName
                    )
                    detailIncident = null
                },
                onDismiss = { detailIncident = null }
            )
        }
    }
}

@Composable
private fun IncidentCard(
    incident: IncidentRecord,
    houseName: String?,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("EEE dd MMM, HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (incident.medicalReferral) Icons.Default.LocalHospital
                    else Icons.Default.Warning,
                    contentDescription = null,
                    tint = severityColor(incident.severity),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = categoryLabel(incident.category),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NavyPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = severityColor(incident.severity).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = incident.severity,
                        color = severityColor(incident.severity),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor(incident.status).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = incident.status.replace('_', ' '),
                        color = statusColor(incident.status),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (incident.studentId.isNotBlank()) {
                    "${incident.studentName} (${incident.studentIndexNumber})"
                } else {
                    "Campus-wide event"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155)
            )
            Text(
                text = listOfNotNull(
                    houseName,
                    incident.location.takeIf(String::isNotBlank)?.let { "at $it" },
                    if (incident.guardianContacted) "guardian contacted" else null,
                    timeFormat.format(Date(incident.createdAt))
                ).joinToString(" · "),
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = incident.description,
                fontSize = 12.sp,
                color = Color(0xFF475569),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (incident.syncStatus != "SYNCED") {
                Text(
                    text = "Saved on this device · queued for the school server",
                    fontSize = 10.sp,
                    color = Color(0xFFB45309),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun IncidentFormDialog(
    students: List<Student>,
    excludeCampusWide: Boolean,
    actorName: String,
    onDismiss: () -> Unit,
    onSubmit: (IncidentReportInput) -> Unit
) {
    var studentMenuExpanded by remember { mutableStateOf(false) }
    var studentQuery by remember { mutableStateOf("") }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var category by remember { mutableStateOf("ILLNESS") }
    var severity by remember { mutableStateOf("MEDIUM") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var immediateAction by remember { mutableStateOf("") }
    var guardianContacted by remember { mutableStateOf(false) }
    var guardianPhone by remember { mutableStateOf("") }
    var guardianTime by remember { mutableStateOf("") }
    var medicalReferral by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val sortedStudents = students.sortedBy { it.fullName.lowercase() }
    val matchedStudents = if (studentQuery.isBlank()) {
        sortedStudents.take(25)
    } else {
        sortedStudents.filter {
            it.fullName.contains(studentQuery, ignoreCase = true) ||
                it.studentId.contains(studentQuery, ignoreCase = true)
        }.take(25)
    }
    val studentSelected = selectedStudent != null || !excludeCampusWide
    val canSubmit = studentSelected &&
        description.isNotBlank() &&
        (!guardianContacted || guardianPhone.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report incident", fontWeight = FontWeight.Bold, color = NavyPrimary) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = studentQuery,
                    onValueChange = { studentQuery = it },
                    label = {
                        Text(
                            if (excludeCampusWide) {
                                "Choose student (type to search) *"
                            } else {
                                "Choose student or leave blank for a campus-wide event"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                selectedStudent?.let { chosen ->
                    Text(
                        text = "Selected: ${chosen.fullName} (${chosen.studentId})",
                        fontSize = 12.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = { studentMenuExpanded = true }) {
                    Text("Search students")
                }
                Box {
                    DropdownMenu(
                        expanded = studentMenuExpanded,
                        onDismissRequest = { studentMenuExpanded = false }
                    ) {
                        if (!excludeCampusWide) {
                            DropdownMenuItem(
                                text = { Text("Campus-wide event") },
                                onClick = {
                                    selectedStudent = null
                                    studentQuery = ""
                                    studentMenuExpanded = false
                                }
                            )
                        }
                        matchedStudents.forEach { student ->
                            DropdownMenuItem(
                                text = { Text("${student.fullName} (${student.studentId})") },
                                onClick = {
                                    selectedStudent = student
                                    studentMenuExpanded = false
                                }
                            )
                        }
                        if (matchedStudents.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No students match \"${studentQuery}\"") },
                                onClick = { studentMenuExpanded = false }
                            )
                        }
                    }
                }

                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                incidentCategories.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            val selected = category == value
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) NavyPrimary else Color(0xFFF1F5F9),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { category = value }
                            ) {
                                Text(
                                    text = categoryLabel(value),
                                    color = if (selected) Color.White else Color(0xFF475569),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }

                Text("Severity", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    incidentSeverities.forEach { value ->
                        val selected = severity == value
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) severityColor(value) else Color(0xFFF1F5F9),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { severity = value }
                        ) {
                            Text(
                                text = value.lowercase(),
                                color = if (selected) Color.White else Color(0xFF475569),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 9.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (house, dorm, field, gate…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What happened? *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = immediateAction,
                    onValueChange = { immediateAction = it },
                    label = { Text("Immediate action taken") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = medicalReferral,
                        onCheckedChange = { medicalReferral = it }
                    )
                    Text(
                        "Sick bay / medical lead",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = guardianContacted,
                        onCheckedChange = { guardianContacted = it }
                    )
                    Text("Guardian contacted", fontSize = 12.sp)
                }
                if (guardianContacted) {
                    OutlinedTextField(
                        value = guardianPhone,
                        onValueChange = { guardianPhone = it },
                        label = { Text("Guardian phone contacted *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = guardianTime,
                        onValueChange = { guardianTime = it },
                        label = { Text("When contacted (e.g. 21:35, 12 Feb)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                validationError?.let {
                    Text(it, fontSize = 12.sp, color = CrimsonAlert)
                }
                Text(
                    text = "Recorded by: ${actorName.ifBlank { "Signed-in staff" }}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!studentSelected) {
                        validationError = "Choose a student for this incident."
                        return@Button
                    }
                    if (description.isBlank()) {
                        validationError = "Describe what happened."
                        return@Button
                    }
                    if (guardianContacted && guardianPhone.isBlank()) {
                        validationError = "Enter the guardian phone number that was contacted."
                        return@Button
                    }
                    onSubmit(
                        IncidentReportInput(
                            student = selectedStudent,
                            houseId = "",
                            category = category,
                            severity = severity,
                            location = location,
                            description = description,
                            immediateAction = immediateAction,
                            guardianContacted = guardianContacted,
                            guardianContactPhone = guardianPhone,
                            guardianContactTime = guardianTime,
                            medicalReferral = medicalReferral,
                            actorName = actorName
                        )
                    )
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
            ) {
                Text("Report incident", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun IncidentDetailDialog(
    incident: IncidentRecord,
    houseName: String?,
    canManage: Boolean,
    onStatus: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("EEE dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var pendingStatus by remember { mutableStateOf<String?>(null) }
    var resolutionNotes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${categoryLabel(incident.category)} · ${incident.severity.lowercase()}",
                fontWeight = FontWeight.Bold,
                color = statusColor(incident.status)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (incident.studentId.isNotBlank()) {
                        "${incident.studentName} (${incident.studentIndexNumber})"
                    } else {
                        "Campus-wide event"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
                Text(
                    "House: ${houseName ?: incident.houseId.ifBlank { "Campus-wide" }}",
                    fontSize = 12.sp
                )
                if (incident.location.isNotBlank()) {
                    Text("Location: ${incident.location}", fontSize = 12.sp)
                }
                Text(
                    "Reported: ${timeFormat.format(Date(incident.createdAt))} by ${incident.reportedByName}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Text("What happened: ${incident.description}", fontSize = 12.sp)
                if (incident.immediateAction.isNotBlank()) {
                    Text(
                        "Action taken: ${incident.immediateAction}",
                        fontSize = 12.sp,
                        color = EmeraldGreen
                    )
                }
                if (incident.medicalReferral) {
                    Text(
                        "Referred to sick bay / medical lead.",
                        fontSize = 12.sp,
                        color = CrimsonAlert
                    )
                }
                Text(
                    text = if (incident.guardianContacted) {
                        "Guardian contacted: yes, ${incident.guardianContactPhone.ifBlank {
                            "number not recorded"
                        }} at ${incident.guardianContactTime.ifBlank { "time not recorded" }}"
                    } else {
                        "Guardian contacted: no"
                    },
                    fontSize = 12.sp
                )
                Text(
                    "Status: ${incident.status.replace('_', ' ')}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (incident.resolutionNotes.isNotBlank()) {
                    Text(
                        "Resolution notes: ${incident.resolutionNotes}",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                }
                if (incident.syncStatus != "SYNCED") {
                    Text(
                        "Saved on this device · queued for the school server",
                        fontSize = 10.sp,
                        color = Color(0xFFB45309)
                    )
                }

                if (canManage && incident.status != "CLOSED") {
                    if (pendingStatus == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (incident.status == "OPEN") {
                                TextButton(onClick = { pendingStatus = "UNDER_REVIEW" }) {
                                    Text("Mark under review", color = AmberWarning)
                                }
                            }
                            TextButton(onClick = { pendingStatus = "CLOSED" }) {
                                Text("Close incident…", color = NavyPrimary)
                            }
                        }
                    } else {
                        Text(
                            text = when (pendingStatus) {
                                "CLOSED" -> "Closing this incident. Record how it was resolved:"
                                else -> "Marking this incident as under review:"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = resolutionNotes,
                            onValueChange = { resolutionNotes = it },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        validationError?.let {
                            Text(it, fontSize = 12.sp, color = CrimsonAlert)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val status = pendingStatus
                    if (status == null) {
                        onDismiss()
                    } else if (status == "CLOSED" && resolutionNotes.isBlank()) {
                        validationError = "Record how this incident was resolved before closing it."
                    } else {
                        onStatus(status, resolutionNotes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text(if (pendingStatus != null) "Save status" else "Done", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (pendingStatus != null) {
                        pendingStatus = null
                        validationError = null
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (pendingStatus != null) "Back" else "Dismiss")
            }
        }
    )
}
