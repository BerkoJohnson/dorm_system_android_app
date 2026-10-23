package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.local.InitialDataSeeder
import com.example.data.repository.BoardingRepository
import com.example.data.repository.SyncManager
import com.example.ui.components.RoleSelectorBar
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DormOccupancyScreen
import com.example.ui.screens.ExeatScreen
import com.example.ui.screens.GateCheckScreen
import com.example.ui.screens.ReportsAuditScreen
import com.example.ui.screens.RollCallScreen
import com.example.ui.screens.StudentDirectoryScreen
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SHSBoardingTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(this)
        val repo = BoardingRepository(db)
        val syncManager = SyncManager()

        setContent {
            SHSBoardingTheme {
                LaunchedEffect(Unit) {
                    InitialDataSeeder.seedIfEmpty(db)
                }

                val viewModel: MainViewModel = remember {
                    MainViewModel(repo, syncManager)
                }

                SHSAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SHSAppRoot(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()

    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD) {
        viewModel.navigateTo(AppScreen.DASHBOARD)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            RoleSelectorBar(
                currentRole = currentRole,
                onRoleSelected = { viewModel.setRole(it) },
                syncStatus = syncState.syncStatusMessage,
                onSyncClick = { viewModel.navigateTo(AppScreen.REPORTS_SYNC) }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Dashboard
                NavigationBarItem(
                    selected = currentScreen == AppScreen.DASHBOARD,
                    onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Overview", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navigationItemColors()
                )

                // Roll Call
                NavigationBarItem(
                    selected = currentScreen == AppScreen.ROLL_CALL,
                    onClick = { viewModel.navigateTo(AppScreen.ROLL_CALL) },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = "Roll Call") },
                    label = { Text("Roll Call", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navigationItemColors()
                )

                // Exeats (with badge for pending)
                NavigationBarItem(
                    selected = currentScreen == AppScreen.EXEATS,
                    onClick = { viewModel.navigateTo(AppScreen.EXEATS) },
                    icon = {
                        if (metrics.pendingExeats > 0) {
                            BadgedBox(badge = { Badge { Text("${metrics.pendingExeats}") } }) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "Exeats")
                            }
                        } else {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Exeats")
                        }
                    },
                    label = { Text("Exeats", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navigationItemColors()
                )

                // Gate QR Check
                NavigationBarItem(
                    selected = currentScreen == AppScreen.GATE_CHECK,
                    onClick = { viewModel.navigateTo(AppScreen.GATE_CHECK) },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Gate Check") },
                    label = { Text("Gate QR", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navigationItemColors()
                )

                // Students
                NavigationBarItem(
                    selected = currentScreen == AppScreen.STUDENTS,
                    onClick = { viewModel.navigateTo(AppScreen.STUDENTS) },
                    icon = { Icon(Icons.Default.People, contentDescription = "Students") },
                    label = { Text("Students", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navigationItemColors()
                )

                // Dorms
                NavigationBarItem(
                    selected = currentScreen == AppScreen.DORMS,
                    onClick = { viewModel.navigateTo(AppScreen.DORMS) },
                    icon = { Icon(Icons.Default.Bed, contentDescription = "Dorms") },
                    label = { Text("Dorms", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navigationItemColors()
                )

                // Sync & Audit
                NavigationBarItem(
                    selected = currentScreen == AppScreen.REPORTS_SYNC,
                    onClick = { viewModel.navigateTo(AppScreen.REPORTS_SYNC) },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Reports & Sync") },
                    label = { Text("Reports", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
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
            when (currentScreen) {
                AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                AppScreen.ROLL_CALL -> RollCallScreen(viewModel = viewModel)
                AppScreen.EXEATS -> ExeatScreen(viewModel = viewModel)
                AppScreen.GATE_CHECK -> GateCheckScreen(viewModel = viewModel)
                AppScreen.STUDENTS -> StudentDirectoryScreen(viewModel = viewModel)
                AppScreen.DORMS -> DormOccupancyScreen(viewModel = viewModel)
                AppScreen.REPORTS_SYNC -> ReportsAuditScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = NavyPrimary,
    selectedTextColor = NavyPrimary,
    indicatorColor = GoldAccent.copy(alpha = 0.25f),
    unselectedIconColor = Color(0xFF64748B),
    unselectedTextColor = Color(0xFF64748B)
)
