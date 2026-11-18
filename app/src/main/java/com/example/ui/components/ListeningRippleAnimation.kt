package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberCyanGlow

/**
 * A subtle, elegant ripple animation on the main screen that triggers
 * whenever ROXY is actively listening to voice input.
 *
 * Emits concentric luminous wave rings that gently expand outward from the audio hub,
 * modulating subtly with the real-time audio input level and smoothly fading in/out.
 */
@Composable
fun ListeningRippleAnimation(
    isListening: Boolean,
    audioLevel: Float = 0f,
    primaryColor: Color = CyberCyan,
    accentColor: Color = CyberCyanGlow,
    modifier: Modifier = Modifier
) {
    // Master opacity that smoothly fades the entire ripple effect in/out
    val masterAlpha by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "ripple_master_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "listening_ripple_transition")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )

    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_pulse"
    )

    Canvas(modifier = modifier) {
        if (masterAlpha <= 0.001f) return@Canvas

        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val center = Offset(centerX, centerY)

        val minDimension = minOf(size.width, size.height)
        val baseRadius = minDimension * 0.28f
        val maxRadius = minDimension * 0.52f

        // 1. Soft inner radial aura that gently breathes when listening
        val auraRadius = (baseRadius * 1.35f + audioLevel * 25f * density) * ambientPulse
        val auraAlpha = (0.12f + audioLevel * 0.15f) * masterAlpha
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = auraAlpha),
                    accentColor.copy(alpha = auraAlpha * 0.4f),
                    Color.Transparent
                ),
                center = center,
                radius = auraRadius
            ),
            radius = auraRadius,
            center = center
        )

        // 2. Concentric expanding subtle ripple wave rings (3 staggered waves)
        val rippleCount = 3
        for (i in 0 until rippleCount) {
            val phaseOffset = i.toFloat() / rippleCount
            val progress = (waveProgress + phaseOffset) % 1f

            // Smooth expansion: starts right at base radius and expands to max radius
            val currentRadius = baseRadius + (maxRadius - baseRadius) * progress

            // Alpha starts gentle, peaks around 25% expansion, and fades completely to 0 at 100%
            val normalizedProgress = progress.coerceIn(0f, 1f)
            val fadeAlpha = if (normalizedProgress < 0.25f) {
                normalizedProgress / 0.25f
            } else {
                1f - ((normalizedProgress - 0.25f) / 0.75f)
            }

            // Audio level adds a subtle responsive brightness boost
            val ringAlpha = (fadeAlpha * (0.28f + audioLevel * 0.22f) * masterAlpha).coerceIn(0f, 0.7f)

            if (ringAlpha > 0.005f) {
                // Subtle stroke width tapering down as the ripple expands outwards
                val strokeWidth = ((2.8f - progress * 1.6f) + audioLevel * 1.5f) * density

                // Gradient stroke: blend between primary (CyberCyan) and accent
                val ringGradient = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = ringAlpha),
                        accentColor.copy(alpha = ringAlpha * 0.7f)
                    ),
                    center = center,
                    radius = currentRadius + 10f * density
                )

                drawCircle(
                    brush = ringGradient,
                    radius = currentRadius,
                    center = center,
                    style = Stroke(width = strokeWidth.coerceAtLeast(1f))
                )

                // Extra subtle soft outer glow ring for the foremost wave
                if (i == 0 && progress > 0.1f && progress < 0.8f) {
                    drawCircle(
                        color = primaryColor.copy(alpha = (ringAlpha * 0.25f)),
                        radius = currentRadius + 3f * density,
                        center = center,
                        style = Stroke(width = strokeWidth * 2.2f)
                    )
                }
            }
        }
    }
}
