package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RoxyMood
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SassMoodIndicator(
    currentMood: RoxyMood,
    onMoodChanged: (RoxyMood) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "sass_pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (currentMood == RoxyMood.SAVAGE || currentMood == RoxyMood.SASSY) 700 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sass_badge_scale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = currentMood.primaryColor.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 500),
        label = "sass_border_color"
    )

    val animatedGlowColor by animateColorAsState(
        targetValue = currentMood.primaryColor.copy(alpha = 0.25f),
        animationSpec = tween(durationMillis = 500),
        label = "sass_glow_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Main Sassy Mood Pill
        Box(
            modifier = Modifier
                .scale(if (currentMood == RoxyMood.SAVAGE) pulseScale else 1f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x331E1B4B),
                            animatedGlowColor,
                            Color(0x331E1B4B)
                        )
                    )
                )
                .border(1.5.dp, animatedBorderColor, RoundedCornerShape(26.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 7.dp)
                .testTag("sass_mood_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Mood Emoji with fire or sparkle indicator
                Box(contentAlignment = Alignment.Center) {
                    Text(text = currentMood.emoji, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Title & Percentage
                AnimatedContent(
                    targetState = currentMood,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "mood_text_anim"
                ) { mood ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SASS LEVEL: ${mood.sassPercentage}%",
                            color = mood.primaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${mood.title}",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Sparkle or Fire icon
                Icon(
                    imageVector = if (currentMood.sassPercentage >= 85) Icons.Default.LocalFireDepartment else Icons.Default.AutoAwesome,
                    contentDescription = "Sass Fire",
                    tint = currentMood.primaryColor,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Dynamic Sassy Subtitle / Quote
        AnimatedContent(
            targetState = currentMood,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "mood_subtitle_anim"
        ) { mood ->
            Text(
                text = "“${mood.subtitle}”",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Expanded Quick Dial Selector
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                RoxyMood.values().forEach { mood ->
                    val isSelected = mood == currentMood
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) mood.primaryColor.copy(alpha = 0.25f) else Color(0x331E293B)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) mood.primaryColor else Color(0x33475569),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onMoodChanged(mood)
                                expanded = false
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = mood.emoji, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = mood.title.split(" ").first(),
                                color = if (isSelected) mood.primaryColor else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
