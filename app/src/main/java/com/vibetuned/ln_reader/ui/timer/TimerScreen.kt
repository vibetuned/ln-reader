package com.vibetuned.ln_reader.ui.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibetuned.ln_reader.ui.common.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen() {
    val container = appContainer()
    val viewModel: TimerViewModel = viewModel(
        factory = TimerViewModel.factory(
            playerHolder = container.playerHolder,
            bookRepository = container.bookRepository,
            sleepTimer = container.sleepTimerController
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sleep Timer") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
        ) {
            TimerControls(
                state = state,
                onStartTime = { viewModel.startTime(it) },
                onStartChapters = { viewModel.startChapters(it) },
                onStartEndOfChapter = { viewModel.startEndOfChapter() },
                onCancel = viewModel::cancel,
                onPostpone = { viewModel.postpone() },
                onDismissExpired = viewModel::dismissExpired,
                onFadeOutChanged = viewModel::setFadeOut
            )
        }
    }
}
