package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RoxyMood
import com.example.service.RoxySessionStatus
import com.example.tools.ToolNotificationState
import com.example.ui.components.OrbVisualizer
import com.example.ui.components.RealtimeWaveformVisualizer
import com.example.ui.components.SassMoodIndicator
import com.example.ui.components.ToolNotificationBanner
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.CyberPink
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.TextMuted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.viewmodel.RoxyAiUiState
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoxyLiveScreen(
    status: RoxySessionStatus,
    audioLevel: Float,
    waveform: List<Float> = emptyList(),
    errorMessage: String?,
    activeToolNotification: ToolNotificationState?,
    onToggleSession: () -> Unit,
    mood: RoxyMood = RoxyMood.SASSY,
    onMoodChanged: (RoxyMood) -> Unit = {},
    aiUiState: RoxyAiUiState = RoxyAiUiState.Idle,
    onAskRoxyAi: (String) -> Unit = {},
    onDismissAiDialog: () -> Unit = {},
    isVoiceInputActive: Boolean = false,
    voiceTranscript: String = "",
    onStartVoiceToText: () -> Unit = {},
    onStopVoiceToText: () -> Unit = {},
    isTtsSpeaking: Boolean = false,
    onStopTts: () -> Unit = {},
    onSpeakTts: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val pulseTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == RoxySessionStatus.LISTENING || status == RoxySessionStatus.SPEAKING || status == RoxySessionStatus.THINKING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBg)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Ambient background glow circles
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyberPink.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(CyberCyan.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar Status Pill / Banner
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (activeToolNotification != null) {
                    ToolNotificationBanner(toolState = activeToolNotification)
                } else {
                    val (statusBg, statusBorder, statusText, textColor) = when (status) {
                        RoxySessionStatus.SPEAKING -> Quadruple(
                            Color(0x33EC4899),
                            CyberPink.copy(alpha = 0.6f),
                            "Roxy is talking (interrupt anytime)",
                            CyberPink
                        )
                        RoxySessionStatus.LISTENING -> Quadruple(
                            Color(0x3306B6D4),
                            CyberCyan.copy(alpha = 0.6f),
                            "Listening... speak to her",
                            CyberCyan
                        )
                        RoxySessionStatus.THINKING -> Quadruple(
                            Color(0x33A855F7),
                            CyberPurple.copy(alpha = 0.6f),
                            "Roxy is thinking...",
                            CyberPurple
                        )
                        RoxySessionStatus.CONNECTING -> Quadruple(
                            Color(0x33EAB308),
                            CyberAmber.copy(alpha = 0.6f),
                            "Connecting to Roxy Live...",
                            CyberAmber
                        )
                        RoxySessionStatus.ERROR -> Quadruple(
                            Color(0x33EF4444),
                            Color(0xFFEF4444).copy(alpha = 0.6f),
                            "Connection issue detected",
                            Color(0xFFEF4444)
                        )
                        RoxySessionStatus.DISCONNECTED -> Quadruple(
                            Color(0x221E293B),
                            Color(0x33475569),
                            "Tap orb to start voice session",
                            TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(statusBg)
                            .border(1.dp, statusBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 18.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Sassy Mood Indicator
            SassMoodIndicator(
                currentMood = mood,
                onMoodChanged = onMoodChanged
            )

            Spacer(modifier = Modifier.weight(1f))

            // Central Glowing Orb Visualizer & Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(290.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggleSession
                    )
                    .testTag("orb_mic_trigger")
            ) {
                OrbVisualizer(
                    status = status,
                    audioLevel = audioLevel,
                    mood = mood
                )

                // Central Button Hub infused with Sassy Mood Colors
                val buttonGradient = when (status) {
                    RoxySessionStatus.SPEAKING,
                    RoxySessionStatus.LISTENING,
                    RoxySessionStatus.THINKING,
                    RoxySessionStatus.CONNECTING -> Brush.linearGradient(
                        listOf(mood.primaryColor, mood.secondaryColor)
                    )
                    else -> Brush.linearGradient(
                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(buttonGradient)
                        .border(
                            width = 2.dp,
                            color = if (status != RoxySessionStatus.DISCONNECTED) mood.primaryColor.copy(alpha = 0.8f) else Color(0xFF334155),
                            shape = CircleShape
                        )
                ) {
                    when (status) {
                        RoxySessionStatus.DISCONNECTED -> {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = "Start Voice Session",
                                tint = TextSecondary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        RoxySessionStatus.SPEAKING -> {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Roxy Speaking",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        RoxySessionStatus.THINKING -> {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Roxy Thinking",
                                tint = CyberAmber,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Listening",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }

            // Error Message Banner if any
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33EF4444))
                        .border(1.dp, Color(0x66EF4444), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFECACA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            // Real-time Audio Waveform Visualizer (Reacts to both User Mic and Roxy Output)
            RealtimeWaveformVisualizer(
                status = status,
                audioLevel = audioLevel,
                waveform = waveform,
                mood = mood,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.weight(0.9f))

            // Suggested Voice Queries & Quick Gemini AI Prompts
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TAP PROMPT OR ASK GEMINI 3.8 FLASH",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val suggestions = listOf(
                    "Who is your creator?",
                    "Give me a sassy hot take",
                    "Roast my life choices playfully",
                    "Remember: I love neon cyberpunk",
                    "Why are you so confident?",
                    "What makes you better than Siri?"
                )

                suggestions.forEach { phrase ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x221E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { onAskRoxyAi(phrase) }
                    ) {
                        Text(
                            text = "\"$phrase\"",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick text prompt field for Gemini AI with Voice-to-Text
            var quickTextPrompt by remember { mutableStateOf("") }

            LaunchedEffect(voiceTranscript) {
                if (voiceTranscript.isNotBlank()) {
                    quickTextPrompt = voiceTranscript
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                // Voice-to-Text mic button
                IconButton(
                    onClick = {
                        if (isVoiceInputActive) {
                            onStopVoiceToText()
                        } else {
                            onStartVoiceToText()
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isVoiceInputActive) CyberCyan else Color(0xFF1E293B))
                        .border(1.dp, if (isVoiceInputActive) CyberCyan else Color(0xFF334155), CircleShape)
                        .testTag("voice_to_text_mic_button")
                ) {
                    Icon(
                        imageVector = if (isVoiceInputActive) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Voice to Text",
                        tint = if (isVoiceInputActive) Color.Black else CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = quickTextPrompt,
                    onValueChange = { quickTextPrompt = it },
                    placeholder = {
                        Text(
                            text = if (isVoiceInputActive) "Listening to your voice..." else "Ask Roxy anything (Voice / Text)...",
                            fontSize = 12.sp,
                            color = if (isVoiceInputActive) CyberCyan else TextMuted
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberPink,
                        unfocusedBorderColor = if (isVoiceInputActive) CyberCyan else Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (quickTextPrompt.isNotBlank()) {
                            onAskRoxyAi(quickTextPrompt.trim())
                            quickTextPrompt = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberPink)
                        .testTag("ask_roxy_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send to Roxy AI",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle status footer
            Text(
                text = "FULL DUPLEX 16KHZ PCM STREAM • 24KHZ NATIVE PLAYBACK • GEMINI 3.8 FLASH • AYUSH EDITION",
                color = TextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Gemini AI Live Streaming Response Dialog
        when (val state = aiUiState) {
            is RoxyAiUiState.Loading -> {
                AlertDialog(
                    onDismissRequest = onDismissAiDialog,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = CyberPink,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Roxy is thinking...", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Text(
                            "Streaming response via Firebase AI Logic (gemini-3.8-flash)...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = onDismissAiDialog) {
                            Text("Cancel", color = TextSecondary)
                        }
                    },
                    containerColor = Color(0xFF0F172A)
                )
            }

            is RoxyAiUiState.Streaming -> {
                AlertDialog(
                    onDismissRequest = onDismissAiDialog,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Roxy • Gemini 3.8 Flash", color = CyberCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "“${state.prompt}”",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.content,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = onDismissAiDialog) {
                            Text("Stop", color = TextSecondary)
                        }
                    },
                    containerColor = Color(0xFF0F172A)
                )
            }

            is RoxyAiUiState.Success -> {
                AlertDialog(
                    onDismissRequest = onDismissAiDialog,
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Roxy Speaks", color = CyberPink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    if (isTtsSpeaking) onStopTts() else onSpeakTts(state.content)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTtsSpeaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Speak verbal response",
                                    tint = if (isTtsSpeaking) CyberPink else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "“${state.prompt}”",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.content,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = onDismissAiDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                        ) {
                            Text("Got it, Roxy", fontSize = 12.sp)
                        }
                    },
                    containerColor = Color(0xFF0F172A)
                )
            }

            is RoxyAiUiState.Error -> {
                AlertDialog(
                    onDismissRequest = onDismissAiDialog,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini Request Failed", color = Color(0xFFEF4444), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Text(
                            text = state.message,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = onDismissAiDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                        ) {
                            Text("Dismiss")
                        }
                    },
                    containerColor = Color(0xFF0F172A)
                )
            }

            RoxyAiUiState.Idle -> { /* do nothing */ }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
