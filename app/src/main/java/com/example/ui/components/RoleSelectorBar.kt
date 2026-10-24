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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserRole
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary

@Composable
fun RoleSelectorBar(
    currentRole: UserRole,
    syncStatus: String,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NavyPrimary,
        shadowElevation = 0.dp
    ) {
        val isError = syncStatus.contains("fail", ignoreCase = true) ||
            syncStatus.contains("unreachable", ignoreCase = true)
        val isConnecting = syncStatus.contains("connect", ignoreCase = true)
        val syncRotation by animateFloatAsState(
            targetValue = if (isConnecting) 360f else 0f,
            animationSpec = spring(stiffness = 180f),
            label = "sync-icon-rotation"
        )

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
            Image(
                painter = painterResource(id = com.example.R.drawable.eden_logo),
                contentDescription = "Eden Creative Systems",
                modifier = Modifier.size(32.dp)
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
                        UserRole.HOUSE_TUTOR -> Icons.Default.MeetingRoom
                        UserRole.SENIOR_HOUSEMASTER -> Icons.Default.AdminPanelSettings
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

            if (currentRole == UserRole.ADMINISTRATOR) Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF123B76).copy(alpha = 0.7f))
                    .clickable { onSyncClick() }
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val syncColor by animateColorAsState(
                    targetValue = when {
                        isError -> Color(0xFFEF4444)
                        isConnecting -> GoldAccent
                        else -> EmeraldGreen
                    },
                    animationSpec = spring(stiffness = 500f),
                    label = "sync-status-color"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(syncColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isError -> "Offline"
                        isConnecting -> "Syncing"
                        syncStatus.contains("ready", ignoreCase = true) -> "Ready"
                        else -> "Synced"
                    },
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync status: $syncStatus",
                    tint = GoldAccent,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = syncRotation }
                )
            }
        }
    }
}
