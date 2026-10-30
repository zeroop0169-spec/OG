package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.configureAppCheck
import com.example.ui.auth.RoxyAuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.screens.MemoryVaultScreen
import com.example.ui.screens.NotesAndToolsScreen
import com.example.ui.screens.RoxyLiveScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.CyberPink
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.RoxyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

enum class RoxyTab(val label: String) {
    LIVE("Live"),
    VAULT("Vault"),
    NOTES("Notes"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: RoxyViewModel by viewModels {
        RoxyViewModel.Factory(application as RoxyApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentUser by Firebase.auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

                val user = currentUser
                if (user == null) {
                    RoxyAuthScreen(
                        onAuthSuccess = {
                            // Auth state change will trigger recomposition
                        }
                    )
                } else {
                    MainContent(viewModel = viewModel, currentUser = user)
                }
            }
        }
    }
}

@Composable
fun MainContent(viewModel: RoxyViewModel, currentUser: FirebaseUser) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(RoxyTab.LIVE) }

    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasRecordPermission = recordAudioGranted
        if (recordAudioGranted) {
            viewModel.toggleSession(context, true) {}
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for Roxy voice conversations.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Auto-request notifications on Android 13+
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // BackHandler: navigate back to Live tab if currently on a sub-screen
    BackHandler(enabled = currentTab != RoxyTab.LIVE) {
        currentTab = RoxyTab.LIVE
    }

    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val activeToolNotif by viewModel.activeToolNotification.collectAsStateWithLifecycle()

    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.memorySearchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val config by viewModel.configState.collectAsStateWithLifecycle()
    val roxyMood by viewModel.roxyMood.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val notesAnalysisState by viewModel.notesAnalysisState.collectAsStateWithLifecycle()
    val firebaseTestStatus by viewModel.firebaseTestStatus.collectAsStateWithLifecycle()
    val firestoreSyncStatus by viewModel.firestoreSyncStatus.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0A0D14),
                contentColor = TextSecondary,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = currentTab == RoxyTab.LIVE,
                    onClick = { currentTab = RoxyTab.LIVE },
                    icon = { Icon(Icons.Default.Bolt, contentDescription = "Live") },
                    label = { Text("Live", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberPink,
                        selectedTextColor = CyberPink,
                        indicatorColor = CyberPink.copy(alpha = 0.15f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_live")
                )

                NavigationBarItem(
                    selected = currentTab == RoxyTab.VAULT,
                    onClick = { currentTab = RoxyTab.VAULT },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "Vault") },
                    label = { Text("Vault", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_vault")
                )

                NavigationBarItem(
                    selected = currentTab == RoxyTab.NOTES,
                    onClick = { currentTab = RoxyTab.NOTES },
                    icon = { Icon(Icons.Default.Description, contentDescription = "Notes") },
                    label = { Text("Notes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_notes")
                )

                NavigationBarItem(
                    selected = currentTab == RoxyTab.SETTINGS,
                    onClick = { currentTab = RoxyTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberPink,
                        selectedTextColor = CyberPink,
                        indicatorColor = CyberPink.copy(alpha = 0.15f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        containerColor = CyberDarkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                RoxyTab.LIVE -> {
                    RoxyLiveScreen(
                        status = sessionStatus,
                        audioLevel = audioLevel,
                        waveform = waveform,
                        errorMessage = errorMessage,
                        activeToolNotification = activeToolNotif,
                        mood = roxyMood,
                        onMoodChanged = { viewModel.setMood(it) },
                        aiUiState = aiUiState,
                        onAskRoxyAi = { viewModel.askRoxyAi(it) },
                        onDismissAiDialog = { viewModel.dismissAiDialog() },
                        onToggleSession = {
                            viewModel.toggleSession(
                                context = context,
                                hasRecordPermission = hasRecordPermission,
                                onPermissionRequired = {
                                    val reqs = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        reqs.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(reqs.toTypedArray())
                                }
                            )
                        }
                    )
                }

                RoxyTab.VAULT -> {
                    MemoryVaultScreen(
                        memories = memories,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.memorySearchQuery.value = it },
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.selectedCategoryFilter.value = it },
                        onAddMemory = { content, cat, conf -> viewModel.addMemory(content, cat, conf) },
                        onUpdateMemory = { viewModel.updateMemory(it) },
                        onDeleteMemory = { viewModel.deleteMemory(it) },
                        onExportMemories = { viewModel.getExportJson() }
                    )
                }

                RoxyTab.NOTES -> {
                    NotesAndToolsScreen(
                        notes = notes,
                        onAddNote = { title, content -> viewModel.addNote(title, content) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        notesAnalysisState = notesAnalysisState,
                        onSummarizeNotes = { viewModel.summarizeNotesAi() },
                        onDismissNotesAnalysis = { viewModel.dismissNotesAnalysis() }
                    )
                }

                RoxyTab.SETTINGS -> {
                    SettingsScreen(
                        config = config,
                        hasValidKey = viewModel.hasValidApiKey(),
                        maskedKey = viewModel.getMaskedApiKey(),
                        onSaveApiKey = { viewModel.saveCustomApiKey(it) },
                        onSelectModel = { viewModel.setModel(it) },
                        onSelectVoice = { viewModel.setVoice(it) },
                        firebaseTestStatus = firebaseTestStatus,
                        onTestFirebaseAi = { viewModel.testFirebaseAiConnection() },
                        firestoreSyncStatus = firestoreSyncStatus,
                        onSyncToFirestore = { viewModel.syncWithFirestore() },
                        onSignOut = { viewModel.signOut() },
                        userEmail = currentUser.email,
                        userDisplayName = currentUser.displayName
                    )
                }
            }
        }
    }
}
