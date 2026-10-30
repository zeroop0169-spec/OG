package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a structured memory stored by Roxy.
 * Includes user preferences, project details, personal facts, goals, or summaries.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // "preference", "fact", "project", "goal", "summary", "creator"
    val content: String,
    val sourceContext: String, // Context where fact was learned (e.g. "Voice conversation", "Manual entry")
    val isConfirmed: Boolean = true, // Distinguishes confirmed user facts from AI guesses
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 1
)
