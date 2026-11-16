package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.KarsaFirestoreRepository
import com.example.security.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AiSearchModalSheet
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodeWebStudioScreen
import com.example.ui.screens.GitHubScreen
import com.example.ui.screens.ImageLabScreen
import com.example.ui.screens.LearnScreen
import com.example.ui.screens.SettingsModalSheet
import com.example.ui.theme.KarsaAiTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.CodeStudioViewModel
import com.example.ui.viewmodel.GitHubViewModel
import com.example.ui.viewmodel.ImageLabViewModel
import com.example.ui.viewmodel.LearnViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class KarsaTab(val route: String, val label: String, val icon: ImageVector) {
    CHAT("chat", "Asisten", Icons.Default.AutoAwesome),
    LEARN("learn", "Belajar", Icons.Default.School),
    STUDIO("studio", "Studio", Icons.Default.Code),
    GITHUB("github", "GitHub", Icons.Default.Terminal),
    IMAGELAB("imagelab", "Gambar", Icons.Default.Image)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Step 1: Configure App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val prefsRepo = remember(context) { UserPreferencesRepository(context.applicationContext) }
            val settings by prefsRepo.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

            KarsaAiTheme(darkTheme = settings.isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        prefsRepo = prefsRepo,
                        settings = settings
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    prefsRepo: UserPreferencesRepository,
    settings: UserSettings,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            KarsaWorkspaceShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Pengguna Karsa",
                prefsRepo = prefsRepo,
                settings = settings
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KarsaWorkspaceShell(
    currentUserId: String,
    userDisplayName: String,
    prefsRepo: UserPreferencesRepository,
    settings: UserSettings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val firestoreRepo = remember(currentUserId) {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        KarsaFirestoreRepository(FirebaseFirestore.getInstance(dbId), Firebase.auth)
    }
    val aiRepo = remember { GeminiAiRepository() }

    LaunchedEffect(currentUserId, settings.preferredLanguage) {
        firestoreRepo.ensureUserProfile(userDisplayName, settings.preferredLanguage)
    }

    val chatViewModel: ChatViewModel = viewModel(
        key = "chat_$currentUserId",
        factory = ChatViewModel.provideFactory(firestoreRepo, aiRepo, context)
    )
    val learnViewModel: LearnViewModel = viewModel(
        key = "learn_$currentUserId",
        factory = LearnViewModel.provideFactory(firestoreRepo, aiRepo)
    )
    val studioViewModel: CodeStudioViewModel = viewModel(
        key = "studio_$currentUserId",
        factory = CodeStudioViewModel.provideFactory(firestoreRepo, aiRepo)
    )
    val githubViewModel: GitHubViewModel = viewModel(
        key = "github_$currentUserId",
        factory = GitHubViewModel.provideFactory(firestoreRepo, aiRepo)
    )
    val imageLabViewModel: ImageLabViewModel = viewModel(
        key = "imglab_$currentUserId",
        factory = ImageLabViewModel.provideFactory(aiRepo)
    )
    val searchViewModel: SearchViewModel = viewModel(
        key = "search_$currentUserId",
        factory = SearchViewModel.provideFactory(aiRepo)
    )

    LaunchedEffect(settings.preferredLanguage) {
        chatViewModel.setLanguage(settings.preferredLanguage)
    }

    var currentTab by remember { mutableStateOf(KarsaTab.CHAT) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val conversations by chatViewModel.conversations.collectAsStateWithLifecycle()
    val lessons by learnViewModel.lessons.collectAsStateWithLifecycle()
    val projects by studioViewModel.savedProjects.collectAsStateWithLifecycle()
    val bookmarks by githubViewModel.bookmarks.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Karsa AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSearchSheet = true },
                            modifier = Modifier.testTag("open_ai_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Pencarian Berbasis AI")
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        KarsaTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        KarsaTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                val openInStudio: (String, String) -> Unit = { lang, code ->
                    studioViewModel.loadCodeFromExternal("Kode dari Karsa", lang, code)
                    currentTab = KarsaTab.STUDIO
                }

                when (currentTab) {
                    KarsaTab.CHAT -> ChatScreen(
                        viewModel = chatViewModel,
                        onOpenCodeInStudio = openInStudio,
                        modifier = Modifier.weight(1f)
                    )
                    KarsaTab.LEARN -> LearnScreen(
                        viewModel = learnViewModel,
                        responseLanguage = settings.preferredLanguage,
                        onOpenCodeInStudio = openInStudio,
                        modifier = Modifier.weight(1f)
                    )
                    KarsaTab.STUDIO -> CodeWebStudioScreen(
                        viewModel = studioViewModel,
                        responseLanguage = settings.preferredLanguage,
                        modifier = Modifier.weight(1f)
                    )
                    KarsaTab.GITHUB -> GitHubScreen(
                        viewModel = githubViewModel,
                        githubToken = settings.githubToken,
                        responseLanguage = settings.preferredLanguage,
                        onOpenCodeInStudio = openInStudio,
                        onOpenSettingsForToken = { showSettingsSheet = true },
                        modifier = Modifier.weight(1f)
                    )
                    KarsaTab.IMAGELAB -> ImageLabScreen(
                        viewModel = imageLabViewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showSearchSheet) {
        AiSearchModalSheet(
            viewModel = searchViewModel,
            conversations = conversations,
            lessons = lessons,
            projects = projects,
            bookmarks = bookmarks,
            responseLanguage = settings.preferredLanguage,
            onSelectResult = { item ->
                when (item.sourceType) {
                    "chat" -> {
                        val conv = conversations.firstOrNull { it.id == item.itemId }
                        chatViewModel.selectConversation(item.itemId, conv?.mode)
                        currentTab = KarsaTab.CHAT
                    }
                    "lesson" -> {
                        val lesson = lessons.firstOrNull { it.id == item.itemId }
                        if (lesson != null) learnViewModel.selectLesson(lesson)
                        currentTab = KarsaTab.LEARN
                    }
                    "project" -> {
                        val proj = projects.firstOrNull { it.id == item.itemId }
                        if (proj != null) studioViewModel.openSavedProject(proj)
                        currentTab = KarsaTab.STUDIO
                    }
                    "github" -> {
                        val bm = bookmarks.firstOrNull { it.id == item.itemId }
                        if (bm != null) githubViewModel.openBookmark(bm)
                        currentTab = KarsaTab.GITHUB
                    }
                }
            },
            onDismiss = { showSearchSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsModalSheet(
            settings = settings,
            onToggleDarkTheme = { enabled ->
                scope.launch { prefsRepo.setDarkTheme(enabled) }
            },
            onSelectLanguage = { lang ->
                scope.launch { prefsRepo.setPreferredLanguage(lang) }
            },
            onSaveGitHubToken = { token ->
                scope.launch { prefsRepo.setGitHubToken(token) }
            },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
