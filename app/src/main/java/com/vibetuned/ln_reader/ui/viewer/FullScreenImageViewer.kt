package com.vibetuned.ln_reader.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.BitmapFactory
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.data.model.EmbeddedImage
import com.vibetuned.ln_reader.data.prefs.ViewerPreferences
import com.vibetuned.ln_reader.ui.common.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun FullScreenImageViewer(
    images: List<EmbeddedImage>,
    startIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex,
        pageCount = { images.size }
    )

    // The page background is auto-picked by sampling the current image's alpha channel:
    // mostly-transparent images (line art on a transparent background) get a white backdrop
    // so dark strokes stay visible. The user can override per-book via the toggle and we
    // persist that choice so the same book reopens with the same backdrop next time.
    val bookId = images.firstOrNull()?.bookId
    val viewerPreferences = appContainer().viewerPreferences
    val scope = rememberCoroutineScope()

    val savedChoice: String? by produceState<String?>(initialValue = null, bookId) {
        if (bookId == null) {
            value = null
        } else {
            viewerPreferences.backgroundFor(bookId).collect { value = it }
        }
    }

    val currentFile = remember(pagerState.currentPage, images) {
        images.getOrNull(pagerState.currentPage)?.let { File(it.cachePath) }
    }
    val autoLight: Boolean by produceState(initialValue = false, currentFile) {
        val f = currentFile
        value = if (f == null) false
        else withContext(Dispatchers.IO) {
            runCatching { isMostlyTransparent(f) }.getOrDefault(false)
        }
    }

    val lightBackground = when (savedChoice) {
        ViewerPreferences.CHOICE_LIGHT -> true
        ViewerPreferences.CHOICE_DARK -> false
        else -> autoLight
    }
    val backgroundColor = if (lightBackground) Color.White else Color.Black
    val foregroundColor = if (lightBackground) Color.Black else Color.White

    val onToggleBackground: () -> Unit = {
        val next = if (lightBackground) ViewerPreferences.CHOICE_DARK
        else ViewerPreferences.CHOICE_LIGHT
        if (bookId != null) {
            scope.launch { viewerPreferences.setBackgroundFor(bookId, next) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomableImage(
                    file = File(images[page].cachePath),
                    pageKey = page,
                    isVisible = page == pagerState.currentPage
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = foregroundColor
                    )
                }
                Text(
                    "${pagerState.currentPage + 1} / ${images.size}",
                    color = foregroundColor,
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = onToggleBackground) {
                    Icon(
                        imageVector = Icons.Filled.InvertColors,
                        contentDescription = "Toggle background",
                        tint = foregroundColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    pageKey: Int,
    isVisible: Boolean
) {
    var scale by remember(pageKey) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageKey) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageKey) { mutableFloatStateOf(0f) }

    LaunchedEffect(isVisible) {
        if (!isVisible) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
            .pointerInput(Unit) {
                // Consume only what's truly a transform gesture, so single-finger swipes
                // fall through to the surrounding HorizontalPager when the image is at rest.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.count { it.pressed }
                        when {
                            activePointers >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                if (newScale > 1f) {
                                    offsetX += panChange.x
                                    offsetY += panChange.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                scale = newScale
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                            activePointers == 1 && scale > 1f -> {
                                val change = event.changes.first { it.pressed }
                                val pan = change.positionChange()
                                if (pan.x != 0f || pan.y != 0f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    change.consume()
                                }
                            }
                            // activePointers == 1 && scale == 1f — let pager handle it.
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    )
}

/**
 * Cheap-enough heuristic to decide whether [file] is a "needs a light backdrop" image: decode
 * downsampled, then sample on a roughly 16×16 grid and count pixels whose alpha is below the
 * midpoint. More than 20% transparent → likely line art / illustration with transparency.
 */
private fun isMostlyTransparent(file: File): Boolean {
    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return false
    try {
        if (!bitmap.hasAlpha()) return false
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return false
        val step = max(1, min(w, h) / 16)
        var transparent = 0
        var sampled = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 128) transparent++
                sampled++
                x += step
            }
            y += step
        }
        if (sampled == 0) return false
        return transparent.toFloat() / sampled > 0.20f
    } finally {
        bitmap.recycle()
    }
}
