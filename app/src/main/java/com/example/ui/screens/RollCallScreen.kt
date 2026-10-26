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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.model.Student
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun RollCallScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val allStudents by viewModel.students.collectAsStateWithLifecycle()
    val selectedHouseId by viewModel.selectedHouseId.collectAsStateWithLifecycle()
    val pendingRollCallCount by viewModel.pendingRollCallCount.collectAsStateWithLifecycle()
    val outboxMessage by viewModel.rollCallOutboxMessage.collectAsStateWithLifecycle()

    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    var sessionType by remember { mutableStateOf("EVENING_ROLL_CALL") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var submittedMessage by remember { mutableStateOf<String?>(null) }
    var submitFailed by remember { mutableStateOf(false) }

    // Dropdown expansion states
    var houseMenuExpanded by remember { mutableStateOf(false) }
    var roomMenuExpanded by remember { mutableStateOf(false) }
    var sessionMenuExpanded by remember { mutableStateOf(false) }

    val houseRooms = rooms.filter { it.houseId == selectedHouseId }
    val currentRoom = rooms.find { it.id == selectedRoomId } ?: houseRooms.firstOrNull()
    val currentHouse = houses.find { it.id == selectedHouseId } ?: houses.firstOrNull()
    // The mobile API creates roll-call sessions by dormitory, not individual room.
    val roomStudents = allStudents.filter { it.roomId == currentRoom?.id }

    // Map of studentId -> status (PRESENT, ABSENT, ON_EXEAT, SICK_BAY)
    val statusMap = remember { mutableStateMapOf<String, String>() }

    // Update defaults when roomStudents change
    LaunchedEffect(roomStudents) {
        statusMap.clear()
        roomStudents.forEach { student ->
            statusMap[student.id] = if (student.status == "ON_EXEAT") "ON_EXEAT" else "UNMARKED"
        }
    }

    val presentCount = statusMap.values.count { it == "PRESENT" }
    val absentCount = statusMap.values.count { it == "ABSENT" }
    val exeatCount = statusMap.values.count { it == "ON_EXEAT" }
    val sickCount = statusMap.values.count { it == "SICK_BAY" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Dormitory & House Selection Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Session Selector
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = NavyPrimary.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sessionMenuExpanded = true }
                        ) {
                            Text(
                                text = when (sessionType) {
                                    "EVENING_ROLL_CALL" -> "Evening Roll Call"
                                    "MORNING_INSPECTION" -> "Morning Inspection"
                                    else -> "Curfew Check"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = sessionMenuExpanded,
                            onDismissRequest = { sessionMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Evening Roll Call (Curfew)") },
                                onClick = {
                                    sessionType = "EVENING_ROLL_CALL"
                                    sessionMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Morning Inspection") },
                                onClick = {
                                    sessionType = "MORNING_INSPECTION"
                                    sessionMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Emergency Roll Call") },
                                onClick = {
                                    sessionType = "CURFEW_CHECK"
                                    sessionMenuExpanded = false
                                }
                            )
                        }
                    }

                    // House Selector
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldAccent.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { houseMenuExpanded = true }
                        ) {
                            Text(
                                text = currentHouse?.name ?: "Select House",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = houseMenuExpanded,
                            onDismissRequest = { houseMenuExpanded = false }
                        ) {
                            houses.forEach { h ->
                                DropdownMenuItem(
                                    text = { Text(h.name) },
                                    onClick = {
                                        viewModel.setSelectedHouse(h.id)
                                        selectedRoomId = rooms.firstOrNull { it.houseId == h.id }?.id
                                        houseMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dormitory Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { roomMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 8.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentRoom?.dormName ?: "Select Dormitory",
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "▼", fontSize = 10.sp, color = Color.Gray)
                        }

                        DropdownMenu(
                            expanded = roomMenuExpanded,
                            onDismissRequest = { roomMenuExpanded = false }
                        ) {
                            houseRooms.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text("${r.dormName} (Cap: ${r.capacity})") },
                                    onClick = {
                                        selectedRoomId = r.id
                                        roomMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Mark All Present Button
                    TextButton(
                        onClick = {
                            roomStudents.forEach { s ->
                                if (statusMap[s.id] != "ON_EXEAT") {
                                    statusMap[s.id] = "PRESENT"
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "Mark all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Summary Tally Strip
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = NavyPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val accountedCount = presentCount + exeatCount + sickCount
                val totalCount = roomStudents.size.coerceAtLeast(1)
                val fraction = (accountedCount.toFloat() / totalCount).coerceIn(0f, 1f)
                val percent = (fraction * 100).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${roomStudents.size} Students in Dormitory",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "$accountedCount/${roomStudents.size} ($percent%)",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                androidx.compose.material3.LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = EmeraldGreen,
                    trackColor = Color(0xFF1E293B)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Present $presentCount",
                        color = Color(0xFF4ADE80),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Exeat $exeatCount",
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sick $sickCount",
                        color = Color(0xFFC084FC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Absent $absentCount",
                        color = Color(0xFFF87171),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (submittedMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (submitFailed) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = submittedMessage ?: "",
                    color = if (submitFailed) CrimsonAlert else EmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (pendingRollCallCount > 0 || outboxMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = outboxMessage
                            ?: "$pendingRollCallCount roll-call action(s) waiting for the server. They will send automatically when connected.",
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Student Roster
        if (roomStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No students currently assigned to this dormitory.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(roomStudents) { student ->
                    RollCallStudentCard(
                        student = student,
                        currentStatus = statusMap[student.id] ?: "UNMARKED",
                        onStatusChange = { newStatus ->
                            statusMap[student.id] = newStatus
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lock & Submit Button
        Button(
            onClick = { showConfirmDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
            enabled = roomStudents.isNotEmpty() &&
                statusMap.size == roomStudents.size &&
                statusMap.values.none { it == "UNMARKED" }
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Lock & Submit Roll Call ($presentCount/${roomStudents.size})",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Submit Official Roll Call",
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to lock and submit this attendance session for ${currentRoom?.dormName}?",
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Summary: $presentCount Present, $absentCount Absent, $exeatCount on Exeat.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = NavyPrimary
                    )
                    if (absentCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠️ Notice: $absentCount students marked as unexcused will be flagged for Senior Housemaster inquiry.",
                            color = CrimsonAlert,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        submittedMessage = "Submitting roll call to the school server…"
                        submitFailed = false
                        viewModel.submitRollCallSession(
                            roomId = currentRoom?.id.orEmpty(),
                            sessionType = sessionType,
                            markedStatuses = statusMap.toMap(),
                            onResult = { result ->
                                submittedMessage = result
                                submitFailed = result.startsWith("Roll call was not submitted:")
                            }
                        )
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("Confirm & Lock", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun RollCallStudentCard(
    student: Student,
    currentStatus: String,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bed Number & Student Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NavyPrimary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${student.bedNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NavyPrimary
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = student.studentId,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )
                        if (student.status == "ON_EXEAT") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "ON EXEAT",
                                    color = Color(0xFFB45309),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Status Buttons
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusToggleButton(
                    label = "—",
                    isSelected = currentStatus == "UNMARKED",
                    activeColor = Color(0xFF64748B),
                    onClick = { onStatusChange("UNMARKED") }
                )
                // Present
                StatusToggleButton(
                    label = "P",
                    isSelected = currentStatus == "PRESENT",
                    activeColor = EmeraldGreen,
                    onClick = { onStatusChange("PRESENT") }
                )
                // Exeat
                StatusToggleButton(
                    label = "EX",
                    isSelected = currentStatus == "ON_EXEAT",
                    activeColor = AmberWarning,
                    onClick = { onStatusChange("ON_EXEAT") }
                )
                // Sick Bay
                StatusToggleButton(
                    label = "SB",
                    isSelected = currentStatus == "SICK_BAY",
                    activeColor = Color(0xFF7C3AED),
                    onClick = { onStatusChange("SICK_BAY") }
                )
                // Absent
                StatusToggleButton(
                    label = "A",
                    isSelected = currentStatus == "ABSENT",
                    activeColor = CrimsonAlert,
                    onClick = { onStatusChange("ABSENT") }
                )
            }
        }
    }
}

@Composable
private fun StatusToggleButton(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) activeColor else Color(0xFFF1F5F9))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF475569),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
