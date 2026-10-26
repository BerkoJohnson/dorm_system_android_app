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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.ExeatWorkflow
import com.example.data.model.ExeatRequest
import com.example.data.model.Student
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExeatScreen(
    viewModel: MainViewModel,
    canCreate: Boolean,
    canApprove: Boolean,
    canReview: Boolean,
    modifier: Modifier = Modifier
) {
    val exeats by viewModel.exeats.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val houses by viewModel.houses.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedExeatForDetail by remember { mutableStateOf<ExeatRequest?>(null) }
    var selectedExeatWorkflow by remember { mutableStateOf<ExeatWorkflow?>(null) }
    var exeatToApprove by remember { mutableStateOf<ExeatRequest?>(null) }
    var exeatToReject by remember { mutableStateOf<ExeatRequest?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<String?>(null) }
    var operationReason by remember { mutableStateOf("") }
    var operationDeparture by remember { mutableStateOf("") }
    var operationReturn by remember { mutableStateOf("") }
    var operationOutcome by remember { mutableStateOf("unused") }

    // Rejection reason input
    var rejectReason by remember { mutableStateOf("") }
    // Approval notes input
    var approvalNotes by remember { mutableStateOf("") }

    val filteredExeats = when (selectedFilter) {
        "PENDING" -> exeats.filter { it.status in setOf("PENDING", "QUEUED") }
        "FOLLOW_UP" -> exeats.filter { it.status in setOf("APPROVED", "DEPARTED") }
        "HISTORY" -> exeats.filter {
            it.status in setOf("RETURNED", "REJECTED", "CANCELLED")
        }
        else -> exeats
    }

    Scaffold(
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = NavyPrimary,
                    contentColor = GoldAccent
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Exeat")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            val filterOptions = listOf(
                "ALL" to "All",
                "PENDING" to "Pending",
                "FOLLOW_UP" to "Follow-ups",
                "HISTORY" to "History"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterOptions) { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) NavyPrimary else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF475569),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredExeats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No exeat passes in this category.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredExeats) { exeat ->
                        ExeatCard(
                            exeat = exeat,
                            canApprove = canApprove,
                            onApprove = {
                                viewModel.loadExeatDetail(exeat) { detail, _ ->
                                    exeatToApprove = detail
                                }
                            },
                            onReject = {
                                viewModel.loadExeatDetail(exeat) { detail, _ ->
                                    exeatToReject = detail
                                }
                            },
                            onFollowUp = {
                                viewModel.loadExeatDetail(exeat) { detail, workflow ->
                                    selectedExeatForDetail = detail
                                    selectedExeatWorkflow = workflow
                                }
                            },
                            onClick = {
                                viewModel.loadExeatDetail(exeat) { detail, workflow ->
                                    selectedExeatForDetail = detail
                                    selectedExeatWorkflow = workflow
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Approve Dialog
    if (exeatToApprove != null) {
        val ex = exeatToApprove!!
        AlertDialog(
            onDismissRequest = { exeatToApprove = null },
            title = {
                Text(
                    text = "Approve Exeat Pass",
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Approve exeat for ${ex.studentName} (${ex.studentIndexNumber}) for ${ex.category} to ${ex.destination}?",
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = approvalNotes,
                        onValueChange = { approvalNotes = it },
                        label = { Text("Approval Conditions / Notes") },
                        placeholder = { Text("e.g. Return doctor's cert, guardian verified") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveExeat(
                            ex.id,
                            "Duty Housemaster",
                            approvalNotes.ifBlank { null }
                        )
                        exeatToApprove = null
                        approvalNotes = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Approve Exeat", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exeatToApprove = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Reject Dialog
    if (exeatToReject != null) {
        val ex = exeatToReject!!
        AlertDialog(
            onDismissRequest = { exeatToReject = null },
            title = {
                Text(
                    text = "Reject Exeat Request",
                    fontWeight = FontWeight.Bold,
                    color = CrimsonAlert
                )
            },
            text = {
                Column {
                    Text(
                        text = "Reject exeat for ${ex.studentName}? A reason is required for administrative audit trail.",
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Rejection Reason *") },
                        placeholder = { Text("e.g. Inadequate justification, disciplinary grounds") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectReason.isNotBlank()) {
                            viewModel.rejectExeat(
                                ex.id,
                                "Duty Housemaster",
                                rejectReason
                            )
                            exeatToReject = null
                            rejectReason = ""
                        }
                    },
                    enabled = rejectReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Confirm Rejection", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exeatToReject = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Create Exeat Dialog
    if (showCreateDialog) {
        CreateExeatDialog(
            students = students,
            onDismiss = { showCreateDialog = false },
            onSubmit = { student, category, reason, destination, depHours, retHours ->
                val now = System.currentTimeMillis()
                viewModel.createExeatRequest(
                    student = student,
                    category = category,
                    reason = reason,
                    destination = destination,
                    departureTime = now + (depHours * 3600 * 1000L),
                    expectedReturnTime = now + (retHours * 3600 * 1000L),
                    actorName = "Duty Housemaster"
                )
                showCreateDialog = false
            }
        )
    }

    // Detail Dialog
    if (selectedExeatForDetail != null) {
        ExeatDetailDialog(
            exeat = selectedExeatForDetail!!,
            workflow = selectedExeatWorkflow,
            canApprove = canApprove,
            canReview = canReview,
            onAction = { action ->
                operation = action
                operationReason = ""
                operationOutcome = when (action) {
                    "reconcile" -> "unused"
                    "review" -> "upheld"
                    else -> ""
                }
                operationDeparture = if (action == "reconcile") {
                    ""
                } else {
                    selectedExeatForDetail?.departureTime
                        ?.let { java.time.Instant.ofEpochMilli(it).toString() }.orEmpty()
                }
                operationReturn = if (action == "reconcile") {
                    ""
                } else {
                    selectedExeatForDetail?.expectedReturnTime
                        ?.let { java.time.Instant.ofEpochMilli(it).toString() }.orEmpty()
                }
            },
            onDismiss = {
                selectedExeatForDetail = null
                selectedExeatWorkflow = null
            }
        )
    }

    if (operation != null && selectedExeatForDetail != null) {
        ExeatOperationDialog(
            operation = operation.orEmpty(),
            reason = operationReason,
            onReasonChange = { operationReason = it },
            departure = operationDeparture,
            onDepartureChange = { operationDeparture = it },
            expectedReturn = operationReturn,
            onExpectedReturnChange = { operationReturn = it },
            outcome = operationOutcome,
            onOutcomeChange = { operationOutcome = it },
            onDismiss = { operation = null },
            onSubmit = {
                val action = operation.orEmpty()
                val payload = when (action) {
                    "override" -> org.json.JSONObject().put("reason", operationReason)
                    "reschedule" -> org.json.JSONObject()
                        .put("expected_departure", operationDeparture)
                        .put("expected_return", operationReturn)
                        .put("reason", operationReason)
                    "reconcile" -> org.json.JSONObject()
                        .put("outcome", operationOutcome)
                        .put("reason", operationReason)
                                        .apply {
                                            if (operationOutcome != "unused") {
                                                put("actual_departure", operationDeparture)
                                            }
                                            if (operationOutcome == "returned") {
                                                put("actual_return", operationReturn)
                                            }
                                        }
                    "review" -> org.json.JSONObject()
                        .put("outcome", operationOutcome)
                        .put("notes", operationReason)
                    else -> org.json.JSONObject()
                }
                viewModel.performExeatOperation(
                    exeatId = selectedExeatForDetail!!.id,
                    operation = action,
                    payload = payload,
                    onSuccess = {
                        val current = selectedExeatForDetail
                        operation = null
                        if (current != null) {
                            viewModel.loadExeatDetail(current) { detail, workflow ->
                                selectedExeatForDetail = detail
                                selectedExeatWorkflow = workflow
                            }
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun ExeatCard(
    exeat: ExeatRequest,
    canApprove: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onFollowUp: () -> Unit,
    onClick: () -> Unit
) {
    val isQueued = exeat.status == "QUEUED"
    val isOverdue = exeat.status == "DEPARTED" && System.currentTimeMillis() > exeat.expectedReturnTime

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isQueued) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categoryColor = when (exeat.category) {
                        "MEDICAL" -> Color(0xFFDC2626)
                        "ACADEMIC" -> Color(0xFF2563EB)
                        "FAMILY_EMERGENCY" -> Color(0xFFD97706)
                        else -> Color(0xFF0D9488)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = categoryColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = exeat.category.replace("_", " "),
                            color = categoryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val (statusBg, statusFg) = when {
                    isOverdue -> Pair(Color(0xFFFEE2E2), CrimsonAlert)
                    exeat.status == "APPROVED" -> Pair(Color(0xFFDCFCE7), EmeraldGreen)
                    exeat.status == "DEPARTED" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                    exeat.status == "REJECTED" -> Pair(Color(0xFFFEE2E2), CrimsonAlert)
                    exeat.status == "RETURNED" -> Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
                    else -> Pair(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isQueued) Color(0xFFFEF3C7) else statusBg
                ) {
                    Text(
                        text = when {
                            isQueued -> "QUEUED"
                            isOverdue -> "OVERDUE"
                            else -> exeat.status
                        },
                        color = if (isQueued) Color(0xFFB45309) else statusFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = exeat.studentName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val studentLine = listOf(exeat.studentIndexNumber, exeat.destination)
                .filter(String::isNotBlank)
                .joinToString(" • ")
            if (studentLine.isNotBlank()) {
                Text(
                    text = studentLine,
                    fontSize = 11.sp,
                    color = Color(0xFF4B5563),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                Text(
                    text = "Return by: ${timeFormat.format(Date(exeat.expectedReturnTime))}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOverdue) CrimsonAlert else Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (exeat.status in setOf("APPROVED", "DEPARTED")) {
                    TextButton(
                        onClick = onFollowUp,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Follow up", fontSize = 11.sp, color = NavyPrimary)
                    }
                }
            }

            if (exeat.status == "PENDING" && canApprove) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", color = CrimsonAlert, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (exeat.category == "EMERGENCY") {
                        OutlinedButton(
                            onClick = onClick,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Review override", color = NavyPrimary, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateExeatDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSubmit: (student: Student, category: String, reason: String, destination: String, depHours: Long, retHours: Long) -> Unit
) {
    var selectedStudentId by remember { mutableStateOf(students.firstOrNull()?.id ?: "") }
    var category by remember { mutableStateOf("Medical") }
    var reason by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var durationHours by remember { mutableStateOf("6") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Issue Exeat Pass", fontWeight = FontWeight.Bold, color = NavyPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Select Student:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                // Student Picker list or simple drop
                var studentMenuExpanded by remember { mutableStateOf(false) }
                val selectedStudent = students.find { it.id == selectedStudentId }

                Box {
                    OutlinedButton(
                        onClick = { studentMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = selectedStudent?.fullName ?: "Select Student",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = studentMenuExpanded,
                        onDismissRequest = { studentMenuExpanded = false }
                    ) {
                        students.forEach { s ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${s.fullName} (${s.studentId})") },
                                onClick = {
                                    selectedStudentId = s.id
                                    studentMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val categories = listOf(
                    "Medical" to "Medical",
                    "Town" to "Town pass",
                    "Weekend" to "Weekend",
                    "Emergency" to "Emergency",
                    "Vacation" to "Vacation"
                )
                categories.chunked(2).forEach { categoryRow ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categoryRow.forEach { (value, label) ->
                            val selected = category == value
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) NavyPrimary else Color(0xFFF1F5F9),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { category = value }
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else Color(0xFF475569),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination Hospital / Address") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Request") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = durationHours,
                    onValueChange = { durationHours = it },
                    label = { Text("Duration (Hours)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val student = students.find { it.id == selectedStudentId }
                    val hours = durationHours.toLongOrNull()
                    if (student != null && destination.isNotBlank() && reason.isNotBlank() && hours != null && hours > 0) {
                        onSubmit(student, category, reason, destination, 0L, hours)
                    }
                },
                enabled = students.any { it.id == selectedStudentId } &&
                    destination.isNotBlank() &&
                    reason.isNotBlank() &&
                    (durationHours.toLongOrNull() ?: 0L) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Submit Request", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun ExeatDetailDialog(
    exeat: ExeatRequest,
    workflow: ExeatWorkflow?,
    canApprove: Boolean,
    canReview: Boolean,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val timeFormat = SimpleDateFormat("EEEE, dd MMM yyyy 'at' HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Official Exeat Pass Record", fontWeight = FontWeight.Bold, color = NavyPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pass ID: ${exeat.id}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Student: ${exeat.studentName} (${exeat.studentIndexNumber})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Category: ${exeat.category}", fontSize = 12.sp)
                Text("Destination: ${exeat.destination}", fontSize = 12.sp)
                Text("Reason: ${exeat.reason}", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Expected Departure: ${timeFormat.format(Date(exeat.departureTime))}", fontSize = 11.sp, color = Color(0xFF475569))
                Text("Expected Return: ${timeFormat.format(Date(exeat.expectedReturnTime))}", fontSize = 11.sp, color = Color(0xFF475569))
                if (exeat.approvedBy != null) {
                    Text("Approved by: ${exeat.approvedBy}", fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                }
                if (exeat.conditionNotes != null) {
                    Text("Conditions: ${exeat.conditionNotes}", fontSize = 11.sp, color = GoldAccent, fontWeight = FontWeight.Medium)
                }
                if (exeat.rejectionReason != null) {
                    Text("Rejection Reason: ${exeat.rejectionReason}", fontSize = 11.sp, color = CrimsonAlert)
                }
                if (workflow?.isOverdue == true) {
                    Text("This pass is overdue.", fontSize = 11.sp, color = CrimsonAlert)
                }
                if (canApprove && exeat.status == "PENDING") {
                    if (exeat.category == "EMERGENCY") {
                        TextButton(onClick = { onAction("override") }) {
                            Text("Emergency override", color = CrimsonAlert)
                        }
                    }
                    TextButton(onClick = { onAction("cancel") }) {
                        Text("Cancel pass", color = CrimsonAlert)
                    }
                }
                if (canApprove && exeat.status in setOf("APPROVED", "DEPARTED")) {
                    if (exeat.status == "APPROVED") {
                        TextButton(onClick = { onAction("reschedule") }) {
                            Text("Reschedule pass", color = NavyPrimary)
                        }
                    }
                    if (workflow?.hasReconciliation != true) {
                        TextButton(onClick = { onAction("reconcile") }) {
                            Text(
                                if (exeat.status == "DEPARTED") {
                                    "Complete follow-up"
                                } else {
                                    "Reconcile missing gate movement"
                                },
                                color = NavyPrimary
                            )
                        }
                    }
                    if (exeat.status == "APPROVED") {
                        TextButton(onClick = { onAction("cancel") }) {
                            Text("Cancel pass", color = CrimsonAlert)
                        }
                    }
                }
                if (canReview && workflow?.hasPendingReview == true) {
                    TextButton(onClick = { onAction("review") }) {
                        Text("Decide post-hoc review", color = NavyPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
private fun ExeatOperationDialog(
    operation: String,
    reason: String,
    onReasonChange: (String) -> Unit,
    departure: String,
    onDepartureChange: (String) -> Unit,
    expectedReturn: String,
    onExpectedReturnChange: (String) -> Unit,
    outcome: String,
    onOutcomeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    var outcomeMenuExpanded by remember(operation) { mutableStateOf(false) }
    val requiresReason = operation in setOf("override", "reschedule", "reconcile")
    val timestampsValid = operation != "reconcile" || when (outcome) {
        "student_out" -> departure.isNotBlank()
        "returned" -> departure.isNotBlank() && expectedReturn.isNotBlank()
        else -> true
    }
    val title = when (operation) {
        "override" -> "Emergency pass override"
        "cancel" -> "Cancel exeat pass?"
        "reschedule" -> "Reschedule exeat"
        "reconcile" -> "Reconcile gate movement"
        "review" -> "Decide post-hoc review"
        else -> "Exeat action"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = NavyPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (operation) {
                    "cancel" -> Text("This will cancel the pass on the school server.")
                    "override" -> OutlinedTextField(
                        value = reason,
                        onValueChange = onReasonChange,
                        label = { Text("Immediate emergency reason *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    "reschedule" -> {
                        Text("Enter ISO date-times in UTC, for example 2026-10-23T12:00:00Z.")
                        OutlinedTextField(
                            value = departure,
                            onValueChange = onDepartureChange,
                            label = { Text("New departure (UTC) *") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = expectedReturn,
                            onValueChange = onExpectedReturnChange,
                            label = { Text("New return (UTC) *") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = reason,
                            onValueChange = onReasonChange,
                            label = { Text("Reason for change *") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "reconcile" -> {
                        Text("Select the verified outcome and provide the evidence or explanation.")
                        Box {
                            OutlinedButton(onClick = { outcomeMenuExpanded = true }) {
                                Text(outcome.replace("_", " ").replaceFirstChar(Char::uppercase))
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = outcomeMenuExpanded,
                                onDismissRequest = { outcomeMenuExpanded = false }
                            ) {
                                listOf(
                                    "unused" to "Pass not used",
                                    "student_out" to "Student travelled and is still out",
                                    "returned" to "Student travelled and returned"
                                ).forEach { (value, label) ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onOutcomeChange(value)
                                            outcomeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = reason,
                            onValueChange = onReasonChange,
                            label = { Text("Evidence / explanation *") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (outcome != "unused") {
                            Text("Enter confirmed or best-known actual times in UTC ISO format.")
                            OutlinedTextField(
                                value = departure,
                                onValueChange = onDepartureChange,
                                label = { Text("Actual departure") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (outcome == "returned") {
                                OutlinedTextField(
                                    value = expectedReturn,
                                    onValueChange = onExpectedReturnChange,
                                    label = { Text("Actual return") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    "review" -> {
                        Text("Choose the review outcome. The authorizer cannot review their own override.")
                        Box {
                            OutlinedButton(onClick = { outcomeMenuExpanded = true }) {
                                Text(outcome.replace("_", " ").replaceFirstChar(Char::uppercase))
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = outcomeMenuExpanded,
                                onDismissRequest = { outcomeMenuExpanded = false }
                            ) {
                                listOf(
                                    "upheld" to "Upheld",
                                    "escalated" to "Escalated",
                                    "rejected" to "Not justified"
                                ).forEach { (value, label) ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onOutcomeChange(value)
                                            outcomeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = reason,
                            onValueChange = onReasonChange,
                            label = { Text("Review notes") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = (!requiresReason || reason.isNotBlank()) && timestampsValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (operation == "cancel") CrimsonAlert else NavyPrimary
                )
            ) {
                Text(
                    text = if (operation == "cancel") "Confirm cancellation" else "Submit to server",
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back") }
        }
    )
}
