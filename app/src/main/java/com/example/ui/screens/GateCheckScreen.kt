package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.data.model.Student
import com.example.data.repository.GateVerificationResult
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.components.QrCameraScanner
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GateCheckScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scanResult by viewModel.gateScanResult.collectAsStateWithLifecycle()
    val scanMessage by viewModel.scanMessage.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()

    var queryInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraActive by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        if (granted) {
            cameraError = null
            cameraActive = true
        } else {
            cameraError = "Camera permission is required to scan a QR pass."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Scanner Viewfinder Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavyPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GATE MOVEMENT LOOKUP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GoldAccent
                    ) {
                        Text(
                            text = "SCHOOL GATE",
                            color = NavyPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF071426))
                        .border(2.dp, GoldAccent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (cameraActive && cameraPermissionGranted) {
                        QrCameraScanner(
                            onCodeScanned = { value ->
                                queryInput = value
                                cameraActive = false
                                cameraError = null
                                viewModel.verifyStudentGateCode(value)
                            },
                            onError = {
                                cameraError = it
                                cameraActive = false
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = GoldAccent,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Point the camera at a school QR pass",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        cameraError = null
                        if (cameraPermissionGranted) {
                            cameraActive = !cameraActive
                        } else {
                            cameraPermissionRequest.launch(Manifest.permission.CAMERA)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                ) {
                    Text(
                        text = if (cameraActive) "Stop QR scanner" else "Start QR scanner",
                        color = NavyPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (cameraError != null) {
                    Text(
                        text = cameraError.orEmpty(),
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // These shortcuts use the same server lookup as manual entry.
                Text(
                    text = "Quick lookup from synced student records:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    students.take(3).forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    queryInput = s.studentId
                                    viewModel.verifyStudentGateCode(s.studentId)
                                }
                        ) {
                            Text(
                                text = s.fullName.split(" ").firstOrNull() ?: s.studentId,
                                color = GoldAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Manual Lookup Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryInput,
                onValueChange = { queryInput = it },
                label = { Text("Admission, index, or pass number") },
                placeholder = { Text("Enter the identifier from the school record") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (queryInput.isNotBlank()) {
                        viewModel.verifyStudentGateCode(queryInput)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                modifier = Modifier.size(54.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Verify")
            }
        }

        if (scanMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFEE2E2),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = scanMessage ?: "",
                    color = CrimsonAlert,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Verification Result Banner / Actions
        if (scanResult != null) {
            Spacer(modifier = Modifier.height(12.dp))
            VerificationResultCard(
                result = scanResult!!,
                onLogMovement = { student, exeatId, type ->
                    viewModel.recordGateMovement(
                        student = student,
                        exeatId = exeatId,
                        movementType = type
                    )
                },
                onClear = { viewModel.clearGateScan() }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "GATE MOVEMENT HISTORY",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NavyPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Gate movement history is not exposed by the mobile API. Departure and return actions are sent to the school server.",
                color = Color(0xFF475569),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun VerificationResultCard(
    result: GateVerificationResult,
    onLogMovement: (student: Student, exeatId: String?, movementType: String) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is GateVerificationResult.ClearedForDeparture -> Color(0xFFF0FDF4)
                is GateVerificationResult.ClearedForReturn -> Color(0xFFEFF6FF)
                else -> Color(0xFFFEF2F2)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (result) {
                        is GateVerificationResult.ClearedForDeparture -> Pair(Icons.Default.CheckCircle, EmeraldGreen)
                        is GateVerificationResult.ClearedForReturn -> Pair(Icons.Default.CheckCircle, Color(0xFF1D4ED8))
                        else -> Pair(Icons.Default.Error, CrimsonAlert)
                    }
                    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (result) {
                            is GateVerificationResult.ClearedForDeparture -> "CLEARED FOR DEPARTURE"
                            is GateVerificationResult.ClearedForReturn -> "CLEARED FOR RETURN"
                            is GateVerificationResult.BlockedNoExeat -> "GATE PASS DENIED"
                            is GateVerificationResult.StudentNotFound -> "STUDENT NOT FOUND"
                            is GateVerificationResult.LookupMessage -> "LOOKUP NEEDS ATTENTION"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = when (result) {
                            is GateVerificationResult.ClearedForDeparture -> EmeraldGreen
                            is GateVerificationResult.ClearedForReturn -> Color(0xFF1D4ED8)
                            else -> CrimsonAlert
                        }
                    )
                }

                IconButton(onClick = onClear) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (result) {
                is GateVerificationResult.ClearedForDeparture -> {
                    Text(
                        text = "${result.student.fullName} (${result.student.studentId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Approved Pass: ${result.exeat.category} to ${result.exeat.destination}",
                        fontSize = 12.sp,
                        color = Color(0xFF334155)
                    )
                    val timeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                    Text(
                        text = "Expected return by: ${timeFormat.format(Date(result.validUntilMs))}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreen
                    )
                    if (result.exeat.conditionNotes != null) {
                        Text(
                            text = "Condition: ${result.exeat.conditionNotes}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onLogMovement(
                                result.student,
                                result.exeat.id,
                                "DEPARTURE"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Gate Departure (Exit)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                is GateVerificationResult.ClearedForReturn -> {
                    Text(
                        text = "${result.student.fullName} (${result.student.studentId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NavyPrimary
                    )
                    if (result.isOverdue) {
                        Text(
                            text = "⚠️ Student is returning OVERDUE! Exeat expired.",
                            color = CrimsonAlert,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = "Returning within authorized time window.",
                            color = EmeraldGreen,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onLogMovement(
                                result.student,
                                result.exeat?.id,
                                "ARRIVAL"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.MeetingRoom, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Gate Return (Arrival)", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                is GateVerificationResult.BlockedNoExeat -> {
                    Text(
                        text = "${result.student.fullName} (${result.student.studentId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NavyPrimary
                    )
                    Text(
                        text = result.reason,
                        fontSize = 12.sp,
                        color = CrimsonAlert,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Current Status: ${result.student.status}. Advise student to return to housemaster.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                is GateVerificationResult.StudentNotFound -> {
                    Text(
                        text = "No student record matching '${result.query}' was found in the school roster.",
                        fontSize = 12.sp,
                        color = CrimsonAlert
                    )
                }

                is GateVerificationResult.LookupMessage -> {
                    Text(
                        text = result.message,
                        fontSize = 12.sp,
                        color = CrimsonAlert
                    )
                }
            }
        }
    }
}
