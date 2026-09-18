package com.vibetuned.ln_reader.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.vibetuned.ln_reader.player.CastSupport
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.ui.common.appContainer
import com.vibetuned.ln_reader.ui.theme.DimCaptionColor
import com.vibetuned.ln_reader.ui.theme.InactiveTrackColor
import com.vibetuned.ln_reader.ui.viewer.FullScreenImageViewer
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String? = null,
    autoPlay: Boolean = false,
    onBack: () -> Unit = {},
    onViewImages: (String) -> Unit = {},
    onOpenReader: (String) -> Unit = {}
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
    var markerImageIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(bookId) {
        if (bookId != null) viewModel.open(bookId, autoPlay)
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
                    // Visible icons: the stateful ones — Cast (connection state) and the sleep
                    // timer (tinted while armed) — plus Read, which has no other entry point on
                    // this screen. Everything else lives in the overflow menu.
                    CastButton()
                    val currentBookId = state.book?.id
                    IconButton(
                        onClick = { currentBookId?.let(onOpenReader) },
                        enabled = currentBookId != null && state.book?.hasEpub == true
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Read")
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
                            else LocalContentColor.current
                        )
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Playback speed") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Speed, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showSpeed = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chapters") },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.List,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showChapters = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Images") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                },
                                enabled = currentBookId != null,
                                onClick = {
                                    showMenu = false
                                    currentBookId?.let(onViewImages)
                                }
                            )
                        }
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
                    onNextChapter = viewModel::nextChapter,
                    onOpenChapters = { showChapters = true },
                    onMarkerClick = { marker -> markerImageIndex = marker.imageIndex }
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

    val markerIdx = markerImageIndex
    if (markerIdx != null && state.images.isNotEmpty()) {
        FullScreenImageViewer(
            images = state.images,
            startIndex = markerIdx.coerceIn(0, state.images.lastIndex),
            onDismiss = { markerImageIndex = null }
        )
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
    onNextChapter: () -> Unit,
    onOpenChapters: () -> Unit,
    onMarkerClick: (ImageMarker) -> Unit
) {
    val book = state.book ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Equal weights above and below centre the cover/title/chapter block in the space over the
        // scrubber, rather than pinning it to either end — a tall tablet has ~300dp of slack, and
        // parking all of it at one edge reads as a hole. The fixed 28dp below is a floor, so the
        // block keeps its breathing room from the scrubber once the weighted spacers collapse on a
        // short screen.
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.weight(1f))
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
        if (state.chapters.isNotEmpty()) {
            ChapterSelector(
                chapterTitle = state.currentChapterTitle,
                chapterNumberLabel =
                    "Chapter ${state.currentChapterIndex.coerceAtLeast(0) + 1} of ${state.chapters.size}",
                onClick = onOpenChapters,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Spacer(Modifier.weight(1f))
        Scrubber(
            positionInChapterMs = state.positionInChapterMs,
            chapterDurationMs = state.currentChapterDurationMs,
            chapterStartMs = state.currentChapterStartMs,
            bookDurationMs = state.durationMs,
            markers = state.imageMarkers,
            onSeek = onSeek,
            onMarkerClick = onMarkerClick
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

/**
 * Stacked, tappable chapter readout: chapter name over "Chapter X of Y" with a dropdown affordance.
 * Tapping opens the chapter-selection sheet via [onClick].
 */
@Composable
private fun ChapterSelector(
    chapterTitle: String?,
    chapterNumberLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (chapterTitle != null) {
            // Medium weight at full-strength onSurface, matching the iOS selector's
            // `.subheadline.weight(.medium)` in the primary label colour: the chapter name is the
            // heading of what you are listening to, so it outranks the "Chapter n of m" caption
            // under it rather than sharing its muted tone.
            Text(
                chapterTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                chapterNumberLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Select chapter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scrubber(
    positionInChapterMs: Long,
    chapterDurationMs: Long,
    chapterStartMs: Long,
    bookDurationMs: Long,
    markers: List<ImageMarker>,
    onSeek: (Long) -> Unit,
    onMarkerClick: (ImageMarker) -> Unit
) {
    // Slider works in chapter-local space. Drag value is also chapter-local.
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    val maxValue = chapterDurationMs.toFloat().coerceAtLeast(1f)
    val sliderValue = (draggingValue ?: positionInChapterMs.toFloat()).coerceIn(0f, maxValue)

    // Only markers that fall inside the current chapter, positioned chapter-locally.
    val chapterEnd = chapterStartMs + chapterDurationMs
    val localMarkers = markers.filter { it.positionMs in chapterStartMs..chapterEnd }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {} // reserves the marker strip height above the slider
        localMarkers.forEach { marker ->
            val frac = ((marker.positionMs - chapterStartMs).toFloat() / maxValue).coerceIn(0f, 1f)
            // Inset by the thumb radius so markers line up with the usable track.
            val x = THUMB_RADIUS + (trackWidth - THUMB_RADIUS * 2) * frac
            IconButton(
                onClick = { onMarkerClick(marker) },
                modifier = Modifier
                    .offset(x = x - 20.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Photo,
                    contentDescription = "Illustration here",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    // Material 3's expressive Slider draws a thick, gapped track with a pill thumb. The scrubber
    // reads better — and matches the iOS player — as a slim capsule with a round knob, so the
    // visuals come from the thumb/track slots while the Slider itself keeps Android's gesture
    // handling, keyboard stepping and accessibility semantics.
    Slider(
        value = sliderValue,
        valueRange = 0f..maxValue,
        onValueChange = { draggingValue = it },
        onValueChangeFinished = {
            draggingValue?.let { onSeek(chapterStartMs + it.toLong()) }
            draggingValue = null
        },
        thumb = {
            Spacer(
                modifier = Modifier
                    .size(THUMB_RADIUS * 2)
                    .shadow(2.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        },
        track = {
            // The track slot is laid out at (slider width - thumb width) and offset by half a
            // thumb, so the thumb centre sits exactly at `fraction` along it — filling the active
            // portion by the same fraction lines the two up without any manual inset.
            val fraction = (sliderValue / maxValue).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SCRUBBER_TRACK_HEIGHT)
                    .clip(CircleShape)
                    .background(InactiveTrackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    )
    // Book-absolute position follows the drag (chapter start + chapter-local slider value).
    val bookPositionMs = (chapterStartMs + sliderValue.toLong()).coerceIn(0L, bookDurationMs.coerceAtLeast(0L))
    val bookProgress = if (bookDurationMs > 0L) (bookPositionMs.toFloat() / bookDurationMs).coerceIn(0f, 1f) else 0f
    val bookRemainingMs = (bookDurationMs - bookPositionMs).coerceAtLeast(0L)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        // The bar is sized off the row so a tablet gets a generous strip while a phone — where the
        // two chapter times and the longer "left" label already eat the width — keeps a strip that
        // still fits between them.
        val stripWidth = (maxWidth * BOOK_STRIP_WIDTH_FRACTION)
            .coerceIn(BOOK_STRIP_MIN_WIDTH, BOOK_STRIP_MAX_WIDTH)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(sliderValue.toLong()), style = MaterialTheme.typography.labelMedium)
            // Whole-book context — how far through the book we are, and the time left in it. Sized to
            // its content and floated between equal spacers so it sits centred between the chapter
            // times rather than stretching to fill, matching the iOS player.
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { bookProgress },
                    // Plain capsule, same track as the scrubber above it: no stop dot and no
                    // expressive gap, which would otherwise read as a third, unrelated element.
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    trackColor = InactiveTrackColor,
                    modifier = Modifier.width(stripWidth).height(SCRUBBER_TRACK_HEIGHT)
                )
                Text(
                    "${formatHoursMinutes(bookRemainingMs)} left",
                    style = MaterialTheme.typography.labelMedium,
                    color = DimCaptionColor,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "-${formatTime((chapterDurationMs - sliderValue.toLong()).coerceAtLeast(0L))}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private val THUMB_RADIUS = 10.dp
private val SCRUBBER_TRACK_HEIGHT = 4.dp
private const val BOOK_STRIP_WIDTH_FRACTION = 0.32f
private val BOOK_STRIP_MIN_WIDTH = 110.dp
private val BOOK_STRIP_MAX_WIDTH = 240.dp

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

/** Whole-book remaining time as "Xh Ym" — minutes rounded up so it only reads 0h 0m at the end. */
private fun formatHoursMinutes(ms: Long): String {
    val totalMinutes = (ms.coerceAtLeast(0L) + 59_999L) / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

internal fun formatTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0)
    val hours = TimeUnit.MILLISECONDS.toHours(clamped)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(clamped) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(clamped) % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/**
 * Google Cast button (device picker + connection state). Renders nothing on devices without Play
 * Services. A classic View wrapped for Compose. Both the button and the device-chooser dialog it
 * opens resolve AppCompat theme attributes from the *activity* — which is why Theme.Lnreader has
 * an AppCompat parent and MainActivity is a FragmentActivity (the chooser is a DialogFragment).
 */
@Composable
private fun CastButton() {
    val context = LocalContext.current
    val castAvailable = remember { CastSupport.castContextOrNull(context) != null }
    if (!castAvailable) return
    AndroidView(
        factory = { ctx ->
            MediaRouteButton(ctx).also { button ->
                CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, button)
            }
        },
        modifier = Modifier.size(48.dp)
    )
}
