package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.RoxyMood
import com.example.service.RoxySessionStatus
import com.example.ui.theme.CyberAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbVisualizer(
    status: RoxySessionStatus,
    audioLevel: Float,
    mood: RoxyMood = RoxyMood.SASSY,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (10000 / mood.rotationMultiplier).toInt().coerceAtLeast(3000),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    val pulseTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == RoxyMood.SAVAGE) 1000 else 1800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val sparkRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spark_rotation"
    )

    val animatedPrimaryColor by animateColorAsState(
        targetValue = mood.primaryColor,
        animationSpec = tween(durationMillis = 600),
        label = "mood_primary"
    )

    val animatedSecondaryColor by animateColorAsState(
        targetValue = mood.secondaryColor,
        animationSpec = tween(durationMillis = 600),
        label = "mood_secondary"
    )

    Canvas(modifier = modifier.size(280.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val center = Offset(centerX, centerY)

        val reactiveRadius = (80f * density + audioLevel * 45f * density) * pulseScale

        // 1. Dynamic Radial Aura Glow Infused with Roxy's Sassy Mood
        val auraColors = when (status) {
            RoxySessionStatus.SPEAKING -> listOf(
                animatedPrimaryColor.copy(alpha = 0.88f),
                animatedSecondaryColor.copy(alpha = 0.45f),
                Color.Transparent
            )
            RoxySessionStatus.LISTENING -> listOf(
                mood.secondaryColor.copy(alpha = 0.85f),
                animatedPrimaryColor.copy(alpha = 0.35f),
                Color.Transparent
            )
            RoxySessionStatus.THINKING -> listOf(
                CyberAmber.copy(alpha = 0.85f),
                animatedPrimaryColor.copy(alpha = 0.45f),
                Color.Transparent
            )
            RoxySessionStatus.CONNECTING -> listOf(
                CyberAmber.copy(alpha = 0.80f),
                CyberAmber.copy(alpha = 0.30f),
                Color.Transparent
            )
            RoxySessionStatus.ERROR -> listOf(
                Color(0xFFEF4444).copy(alpha = 0.80f),
                Color(0xFFEF4444).copy(alpha = 0.25f),
                Color.Transparent
            )
            RoxySessionStatus.DISCONNECTED -> listOf(
                animatedPrimaryColor.copy(alpha = 0.25f),
                Color(0xFF334155).copy(alpha = 0.15f),
                Color.Transparent
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = auraColors,
                center = center,
                radius = reactiveRadius * 1.75f
            ),
            radius = reactiveRadius * 1.75f,
            center = center
        )

        // 2. Harmonic Fluid Rings reflecting Sass Wave Count
        val numRings = if (status == RoxySessionStatus.DISCONNECTED) 2 else 4
        val rotationRad = Math.toRadians(rotation.toDouble()).toFloat()

        for (r in 0 until numRings) {
            val ringRadius = reactiveRadius * 0.7f + r * 13f * density
            val points = 48
            val path = Path()

            val ringColor = when (status) {
                RoxySessionStatus.SPEAKING -> animatedPrimaryColor.copy(
                    alpha = (0.92f - r * 0.18f).coerceAtLeast(0.2f)
                )
                RoxySessionStatus.LISTENING -> animatedSecondaryColor.copy(
                    alpha = (0.90f - r * 0.18f).coerceAtLeast(0.2f)
                )
                RoxySessionStatus.THINKING -> CyberAmber.copy(alpha = (0.88f - r * 0.18f).coerceAtLeast(0.2f))
                RoxySessionStatus.CONNECTING -> CyberAmber.copy(alpha = (0.8f - r * 0.18f).coerceAtLeast(0.2f))
                RoxySessionStatus.ERROR -> Color(0xFFEF4444).copy(alpha = (0.8f - r * 0.18f).coerceAtLeast(0.2f))
                RoxySessionStatus.DISCONNECTED -> mood.primaryColor.copy(alpha = 0.35f)
            }

            for (i in 0..points) {
                val angle = (i.toFloat() / points) * 2f * PI.toFloat()
                val totalAngle = angle + rotationRad * (if (r % 2 == 0) 1f else -1f)

                val waveCount = mood.ringWaveCount
                val wobble = if (status == RoxySessionStatus.SPEAKING || status == RoxySessionStatus.LISTENING || status == RoxySessionStatus.THINKING) {
                    sin(angle * waveCount + rotationRad * 3.0 + r).toFloat() * (8f * density + audioLevel * 28f * density)
                } else {
                    sin(angle * (waveCount - 1).coerceAtLeast(2) + rotationRad).toFloat() * 4f * density
                }

                val currentRadius = ringRadius + wobble
                val x = centerX + currentRadius * cos(totalAngle)
                val y = centerY + currentRadius * sin(totalAngle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            drawPath(
                path = path,
                color = ringColor,
                style = Stroke(width = (2.6f - r * 0.4f).coerceAtLeast(1f) * density)
            )
        }

        // 3. Sassy Sparkling Particle Stardust Orbiting the Orb
        val sparkCount = if (mood == RoxyMood.SAVAGE) 16 else if (mood == RoxyMood.SASSY) 12 else 8
        val sparkRad = Math.toRadians(sparkRotation.toDouble()).toFloat()

        for (s in 0 until sparkCount) {
            val sparkAngle = (s.toFloat() / sparkCount) * 2f * PI.toFloat() + sparkRad * (if (s % 2 == 0) 1f else -1.2f)
            val distance = reactiveRadius * (1.15f + 0.35f * sin(sparkAngle * 3f))
            val sx = centerX + distance * cos(sparkAngle)
            val sy = centerY + distance * sin(sparkAngle)
            val sparkAlpha = (0.4f + 0.6f * sin(sparkAngle * 4f + rotationRad)).coerceIn(0.2f, 1f)

            drawCircle(
                color = mood.sparkColor.copy(alpha = sparkAlpha),
                radius = if (s % 3 == 0) 2.5f * density else 1.8f * density,
                center = Offset(sx, sy)
            )
        }
    }
}
