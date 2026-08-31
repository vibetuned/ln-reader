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
    primary = BrandPurple,
    onPrimary = Color(0xFF12082B),
    primaryContainer = BrandPurpleDeep,
    onPrimaryContainer = Color(0xFFEADDFF),
    inversePrimary = Color(0xFF6844C9),

    secondary = BrandTeal,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF372C5E),
    onSecondaryContainer = Color(0xFFE6DCFF),

    tertiary = BrandTeal,
    onTertiary = Color(0xFF00201C),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFFB8FFF5),

    background = BrandBg,
    onBackground = BrandText,
    surface = BrandBg,
    onSurface = BrandText,
    surfaceVariant = BrandSurface2,
    onSurfaceVariant = BrandTextDim,

    surfaceDim = BrandBg,
    surfaceBright = Color(0xFF322B42),
    surfaceContainerLowest = Color(0xFF07050B),
    surfaceContainerLow = BrandBgSoft,
    surfaceContainer = BrandSurface,
    surfaceContainerHigh = BrandSurface2,
    surfaceContainerHighest = BrandBorder,

    inverseSurface = BrandText,
    inverseOnSurface = BrandBgSoft,

    outline = BrandTextFaint,
    outlineVariant = BrandBorder,
    scrim = Color(0xFF000000),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6743C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21005D),
    inversePrimary = BrandPurple,

    secondary = Color(0xFF006A60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DEF8),
    onSecondaryContainer = Color(0xFF1F1A2C),

    tertiary = Color(0xFF006A60),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9FF2E6),
    onTertiaryContainer = Color(0xFF00201C),

    background = Color(0xFFFDFAFF),
    onBackground = BrandSurface,
    surface = Color(0xFFFDFAFF),
    onSurface = BrandSurface,
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF494455),

    surfaceDim = Color(0xFFDED8E4),
    surfaceBright = Color(0xFFFDFAFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2FC),
    surfaceContainer = Color(0xFFF2ECF7),
    surfaceContainerHigh = Color(0xFFECE6F1),
    surfaceContainerHighest = Color(0xFFE6E0EB),

    inverseSurface = BrandSurface,
    inverseOnSurface = Color(0xFFF5EFFA),

    outline = Color(0xFF7A7488),
    outlineVariant = Color(0xFFCBC4D4),
    scrim = Color(0xFF000000)
)

@Composable
fun LnReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
