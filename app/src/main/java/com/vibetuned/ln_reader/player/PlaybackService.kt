package com.vibetuned.ln_reader.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vibetuned.ln_reader.LnReaderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground media session host. The player itself lives in here so playback survives the
 * activity going away; UI talks to it via a MediaController.
 *
 * Position is throttled-saved every [POSITION_SAVE_INTERVAL_MS] while playing, and on every
 * pause / stop, via [PositionRepository] from [AppContainer].
 */
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSaveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as LnReaderApplication).container

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startPositionSaver(container.positionRepository)
                else {
                    stopPositionSaver()
                    saveCurrentPosition(container.positionRepository)
                }
            }
        })

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // If the user swipes the app away while paused, tear down. If playing, keep going.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopPositionSaver()
        session?.run {
            player.release()
            release()
            session = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPositionSaver(positionRepository: com.vibetuned.ln_reader.data.repo.PositionRepository) {
        stopPositionSaver()
        positionSaveJob = serviceScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveCurrentPosition(positionRepository)
            }
        }
    }

    private fun stopPositionSaver() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    private fun saveCurrentPosition(
        positionRepository: com.vibetuned.ln_reader.data.repo.PositionRepository
    ) {
        val bookId = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        val pos = player.currentPosition
        if (pos < 0) return
        serviceScope.launch { positionRepository.save(bookId, pos) }
    }

    companion object {
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}
