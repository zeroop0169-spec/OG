package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RoxyMood
import com.example.service.RoxySessionStatus
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPink
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun RealtimeWaveformVisualizer(
    status: RoxySessionStatus,
    audioLevel: Float,
    waveform: List<Float>,
    mood: RoxyMood = RoxyMood.SASSY,
    modifier: Modifier = Modifier
) {
    // Infinite transition for thinking/idle ripple animations
    val transition = rememberInfiniteTransition(label = "waveform_idle_ripple")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )

    // Beacon pulse animation
    val beaconPulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_pulse"
    )

    // Animated container border color based on who is producing audio
    val activeBorderColor by animateColorAsState(
        targetValue = when (status) {
            RoxySessionStatus.SPEAKING -> mood.primaryColor.copy(alpha = 0.65f)
            RoxySessionStatus.LISTENING -> CyberCyan.copy(alpha = 0.65f)
            RoxySessionStatus.THINKING -> CyberAmber.copy(alpha = 0.65f)
            RoxySessionStatus.CONNECTING -> CyberAmber.copy(alpha = 0.4f)
            RoxySessionStatus.ERROR -> Color(0xFFEF4444).copy(alpha = 0.5f)
            RoxySessionStatus.DISCONNECTED -> Color(0x33475569)
        },
        animationSpec = tween(durationMillis = 400),
        label = "waveform_border"
    )

    // Source description badge and LED color
    val (sourceLabel, rateLabel, beaconColor) = when (status) {
        RoxySessionStatus.SPEAKING -> Triple(
            "ROXY AUDIO OUTPUT",
            "24 kHz • HI-FI",
            mood.primaryColor
        )
        RoxySessionStatus.LISTENING -> Triple(
            "USER MIC INPUT",
            "16 kHz • PCM",
            CyberCyan
        )
        RoxySessionStatus.THINKING -> Triple(
            "AI AUDIO SYNTHESIS",
            "GEMINI LIVE",
            CyberAmber
        )
        RoxySessionStatus.CONNECTING -> Triple(
            "INITIALIZING AUDIO",
            "WEBSOCKET",
            CyberAmber
        )
        RoxySessionStatus.ERROR -> Triple(
            "AUDIO LINK ERROR",
            "OFFLINE",
            Color(0xFFEF4444)
        )
        RoxySessionStatus.DISCONNECTED -> Triple(
            "STANDBY SPECTRUM",
            "TAP ORB",
            TextMuted
        )
    }

    // Dynamic gradient for bars
    val barBrush = when (status) {
        RoxySessionStatus.SPEAKING -> Brush.verticalGradient(
            colors = listOf(mood.primaryColor, mood.secondaryColor, mood.sparkColor)
        )
        RoxySessionStatus.LISTENING -> Brush.verticalGradient(
            colors = listOf(CyberCyan, Color(0xFF38BDF8), Color(0xFF0284C7))
        )
        RoxySessionStatus.THINKING -> Brush.verticalGradient(
            colors = listOf(CyberAmber, CyberPurple, Color(0xFF9333EA))
        )
        RoxySessionStatus.CONNECTING -> Brush.verticalGradient(
            colors = listOf(CyberAmber, Color(0xFFD97706), Color(0xFFB45309))
        )
        else -> Brush.verticalGradient(
            colors = listOf(Color(0xFF475569), Color(0xFF334155), Color(0xFF1E293B))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE60F172A))
            .border(1.dp, activeBorderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("realtime_waveform_visualizer")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Live Channel Indicator & Decibel Level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing LED Beacon
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                beaconColor.copy(
                                    alpha = if (status != RoxySessionStatus.DISCONNECTED) beaconPulse else 0.4f
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = sourceLabel,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x33334155))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = rateLabel,
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Dynamic Audio Activity Meter
                val activityPct = when (status) {
                    RoxySessionStatus.SPEAKING,
                    RoxySessionStatus.LISTENING -> (audioLevel * 100f).coerceIn(0f, 100f).toInt()
                    RoxySessionStatus.THINKING -> ((sin(phase) + 1f) * 35f).toInt()
                    else -> 0
                }
                Text(
                    text = if (status == RoxySessionStatus.DISCONNECTED) "IDLE" else "$activityPct% GAIN",
                    color = if (activityPct > 35) beaconColor else TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas: 32-Bar Real-Time Mirrored Waveform Equalizer
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val barCount = 32
                val totalWidth = size.width
                val totalHeight = size.height
                val centerY = totalHeight / 2f
                val spacing = 2.5f * density
                val totalSpacing = spacing * (barCount - 1)
                val barWidth = ((totalWidth - totalSpacing) / barCount).coerceAtLeast(1.5f * density)

                // Draw central baseline subtle glow axis
                drawLine(
                    color = Color(0x33475569),
                    start = Offset(0f, centerY),
                    end = Offset(totalWidth, centerY),
                    strokeWidth = 1f * density
                )

                for (i in 0 until barCount) {
                    val rawVal = waveform.getOrElse(i) { 0.04f }

                    // Compute height with pleasant dynamics
                    val heightRatio = when (status) {
                        RoxySessionStatus.SPEAKING,
                        RoxySessionStatus.LISTENING -> {
                            // Active audio: raw multi-band value modulated with overall RMS level
                            val base = rawVal * 0.75f + audioLevel * 0.55f
                            base.coerceIn(0.08f, 1.0f)
                        }
                        RoxySessionStatus.THINKING -> {
                            // Harmonic traveling sine ripple across bars during thinking
                            val wave1 = (sin((i.toFloat() / barCount) * 4f * PI.toFloat() - phase) + 1f) * 0.5f
                            val wave2 = (sin((i.toFloat() / barCount) * 2f * PI.toFloat() + phase * 0.5f) + 1f) * 0.5f
                            ((wave1 * 0.6f + wave2 * 0.4f) * 0.75f + 0.1f).coerceIn(0.08f, 0.9f)
                        }
                        RoxySessionStatus.CONNECTING -> {
                            // Gentle breathing ripple
                            val wave = (sin(phase + i * 0.3f) + 1f) * 0.5f
                            (wave * 0.4f + 0.08f).coerceIn(0.08f, 0.5f)
                        }
                        else -> {
                            // Standby subtle idle ambient hum
                            val idleWave = (sin(phase * 0.5f + i * 0.2f) + 1f) * 0.5f
                            (idleWave * 0.12f + 0.05f).coerceIn(0.05f, 0.2f)
                        }
                    }

                    val halfBarHeight = ((totalHeight * 0.85f / 2f) * heightRatio).coerceAtLeast(2f * density)
                    val left = i * (barWidth + spacing)
                    val top = centerY - halfBarHeight
                    val barHeight = halfBarHeight * 2f

                    // Mirrored rounded bar
                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )

                    // Peak accent dot on top for active speech frequencies
                    if ((status == RoxySessionStatus.SPEAKING || status == RoxySessionStatus.LISTENING) && heightRatio > 0.45f) {
                        drawCircle(
                            color = Color.White.copy(alpha = (heightRatio * 0.9f).coerceIn(0.3f, 0.9f)),
                            radius = (barWidth * 0.45f).coerceIn(1.2f * density, 2.5f * density),
                            center = Offset(left + barWidth / 2f, top - 2.5f * density)
                        )
                    }
                }
            }
        }
    }
}
