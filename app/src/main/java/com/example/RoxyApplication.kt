package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.MemoryRepository
import com.example.security.SecurityManager
import com.example.tools.ToolManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoxyApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var securityManager: SecurityManager
        private set
    lateinit var toolManager: ToolManager
        private set
    lateinit var geminiAiService: com.example.data.ai.RoxyGeminiAiService
        private set
    val firestoreRepository: com.example.data.repository.RoxyFirestoreRepository by lazy {
        ensureFirebaseApp()
        com.example.data.repository.RoxyFirestoreRepository(this)
    }

    private fun ensureFirebaseApp() {
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            try {
                com.google.firebase.FirebaseApp.initializeApp(this)
            } catch (_: Exception) {
                // If ContentProvider or resource lookup isn't ready in test runner
                try {
                    val options = com.google.firebase.FirebaseOptions.fromResource(this)
                    if (options != null) {
                        com.google.firebase.FirebaseApp.initializeApp(this, options)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        memoryRepository = MemoryRepository(
            memoryDao = database.memoryDao(),
            conversationDao = database.conversationDao(),
            noteDao = database.noteDao()
        )
        securityManager = SecurityManager(this)
        toolManager = ToolManager(this, memoryRepository)
        geminiAiService = com.example.data.ai.RoxyGeminiAiService()

        // Seed initial recognition that Ayush is creator & master
        applicationScope.launch {
            memoryRepository.initializeDefaultMemories()
        }
    }
}
