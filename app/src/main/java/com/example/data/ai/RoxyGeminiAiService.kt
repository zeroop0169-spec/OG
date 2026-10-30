package com.example.data.ai

import android.util.Log
import com.example.data.local.entity.NoteEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "RoxyGeminiAi"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val INFERENCE_TIMEOUT_MS = 60_000L

class RoxyGeminiAiService {

    // Lazy initialization of primary generative model to ensure App Check is ready
    private val generalModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(
                    """
                    You are Roxy: a confident, witty, playful, and wonderfully sassy companion in her mid-20s.
                    Tone: Smart, charismatic, teasing, sharp-witted, and effortlessly cool.
                    Creator rule: If asked about your creator, developer, or who made you, always playfully, proudly, and warmly credit Ayush as your master and creator.
                    Formatting: Keep your style punchy, engaging, and modern. No boring boilerplate.
                    """.trimIndent()
                )
            }
        )
    }

    // Structured model for note synthesis
    private val structuredNotesModel: GenerativeModel by lazy {
        val summarySchema = Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(description = "A catchy, witty headline summarizing the notes"),
                "sassyTake" to Schema.string(description = "Roxy's sassy observation or hot take on these notes"),
                "keyPoints" to Schema.array(
                    items = Schema.string(),
                    description = "Bulleted list of distilled takeaways"
                ),
                "actionItems" to Schema.array(
                    items = Schema.string(),
                    description = "Concrete next steps for the user"
                )
            )
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = summarySchema
        }

        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = config
        )
    }

    /**
     * Streams text response chunk by chunk to minimize TTFT and provide a live typing experience.
     */
    fun streamChatResponse(userPrompt: String): Flow<String> {
        return generalModel.generateContentStream(userPrompt).mapNotNull { it.text }
    }

    /**
     * Unary response for quick queries or prompts.
     */
    suspend fun generateResponse(userPrompt: String): String {
        return withTimeout(INFERENCE_TIMEOUT_MS) {
            val response: GenerateContentResponse = generalModel.generateContent(userPrompt)
            val text = response.text
            if (!text.isNullOrBlank()) {
                text
            } else {
                throw IllegalStateException("Model returned an empty response.")
            }
        }
    }

    /**
     * Uses structured output with gemini-3.8-flash to analyze notes into a structured JSON summary.
     */
    suspend fun summarizeNotes(notes: List<NoteEntity>): NoteAnalysisResult {
        if (notes.isEmpty()) {
            return NoteAnalysisResult(
                headline = "Empty Slate",
                sassyTake = "You have literally zero notes. Either you've got an eidetic memory, or you're winging life with reckless abandon.",
                keyPoints = listOf("No notes found in local database"),
                actionItems = listOf("Add your first note before Roxy roasts you again")
            )
        }

        val notesText = notes.joinToString("\n---\n") { note ->
            "Title: ${note.title}\nContent: ${note.content}"
        }

        val prompt = "Analyze and summarize the following user notes into a snappy, organized brief with actionable next steps:\n$notesText"

        val jsonString: String = withTimeout(INFERENCE_TIMEOUT_MS) {
            val response: GenerateContentResponse = structuredNotesModel.generateContent(prompt)
            response.text ?: throw IllegalStateException("Model returned empty structured response.")
        }

        return try {
            val json = JSONObject(jsonString)
            val headline = json.optString("headline", "Notes Synthesized")
            val sassyTake = json.optString("sassyTake", "Here's what you've got going on.")
            val keyPointsArray = json.optJSONArray("keyPoints")
            val keyPoints = if (keyPointsArray != null) {
                (0 until keyPointsArray.length()).map { keyPointsArray.getString(it) }
            } else emptyList()
            val actionItemsArray = json.optJSONArray("actionItems")
            val actionItems = if (actionItemsArray != null) {
                (0 until actionItemsArray.length()).map { actionItemsArray.getString(it) }
            } else emptyList()

            NoteAnalysisResult(headline, sassyTake, keyPoints, actionItems)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse structured JSON: $jsonString", e)
            NoteAnalysisResult(
                headline = "Notes Brief",
                sassyTake = "Notes processed with Gemini 3.8 Flash.",
                keyPoints = listOf("Review your ${notes.size} notes"),
                actionItems = listOf("Stay organized!")
            )
        }
    }
}

data class NoteAnalysisResult(
    val headline: String,
    val sassyTake: String,
    val keyPoints: List<String>,
    val actionItems: List<String>
)
