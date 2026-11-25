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
        glassStart = Color(0x9E0E141F),
        glassEnd = Color(0x8C080C13),
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
        onPrimary = DimTextPrimary,
        primaryContainer = ElectricCyanDim,
        onPrimaryContainer = Color(0xFF8CA2BC),
        secondary = AuroraViolet,
        onSecondary = DimTextPrimary,
        secondaryContainer = Color(0xFF131922),
        onSecondaryContainer = Color(0xFF8D99AB),
        tertiary = EmeraldMint,
        onTertiary = DimTextPrimary,
        background = ObsidianNight,
        onBackground = DimTextPrimary,
        surface = ObsidianSurface,
        onSurface = DimTextPrimary,
        surfaceVariant = ObsidianElevated,
        onSurfaceVariant = DimTextSecondary,
        outline = Color(0xFF1D2636),
        outlineVariant = Color(0xFF131A26),
        error = CoralAlert,
        onError = DimTextPrimary
    )

    AppThemePalette.AURORA_VIOLET -> darkColorScheme(
        primary = AuroraVioletSoft,
        onPrimary = DimTextPrimary,
        primaryContainer = Color(0xFF181529),
        onPrimaryContainer = Color(0xFF9992B5),
        secondary = MagentaGlow,
        onSecondary = DimTextPrimary,
        secondaryContainer = Color(0xFF1C142B),
        onSecondaryContainer = Color(0xFFA292BD),
        tertiary = ElectricCyan,
        onTertiary = DimTextPrimary,
        background = VioletNight,
        onBackground = DimTextPrimary,
        surface = VioletSurface,
        onSurface = DimTextPrimary,
        surfaceVariant = Color(0xFF141428),
        onSurfaceVariant = DimTextSecondary,
        outline = Color(0xFF252040),
        outlineVariant = Color(0xFF19162B),
        error = CoralAlert,
        onError = DimTextPrimary
    )

    AppThemePalette.EMERALD_NEON -> darkColorScheme(
        primary = EmeraldMint,
        onPrimary = DimTextPrimary,
        primaryContainer = Color(0xFF0C2217),
        onPrimaryContainer = Color(0xFF83AB92),
        secondary = TealSpark,
        onSecondary = DimTextPrimary,
        secondaryContainer = Color(0xFF0A1D1B),
        onSecondaryContainer = Color(0xFF7FA8A2),
        tertiary = ElectricCyan,
        onTertiary = DimTextPrimary,
        background = EmeraldBg,
        onBackground = DimTextPrimary,
        surface = EmeraldSurface,
        onSurface = DimTextPrimary,
        surfaceVariant = Color(0xFF0D201A),
        onSurfaceVariant = DimTextSecondary,
        outline = Color(0xFF173329),
        outlineVariant = Color(0xFF0F241D),
        error = CoralAlert,
        onError = DimTextPrimary
    )

    AppThemePalette.MIDNIGHT_ROSE -> darkColorScheme(
        primary = RoseNeon,
        onPrimary = DimTextPrimary,
        primaryContainer = Color(0xFF241320),
        onPrimaryContainer = Color(0xFFA8899F),
        secondary = AuroraVioletSoft,
        onSecondary = DimTextPrimary,
        secondaryContainer = Color(0xFF1B1226),
        onSecondaryContainer = Color(0xFF9C8EAD),
        tertiary = ElectricCyan,
        onTertiary = DimTextPrimary,
        background = RoseDeepBg,
        onBackground = DimTextPrimary,
        surface = RoseSurface,
        onSurface = DimTextPrimary,
        surfaceVariant = Color(0xFF191020),
        onSurfaceVariant = DimTextSecondary,
        outline = Color(0xFF2F1D36),
        outlineVariant = Color(0xFF211326),
        error = CoralAlert,
        onError = DimTextPrimary
    )

    AppThemePalette.SOLAR_AMBER -> darkColorScheme(
        primary = AmberPulse,
        onPrimary = DimTextPrimary,
        primaryContainer = Color(0xFF261A0E),
        onPrimaryContainer = Color(0xFFA8947C),
        secondary = CoralFlame,
        onSecondary = DimTextPrimary,
        secondaryContainer = Color(0xFF26160D),
        onSecondaryContainer = Color(0xFFA88E7B),
        tertiary = ElectricCyan,
        onTertiary = DimTextPrimary,
        background = SolarNight,
        onBackground = DimTextPrimary,
        surface = SolarSurface,
        onSurface = DimTextPrimary,
        surfaceVariant = Color(0xFF1E160E),
        onSurfaceVariant = DimTextSecondary,
        outline = Color(0xFF332518),
        outlineVariant = Color(0xFF241A10),
        error = CoralAlert,
        onError = DimTextPrimary
    )
}

private fun lightSchemeFor(palette: AppThemePalette): ColorScheme = when (palette) {
    AppThemePalette.OBSIDIAN_CYAN -> lightColorScheme(
        primary = DeepSapphire,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD5E1EE),
        onPrimaryContainer = Color(0xFF112236),
        secondary = DeepIndigo,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDFE5EE),
        onSecondaryContainer = Color(0xFF1E293B),
        tertiary = Color(0xFF245742),
        onTertiary = Color.White,
        background = PearlMist,
        onBackground = InkDark,
        surface = PearlSurface,
        onSurface = InkDark,
        surfaceVariant = PearlElevated,
        onSurfaceVariant = InkMuted,
        outline = Color(0xFFB7C3D4),
        outlineVariant = Color(0xFFD4DCE8),
        error = Color(0xFFB33838),
        onError = Color.White
    )

    AppThemePalette.AURORA_VIOLET -> lightColorScheme(
        primary = Color(0xFF473775),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3DDF2),
        onPrimaryContainer = Color(0xFF1F163D),
        secondary = Color(0xFF5D467D),
        onSecondary = Color.White,
        background = Color(0xFFEFEDF5),
        onBackground = Color(0xFF191526),
        surface = Color(0xFFF7F5FC),
        onSurface = Color(0xFF191526),
        surfaceVariant = Color(0xFFE2DDF0),
        onSurfaceVariant = Color(0xFF554D6B),
        outline = Color(0xFFC5BDD9),
        outlineVariant = Color(0xFFDBD5EB)
    )

    AppThemePalette.EMERALD_NEON -> lightColorScheme(
        primary = Color(0xFF235C44),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD2E8DD),
        onPrimaryContainer = Color(0xFF0E2B1E),
        secondary = Color(0xFF275B53),
        onSecondary = Color.White,
        background = Color(0xFFECF3EF),
        onBackground = Color(0xFF112119),
        surface = Color(0xFFF4FAF7),
        onSurface = Color(0xFF112119),
        surfaceVariant = Color(0xFFD8E8E0),
        onSurfaceVariant = Color(0xFF456153),
        outline = Color(0xFFB4CFC1),
        outlineVariant = Color(0xFFD1E3DA)
    )

    AppThemePalette.MIDNIGHT_ROSE -> lightColorScheme(
        primary = Color(0xFF6E354E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDD8E2),
        onPrimaryContainer = Color(0xFF301220),
        secondary = Color(0xFF593B6B),
        onSecondary = Color.White,
        background = Color(0xFFF5EEF2),
        onBackground = Color(0xFF24131B),
        surface = Color(0xFFFAF5F8),
        onSurface = Color(0xFF24131B),
        surfaceVariant = Color(0xFFEBDCE4),
        onSurfaceVariant = Color(0xFF664B58),
        outline = Color(0xFFD4BAC6),
        outlineVariant = Color(0xFFE8D5DE)
    )

    AppThemePalette.SOLAR_AMBER -> lightColorScheme(
        primary = Color(0xFF704D24),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE0CE),
        onPrimaryContainer = Color(0xFF2E1E0B),
        secondary = Color(0xFF754024),
        onSecondary = Color.White,
        background = Color(0xFFF5F0E9),
        onBackground = Color(0xFF241B11),
        surface = Color(0xFFFAF7F2),
        onSurface = Color(0xFF241B11),
        surfaceVariant = Color(0xFFEBE0D1),
        onSurfaceVariant = Color(0xFF695845),
        outline = Color(0xFFD4C4B0),
        outlineVariant = Color(0xFFE8DCCD)
    )
}

private fun glassTokensFor(
    palette: AppThemePalette,
    darkTheme: Boolean,
    scheme: ColorScheme
): SaveBoxGlassTokens {
    return if (darkTheme) {
        SaveBoxGlassTokens(
            glassStart = scheme.surfaceVariant.copy(alpha = 0.64f),
            glassEnd = scheme.surface.copy(alpha = 0.56f),
            borderHighlight = Color.White.copy(alpha = 0.08f),
            borderFade = scheme.primary.copy(alpha = 0.14f),
            glowPrimary = scheme.primary,
            glowSecondary = scheme.secondary,
            backdropGradientTop = scheme.background,
            backdropGradientBottom = scheme.surface
        )
    } else {
        SaveBoxGlassTokens(
            glassStart = Color.White.copy(alpha = 0.82f),
            glassEnd = scheme.surfaceVariant.copy(alpha = 0.72f),
            borderHighlight = Color.White.copy(alpha = 0.75f),
            borderFade = scheme.primary.copy(alpha = 0.18f),
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
