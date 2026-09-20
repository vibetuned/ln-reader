package com.vibetuned.ln_reader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Library("library", "Library", Icons.AutoMirrored.Outlined.LibraryBooks),
    Player("player", "Player", Icons.Outlined.PlayCircle),
    Viewer("viewer", "Images", Icons.Outlined.Image),
    // The sleep timer lives inside Settings rather than owning a tab of its own — it is a
    // setting you adjust occasionally, and the bottom bar reads better with four destinations.
    Settings("settings", "Settings", Icons.Outlined.Settings);

    companion object {
        val Start: TopLevelDestination = Library
    }
}
