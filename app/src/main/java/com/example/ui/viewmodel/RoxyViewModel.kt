package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.RoxyApplication
import com.example.data.local.entity.MemoryEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.repository.MemoryRepository
import com.example.security.AssistantConfig
import com.example.security.SecurityManager
import com.example.service.RoxySessionStatus
import com.example.service.RoxyVoiceService
import com.example.tools.ToolNotificationState
import android.util.Log
import com.example.data.ai.NoteAnalysisResult
import com.example.data.ai.RoxyGeminiAiService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RoxyAiUiState {
    data object Idle : RoxyAiUiState
    data object Loading : RoxyAiUiState
    data class Streaming(val prompt: String, val content: String) : RoxyAiUiState
    data class Success(val prompt: String, val content: String) : RoxyAiUiState
    data class Error(val message: String) : RoxyAiUiState
}

sealed interface NoteAnalysisUiState {
    data object Idle : NoteAnalysisUiState
    data object Loading : NoteAnalysisUiState
    data class Success(val result: NoteAnalysisResult) : NoteAnalysisUiState
    data class Error(val message: String) : NoteAnalysisUiState
}

class RoxyViewModel(
    private val memoryRepository: MemoryRepository,
    private val securityManager: SecurityManager,
    private val geminiAiService: RoxyGeminiAiService,
    private val firestoreRepository: com.example.data.repository.RoxyFirestoreRepository,
    private val speechEngine: com.example.audio.RoxySpeechEngine? = null
) : ViewModel() {

    // TextToSpeech & Voice-to-Text state
    val isTtsSpeaking: StateFlow<Boolean> = speechEngine?.isSpeaking ?: MutableStateFlow(false)
    val isVoiceInputActive: StateFlow<Boolean> = speechEngine?.isListening ?: MutableStateFlow(false)
    val voiceTranscript: StateFlow<String> = speechEngine?.recognizedText ?: MutableStateFlow("")

    fun startVoiceToText() {
        speechEngine?.startListening()
    }

    fun stopVoiceToText() {
        speechEngine?.stopListening()
    }

    fun speakText(text: String) {
        speechEngine?.speak(text)
    }

    fun stopSpeaking() {
        speechEngine?.stopSpeaking()
    }

    // Status from foreground service
    val sessionStatus: StateFlow<RoxySessionStatus> = RoxyVoiceService.sessionStatus
    val audioLevel: StateFlow<Float> = RoxyVoiceService.audioVisualizerLevel
    val waveform: StateFlow<List<Float>> = RoxyVoiceService.waveformBars
    val errorMessage: StateFlow<String?> = RoxyVoiceService.errorMessage
    val activeToolNotification: StateFlow<ToolNotificationState?> = MutableStateFlow<ToolNotificationState?>(null).apply {
        viewModelScope.launch {
            RoxyVoiceService.activeToolNotification.collect { notif ->
                value = notif
                kotlinx.coroutines.delay(4500)
                if (value == notif) {
                    value = null
                }
            }
        }
    }

    // Sassy Mood State
    private val _roxyMood = MutableStateFlow(com.example.model.RoxyMood.SASSY)
    val roxyMood: StateFlow<com.example.model.RoxyMood> = _roxyMood.asStateFlow()

    fun setMood(mood: com.example.model.RoxyMood) {
        _roxyMood.value = mood
    }


    // Memories State & Filter
    val memorySearchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow("all")

    val memories: StateFlow<List<MemoryEntity>> = combine(
        memoryRepository.allMemories,
        memorySearchQuery,
        selectedCategoryFilter
    ) { all, query, category ->
        all.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.content.contains(query, ignoreCase = true) ||
                    item.category.contains(query, ignoreCase = true)
            val matchesCategory = category == "all" ||
                    (category == "confirmed" && item.isConfirmed) ||
                    (category == "observation" && !item.isConfirmed) ||
                    item.category.equals(category, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes State
    val notes: StateFlow<List<NoteEntity>> = memoryRepository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Configuration
    private val _configState = MutableStateFlow(securityManager.getConfig())
    val configState: StateFlow<AssistantConfig> = _configState.asStateFlow()

    fun toggleSession(context: Context, hasRecordPermission: Boolean, onPermissionRequired: () -> Unit) {
        if (!hasRecordPermission) {
            onPermissionRequired()
            return
        }

        if (sessionStatus.value == RoxySessionStatus.DISCONNECTED || sessionStatus.value == RoxySessionStatus.ERROR) {
            val intent = Intent(context, RoxyVoiceService::class.java).apply {
                action = RoxyVoiceService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        } else {
            val intent = Intent(context, RoxyVoiceService::class.java).apply {
                action = RoxyVoiceService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    fun stopSession(context: Context) {
        val intent = Intent(context, RoxyVoiceService::class.java).apply {
            action = RoxyVoiceService.ACTION_STOP
        }
        context.startService(intent)
    }

    // Memory actions
    fun addMemory(content: String, category: String, isConfirmed: Boolean) {
        viewModelScope.launch {
            memoryRepository.saveOrUpdateMemory(
                content = content,
                category = category,
                sourceContext = "Manual user vault entry",
                isConfirmed = isConfirmed
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            memoryRepository.updateMemory(memory)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
        }
    }

    suspend fun getExportJson(): String {
        return memoryRepository.exportMemoriesAsJson()
    }

    // Note actions
    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            memoryRepository.createNote(title, content)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteNote(id)
        }
    }

    // Settings
    fun saveCustomApiKey(key: String) {
        if (key.isBlank()) {
            securityManager.clearCustomApiKey()
        } else {
            securityManager.setCustomApiKey(key)
        }
        _configState.value = securityManager.getConfig()
    }

    fun setModel(model: String) {
        securityManager.setSelectedModel(model)
        _configState.value = securityManager.getConfig()
    }

    fun setVoice(voice: String) {
        securityManager.setSelectedVoice(voice)
        _configState.value = securityManager.getConfig()
    }

    fun getMaskedApiKey(): String = securityManager.getMaskedApiKey()
    fun hasValidApiKey(): Boolean = securityManager.hasValidApiKey()

    // Firebase AI Logic State
    private val _aiUiState = MutableStateFlow<RoxyAiUiState>(RoxyAiUiState.Idle)
    val aiUiState: StateFlow<RoxyAiUiState> = _aiUiState.asStateFlow()

    private val _notesAnalysisState = MutableStateFlow<NoteAnalysisUiState>(NoteAnalysisUiState.Idle)
    val notesAnalysisState: StateFlow<NoteAnalysisUiState> = _notesAnalysisState.asStateFlow()

    private val _firebaseTestStatus = MutableStateFlow<String?>(null)
    val firebaseTestStatus: StateFlow<String?> = _firebaseTestStatus.asStateFlow()

    fun askRoxyAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _aiUiState.value = RoxyAiUiState.Loading
            val accumulated = StringBuilder()
            try {
                geminiAiService.streamChatResponse(prompt).collect { chunk ->
                    accumulated.append(chunk)
                    _aiUiState.value = RoxyAiUiState.Streaming(prompt, accumulated.toString())
                }
                if (accumulated.isNotBlank()) {
                    val fullResponse = accumulated.toString()
                    _aiUiState.value = RoxyAiUiState.Success(prompt, fullResponse)
                    speechEngine?.speak(fullResponse)
                } else {
                    _aiUiState.value = RoxyAiUiState.Error("Model returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("RoxyViewModel", "Gemini AI generation timed out", e)
                _aiUiState.value = RoxyAiUiState.Error("Generation timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoxyViewModel", "Gemini AI generation failed", e)
                _aiUiState.value = RoxyAiUiState.Error(e.localizedMessage ?: "AI generation failed.")
            }
        }
    }

    fun summarizeNotesAi() {
        viewModelScope.launch {
            _notesAnalysisState.value = NoteAnalysisUiState.Loading
            try {
                val currentNotes = notes.value
                val result = geminiAiService.summarizeNotes(currentNotes)
                _notesAnalysisState.value = NoteAnalysisUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e("RoxyViewModel", "Notes analysis timed out", e)
                _notesAnalysisState.value = NoteAnalysisUiState.Error("Notes analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoxyViewModel", "Notes analysis failed", e)
                _notesAnalysisState.value = NoteAnalysisUiState.Error(e.localizedMessage ?: "Notes analysis failed.")
            }
        }
    }

    fun testFirebaseAiConnection() {
        viewModelScope.launch {
            _firebaseTestStatus.value = "Testing Firebase AI Logic (gemini-3.8-flash)..."
            val startTime = System.currentTimeMillis()
            try {
                val reply = geminiAiService.generateResponse("Reply in one short sassy sentence confirming ROXY AI is online.")
                val latency = System.currentTimeMillis() - startTime
                _firebaseTestStatus.value = "Online (${latency}ms): \"$reply\""
            } catch (e: TimeoutCancellationException) {
                _firebaseTestStatus.value = "Error: Timed out after 60 seconds"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoxyViewModel", "Firebase AI test connection failed", e)
                _firebaseTestStatus.value = "Error: ${e.localizedMessage ?: "Unknown failure"}"
            }
        }
    }

    fun dismissAiDialog() {
        speechEngine?.stopSpeaking()
        _aiUiState.value = RoxyAiUiState.Idle
    }

    fun dismissNotesAnalysis() {
        _notesAnalysisState.value = NoteAnalysisUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine?.stopSpeaking()
        speechEngine?.stopListening()
    }

    // Firebase Firestore Sync
    private val _firestoreSyncStatus = MutableStateFlow<String?>("Connected to Firebase Firestore")
    val firestoreSyncStatus: StateFlow<String?> = _firestoreSyncStatus.asStateFlow()

    fun syncWithFirestore() {
        viewModelScope.launch {
            val user = com.google.firebase.Firebase.auth.currentUser
            if (user == null) {
                _firestoreSyncStatus.value = "Sign-in required to sync with Firestore."
                return@launch
            }
            _firestoreSyncStatus.value = "Syncing with Firestore..."
            try {
                // Save user profile
                firestoreRepository.saveUserProfile(
                    com.example.data.firestore.UserProfile(
                        userId = user.uid,
                        displayName = user.displayName ?: "Ayush's Guest",
                        email = user.email ?: "",
                        roxyPersonality = _roxyMood.value.name
                    )
                )

                // Sync current notes up to Firestore
                val currentNotes = notes.value
                firestoreRepository.syncLocalNotesToCloud(user.uid, currentNotes)

                // Sync confirmed memories
                val currentMemories = memories.value
                currentMemories.forEach { mem ->
                    firestoreRepository.saveMemory(
                        user.uid,
                        com.example.data.firestore.FirestoreMemory(
                            id = "mem_${mem.id}",
                            userId = user.uid,
                            summary = mem.content,
                            keyFacts = mem.sourceContext,
                            mood = _roxyMood.value.name
                        )
                    )
                }
                _firestoreSyncStatus.value = "All data securely synced to Firestore (${currentNotes.size} notes, ${currentMemories.size} memories)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RoxyViewModel", "Firestore sync failed", e)
                _firestoreSyncStatus.value = "Sync error: ${e.localizedMessage ?: "Check connection"}"
            }
        }
    }

    fun signOut() {
        com.google.firebase.Firebase.auth.signOut()
    }

    class Factory(private val application: RoxyApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoxyViewModel(
                memoryRepository = application.memoryRepository,
                securityManager = application.securityManager,
                geminiAiService = application.geminiAiService,
                firestoreRepository = application.firestoreRepository,
                speechEngine = application.speechEngine
            ) as T
        }
    }
}
