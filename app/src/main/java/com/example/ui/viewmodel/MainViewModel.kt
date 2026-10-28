package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.BedSpace
import com.example.data.model.ExeatRequest
import com.example.data.model.GateMovement
import com.example.data.model.House
import com.example.data.model.HouseScopePolicy
import com.example.data.model.IncidentRecord
import com.example.data.model.MobileAuditEvent
import com.example.data.model.MobileReport
import com.example.data.model.MobileReportSection
import com.example.data.model.RemoteMutationOutboxItem
import com.example.data.model.OutboxKind
import com.example.data.model.RollCallIdempotencyKey
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
import com.example.data.repository.OutboxWorkScheduler
import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoApiException
import com.example.data.remote.DjangoDataSync
import com.example.data.remote.CacheOwnershipPolicy
import com.example.data.remote.SessionAccessPolicy
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
import kotlinx.coroutines.flow.flatMapLatest
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
    INCIDENTS,
    OPERATIONS,
    STUDENTS,
    DORMS,
    ROLL_CALL_HISTORY,
    NOTIFICATIONS,
    REPORTS_SYNC,
    SMS_CAMPAIGNS,
    OUTBOX,
    HELP
}

data class OutboxDisplayItem(
    val id: String,
    val kind: OutboxKind,
    val title: String,
    val status: String,
    val createdAt: Long,
    val attempts: Int,
    val lastError: String?
)

data class StudentRecordInput(
    val studentId: String,
    val fullName: String,
    val gender: String,
    val classForm: String,
    val program: String,
    val houseId: String,
    val guardianName: String,
    val guardianPhone: String,
    val guardianRelationship: String,
    val emergencyNotes: String
)

data class IncidentReportInput(
    val student: Student?,
    /** Used when the incident is campus-wide and no student is attached. */
    val houseId: String,
    val category: String,
    val severity: String,
    val location: String,
    val description: String,
    val immediateAction: String,
    val guardianContacted: Boolean,
    val guardianContactPhone: String,
    val guardianContactTime: String,
    val medicalReferral: Boolean,
    val actorName: String
)

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
    val isHeadmaster: Boolean = false,
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
    val houseId: String,
    val houseName: String,
    val status: String,
    val remarks: String,
    val gateConflict: String?
)

class MainViewModel(
    private val repository: BoardingRepository,
    private val syncManager: SyncManager,
    private val dataSync: DjangoDataSync,
    private val apiClient: DjangoApiClient,
    private val appContext: Context
) : ViewModel() {
    private val sessionDataMutex = Mutex()
    private var currentUserId: String? = null
    private var activeUserDisplayName: String = ""
    private val _currentUserDisplayName = MutableStateFlow("")
    val currentUserDisplayName: StateFlow<String> = _currentUserDisplayName.asStateFlow()
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
    private val _studentRecordMessage = MutableStateFlow<String?>(null)
    val studentRecordMessage: StateFlow<String?> = _studentRecordMessage.asStateFlow()

    val incidents: StateFlow<List<IncidentRecord>> = combine(
        repository.allIncidents,
        _currentRole,
        _apiPermissions
    ) { rows, role, permissions ->
        if (!role.isHouseScoped) {
            rows
        } else {
            val houseId = permissions.houseId?.takeIf(String::isNotBlank)
                ?: return@combine emptyList()
            // Blank-house incidents are campus-wide events; every house staff member needs
            // to see those (drills, security alerts, power or water failures).
            rows.filter { it.houseId == houseId || it.houseId.isBlank() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val houses: StateFlow<List<House>> = combine(
        repository.allHouses,
        _currentRole,
        _apiPermissions
    ) { rows, role, permissions ->
        HouseScopePolicy.filter(rows, role, permissions.houseId) { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rooms: StateFlow<List<RoomEntity>> = combine(
        repository.allRooms,
        _currentRole,
        _apiPermissions
    ) { rows, role, permissions ->
        HouseScopePolicy.filter(rows, role, permissions.houseId) { it.houseId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val students: StateFlow<List<Student>> = combine(
        repository.allStudents,
        _currentRole,
        _apiPermissions
    ) { rows, role, permissions ->
        HouseScopePolicy.filter(rows, role, permissions.houseId) { it.houseId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exeats: StateFlow<List<ExeatRequest>> = combine(
        repository.allExeats,
        _currentRole,
        _apiPermissions
    ) { rows, role, permissions ->
        HouseScopePolicy.filter(rows, role, permissions.houseId) { it.houseId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movements: StateFlow<List<GateMovement>> = combine(
        repository.allMovements,
        students
    ) { rows, visibleStudents ->
        val visibleStudentIds = visibleStudents.mapTo(mutableSetOf()) { it.id }
        rows.filter { it.studentId in visibleStudentIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val auditScope = MutableStateFlow("" to "")
    val auditLogs: StateFlow<List<AuditLog>> = auditScope
        .flatMapLatest { (ownerId, tenantUrl) ->
            if (ownerId.isBlank() || tenantUrl.isBlank()) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                repository.auditLogs(ownerId, tenantUrl)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState: StateFlow<SyncState> = syncManager.syncState
    private val _outboxItems = MutableStateFlow<List<OutboxDisplayItem>>(emptyList())
    val outboxItems: StateFlow<List<OutboxDisplayItem>> = _outboxItems.asStateFlow()
    private val _serverDashboardMetrics = MutableStateFlow<DashboardMetrics?>(null)

    // Computed Dashboard Metrics
    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        students,
        exeats,
        houses,
        _serverDashboardMetrics,
        _currentRole
    ) { studentList, exeatList, houseList, serverMetrics, role ->
        if (serverMetrics != null && !role.isHouseScoped) return@combine serverMetrics
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

    fun houseScopedStudents(source: List<Student>): List<Student> {
        if (!_currentRole.value.isHouseScoped) return source
        val assignedHouseId = _apiPermissions.value.houseId ?: return emptyList()
        return source.filter { it.houseId == assignedHouseId }
    }

    fun setAuthenticatedUserId(userId: String?) {
        currentUserId = userId?.takeIf(String::isNotBlank)
        viewModelScope.launch {
            refreshRollCallOutboxCount()
            enqueuePendingOutboxWork()
        }
    }

    suspend fun hasUsableOfflineCache(): Boolean = repository.hasUsableSchoolCache()

    fun reportOffline() {
        syncManager.reportOffline()
    }

    fun restrictOfflineAccess() {
        _apiPermissions.value = ApiPermissions()
        apiClient.restrictOfflineStartup("offline_grace_expired")
        syncManager.reportOfflineGraceExpired()
    }

    suspend fun activateSession(session: JSONObject, serverValidated: Boolean) {
        val user = session.optJSONObject("user")
            ?: throw IllegalStateException("The school server returned no staff account.")
        val userId = user.optString("id").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("The school server returned no staff account ID.")
        val cachedSession = apiClient.cachedSessionForCurrentTenant()
        val cachedUser = cachedSession?.optJSONObject("user")
        val cachedUserId = cachedUser
            ?.optString("id")
            ?.takeIf(String::isNotBlank)
        val cacheOwnerMatches = CacheOwnershipPolicy.belongsToOwner(cachedUserId, userId)
        if (serverValidated && !cacheOwnerMatches) {
            if (repository.hasCachedSchoolData()) {
                sessionDataMutex.withLock { repository.clearSessionData() }
            }
            syncManager.clearSessionStatus()
        }
        if (serverValidated) apiClient.recordSuccessfulValidation(session)
        val accessChanged = serverValidated &&
            cacheOwnerMatches &&
            !SessionAccessPolicy.matches(
                cachedUser,
                user,
                cachedSession?.optJSONObject("features"),
                session.optJSONObject("features")
            )
        if (accessChanged) apiClient.restrictOfflineStartup("permissions_changed")

        val role = userRoleFromServerRole(user.optString("role"))
            ?: throw IllegalStateException("This account has no supported active staff role.")
        _currentRole.value = role
        val capabilities = user.optJSONObject("capabilities")
        _selectedHouseId.value = if (accessChanged) null else capabilities?.optString("house_id")
            ?.takeUnless { it.isBlank() || it == "null" }
        setApiPermissions(
            if (accessChanged) null else capabilities,
            if (accessChanged) null else session.optJSONObject("features")
        )
        setAuthenticatedUserId(userId)
        repository.setAuditScope(userId, apiClient.baseUrl)
        auditScope.value = userId to apiClient.baseUrl
        activeUserDisplayName = user.optString("full_name")
            .takeIf(String::isNotBlank)
            ?: user.optString("name").takeIf(String::isNotBlank)
            ?: userId
        _currentUserDisplayName.value = activeUserDisplayName
        if (!serverValidated) syncManager.reportOffline()
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
        val isHeadmaster = _currentRole.value == UserRole.HEADMASTER ||
            capabilities?.optBoolean("is_headmaster") == true
        val isReadOnly = isHeadmaster || _currentRole.value.isReadOnly
        val houseScopeValid = !_currentRole.value.isHouseScoped || houseId != null
        val canViewScopedData = houseScopeValid
        val advancedReportsEnabled =
            features?.optBoolean("advanced_reports", true) ?: true
        return ApiPermissions(
            isAdmin = isAdmin,
            isSeniorHousemaster = isSeniorHousemaster,
            isHeadmaster = isHeadmaster,
            houseId = houseId,
            studentsView = houseScopeValid && capabilities?.optBoolean("students_view") == true,
            studentsManage = canViewScopedData && !isReadOnly &&
                capabilities?.optBoolean("students_manage") == true,
            exeatsView = houseScopeValid &&
                capabilities?.optBoolean("exeats_view") == true && exeatFeatureEnabled,
            exeatsCreate = canViewScopedData && !isReadOnly &&
                capabilities?.optBoolean("exeats_create") == true && exeatFeatureEnabled,
            exeatsApprove = canViewScopedData && !isReadOnly &&
                capabilities?.optBoolean("exeats_approve") == true && exeatFeatureEnabled,
            gateOperate = !isReadOnly && capabilities?.optBoolean("gate_operate") == true && exeatFeatureEnabled,
            rollCall = houseScopeValid && !isReadOnly &&
                capabilities?.optBoolean("roll_call") == true && rollCallFeatureEnabled,
            reports = capabilities?.optBoolean("reports") == true &&
                (!_currentRole.value.isHouseScoped ||
                    capabilities?.optBoolean("house_scoped_reports") == true) &&
                advancedReportsEnabled,
            auditView = (isAdmin || isSeniorHousemaster || isHeadmaster) &&
                (!_currentRole.value.isHouseScoped ||
                    capabilities?.optBoolean("house_scoped_audit") == true) &&
                advancedReportsEnabled,
            smsDispatchEnabled = !isReadOnly && isAdmin &&
                (features?.optBoolean("sms_dispatch", false) ?: false),
            exeatReview = !isReadOnly &&
                (capabilities?.optBoolean("is_admin") == true ||
                    capabilities?.optBoolean("is_senior_housemaster") == true)
        )
    }

    fun clearSensitiveState(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            apiClient.clearSession()
            syncManager.clearSessionStatus()
            clearAuthenticatedUiState()
            onComplete()
        }
    }

    fun clearAuthenticatedUiState() {
        _apiPermissions.value = ApiPermissions()
        _bedSpaces.value = emptyList()
        clearOperationsState()
        currentUserId = null
        activeUserDisplayName = ""
        _currentUserDisplayName.value = ""
        repository.setAuditScope("", "")
        auditScope.value = "" to ""
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

    fun refreshNotificationBadge() {
        viewModelScope.launch {
            try {
                val me = apiClient.get("auth/me/")
                val unread = me.optJSONObject("unread")
                if (unread != null) {
                    _unreadNotificationCount.value =
                        unread.optInt("notifications") + unread.optInt("roll_call_notifications")
                }
            } catch (_: IOException) {
                // Keep the last known badge while the device is offline.
            } catch (_: IllegalStateException) {
                // Keep the last known badge if the session cannot be refreshed.
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
                        houseId = student.optJSONObject("house")?.optString("id")
                            ?.takeUnless { it.isBlank() || it == "null" }
                            ?: student.optString("house_id").takeUnless { it == "null" }.orEmpty(),
                        houseName = student.optJSONObject("house")?.optString("name")
                            ?.takeUnless { it.isBlank() || it == "null" }
                            ?: student.optString("house_name").takeUnless { it == "null" }.orEmpty(),
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
                if (!session.status.equals("In Progress", ignoreCase = true)) {
                    throw IllegalArgumentException(
                        "Finalized attendance must use the correction-request workflow."
                    )
                }
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

    fun requestRollCallCorrection(
        session: RollCallSessionItem,
        entry: RollCallEntryItem,
        requestedStatus: String,
        reason: String
    ) {
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) {
            _rollCallHistoryMessage.value =
                "Enter a reason for the finalized-session correction."
            return
        }
        val serverStatus = mapOf(
            "Present" to "Present",
            "Absent" to "Absent",
            "On Exeat" to "On Exeat",
            "Infirmary" to "Infirmary",
            "Activity" to "Activity",
            "Unmarked" to "Unmarked"
        )[requestedStatus]
        if (serverStatus == null) {
            _rollCallHistoryMessage.value = "Choose a valid attendance status."
            return
        }
        viewModelScope.launch {
            try {
                val delivered = submitRemoteMutation(
                    label = "Roll-call correction requested",
                    path = "roll-call/sessions/${session.id}/correction-requests/",
                    payload = JSONObject()
                        .put("entry_id", entry.id)
                        .put("requested_status", serverStatus)
                        .put("reason", cleanReason)
                )
                _rollCallHistoryMessage.value = if (delivered) {
                    "Correction request submitted for approval."
                } else {
                    "Correction request saved and queued for delivery."
                }
            } catch (error: IOException) {
                _rollCallHistoryMessage.value =
                    "Could not submit correction request: ${error.message}"
            } catch (error: IllegalStateException) {
                _rollCallHistoryMessage.value =
                    "Could not submit correction request: ${error.message}"
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
        remarks: Map<String, String> = emptyMap(),
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
                        it !in setOf("PRESENT", "ABSENT", "ON_EXEAT", "SICK_BAY", "ACTIVITY")
                    }
                ) {
                    throw IllegalArgumentException("Mark every student before submitting roll call.")
                }
                if (markedStatuses.any { (studentId, status) ->
                        status in setOf("ABSENT", "SICK_BAY") &&
                            remarks[studentId].orEmpty().isBlank()
                    }
                ) {
                    throw IllegalArgumentException(
                        "Add a reason for every student marked absent or in the infirmary."
                    )
                }
                if (!apiClient.hasUsableOfflineGrace()) {
                    throw IllegalStateException(
                        "Offline access expired. Reconnect and complete a school-data sync before submitting roll call."
                    )
                }
                val inspectionDate =
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val actionId = RollCallIdempotencyKey.create(
                    ownerId = ownerId,
                    tenantUrl = apiClient.baseUrl,
                    houseId = houseId,
                    roomId = roomId,
                    sessionType = sessionType,
                    inspectionDate = inspectionDate
                )
                val queuedAction = RollCallOutboxItem(
                    id = actionId,
                    ownerId = ownerId,
                    tenantUrl = apiClient.baseUrl,
                    houseId = houseId,
                    dormitoryId = roomId,
                    sessionType = sessionType,
                    inspectionDate = inspectionDate,
                    statusesJson = JSONObject(markedStatuses).toString(),
                    remarksJson = JSONObject(remarks).toString(),
                    remoteSessionId = null,
                    attempts = 0,
                    lastError = null,
                    createdAt = System.currentTimeMillis()
                )
                val inserted = repository.rollCallOutboxDao.insert(queuedAction)
                if (inserted == -1L) {
                    _rollCallOutboxMessage.value =
                        "A roll call for this room and session is already queued."
                    onResult(_rollCallOutboxMessage.value.orEmpty())
                    return@launch
                }
                refreshRollCallOutboxCount()
                _rollCallOutboxMessage.value = "Roll call saved securely and queued for delivery."
                OutboxWorkScheduler.enqueue(
                    appContext,
                    OutboxKind.ROLL_CALL,
                    queuedAction.id,
                    ownerId,
                    apiClient.baseUrl
                )
                triggerSync()
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
            syncManager.updateOutboxCounts(0, 0)
            return
        }
        _pendingRollCallCount.value = repository.rollCallOutboxDao.countFor(
            ownerId,
            apiClient.baseUrl
        )
        refreshPendingOutboxCount(ownerId)
    }

    private suspend fun refreshPendingOutboxCount(ownerId: String) {
        val remoteDao = repository.remoteMutationOutboxDao
        val rollCallDao = repository.rollCallOutboxDao
        syncManager.updateOutboxCounts(
            pendingCount = remoteDao.countFor(ownerId, apiClient.baseUrl) +
                rollCallDao.countFor(ownerId, apiClient.baseUrl),
            failedCount = remoteDao.countFailedFor(ownerId, apiClient.baseUrl) +
                rollCallDao.countFailedFor(ownerId, apiClient.baseUrl)
        )
    }

    suspend fun refreshOutboxItems() {
        val ownerId = currentUserId ?: return
        val remote = repository.remoteMutationOutboxDao.getFor(ownerId, apiClient.baseUrl)
            .map { item ->
                OutboxDisplayItem(
                    id = item.id,
                    kind = OutboxKind.REMOTE_MUTATION,
                    title = when {
                        item.path.startsWith("gate/") -> "Gate movement"
                        item.path.endsWith("/approve/") -> "Exeat approval"
                        item.path.endsWith("/review/") -> "Exeat review"
                        item.path.endsWith("/override/") -> "Exeat override"
                        item.path.endsWith("/reschedule/") -> "Reschedule exeat"
                        item.path.endsWith("/reconcile/") -> "Exeat reconciliation"
                        item.path.endsWith("/cancel/") -> "Cancel exeat"
                        item.path == "exeats/" -> "Create exeat"
                        else -> "School record update"
                    },
                    status = item.deliveryStatus,
                    createdAt = item.createdAt,
                    attempts = item.attempts,
                    lastError = item.lastError
                )
            }
        val rollCalls = repository.rollCallOutboxDao.getAll()
            .filter { it.ownerId == ownerId && it.tenantUrl == apiClient.baseUrl }
            .map { item ->
                OutboxDisplayItem(
                    id = item.id,
                    kind = OutboxKind.ROLL_CALL,
                    title = "${item.sessionType.replace('_', ' ')} · ${item.inspectionDate}",
                    status = item.deliveryStatus,
                    createdAt = item.createdAt,
                    attempts = item.attempts,
                    lastError = item.lastError
                )
            }
        _outboxItems.value = (remote + rollCalls).sortedByDescending { it.createdAt }
        refreshPendingOutboxCount(ownerId)
    }

    fun retryOutboxItem(item: OutboxDisplayItem) {
        val ownerId = currentUserId ?: return
        viewModelScope.launch {
            when (item.kind) {
                OutboxKind.REMOTE_MUTATION -> {
                    val dao = repository.remoteMutationOutboxDao
                    val stored = dao.getById(item.id, ownerId, apiClient.baseUrl) ?: return@launch
                    if (stored.deliveryStatus == "DELIVERED") return@launch
                    dao.update(stored.copy(deliveryStatus = "PENDING", lastError = null))
                }
                OutboxKind.ROLL_CALL -> {
                    val dao = repository.rollCallOutboxDao
                    val stored = dao.getById(item.id, ownerId, apiClient.baseUrl) ?: return@launch
                    if (stored.deliveryStatus == "DELIVERED") return@launch
                    dao.update(stored.copy(deliveryStatus = "PENDING", lastError = null))
                }
            }
            OutboxWorkScheduler.enqueue(
                appContext,
                item.kind,
                item.id,
                ownerId,
                apiClient.baseUrl,
                replace = true
            )
            refreshOutboxItems()
        }
    }

    fun deleteOutboxItem(item: OutboxDisplayItem) {
        val ownerId = currentUserId ?: return
        viewModelScope.launch {
            OutboxWorkScheduler.cancel(
                appContext,
                item.kind,
                item.id,
                ownerId,
                apiClient.baseUrl
            )
            when (item.kind) {
                OutboxKind.REMOTE_MUTATION -> {
                    val dao = repository.remoteMutationOutboxDao
                    val stored = dao.getById(item.id, ownerId, apiClient.baseUrl)
                        ?: return@launch
                    dao.deleteFor(item.id, ownerId, apiClient.baseUrl)
                    if (stored.path == "gate/depart/" || stored.path == "gate/return/") {
                        repository.updateGateMovementSyncState(
                            item.id,
                            "DISCARDED",
                            "Removed from the delivery queue by the signed-in user."
                        )
                    }
                }
                OutboxKind.ROLL_CALL -> {
                    repository.rollCallOutboxDao.deleteFor(item.id, ownerId, apiClient.baseUrl)
                }
            }
            refreshOutboxItems()
        }
    }

    private suspend fun enqueuePendingOutboxWork() {
        val ownerId = currentUserId ?: return
        repository.remoteMutationOutboxDao
            .getPendingFor(ownerId, apiClient.baseUrl)
            .forEach {
                OutboxWorkScheduler.enqueue(
                    appContext,
                    OutboxKind.REMOTE_MUTATION,
                    it.id,
                    ownerId,
                    apiClient.baseUrl
                )
            }
        repository.rollCallOutboxDao
            .getPendingFor(ownerId, apiClient.baseUrl)
            .forEach {
                OutboxWorkScheduler.enqueue(
                    appContext,
                    OutboxKind.ROLL_CALL,
                    it.id,
                    ownerId,
                    apiClient.baseUrl
                )
            }
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

    fun reportIncident(input: IncidentReportInput) {
        viewModelScope.launch {
            val ownerId = currentUserId ?: run {
                syncManager.reportActionError("Sign in before recording incidents.")
                return@launch
            }
            val cleanDescription = input.description.trim()
            val cleanPhone = input.guardianContactPhone.trim()
            if (cleanDescription.isBlank()) {
                syncManager.reportActionError("Describe what happened before saving an incident.")
                return@launch
            }
            if (input.guardianContacted && cleanPhone.isBlank()) {
                syncManager.reportActionError("Enter the guardian phone number that was contacted.")
                return@launch
            }
            val incidentId = UUID.randomUUID().toString()
            val record = IncidentRecord(
                id = incidentId,
                ownerId = ownerId,
                tenantUrl = apiClient.baseUrl,
                houseId = input.student?.houseId ?: input.houseId,
                studentId = input.student?.id.orEmpty(),
                studentName = input.student?.fullName ?: "Campus-wide event",
                studentIndexNumber = input.student?.studentId.orEmpty(),
                category = input.category,
                severity = input.severity,
                location = input.location.trim(),
                description = cleanDescription,
                immediateAction = input.immediateAction.trim(),
                guardianContacted = input.guardianContacted,
                guardianContactPhone = cleanPhone,
                guardianContactTime = if (input.guardianContacted) {
                    input.guardianContactTime.trim()
                } else {
                    ""
                },
                medicalReferral = input.medicalReferral,
                reportedByName = input.actorName
            )
            // Local-first: an incident must be a durable record even with no connection.
            repository.saveIncident(record)
            repository.logAudit(
                action = "INCIDENT_REPORTED",
                actorRole = "House staff",
                actorName = record.reportedByName,
                details = "Reported ${record.severity.lowercase()} ${record.category.lowercase()} " +
                    "incident for ${record.studentName} at " +
                    record.location.ifBlank { "unrecorded location" } +
                    " (guardian contacted: ${record.guardianContacted})."
            )
            remoteAction(
                label = "Incident reported",
                path = "incidents/",
                payload = JSONObject()
                    .put("incident_id", incidentId)
                    .put("student_id", record.studentId)
                    .put("house_id", record.houseId)
                    .put("category", record.category)
                    .put("severity", record.severity)
                    .put("location", record.location)
                    .put("description", record.description)
                    .put("immediate_action", record.immediateAction)
                    .put("guardian_contacted", record.guardianContacted)
                    .put("guardian_contact_phone", record.guardianContactPhone)
                    .put("guardian_contact_time", record.guardianContactTime)
                    .put("medical_referral", record.medicalReferral)
                    .put("reported_by", record.reportedByName)
                    .put("reported_at", Instant.ofEpochMilli(record.createdAt).toString()),
                idempotencyKey = incidentId,
                onSuccess = {
                    repository.updateIncidentSyncState(incidentId, "SYNCED")
                },
                onQueued = {
                    repository.updateIncidentSyncState(incidentId, "QUEUED")
                }
            )
        }
    }

    fun updateIncidentStatus(
        incident: IncidentRecord,
        status: String,
        resolutionNotes: String,
        actorName: String
    ) {
        viewModelScope.launch {
            val cleanNotes = resolutionNotes.trim()
            if (status == "CLOSED" && cleanNotes.isBlank()) {
                syncManager.reportActionError("Record how the incident was resolved before closing it.")
                return@launch
            }
            repository.updateIncidentStatus(incident.id, status, cleanNotes)
            repository.logAudit(
                action = "INCIDENT_UPDATED",
                actorRole = "House staff",
                actorName = actorName,
                details = "Incident ${incident.id} marked $status for ${incident.studentName}."
            )
            remoteAction(
                label = "Incident update saved",
                path = "incidents/${incident.id}/status/",
                payload = JSONObject()
                    .put("status", status)
                    .put("resolution_notes", cleanNotes),
                idempotencyKey = UUID.randomUUID().toString()
            )
        }
    }

    fun rejectExeat(exeatId: String, approverName: String, reason: String) {        viewModelScope.launch {
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
                val cached = students.value.firstOrNull { it.id == mapped.id } ?: student
                val complete = dataSync.preserveUnprovidedStudentDetails(detail, mapped, cached)
                repository.saveStudent(complete)
                onLoaded(complete)
            } catch (error: IOException) {
                syncManager.reportActionError("Could not load student details: ${error.message}")
            } catch (error: IllegalStateException) {
                syncManager.reportActionError("Could not load student details: ${error.message}")
            }
        }
    }

    fun clearStudentRecordMessage() {
        _studentRecordMessage.value = null
    }

    fun saveStudentRecord(
            existing: Student?,
            input: StudentRecordInput,
            onSaved: (Student) -> Unit
        ) {
            if (!_apiPermissions.value.studentsManage) {
                _studentRecordMessage.value = "Your account cannot create or edit student records."
                return
            }
            val indexNumber = input.studentId.trim()
            val fullName = input.fullName.trim()
            val phone = input.guardianPhone.filterNot(Char::isWhitespace)
            if (indexNumber.length !in 3..32 ||
                !indexNumber.matches(Regex("[A-Za-z0-9][A-Za-z0-9./_-]*"))
            ) {
                _studentRecordMessage.value = "Enter a valid student index/admission number."
                return
            }
            if (fullName.length < 2) {
                _studentRecordMessage.value = "Enter the student's full name."
                return
            }
            if (input.houseId.isBlank()) {
                _studentRecordMessage.value = "Select the student's house."
                return
            }
            if (input.classForm.isBlank()) {
                _studentRecordMessage.value = "Enter the student's form/class."
                return
            }
            if (phone.isNotEmpty() && !phone.matches(Regex("^\\+?[0-9]{7,15}$"))) {
                _studentRecordMessage.value = "Enter a valid guardian phone number."
                return
            }
            viewModelScope.launch {
                try {
                    if (repository.studentIndexExists(indexNumber, existing?.id)) {
                        throw IllegalArgumentException(
                            "A student with index number $indexNumber already exists on this device."
                        )
                    }
                    val payload = JSONObject()
                        .put("index_number", indexNumber)
                        .put("full_name", fullName)
                        .put("gender", input.gender)
                        .put("class_form", input.classForm.trim())
                        .put("program", input.program.trim())
                        .put("house_id", input.houseId)
                        .put("guardian_name", input.guardianName.trim())
                        .put("guardian_phone", phone)
                        .put("guardian_relationship", input.guardianRelationship.trim())
                        .put("emergency_notes", input.emergencyNotes.trim())
                    val path = existing?.let { "students/${it.id}/" } ?: "students/"
                    val response = apiClient.post(path, payload)
                    val returnedStudent = response.optJSONObject("student") ?: response
                    val returnedId = returnedStudent.optString("id")
                        .takeIf(String::isNotBlank)
                        ?: existing?.id
                        ?: throw IllegalStateException(
                            "The server saved the student but did not return a student ID."
                        )
                    val mapped = dataSync.studentFrom(returnedStudent)
                    val saved = existing?.copy(
                        id = returnedId,
                        studentId = indexNumber,
                        fullName = fullName,
                        gender = input.gender,
                        classForm = input.classForm.trim(),
                        program = input.program.trim(),
                        houseId = input.houseId,
                        guardianName = input.guardianName.trim(),
                        guardianPhone = phone,
                        guardianRelationship = input.guardianRelationship.trim(),
                        emergencyNotes = input.emergencyNotes.trim().ifBlank { "Unknown" }
                    ) ?: Student(
                        id = returnedId,
                        studentId = indexNumber,
                        fullName = fullName,
                        gender = input.gender,
                        classForm = input.classForm.trim(),
                        program = input.program.trim().ifBlank { "Unknown" },
                        houseId = input.houseId,
                        roomId = "",
                        bedNumber = 0,
                        guardianName = input.guardianName.trim(),
                        guardianPhone = phone,
                        guardianRelationship = input.guardianRelationship.trim(),
                        status = "IN_DORM",
                        qrCodeKey = returnedStudent.optString("public_token"),
                        emergencyNotes = input.emergencyNotes.trim().ifBlank { "Unknown" }
                    )
                    val merged = mapped?.let {
                        saved.copy(
                            fullName = it.fullName.takeUnless { name -> name == "Student" }
                                ?: saved.fullName,
                            studentId = it.studentId.ifBlank { saved.studentId },
                            gender = it.gender.ifBlank { saved.gender },
                            classForm = it.classForm.ifBlank { saved.classForm },
                            program = it.program.ifBlank { saved.program },
                            houseId = it.houseId.ifBlank { saved.houseId },
                            roomId = it.roomId.ifBlank { saved.roomId },
                            bedNumber = it.bedNumber.takeIf { bed -> bed > 0 } ?: saved.bedNumber,
                            guardianName = it.guardianName.ifBlank { saved.guardianName },
                            guardianPhone = it.guardianPhone.ifBlank { saved.guardianPhone },
                            guardianRelationship = it.guardianRelationship.ifBlank {
                                saved.guardianRelationship
                            },
                            emergencyNotes = it.emergencyNotes.ifBlank {
                                saved.emergencyNotes
                            },
                            qrCodeKey = it.qrCodeKey.ifBlank { saved.qrCodeKey },
                            assignedRoomId = it.assignedRoomId ?: saved.assignedRoomId,
                            assignedRoomName = it.assignedRoomName.ifBlank {
                                saved.assignedRoomName
                            },
                            photoUrl = it.photoUrl.ifBlank { saved.photoUrl }
                        )
                    } ?: saved
                    repository.saveStudent(merged)
                    _studentRecordMessage.value = "Student record saved."
                    onSaved(merged)
                } catch (error: IOException) {
                    _studentRecordMessage.value = "Could not save student: ${error.message}"
                } catch (error: IllegalStateException) {
                    _studentRecordMessage.value = "Could not save student: ${error.message}"
                } catch (error: IllegalArgumentException) {
                    _studentRecordMessage.value = error.message
                }
            }
        }

    fun uploadStudentPhoto(student: Student, uri: Uri, onSaved: (Student) -> Unit) {
            if (!_apiPermissions.value.studentsManage) {
                _studentRecordMessage.value = "Your account cannot update student photos."
                return
            }
            viewModelScope.launch {
                try {
                    val response = apiClient.uploadImage("students/${student.id}/photo/", uri)
                    val rawPhotoUrl = listOf(
                        response.optString("photo_url"),
                        response.optString("profile_photo_url"),
                        response.optString("image_url"),
                        response.optJSONObject("student")?.optString("photo_url")
                    ).firstNotNullOfOrNull {
                        it?.takeIf { value -> value.isNotBlank() && value != "null" }
                    } ?: throw IllegalStateException(
                        "The server accepted the photo but returned no photo URL."
                    )
                    val photoUrl = if (rawPhotoUrl.startsWith("http://") ||
                        rawPhotoUrl.startsWith("https://")
                    ) {
                        rawPhotoUrl
                    } else {
                        "${apiClient.baseUrl.removeSuffix("/api/v1")}/${rawPhotoUrl.trimStart('/')}"
                    }
                    val updated = student.copy(photoUrl = photoUrl)
                    repository.saveStudent(updated)
                    _studentRecordMessage.value = "Student photo updated."
                    onSaved(updated)
                } catch (error: IOException) {
                    _studentRecordMessage.value = "Could not upload student photo: ${error.message}"
                } catch (error: IllegalStateException) {
                    _studentRecordMessage.value = "Could not upload student photo: ${error.message}"
                }
        }
    }

    // Gate Actions
    fun verifyStudentGateCode(codeOrIndex: String) {
        if (!_apiPermissions.value.gateOperate) {
            _gateScanResult.value = GateVerificationResult.LookupMessage(
                "Your signed-in account does not have gate-operation permission."
            )
            return
        }
        if (!apiClient.hasUsableOfflineGrace()) {
            _gateScanResult.value = GateVerificationResult.LookupMessage(
                "Revalidate the session online before verifying a gate pass."
            )
            return
        }
        viewModelScope.launch {
            _scanMessage.value = null
            try {
                _gateScanResult.value = repository.verifyGatePass(codeOrIndex.trim())
            } catch (error: IllegalStateException) {
                _gateScanResult.value = null
                _scanMessage.value = "Local gate verification failed: ${error.message}"
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
        if (!_apiPermissions.value.gateOperate) {
            syncManager.reportActionError("Your signed-in account does not have gate-operation permission.")
            return
        }
        if (!apiClient.hasUsableOfflineGrace()) {
            syncManager.reportActionError(
                "Gate movements are disabled until the session is revalidated online."
            )
            return
        }
        val ownerId = currentUserId
        if (ownerId.isNullOrBlank() || exeatId.isNullOrBlank()) {
            syncManager.reportActionError("A signed-in account and verified exeat pass are required.")
            return
        }
        viewModelScope.launch {
            try {
                val movement = repository.recordGateMovement(
                    student = student,
                    exeatId = exeatId,
                    movementType = movementType,
                    officerName = activeUserDisplayName.ifBlank { ownerId },
                    gateName = "Main School Gate",
                    remarks = "Verified against locally synced student and exeat records.",
                    ownerId = ownerId,
                    tenantUrl = apiClient.baseUrl
                )
                refreshPendingOutboxCount(ownerId)
                val pendingCount = repository.remoteMutationOutboxDao.countFor(
                    ownerId,
                    apiClient.baseUrl
                ) + repository.rollCallOutboxDao.countFor(ownerId, apiClient.baseUrl)
                syncManager.reportActionQueued(
                    "${movement.movementType.lowercase().replaceFirstChar(Char::uppercase)} verified locally at " +
                        "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(movement.timestamp))}; pending sync.",
                    pendingCount
                )
                OutboxWorkScheduler.enqueue(
                    appContext,
                    OutboxKind.REMOTE_MUTATION,
                    movement.id,
                    ownerId,
                    apiClient.baseUrl
                )
                _scanMessage.value =
                    "Verified locally at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(movement.timestamp))} — pending sync."
                _gateScanResult.value = null
                triggerSync()
            } catch (error: IllegalStateException) {
                val message = error.message ?: "The gate movement could not be recorded."
                _scanMessage.value = message
                syncManager.reportActionError(message)
            } catch (error: IllegalArgumentException) {
                val message = error.message ?: "The gate movement is invalid."
                _scanMessage.value = message
                syncManager.reportActionError(message)
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
                    currentUserId?.let { ownerId ->
                        refreshPendingOutboxCount(ownerId)
                    }
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
                                apiClient.restrictOfflineStartup("permissions_changed")
                                _apiPermissions.value = ApiPermissions()
                            }
                            _currentRole.value = newRole
                            _selectedHouseId.value = newPermissions.houseId
                        }
                        _serverDashboardMetrics.value = snapshot.metrics
                        _selectedHouseId.value = snapshot.defaultHouseId
                        setApiPermissions(snapshot.capabilities, snapshot.features)
                    }
                    apiClient.clearAccessRestriction()
                    currentUserId?.let { ownerId ->
                        refreshPendingOutboxCount(ownerId)
                        enqueuePendingOutboxWork()
                    }
                }
            } catch (error: DjangoApiException) {
                if (error.statusCode == 403) {
                    apiClient.restrictOfflineStartup(error.errorCode)
                    _apiPermissions.value = ApiPermissions()
                    syncManager.reportActionError(
                        apiClient.accessRestrictionMessage(error.errorCode)
                    )
                }
            } catch (_: IOException) {
                // SyncManager publishes the server error to the sync status flow.
            } catch (_: IllegalStateException) {
                // SyncManager publishes invalid server data to the sync status flow.
            }
        }
    }

    private suspend fun submitRemoteMutation(
        label: String,
        path: String,
        payload: JSONObject,
        idempotencyKey: String? = null,
        onSuccess: suspend (JSONObject) -> Unit = {},
        onQueued: suspend (String) -> Unit = {}
    ): Boolean {
        val itemId = idempotencyKey ?: UUID.randomUUID().toString()
        val response = try {
            apiClient.post(path, payload, idempotencyKey = itemId)
        } catch (error: IOException) {
            if (error is DjangoApiException && error.statusCode < 500) throw error
            if (!apiClient.hasUsableOfflineGrace()) {
                throw IllegalStateException(
                    "Offline access expired. Reconnect and revalidate before queuing changes."
                )
            }
            val ownerId = currentUserId
                ?: throw IllegalStateException("Sign in before saving changes for delivery.")
            val dao = repository.remoteMutationOutboxDao
            val inserted = dao.insert(
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
            if (inserted == -1L) {
                val count = dao.countFor(ownerId, apiClient.baseUrl) +
                    repository.rollCallOutboxDao.countFor(ownerId, apiClient.baseUrl)
                syncManager.reportActionQueued(
                    "$label is already queued for delivery.",
                    count
                )
                onQueued(itemId)
                return false
            }
            OutboxWorkScheduler.enqueue(
                appContext,
                OutboxKind.REMOTE_MUTATION,
                itemId,
                ownerId,
                apiClient.baseUrl
            )
            val pendingCount = dao.countFor(ownerId, apiClient.baseUrl) +
                repository.rollCallOutboxDao.countFor(ownerId, apiClient.baseUrl)
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
        idempotencyKey: String? = null,
        onSuccess: suspend (JSONObject) -> Unit = {},
        onQueued: suspend (String) -> Unit = {}
    ) {
        try {
            submitRemoteMutation(label, path, payload, idempotencyKey, onSuccess, onQueued)
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
                syncManager.clearSessionStatus()
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
