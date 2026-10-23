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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.House
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
    modifier: Modifier = Modifier
) {
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()

    var studentToReallocate by remember { mutableStateOf<Student?>(null) }
    var reallocationRoomId by remember { mutableStateOf("") }
    var reallocationBedNum by remember { mutableStateOf("1") }

    LazyColumn(
        modifier = modifier
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
                text = "Monitor bed space availability, fire safety capacity limits, and student room placements.",
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
                onReassignClick = { s ->
                    studentToReallocate = s
                    reallocationRoomId = s.roomId
                    reallocationBedNum = s.bedNumber.toString()
                }
            )
        }
    }

    // Reallocation Dialog
    if (studentToReallocate != null) {
        val s = studentToReallocate!!
        val availableRooms = rooms.filter { it.houseId == s.houseId }

        AlertDialog(
            onDismissRequest = { studentToReallocate = null },
            title = {
                Text(
                    text = "Reallocate Bed Space",
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reassign ${s.fullName} (${s.studentId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text("Target Room:", fontSize = 12.sp, color = Color.Gray)
                    var roomMenuExpanded by remember { mutableStateOf(false) }
                    val currentTargetRoom = availableRooms.find { it.id == reallocationRoomId }

                    Box {
                        Button(
                            onClick = { roomMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentTargetRoom?.dormName ?: "Select Room",
                                color = NavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = roomMenuExpanded,
                            onDismissRequest = { roomMenuExpanded = false }
                        ) {
                            availableRooms.forEach { r ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("${r.dormName} (Capacity: ${r.capacity})") },
                                    onClick = {
                                        reallocationRoomId = r.id
                                        roomMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = reallocationBedNum,
                        onValueChange = { reallocationBedNum = it },
                        label = { Text("Bed Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bed = reallocationBedNum.toIntOrNull() ?: 1
                        viewModel.reallocateStudentRoom(
                            studentId = s.id,
                            newRoomId = reallocationRoomId,
                            newBedNumber = bed,
                            actorName = "Duty Housemaster"
                        )
                        studentToReallocate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("Confirm Reassignment", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToReallocate = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun HouseDormSection(
    house: House,
    rooms: List<RoomEntity>,
    students: List<Student>,
    onReassignClick: (Student) -> Unit
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
                Column {
                    Text(
                        text = house.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Housemaster: ${house.housemasterName} (${house.housemasterPhone})",
                        fontSize = 11.sp,
                        color = Color.Gray
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        color = NavyPrimary
                                    )
                                }

                                Text(
                                    text = "${roomOccupants.size} / ${room.capacity} occupied",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isFull) CrimsonAlert else EmeraldGreen
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(NavyPrimary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${occupant.bedNumber}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NavyPrimary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = occupant.fullName,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF334155)
                                                )
                                            }

                                            Text(
                                                text = "Reassign",
                                                fontSize = 11.sp,
                                                color = NavyPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { onReassignClick(occupant) }
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
}
