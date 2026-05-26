package com.vibetuned.ln_reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vibetuned.ln_reader.ui.library.LibraryScreen
import com.vibetuned.ln_reader.ui.player.PlayerScreen
import com.vibetuned.ln_reader.ui.reader.ReaderScreen
import com.vibetuned.ln_reader.ui.settings.SettingsScreen
import com.vibetuned.ln_reader.ui.timer.TimerScreen
import com.vibetuned.ln_reader.ui.viewer.ViewerScreen

object PlayerRoute {
    const val PATTERN = "player?bookId={bookId}"
    fun forBook(bookId: String) = "player?bookId=$bookId"
}

object ViewerRoute {
    const val PATTERN = "viewer?bookId={bookId}"
    fun forBook(bookId: String) = "viewer?bookId=$bookId"
}

object ReaderRoute {
    const val PATTERN = "reader?bookId={bookId}"
    fun forBook(bookId: String) = "reader?bookId=$bookId"
}

@Composable
fun LnReaderNavGraph(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.Start.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(TopLevelDestination.Library.route) {
            LibraryScreen(
                onPlayBook = { bookId ->
                    navController.navigate(PlayerRoute.forBook(bookId))
                },
                onViewImages = { bookId ->
                    navController.navigate(ViewerRoute.forBook(bookId))
                },
                onReadBook = { bookId ->
                    navController.navigate(ReaderRoute.forBook(bookId))
                }
            )
        }
        composable(
            route = PlayerRoute.PATTERN,
            arguments = listOf(
                navArgument("bookId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            PlayerScreen(
                bookId = backStackEntry.arguments?.getString("bookId"),
                onBack = { navController.popBackStack() },
                onViewImages = { bookId ->
                    navController.navigate(ViewerRoute.forBook(bookId))
                },
                onOpenReader = { bookId ->
                    navController.navigate(ReaderRoute.forBook(bookId))
                }
            )
        }
        composable(
            route = ViewerRoute.PATTERN,
            arguments = listOf(
                navArgument("bookId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            ViewerScreen(
                bookId = backStackEntry.arguments?.getString("bookId"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = ReaderRoute.PATTERN,
            arguments = listOf(
                navArgument("bookId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            ReaderScreen(
                bookId = backStackEntry.arguments?.getString("bookId"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(TopLevelDestination.Timer.route) { TimerScreen() }
        composable(TopLevelDestination.Settings.route) { SettingsScreen() }
    }
}
