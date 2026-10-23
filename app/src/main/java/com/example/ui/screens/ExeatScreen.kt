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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    modifier: Modifier = Modifier
) {
    val exeats by viewModel.exeats.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val houses by viewModel.houses.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedExeatForDetail by remember { mutableStateOf<ExeatRequest?>(null) }
    var exeatToApprove by remember { mutableStateOf<ExeatRequest?>(null) }
    var exeatToReject by remember { mutableStateOf<ExeatRequest?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Rejection reason input
    var rejectReason by remember { mutableStateOf("") }
    // Approval notes input
    var approvalNotes by remember { mutableStateOf("") }

    val filteredExeats = when (selectedFilter) {
        "PENDING" -> exeats.filter { it.status == "PENDING" }
        "APPROVED" -> exeats.filter { it.status == "APPROVED" }
        "DEPARTED" -> exeats.filter { it.status == "DEPARTED" }
        "OVERDUE" -> exeats.filter {
            it.status == "DEPARTED" && System.currentTimeMillis() > it.expectedReturnTime
        }
        "RETURNED" -> exeats.filter { it.status == "RETURNED" }
        else -> exeats
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = NavyPrimary,
                contentColor = GoldAccent
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Exeat")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            val filterOptions = listOf(
                "ALL" to "All (${exeats.size})",
                "PENDING" to "Pending (${exeats.count { it.status == "PENDING" }})",
                "APPROVED" to "Approved (${exeats.count { it.status == "APPROVED" }})",
                "DEPARTED" to "Departed (${exeats.count { it.status == "DEPARTED" }})",
                "OVERDUE" to "Overdue (${exeats.count { it.status == "DEPARTED" && System.currentTimeMillis() > it.expectedReturnTime }})",
                "RETURNED" to "Returned (${exeats.count { it.status == "RETURNED" }})"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterOptions) { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) NavyPrimary else Color(0xFFF1F5F9),
                        modifier = Modifier.clickable { selectedFilter = key }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFF475569),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                            onApprove = { exeatToApprove = exeat },
                            onReject = { exeatToReject = exeat },
                            onClick = { selectedExeatForDetail = exeat }
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
            onDismiss = { selectedExeatForDetail = null }
        )
    }
}

@Composable
private fun ExeatCard(
    exeat: ExeatRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onClick: () -> Unit
) {
    val isOverdue = exeat.status == "DEPARTED" && System.currentTimeMillis() > exeat.expectedReturnTime

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category & Pass ID
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = exeat.id,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                }

                // Status Pill
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
                    color = statusBg
                ) {
                    Text(
                        text = if (isOverdue) "⚠️ OVERDUE" else exeat.status,
                        color = statusFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = exeat.studentName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary
            )
            Text(
                text = "${exeat.studentIndexNumber} • Destination: ${exeat.destination}",
                fontSize = 12.sp,
                color = Color(0xFF4B5563)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Reason: ${exeat.reason}",
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val timeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                Text(
                    text = "Out: ${timeFormat.format(Date(exeat.departureTime))}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "Return by: ${timeFormat.format(Date(exeat.expectedReturnTime))}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOverdue) CrimsonAlert else Color(0xFF64748B)
                )
            }

            // Approval Buttons for Pending Exeats
            if (exeat.status == "PENDING") {
                Spacer(modifier = Modifier.height(10.dp))
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

@Composable
private fun CreateExeatDialog(
    students: List<Student>,
    onDismiss: () -> Unit,
    onSubmit: (student: Student, category: String, reason: String, destination: String, depHours: Long, retHours: Long) -> Unit
) {
    var selectedStudentId by remember { mutableStateOf(students.firstOrNull()?.id ?: "") }
    var category by remember { mutableStateOf("MEDICAL") }
    var reason by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var durationHours by remember { mutableStateOf("6") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Issue Exeat Pass", fontWeight = FontWeight.Bold, color = NavyPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            color = NavyPrimary
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

                // Category Buttons
                Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val categories = listOf("MEDICAL", "ACADEMIC", "FAMILY_EMERGENCY", "TOWN_PASS")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (category == cat) NavyPrimary else Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Text(
                                text = cat.take(4),
                                color = if (category == cat) Color.White else Color(0xFF475569),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val student = students.find { it.id == selectedStudentId }
                    if (student != null && destination.isNotBlank() && reason.isNotBlank()) {
                        val hours = durationHours.toLongOrNull() ?: 6L
                        onSubmit(student, category, reason, destination, 0L, hours)
                    }
                },
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
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)) {
                Text("Close", color = Color.White)
            }
        }
    )
}
