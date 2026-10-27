package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.UserRole
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoleSelectorBar(
    currentRole: UserRole,
    syncStatus: String,
    isOnline: Boolean,
    lastSyncTime: Long?,
    pendingOutboxCount: Int,
    failedOutboxCount: Int,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    schoolName: String = "School",
    schoolLogoUrl: String = "",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NavyPrimary,
        shadowElevation = 0.dp
    ) {
        val isError = failedOutboxCount > 0 ||
            (!isOnline && !syncStatus.contains("queued", ignoreCase = true))
        val statusColor = when {
            failedOutboxCount > 0 -> Color(0xFFEF4444)
            isError -> Color(0xFFEF4444)
            !isOnline -> GoldAccent
            else -> EmeraldGreen
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(NavyPrimary, Color(0xFF0E3A7A))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TenantLogo(
                logoUrl = schoolLogoUrl,
                contentDescription = "$schoolName logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF123B76))
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (currentRole) {
                        UserRole.HOUSEMASTER -> Icons.Default.MeetingRoom
                        UserRole.ASSISTANT_HOUSEMASTER -> Icons.Default.MeetingRoom
                        UserRole.ACTING_HOUSEMASTER -> Icons.Default.MeetingRoom
                        UserRole.HOUSE_TUTOR -> Icons.Default.MeetingRoom
                        UserRole.SENIOR_HOUSEMASTER -> Icons.Default.AdminPanelSettings
                        UserRole.HEADMASTER -> Icons.Default.AdminPanelSettings
                        UserRole.MATRON -> Icons.Default.LocalHospital
                        UserRole.MEDICAL -> Icons.Default.LocalHospital
                        UserRole.GATE_OFFICER -> Icons.Default.Security
                        UserRole.ADMINISTRATOR -> Icons.Default.AdminPanelSettings
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = currentRole.badgeTitle,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = currentRole.displayName,
                            color = Color(0xFFB8C7D8),
                            fontSize = 9.sp
                        )
                    }
                }

            }

            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Account options",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sign out") },
                        leadingIcon = {
                            Icon(Icons.Default.ExitToApp, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onLogoutClick()
                        }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF123B76).copy(alpha = 0.7f))
                    .clickable(enabled = currentRole == UserRole.ADMINISTRATOR) {
                        onSyncClick()
                    }
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                Text(
                    text = when {
                        failedOutboxCount > 0 ->
                            "Sync attention · Failed: $failedOutboxCount"
                        syncStatus.contains("suspend", ignoreCase = true) ||
                            syncStatus.contains("denied", ignoreCase = true) ||
                            syncStatus.contains("revoked", ignoreCase = true) ->
                            "Access restricted"
                        pendingOutboxCount > 0 && !isOnline ->
                            "Offline · Queued: $pendingOutboxCount"
                        pendingOutboxCount > 0 -> "Queued: $pendingOutboxCount unsynced"
                        !isOnline -> "Offline"
                        else -> "Online"
                    },
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (pendingOutboxCount > 0) {
                    Text(
                        text = "Pending: $pendingOutboxCount",
                        color = Color(0xFFB8C7D8),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
                val lastSynced = lastSyncTime?.let {
                    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        .format(Date(it))
                } ?: "never"
                Text(
                    text = "Last sync: $lastSynced",
                    color = Color(0xFFB8C7D8),
                    fontSize = 9.sp,
                    maxLines = 1
                )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync status: $syncStatus",
                    tint = GoldAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
