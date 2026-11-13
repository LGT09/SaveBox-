package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MediaPlatform
import com.example.ui.theme.AuroraViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassBorderDark
import com.example.ui.theme.GlassBorderLight
import java.util.Locale
import kotlin.math.sin

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = ms / 1000L
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}

@Composable
fun AnimatedAuroraBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_phase"
    )

    val bg = MaterialTheme.colorScheme.background
    val primaryGlow = MaterialTheme.colorScheme.primary
    val secondaryGlow = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        if (isDark) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .alpha(0.24f)
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = if (isDark) 0.16f else 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.2f + 0.3f * phase), h * 0.12f),
                    radius = w * 0.75f
                ),
                radius = w * 0.75f,
                center = Offset(w * (0.2f + 0.3f * phase), h * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryGlow.copy(alpha = if (isDark) 0.15f else 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.85f - 0.25f * phase), h * 0.45f),
                    radius = w * 0.68f
                ),
                radius = w * 0.68f,
                center = Offset(w * (0.85f - 0.25f * phase), h * 0.45f)
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "glass_press_scale"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val glassSurfaceBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xB818233E),
                Color(0x9910182C)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xEBFFFFFF),
                Color(0xD9EEF4FC)
            )
        )
    }

    val borderBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(GlassBorderLight, GlassBorderDark, Color(0x1400E5FF))
        } else {
            listOf(Color.White, Color(0x80B0C4DE))
        }
    )

    val cardModifier = modifier
        .scale(scale)
        .shadow(
            elevation = if (isDark) 10.dp else 6.dp,
            shape = shape,
            ambientColor = ElectricCyan.copy(alpha = 0.14f),
            spotColor = AuroraViolet.copy(alpha = 0.18f)
        )
        .clip(shape)
        .background(glassSurfaceBrush)
        .border(width = 1.dp, brush = borderBrush, shape = shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Column(
        modifier = cardModifier.padding(contentPadding),
        content = content
    )
}

@Composable
fun PlatformBadgeChip(
    platform: MediaPlatform,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = platform.accentColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, platform.accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(platform.accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ShimmerSkeletonCard(
    modifier: Modifier = Modifier,
    height: Dp = 140.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset, 0f),
        end = Offset(offset + 280f, 180f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(brush)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
    )
}

@Composable
fun SaveBoxOrbitalLogo(
    size: Dp = 128.dp,
    isAnimating: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbital_logo")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing)
        ),
        label = "orbit_rot"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbit_pulse"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(if (isAnimating) pulse else 1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val center = Offset(r, r)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.32f),
                        AuroraViolet.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                ),
                radius = r
            )

            rotate(if (isAnimating) rotation else 0f, center) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(ElectricCyan, AuroraViolet, Color.Transparent, ElectricCyan)),
                    startAngle = 0f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(8f, 8f),
                    size = Size(this.size.width - 16f, this.size.height - 16f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }

            // Frosted vault rounded square
            val boxSide = this.size.minDimension * 0.58f
            val topLeft = Offset((this.size.width - boxSide) / 2f, (this.size.height - boxSide) / 2f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF152445), Color(0xFF0C152B))
                ),
                topLeft = topLeft,
                size = Size(boxSide, boxSide),
                cornerRadius = CornerRadius(boxSide * 0.28f, boxSide * 0.28f)
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(ElectricCyan, AuroraViolet)),
                topLeft = topLeft,
                size = Size(boxSide, boxSide),
                cornerRadius = CornerRadius(boxSide * 0.28f, boxSide * 0.28f),
                style = Stroke(width = 4f)
            )

            // Download arrow + Play accent inside vault
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val arrowPath = Path().apply {
                moveTo(cx, cy - boxSide * 0.24f)
                lineTo(cx, cy + boxSide * 0.14f)
                moveTo(cx - boxSide * 0.16f, cy + boxSide * 0.01f)
                lineTo(cx, cy + boxSide * 0.18f)
                lineTo(cx + boxSide * 0.16f, cy + boxSide * 0.01f)
            }
            drawPath(
                path = arrowPath,
                color = ElectricCyan,
                style = Stroke(width = 7f, cap = StrokeCap.Round)
            )
            drawLine(
                color = AuroraViolet,
                start = Offset(cx - boxSide * 0.18f, cy + boxSide * 0.27f),
                end = Offset(cx + boxSide * 0.18f, cy + boxSide * 0.27f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun AnimatedAudioVisualizer(
    isPlaying: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 34
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing)
        ),
        label = "viz_phase"
    )

    val activeColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 1.7f)
        val gap = (size.width - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)
        val maxH = size.height

        for (i in 0 until barCount) {
            val normalizedPos = i.toFloat() / barCount.toFloat()
            val wave = if (isPlaying) {
                0.35f + 0.65f * ((sin(phase + i * 0.6f) + 1f) / 2f)
            } else {
                0.25f + 0.35f * ((sin(i * 0.85f) + 1f) / 2f)
            }
            val barH = (maxH * wave).coerceAtLeast(8f)
            val x = i * (barWidth + gap)
            val y = (maxH - barH) / 2f
            val isPassed = normalizedPos <= progress

            drawRoundRect(
                brush = if (isPassed) {
                    Brush.verticalGradient(listOf(activeColor, secondaryColor))
                } else {
                    Brush.verticalGradient(listOf(inactiveColor, inactiveColor))
                },
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
