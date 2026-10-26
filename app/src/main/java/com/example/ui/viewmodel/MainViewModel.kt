package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.BedSpace
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.MobileAuditEvent
import com.example.data.model.MobileReport
import com.example.data.model.MobileReportSection
import com.example.data.model.RemoteMutationOutboxItem
import com.example.data.model.RoomEntity
import com.example.data.model.RollCallOutboxItem
import com.example.data.model.SmsCampaign
import com.example.data.model.Student
import com.example.data.model.UserRole
import com.example.data.model.userRoleFromServerRole
import com.example.data.repository.BoardingRepository
import com.example.data.repository.GateVerificationResult
import com.example.data.repository.SyncManager
import com.example.data.repository.SyncState
import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoApiException
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

private const val LIST_PAGE_SIZE = 100

enum class AppScreen {
    DASHBOARD,
    ROLL_CALL,
    EXEATS,
    GATE_CHECK,
    OPERATIONS,
    STUDENTS,
    DORMS,
    ROLL_CALL_HISTORY,
    NOTIFICATIONS,
    REPORTS_SYNC,
    SMS_CAMPAIGNS
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
    val isSeniorHousemaster: Boolean = false,
    val houseId: String? = null,
    val studentsView: Boolean = false,
    val studentsManage: Boolean = false,
    val exeatsView: Boolean = false,
    val exeatsCreate: Boolean = false,
    val exeatsApprove: Boolean = false,
    val gateOperate: Boolean = false,
    val rollCall: Boolean = false,
    val reports: Boolean = false,
    val auditView: Boolean = false,
    val smsDispatchEnabled: Boolean = false,
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
    private val rollCallOutboxMutex = Mutex()
    private val remoteMutationOutboxMutex = Mutex()
    private var currentUserId: String? = null
    private val _pendingRollCallCount = MutableStateFlow(0)
    val pendingRollCallCount: StateFlow<Int> = _pendingRollCallCount.asStateFlow()
    private val _rollCallOutboxMessage = MutableStateFlow<String?>(null)
    val rollCallOutboxMessage: StateFlow<String?> = _rollCallOutboxMessage.asStateFlow()

    private val _currentRole = MutableStateFlow(UserRole.HOUSEMASTER)
    val currentRole: StateFlow<UserRole> = _currentRole.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedHouseId = MutableStateFlow<String?>(null)
    val selectedHouseId: StateFlow<String?> = _selectedHouseId.asStateFlow()

    private val _apiPermissions = MutableStateFlow(ApiPermissions())
    val apiPermissions: StateFlow<ApiPermissions> = _apiPermissions.asStateFlow()
    private val _bedSpaces = MutableStateFlow<List<BedSpace>>(emptyList())
    val bedSpaces: StateFlow<List<BedSpace>> = _bedSpaces.asStateFlow()
    private val _mobileReport = MutableStateFlow<MobileReport?>(null)
    val mobileReport: StateFlow<MobileReport?> = _mobileReport.asStateFlow()
    private var selectedReportType = "housing"
    private val _isLoadingReport = MutableStateFlow(false)
    val isLoadingReport: StateFlow<Boolean> = _isLoadingReport.asStateFlow()
    private val _hasMoreReportRows = MutableStateFlow(false)
    val hasMoreReportRows: StateFlow<Boolean> = _hasMoreReportRows.asStateFlow()
    private var reportPage = 1
    private val _auditEvents = MutableStateFlow<List<MobileAuditEvent>>(emptyList())
    val auditEvents: StateFlow<List<MobileAuditEvent>> = _auditEvents.asStateFlow()
    private val _isLoadingAudit = MutableStateFlow(false)
    val isLoadingAudit: StateFlow<Boolean> = _isLoadingAudit.asStateFlow()
    private val _hasMoreAuditEvents = MutableStateFlow(false)
    val hasMoreAuditEvents: StateFlow<Boolean> = _hasMoreAuditEvents.asStateFlow()
    private var auditPage = 1
    private val _smsCampaigns = MutableStateFlow<List<SmsCampaign>>(emptyList())
    val smsCampaigns: StateFlow<List<SmsCampaign>> = _smsCampaigns.asStateFlow()
    private val _smsEnabled = MutableStateFlow(false)
    val smsEnabled: StateFlow<Boolean> = _smsEnabled.asStateFlow()
    private val _isLoadingSmsCampaigns = MutableStateFlow(false)
    val isLoadingSmsCampaigns: StateFlow<Boolean> = _isLoadingSmsCampaigns.asStateFlow()
    private val _operationsMessage = MutableStateFlow<String?>(null)
    val operationsMessage: StateFlow<String?> = _operationsMessage.asStateFlow()

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
    private val _isLoadingNotifications = MutableStateFlow(false)
    val isLoadingNotifications: StateFlow<Boolean> = _isLoadingNotifications.asStateFlow()
    private val _hasMoreNotifications = MutableStateFlow(false)
    val hasMoreNotifications: StateFlow<Boolean> = _hasMoreNotifications.asStateFlow()
    private val _notificationMessage = MutableStateFlow<String?>(null)
    val notificationMessage: StateFlow<String?> = _notificationMessage.asStateFlow()
    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()
    private var notificationPage = 1
    private val _rollCallSessions = MutableStateFlow<List<RollCallSessionItem>>(emptyList())
    val rollCallSessions: StateFlow<List<RollCallSessionItem>> = _rollCallSessions.asStateFlow()
    private val _isLoadingRollCallHistory = MutableStateFlow(false)
    val isLoadingRollCallHistory: StateFlow<Boolean> = _isLoadingRollCallHistory.asStateFlow()
    private val _hasMoreRollCallHistory = MutableStateFlow(false)
    val hasMoreRollCallHistory: StateFlow<Boolean> = _hasMoreRollCallHistory.asStateFlow()
    private var rollCallHistoryPage = 1
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

    fun setAuthenticatedUserId(userId: String?) {
        currentUserId = userId?.takeIf(String::isNotBlank)
        viewModelScope.launch {
            refreshRollCallOutboxCount()
            processRollCallOutbox()
        }
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
        val isSeniorHousemaster =
            capabilities?.optBoolean("is_senior_housemaster") == true
        val advancedReportsEnabled =
            features?.optBoolean("advanced_reports", true) ?: true
        return ApiPermissions(
            isAdmin = isAdmin,
            isSeniorHousemaster = isSeniorHousemaster,
            houseId = houseId,
            studentsView = capabilities?.optBoolean("students_view") == true,
            studentsManage = capabilities?.optBoolean("students_manage") == true,
            exeatsView = capabilities?.optBoolean("exeats_view") == true && exeatFeatureEnabled,
            exeatsCreate = capabilities?.optBoolean("exeats_create") == true && exeatFeatureEnabled,
            exeatsApprove = capabilities?.optBoolean("exeats_approve") == true && exeatFeatureEnabled,
            gateOperate = capabilities?.optBoolean("gate_operate") == true && exeatFeatureEnabled,
            rollCall = capabilities?.optBoolean("roll_call") == true && rollCallFeatureEnabled,
            reports = capabilities?.optBoolean("reports") == true &&
                advancedReportsEnabled,
            auditView = (isAdmin || isSeniorHousemaster) && advancedReportsEnabled,
            smsDispatchEnabled = isAdmin &&
                (features?.optBoolean("sms_dispatch", false) ?: false),
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
            _bedSpaces.value = emptyList()
            clearOperationsState()
            currentUserId = null
            _pendingRollCallCount.value = 0
            _rollCallOutboxMessage.value = null
            _currentRole.value = UserRole.HOUSEMASTER
            _selectedHouseId.value = null
            _serverDashboardMetrics.value = null
            _notifications.value = emptyList()
            _isLoadingNotifications.value = false
            _hasMoreNotifications.value = false
            _notificationMessage.value = null
            _unreadNotificationCount.value = 0
            notificationPage = 1
            _rollCallSessions.value = emptyList()
            _isLoadingRollCallHistory.value = false
            _hasMoreRollCallHistory.value = false
            rollCallHistoryPage = 1
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
        _bedSpaces.value = emptyList()
        clearOperationsState()
        _pendingRollCallCount.value = 0
        _rollCallOutboxMessage.value = null
        _notifications.value = emptyList()
        _isLoadingNotifications.value = false
        _hasMoreNotifications.value = false
        _notificationMessage.value = null
        _unreadNotificationCount.value = 0
        notificationPage = 1
        _rollCallSessions.value = emptyList()
        _isLoadingRollCallHistory.value = false
        _hasMoreRollCallHistory.value = false
        rollCallHistoryPage = 1
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

    fun loadBedSpaces() {
        viewModelScope.launch {
            try {
                val response = apiClient.get("students/allocation-options/")
                val rooms = response.optJSONArray("rooms") ?: JSONArray()
                _bedSpaces.value = (0 until rooms.length()).mapNotNull { index ->
                    val room = rooms.optJSONObject(index) ?: return@mapNotNull null
                    val id = room.optString("id").takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val dormitoryId = room.optString("dormitory_id")
                        .takeIf(String::isNotBlank) ?: return@mapNotNull null
                    BedSpace(
                        id = id,
                        dormitoryId = dormitoryId,
                        name = room.optString("name"),
                        capacity = room.optInt("capacity")
                    )
                }
            } catch (error: IOException) {
                syncManager.reportActionError("Could not load room options: ${error.message}")
            } catch (error: IllegalStateException) {
                syncManager.reportActionError("Could not load room options: ${error.message}")
            }
        }
    }

    fun reassignStudentRoom(student: Student, bedSpace: BedSpace) {
        viewModelScope.launch {
            remoteAction(
                label = "Student room allocation updated",
                path = "students/${student.id}/allocation/",
                payload = JSONObject()
                    .put("dormitory_id", bedSpace.dormitoryId)
                    .put("room_id", bedSpace.id)
            )
        }
    }

    fun loadMobileReport(reportType: String, loadMore: Boolean = false) {
        if (_isLoadingReport.value) return
        if (loadMore && !_hasMoreReportRows.value) return
        if (!loadMore) {
            selectedReportType = reportType
            reportPage = 1
            _mobileReport.value = null
        }
        val requestedPage = if (loadMore) reportPage + 1 else 1
        viewModelScope.launch {
            _isLoadingReport.value = true
            _operationsMessage.value = null
            try {
                val response = apiClient.get(
                    "reports/",
                    mapOf(
                        "report" to reportType,
                        "page" to requestedPage.toString(),
                        "page_size" to "100"
                    )
                )
                val metrics = response.optJSONArray("kpi_tiles") ?: JSONArray()
                val headers = response.optJSONArray("headers") ?: JSONArray()
                val rows = response.optJSONArray("rows") ?: JSONArray()
                val sections = response.optJSONArray("sections") ?: JSONArray()
                val pageReport = MobileReport(
                    title = response.optString("title", reportType),
                    recordCount = response.optInt("record_count"),
                    generatedAt = response.optString("generated_at"),
                    metrics = (0 until metrics.length()).mapNotNull { index ->
                        val item = metrics.optJSONArray(index) ?: return@mapNotNull null
                        if (item.length() < 2) null else {
                            item.optString(0) to item.optString(1)
                        }
                    },
                    headers = (0 until headers.length()).map { headers.optString(it) },
                    rows = (0 until rows.length()).mapNotNull { index ->
                        val row = rows.optJSONArray(index) ?: return@mapNotNull null
                        (0 until row.length()).map { row.optString(it) }
                    },
                    sections = (0 until sections.length()).mapNotNull { index ->
                        val section = sections.optJSONObject(index)
                            ?: return@mapNotNull null
                        val sectionHeaders = section.optJSONArray("headers") ?: JSONArray()
                        val sectionRows = section.optJSONArray("rows") ?: JSONArray()
                        MobileReportSection(
                            title = section.optString("title"),
                            headers = (0 until sectionHeaders.length()).map {
                                sectionHeaders.optString(it)
                            },
                            rows = (0 until sectionRows.length()).mapNotNull { rowIndex ->
                                val row = sectionRows.optJSONArray(rowIndex)
                                    ?: return@mapNotNull null
                                (0 until row.length()).map { row.optString(it) }
                            }
                        )
                    }
                )
                val previous = _mobileReport.value
                _mobileReport.value = if (loadMore && previous != null) {
                    pageReport.copy(
                        metrics = previous.metrics,
                        rows = previous.rows + pageReport.rows,
                        sections = pageReport.sections.mapIndexed { index, section ->
                            val previousSection = previous.sections.getOrNull(index)
                            if (previousSection == null) section else {
                                section.copy(rows = previousSection.rows + section.rows)
                            }
                        }
                    )
                } else {
                    pageReport
                }
                reportPage = requestedPage
                _hasMoreReportRows.value =
                    response.optJSONObject("pagination")?.optBoolean("has_next")
                        ?: (pageReport.rows.size + pageReport.sections.sumOf {
                            it.rows.size
                        } == 100)
            } catch (error: IOException) {
                _operationsMessage.value = "Could not load report: ${error.message}"
            } catch (error: IllegalStateException) {
                _operationsMessage.value = "Could not load report: ${error.message}"
            } finally {
                _isLoadingReport.value = false
            }
        }
    }

    suspend fun downloadReport(format: String): ByteArray =
        apiClient.download(
            "reports/export/",
            mapOf("report" to selectedReportType, "format" to format)
        )

    fun loadAuditEvents(loadMore: Boolean = false) {
        if (_isLoadingAudit.value || (loadMore && !_hasMoreAuditEvents.value)) return
        val requestedPage = if (loadMore) auditPage + 1 else 1
        viewModelScope.launch {
            _isLoadingAudit.value = true
            _operationsMessage.value = null
            try {
                val response = apiClient.get(
                    "audit/events/",
                    mapOf("page" to requestedPage.toString(), "page_size" to "30")
                )
                val items = response.optJSONArray("items") ?: JSONArray()
                val parsed = (0 until items.length()).mapNotNull { index ->
                    val item = items.optJSONObject(index) ?: return@mapNotNull null
                    MobileAuditEvent(
                        id = item.optString("id"),
                        action = item.optString("action_display"),
                        actor = item.optString("actor"),
                        targetType = item.optString("target_type"),
                        summary = item.optString("summary"),
                        createdAt = item.optString("created_at")
                    )
                }
                _auditEvents.value = if (loadMore) {
                    (_auditEvents.value + parsed).distinctBy { it.id }
                } else {
                    parsed
                }
                auditPage = requestedPage
                _hasMoreAuditEvents.value =
                    response.optJSONObject("pagination")?.optBoolean("has_next")
                        ?: (parsed.size == 30)
            } catch (error: DjangoApiException) {
                _operationsMessage.value = if (
                    error.statusCode == 404 ||
                    error.message?.contains(
                        "No such api endpoint exists in this version",
                        ignoreCase = true
                    ) == true
                ) {
                    "This school server version does not provide the audit/events/ API. The server must expose an audit-history route for this screen."
                } else {
                    "Could not load audit history: ${error.message}"
                }
            } catch (error: IOException) {
                _operationsMessage.value = "Could not load audit history: ${error.message}"
            } catch (error: IllegalStateException) {
                _operationsMessage.value = "Could not load audit history: ${error.message}"
            } finally {
                _isLoadingAudit.value = false
            }
        }
    }

    fun loadSmsCampaigns() {
        if (_isLoadingSmsCampaigns.value) return
        viewModelScope.launch {
            _isLoadingSmsCampaigns.value = true
            _operationsMessage.value = null
            try {
                val response = apiClient.get(
                    "sms/campaigns/",
                    mapOf("page" to "1", "page_size" to "50")
                )
                val items = response.optJSONArray("items") ?: JSONArray()
                _smsEnabled.value = response.optBoolean("sms_enabled")
                _smsCampaigns.value = (0 until items.length()).mapNotNull { index ->
                    items.optJSONObject(index)?.let(::smsCampaignFrom)
                }
            } catch (error: IOException) {
                _operationsMessage.value = "Could not load SMS campaigns: ${error.message}"
            } catch (error: IllegalStateException) {
                _operationsMessage.value = "Could not load SMS campaigns: ${error.message}"
            } finally {
                _isLoadingSmsCampaigns.value = false
            }
        }
    }

    fun createSmsCampaign(scope: String, houseId: String?, message: String) {
        viewModelScope.launch {
            val payload = JSONObject()
                .put("scope", scope)
                .put("message", message.trim())
            if (scope == "house" && houseId != null) payload.put("house_id", houseId)
            remoteOperation("SMS campaign submitted", "sms/campaigns/", payload) {
                loadSmsCampaigns()
            }
        }
    }

    fun decideSmsCampaign(campaignId: String, approve: Boolean, note: String) {
        viewModelScope.launch {
            remoteOperation(
                "SMS campaign decision recorded",
                "sms/campaigns/$campaignId/decision/",
                JSONObject()
                    .put("approve", approve)
                    .put("decision_note", note.trim())
            ) {
                loadSmsCampaigns()
            }
        }
    }

    fun reportOperationsError(message: String) {
        _operationsMessage.value = message
    }

    private suspend fun remoteOperation(
        label: String,
        path: String,
        payload: JSONObject,
        onSuccess: () -> Unit
    ) {
        _operationsMessage.value = null
        try {
        val delivered = submitRemoteMutation(
            label,
            path,
            payload,
            onSuccess = { onSuccess() }
        )
            if (!delivered) {
                _operationsMessage.value = "$label saved and queued until the server is reachable."
            }
        } catch (error: IOException) {
            _operationsMessage.value = "$label failed: ${error.message}"
        } catch (error: IllegalStateException) {
            _operationsMessage.value = "$label failed: ${error.message}"
        }
    }

    private fun smsCampaignFrom(item: JSONObject): SmsCampaign {
        return SmsCampaign(
            id = item.optString("id"),
            scope = item.optString("scope"),
            houseName = item.optJSONObject("house")?.optString("name"),
            message = item.optString("message"),
            status = item.optString("status_display"),
            recipientCount = item.optInt("recipient_count"),
            decisionNote = item.optString("decision_note"),
            createdBy = item.optString("created_by")
        )
    }

    private fun clearOperationsState() {
        _mobileReport.value = null
        selectedReportType = "housing"
        _isLoadingReport.value = false
        _hasMoreReportRows.value = false
        reportPage = 1
        _auditEvents.value = emptyList()
        _isLoadingAudit.value = false
        _hasMoreAuditEvents.value = false
        auditPage = 1
        _smsCampaigns.value = emptyList()
        _smsEnabled.value = false
        _isLoadingSmsCampaigns.value = false
        _operationsMessage.value = null
    }

    fun loadNotifications() {
        if (_isLoadingNotifications.value) return
        notificationPage = 1
        _notifications.value = emptyList()
        _hasMoreNotifications.value = false
        _notificationMessage.value = null
        _isLoadingNotifications.value = true
        viewModelScope.launch {
            try {
                val response = fetchListPage("notifications/", notificationPage)
                val items = response.optJSONArray("items") ?: JSONArray()
                _notifications.value = parseNotifications(items)
                _hasMoreNotifications.value = hasNextPage(response, items)
                val me = apiClient.get("auth/me/")
                val unread = me.optJSONObject("unread")
                _unreadNotificationCount.value =
                    (unread?.optInt("notifications") ?: 0) +
                    (unread?.optInt("roll_call_notifications") ?: 0)
            } catch (error: IOException) {
                _notificationMessage.value = "Could not load notifications: ${error.message}"
                syncManager.reportActionError(_notificationMessage.value.orEmpty())
            } catch (error: IllegalStateException) {
                _notificationMessage.value = "Could not load notifications: ${error.message}"
                syncManager.reportActionError(_notificationMessage.value.orEmpty())
            } finally {
                _isLoadingNotifications.value = false
            }
        }
    }

    fun loadMoreNotifications() {
        if (_isLoadingNotifications.value || !_hasMoreNotifications.value) return
        val nextPage = notificationPage + 1
        _isLoadingNotifications.value = true
        _notificationMessage.value = null
        viewModelScope.launch {
            try {
                val response = fetchListPage("notifications/", nextPage)
                val items = response.optJSONArray("items") ?: JSONArray()
                _notifications.value = mergeById(
                    _notifications.value,
                    parseNotifications(items),
                    MobileNotification::id
                )
                notificationPage = nextPage
                _hasMoreNotifications.value = hasNextPage(response, items)
            } catch (error: IOException) {
                _notificationMessage.value = "Could not load more notifications: ${error.message}"
                syncManager.reportActionError(_notificationMessage.value.orEmpty())
            } catch (error: IllegalStateException) {
                _notificationMessage.value = "Could not load more notifications: ${error.message}"
                syncManager.reportActionError(_notificationMessage.value.orEmpty())
            } finally {
                _isLoadingNotifications.value = false
            }
        }
    }

    private suspend fun fetchListPage(path: String, page: Int): JSONObject =
        apiClient.get(
            path,
            mapOf("page" to page.toString(), "page_size" to LIST_PAGE_SIZE.toString())
        )

    private fun hasNextPage(response: JSONObject, items: JSONArray): Boolean =
        response.optJSONObject("pagination")?.let { it.optBoolean("has_next") }
            ?: (items.length() == LIST_PAGE_SIZE)

    private fun parseNotifications(items: JSONArray): List<MobileNotification> =
        (0 until items.length()).mapNotNull { index ->
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

    private fun <T> mergeById(existing: List<T>, incoming: List<T>, id: (T) -> String): List<T> =
        (existing + incoming).distinctBy(id)

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            remoteAction(
                label = "Notification marked as read",
                path = "notifications/$notificationId/read/",
                onSuccess = { response ->
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
                },
                onQueued = {
                    _notifications.value = _notifications.value.map { notification ->
                        if (notification.id == notificationId) notification.copy(isRead = true)
                        else notification
                    }
                }
            )
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            remoteAction(
                label = "All notifications marked as read",
                path = "notifications/read-all/",
                onSuccess = {
                _notifications.value = _notifications.value.map { it.copy(isRead = true) }
                _unreadNotificationCount.value = 0
                },
                onQueued = {
                    _notifications.value = _notifications.value.map { it.copy(isRead = true) }
                    _unreadNotificationCount.value = 0
                }
            )
        }
    }

    fun loadRollCallHistory() {
        if (_isLoadingRollCallHistory.value) return
        rollCallHistoryPage = 1
        _rollCallSessions.value = emptyList()
        _hasMoreRollCallHistory.value = false
        _rollCallHistoryMessage.value = null
        _isLoadingRollCallHistory.value = true
        viewModelScope.launch {
            try {
                val response = fetchListPage("roll-call/sessions/", rollCallHistoryPage)
                val items = response.optJSONArray("items") ?: JSONArray()
                _rollCallSessions.value = parseRollCallSessions(items)
                _hasMoreRollCallHistory.value = hasNextPage(response, items)
            } catch (error: IOException) {
                _rollCallHistoryMessage.value = "Could not load roll-call history: ${error.message}"
            } catch (error: IllegalStateException) {
                _rollCallHistoryMessage.value = "Could not load roll-call history: ${error.message}"
            } finally {
                _isLoadingRollCallHistory.value = false
            }
        }
    }

    fun loadMoreRollCallHistory() {
        if (_isLoadingRollCallHistory.value || !_hasMoreRollCallHistory.value) return
        val nextPage = rollCallHistoryPage + 1
        _isLoadingRollCallHistory.value = true
        _rollCallHistoryMessage.value = null
        viewModelScope.launch {
            try {
                val response = fetchListPage("roll-call/sessions/", nextPage)
                val items = response.optJSONArray("items") ?: JSONArray()
                _rollCallSessions.value = mergeById(
                    _rollCallSessions.value,
                    parseRollCallSessions(items),
                    RollCallSessionItem::id
                )
                rollCallHistoryPage = nextPage
                _hasMoreRollCallHistory.value = hasNextPage(response, items)
            } catch (error: IOException) {
                _rollCallHistoryMessage.value = "Could not load more roll-call history: ${error.message}"
            } catch (error: IllegalStateException) {
                _rollCallHistoryMessage.value = "Could not load more roll-call history: ${error.message}"
            } finally {
                _isLoadingRollCallHistory.value = false
            }
        }
    }

    private fun parseRollCallSessions(items: JSONArray): List<RollCallSessionItem> =
        (0 until items.length()).mapNotNull { index ->
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
                val delivered = submitRemoteMutation(
                    label = "Roll-call attendance updated",
                    path = "roll-call/sessions/${session.id}/entries/${entry.id}/",
                    payload = JSONObject().put("status", serverStatus).put("remarks", remarks),
                    onSuccess = {
                        openRollCallSession(session)
                        loadRollCallHistory()
                    },
                    onQueued = {
                        _rollCallEntries.value = _rollCallEntries.value.map { current ->
                            if (current.id == entry.id) {
                                current.copy(status = serverStatus, remarks = remarks)
                            } else {
                                current
                            }
                        }
                    }
                )
                if (!delivered) {
                    _rollCallHistoryMessage.value =
                        "Attendance change saved on this device and queued for delivery."
                }
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
                val ownerId = currentUserId
                    ?: throw IllegalStateException("Sign in again before submitting roll call.")
                val houseId = _selectedHouseId.value
                    ?: throw IllegalArgumentException("Choose a house before submitting roll call.")
                if (roomId.isBlank()) {
                    throw IllegalArgumentException("Choose a dormitory before submitting roll call.")
                }
                if (markedStatuses.isEmpty() || markedStatuses.values.any {
                        it !in setOf("PRESENT", "ABSENT", "ON_EXEAT", "SICK_BAY")
                    }
                ) {
                    throw IllegalArgumentException("Mark every student before submitting roll call.")
                }
                val queuedAction = RollCallOutboxItem(
                    id = UUID.randomUUID().toString(),
                    ownerId = ownerId,
                    tenantUrl = apiClient.baseUrl,
                    houseId = houseId,
                    dormitoryId = roomId,
                    sessionType = sessionType,
                    inspectionDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    statusesJson = JSONObject(markedStatuses).toString(),
                    remoteSessionId = null,
                    attempts = 0,
                    lastError = null,
                    createdAt = System.currentTimeMillis()
                )
                repository.rollCallOutboxDao.insert(queuedAction)
                refreshRollCallOutboxCount()
                _rollCallOutboxMessage.value = "Roll call saved securely and queued for delivery."
                processRollCallOutbox()
                if (_pendingRollCallCount.value == 0) {
                    onResult("Roll call submitted to the school server.")
                } else {
                    onResult("Roll call is saved and queued. It will retry when the server is reachable.")
                }
            } catch (error: IOException) {
                val message = "Roll call was not queued: ${error.message}"
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

    private suspend fun refreshRollCallOutboxCount() {
        val ownerId = currentUserId
        if (ownerId.isNullOrBlank()) {
            _pendingRollCallCount.value = 0
            return
        }
        _pendingRollCallCount.value = repository.rollCallOutboxDao.countFor(
            ownerId,
            apiClient.baseUrl
        )
    }

    private suspend fun processRollCallOutbox() {
        if (currentUserId.isNullOrBlank() || !_apiPermissions.value.rollCall) return
        rollCallOutboxMutex.withLock {
            try {
                val dao = repository.rollCallOutboxDao
                val actions = dao.getAll().filter {
                    it.ownerId == currentUserId && it.tenantUrl == apiClient.baseUrl
                }
                for (action in actions) {
                    try {
                        processRollCallAction(action)
                    } catch (error: IOException) {
                        recordRollCallOutboxFailure(
                            action,
                            error.message ?: "School server unavailable."
                        )
                        _rollCallOutboxMessage.value =
                            "Roll-call delivery is pending: ${error.message ?: "server unavailable."}"
                        break
                    } catch (error: IllegalStateException) {
                        recordRollCallOutboxFailure(
                            action,
                            error.message ?: "Invalid server response."
                        )
                        _rollCallOutboxMessage.value =
                            "Roll-call delivery is pending: ${error.message ?: "invalid server response."}"
                        break
                    } catch (error: IllegalArgumentException) {
                        recordRollCallOutboxFailure(
                            action,
                            error.message ?: "Invalid roll-call data."
                        )
                        _rollCallOutboxMessage.value =
                            "Roll-call delivery needs attention: ${error.message ?: "invalid data."}"
                        break
                    }
                }
            } finally {
                refreshRollCallOutboxCount()
            }
        }
    }

    private suspend fun recordRollCallOutboxFailure(
        action: RollCallOutboxItem,
        message: String
    ) {
        val latest = repository.rollCallOutboxDao.getAll().firstOrNull { it.id == action.id }
            ?: return
        repository.rollCallOutboxDao.update(
            latest.copy(attempts = latest.attempts + 1, lastError = message)
        )
    }

    private suspend fun processRollCallAction(action: RollCallOutboxItem) {
        val dao = repository.rollCallOutboxDao
        val serverType = when (action.sessionType) {
            "EVENING_ROLL_CALL" -> "Evening"
            "MORNING_INSPECTION" -> "Morning"
            else -> "Special"
        }
        var sessionId = action.remoteSessionId
        val entries: JSONArray
        if (sessionId == null) {
            val response = apiClient.post(
                "roll-call/sessions/",
                JSONObject()
                    .put("house_id", action.houseId)
                    .put("dormitory_id", action.dormitoryId)
                    .put("session_type", serverType)
                    .put("inspection_date", action.inspectionDate),
                idempotencyKey = "${action.id}-start"
            )
            sessionId = response.optString("id").takeIf(String::isNotBlank)
                ?: throw IllegalStateException("The server did not create a roll-call session.")
            entries = response.optJSONArray("entries") ?: JSONArray()
            dao.update(action.copy(remoteSessionId = sessionId, lastError = null))
        } else {
            val detail = apiClient.get("roll-call/sessions/$sessionId/")
            entries = detail.optJSONArray("entries") ?: JSONArray()
        }

        val statuses = JSONObject(action.statusesJson)
        val apiStatuses = mapOf(
            "PRESENT" to "Present",
            "ABSENT" to "Absent",
            "ON_EXEAT" to "On Exeat",
            "SICK_BAY" to "Infirmary"
        )
        val entriesToSubmit = (0 until entries.length()).mapNotNull(entries::optJSONObject)
        if (entriesToSubmit.isEmpty()) {
            throw IllegalStateException("The server returned no students for this roll-call session.")
        }
        val missingCount = entriesToSubmit.count { entry ->
            val studentId = entry.optJSONObject("student")?.optString("id").orEmpty()
            statuses.optString(studentId).isBlank()
        }
        if (missingCount > 0) {
            throw IllegalStateException(
                "The server roster includes $missingCount student(s) without a saved roll-call mark."
            )
        }
        for (entry in entriesToSubmit) {
            val studentId = entry.optJSONObject("student")?.optString("id").orEmpty()
            val requestedStatus = statuses.optString(studentId)
            val apiStatus = apiStatuses[requestedStatus]
                ?: throw IllegalArgumentException("Unsupported attendance status: $requestedStatus")
            apiClient.post(
                "roll-call/sessions/$sessionId/entries/${entry.optString("id")}/",
                JSONObject().put("status", apiStatus),
                idempotencyKey = UUID.nameUUIDFromBytes(
                    "${action.id}:${entry.optString("id")}".toByteArray()
                ).toString()
            )
        }
        apiClient.post(
            "roll-call/sessions/$sessionId/complete/",
            idempotencyKey = "${action.id}-complete"
        )
        dao.delete(action.id)
        _rollCallOutboxMessage.value = "Queued roll call delivered successfully."
        syncManager.reportActionSuccess("Roll call submitted successfully.")
        triggerSync()
    }

    // Exeat Actions
    fun approveExeat(exeatId: String, approverName: String, notes: String?) {
        viewModelScope.launch {
            remoteAction(
                label = "Exeat approved",
                path = "exeats/$exeatId/approve/",
                payload = JSONObject().put("approval_remarks", notes.orEmpty()),
                onQueued = {
                    repository.approveExeat(exeatId, approverName, notes)
                }
            )
        }
    }

    fun rejectExeat(exeatId: String, approverName: String, reason: String) {
        viewModelScope.launch {
            remoteAction(
                label = "Exeat rejected",
                path = "exeats/$exeatId/reject/",
                payload = JSONObject().put("approval_remarks", reason),
                onQueued = {
                    repository.rejectExeat(exeatId, approverName, reason)
                }
            )
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
            remoteAction(
                label = "Exeat request submitted",
                path = "exeats/",
                payload = JSONObject()
                    .put("student_id", student.id)
                    .put("exeat_type", category)
                    .put("reason", reason)
                    .put("destination", destination)
                    .put("expected_departure", Instant.ofEpochMilli(departureTime).toString())
                    .put("expected_return", Instant.ofEpochMilli(expectedReturnTime).toString()),
                onQueued = { queueId ->
                    repository.submitExeat(
                        ExeatRequest(
                            id = "pending_$queueId",
                            studentId = student.id,
                            studentName = student.fullName,
                            studentIndexNumber = student.studentId,
                            houseId = student.houseId,
                            category = category.uppercase(),
                            reason = reason,
                            destination = destination,
                            departureTime = departureTime,
                            expectedReturnTime = expectedReturnTime,
                            status = "QUEUED"
                        ),
                        actorRole = "Housemaster",
                        actorName = actorName
                    )
                }
            )
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
            remoteAction(
                label = "Exeat action completed",
                path = endpoint,
                payload = payload,
                onSuccess = { onSuccess() }
            )
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
            val route = if (movementType == "DEPARTURE") "gate/depart/" else "gate/return/"
            val passId = exeatId
            if (passId == null) {
                syncManager.reportActionError(
                    "Gate movement was not recorded: A verified exeat pass is required."
                )
                return@launch
            }
            remoteAction(
                label = "Gate movement recorded",
                path = route,
                payload = JSONObject().put("exeat_id", passId),
                onSuccess = {
                _scanMessage.value = "Movement recorded by the school server for ${student.fullName}."
                _gateScanResult.value = null
                },
                onQueued = {
                    _scanMessage.value =
                        "Movement saved on this device and queued until the server is reachable."
                    _gateScanResult.value = null
                }
            )
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
                    currentUserId?.let { ownerId ->
                        val pendingCount = repository.remoteMutationOutboxDao.countFor(
                            ownerId,
                            apiClient.baseUrl
                        )
                        syncManager.updatePendingOutboxCount(pendingCount)
                    }
                    var outboxWarning: String? = null
                    syncManager.performSync {
                        outboxWarning = processRemoteMutationOutbox()
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
                    outboxWarning?.let(syncManager::reportActionWarning)
                }
                processRollCallOutbox()
            } catch (_: IOException) {
                // SyncManager publishes the server error to the sync status flow.
            } catch (_: IllegalStateException) {
                // SyncManager publishes invalid server data to the sync status flow.
            }
        }
    }

    private suspend fun processRemoteMutationOutbox(): String? {
        val ownerId = currentUserId ?: return null
        return remoteMutationOutboxMutex.withLock {
            val dao = repository.remoteMutationOutboxDao
            val actions = dao.getFor(ownerId, apiClient.baseUrl)
            var warning = actions.firstOrNull { it.lastError != null }?.let {
                "A queued change needs attention: ${it.lastError}"
            }
            for (action in actions) {
                if (action.lastError != null) continue
                try {
                    apiClient.post(
                        action.path,
                        JSONObject(action.payloadJson),
                        idempotencyKey = action.id
                    )
                    dao.delete(action.id)
                } catch (error: IOException) {
                    val permanentFailure =
                        error is DjangoApiException &&
                            error.statusCode in 400..499 &&
                            error.statusCode !in setOf(401, 408, 429)
                    dao.update(
                        action.copy(
                            attempts = action.attempts + 1,
                            lastError = if (permanentFailure) error.message else null
                        )
                    )
                    if (!permanentFailure) throw error
                    warning = "A queued change needs attention: ${error.message ?: "the server rejected it."}"
                } catch (error: IllegalStateException) {
                    dao.update(
                        action.copy(
                            attempts = action.attempts + 1,
                            lastError = error.message ?: "Invalid queued request."
                        )
                    )
                    warning = "A queued change needs attention: ${error.message ?: "invalid request."}"
                }
            }
            syncManager.updatePendingOutboxCount(
                dao.countFor(ownerId, apiClient.baseUrl)
            )
            warning
        }
    }

    private suspend fun submitRemoteMutation(
        label: String,
        path: String,
        payload: JSONObject,
        onSuccess: suspend (JSONObject) -> Unit = {},
        onQueued: suspend (String) -> Unit = {}
    ): Boolean {
        val itemId = UUID.randomUUID().toString()
        val response = try {
            apiClient.post(path, payload, idempotencyKey = itemId)
        } catch (error: IOException) {
            if (error is DjangoApiException && error.statusCode < 500) throw error
            val ownerId = currentUserId
                ?: throw IllegalStateException("Sign in before saving changes for delivery.")
            val dao = repository.remoteMutationOutboxDao
            dao.insert(
                RemoteMutationOutboxItem(
                    id = itemId,
                    ownerId = ownerId,
                    tenantUrl = apiClient.baseUrl,
                    path = path,
                    payloadJson = payload.toString(),
                    attempts = 0,
                    lastError = null,
                    createdAt = System.currentTimeMillis()
                )
            )
            val pendingCount = dao.countFor(ownerId, apiClient.baseUrl)
            syncManager.reportActionQueued(
                "$label saved on this device and queued for delivery.",
                pendingCount
            )
            onQueued(itemId)
            triggerSync()
            return false
        }
        onSuccess(response)
        syncManager.reportActionSuccess("$label on the school server.")
        triggerSync()
        return true
    }

    private suspend fun remoteAction(
        label: String,
        path: String,
        payload: JSONObject = JSONObject(),
        onSuccess: suspend (JSONObject) -> Unit = {},
        onQueued: suspend (String) -> Unit = {}
    ) {
        try {
            submitRemoteMutation(label, path, payload, onSuccess, onQueued)
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
                _bedSpaces.value = emptyList()
                currentUserId = null
                _pendingRollCallCount.value = 0
                _rollCallOutboxMessage.value = null
                _currentRole.value = UserRole.HOUSEMASTER
                _selectedHouseId.value = null
                _serverDashboardMetrics.value = null
                _notifications.value = emptyList()
                _isLoadingNotifications.value = false
                _hasMoreNotifications.value = false
                _notificationMessage.value = null
                _unreadNotificationCount.value = 0
                notificationPage = 1
                _rollCallSessions.value = emptyList()
                _isLoadingRollCallHistory.value = false
                _hasMoreRollCallHistory.value = false
                rollCallHistoryPage = 1
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
