package com.vibetuned.ln_reader.player

import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import com.google.android.gms.cast.MediaMetadata as CastMetadata

/**
 * Translates the app's media items for the Cast receiver.
 *
 * The app keeps using local file/content URIs everywhere — this converter swaps in the
 * [CastMediaServer] HTTP URL only in the outgoing [MediaInfo], while the round-trip payload
 * (customData, built by [DefaultMediaItemConverter]) still carries the *original* item. So when
 * the cast session ends and the timeline item comes back through [toMediaItem], playback hands the
 * local URI straight back to ExoPlayer — no un-rewriting, and no dependence on the server that is
 * about to shut down.
 */
@OptIn(UnstableApi::class)
class CastMediaItemConverter(private val server: CastMediaServer) : MediaItemConverter {

    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        // DefaultMediaItemConverter requires a MIME type; the local item never carries one.
        val localItem = mediaItem.buildUpon().setMimeType(MimeTypes.AUDIO_MP4).build()
        val defaultQueueItem = default.toMediaQueueItem(localItem)
        // No reachable server (no Wi-Fi IP, failed start): fall back to the default conversion.
        // The receiver won't be able to fetch the local URI, but the app stays healthy.
        val streamUrl = server.bookUrl(mediaItem.mediaId) ?: return defaultQueueItem

        val castMetadata = CastMetadata(CastMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            mediaItem.mediaMetadata.title?.let { putString(CastMetadata.KEY_TITLE, it.toString()) }
            mediaItem.mediaMetadata.artist?.let { putString(CastMetadata.KEY_ARTIST, it.toString()) }
            server.coverUrl(mediaItem.mediaId)?.let { addImage(WebImage(it)) }
        }
        val mediaInfo = MediaInfo.Builder(streamUrl.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(MimeTypes.AUDIO_MP4)
            .setMetadata(castMetadata)
            .setCustomData(defaultQueueItem.media?.customData)
            .build()
        return MediaQueueItem.Builder(mediaInfo).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem =
        default.toMediaItem(mediaQueueItem)
}
