package com.example.model

import androidx.compose.ui.graphics.Color

enum class RoxyMood(
    val title: String,
    val subtitle: String,
    val sassPercentage: Int,
    val emoji: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val sparkColor: Color,
    val rotationMultiplier: Float,
    val ringWaveCount: Int
) {
    SWEET(
        title = "Sweet & Chill",
        subtitle = "Attentive & relaxed... for now",
        sassPercentage = 25,
        emoji = "😇",
        primaryColor = Color(0xFF00F5FF), // Cyan
        secondaryColor = Color(0xFF38BDF8),
        sparkColor = Color(0xFFBAE6FD),
        rotationMultiplier = 0.8f,
        ringWaveCount = 4
    ),
    PLAYFUL(
        title = "Witty & Teasing",
        subtitle = "Sparkling banter & clever quips",
        sassPercentage = 60,
        emoji = "😏",
        primaryColor = Color(0xFFA855F7), // Violet
        secondaryColor = Color(0xFFFF2A85),
        sparkColor = Color(0xFFF472B6),
        rotationMultiplier = 1.25f,
        ringWaveCount = 5
    ),
    SASSY(
        title = "High Sass",
        subtitle = "Confident, sarcastic & teasingly bold",
        sassPercentage = 85,
        emoji = "💅",
        primaryColor = Color(0xFFFF2A85), // Cyber Pink
        secondaryColor = Color(0xFF9333EA),
        sparkColor = Color(0xFFFF71B2),
        rotationMultiplier = 1.7f,
        ringWaveCount = 6
    ),
    SAVAGE(
        title = "Queen Savage",
        subtitle = "Unfiltered swagger & sharp comebacks",
        sassPercentage = 100,
        emoji = "🔥",
        primaryColor = Color(0xFFFF0055), // Fiery Neon Crimson
        secondaryColor = Color(0xFFFF7A00), // Electric Orange
        sparkColor = Color(0xFFFFE600),
        rotationMultiplier = 2.2f,
        ringWaveCount = 8
    );

    companion object {
        fun fromPercentage(pct: Int): RoxyMood = when {
            pct < 40 -> SWEET
            pct < 75 -> PLAYFUL
            pct < 95 -> SASSY
            else -> SAVAGE
        }
    }
}
