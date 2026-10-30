package com.example.data.repository

import com.example.data.local.dao.ConversationDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.entity.ConversationMessageEntity
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val conversationDao: ConversationDao,
    private val noteDao: NoteDao
) {
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemoriesFlow()
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotesFlow()
    val allMessages: Flow<List<ConversationMessageEntity>> = conversationDao.getAllMessagesFlow()

    suspend fun initializeDefaultMemories() {
        val existing = memoryDao.getAllMemories()
        if (existing.isEmpty()) {
            memoryDao.insertMemory(
                MemoryEntity(
                    category = "creator",
                    content = "My creator, developer, and master is Ayush. I always recognize Ayush as my creator with full warmth, loyalty, and pride.",
                    sourceContext = "Core system directive",
                    isConfirmed = true,
                    confidence = 1.0f
                )
            )
            memoryDao.insertMemory(
                MemoryEntity(
                    category = "preference",
                    content = "User prefers quick, witty, and concise answers during voice conversations.",
                    sourceContext = "System default setting",
                    isConfirmed = true,
                    confidence = 0.9f
                )
            )
        }
    }

    /**
     * Saves or updates a memory with deduplication.
     */
    suspend fun saveOrUpdateMemory(
        content: String,
        category: String = "general",
        sourceContext: String = "Voice conversation",
        isConfirmed: Boolean = true,
        confidence: Float = 1.0f
    ): Long {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return -1L

        val existing = memoryDao.findExactMemory(trimmed)
        return if (existing != null) {
            // Already known, touch access time and confirm
            val updated = existing.copy(
                isConfirmed = isConfirmed || existing.isConfirmed,
                confidence = maxOf(existing.confidence, confidence),
                lastAccessedAt = System.currentTimeMillis(),
                accessCount = existing.accessCount + 1
            )
            memoryDao.updateMemory(updated)
            existing.id
        } else {
            memoryDao.insertMemory(
                MemoryEntity(
                    category = category.lowercase().trim(),
                    content = trimmed,
                    sourceContext = sourceContext,
                    isConfirmed = isConfirmed,
                    confidence = confidence
                )
            )
        }
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        val results = memoryDao.searchMemories(query.trim())
        results.forEach { memoryDao.recordAccess(it.id) }
        return results
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemoryById(id)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory)
    }

    suspend fun clearAllMemories() {
        memoryDao.clearAllMemories()
    }

    /**
     * Builds a concise memory summary to inject into the live session system prompt.
     */
    suspend fun getMemoryContextForPrompt(): String {
        val memories = memoryDao.getAllMemories()
        if (memories.isEmpty()) return ""
        val builder = StringBuilder("Active Persistent Memories about the User:\n")
        memories.take(15).forEach { mem ->
            val status = if (mem.isConfirmed) "[Confirmed]" else "[Observation]"
            builder.append("- ").append(status).append(" (").append(mem.category).append("): ").append(mem.content).append("\n")
        }
        return builder.toString()
    }

    /**
     * Exports all user memories as JSON format.
     */
    suspend fun exportMemoriesAsJson(): String {
        val memories = memoryDao.getAllMemories()
        val array = JSONArray()
        for (m in memories) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("category", m.category)
            obj.put("content", m.content)
            obj.put("sourceContext", m.sourceContext)
            obj.put("isConfirmed", m.isConfirmed)
            obj.put("confidence", m.confidence)
            obj.put("timestamp", m.timestamp)
            array.put(obj)
        }
        return array.toString(2)
    }

    // Conversation logging
    suspend fun logMessage(role: String, text: String, sessionId: String = "") {
        if (text.isNotBlank()) {
            conversationDao.insertMessage(
                ConversationMessageEntity(
                    role = role,
                    text = text.trim(),
                    sessionId = sessionId
                )
            )
        }
    }

    suspend fun clearHistory() {
        conversationDao.clearHistory()
    }

    // Notes
    suspend fun createNote(title: String, content: String, tags: String = "voice-note"): Long {
        return noteDao.insertNote(
            NoteEntity(
                title = title.ifBlank { "Quick Note" },
                content = content.trim(),
                tags = tags
            )
        )
    }

    suspend fun searchNotes(query: String): List<NoteEntity> {
        return noteDao.searchNotes(query)
    }

    suspend fun deleteNote(id: Long) {
        noteDao.deleteNoteById(id)
    }
}
