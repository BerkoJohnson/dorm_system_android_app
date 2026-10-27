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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.theme.EdenBrightGreen
import com.example.ui.theme.EdenDeepNavy
import com.example.ui.theme.EdenElectricBlue
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel

@Composable
fun OperationsHubScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val permissions by viewModel.apiPermissions.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadNotifications()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "BOARDING OPERATIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Student records, dorm spaces, saved changes, and school data.",
                fontSize = 12.sp,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            val failedCount = syncState.failedOutboxCount
            HubNavigationCard(
                icon = Icons.Default.CloudSync,
                iconTint = if (failedCount > 0) Color(0xFFB91C1C) else EdenElectricBlue,
                title = "Delivery outbox",
                subtitle = "Inspect queued school changes and resolve failed deliveries",
                badge = if (failedCount > 0) "$failedCount Failed" else
                    "${syncState.pendingOutboxCount} Pending",
                badgeColor = if (failedCount > 0) Color(0xFFFEE2E2) else Color(0xFFDBEAFE),
                badgeTextColor = if (failedCount > 0) Color(0xFFB91C1C) else EdenElectricBlue,
                onClick = { viewModel.navigateTo(AppScreen.OUTBOX) }
            )
        }

        item {
            HubNavigationCard(
                icon = Icons.Default.Notifications,
                iconTint = EdenElectricBlue,
                title = "Help & school data",
                subtitle = "Offline work, saved changes, access and setup guidance",
                badge = "Help",
                onClick = { viewModel.navigateTo(AppScreen.HELP) }
            )
        }

        if (permissions.studentsView) item {
            HubNavigationCard(
                icon = Icons.Default.People,
                iconTint = EdenDeepNavy,
                title = "Student Directory & ID Cards",
                subtitle = "${students.size} Enrolled Boarders across ${houses.size} Houses",
                badge = "${students.size} Students",
                onClick = { viewModel.navigateTo(AppScreen.STUDENTS) }
            )
        }

        if (permissions.studentsView) item {
            HubNavigationCard(
                icon = Icons.Default.Bed,
                iconTint = EdenBrightGreen,
                title = "Dormitory Occupancy",
                subtitle = "${rooms.size} Dorm Rooms • View assigned bed spaces",
                badge = "${rooms.size} Rooms",
                onClick = { viewModel.navigateTo(AppScreen.DORMS) }
            )
        }

        if (permissions.rollCall || permissions.isSeniorHousemaster || permissions.isAdmin) item {
            HubNavigationCard(
                icon = Icons.Default.History,
                iconTint = EdenElectricBlue,
                title = "Historical Attendance Logs",
                subtitle = "Past Evening Roll Calls, Morning Inspections & Summaries",
                badge = "Past Sessions",
                onClick = { viewModel.navigateTo(AppScreen.ROLL_CALL_HISTORY) }
            )
        }

        // School notifications
        item {
            val count = notifications.size
            HubNavigationCard(
                icon = Icons.Default.Notifications,
                iconTint = Color(0xFFD97706),
                title = "School Notifications",
                subtitle = "View alerts and mark them as read",
                badge = if (count > 0) "$count Loaded" else "Alerts",
                onClick = { viewModel.navigateTo(AppScreen.NOTIFICATIONS) }
            )
        }

        if (permissions.isAdmin || permissions.reports || permissions.auditView) item {
            HubNavigationCard(
                icon = Icons.Default.CloudSync,
                iconTint = EdenDeepNavy,
                title = "School data sync & audit history",
                subtitle = if (syncState.isOnline) {
                    "School data is connected and ready to refresh."
                } else {
                    "Saved school data is available offline."
                },
                badge = if (syncState.isOnline) "Connected" else "Offline",
                badgeColor = if (syncState.isOnline) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                badgeTextColor = if (syncState.isOnline) EdenBrightGreen else Color(0xFFB45309),
                onClick = { viewModel.navigateTo(AppScreen.REPORTS_SYNC) }
            )
        }
    }
}

@Composable
private fun HubNavigationCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color = Color(0xFFF1F5F9),
    badgeTextColor: Color = EdenDeepNavy,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = EdenDeepNavy
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badge,
                        color = badgeTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Navigate",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
