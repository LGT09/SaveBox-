package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.model.AppThemeMode

private val SaveBoxDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianNight,
    primaryContainer = Color(0xFF004D59),
    onPrimaryContainer = Color(0xFF9DF0FF),
    secondary = AuroraViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D1E5F),
    onSecondaryContainer = Color(0xFFE4D8FF),
    tertiary = EmeraldMint,
    onTertiary = ObsidianNight,
    tertiaryContainer = Color(0xFF003D22),
    onTertiaryContainer = Color(0xFF99FFD0),
    background = ObsidianNight,
    onBackground = Color(0xFFF2F6FF),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF2F6FF),
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = Color(0xFFAAB8D6),
    outline = Color(0xFF2D3C5E),
    outlineVariant = Color(0xFF1D2842),
    error = CoralAlert,
    onError = Color.White
)

private val SaveBoxLightColorScheme = lightColorScheme(
    primary = DeepSapphire,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF6FF),
    onPrimaryContainer = Color(0xFF003640),
    secondary = DeepIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBE2FF),
    onSecondaryContainer = Color(0xFF240961),
    tertiary = Color(0xFF00875A),
    onTertiary = Color.White,
    background = PearlMist,
    onBackground = InkDark,
    surface = PearlSurface,
    onSurface = InkDark,
    surfaceVariant = PearlElevated,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFBFCDE2),
    outlineVariant = Color(0xFFDDE6F2),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

val SaveBoxShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SaveBoxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SaveBoxDarkColorScheme
        else -> SaveBoxLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SaveBoxTypography,
        shapes = SaveBoxShapes,
        content = content
    )
}
