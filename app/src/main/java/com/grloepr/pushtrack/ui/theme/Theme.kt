package com.grloepr.pushtrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * OmniPose Fit ships a single, deliberately-branded dark experience: camera-first
 * fitness UIs live on top of a live video feed, so a consistent deep-space scheme
 * with vibrant accent hues beats system dynamic color here.
 */
private val OmniPoseDarkScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = AbyssBlack,
    primaryContainer = CyanDim,
    onPrimaryContainer = TextBright,
    secondary = NeonViolet,
    onSecondary = TextBright,
    secondaryContainer = VioletDim,
    onSecondaryContainer = TextBright,
    tertiary = VoltLime,
    onTertiary = AbyssBlack,
    tertiaryContainer = Color(0xFF3A4D14),
    onTertiaryContainer = TextBright,
    error = CriticalRed,
    onError = TextBright,
    background = DeepSpace,
    onBackground = TextBright,
    surface = DeepSpace,
    onSurface = TextBright,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextMuted,
    surfaceContainer = SurfaceRaised,
    surfaceContainerHigh = SurfaceHigh,
    outline = OutlineSteel,
    outlineVariant = Color(0xFF1D2A45),
    scrim = AbyssBlack,
)

@Composable
fun PushTrackTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OmniPoseDarkScheme,
        typography = Typography,
        content = content
    )
}
