package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.MemoryRepository
import com.example.tools.ToolManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoxyAssistantRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: MemoryRepository
    private lateinit var toolManager: ToolManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MemoryRepository(
            memoryDao = db.memoryDao(),
            conversationDao = db.conversationDao(),
            noteDao = db.noteDao()
        )
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("1:250514695581:android:2d02cb884c368d6d02fb74")
                .setProjectId("gen-lang-client-0693951539")
                .setApiKey("AIzaSyANqm22OLTgtI8lwkE4MS6XFCe7ofgDpHA")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
        toolManager = ToolManager(context, repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAppNameIsRoxy() {
        val appName = context.getString(R.string.app_name)
        assertEquals("ROXY", appName)
    }

    @Test
    fun testCreatorMemoryInitialization() = runBlocking {
        repository.initializeDefaultMemories()
        val memories = db.memoryDao().getAllMemories()
        assertTrue(memories.isNotEmpty())
        val creatorMem = memories.find { it.category == "creator" }
        assertNotNull(creatorMem)
        assertTrue(creatorMem!!.content.contains("Ayush"))
    }

    @Test
    fun testMemoryDeduplication() = runBlocking {
        val id1 = repository.saveOrUpdateMemory(
            content = "User loves cyberpunk synthwave",
            category = "preference"
        )
        val id2 = repository.saveOrUpdateMemory(
            content = "User loves cyberpunk synthwave",
            category = "preference"
        )
        assertEquals(id1, id2)

        val all = db.memoryDao().getAllMemories()
        val count = all.count { it.content == "User loves cyberpunk synthwave" }
        assertEquals(1, count)
    }

    @Test
    fun testMemorySearchAndExport() = runBlocking {
        repository.saveOrUpdateMemory("Likes black coffee without sugar", "preference")
        repository.saveOrUpdateMemory("Working on Android Gemini Live voice app", "project")

        val searchResults = repository.searchMemories("coffee")
        assertEquals(1, searchResults.size)
        assertTrue(searchResults[0].content.contains("coffee"))

        val exportedJson = repository.exportMemoriesAsJson()
        assertTrue(exportedJson.contains("coffee"))
        assertTrue(exportedJson.contains("Gemini Live"))
    }

    @Test
    fun testToolUrlValidationSecurity() = runBlocking {
        // Valid HTTPS URL
        val validArgs = JSONObject().apply {
            put("url", "https://youtube.com")
            put("title", "YouTube")
        }
        val validResult = toolManager.executeTool("call_1", "openWebsite", validArgs)
        assertTrue(validResult.success)

        // Invalid non-HTTPS or unsafe script protocol
        val invalidArgs = JSONObject().apply {
            put("url", "javascript:alert(1)")
        }
        val invalidResult = toolManager.executeTool("call_2", "openWebsite", invalidArgs)
        assertFalse(invalidResult.success)
    }

    @Test
    fun testToolSaveMemoryExecution() = runBlocking {
        val args = JSONObject().apply {
            put("fact", "Favorite color is electric neon pink")
            put("category", "preference")
            put("isConfirmed", true)
        }
        val result = toolManager.executeTool("call_3", "saveMemory", args)
        assertTrue(result.success)

        val memories = db.memoryDao().getAllMemories()
        assertTrue(memories.any { it.content.contains("electric neon pink") })
    }

    @Test
    fun testRoxyMoodAttributes() {
        val savage = com.example.model.RoxyMood.SAVAGE
        assertEquals(100, savage.sassPercentage)
        assertEquals("Queen Savage", savage.title)

        val sassy = com.example.model.RoxyMood.fromPercentage(85)
        assertEquals(com.example.model.RoxyMood.SASSY, sassy)

        val sweet = com.example.model.RoxyMood.fromPercentage(20)
        assertEquals(com.example.model.RoxyMood.SWEET, sweet)
    }

    @Test
    fun testWaveformCalculation() {
        val recorder = com.example.audio.AudioRecorderManager(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            onAudioChunk = {},
            onAmplitudeChanged = {},
            onWaveformChanged = {}
        )

        // Silent PCM buffer
        val silentBytes = ByteArray(1024)
        val silentBars = recorder.calculateWaveform(silentBytes, 32)
        assertEquals(32, silentBars.size)
        assertTrue(silentBars.all { it >= 0.04f })

        // Synthetic non-silent sine wave PCM buffer
        val activeBytes = ByteArray(1024)
        for (i in 0 until 512) {
            val sample = (10000.0 * kotlin.math.sin(i * 0.1)).toInt().toShort()
            activeBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            activeBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        val activeBars = recorder.calculateWaveform(activeBytes, 32)
        assertEquals(32, activeBars.size)
        assertTrue(activeBars.any { it > 0.1f })
    }

    @Test
    fun testConfigureAppCheckExecutesSafely() {
        // Must execute cleanly without uncaught exceptions
        com.example.security.configureAppCheck(context, null)
    }

    @Test
    fun testNoteAnalysisResultStructure() {
        val result = com.example.data.ai.NoteAnalysisResult(
            headline = "Witty Brief",
            sassyTake = "Looking organized, for once.",
            keyPoints = listOf("Point 1", "Point 2"),
            actionItems = listOf("Action 1")
        )
        assertEquals("Witty Brief", result.headline)
        assertEquals(2, result.keyPoints.size)
        assertEquals(1, result.actionItems.size)
    }

    @Test
    fun testFirestoreDatabaseIdConfigured() {
        val databaseId = context.getString(R.string.firestore_database_id)
        assertNotNull(databaseId)
        assertTrue(databaseId.contains("roxy"))
    }

    @Test
    fun testFirestoreDataModels() {
        val profile = com.example.data.firestore.UserProfile(
            userId = "test_user_123",
            displayName = "Ayush",
            email = "ayush@example.com",
            roxyPersonality = "SASSY"
        )
        assertEquals("test_user_123", profile.userId)
        assertEquals("Ayush", profile.displayName)

        val note = com.example.data.firestore.FirestoreNote(
            id = "note_1",
            userId = "test_user_123",
            title = "Cyberpunk Ideas",
            content = "Add neon glow to waveforms"
        )
        assertEquals("note_1", note.id)
        assertEquals("Cyberpunk Ideas", note.title)
    }
}
