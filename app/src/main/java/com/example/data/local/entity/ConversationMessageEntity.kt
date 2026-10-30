package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores voice conversation history transcripts and tool responses.
 */
@Entity(tableName = "conversation_messages")
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "user", "roxy", "system", "tool"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = ""
)
