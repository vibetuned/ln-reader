package com.vibetuned.ln_reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ForestLight,
    onPrimary = ForestDark,
    primaryContainer = ForestDeep,
    onPrimaryContainer = ForestPale,
    inversePrimary = ForestMid,

    secondary = SageLight,
    onSecondary = SageDark,
    secondaryContainer = SageContainer,
    onSecondaryContainer = SageOnContainer,

    tertiary = AmberLight,
    onTertiary = AmberDark,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,

    background = SlateBase,
    onBackground = ParchmentText,
    surface = SlateBase,
    onSurface = ParchmentText,
    surfaceVariant = SlateHighest,
    onSurfaceVariant = SageText,

    surfaceDim = SlateBase,
    surfaceBright = SlateBright,
    surfaceContainerLowest = SlateLowest,
    surfaceContainerLow = SlateLow,
    surfaceContainer = SlateContainer,
    surfaceContainerHigh = SlateHigh,
    surfaceContainerHighest = SlateHighest,

    inverseSurface = ParchmentText,
    inverseOnSurface = Color(0xFF2F3034),

    outline = OutlineSage,
    outlineVariant = OutlineSageDim,
    scrim = Color(0xFF000000),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestOnLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = ForestContainerLight,
    onPrimaryContainer = ForestOnContainerLight,
    inversePrimary = ForestLight,

    secondary = SageOnLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SageContainerLight,
    onSecondaryContainer = SageOnContainerLight,

    tertiary = AmberOnLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,

    background = PaperBright,
    onBackground = InkText,
    surface = PaperBright,
    onSurface = InkText,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = InkVariant,

    surfaceDim = PaperDim,
    surfaceBright = PaperBright,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = PaperContainerLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHighest,

    inverseSurface = Color(0xFF2E312D),
    inverseOnSurface = Color(0xFFEFF2EC),

    outline = OutlineInk,
    outlineVariant = OutlineInkDim,
    scrim = Color(0xFF000000)
)

/**
 * The unfilled portion of sliders and progress bars.
 *
 * Not a Material colour role: visual-design.md calls for a seek-slider track a shade softer than
 * any of the surface containers, so the sage fill carries the eye rather than the empty track.
 * Resolved against the active scheme so the light theme gets a light track rather than a dark one.
 */
val InactiveTrackColor: Color
    @Composable
    get() = if (isSystemInDarkTheme()) SlateOverlay else InkSurfaceVariant

/**
 * Deliberately recessive caption text, for labels that should sit well below the figures they
 * annotate — the whole-book "left" label under the scrubber. Roughly 4:1 against the surface in
 * either theme: a watermark, but still legible at a glance.
 */
val DimCaptionColor: Color
    @Composable
    get() = if (isSystemInDarkTheme()) CaptionDim else CaptionDimLight

/**
 * Material 3 with the Athenaeum brand scheme.
 *
 * [dynamicColor] defaults to false: the palette is the shared identity across the Android app, the
 * iOS app, the site and the store listings, and Material You wallpaper extraction would replace it
 * on Android 12+. Everything else — component shapes, motion, typography scale, edge-to-edge
 * chrome — stays stock Material 3, so the app still reads as native Android.
 */
@Composable
fun LnReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
