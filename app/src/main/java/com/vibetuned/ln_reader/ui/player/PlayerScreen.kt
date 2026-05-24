package com.vibetuned.ln_reader.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.ui.common.appContainer
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String? = null,
    onBack: () -> Unit = {},
    onViewImages: (String) -> Unit = {}
) {
    val container = appContainer()
    val viewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(
            playerHolder = container.playerHolder,
            bookRepository = container.bookRepository,
            positionRepository = container.positionRepository
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sleepTimerState by container.sleepTimerController.state.collectAsStateWithLifecycle()
    val sleepTimerExpired by container.sleepTimerController.expiredConfig.collectAsStateWithLifecycle()
    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        if (bookId != null) viewModel.open(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSpeed = true }) {
                        Icon(Icons.Outlined.Speed, contentDescription = "Playback speed")
                    }
                    IconButton(onClick = { showChapters = true }) {
                        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "Chapters")
                    }
                    val currentBookId = state.book?.id
                    IconButton(
                        onClick = { currentBookId?.let(onViewImages) },
                        enabled = currentBookId != null
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = "Images")
                    }
                    val timerActive = sleepTimerState != null || sleepTimerExpired != null
                    IconButton(
                        onClick = { showSleepTimer = true },
                        enabled = state.book != null
                    ) {
                        Icon(
                            imageVector = if (timerActive) Icons.Filled.Bedtime
                            else Icons.Outlined.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (timerActive) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CenteredProgress()
                state.book == null -> NoBookSelected()
                else -> PlayerContent(
                    state = state,
                    isBuffering = state.isBuffering,
                    onPlayPause = viewModel::togglePlay,
                    onSeek = viewModel::seekTo,
                    onSkipBack = viewModel::skipBack,
                    onSkipForward = viewModel::skipForward,
                    onPrevChapter = viewModel::prevChapter,
                    onNextChapter = viewModel::nextChapter
                )
            }
        }
    }

    if (showSpeed) {
        SpeedSheet(
            current = state.playbackSpeed,
            onSpeed = {
                viewModel.setSpeed(it)
                showSpeed = false
            },
            onDismiss = { showSpeed = false }
        )
    }

    if (showChapters && state.book != null) {
        ChapterListSheet(
            chapters = state.chapters,
            currentChapterIndex = state.currentChapterIndex,
            onChapterClick = { chapter ->
                viewModel.seekTo(chapter.startMs)
                showChapters = false
            },
            onDismiss = { showChapters = false }
        )
    }

    if (showSleepTimer) {
        SleepTimerSheet(onDismiss = { showSleepTimer = false })
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoBookSelected() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "No book playing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Open a book from the Library to start listening.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlayerContent(
    state: PlayerUiState,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    val book = state.book ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = File(book.coverPath),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        state.currentChapterTitle?.let { title ->
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.chapters.isNotEmpty()) {
            Text(
                "Chapter ${state.currentChapterIndex.coerceAtLeast(0) + 1} of ${state.chapters.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
        Scrubber(
            positionInChapterMs = state.positionInChapterMs,
            chapterDurationMs = state.currentChapterDurationMs,
            chapterStartMs = state.currentChapterStartMs,
            onSeek = onSeek
        )
        Spacer(Modifier.height(8.dp))
        TransportRow(
            isPlaying = state.isPlaying,
            isBuffering = isBuffering,
            canChangeChapter = state.chapters.isNotEmpty(),
            onPlayPause = onPlayPause,
            onSkipBack = onSkipBack,
            onSkipForward = onSkipForward,
            onPrevChapter = onPrevChapter,
            onNextChapter = onNextChapter
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Scrubber(
    positionInChapterMs: Long,
    chapterDurationMs: Long,
    chapterStartMs: Long,
    onSeek: (Long) -> Unit
) {
    // Slider works in chapter-local space. Drag value is also chapter-local.
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    val maxValue = chapterDurationMs.toFloat().coerceAtLeast(1f)
    val sliderValue = (draggingValue ?: positionInChapterMs.toFloat()).coerceIn(0f, maxValue)

    Slider(
        value = sliderValue,
        valueRange = 0f..maxValue,
        onValueChange = { draggingValue = it },
        onValueChangeFinished = {
            draggingValue?.let { onSeek(chapterStartMs + it.toLong()) }
            draggingValue = null
        }
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.labelMedium)
        Text(
            "-${formatTime((chapterDurationMs - sliderValue.toLong()).coerceAtLeast(0L))}",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    canChangeChapter: Boolean,
    onPlayPause: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevChapter, enabled = canChangeChapter) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter")
        }
        IconButton(onClick = onSkipBack) {
            Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
        }
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors()
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = androidx.compose.material3.LocalContentColor.current
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        IconButton(onClick = onSkipForward) {
            Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds")
        }
        IconButton(onClick = onNextChapter, enabled = canChangeChapter) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter")
        }
    }
}

internal fun formatTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    val hours = TimeUnit.MILLISECONDS.toHours(clamped)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(clamped) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(clamped) % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
