package com.vibetuned.ln_reader.player

import android.content.Context
import androidx.media3.session.MediaController
import com.vibetuned.ln_reader.data.model.Chapter
import com.vibetuned.ln_reader.data.repo.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped sleep timer.
 *
 * - Time mode freezes when playback is paused (counts elapsed *play* time, not wall time).
 * - Chapter mode keys off [MediaController.getCurrentPosition], which doesn't advance while paused.
 * - When the timer fires we pause playback, post a notification with a Postpone action, and start
 *   listening for a phone shake. Either trigger restarts the timer with the same config. The user
 *   can also Dismiss to clear the expired state without restarting.
 */
class SleepTimerController(
    context: Context,
    private val playerHolder: PlayerHolder,
    private val bookRepository: BookRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<SleepTimerState?>(null)
    val state: StateFlow<SleepTimerState?> = _state.asStateFlow()

    /**
     * Non-null while the timer has just fired and is waiting for the user to either postpone
     * (restart with this config), dismiss, or shake. Cleared the moment any of those happens.
     */
    private val _expiredConfig = MutableStateFlow<SleepTimerConfig?>(null)
    val expiredConfig: StateFlow<SleepTimerConfig?> = _expiredConfig.asStateFlow()

    private val notifier = SleepTimerNotifier(context.applicationContext)
    private val shakeDetector = ShakeDetector(context.applicationContext) { postpone() }

    private var job: Job? = null

    fun start(config: SleepTimerConfig): Boolean {
        val controller = playerHolder.controller.value ?: return false
        cancel()
        dismissExpired()
        job = scope.launch {
            when (config) {
                is SleepTimerConfig.TimeBased -> runTimeMode(controller, config)
                is SleepTimerConfig.ChapterBased -> runChapterMode(controller, config.chapterCount, config)
                is SleepTimerConfig.EndOfChapter -> runChapterMode(controller, 1, config)
            }
        }
        return true
    }

    /** Cancels an active timer but leaves [expiredConfig] untouched. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = null
        playerHolder.controller.value?.volume = 1f
    }

    /** Clears the expired state (notification + shake listener) without restarting. */
    fun dismissExpired() {
        if (_expiredConfig.value == null) return
        _expiredConfig.value = null
        notifier.cancel()
        shakeDetector.stop()
    }

    /** Restart the just-expired timer with the same config and resume playback. */
    fun postpone(): Boolean {
        val config = _expiredConfig.value ?: return false
        val controller = playerHolder.controller.value ?: return false
        dismissExpired()
        controller.play()
        return start(config)
    }

    private suspend fun runTimeMode(
        controller: MediaController,
        config: SleepTimerConfig.TimeBased
    ) {
        val totalMs = config.totalMs
        var elapsedMs = 0L
        var lastTick = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val wallDelta = now - lastTick
            lastTick = now
            if (controller.isPlaying) elapsedMs += wallDelta
            val remaining = totalMs - elapsedMs
            if (remaining <= 0) break
            _state.value = SleepTimerState.TimeRemaining(remaining, totalMs)
            applyFade(controller, remaining, config.fadeOut)
            delay(TICK_MS)
        }
        finishAndExpire(controller, config)
    }

    private suspend fun runChapterMode(
        controller: MediaController,
        chapterCount: Int,
        configForPostpone: SleepTimerConfig
    ) {
        val mediaId = controller.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() }
        val chapters = mediaId?.let { bookRepository.getDetail(it)?.chapters }.orEmpty()
        if (chapters.isEmpty()) {
            cancel()
            return
        }
        val duration = controller.duration.coerceAtLeast(0L)
        val startIdx = currentChapterIndex(chapters, controller.currentPosition)
        val targetIdx = (startIdx + chapterCount).coerceAtMost(chapters.size)
        val targetMs = chapters.getOrNull(targetIdx)?.startMs ?: duration

        while (true) {
            val pos = controller.currentPosition
            val remaining = targetMs - pos
            if (remaining <= 0) break
            val curIdx = currentChapterIndex(chapters, pos)
            val chaptersLeft = (targetIdx - curIdx).coerceAtLeast(0)
            _state.value = SleepTimerState.UntilChapterBoundary(
                msUntilStop = remaining,
                chaptersRemaining = chaptersLeft
            )
            applyFade(controller, remaining, configForPostpone.fadeOut)
            delay(TICK_MS)
        }
        finishAndExpire(controller, configForPostpone)
    }

    private fun finishAndExpire(controller: MediaController, config: SleepTimerConfig) {
        controller.pause()
        controller.volume = 1f
        _state.value = null
        job = null
        _expiredConfig.value = config
        notifier.postExpired(config)
        shakeDetector.start()
    }

    private fun applyFade(controller: MediaController, remaining: Long, fadeOut: Boolean) {
        if (!fadeOut) {
            controller.volume = 1f
            return
        }
        controller.volume =
            if (remaining >= FADE_OUT_MS) 1f
            else (remaining.toFloat() / FADE_OUT_MS.toFloat()).coerceIn(0f, 1f)
    }

    private fun currentChapterIndex(chapters: List<Chapter>, positionMs: Long): Int {
        var idx = -1
        for (i in chapters.indices) {
            if (chapters[i].startMs <= positionMs) idx = i else break
        }
        return idx
    }

    companion object {
        private const val TICK_MS = 250L
        private const val FADE_OUT_MS = 10_000L
    }
}
