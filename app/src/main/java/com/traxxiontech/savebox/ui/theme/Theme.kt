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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.traxxiontech.savebox.model.AppThemeMode
import com.traxxiontech.savebox.model.AppThemePalette
import com.traxxiontech.savebox.model.BackgroundColorBlendMode
import java.io.File

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

@Immutable
data class SaveBoxBackgroundConfig(
    val imagePath: String? = null,
    val overlayDim: Float = 0.42f
) {
    val hasActiveImage: Boolean
        get() = !imagePath.isNullOrBlank() && File(imagePath).exists()
}

val LocalSaveBoxGlassTokens = staticCompositionLocalOf {
    SaveBoxGlassTokens(
        glassStart = Color(0x88151D2B),
        glassEnd = Color(0x660B1019),
        borderHighlight = Color(0x36FFFFFF),
        borderFade = Color(0x14FFFFFF),
        glowPrimary = ElectricCyan,
        glowSecondary = AuroraViolet,
        backdropGradientTop = ObsidianNight,
        backdropGradientBottom = ObsidianSurface
    )
}

val LocalSaveBoxBackgroundConfig = staticCompositionLocalOf {
    SaveBoxBackgroundConfig()
}

private fun darkSchemeFor(palette: AppThemePalette): ColorScheme = when (palette) {
    AppThemePalette.OBSIDIAN_CYAN -> darkColorScheme(
        primary = Color(0xFF4C7AA8),
        onPrimary = Color(0xFFEAF1FA),
        primaryContainer = ElectricCyanDim,
        onPrimaryContainer = Color(0xFFA7C0DB),
        secondary = Color(0xFF56677E),
        onSecondary = Color(0xFFEAF1FA),
        secondaryContainer = Color(0xFF151C27),
        onSecondaryContainer = Color(0xFFA3B1C6),
        tertiary = Color(0xFF3E7855),
        onTertiary = Color(0xFFEAF5EE),
        background = ObsidianNight,
        onBackground = Color(0xFFD5DFEC),
        surface = ObsidianSurface,
        onSurface = Color(0xFFD5DFEC),
        surfaceVariant = ObsidianElevated,
        onSurfaceVariant = Color(0xFF8B99AF),
        outline = Color(0xFF283448),
        outlineVariant = Color(0xFF18202E),
        error = CoralAlert,
        onError = Color(0xFFFCE8E8)
    )

    AppThemePalette.AURORA_VIOLET -> darkColorScheme(
        primary = Color(0xFF6B669E),
        onPrimary = Color(0xFFF0EEFA),
        primaryContainer = Color(0xFF1E1A36),
        onPrimaryContainer = Color(0xFFBBB6DC),
        secondary = Color(0xFF7B5EA3),
        onSecondary = Color(0xFFF0EEFA),
        secondaryContainer = Color(0xFF221836),
        onSecondaryContainer = Color(0xFFC4B6DE),
        tertiary = Color(0xFF4C7AA8),
        onTertiary = Color(0xFFEAF1FA),
        background = VioletNight,
        onBackground = Color(0xFFD9D6EB),
        surface = VioletSurface,
        onSurface = Color(0xFFD9D6EB),
        surfaceVariant = Color(0xFF181832),
        onSurfaceVariant = Color(0xFF9691B5),
        outline = Color(0xFF312B52),
        outlineVariant = Color(0xFF1E1A36),
        error = CoralAlert,
        onError = Color(0xFFFCE8E8)
    )

    AppThemePalette.EMERALD_NEON -> darkColorScheme(
        primary = Color(0xFF3D7D59),
        onPrimary = Color(0xFFE9F7EF),
        primaryContainer = Color(0xFF0E291C),
        onPrimaryContainer = Color(0xFF9ED1B4),
        secondary = Color(0xFF35736A),
        onSecondary = Color(0xFFE9F7F4),
        secondaryContainer = Color(0xFF0C2420),
        onSecondaryContainer = Color(0xFF98CEC5),
        tertiary = Color(0xFF4C7AA8),
        onTertiary = Color(0xFFEAF1FA),
        background = EmeraldBg,
        onBackground = Color(0xFFD2E5DA),
        surface = EmeraldSurface,
        onSurface = Color(0xFFD2E5DA),
        surfaceVariant = Color(0xFF102820),
        onSurfaceVariant = Color(0xFF84A895),
        outline = Color(0xFF1F4235),
        outlineVariant = Color(0xFF132C23),
        error = CoralAlert,
        onError = Color(0xFFFCE8E8)
    )

    AppThemePalette.MIDNIGHT_ROSE -> darkColorScheme(
        primary = Color(0xFF8C5377),
        onPrimary = Color(0xFFFAEFF5),
        primaryContainer = Color(0xFF2D1727),
        onPrimaryContainer = Color(0xFFD9AFC7),
        secondary = Color(0xFF70588A),
        onSecondary = Color(0xFFF4EFFA),
        secondaryContainer = Color(0xFF221630),
        onSecondaryContainer = Color(0xFFC1B0D6),
        tertiary = Color(0xFF4C7AA8),
        onTertiary = Color(0xFFEAF1FA),
        background = RoseDeepBg,
        onBackground = Color(0xFFE6D8E0),
        surface = RoseSurface,
        onSurface = Color(0xFFE6D8E0),
        surfaceVariant = Color(0xFF201329),
        onSurfaceVariant = Color(0xFFA88E9E),
        outline = Color(0xFF3D2647),
        outlineVariant = Color(0xFF291830),
        error = CoralAlert,
        onError = Color(0xFFFCE8E8)
    )

    AppThemePalette.SOLAR_AMBER -> darkColorScheme(
        primary = Color(0xFF94693C),
        onPrimary = Color(0xFFFAF3EB),
        primaryContainer = Color(0xFF2E1F10),
        onPrimaryContainer = Color(0xFFD9BEA2),
        secondary = Color(0xFF855331),
        onSecondary = Color(0xFFFAF1EB),
        secondaryContainer = Color(0xFF2C190F),
        onSecondaryContainer = Color(0xFFD6B5A1),
        tertiary = Color(0xFF4C7AA8),
        onTertiary = Color(0xFFEAF1FA),
        background = SolarNight,
        onBackground = Color(0xFFE6DDD2),
        surface = SolarSurface,
        onSurface = Color(0xFFE6DDD2),
        surfaceVariant = Color(0xFF241A10),
        onSurfaceVariant = Color(0xFFA89682),
        outline = Color(0xFF42301F),
        outlineVariant = Color(0xFF2C2014),
        error = CoralAlert,
        onError = Color(0xFFFCE8E8)
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

private fun imageMatchedScheme(
    primary: Color,
    secondary: Color,
    surface: Color,
    background: Color,
    darkTheme: Boolean
): ColorScheme {
    return if (darkTheme) {
        val textInk = lerp(Color(0xFFE8EEF5), primary, 0.14f)
        val mutedInk = lerp(Color(0xFF9BA8B8), primary, 0.18f)
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFFF5F8FC),
            primaryContainer = lerp(background, primary, 0.28f),
            onPrimaryContainer = textInk,
            secondary = secondary,
            onSecondary = Color(0xFFF5F8FC),
            secondaryContainer = lerp(surface, secondary, 0.25f),
            onSecondaryContainer = textInk,
            tertiary = lerp(primary, secondary, 0.5f),
            onTertiary = Color(0xFFF5F8FC),
            background = background,
            onBackground = textInk,
            surface = surface,
            onSurface = textInk,
            surfaceVariant = lerp(surface, primary, 0.14f),
            onSurfaceVariant = mutedInk,
            outline = lerp(surface, Color.White, 0.18f),
            outlineVariant = lerp(surface, Color.White, 0.10f),
            error = CoralAlert,
            onError = Color(0xFFFCE8E8)
        )
    } else {
        val lightBg = lerp(Color(0xFFF4F6FA), primary, 0.08f)
        val lightSurf = lerp(Color(0xFFFFFFFF), secondary, 0.06f)
        val lightSurfVar = lerp(Color(0xFFE7ECF3), primary, 0.12f)
        val darkInk = lerp(Color(0xFF141B24), primary, 0.18f)
        lightColorScheme(
            primary = lerp(primary, Color.Black, 0.24f),
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, primary, 0.22f),
            onPrimaryContainer = darkInk,
            secondary = lerp(secondary, Color.Black, 0.22f),
            onSecondary = Color.White,
            secondaryContainer = lerp(Color.White, secondary, 0.18f),
            onSecondaryContainer = darkInk,
            tertiary = primary,
            onTertiary = Color.White,
            background = lightBg,
            onBackground = darkInk,
            surface = lightSurf,
            onSurface = darkInk,
            surfaceVariant = lightSurfVar,
            onSurfaceVariant = lerp(darkInk, Color.White, 0.35f),
            outline = lerp(lightSurfVar, Color.Black, 0.18f),
            outlineVariant = lightSurfVar
        )
    }
}

private fun glassTokensFor(
    darkTheme: Boolean,
    scheme: ColorScheme,
    hasWallpaperImage: Boolean
): SaveBoxGlassTokens {
    return if (darkTheme) {
        val cardTopAlpha = if (hasWallpaperImage) 0.46f else 0.62f
        val cardBottomAlpha = if (hasWallpaperImage) 0.34f else 0.52f
        SaveBoxGlassTokens(
            glassStart = lerp(scheme.surfaceVariant, Color.White, 0.08f).copy(alpha = cardTopAlpha),
            glassEnd = scheme.surface.copy(alpha = cardBottomAlpha),
            borderHighlight = Color.White.copy(alpha = if (hasWallpaperImage) 0.28f else 0.20f),
            borderFade = scheme.primary.copy(alpha = 0.20f),
            glowPrimary = scheme.primary,
            glowSecondary = scheme.secondary,
            backdropGradientTop = scheme.background,
            backdropGradientBottom = scheme.surface
        )
    } else {
        SaveBoxGlassTokens(
            glassStart = Color.White.copy(alpha = if (hasWallpaperImage) 0.64f else 0.80f),
            glassEnd = scheme.surfaceVariant.copy(alpha = if (hasWallpaperImage) 0.48f else 0.68f),
            borderHighlight = Color.White.copy(alpha = 0.85f),
            borderFade = scheme.primary.copy(alpha = 0.22f),
            glowPrimary = scheme.primary,
            glowSecondary = scheme.secondary,
            backdropGradientTop = scheme.background,
            backdropGradientBottom = scheme.surfaceVariant
        )
    }
}

val SaveBoxShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun SaveBoxTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    themePalette: AppThemePalette = AppThemePalette.OBSIDIAN_CYAN,
    dynamicColor: Boolean = false,
    backgroundImagePath: String? = null,
    backgroundColorBlendMode: BackgroundColorBlendMode = BackgroundColorBlendMode.AUTO_FROM_IMAGE,
    backgroundOverlayDim: Float = 0.42f,
    extractedPrimaryArgb: Long? = null,
    extractedSecondaryArgb: Long? = null,
    extractedSurfaceArgb: Long? = null,
    extractedBackgroundArgb: Long? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val bgConfig = SaveBoxBackgroundConfig(
        imagePath = backgroundImagePath,
        overlayDim = backgroundOverlayDim
    )
    val useImageMatchedColors = bgConfig.hasActiveImage &&
        backgroundColorBlendMode == BackgroundColorBlendMode.AUTO_FROM_IMAGE &&
        extractedPrimaryArgb != null &&
        extractedSecondaryArgb != null &&
        extractedSurfaceArgb != null &&
        extractedBackgroundArgb != null

    val colorScheme = when {
        useImageMatchedColors -> {
            imageMatchedScheme(
                primary = Color(extractedPrimaryArgb!!.toInt()),
                secondary = Color(extractedSecondaryArgb!!.toInt()),
                surface = Color(extractedSurfaceArgb!!.toInt()),
                background = Color(extractedBackgroundArgb!!.toInt()),
                darkTheme = darkTheme
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkSchemeFor(themePalette)
        else -> lightSchemeFor(themePalette)
    }

    val glassTokens = glassTokensFor(darkTheme, colorScheme, bgConfig.hasActiveImage)

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

    CompositionLocalProvider(
        LocalSaveBoxGlassTokens provides glassTokens,
        LocalSaveBoxBackgroundConfig provides bgConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SaveBoxTypography,
            shapes = SaveBoxShapes,
            content = content
        )
    }
}
