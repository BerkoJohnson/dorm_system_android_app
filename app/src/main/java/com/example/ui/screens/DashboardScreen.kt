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
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.House
import com.example.data.model.UserRole
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.DashboardMetrics
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Role-Specific Welcome Card
            WelcomeBanner(
                role = currentRole,
                metrics = metrics,
                onNavigate = { viewModel.navigateTo(it) }
            )
        }

        item {
            // Metrics Grid
            Text(
                text = "BOARDING OPERATIONS OVERVIEW",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "In Dorms",
                    value = "${metrics.presentInDorm}",
                    sub = "Safe on campus",
                    color = EmeraldGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "On Exeat",
                    value = "${metrics.onExeat}",
                    sub = "Cleared passes",
                    color = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Sick Bay",
                    value = "${metrics.sickBay}",
                    sub = "Infirmary care",
                    color = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Flagged Absent",
                    value = "${metrics.unexcusedAbsent}",
                    sub = "Needs follow-up",
                    color = CrimsonAlert,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Role Actions
        item {
            Text(
                text = "QUICK ACTIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Checklist,
                    label = "Roll Call",
                    color = NavyPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(AppScreen.ROLL_CALL) }
                )
                QuickActionButton(
                    icon = Icons.Default.QrCodeScanner,
                    label = "Scan QR",
                    color = GoldAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(AppScreen.GATE_CHECK) }
                )
                QuickActionButton(
                    icon = Icons.Default.ExitToApp,
                    label = "Exeats",
                    badge = if (metrics.pendingExeats > 0) "${metrics.pendingExeats}" else null,
                    color = Color(0xFF0284C7),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(AppScreen.EXEATS) }
                )
                QuickActionButton(
                    icon = Icons.Default.Bed,
                    label = "Dorms",
                    color = Color(0xFF059669),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(AppScreen.DORMS) }
                )
            }
        }

        // Houses Capacity Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BOARDING HOUSES (${houses.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "View All →",
                    fontSize = 12.sp,
                    color = NavyPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.navigateTo(AppScreen.DORMS) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(houses) { house ->
                    val houseStudents = students.filter { it.houseId == house.id }
                    HouseCard(
                        house = house,
                        studentCount = houseStudents.size,
                        onClick = {
                            viewModel.setSelectedHouse(house.id)
                            viewModel.navigateTo(AppScreen.ROLL_CALL)
                        }
                    )
                }
            }
        }

        // Recent Gate Movements Stream
        item {
            Text(
                text = "RECENT GATE MOVEMENTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (movements.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No gate movements logged today yet.",
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    movements.take(3).forEach { mov ->
                        GateMovementRow(movement = mov)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WelcomeBanner(
    role: UserRole,
    metrics: DashboardMetrics,
    onNavigate: (AppScreen) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyPrimary)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.eden_logo),
                        contentDescription = "Eden Creative Systems",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (role) {
                                UserRole.HOUSEMASTER -> "Housemaster Duty Portal"
                                UserRole.GATE_OFFICER -> "Campus Gate Security"
                                UserRole.ADMINISTRATOR -> "School Boarding Admin"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Eden Creative Systems • SHS Boarding",
                            color = GoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GoldAccent
                ) {
                    Text(
                        text = "${metrics.totalBoarders} Boarders",
                        color = NavyPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val bannerMessage = when (role) {
                UserRole.HOUSEMASTER ->
                    if (metrics.pendingExeats > 0)
                        "${metrics.pendingExeats} pending exeat requests awaiting your review and approval."
                    else
                        "All dorms accounted for. Ready for evening roll call inspection."
                UserRole.GATE_OFFICER ->
                    "${metrics.onExeat} students currently on authorized exeat passes. Scan QR codes at the gate."
                UserRole.ADMINISTRATOR ->
                    "Overall dormitory occupancy is at ${metrics.occupancyPercent}%. System running in offline-first mode."
            }

            Text(
                text = bannerMessage,
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        when (role) {
                            UserRole.HOUSEMASTER -> onNavigate(AppScreen.ROLL_CALL)
                            UserRole.GATE_OFFICER -> onNavigate(AppScreen.GATE_CHECK)
                            UserRole.ADMINISTRATOR -> onNavigate(AppScreen.REPORTS_SYNC)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when (role) {
                            UserRole.HOUSEMASTER -> "Start Roll Call"
                            UserRole.GATE_OFFICER -> "Open QR Scanner"
                            UserRole.ADMINISTRATOR -> "View Audit Logs"
                        },
                        color = NavyPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = sub,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(CrimsonAlert),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
private fun HouseCard(
    house: House,
    studentCount: Int,
    onClick: () -> Unit
) {
    val occupancyFraction = (studentCount.toFloat() / house.capacity.coerceAtLeast(1)).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = house.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NavyPrimary
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(android.graphics.Color.parseColor(house.colorHex)).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = house.gender.take(1),
                        color = Color(android.graphics.Color.parseColor(house.colorHex)),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = house.housemasterName,
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Occupancy",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "$studentCount / ${house.capacity}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { occupancyFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(android.graphics.Color.parseColor(house.colorHex)),
                trackColor = Color(0xFFF1F5F9)
            )
        }
    }
}

@Composable
fun GateMovementRow(movement: com.example.data.model.GateMovement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isDepart = movement.movementType == "DEPARTURE"
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDepart) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDepart) Icons.Default.ExitToApp else Icons.Default.MeetingRoom,
                        contentDescription = null,
                        tint = if (isDepart) Color(0xFFB45309) else EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = movement.studentName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NavyPrimary
                    )
                    Text(
                        text = "${movement.studentIndexNumber} • ${movement.officerName}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (movement.movementType == "DEPARTURE") Color(0xFFFFFBEB) else Color(0xFFF0FDF4)
                ) {
                    Text(
                        text = movement.movementType,
                        color = if (movement.movementType == "DEPARTURE") Color(0xFFB45309) else EmeraldGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(movement.timestamp)),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
