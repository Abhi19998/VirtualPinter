package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-tech branded app logo with glowing radial gradients,
 * layered 3D printer chassis, and a materialized PDF sheet.
 */
@Composable
fun AppBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    isAnimated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(if (isAnimated) pulseScale else 1f)
            .testTag("app_brand_logo"),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing ambient ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = this.size.minDimension
            if (minDim <= 0f) return@Canvas
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val radius = (minDim / 2).coerceAtLeast(1f)

            // Ambient radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = if (isAnimated) glowAlpha else 0.4f),
                        Color(0xFF0284C7).copy(alpha = if (isAnimated) glowAlpha * 0.5f else 0.2f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Orbiting tech arc
            if (isAnimated) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF38BDF8),
                            Color(0xFF818CF8),
                            Color.Transparent
                        )
                    ),
                    startAngle = rotationAngle,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Inner Shield Badge
        Surface(
            modifier = Modifier
                .size(size * 0.76f)
                .shadow(10.dp, shape = RoundedCornerShape(size * 0.22f)),
            shape = RoundedCornerShape(size * 0.22f),
            color = Color(0xFF0F172A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A),
                                Color(0xFF0284C7).copy(alpha = 0.35f)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF38BDF8),
                                Color(0xFF6366F1),
                                Color(0xFF0284C7).copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(size * 0.22f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Vector Printer & PDF Canvas
                Canvas(modifier = Modifier.fillMaxSize(0.72f)) {
                    drawPrinterLogo(this)
                }
            }
        }
    }
}

/**
 * Draws the high-res vector printer chassis, paper sheet, and red PDF ribbon
 */
private fun drawPrinterLogo(scope: DrawScope) {
    with(scope) {
        val w = size.width
        val h = size.height

        // 1. Paper sheet ejecting from top
        val paperLeft = w * 0.24f
        val paperTop = h * 0.08f
        val paperWidth = w * 0.52f
        val paperHeight = h * 0.48f

        // Paper body
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFFE2E8F0))
            ),
            topLeft = Offset(paperLeft, paperTop),
            size = Size(paperWidth, paperHeight),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Red "PDF" badge ribbon on top left of paper
        drawRoundRect(
            color = Color(0xFFEF4444),
            topLeft = Offset(paperLeft + w * 0.04f, paperTop + h * 0.04f),
            size = Size(paperWidth * 0.44f, paperHeight * 0.26f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )

        // Paper text placeholder lines
        val line1Top = paperTop + paperHeight * 0.40f
        val line2Top = paperTop + paperHeight * 0.60f
        drawLine(
            color = Color(0xFF94A3B8),
            start = Offset(paperLeft + w * 0.04f, line1Top),
            end = Offset(paperLeft + paperWidth - w * 0.04f, line1Top),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(paperLeft + w * 0.04f, line2Top),
            end = Offset(paperLeft + paperWidth * 0.7f, line2Top),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )

        // 2. Printer Main Body
        val bodyLeft = w * 0.08f
        val bodyTop = h * 0.42f
        val bodyWidth = w * 0.84f
        val bodyHeight = h * 0.44f

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                start = Offset(bodyLeft, bodyTop),
                end = Offset(bodyLeft + bodyWidth, bodyTop + bodyHeight)
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )

        // Printer Chassis Highlight Rim
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7).copy(alpha = 0.3f))
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = w * 0.03f)
        )

        // Output Slot
        val slotLeft = w * 0.18f
        val slotTop = h * 0.56f
        val slotWidth = w * 0.64f
        val slotHeight = h * 0.14f

        drawRoundRect(
            color = Color(0xFF020617),
            topLeft = Offset(slotLeft, slotTop),
            size = Size(slotWidth, slotHeight),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Output glowing paper shelf
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF8FAFC), Color(0xFF38BDF8))
            ),
            topLeft = Offset(slotLeft + w * 0.04f, slotTop + slotHeight * 0.4f),
            size = Size(slotWidth - w * 0.08f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Status LED Lights
        drawCircle(
            color = Color(0xFF22C55E),
            radius = w * 0.035f,
            center = Offset(bodyLeft + bodyWidth - w * 0.12f, bodyTop + h * 0.08f)
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = w * 0.025f,
            center = Offset(bodyLeft + bodyWidth - w * 0.22f, bodyTop + h * 0.08f)
        )
    }
}

/**
 * Animated Scanning laser and radar waves visualizer (Draw-phase optimized, zero recomposition)
 */
@Composable
fun LivePrinterRadarWave(
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isRunning) return

    val infiniteTransition = rememberInfiniteTransition(label = "radar_wave")
    val waveRadius = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_radius"
    )

    Canvas(modifier = modifier) {
        val minDim = size.minDimension
        if (minDim <= 0f) return@Canvas
        val progress = waveRadius.value
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = minDim / 2
        val currentRadius = (maxRadius * progress).coerceAtLeast(0.1f)
        val alpha = ((1f - progress) * 0.5f).coerceIn(0f, 1f)

        drawCircle(
            color = Color(0xFF22C55E).copy(alpha = alpha),
            radius = currentRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
