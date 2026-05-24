package com.vibetuned.ln_reader.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibetuned.ln_reader.ui.common.appContainer
import com.vibetuned.ln_reader.ui.timer.TimerControls
import com.vibetuned.ln_reader.ui.timer.TimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.verticalScroll(scroll)) {
            Text(
                "Sleep timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp)
            )
            TimerControls(
                state = state,
                onStartTime = { minutes ->
                    if (viewModel.startTime(minutes)) onDismiss()
                },
                onStartChapters = { count ->
                    if (viewModel.startChapters(count)) onDismiss()
                },
                onStartEndOfChapter = {
                    if (viewModel.startEndOfChapter()) onDismiss()
                },
                onCancel = {
                    viewModel.cancel()
                    onDismiss()
                },
                onPostpone = {
                    if (viewModel.postpone()) onDismiss()
                },
                onDismissExpired = {
                    viewModel.dismissExpired()
                    onDismiss()
                },
                onFadeOutChanged = viewModel::setFadeOut
            )
        }
    }
}
