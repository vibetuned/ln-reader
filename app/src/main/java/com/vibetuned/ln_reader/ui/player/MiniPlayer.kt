package com.vibetuned.ln_reader.ui.player

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.ui.common.appContainer
import kotlinx.coroutines.delay

/** Lightweight snapshot of the active playback session, read straight off the [MediaController]. */
data class NowPlaying(
    val bookId: String,
    val title: String,
    val subtitle: String?,
    val artworkUri: Uri?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

/**
 * Reduced playback bar driven by the process-scoped [MediaController]. Renders nothing when no book
 * is loaded, so callers can place it unconditionally. Tapping the row (outside the transport
 * buttons) opens the full player for the current book via [onExpand]. When [onOpenReader] is
 * non-null and the current book has an attached EPUB, a Read button opens the reader; callers pass
 * null while already in the reader to hide it.
 */
@Composable
fun MiniPlayerBar(
    onExpand: (bookId: String) -> Unit,
    onOpenReader: ((bookId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val container = appContainer()
    val controller by container.playerHolder.controller.collectAsStateWithLifecycle()
    val books by container.bookRepository.books().collectAsStateWithLifecycle(initialValue = emptyList())
    val nowPlaying = rememberNowPlaying(controller) ?: return
    val c = controller ?: return
    val bookHasEpub = onOpenReader != null && books.any { it.id == nowPlaying.bookId && it.hasEpub }

    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column {
            val fraction = if (nowPlaying.durationMs > 0)
                (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand(nowPlaying.bookId) }
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nowPlaying.artworkUri != null) {
                    AsyncImage(
                        model = nowPlaying.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        nowPlaying.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!nowPlaying.subtitle.isNullOrBlank()) {
                        Text(
                            nowPlaying.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val openReader = onOpenReader
                if (openReader != null && bookHasEpub) {
                    IconButton(onClick = { openReader(nowPlaying.bookId) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = "Read"
                        )
                    }
                }
                IconButton(onClick = { c.seekBack() }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
                }
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (nowPlaying.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                    } else {
                        IconButton(onClick = { if (c.isPlaying) c.pause() else c.play() }) {
                            Icon(
                                imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause
                                else Icons.Filled.PlayArrow,
                                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play"
                            )
                        }
                    }
                }
                IconButton(onClick = { c.seekForward() }) {
                    Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds")
                }
            }
        }
    }
}

/**
 * Mirrors the controller's now-playing state into Compose: refreshes on every player event and
 * polls once a second so the position progress advances during playback. Listener is removed when
 * the bar leaves composition. Null while nothing is loaded.
 */
@Composable
private fun rememberNowPlaying(controller: MediaController?): NowPlaying? {
    val state by produceState<NowPlaying?>(initialValue = controller?.toNowPlaying(), controller) {
        val c = controller
        if (c == null) {
            value = null
            return@produceState
        }
        value = c.toNowPlaying()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                value = c.toNowPlaying()
            }
        }
        c.addListener(listener)
        try {
            while (true) {
                delay(1000)
                value = c.toNowPlaying()
            }
        } finally {
            c.removeListener(listener)
        }
    }
    return state
}

private fun MediaController.toNowPlaying(): NowPlaying? {
    val item = currentMediaItem ?: return null
    val id = item.mediaId.takeIf { it.isNotEmpty() } ?: return null
    val md = item.mediaMetadata
    return NowPlaying(
        bookId = id,
        title = md.title?.toString().orEmpty(),
        subtitle = md.artist?.toString(),
        artworkUri = md.artworkUri,
        isPlaying = isPlaying,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = duration.takeIf { it > 0 } ?: 0L
    )
}
