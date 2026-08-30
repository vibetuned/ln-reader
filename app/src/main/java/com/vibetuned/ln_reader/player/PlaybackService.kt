package com.vibetuned.ln_reader.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vibetuned.ln_reader.LnReaderApplication
import com.vibetuned.ln_reader.R
import com.vibetuned.ln_reader.data.repo.PositionRepository
import com.vibetuned.ln_reader.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Notification buttons are backed by custom session commands rather than player commands:
// DefaultMediaNotificationProvider only renders custom-layout buttons whose sessionCommand is set,
// and silently drops player-command buttons. So the seeks are dispatched by hand in onCustomCommand.
private const val ACTION_SKIP_BACK = "com.vibetuned.ln_reader.action.SKIP_BACK"
private const val ACTION_SKIP_FORWARD = "com.vibetuned.ln_reader.action.SKIP_FORWARD"
private const val ACTION_SLEEP_EOC = "com.vibetuned.ln_reader.action.SLEEP_EOC"

/**
 * Foreground media session host. The player itself lives in here so playback survives the
 * activity going away; UI talks to it via a MediaController.
 *
 * The media notification carries a custom layout — skip-back 10s, skip-forward 30s, and an
 * end-of-chapter sleep button — wired through [LnReaderSessionCallback].
 *
 * Position is throttled-saved every [POSITION_SAVE_INTERVAL_MS] while playing, and on every
 * pause / stop, via [PositionRepository] from [AppContainer].
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var castPlayer: CastPlayer? = null
    private var castMediaServer: CastMediaServer? = null
    private var session: MediaSession? = null

    /** Whichever player the session is currently driving — local ExoPlayer or the cast player. */
    private val activePlayer: Player
        get() = session?.player ?: player

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
            // Make the seek-back/seek-forward player commands match the notification buttons.
            .setSeekBackIncrementMs(SKIP_BACK_MS)
            .setSeekForwardIncrementMs(SKIP_FORWARD_MS)
            .build()

        val positionSaverListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startPositionSaver(container.positionRepository)
                else {
                    stopPositionSaver()
                    saveCurrentPosition(container.positionRepository)
                }
            }
        }
        player.addListener(positionSaverListener)

        session = MediaSession.Builder(this, player)
            .setCallback(LnReaderSessionCallback(container, buildCustomLayout()))
            .build()

        setUpCast(container, positionSaverListener)
    }

    /**
     * Google Cast: when a cast session starts, the media server is brought up (the receiver
     * fetches the m4b from it over the LAN) and the session is handed the [CastPlayer]; when it
     * ends, playback moves back to the local player — paused, so the phone doesn't suddenly start
     * talking — and the server shuts down. Skipped entirely on devices without Play Services.
     */
    private fun setUpCast(container: AppContainer, positionSaverListener: Player.Listener) {
        val castContext = CastSupport.castContextOrNull(this) ?: return
        val server = CastMediaServer(this, container.bookRepository)
        castMediaServer = server
        val cast = CastPlayer(
            castContext,
            CastMediaItemConverter(server),
            SKIP_BACK_MS,
            SKIP_FORWARD_MS
        )
        cast.addListener(positionSaverListener)
        cast.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                runCatching { server.startIfNeeded() }
                switchSessionPlayer(cast, pauseAfterSwitch = false)
            }

            override fun onCastSessionUnavailable() {
                switchSessionPlayer(player, pauseAfterSwitch = true)
                server.stopIfRunning()
            }
        })
        castPlayer = cast
    }

    /** Moves the current item, position, and play state onto [newPlayer] and rebinds the session. */
    private fun switchSessionPlayer(newPlayer: Player, pauseAfterSwitch: Boolean) {
        val session = session ?: return
        val oldPlayer = session.player
        if (oldPlayer === newPlayer) return
        val item = oldPlayer.currentMediaItem
        val position = oldPlayer.currentPosition
        val playWhenReady = oldPlayer.playWhenReady && !pauseAfterSwitch
        oldPlayer.stop()
        oldPlayer.clearMediaItems()
        if (item != null) {
            newPlayer.setMediaItem(item, position)
            newPlayer.playWhenReady = playWhenReady
            newPlayer.prepare()
        }
        session.player = newPlayer
    }

    /**
     * Notification buttons. All three are custom session-command buttons — the only kind
     * [androidx.media3.session.DefaultMediaNotificationProvider] renders from a custom layout. The
     * predefined `ICON_SKIP_*` constants auto-resolve to Media3's bundled drawables, so the skip
     * buttons get proper icons without shipping our own. Commands are handled in
     * [LnReaderSessionCallback.onCustomCommand].
     */
    private fun buildCustomLayout(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setSessionCommand(SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY))
            .setDisplayName("Back 10 seconds")
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setSessionCommand(SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY))
            .setDisplayName("Forward 30 seconds")
            .build(),
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_sleep_timer)
            .setSessionCommand(SessionCommand(ACTION_SLEEP_EOC, Bundle.EMPTY))
            .setDisplayName("Sleep at end of chapter")
            .build()
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // If the user swipes the app away while paused, tear down. If playing, keep going.
        if (!activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopPositionSaver()
        castPlayer?.setSessionAvailabilityListener(null)
        castMediaServer?.stopIfRunning()
        session?.run {
            release()
            session = null
        }
        castPlayer?.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPositionSaver(positionRepository: PositionRepository) {
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

    private fun saveCurrentPosition(positionRepository: PositionRepository) {
        val bookId = activePlayer.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        val pos = activePlayer.currentPosition
        if (pos < 0) return
        serviceScope.launch { positionRepository.save(bookId, pos) }
    }

    companion object {
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val SKIP_BACK_MS = 10_000L
        private const val SKIP_FORWARD_MS = 30_000L
    }
}

/**
 * Wires up the notification's custom layout: grants the buttons' custom session commands on
 * connect (otherwise they render disabled), pushes the layout to each controller in
 * [onPostConnect], and performs the seeks / arms the [SleepTimerController] in [onCustomCommand].
 */
@OptIn(UnstableApi::class)
private class LnReaderSessionCallback(
    private val container: AppContainer,
    private val customLayout: List<CommandButton>
) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        // Grant the custom commands behind our notification buttons; without these a controller
        // (including the notification controller) renders the buttons disabled.
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            .buildUpon()
            .apply { customLayout.forEach { button -> button.sessionCommand?.let { add(it) } } }
            .build()
        // Drop the previous/next commands so DefaultMediaNotificationProvider doesn't render its
        // own skip-to-item buttons — for a single-file audiobook they're useless and they'd
        // displace our skip buttons. Play/pause and the seek bar stay available.
        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            .buildUpon()
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    /** Push the custom layout once a controller (including the notification controller) connects. */
    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        session.setCustomLayout(controller, customLayout)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            // seekBack/seekForward use the increments configured on the ExoPlayer (10s / 30s).
            ACTION_SKIP_BACK -> session.player.seekBack()
            ACTION_SKIP_FORWARD -> session.player.seekForward()
            ACTION_SLEEP_EOC -> container.sleepTimerController.start(SleepTimerConfig.EndOfChapter())
            else -> return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            )
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}
