package com.vibetuned.ln_reader.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibetuned.ln_reader.player.SleepTimerState
import com.vibetuned.ln_reader.ui.theme.ForestLight

/**
 * The armed sleep timer, as a band across the bottom of the cover: until now the countdown was
 * only visible in Settings and in the notification shade, never on the screen you are actually
 * listening from. Tapping it opens the timer sheet, to change or cancel it.
 *
 * Fills [modifier]'s bounds and pins itself to their bottom edge, sliding in when a timer is armed
 * and out when it stops. The slide is clipped to those bounds, so the band rises out of the
 * cover's own edge rather than drifting in from below it.
 */
@Composable
fun SleepTimerRibbonSlot(
    timer: SleepTimerState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The exit animation runs after the timer is gone; keep showing what it last said while the
    // band slides away, rather than emptying it first.
    val last = remember { mutableStateOf<SleepTimerState?>(null) }
    SideEffect { if (timer != null) last.value = timer }

    Box(modifier = modifier.clipToBounds(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = timer != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            (timer ?: last.value)?.let { SleepTimerRibbon(it, onClick, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun SleepTimerRibbon(timer: SleepTimerState, onClick: () -> Unit, modifier: Modifier) {
    val label = sleepTimerRibbonLabel(
        msUntilStop = timer.msUntilStop,
        chaptersLeft = (timer as? SleepTimerState.UntilChapterBoundary)?.chaptersRemaining
    )
    Row(
        modifier = modifier
            // A dark scrim in either theme: it sits on cover art, not on the app's own surface.
            .background(RIBBON_SCRIM)
            .clickable(onClickLabel = "Change the sleep timer", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Sleep timer, $label" }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Bedtime,
            contentDescription = null,
            // The dark scheme's primary whatever the theme: the light scheme's is a deep green that
            // all but disappears on the scrim.
            tint = ForestLight,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            // Tabular figures, so the countdown doesn't shuffle sideways as its digits change.
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * "29:41 left" for a time-based timer; for a chapter-based one, which chapter it stops at:
 * "3:12 left · end of chapter" when the current chapter is the last, "12:30 left · 2 chapters"
 * otherwise. Hours appear only once there is at least one: "1:30:00 left".
 *
 * Mirrors `SleepTimerLabel.ribbon` in the iOS app, so the two read the same.
 */
internal fun sleepTimerRibbonLabel(msUntilStop: Long, chaptersLeft: Int?): String {
    // Round up, like the Settings countdown: a timer with 0.4 s to go still reads 0:01.
    val total = (msUntilStop.coerceAtLeast(0L) + 999) / 1000
    val countdown = if (total >= 3600) {
        "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    } else {
        "%d:%02d".format(total / 60, total % 60)
    }
    return when {
        chaptersLeft == null -> "$countdown left"
        chaptersLeft <= 1 -> "$countdown left · end of chapter"
        else -> "$countdown left · $chaptersLeft chapters"
    }
}

private val RIBBON_SCRIM = Color.Black.copy(alpha = 0.62f)
