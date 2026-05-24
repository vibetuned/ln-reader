package com.vibetuned.ln_reader.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Timer
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
    Timer("timer", "Timer", Icons.Outlined.Timer),
    Settings("settings", "Settings", Icons.Outlined.Settings);

    companion object {
        val Start: TopLevelDestination = Library
    }
}
