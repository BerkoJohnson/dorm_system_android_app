package com.example

import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import com.example.data.local.AppDatabase
import com.example.data.remote.DjangoApiClient
import com.example.data.remote.DjangoDataSync
import com.example.data.repository.BoardingRepository
import com.example.data.repository.SyncManager
import com.example.ui.components.RoleSelectorBar
import com.example.ui.screens.ApiLoginScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DormOccupancyScreen
import com.example.ui.screens.ExeatScreen
import com.example.ui.screens.GateCheckScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OperationsHubScreen
import com.example.ui.screens.ReportsAuditScreen
import com.example.ui.screens.RollCallScreen
import com.example.ui.screens.RollCallHistoryScreen
import com.example.ui.screens.StudentDirectoryScreen
import com.example.ui.screens.SmsCampaignScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.SHSBoardingTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.data.model.UserRole
import com.example.data.model.userRoleFromServerRole
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(this)
        val repo = BoardingRepository(db)
        val apiClient = DjangoApiClient(applicationContext)
        val dataSync = DjangoDataSync(db, apiClient)
        val syncManager = SyncManager(apiClient)

        setContent {
            SHSBoardingTheme {
                val viewModel: MainViewModel = remember {
                    MainViewModel(repo, syncManager, dataSync, apiClient)
                }

                SHSAppRoot(viewModel = viewModel, apiClient = apiClient)
            }
        }
    }
}

@Composable
fun SHSAppRoot(viewModel: MainViewModel, apiClient: DjangoApiClient) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val permissions by viewModel.apiPermissions.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val sessionExpired by apiClient.sessionExpired.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var checkingSession by remember { mutableStateOf(true) }
    var authenticated by remember { mutableStateOf(false) }
    val moreItems = buildList {
        if (permissions.gateOperate) add(AppScreen.GATE_CHECK to ("Gate lookup" to Icons.Default.QrCodeScanner))
        if (permissions.studentsView) {
            add(AppScreen.STUDENTS to ("Students" to Icons.Default.People))
            add(AppScreen.DORMS to ("Dorms" to Icons.Default.Bed))
        }
        if (permissions.rollCall) {
            add(AppScreen.ROLL_CALL_HISTORY to ("Roll-call history" to Icons.Default.History))
        }
        add(AppScreen.NOTIFICATIONS to ("Notifications" to Icons.Default.Notifications))
        if (permissions.isAdmin || permissions.reports || permissions.auditView) {
            add(AppScreen.REPORTS_SYNC to ("Reports & audit" to Icons.Default.Analytics))
        }
        if (permissions.smsDispatchEnabled) {
            add(AppScreen.SMS_CAMPAIGNS to ("SMS campaigns" to Icons.Default.Notifications))
        }
    }
    val moreScreens = moreItems.mapTo(mutableSetOf()) { it.first }

    DisposableEffect(authenticated, context, viewModel) {
        if (!authenticated) {
            onDispose {}
        } else {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            var wasValidated = false
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val validated = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )
                    if (validated && !wasValidated) viewModel.triggerSync()
                    wasValidated = validated
                }

                override fun onLost(network: Network) {
                    wasValidated = false
                }
            }
            connectivity.registerDefaultNetworkCallback(callback)
            onDispose { connectivity.unregisterNetworkCallback(callback) }
        }
    }

    LaunchedEffect(apiClient) {
        viewModel.clearCachedSchoolData()
        if (apiClient.hasSession && apiClient.tenantConfigured) {
            try {
                val session = apiClient.get("auth/me/")
                val user = session.optJSONObject("user")
                val role = user?.optString("role").orEmpty()
                val appRole = validatedSessionRole(session)
                    ?: throw IOException("This account has no supported active staff role. Contact the school administrator.")
                val capabilities = user?.optJSONObject("capabilities")
                if (
                    role in setOf("HOUSEMASTER", "HOUSE_TUTOR") &&
                    capabilities?.optString("house_id").isNullOrBlank()
                ) {
                    throw IOException("This account is not assigned to a house. Contact the school administrator.")
                }
                viewModel.setAuthenticatedRole(appRole)
                viewModel.setApiPermissions(
                    capabilities,
                    session.optJSONObject("features")
                )
                viewModel.setAuthenticatedUserId(user?.optString("id"))
                authenticated = true
                viewModel.loadNotifications()
                viewModel.triggerSync()
            } catch (_: IOException) {
                viewModel.clearSensitiveState()
            } catch (_: IllegalStateException) {
                viewModel.clearSensitiveState()
            }
        } else if (apiClient.hasSession) {
            viewModel.clearSensitiveState()
        }
        checkingSession = false
    }

    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            authenticated = false
            viewModel.navigateTo(AppScreen.DASHBOARD)
            viewModel.clearSensitiveState()
        }
    }

    BackHandler(enabled = authenticated && currentScreen != AppScreen.DASHBOARD) {
        if (currentScreen in moreScreens) {
            viewModel.navigateTo(AppScreen.OPERATIONS)
        } else {
            viewModel.navigateTo(AppScreen.DASHBOARD)
        }
    }

    LaunchedEffect(authenticated, permissions, currentScreen) {
        val canAccessScreen = authenticated && when (currentScreen) {
            AppScreen.DASHBOARD -> true
            AppScreen.OPERATIONS -> true
            AppScreen.ROLL_CALL -> permissions.rollCall
            AppScreen.EXEATS -> permissions.exeatsView
            AppScreen.REPORTS_SYNC ->
                permissions.isAdmin || permissions.reports || permissions.auditView
            AppScreen.SMS_CAMPAIGNS -> permissions.smsDispatchEnabled
            else -> currentScreen in moreScreens
        }
        if (!canAccessScreen) {
            viewModel.navigateTo(AppScreen.DASHBOARD)
        }
    }

    if (!authenticated) {
        if (checkingSession) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Text("Checking school sign-in…", modifier = Modifier.padding(top = 12.dp))
            }
        } else {
            ApiLoginScreen(apiClient = apiClient) { session ->
                val appRole = validatedSessionRole(session)
                if (appRole == null) {
                    viewModel.clearSensitiveState()
                    return@ApiLoginScreen
                }
                authenticated = true
                val user = session.optJSONObject("user")
                viewModel.setAuthenticatedRole(appRole)
                viewModel.setApiPermissions(
                    user?.optJSONObject("capabilities"),
                    session.optJSONObject("features")
                )
                viewModel.setAuthenticatedUserId(user?.optString("id"))
                viewModel.loadNotifications()
                viewModel.triggerSync()
            }
        }
        return
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            RoleSelectorBar(
                currentRole = currentRole,
                syncStatus = syncState.syncStatusMessage,
                isOnline = syncState.isOnline,
                pendingOutboxCount = syncState.pendingOutboxCount,
                onSyncClick = {
                    if (permissions.isAdmin) viewModel.navigateTo(AppScreen.REPORTS_SYNC)
                },
                onLogoutClick = {
                    viewModel.signOut {
                        authenticated = false
                        viewModel.navigateTo(AppScreen.DASHBOARD)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.DASHBOARD,
                    onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                    icon = { AnimatedNavigationIcon(currentScreen == AppScreen.DASHBOARD) { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") } },
                    label = { Text("Home", style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                    colors = navigationItemColors()
                )

                if (permissions.rollCall) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.ROLL_CALL,
                        onClick = { viewModel.navigateTo(AppScreen.ROLL_CALL) },
                        icon = { AnimatedNavigationIcon(currentScreen == AppScreen.ROLL_CALL) { Icon(Icons.Default.Checklist, contentDescription = "Roll Call") } },
                        label = { Text("Roll Call", style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                        colors = navigationItemColors()
                    )
                }

                if (permissions.exeatsView) {
                    NavigationBarItem(
                        selected = currentScreen == AppScreen.EXEATS,
                        onClick = { viewModel.navigateTo(AppScreen.EXEATS) },
                        icon = {
                            AnimatedNavigationIcon(currentScreen == AppScreen.EXEATS) {
                                if (metrics.pendingExeats > 0) {
                                    BadgedBox(badge = { Badge { Text("${metrics.pendingExeats}") } }) {
                                        Icon(Icons.Default.ExitToApp, contentDescription = "Exeats")
                                    }
                                } else {
                                    Icon(Icons.Default.ExitToApp, contentDescription = "Exeats")
                                }
                            }
                        },
                        label = { Text("Exeats", style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                        colors = navigationItemColors()
                    )
                }

                val isOperationsSelected = currentScreen == AppScreen.OPERATIONS || currentScreen in moreScreens
                NavigationBarItem(
                    selected = isOperationsSelected,
                    onClick = { viewModel.navigateTo(AppScreen.OPERATIONS) },
                    icon = {
                        AnimatedNavigationIcon(isOperationsSelected) {
                            if (unreadNotificationCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(
                                                unreadNotificationCount.coerceAtMost(99).toString()
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.MoreHoriz, contentDescription = "Operations")
                                }
                            } else {
                                Icon(Icons.Default.MoreHoriz, contentDescription = "Operations")
                            }
                        }
                    },
                    label = { Text("Operations", style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                    colors = navigationItemColors()
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 24 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 24 })
                },
                label = "screen-transition"
            ) { screen ->
                val canRenderScreen = when (screen) {
                    AppScreen.DASHBOARD -> true
                    AppScreen.OPERATIONS -> true
                    AppScreen.ROLL_CALL -> permissions.rollCall
                    AppScreen.EXEATS -> permissions.exeatsView
                    AppScreen.REPORTS_SYNC ->
                        permissions.isAdmin || permissions.reports || permissions.auditView
                    else -> screen in moreScreens
                }
                if (!canRenderScreen) {
                    Text("Your staff role does not have access to this page.")
                } else {
                    when (screen) {
                        AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                        AppScreen.ROLL_CALL -> RollCallScreen(viewModel = viewModel)
                        AppScreen.EXEATS -> ExeatScreen(
                            viewModel = viewModel,
                            canCreate = permissions.exeatsCreate,
                            canApprove = permissions.exeatsApprove,
                            canReview = permissions.exeatReview
                        )
                        AppScreen.GATE_CHECK -> GateCheckScreen(viewModel = viewModel)
                        AppScreen.STUDENTS -> StudentDirectoryScreen(viewModel = viewModel)
                        AppScreen.DORMS -> DormOccupancyScreen(
                            viewModel = viewModel,
                            canManageAllocations = permissions.studentsManage
                        )
                        AppScreen.OPERATIONS -> OperationsHubScreen(viewModel = viewModel)
                        AppScreen.ROLL_CALL_HISTORY -> RollCallHistoryScreen(viewModel = viewModel)
                        AppScreen.NOTIFICATIONS -> NotificationsScreen(viewModel = viewModel)
                        AppScreen.REPORTS_SYNC -> ReportsAuditScreen(
                            viewModel = viewModel,
                            canViewReports = permissions.reports,
                            canViewAudit = permissions.auditView
                        )
                        AppScreen.SMS_CAMPAIGNS -> SmsCampaignScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

private fun validatedSessionRole(session: org.json.JSONObject): UserRole? {
    val user = session.optJSONObject("user") ?: return null
    if (!user.optBoolean("is_active")) return null
    val role = user.optString("role")
    val capabilities = user.optJSONObject("capabilities") ?: return null
    if (!role.equals(capabilities.optString("role"), ignoreCase = true)) return null
    if (role.equals("ADMIN", ignoreCase = true) && !capabilities.optBoolean("is_admin")) return null
    return userRoleFromServerRole(role)
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = GoldAccent.copy(alpha = 0.22f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun AnimatedNavigationIcon(
    selected: Boolean,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "navigation-icon-scale"
    )
    Box(modifier = androidx.compose.ui.Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }) {
        content()
    }
}
