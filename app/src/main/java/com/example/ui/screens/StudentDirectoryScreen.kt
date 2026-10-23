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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Student
import com.example.ui.components.StudentIdCard
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDirectoryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val houses by viewModel.houses.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedHouseFilter by remember { mutableStateOf<String?>("ALL") }
    var viewingStudentIdCard by remember { mutableStateOf<Student?>(null) }

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
        // Search TextField
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Students by name, index, form...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NavyPrimary)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // House Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            items(houses) { house ->
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "ENROLLED BOARDERS (${filtered.size})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFF64748B)
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
                        onClick = { viewingStudentIdCard = student }
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
                modifier = Modifier.fillMaxWidth(),
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
                    house = house
                )
            }
        }
    }
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
        shape = RoundedCornerShape(10.dp),
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

                Column {
                    Text(
                        text = student.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NavyPrimary
                    )
                    Text(
                        text = "${student.studentId} • ${student.classForm} • $houseName",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = "View ID Card", fontSize = 10.sp, color = NavyPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
