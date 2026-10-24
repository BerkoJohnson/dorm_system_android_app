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
import com.example.data.model.userRoleFromServerRole
import com.example.data.repository.BoardingRepository
import com.example.data.repository.GateVerificationResult
import com.example.data.repository.SyncManager
import com.example.data.repository.SyncState
import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoDataSync
import java.io.IOException
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    ROLL_CALL_HISTORY,
    NOTIFICATIONS,
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

data class ApiPermissions(
    val isAdmin: Boolean = false,
    val houseId: String? = null,
    val studentsView: Boolean = false,
    val exeatsView: Boolean = false,
    val exeatsCreate: Boolean = false,
    val exeatsApprove: Boolean = false,
    val gateOperate: Boolean = false,
    val rollCall: Boolean = false,
    val reports: Boolean = false,
    val exeatReview: Boolean = false
)

data class ExeatWorkflow(
    val isEmergencyOverride: Boolean,
    val hasPendingReview: Boolean,
    val hasReconciliation: Boolean,
    val isOverdue: Boolean
)

data class MobileNotification(
    val id: String,
    val title: String,
    val message: String,
    val kind: String,
    val createdAt: String,
    val isRead: Boolean
)

data class RollCallSessionItem(
    val id: String,
    val sessionType: String,
    val date: String,
    val status: String,
    val dormitory: String,
    val total: Int,
    val present: Int,
    val absent: Int,
    val onExeat: Int,
    val infirmary: Int,
    val unmarked: Int
)

data class RollCallEntryItem(
    val id: String,
    val studentId: String,
    val studentName: String,
    val admissionNumber: String,
    val status: String,
    val remarks: String,
    val gateConflict: String?
)

class MainViewModel(
    private val repository: BoardingRepository,
    private val syncManager: SyncManager,
    private val dataSync: DjangoDataSync,
    private val apiClient: DjangoApiClient
) : ViewModel() {
    private val sessionDataMutex = Mutex()

    private val _currentRole = MutableStateFlow(UserRole.HOUSEMASTER)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedHouseId = MutableStateFlow<String?>(null)
    val selectedHouseId: StateFlow<String?> = _selectedHouseId.asStateFlow()

    private val _apiPermissions = MutableStateFlow(ApiPermissions())
    val apiPermissions: StateFlow<ApiPermissions> = _apiPermissions.asStateFlow()

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
    private val _serverDashboardMetrics = MutableStateFlow<DashboardMetrics?>(null)

    // Computed Dashboard Metrics
    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        students,
        exeats,
        houses,
        _serverDashboardMetrics
    ) { studentList, exeatList, houseList, serverMetrics ->
        if (serverMetrics != null) return@combine serverMetrics
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

    private val _notifications = MutableStateFlow<List<MobileNotification>>(emptyList())
    val notifications: StateFlow<List<MobileNotification>> = _notifications.asStateFlow()
    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()
    private val _rollCallSessions = MutableStateFlow<List<RollCallSessionItem>>(emptyList())
    val rollCallSessions: StateFlow<List<RollCallSessionItem>> = _rollCallSessions.asStateFlow()
    private val _rollCallEntries = MutableStateFlow<List<RollCallEntryItem>>(emptyList())
    val rollCallEntries: StateFlow<List<RollCallEntryItem>> = _rollCallEntries.asStateFlow()
    private val _selectedRollCallSession = MutableStateFlow<RollCallSessionItem?>(null)
    val selectedRollCallSession: StateFlow<RollCallSessionItem?> =
        _selectedRollCallSession.asStateFlow()
    private val _rollCallHistoryMessage = MutableStateFlow<String?>(null)
    val rollCallHistoryMessage: StateFlow<String?> = _rollCallHistoryMessage.asStateFlow()

    internal fun setAuthenticatedRole(role: UserRole) {
        _currentRole.value = role
    }

    fun setApiPermissions(capabilities: JSONObject?, features: JSONObject? = null) {
        _apiPermissions.value = apiPermissionsFrom(capabilities, features)
    }

    private fun apiPermissionsFrom(
        capabilities: JSONObject?,
        features: JSONObject?
    ): ApiPermissions {
        val exeatFeatureEnabled = features?.optBoolean("exeat_gate", true) ?: true
        val rollCallFeatureEnabled = features?.optBoolean("roll_call", true) ?: true
        val houseId = capabilities?.optString("house_id")
            ?.takeUnless { it.isBlank() || it == "null" }
        val isAdmin = capabilities?.optBoolean("is_admin") == true
        return ApiPermissions(
            isAdmin = isAdmin,
            houseId = houseId,
            studentsView = capabilities?.optBoolean("students_view") == true,
            exeatsView = capabilities?.optBoolean("exeats_view") == true && exeatFeatureEnabled,
            exeatsCreate = capabilities?.optBoolean("exeats_create") == true && exeatFeatureEnabled,
            exeatsApprove = capabilities?.optBoolean("exeats_approve") == true && exeatFeatureEnabled,
            gateOperate = capabilities?.optBoolean("gate_operate") == true && exeatFeatureEnabled,
            rollCall = capabilities?.optBoolean("roll_call") == true && rollCallFeatureEnabled,
            reports = capabilities?.optBoolean("reports") == true &&
                (features?.optBoolean("advanced_reports", true) ?: true),
            exeatReview = capabilities?.optBoolean("is_admin") == true ||
                capabilities?.optBoolean("is_senior_housemaster") == true
        )
    }

    fun clearSensitiveState(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            apiClient.clearSession()
            sessionDataMutex.withLock {
                repository.clearSessionData()
            }
            _apiPermissions.value = ApiPermissions()
            _currentRole.value = UserRole.HOUSEMASTER
            _selectedHouseId.value = null
            _serverDashboardMetrics.value = null
            _notifications.value = emptyList()
            _unreadNotificationCount.value = 0
            _rollCallSessions.value = emptyList()
            _rollCallEntries.value = emptyList()
            _selectedRollCallSession.value = null
            _rollCallHistoryMessage.value = null
            _gateScanResult.value = null
            _scanMessage.value = null
            onComplete()
        }
    }

    suspend fun clearCachedSchoolData() {
        sessionDataMutex.withLock {
            repository.clearSessionData()
        }
        _serverDashboardMetrics.value = null
        _notifications.value = emptyList()
        _unreadNotificationCount.value = 0
        _rollCallSessions.value = emptyList()
        _rollCallEntries.value = emptyList()
        _selectedRollCallSession.value = null
        _gateScanResult.value = null
        _scanMessage.value = null
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun setSelectedHouse(houseId: String?) {
        _selectedHouseId.value = houseId
    }

    fun loadNotifications() {
        viewModelScope.launch {
            try {
                val response = apiClient.get(
                    "notifications/",
                    mapOf("page" to "1", "page_size" to "100")
                )
                val items = response.optJSONArray("items") ?: JSONArray()
                _notifications.value = (0 until items.length()).mapNotNull { index ->
                    val json = items.optJSONObject(index) ?: return@mapNotNull null
                    val id = json.optString("id").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    MobileNotification(
                        id = id,
                        title = json.optString("title", "School notification"),
                        message = json.optString("message"),
                        kind = json.optString("kind_display", json.optString("kind")),
                        createdAt = json.optString("created_at"),
                        isRead = json.optBoolean("is_read")
                    )
                }
                val me = apiClient.get("auth/me/")
                val unread = me.optJSONObject("unread")
                _unreadNotificationCount.value =
                    (unread?.optInt("notifications") ?: 0) +
                    (unread?.optInt("roll_call_notifications") ?: 0)
            } catch (error: IOException) {
                syncManager.reportActionError("Could not load notifications: ${error.message}")
            } catch (error: IllegalStateException) {
                syncManager.reportActionError("Could not load notifications: ${error.message}")
            }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            remoteAction("Notification marked as read") {
                val response = apiClient.post("notifications/$notificationId/read/")
                _notifications.value = _notifications.value.map { notification ->
                    if (notification.id == notificationId) notification.copy(isRead = true)
                    else notification
                }
                val unread = response.optJSONObject("unread")
                if (unread != null) {
                    _unreadNotificationCount.value =
                        unread.optInt("notifications") + unread.optInt("roll_call_notifications")
                } else {
                    loadNotifications()
                }
            }
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            remoteAction("All notifications marked as read") {
                apiClient.post("notifications/read-all/")
                _notifications.value = _notifications.value.map { it.copy(isRead = true) }
                _unreadNotificationCount.value = 0
            }
        }
    }

    fun loadRollCallHistory() {
        viewModelScope.launch {
            try {
                _rollCallHistoryMessage.value = null
                val response = apiClient.get(
                    "roll-call/sessions/",
                    mapOf("page" to "1", "page_size" to "100")
                )
                val items = response.optJSONArray("items") ?: JSONArray()
                _rollCallSessions.value = (0 until items.length()).mapNotNull { index ->
                    val json = items.optJSONObject(index) ?: return@mapNotNull null
                    val id = json.optString("id").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val counts = json.optJSONObject("counts") ?: JSONObject()
                    RollCallSessionItem(
                        id = id,
                        sessionType = json.optString("session_type_display", json.optString("session_type")),
                        date = json.optString("date"),
                        status = json.optString("status_display", json.optString("status")),
                        dormitory = json.optJSONObject("dormitory")?.optString("name").orEmpty(),
                        total = counts.optInt("total_students"),
                        present = counts.optInt("present"),
                        absent = counts.optInt("absent"),
                        onExeat = counts.optInt("on_exeat"),
                        infirmary = counts.optInt("infirmary"),
                        unmarked = counts.optInt("unmarked")
                    )
                }
            } catch (error: IOException) {
                _rollCallHistoryMessage.value = "Could not load roll-call history: ${error.message}"
            } catch (error: IllegalStateException) {
                _rollCallHistoryMessage.value = "Could not load roll-call history: ${error.message}"
            }
        }
    }

    fun openRollCallSession(session: RollCallSessionItem) {
        viewModelScope.launch {
            try {
                _rollCallHistoryMessage.value = null
                val response = apiClient.get("roll-call/sessions/${session.id}/")
                val entries = response.optJSONArray("entries") ?: JSONArray()
                _rollCallEntries.value = (0 until entries.length()).mapNotNull { index ->
                    val entry = entries.optJSONObject(index) ?: return@mapNotNull null
                    val student = entry.optJSONObject("student") ?: return@mapNotNull null
                    val id = entry.optString("id").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val conflict = entry.optJSONObject("gate_conflict")
                    RollCallEntryItem(
                        id = id,
                        studentId = student.optString("id"),
                        studentName = student.optString("full_name", "Student"),
                        admissionNumber = student.optString("admission_no"),
                        status = entry.optString("status"),
                        remarks = entry.optString("remarks").takeUnless { it == "null" }.orEmpty(),
                        gateConflict = conflict?.optString("note")
                            ?.takeUnless { it.isBlank() || it == "null" }
                    )
                }
                _selectedRollCallSession.value = session
            } catch (error: IOException) {
                _rollCallHistoryMessage.value = "Could not load session details: ${error.message}"
            } catch (error: IllegalStateException) {
                _rollCallHistoryMessage.value = "Could not load session details: ${error.message}"
            }
        }
    }

    fun updateRollCallEntry(
        session: RollCallSessionItem,
        entry: RollCallEntryItem,
        status: String,
        remarks: String
    ) {
        viewModelScope.launch {
            try {
                val serverStatus = mapOf(
                    "Present" to "Present",
                    "Absent" to "Absent",
                    "On Exeat" to "On Exeat",
                    "Infirmary" to "Infirmary",
                    "Activity" to "Activity",
                    "Unmarked" to "Unmarked"
                )[status] ?: throw IllegalArgumentException("Choose a valid attendance status.")
                apiClient.post(
                    "roll-call/sessions/${session.id}/entries/${entry.id}/",
                    JSONObject().put("status", serverStatus).put("remarks", remarks)
                )
                openRollCallSession(session)
                loadRollCallHistory()
                syncManager.reportActionSuccess("Roll-call entry updated on the school server.")
            } catch (error: IOException) {
                _rollCallHistoryMessage.value = "Could not update attendance: ${error.message}"
                syncManager.reportActionError(_rollCallHistoryMessage.value.orEmpty())
            } catch (error: IllegalStateException) {
                _rollCallHistoryMessage.value = "Could not update attendance: ${error.message}"
                syncManager.reportActionError(_rollCallHistoryMessage.value.orEmpty())
            } catch (error: IllegalArgumentException) {
                _rollCallHistoryMessage.value = "Could not update attendance: ${error.message}"
                syncManager.reportActionError(_rollCallHistoryMessage.value.orEmpty())
            }
        }
    }

    fun closeRollCallSession() {
        _selectedRollCallSession.value = null
        _rollCallEntries.value = emptyList()
    }

    // Roll Call Actions
    fun submitRollCallSession(
        roomId: String,
        sessionType: String,
        markedStatuses: Map<String, String>,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val houseId = _selectedHouseId.value
                    ?: throw IllegalArgumentException("Choose a house before submitting roll call.")
                if (roomId.isBlank()) {
                    throw IllegalArgumentException("Choose a dormitory before submitting roll call.")
                }
                val serverType = when (sessionType) {
                    "EVENING_ROLL_CALL" -> "Evening"
                    "MORNING_INSPECTION" -> "Morning"
                    else -> "Special"
                }
                val start = apiClient.post(
                    "roll-call/sessions/",
                    JSONObject()
                        .put("house_id", houseId)
                        .put("dormitory_id", roomId)
                        .put("session_type", serverType)
                        .put("inspection_date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                )
                val sessionId = start.optString("id")
                if (sessionId.isBlank()) throw IllegalStateException("The server did not create a roll-call session.")
                val entries = start.optJSONArray("entries") ?: JSONArray()
                val entriesToSubmit = (0 until entries.length()).mapNotNull(entries::optJSONObject)
                val missingStudentIds = entriesToSubmit.mapNotNull { entry ->
                    val studentId = entry.optJSONObject("student")?.optString("id").orEmpty()
                    val status = markedStatuses[studentId]
                    if (status.isNullOrBlank() || status == "UNMARKED") studentId else null
                }
                if (missingStudentIds.isNotEmpty()) {
                    throw IllegalStateException(
                        "The server roster contains ${missingStudentIds.size} student(s) not marked in the app. Refresh the roster and try again."
                    )
                }
                val apiStatuses = mapOf(
                    "PRESENT" to "Present",
                    "ABSENT" to "Absent",
                    "ON_EXEAT" to "On Exeat",
                    "SICK_BAY" to "Infirmary"
                )
                for (entry in entriesToSubmit) {
                    val studentId = entry.optJSONObject("student")?.optString("id").orEmpty()
                    val requestedStatus = markedStatuses.getValue(studentId)
                    val status = apiStatuses[requestedStatus]
                        ?: throw IllegalArgumentException("Unsupported attendance status: $requestedStatus")
                    apiClient.post(
                        "roll-call/sessions/$sessionId/entries/${entry.optString("id")}/",
                        JSONObject().put("status", status)
                    )
                }
                apiClient.post("roll-call/sessions/$sessionId/complete/")
                syncManager.reportActionSuccess("Roll call submitted successfully.")
                onResult("Roll call submitted to the school server.")
                triggerSync()
            } catch (error: IOException) {
                val message = "Roll call was not submitted: ${error.message}"
                syncManager.reportActionError(message)
                onResult(message)
            } catch (error: IllegalStateException) {
                val message = "Roll call was not submitted: ${error.message}"
                syncManager.reportActionError(message)
                onResult(message)
            } catch (error: IllegalArgumentException) {
                val message = "Roll call was not submitted: ${error.message}"
                syncManager.reportActionError(message)
                onResult(message)
            }
        }
    }

    // Exeat Actions
    fun approveExeat(exeatId: String, approverName: String, notes: String?) {
        viewModelScope.launch {
            remoteAction("Exeat approved") {
                apiClient.post(
                    "exeats/$exeatId/approve/",
                    JSONObject().put("approval_remarks", notes.orEmpty())
                )
            }
        }
    }

    fun rejectExeat(exeatId: String, approverName: String, reason: String) {
        viewModelScope.launch {
            remoteAction("Exeat rejected") {
                apiClient.post(
                    "exeats/$exeatId/reject/",
                    JSONObject().put("approval_remarks", reason)
                )
            }
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
            remoteAction("Exeat request submitted") {
                apiClient.post(
                    "exeats/",
                    JSONObject()
                        .put("student_id", student.id)
                        .put("exeat_type", category)
                        .put("reason", reason)
                        .put("destination", destination)
                        .put("expected_departure", Instant.ofEpochMilli(departureTime).toString())
                        .put("expected_return", Instant.ofEpochMilli(expectedReturnTime).toString())
                )
            }
        }
    }

    fun loadExeatDetail(
        exeat: ExeatRequest,
        onLoaded: (ExeatRequest, ExeatWorkflow) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val detail = apiClient.get("exeats/${exeat.id}/")
                val mapped = dataSync.exeatFrom(detail)
                    ?: throw IllegalStateException("The school server returned invalid exeat details.")
                val reviews = detail.optJSONArray("reviews") ?: JSONArray()
                val hasPendingReview = (0 until reviews.length()).any { index ->
                    reviews.optJSONObject(index)?.optString("status") == "pending"
                }
                onLoaded(
                    mapped,
                    ExeatWorkflow(
                        isEmergencyOverride = detail.optBoolean("is_emergency_override"),
                        hasPendingReview = hasPendingReview,
                        hasReconciliation = detail.optJSONObject("reconciliation") != null,
                        isOverdue = detail.optBoolean("is_overdue")
                    )
                )
            } catch (error: IOException) {
                syncManager.reportActionError("Could not load exeat details: ${error.message}")
            } catch (error: IllegalStateException) {
                syncManager.reportActionError("Could not load exeat details: ${error.message}")
            }
        }
    }

    fun performExeatOperation(
        exeatId: String,
        operation: String,
        payload: JSONObject = JSONObject(),
        onSuccess: () -> Unit = {}
    ) {
        val endpoint = when (operation) {
            "override" -> "exeats/$exeatId/override/"
            "cancel" -> "exeats/$exeatId/cancel/"
            "reschedule" -> "exeats/$exeatId/reschedule/"
            "reconcile" -> "exeats/$exeatId/reconcile/"
            "review" -> "exeats/$exeatId/review/"
            else -> {
                syncManager.reportActionError("Unsupported exeat action.")
                return
            }
        }
        viewModelScope.launch {
            remoteAction("Exeat action completed") {
                apiClient.post(endpoint, payload)
                onSuccess()
            }
        }
    }

    fun loadStudentDetail(student: Student, onLoaded: (Student) -> Unit) {
        viewModelScope.launch {
            try {
                val detail = apiClient.get("students/${student.id}/")
                val mapped = dataSync.studentFrom(detail)
                    ?: throw IllegalStateException("The school server returned invalid student details.")
                onLoaded(mapped)
            } catch (error: IOException) {
                syncManager.reportActionError("Could not load student details: ${error.message}")
            } catch (error: IllegalStateException) {
                syncManager.reportActionError("Could not load student details: ${error.message}")
            }
        }
    }

    // Gate Actions
    fun verifyStudentGateCode(codeOrIndex: String) {
        viewModelScope.launch {
            _scanMessage.value = null
            try {
                val response = apiClient.post(
                    "gate/verify/",
                    JSONObject().put("code", codeOrIndex.trim())
                )
                val match = response.optString("match")
                if (match == "none") {
                    _gateScanResult.value = GateVerificationResult.StudentNotFound(codeOrIndex)
                    return@launch
                }
                if (match == "ambiguous") {
                    val warning = response.optJSONArray("warnings")
                        ?.optString(0)
                        .orEmpty()
                        .ifBlank { "Several passes match. Enter the full student or pass number." }
                    _gateScanResult.value = GateVerificationResult.LookupMessage(warning)
                    return@launch
                }
                val studentJson = response.optJSONObject("student")
                    ?: throw IllegalStateException("The school server returned a gate result without a student.")
                val student = dataSync.studentFrom(studentJson)
                    ?: throw IllegalStateException("The school server returned invalid student data.")
                val exeat = response.optJSONObject("exeat")?.let(dataSync::exeatFrom)
                val warnings = response.optJSONArray("warnings")
                val warningText = (0 until (warnings?.length() ?: 0))
                    .mapNotNull { warnings?.optString(it)?.takeIf(String::isNotBlank) }
                    .joinToString("\n")
                _gateScanResult.value = when {
                    response.optBoolean("can_depart") && exeat != null ->
                        GateVerificationResult.ClearedForDeparture(
                            student,
                            exeat,
                            exeat.expectedReturnTime
                        )
                    response.optBoolean("can_return") && exeat != null ->
                        GateVerificationResult.ClearedForReturn(
                            student,
                            exeat,
                            warningText.contains("overdue", ignoreCase = true)
                        )
                    else -> GateVerificationResult.BlockedNoExeat(
                        student,
                        warningText.ifBlank { "The school server did not clear this pass for movement." }
                    )
                }
            } catch (error: IOException) {
                _gateScanResult.value = null
                _scanMessage.value = "Server verification failed: ${error.message}"
                syncManager.reportActionError(_scanMessage.value.orEmpty())
            } catch (error: IllegalStateException) {
                _gateScanResult.value = null
                _scanMessage.value = "Server verification failed: ${error.message}"
                syncManager.reportActionError(_scanMessage.value.orEmpty())
            }
        }
    }

    fun clearGateScan() {
        _gateScanResult.value = null
        _scanMessage.value = null
    }

    fun recordGateMovement(
        student: Student,
        exeatId: String?,
        movementType: String
    ) {
        viewModelScope.launch {
            remoteAction("Gate movement recorded") {
                val route = if (movementType == "DEPARTURE") "gate/depart/" else "gate/return/"
                val passId = exeatId
                    ?: throw IllegalArgumentException("A verified exeat pass is required to record movement.")
                apiClient.post(route, JSONObject().put("exeat_id", passId))
                _scanMessage.value = "Movement recorded by the school server for ${student.fullName}."
                _gateScanResult.value = null
            }
        }
    }

    // Sync
    fun triggerSync() {
        if (!apiClient.hasSession) return
        viewModelScope.launch {
            try {
                sessionDataMutex.withLock {
                    if (!apiClient.hasSession) return@withLock
                    syncManager.updateConfig(apiClient.baseUrl)
                    syncManager.performSync {
                        val snapshot = dataSync.refresh { capabilities, features ->
                            val newRole = userRoleFromServerRole(
                                capabilities.optString("role")
                            ) ?: throw IllegalStateException(
                                "The school server returned an unsupported staff role."
                            )
                            val newPermissions = apiPermissionsFrom(capabilities, features)
                            if (
                                newRole != _currentRole.value ||
                                newPermissions != _apiPermissions.value
                            ) {
                                repository.clearSessionData()
                                _serverDashboardMetrics.value = null
                            }
                            _currentRole.value = newRole
                            _selectedHouseId.value = newPermissions.houseId
                            _apiPermissions.value = newPermissions
                        }
                        _serverDashboardMetrics.value = snapshot.metrics
                        _selectedHouseId.value = snapshot.defaultHouseId
                        setApiPermissions(snapshot.capabilities, snapshot.features)
                    }
                }
            } catch (_: IOException) {
                // SyncManager publishes the server error to the sync status flow.
            } catch (_: IllegalStateException) {
                // SyncManager publishes invalid server data to the sync status flow.
            }
        }
    }

    private suspend fun remoteAction(label: String, action: suspend () -> Unit) {
        try {
            action()
            syncManager.reportActionSuccess("$label on the school server.")
            triggerSync()
        } catch (error: IOException) {
            syncManager.reportActionError("$label failed: ${error.message ?: "server unavailable."}")
        } catch (error: IllegalStateException) {
            syncManager.reportActionError("$label failed: ${error.message ?: "invalid server response."}")
        } catch (error: IllegalArgumentException) {
            syncManager.reportActionError("$label failed: ${error.message ?: "invalid request."}")
        }
    }

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                apiClient.logout()
            } catch (_: IOException) {
                apiClient.clearSession()
            } finally {
                sessionDataMutex.withLock {
                    repository.clearSessionData()
                }
                _apiPermissions.value = ApiPermissions()
                _currentRole.value = UserRole.HOUSEMASTER
                _selectedHouseId.value = null
                _serverDashboardMetrics.value = null
                _notifications.value = emptyList()
                _unreadNotificationCount.value = 0
                _rollCallSessions.value = emptyList()
                _rollCallEntries.value = emptyList()
                _selectedRollCallSession.value = null
                _rollCallHistoryMessage.value = null
                _gateScanResult.value = null
                _scanMessage.value = null
                onComplete()
            }
        }
    }
}
