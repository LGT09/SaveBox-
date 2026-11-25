package com.traxxiontech.savebox.ui.components

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
import com.traxxiontech.savebox.R
import com.traxxiontech.savebox.model.VisualizerStyle
import com.traxxiontech.savebox.ui.theme.LocalSaveBoxGlassTokens
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
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
    val tokens = LocalSaveBoxGlassTokens.current
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tokens.backdropGradientTop,
                        tokens.backdropGradientBottom,
                        tokens.backdropGradientTop
                    )
                )
            )
    ) {
        if (isDark) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .alpha(0.08f)
            )
        }
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val pAlpha = if (isDark) 0.10f else 0.12f
            val sAlpha = if (isDark) 0.08f else 0.10f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to tokens.glowPrimary.copy(alpha = pAlpha),
                        0.45f to tokens.glowPrimary.copy(alpha = pAlpha * 0.42f),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(w * (0.18f + 0.34f * phase), h * 0.14f),
                    radius = w * 0.85f
                ),
                radius = w * 0.85f,
                center = Offset(w * (0.18f + 0.34f * phase), h * 0.14f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to tokens.glowSecondary.copy(alpha = sAlpha),
                        0.45f to tokens.glowSecondary.copy(alpha = sAlpha * 0.42f),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(w * (0.88f - 0.28f * phase), h * 0.48f),
                    radius = w * 0.78f
                ),
                radius = w * 0.78f,
                center = Offset(w * (0.88f - 0.28f * phase), h * 0.48f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to tokens.glowPrimary.copy(alpha = pAlpha * 0.55f),
                        0.5f to tokens.glowPrimary.copy(alpha = pAlpha * 0.22f),
                        1.0f to Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.72f
                ),
                radius = w * 0.72f,
                center = Offset(w * 0.5f, h * 0.88f)
            )
        }
        content()
    }
}

/**
 * Reusable GlassCard built on Material 3 Surface with translucent glass backdrop,
 * crisp specular border gradient, soft elevation, and micro-interaction press spring.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    tonalElevation: Dp = 6.dp,
    shadowElevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalSaveBoxGlassTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "glass_press_scale"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val glassSurfaceBrush = Brush.linearGradient(
        colors = listOf(tokens.glassStart, tokens.glassEnd)
    )
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            tokens.borderHighlight,
            tokens.borderFade,
            tokens.glowSecondary.copy(alpha = 0.16f)
        )
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(shape)
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
            ),
        shape = shape,
        color = tokens.glassEnd.copy(alpha = 0.52f),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .background(glassSurfaceBrush)
                .padding(contentPadding),
            content = content
        )
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
    GlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        onClick = onClick,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun MediaSourceBadgeChip(
    label: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
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
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
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
                        primary.copy(alpha = 0.32f),
                        secondary.copy(alpha = 0.14f),
                        Color.Transparent
                    )
                ),
                radius = r
            )

            rotate(if (isAnimating) rotation else 0f, center) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(primary, secondary, Color.Transparent, primary)),
                    startAngle = 0f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(8f, 8f),
                    size = Size(this.size.width - 16f, this.size.height - 16f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }

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
                brush = Brush.linearGradient(listOf(primary, secondary)),
                topLeft = topLeft,
                size = Size(boxSide, boxSide),
                cornerRadius = CornerRadius(boxSide * 0.28f, boxSide * 0.28f),
                style = Stroke(width = 4f)
            )

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
                color = primary,
                style = Stroke(width = 7f, cap = StrokeCap.Round)
            )
            drawLine(
                color = secondary,
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
    style: VisualizerStyle = VisualizerStyle.SPECTRUM_BARS,
    barCount: Int = 36
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing)
        ),
        label = "viz_phase"
    )

    val activeColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)

    Canvas(modifier = modifier) {
        when (style) {
            VisualizerStyle.SPECTRUM_BARS -> {
                val barWidth = size.width / (barCount * 1.65f)
                val gap = (size.width - barCount * barWidth) / (barCount - 1).coerceAtLeast(1)
                val maxH = size.height

                for (i in 0 until barCount) {
                    val normalizedPos = i.toFloat() / barCount.toFloat()
                    val harmonic = sin(phase * 2f + i * 0.35f) * 0.18f
                    val wave = if (isPlaying) {
                        (0.32f + 0.56f * ((sin(phase + i * 0.58f) + 1f) / 2f) + harmonic).coerceIn(0.18f, 1f)
                    } else {
                        0.22f + 0.30f * ((sin(i * 0.85f) + 1f) / 2f)
                    }
                    val barH = (maxH * wave).coerceAtLeast(8f)
                    val x = i * (barWidth + gap)
                    val y = (maxH - barH) / 2f
                    val isPassed = normalizedPos <= progress

                    drawRoundRect(
                        brush = if (isPassed) {
                            Brush.verticalGradient(listOf(activeColor, secondaryColor, tertiaryColor))
                        } else {
                            Brush.verticalGradient(listOf(inactiveColor, inactiveColor.copy(alpha = 0.25f)))
                        },
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            VisualizerStyle.ORBITAL_RING -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = (minOf(size.width, size.height) * 0.34f).coerceAtLeast(14f)
                val spokes = 42
                for (i in 0 until spokes) {
                    val angle = (2 * PI * i / spokes).toFloat()
                    val amp = if (isPlaying) {
                        0.28f + 0.72f * ((sin(phase * 2f + i * 0.55f) + 1f) / 2f)
                    } else {
                        0.22f
                    }
                    val innerR = baseRadius * 0.72f
                    val outerR = innerR + baseRadius * 0.65f * amp
                    val start = Offset(
                        center.x + cos(angle) * innerR,
                        center.y + sin(angle) * innerR
                    )
                    val end = Offset(
                        center.x + cos(angle) * outerR,
                        center.y + sin(angle) * outerR
                    )
                    val color = if (i % 2 == 0) activeColor else secondaryColor
                    drawLine(
                        color = if (isPlaying) color else color.copy(alpha = 0.45f),
                        start = start,
                        end = end,
                        strokeWidth = 4.5f,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    brush = Brush.sweepGradient(listOf(activeColor, secondaryColor, tertiaryColor, activeColor)),
                    radius = baseRadius * 0.58f,
                    center = center,
                    style = Stroke(width = 3f)
                )
            }

            VisualizerStyle.DUAL_WAVE -> {
                val midY = size.height / 2f
                val step = 6f
                val path1 = Path()
                val path2 = Path()
                var x = 0f
                var first = true
                while (x <= size.width) {
                    val normX = x / size.width
                    val envelope = sin(normX * PI).toFloat()
                    val amp1 = if (isPlaying) size.height * 0.40f * envelope else size.height * 0.14f * envelope
                    val amp2 = if (isPlaying) size.height * 0.30f * envelope else size.height * 0.10f * envelope
                    val y1 = midY + sin(normX * 12f + phase) * amp1
                    val y2 = midY + sin(normX * 9f - phase * 1.3f) * amp2
                    if (first) {
                        path1.moveTo(x, y1)
                        path2.moveTo(x, y2)
                        first = false
                    } else {
                        path1.lineTo(x, y1)
                        path2.lineTo(x, y2)
                    }
                    x += step
                }
                drawPath(
                    path = path1,
                    brush = Brush.horizontalGradient(listOf(activeColor, secondaryColor)),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = path2,
                    brush = Brush.horizontalGradient(listOf(secondaryColor, tertiaryColor)),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Custom Canvas circular progress indicator that overlays directly on top of media thumbnails
 * while a download is QUEUED, DOWNLOADING, or PAUSED.
 */
@Composable
fun ThumbnailDownloadProgressOverlay(
    progress: Float,
    status: String,
    isIndeterminate: Boolean = false,
    modifier: Modifier = Modifier,
    ringSize: Dp = 42.dp
) {
    val active = status == "DOWNLOADING" || status == "QUEUED" || status == "PAUSED"
    if (!active) return

    val clamped = progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val arcColor = if (status == "PAUSED") {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val secondaryArc = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "thumb_dl_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing)
        ),
        label = "thumb_dl_angle"
    )

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = (size.minDimension * 0.11f).coerceIn(3f, 7f)
                val inset = strokePx / 2f
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                val start = if (isIndeterminate) spinAngle else -90f
                val sweep = if (isIndeterminate) 110f else (clamped * 360f).coerceAtLeast(18f)

                drawArc(
                    brush = Brush.sweepGradient(listOf(arcColor, secondaryArc, arcColor)),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            val label = when {
                status == "PAUSED" -> "||"
                isIndeterminate || status == "QUEUED" && clamped <= 0f -> "..."
                else -> "${(clamped * 100).toInt()}%"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

