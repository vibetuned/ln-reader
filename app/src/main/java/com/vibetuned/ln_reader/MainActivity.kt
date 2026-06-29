package com.vibetuned.ln_reader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vibetuned.ln_reader.ui.common.appContainer
import com.vibetuned.ln_reader.ui.navigation.LnReaderNavGraph
import com.vibetuned.ln_reader.ui.navigation.PlayerRoute
import com.vibetuned.ln_reader.ui.navigation.ReaderRoute
import com.vibetuned.ln_reader.ui.navigation.TopLevelDestination
import com.vibetuned.ln_reader.ui.player.MiniPlayerBar
import com.vibetuned.ln_reader.ui.theme.LnReaderTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()
        enableEdgeToEdge()
        setContent {
            LnReaderTheme {
                LnReaderApp()
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun LnReaderApp() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination

    // On a fresh process, reopen whatever book the user was last listening to (paused at the saved
    // position — launching the app should not start audio on its own), layered on top of the
    // Library so Back still returns home. The guard lives on the process-scoped container so this
    // fires once per launch — on a cold start and after Android kills the backgrounded process —
    // but never again across config changes (rotating or returning from another screen must not
    // yank the user back to the player).
    val container = appContainer()
    LaunchedEffect(Unit) {
        if (container.lastBookRestoreHandled) return@LaunchedEffect
        container.lastBookRestoreHandled = true
        val bookId = container.positionRepository.lastPlayedBookId() ?: return@LaunchedEffect
        // If a process-death restore already put the player back on top via the saved back stack,
        // it reopens the book on its own — don't push a duplicate entry.
        if (navController.currentDestination?.route == PlayerRoute.PATTERN) return@LaunchedEffect
        navController.navigate(PlayerRoute.forBook(bookId))
    }

    Scaffold(
        // Don't let the outer Scaffold add system-bar insets to the content padding — each
        // screen's own Scaffold/TopAppBar consumes the status bar, and the NavigationBar below
        // consumes the bottom inset. Without this, edge-to-edge (enforced on Android 15+) makes
        // the status-bar inset get applied twice, leaving a tall blank band above the app bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Mini-player sits just above the navigation bar on every screen except the full
            // Player (which already has the complete transport). It renders nothing when no book
            // is loaded, so the nav bar simply sits on its own.
            Column {
                if (currentDestination?.route != PlayerRoute.PATTERN) {
                    val openReader: (String) -> Unit = { bookId ->
                        navController.navigate(ReaderRoute.forBook(bookId)) {
                            launchSingleTop = true
                        }
                    }
                    MiniPlayerBar(
                        onExpand = { bookId ->
                            navController.navigate(PlayerRoute.forBook(bookId)) {
                                launchSingleTop = true
                            }
                        },
                        // Hide the Read button when we're already in the reader.
                        onOpenReader = if (currentDestination?.route == ReaderRoute.PATTERN) null
                        else openReader
                    )
                }
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any {
                            (it.route ?: "").substringBefore('?') == dest.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LnReaderNavGraph(
                navController = navController,
                startDestination = TopLevelDestination.Start.route
            )
        }
    }
}
