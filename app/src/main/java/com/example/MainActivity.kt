package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.RecoveryUiState
import com.example.ui.RecoveryViewModel
import com.example.ui.components.DismissibleBanner
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.GuidesHistoryScreen
import com.example.ui.screens.TerminalScriptsScreen
import com.example.ui.screens.ToolsInspectorScreen
import com.example.ui.theme.RecoveryAdbTheme

private data class NavItem(
    val route: String,
    val labelHe: String,
    val labelEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private const val ROUTE_CONNECTION = "connection"
private const val ROUTE_TERMINAL = "terminal"
private const val ROUTE_TOOLS = "tools"
private const val ROUTE_GUIDES = "guides"

private val navItems = listOf(
    NavItem(ROUTE_CONNECTION, "חיבור", "Connect", Icons.Filled.Usb, Icons.Outlined.Usb, "nav_connection"),
    NavItem(ROUTE_TERMINAL, "טרמינל", "Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_terminal"),
    NavItem(ROUTE_TOOLS, "כלים", "Tools", Icons.Filled.Build, Icons.Outlined.Build, "nav_tools"),
    NavItem(ROUTE_GUIDES, "מדריך", "Guides", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "nav_guides"),
)

class MainActivity : ComponentActivity() {
    private val viewModel: RecoveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
            val batchScripts by viewModel.batchScripts.collectAsStateWithLifecycle()
            val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
            val savedPresets by viewModel.savedPresets.collectAsStateWithLifecycle()

            val layoutDir = if (state.isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr

            RecoveryAdbTheme(darkTheme = state.darkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                    RecoveryAdbAppShell(
                        state = state,
                        savedDevices = savedDevices,
                        batchScripts = batchScripts,
                        commandLogs = commandLogs,
                        savedPresets = savedPresets,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryAdbAppShell(
    state: RecoveryUiState,
    savedDevices: List<com.example.data.local.SavedDeviceEntity>,
    batchScripts: List<com.example.data.local.BatchScriptEntity>,
    commandLogs: List<com.example.data.local.CommandLogEntity>,
    savedPresets: List<com.example.data.local.SavedPresetEntity>,
    viewModel: RecoveryViewModel,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isHe = state.isHebrew

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Recovery ADB",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    actions = {
                        TextButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("language_toggle_button"),
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(if (isHe) "EN" else "עב")
                        }
                        IconButton(
                            onClick = { viewModel.toggleTheme() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("theme_toggle_button"),
                        ) {
                            Icon(
                                imageVector = if (state.darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isHe) "החלף ערכת נושא" else "Toggle theme",
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = if (isHe) item.labelHe else item.labelEn,
                                    )
                                },
                                label = { Text(if (isHe) item.labelHe else item.labelEn) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = if (isHe) item.labelHe else item.labelEn,
                                    )
                                },
                                label = { Text(if (isHe) item.labelHe else item.labelEn) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize(),
                    ) {
                        val bannerMsg = state.errorBanner ?: state.statusMessage
                        if (bannerMsg != null) {
                            DismissibleBanner(
                                message = bannerMsg,
                                isError = state.errorBanner != null,
                                onDismiss = { viewModel.clearErrorBanner() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_CONNECTION,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            composable(ROUTE_CONNECTION) {
                                ConnectionScreen(
                                    state = state,
                                    savedDevices = savedDevices,
                                    viewModel = viewModel,
                                )
                            }
                            composable(ROUTE_TERMINAL) {
                                TerminalScriptsScreen(
                                    state = state,
                                    scripts = batchScripts,
                                    viewModel = viewModel,
                                )
                            }
                            composable(ROUTE_TOOLS) {
                                ToolsInspectorScreen(
                                    state = state,
                                    savedPresets = savedPresets,
                                    viewModel = viewModel,
                                    onNavigateToTerminal = { navigateTo(ROUTE_TERMINAL) },
                                )
                            }
                            composable(ROUTE_GUIDES) {
                                GuidesHistoryScreen(
                                    state = state,
                                    logs = commandLogs,
                                    viewModel = viewModel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
