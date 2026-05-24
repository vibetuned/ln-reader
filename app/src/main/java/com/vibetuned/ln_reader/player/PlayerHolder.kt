package com.vibetuned.ln_reader.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vibetuned.ln_reader.data.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Process-scoped holder for a [MediaController] bound to [PlaybackService]. UI code observes
 * [controller] as a StateFlow — null while connecting / disconnected, non-null once the session
 * is bound. Use [connect] from the first screen that needs playback; [release] when nothing
 * else is going to talk to it (we don't bother in phase 4).
 */
class PlayerHolder(private val context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener(
            {
                _controller.value = f.get()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _controller.value = null
    }

    /**
     * Sets [book] as the current item, seeks to [startPositionMs], and starts buffering.
     * Does NOT auto-play — caller decides via [MediaController.play].
     */
    fun loadBook(book: Book, startPositionMs: Long) {
        val controller = _controller.value ?: return
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(book.author)
            .setAlbumTitle(book.album)
        book.coverPath?.let { metadataBuilder.setArtworkUri(File(it).toUri()) }
        val mediaItem = MediaItem.Builder()
            .setMediaId(book.id)
            .setUri(book.uri.toUri())
            .setMediaMetadata(metadataBuilder.build())
            .build()
        controller.setMediaItem(mediaItem, startPositionMs)
        controller.prepare()
    }
}
