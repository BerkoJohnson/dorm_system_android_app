package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.RoomEntity
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.data.repository.BoardingRepository
import com.example.data.repository.GateVerificationResult
import com.example.data.repository.SyncManager
import com.example.data.repository.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen {
    DASHBOARD,
    ROLL_CALL,
    EXEATS,
    GATE_CHECK,
    STUDENTS,
    DORMS,
    REPORTS_SYNC
}

data class DashboardMetrics(
    val totalBoarders: Int = 0,
    val presentInDorm: Int = 0,
    val onExeat: Int = 0,
    val sickBay: Int = 0,
    val unexcusedAbsent: Int = 0,
    val pendingExeats: Int = 0,
    val totalHouses: Int = 0,
    val occupancyPercent: Int = 0
)

class MainViewModel(
    private val repository: BoardingRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _currentRole = MutableStateFlow(UserRole.HOUSEMASTER)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedHouseId = MutableStateFlow<String?>("H_AGG")
    val selectedHouseId: StateFlow<String?> = _selectedHouseId.asStateFlow()

    val houses: StateFlow<List<House>> = repository.allHouses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rooms: StateFlow<List<RoomEntity>> = repository.allRooms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exeats: StateFlow<List<ExeatRequest>> = repository.allExeats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movements: StateFlow<List<GateMovement>> = repository.allMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncState> = syncManager.syncState

    // Computed Dashboard Metrics
    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        students,
        exeats,
        houses
    ) { studentList, exeatList, houseList ->
        val total = studentList.size
        val inDorm = studentList.count { it.status == "IN_DORM" }
        val onExeat = studentList.count { it.status == "ON_EXEAT" }
        val sick = studentList.count { it.status == "SICK_BAY" }
        val absent = studentList.count { it.status == "ABSENT" }
        val pendingEx = exeatList.count { it.status == "PENDING" }
        val totalCapacity = houseList.sumOf { it.capacity }.coerceAtLeast(1)
        val occupancy = ((total.toFloat() / totalCapacity) * 100).toInt().coerceAtMost(100)

        DashboardMetrics(
            totalBoarders = total,
            presentInDorm = inDorm,
            onExeat = onExeat,
            sickBay = sick,
            unexcusedAbsent = absent,
            pendingExeats = pendingEx,
            totalHouses = houseList.size,
            occupancyPercent = occupancy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    // Gate Verification State
    private val _gateScanResult = MutableStateFlow<GateVerificationResult?>(null)
    val gateScanResult: StateFlow<GateVerificationResult?> = _gateScanResult.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    fun setRole(role: UserRole) {
        _currentRole.value = role
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSelectedHouse(houseId: String?) {
        _selectedHouseId.value = houseId
    }

    // Roll Call Actions
    fun submitRollCallSession(
        roomId: String,
        sessionType: String,
        markedStatuses: Map<String, String>, // studentId -> Status
        studentNames: Map<String, String>,
        housemasterName: String
    ) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val records = markedStatuses.map { (studentId, status) ->
                AttendanceRecord(
                    id = "ATT_${dateStr}_${sessionType}_${studentId}",
                    dateStr = dateStr,
                    sessionType = sessionType,
                    houseId = _selectedHouseId.value ?: "H_AGG",
                    roomId = roomId,
                    studentId = studentId,
                    studentName = studentNames[studentId] ?: "Student",
                    status = status,
                    markedBy = housemasterName,
                    markedAt = System.currentTimeMillis()
                )
            }
            repository.submitRollCall(records, housemasterName, roomId, sessionType, dateStr)
        }
    }

    // Exeat Actions
    fun approveExeat(exeatId: String, approverName: String, notes: String?) {
        viewModelScope.launch {
            repository.approveExeat(exeatId, approverName, notes)
        }
    }

    fun rejectExeat(exeatId: String, approverName: String, reason: String) {
        viewModelScope.launch {
            repository.rejectExeat(exeatId, approverName, reason)
        }
    }

    fun createExeatRequest(
        student: Student,
        category: String,
        reason: String,
        destination: String,
        departureTime: Long,
        expectedReturnTime: Long,
        actorName: String
    ) {
        viewModelScope.launch {
            val exeat = ExeatRequest(
                id = "EX_" + System.currentTimeMillis().toString().takeLast(6),
                studentId = student.id,
                studentName = student.fullName,
                studentIndexNumber = student.studentId,
                houseId = student.houseId,
                category = category,
                reason = reason,
                destination = destination,
                departureTime = departureTime,
                expectedReturnTime = expectedReturnTime,
                status = "PENDING"
            )
            repository.submitExeat(exeat, _currentRole.value.displayName, actorName)
        }
    }

    // Gate Actions
    fun verifyStudentGateCode(codeOrIndex: String) {
        viewModelScope.launch {
            _scanMessage.value = null
            val result = repository.verifyGatePass(codeOrIndex)
            _gateScanResult.value = result
        }
    }

    fun clearGateScan() {
        _gateScanResult.value = null
        _scanMessage.value = null
    }

    fun recordGateMovement(
        student: Student,
        exeatId: String?,
        movementType: String,
        officerName: String,
        gateName: String,
        remarks: String
    ) {
        viewModelScope.launch {
            repository.recordGateMovement(
                student = student,
                exeatId = exeatId,
                movementType = movementType,
                officerName = officerName,
                gateName = gateName,
                remarks = remarks
            )
            _scanMessage.value = "Movement logged: ${student.fullName} marked as $movementType."
            _gateScanResult.value = null
        }
    }

    // Room Reallocation
    fun reallocateStudentRoom(studentId: String, newRoomId: String, newBedNumber: Int, actorName: String) {
        viewModelScope.launch {
            repository.reassignRoom(
                studentId,
                newRoomId,
                newBedNumber,
                _currentRole.value.displayName,
                actorName
            )
        }
    }

    // Sync
    fun triggerSync() {
        viewModelScope.launch {
            syncManager.performSync()
        }
    }

    fun updateSyncConfig(url: String, token: String) {
        syncManager.updateConfig(url, token)
    }
}
