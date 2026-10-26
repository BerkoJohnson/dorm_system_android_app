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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.House
import com.example.data.model.BedSpace
import com.example.data.model.RoomEntity
import com.example.data.model.Student
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun DormOccupancyScreen(
    viewModel: MainViewModel,
    canManageAllocations: Boolean,
    modifier: Modifier = Modifier
) {
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val bedSpaces by viewModel.bedSpaces.collectAsStateWithLifecycle()
    var selectedStudent by remember { mutableStateOf<Student?>(null) }

    LaunchedEffect(canManageAllocations) {
        if (canManageAllocations) viewModel.loadBedSpaces()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "DORMITORY CAPACITY & ALLOCATIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (canManageAllocations) {
                        "Review house and dormitory capacity, then assign boarding students to rooms."
                    } else {
                        "Monitor server-reported house and dormitory capacity and room assignments."
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            items(houses) { house ->
                val houseRooms = rooms.filter { it.houseId == house.id }
                val houseStudents = students.filter { it.houseId == house.id }

                HouseDormSection(
                    house = house,
                    rooms = houseRooms,
                    students = houseStudents,
                    canManageAllocations = canManageAllocations,
                    onAssign = { selectedStudent = it }
                )
            }
        }

        selectedStudent?.let { student ->
            RoomAllocationDialog(
                student = student,
                bedSpaces = bedSpaces.filter { bedSpace ->
                    rooms.any {
                        it.houseId == student.houseId && it.id == bedSpace.dormitoryId
                    }
                },
                students = students,
                onDismiss = { selectedStudent = null },
                onSelect = { bedSpace ->
                    viewModel.reassignStudentRoom(student, bedSpace)
                    selectedStudent = null
                }
            )
        }
    }
}

@Composable
private fun HouseDormSection(
    house: House,
    rooms: List<RoomEntity>,
    students: List<Student>,
    canManageAllocations: Boolean,
    onAssign: (Student) -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = house.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Housemaster: ${house.housemasterName} (${house.housemasterPhone})",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(android.graphics.Color.parseColor(house.colorHex)).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${students.size} / ${house.capacity} Beds",
                        color = Color(android.graphics.Color.parseColor(house.colorHex)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rooms within this house
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { room ->
                    val roomOccupants = students.filter { it.roomId == room.id }
                    val isFull = roomOccupants.size >= room.capacity

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
                                        imageVector = Icons.Default.MeetingRoom,
                                        contentDescription = null,
                                        tint = NavyPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = room.dormName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = NavyPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Text(
                                    text = "${roomOccupants.size} / ${room.capacity}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isFull) CrimsonAlert else EmeraldGreen,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Occupants mini chips
                            if (roomOccupants.isEmpty()) {
                                Text(
                                    text = "No students currently assigned (Vacant Room)",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    roomOccupants.forEach { occupant ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${occupant.fullName}\n${occupant.assignedRoomName.ifBlank { "No room assigned" }}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF334155),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (canManageAllocations) {
                                                TextButton(onClick = { onAssign(occupant) }) {
                                                    Text("Assign room")
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = occupant.studentId,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    @Composable
                                    private fun RoomAllocationDialog(
                                        student: Student,
                                        bedSpaces: List<BedSpace>,
                                        students: List<Student>,
                                        onDismiss: () -> Unit,
                                        onSelect: (BedSpace) -> Unit
                                    ) {
                                        AlertDialog(
                                            onDismissRequest = onDismiss,
                                            title = { Text("Assign room to ${student.fullName}") },
                                            text = {
                                                if (bedSpaces.isEmpty()) {
                                                    Text("No rooms are available for this dormitory.")
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        bedSpaces.forEach { bedSpace ->
                                                            val occupancy = students.count {
                                                                it.assignedRoomId == bedSpace.id
                                                            }
                                                            val isCurrent = student.assignedRoomId == bedSpace.id
                                                            val isAvailable = occupancy < bedSpace.capacity || isCurrent
                                                            TextButton(
                                                                onClick = { onSelect(bedSpace) },
                                                                enabled = isAvailable,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = "${bedSpace.name} · $occupancy/${bedSpace.capacity}" +
                                                                        if (isCurrent) " · Current" else "",
                                                                    modifier = Modifier.fillMaxWidth()
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = onDismiss) { Text("Cancel") }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
