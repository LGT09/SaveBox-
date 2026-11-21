package com.traxxiontech.savebox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.traxxiontech.savebox.model.AppThemeMode
import com.traxxiontech.savebox.model.AppThemePalette

@Immutable
data class SaveBoxGlassTokens(
    val glassStart: Color,
    val glassEnd: Color,
    val borderHighlight: Color,
    val borderFade: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val backdropGradientTop: Color,
    val backdropGradientBottom: Color
)

val LocalSaveBoxGlassTokens = staticCompositionLocalOf {
    SaveBoxGlassTokens(
        glassStart = Color(0xB818233E),
        glassEnd = Color(0x9910182C),
        borderHighlight = GlassBorderLight,
        borderFade = GlassBorderDark,
        glowPrimary = ElectricCyan,
        glowSecondary = AuroraViolet,
        backdropGradientTop = ObsidianNight,
        backdropGradientBottom = ObsidianSurface
    )
}

private fun darkSchemeFor(palette: AppThemePalette): ColorScheme = when (palette) {
    AppThemePalette.OBSIDIAN_CYAN -> darkColorScheme(
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

    AppThemePalette.AURORA_VIOLET -> darkColorScheme(
        primary = AuroraVioletSoft,
        onPrimary = VioletNight,
        primaryContainer = Color(0xFF3E1F7A),
        onPrimaryContainer = Color(0xFFEDE0FF),
        secondary = MagentaGlow,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF561869),
        onSecondaryContainer = Color(0xFFFFD6FF),
        tertiary = ElectricCyan,
        onTertiary = VioletNight,
        background = VioletNight,
        onBackground = Color(0xFFF6F2FF),
        surface = VioletSurface,
        onSurface = Color(0xFFF6F2FF),
        surfaceVariant = Color(0xFF1E1642),
        onSurfaceVariant = Color(0xFFB9B0DF),
        outline = Color(0xFF3F2F73),
        outlineVariant = Color(0xFF2A1E52),
        error = CoralAlert,
        onError = Color.White
    )

    AppThemePalette.EMERALD_NEON -> darkColorScheme(
        primary = EmeraldMint,
        onPrimary = EmeraldBg,
        primaryContainer = Color(0xFF004D29),
        onPrimaryContainer = Color(0xFFB3FFD8),
        secondary = TealSpark,
        onSecondary = EmeraldBg,
        secondaryContainer = Color(0xFF00473C),
        onSecondaryContainer = Color(0xFFA7FFEB),
        tertiary = ElectricCyan,
        onTertiary = EmeraldBg,
        background = EmeraldBg,
        onBackground = Color(0xFFEFFFF6),
        surface = EmeraldSurface,
        onSurface = Color(0xFFEFFFF6),
        surfaceVariant = Color(0xFF123025),
        onSurfaceVariant = Color(0xFF9AC9B5),
        outline = Color(0xFF214E3D),
        outlineVariant = Color(0xFF15382B),
        error = CoralAlert,
        onError = Color.White
    )

    AppThemePalette.MIDNIGHT_ROSE -> darkColorScheme(
        primary = RoseNeon,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF69123B),
        onPrimaryContainer = Color(0xFFFFD8E7),
        secondary = AuroraVioletSoft,
        onSecondary = RoseDeepBg,
        secondaryContainer = Color(0xFF3B1B59),
        onSecondaryContainer = Color(0xFFEAD8FF),
        tertiary = ElectricCyan,
        onTertiary = RoseDeepBg,
        background = RoseDeepBg,
        onBackground = Color(0xFFFFF2F7),
        surface = RoseSurface,
        onSurface = Color(0xFFFFF2F7),
        surfaceVariant = Color(0xFF2E1431),
        onSurfaceVariant = Color(0xFFD6ACC2),
        outline = Color(0xFF542758),
        outlineVariant = Color(0xFF3A1A3D),
        error = CoralAlert,
        onError = Color.White
    )

    AppThemePalette.SOLAR_AMBER -> darkColorScheme(
        primary = AmberPulse,
        onPrimary = SolarNight,
        primaryContainer = Color(0xFF5C3E00),
        onPrimaryContainer = Color(0xFFFFE6A8),
        secondary = CoralFlame,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF5E2500),
        onSecondaryContainer = Color(0xFFFFDBB8),
        tertiary = ElectricCyan,
        onTertiary = SolarNight,
        background = SolarNight,
        onBackground = Color(0xFFFFF8EE),
        surface = SolarSurface,
        onSurface = Color(0xFFFFF8EE),
        surfaceVariant = Color(0xFF302212),
        onSurfaceVariant = Color(0xFFD5BEA2),
        outline = Color(0xFF573E22),
        outlineVariant = Color(0xFF3E2B16),
        error = CoralAlert,
        onError = Color.White
    )
}

private fun lightSchemeFor(palette: AppThemePalette): ColorScheme = when (palette) {
    AppThemePalette.OBSIDIAN_CYAN -> lightColorScheme(
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

    AppThemePalette.AURORA_VIOLET -> lightColorScheme(
        primary = Color(0xFF651FFF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE4FF),
        onPrimaryContainer = Color(0xFF25006E),
        secondary = Color(0xFFAA00FF),
        onSecondary = Color.White,
        background = Color(0xFFF6F2FF),
        onBackground = Color(0xFF140D26),
        surface = Color.White,
        onSurface = Color(0xFF140D26),
        surfaceVariant = Color(0xFFEBE3FA),
        onSurfaceVariant = Color(0xFF564A75),
        outline = Color(0xFFCCC0E5),
        outlineVariant = Color(0xFFE2DAF5)
    )

    AppThemePalette.EMERALD_NEON -> lightColorScheme(
        primary = Color(0xFF008952),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8FFE5),
        onPrimaryContainer = Color(0xFF00331D),
        secondary = Color(0xFF00796B),
        onSecondary = Color.White,
        background = Color(0xFFF1FAF5),
        onBackground = Color(0xFF082117),
        surface = Color.White,
        onSurface = Color(0xFF082117),
        surfaceVariant = Color(0xFFDCF2E6),
        onSurfaceVariant = Color(0xFF3E6353),
        outline = Color(0xFFB6D9C6),
        outlineVariant = Color(0xFFD7EDE1)
    )

    AppThemePalette.MIDNIGHT_ROSE -> lightColorScheme(
        primary = Color(0xFFC2185B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E8),
        onPrimaryContainer = Color(0xFF4A0020),
        secondary = Color(0xFF7B1FA2),
        onSecondary = Color.White,
        background = Color(0xFFFFF4F8),
        onBackground = Color(0xFF240915),
        surface = Color.White,
        onSurface = Color(0xFF240915),
        surfaceVariant = Color(0xFFFCE4EE),
        onSurfaceVariant = Color(0xFF6B4256),
        outline = Color(0xFFE3BDCE),
        outlineVariant = Color(0xFFF3DAE5)
    )

    AppThemePalette.SOLAR_AMBER -> lightColorScheme(
        primary = Color(0xFFD87A00),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE7C2),
        onPrimaryContainer = Color(0xFF452400),
        secondary = Color(0xFFE65100),
        onSecondary = Color.White,
        background = Color(0xFFFFF9F0),
        onBackground = Color(0xFF241606),
        surface = Color.White,
        onSurface = Color(0xFF241606),
        surfaceVariant = Color(0xFFFAECDA),
        onSurfaceVariant = Color(0xFF6B5338),
        outline = Color(0xFFE3CBB0),
        outlineVariant = Color(0xFFF2E2CF)
    )
}

private fun glassTokensFor(
    palette: AppThemePalette,
    darkTheme: Boolean,
    scheme: ColorScheme
): SaveBoxGlassTokens {
    return if (darkTheme) {
        SaveBoxGlassTokens(
            glassStart = scheme.surfaceVariant.copy(alpha = 0.72f),
            glassEnd = scheme.surface.copy(alpha = 0.62f),
            borderHighlight = Color.White.copy(alpha = 0.28f),
            borderFade = scheme.primary.copy(alpha = 0.24f),
            glowPrimary = scheme.primary,
            glowSecondary = scheme.secondary,
            backdropGradientTop = scheme.background,
            backdropGradientBottom = scheme.surface
        )
    } else {
        SaveBoxGlassTokens(
            glassStart = Color.White.copy(alpha = 0.88f),
            glassEnd = scheme.surfaceVariant.copy(alpha = 0.78f),
            borderHighlight = Color.White,
            borderFade = scheme.primary.copy(alpha = 0.26f),
            glowPrimary = scheme.primary,
            glowSecondary = scheme.secondary,
            backdropGradientTop = scheme.background,
            backdropGradientBottom = scheme.surfaceVariant
        )
    }
}

val SaveBoxShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SaveBoxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    themePalette: AppThemePalette = AppThemePalette.OBSIDIAN_CYAN,
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
        darkTheme -> darkSchemeFor(themePalette)
        else -> lightSchemeFor(themePalette)
    }

    val glassTokens = glassTokensFor(themePalette, darkTheme, colorScheme)

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

    CompositionLocalProvider(LocalSaveBoxGlassTokens provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SaveBoxTypography,
            shapes = SaveBoxShapes,
            content = content
        )
    }
}
