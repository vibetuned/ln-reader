package com.vibetuned.ln_reader.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.HorizontalDivider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibetuned.ln_reader.ui.common.appContainer
import com.vibetuned.ln_reader.ui.stats.StatsSection
import com.vibetuned.ln_reader.ui.stats.StatsViewModel
import com.vibetuned.ln_reader.ui.timer.TimerControls
import com.vibetuned.ln_reader.ui.timer.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenSessions: () -> Unit = {}) {
    val container = appContainer()
    val appContext = LocalContext.current.applicationContext
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(appContext, container.downloadPreferences)
    )
    // The sleep timer keeps its own ViewModel — the same one the player's timer sheet uses — so
    // the two surfaces stay in step without Settings knowing anything about playback.
    val timerViewModel: TimerViewModel = viewModel(
        factory = TimerViewModel.factory(
            playerHolder = container.playerHolder,
            bookRepository = container.bookRepository,
            sleepTimer = container.sleepTimerController
        )
    )
    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.factory(container.readLogRepository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timerState by timerViewModel.state.collectAsStateWithLifecycle()
    val statsState by statsViewModel.state.collectAsStateWithLifecycle()

    // The log grows while you listen; re-aggregate each time this screen is shown.
    LaunchedEffect(Unit) { statsViewModel.refresh() }
    val scroll = rememberScrollState()

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "When you import a book from Drive, OneDrive or any cloud source, the file " +
                        "is copied locally so playback streams from disk. Choose where those " +
                        "copies live. The default is the app's internal storage (hidden, " +
                        "deleted when you uninstall the app).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                DownloadFolderRow(
                    folderName = state.downloadFolderName,
                    isCustom = state.downloadFolderUri != null,
                    onPick = { pickFolder.launch(null) },
                    onReset = viewModel::resetToInternal
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Existing books stay in their current location. New downloads use whichever " +
                        "folder is selected here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))

                Text(
                    "Time spent",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "How long each book has been open, listening and reading together. " +
                        "Tap Details for the individual sessions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                StatsSection(
                    state = statsState,
                    onGranularityChange = statsViewModel::setGranularity,
                    onOpenSessions = onOpenSessions
                )

                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))

                Text(
                    "Sleep timer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stop playback after a stretch of listening or at a chapter boundary. " +
                        "Picking an option while a timer runs replaces it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TimerControls(
                    state = timerState,
                    // This column already insets its content; don't stack a second margin.
                    horizontalPadding = 0.dp,
                    onStartTime = { timerViewModel.startTime(it) },
                    onStartChapters = { timerViewModel.startChapters(it) },
                    onStartEndOfChapter = { timerViewModel.startEndOfChapter() },
                    onCancel = timerViewModel::cancel,
                    onPostpone = { timerViewModel.postpone() },
                    onDismissExpired = timerViewModel::dismissExpired,
                    onFadeOutSecondsChanged = timerViewModel::setFadeOutSeconds
                )
            }
        }
    }
}

@Composable
private fun DownloadFolderRow(
    folderName: String?,
    isCustom: Boolean,
    onPick: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Download folder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            isCustom -> folderName ?: "Custom folder"
                            else -> "Internal app storage"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCustom) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f)
                    ) { Text("Use internal") }
                    Button(
                        onClick = onPick,
                        modifier = Modifier.weight(1f)
                    ) { Text("Change") }
                } else {
                    Button(
                        onClick = onPick,
                        modifier = Modifier.weight(1f)
                    ) { Text("Pick folder") }
                }
            }
        }
    }
}
