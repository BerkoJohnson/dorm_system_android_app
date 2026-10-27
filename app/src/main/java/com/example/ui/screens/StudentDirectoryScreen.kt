package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Student
import com.example.ui.components.StudentIdCard
import com.example.ui.components.printStudentReference
import com.example.data.remote.DjangoApiClient
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.StudentRecordInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDirectoryScreen(
    viewModel: MainViewModel,
    apiClient: DjangoApiClient,
    canManageStudents: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allStudents by viewModel.students.collectAsStateWithLifecycle()
    val students = viewModel.houseScopedStudents(allStudents)
    val houses by viewModel.houses.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val permissions by viewModel.apiPermissions.collectAsStateWithLifecycle()
    val studentRecordMessage by viewModel.studentRecordMessage.collectAsStateWithLifecycle()
    val visibleHouses = houses.filter { house ->
        !currentRole.isHouseScoped || house.id == permissions.houseId
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedHouseFilter by remember { mutableStateOf<String?>("ALL") }
    var viewingStudentIdCard by remember { mutableStateOf<Student?>(null) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var showCreateEditor by remember { mutableStateOf(false) }
    var photoTarget by remember { mutableStateOf<Student?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val student = photoTarget
        if (uri != null && student != null) {
            viewModel.uploadStudentPhoto(student, uri) { updated ->
                viewingStudentIdCard = updated
                photoTarget = null
            }
        } else {
            photoTarget = null
        }
    }

    val filtered = students.filter { s ->
        val matchesHouse = selectedHouseFilter == "ALL" || s.houseId == selectedHouseFilter
        val matchesQuery = searchQuery.isBlank() ||
                s.fullName.contains(searchQuery, ignoreCase = true) ||
                s.studentId.contains(searchQuery, ignoreCase = true) ||
                s.classForm.contains(searchQuery, ignoreCase = true)
        matchesHouse && matchesQuery
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (canManageStudents) {
            Button(
                onClick = {
                    viewModel.clearStudentRecordMessage()
                    showCreateEditor = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add student")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        studentRecordMessage?.let { message ->
            Text(message, color = NavyPrimary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Search TextField
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search students") },
            placeholder = { Text("Name, index or form") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NavyPrimary)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // House Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!currentRole.isHouseScoped) {
                item {
                    val isSelected = selectedHouseFilter == "ALL"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) NavyPrimary else Color(0xFFF1F5F9),
                        modifier = Modifier.clickable { selectedHouseFilter = "ALL" }
                    ) {
                        Text(
                            text = "All Houses (${students.size})",
                            color = if (isSelected) Color.White else Color(0xFF475569),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            items(visibleHouses) { house ->
                val isSelected = selectedHouseFilter == house.id
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) NavyPrimary else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable { selectedHouseFilter = house.id }
                ) {
                    Text(
                        text = house.name,
                        color = if (isSelected) Color.White else Color(0xFF475569),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Enrolled boarders (${filtered.size})",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = NavyPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No students match the criteria.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { student ->
                    val house = houses.find { it.id == student.houseId }
                    StudentRowItem(
                        student = student,
                        houseName = house?.name ?: "House",
                        onClick = {
                            viewingStudentIdCard = student
                            viewModel.loadStudentDetail(student) {
                                if (viewingStudentIdCard?.id == student.id) viewingStudentIdCard = it
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal to view Digital Student ID Card
    if (viewingStudentIdCard != null) {
        val s = viewingStudentIdCard!!
        val house = houses.find { it.id == s.houseId }

        BasicAlertDialog(
            onDismissRequest = { viewingStudentIdCard = null },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = { viewingStudentIdCard = null },
                        modifier = Modifier
                            .background(Color.White, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = NavyPrimary)
                    }
                }
                StudentIdCard(
                    student = s,
                    house = house,
                    schoolName = apiClient.configuredSchoolName,
                    schoolAddress = apiClient.configuredSchoolAddress,
                    schoolPhone = apiClient.configuredSchoolPhone,
                    schoolEmail = apiClient.configuredSchoolEmail,
                    schoolLogoUrl = apiClient.configuredSchoolLogoUrl
                )
                Button(
                    onClick = {
                        printStudentReference(
                            context = context,
                            student = s,
                            house = house,
                            schoolName = apiClient.configuredSchoolName
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Print / save student reference")
                }
                Text(
                    text = "Programme: ${s.program.ifBlank { "Unknown" }}",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF334155),
                    fontSize = 12.sp
                )
                Text(
                    text = "House: ${house?.name ?: "Unknown"} · Bed: ${
                        s.bedNumber.takeIf { it > 0 }?.toString() ?: "Unknown"
                    }${s.assignedRoomName.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF334155),
                    fontSize = 12.sp
                )
                Text(
                    text = "Guardian: ${s.guardianName.ifBlank { "Unknown" }} · " +
                        "${s.guardianRelationship.ifBlank { "Relationship unknown" }} · " +
                        s.guardianPhone.ifBlank { "Phone unknown" },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF334155),
                    fontSize = 12.sp
                )
                Text(
                    text = "Medical / emergency alert: ${
                        s.emergencyNotes.ifBlank { "Unknown" }
                    }",
                    modifier = Modifier.fillMaxWidth(),
                    color = if (s.emergencyNotes.isBlank() || s.emergencyNotes == "Unknown") {
                        Color(0xFF64748B)
                    } else {
                        Color(0xFFB91C1C)
                    },
                    fontSize = 12.sp
                )
                if (canManageStudents) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.clearStudentRecordMessage()
                                editingStudent = s
                            }
                        ) { Text("Edit student") }
                        Button(
                            onClick = {
                                photoTarget = s
                                photoPicker.launch("image/*")
                            }
                        ) { Text("Upload photo") }
                    }
                }
            }
        }
    }

    if (showCreateEditor || editingStudent != null) {
        val initialStudent = editingStudent
        StudentRecordEditor(
            student = initialStudent,
            houses = visibleHouses,
            message = studentRecordMessage,
            onDismiss = {
                showCreateEditor = false
                editingStudent = null
            },
            onSave = { input ->
                viewModel.saveStudentRecord(initialStudent, input) { saved ->
                    showCreateEditor = false
                    editingStudent = null
                    viewingStudentIdCard = saved
                }
            }
        )
    }
}

@Composable
private fun StudentRecordEditor(
    student: Student?,
    houses: List<com.example.data.model.House>,
    message: String?,
    onDismiss: () -> Unit,
    onSave: (StudentRecordInput) -> Unit
) {
    var studentId by remember(student?.id) { mutableStateOf(student?.studentId.orEmpty()) }
    var fullName by remember(student?.id) { mutableStateOf(student?.fullName.orEmpty()) }
    var gender by remember(student?.id) { mutableStateOf(student?.gender.orEmpty()) }
    var form by remember(student?.id) { mutableStateOf(student?.classForm.orEmpty()) }
    var program by remember(student?.id) { mutableStateOf(student?.program.orEmpty()) }
    var houseId by remember(student?.id) { mutableStateOf(student?.houseId.orEmpty()) }
    var guardianName by remember(student?.id) { mutableStateOf(student?.guardianName.orEmpty()) }
    var guardianPhone by remember(student?.id) { mutableStateOf(student?.guardianPhone.orEmpty()) }
    var guardianRelationship by remember(student?.id) {
        mutableStateOf(student?.guardianRelationship.orEmpty())
    }
    var emergencyNotes by remember(student?.id) {
        mutableStateOf(student?.emergencyNotes?.takeUnless { it == "Unknown" }.orEmpty())
    }
    var houseMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (student == null) "Add student" else "Edit student") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!message.isNullOrBlank()) {
                    Text(message, color = Color(0xFFB91C1C), fontSize = 12.sp)
                }
                OutlinedTextField(
                    studentId,
                    { studentId = it },
                    label = { Text("Index/admission number") }
                )
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") })
                OutlinedTextField(gender, { gender = it }, label = { Text("Gender") })
                OutlinedTextField(form, { form = it }, label = { Text("Form / class") })
                OutlinedTextField(program, { program = it }, label = { Text("Programme") })
                Box {
                    OutlinedTextField(
                        value = houses.firstOrNull { it.id == houseId }?.name.orEmpty(),
                        onValueChange = {},
                        label = { Text("House") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { houseMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = houseMenuExpanded,
                        onDismissRequest = { houseMenuExpanded = false }
                    ) {
                        houses.forEach { house ->
                            DropdownMenuItem(
                                text = { Text(house.name) },
                                onClick = {
                                    houseId = house.id
                                    houseMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    guardianName,
                    { guardianName = it },
                    label = { Text("Guardian name") }
                )
                OutlinedTextField(
                    guardianPhone,
                    { guardianPhone = it },
                    label = { Text("Guardian phone") }
                )
                OutlinedTextField(
                    guardianRelationship,
                    { guardianRelationship = it },
                    label = { Text("Guardian relationship") }
                )
                OutlinedTextField(
                    emergencyNotes,
                    { emergencyNotes = it },
                    label = { Text("Medical / emergency notes") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        StudentRecordInput(
                            studentId = studentId,
                            fullName = fullName,
                            gender = gender,
                            classForm = form,
                            program = program,
                            houseId = houseId,
                            guardianName = guardianName,
                            guardianPhone = guardianPhone,
                            guardianRelationship = guardianRelationship,
                            emergencyNotes = emergencyNotes
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StudentRowItem(
    student: Student,
    houseName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
                val initials = student.fullName.split(" ")
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .take(2)
                    .joinToString("")
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(NavyPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = student.studentId,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(
                            student.classForm.ifBlank { "Form unknown" },
                            student.program.ifBlank { "Programme unknown" },
                            houseName
                        )
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = student.guardianPhone.ifBlank { "Guardian phone unknown" },
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val (statusBg, statusFg) = when (student.status) {
                    "IN_DORM" -> Pair(Color(0xFFDCFCE7), EmeraldGreen)
                    "ON_EXEAT" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                    "SICK_BAY" -> Pair(Color(0xFFEDE9FE), Color(0xFF6D28D9))
                    else -> Pair(Color(0xFFFEE2E2), Color(0xFFDC2626))
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusBg
                ) {
                    Text(
                        text = student.status.replace("_", " "),
                        color = statusFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = "View ID Card", fontSize = 11.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
