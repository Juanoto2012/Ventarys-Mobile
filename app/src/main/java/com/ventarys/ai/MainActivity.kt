package com.ventarys.ai

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ventarys.ai.R
import com.ventarys.ai.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit

// --- NAVIGATION --- //
object AppDestinations {
    const val CHAT_ROUTE = "chat"
    const val HISTORY_ROUTE = "history"
    const val SETTINGS_ROUTE = "settings"
    const val ABOUT_ROUTE = "about"
}

enum class ThemeOption {
    System, Light, Dark
}

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeOptionState = remember { mutableStateOf(ThemeOption.System) }
            VentarysChatTheme(themeOption = themeOptionState.value) {
                val navController = rememberNavController()
                VentarysNavHost(navController = navController, viewModel = chatViewModel, themeOptionState = themeOptionState)
            }
        }
    }
}

// --- THEME --- //
private val CoffeePeachLightColorScheme = lightColorScheme(
    primary = Color(0xFF8D6E63), // Coffee Brown
    onPrimary = Color.White,
    background = Color(0xFFFBE9E7), // Light Peach
    onBackground = Color(0xFF4E342E), // Dark Coffee
    surface = Color(0xFFFBE9E7),
    onSurface = Color(0xFF4E342E),
    surfaceVariant = Color(0xFFD7CCC8), // User bubble bg (Light Brown)
    onSurfaceVariant = Color(0xFF4E342E), // User bubble text
    outline = Color(0xFFBCAAA4)
)

private val CoffeePeachDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D), // Peach accent
    onPrimary = Color(0xFF4E342E),
    background = Color(0xFF3E2723), // Dark Coffee
    onBackground = Color(0xFFFFF3E0), // Cream
    surface = Color(0xFF3E2723),
    onSurface = Color(0xFFFFF3E0),
    surfaceVariant = Color(0xFF5D4037), // User bubble bg (Medium Brown)
    onSurfaceVariant = Color(0xFFFFF3E0), // User bubble text
    outline = Color(0xFF795548)
)

@Composable
fun VentarysChatTheme(themeOption: ThemeOption, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeOption) {
        ThemeOption.System -> isSystemInDarkTheme()
        ThemeOption.Light -> false
        ThemeOption.Dark -> true
    }
    val colorScheme = if (useDarkTheme) CoffeePeachDarkColorScheme else CoffeePeachLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as ComponentActivity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}

// --- DATA & VIEWMODEL --- //
data class Message(val text: String, val isFromUser: Boolean)
data class APIMessage(val role: String, val content: String)
data class ChatHistory(val id: String, val title: String, val messages: MutableList<Message>)

val SYSTEM_MESSAGE = APIMessage(
    role = "system",
    content = "Eres Ventarys AI. Responde de forma útil, precisa y concisa. Usa Markdown solo para negritas (**texto**) y listas (* elemento)."
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val gson = Gson()
    private val historyFile = File(application.filesDir, "chat_history.json")

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var currentChatId: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.MINUTES)
        .build()

    init {
        loadChatHistory()
    }

    fun startNewChat() {
        currentChatId = null
        _messages.value = emptyList()
    }

    fun loadChat(chatId: String) {
        val chat = _chatHistories.value.find { it.id == chatId }
        if (chat != null) {
            currentChatId = chat.id
            _messages.value = chat.messages.toList()
        }
    }

    fun deleteChat(chatId: String) {
        _chatHistories.update { it.filterNot { chat -> chat.id == chatId } }
        saveChatHistory()
        if (currentChatId == chatId) {
            startNewChat()
        }
    }

    fun sendMessage(userInput: String) {
        viewModelScope.launch {
            if (currentChatId == null) {
                currentChatId = Date().time.toString()
                val newChat = ChatHistory(currentChatId!!, userInput, mutableListOf())
                _chatHistories.value = _chatHistories.value + newChat
            }

            val userMessage = Message(userInput, true)
            addMessageToCurrentChat(userMessage)
            _isLoading.value = true

            try {
                val apiHistory = mutableListOf(SYSTEM_MESSAGE) + _messages.value.map { m -> APIMessage(if (m.isFromUser) "user" else "assistant", m.text) }
                val responseText = generatePrivateText(apiHistory)
                addMessageToCurrentChat(Message(responseText, false))
            } catch (e: Exception) {
                addMessageToCurrentChat(Message("Error: ${e.message}", false))
            } finally {
                _isLoading.value = false
                saveChatHistory()
            }
        }
    }

    private fun addMessageToCurrentChat(message: Message) {
        _messages.value = _messages.value + message
        val chat = _chatHistories.value.find { it.id == currentChatId }
        chat?.messages?.add(message)
    }

    private fun saveChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = gson.toJson(_chatHistories.value)
                historyFile.writeText(json)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            if (historyFile.exists()) {
                try {
                    val json = historyFile.readText()
                    val type = object : TypeToken<List<ChatHistory>>() {}.type
                    _chatHistories.value = gson.fromJson(json, type)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteAllChats() {
        _chatHistories.value = emptyList()
        startNewChat()
        saveChatHistory()
    }

    private suspend fun generatePrivateText(history: List<APIMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val messagesJson = JSONArray(history.map { JSONObject().put("role", it.role).put("content", it.content) })
                val json = JSONObject()
                    .put("messages", messagesJson)
                    .put("model", "gpt-oss-20b")
                    .put("private", true)

                val requestBody = json.toString().toRequestBody(mediaType)

                val request = Request.Builder().url("https://text.pollinations.ai/").post(requestBody).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) "API Error: ${response.code} ${response.message}" else response.body?.string() ?: "No response"
            } catch (e: IOException) {
                e.message ?: "Unknown error"
            }
        }
    }
}

// --- COMPOSABLES --- //
@Composable
fun VentarysNavHost(navController: NavHostController, viewModel: ChatViewModel, themeOptionState: MutableState<ThemeOption>) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestinations.CHAT_ROUTE

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    navController.navigate(AppDestinations.CHAT_ROUTE) { launchSingleTop = true }
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        NavHost(navController = navController, startDestination = AppDestinations.CHAT_ROUTE) {
            composable(AppDestinations.CHAT_ROUTE) {
                ChatScreen(viewModel = viewModel, onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(AppDestinations.HISTORY_ROUTE) {
                HistoryScreen(viewModel = viewModel, onMenuClick = { scope.launch { drawerState.open() } }, onChatClicked = {
                    viewModel.loadChat(it)
                    navController.navigate(AppDestinations.CHAT_ROUTE)
                })
            }
            composable(AppDestinations.SETTINGS_ROUTE) {
                SettingsScreen(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    themeOption = themeOptionState.value,
                    onThemeChange = { themeOptionState.value = it },
                    onDeleteHistory = { viewModel.deleteAllChats() }
                )
            }
            composable(AppDestinations.ABOUT_ROUTE) {
                AboutScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
        }
    }
}

@Composable
fun AppDrawer(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onNewChat: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), "App Logo", Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text("Ventarys AI", style = MaterialTheme.typography.titleLarge)
        }
        Divider()
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, "New Chat") },
            label = { Text("Nuevo Chat") },
            selected = false,
            onClick = { onNewChat(); onClose() }
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Forum, "Chat") },
            label = { Text("Chat") },
            selected = currentRoute == AppDestinations.CHAT_ROUTE,
            onClick = { onNavigate(AppDestinations.CHAT_ROUTE); onClose() }
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.History, "History") },
            label = { Text("Historial") },
            selected = currentRoute == AppDestinations.HISTORY_ROUTE,
            onClick = { onNavigate(AppDestinations.HISTORY_ROUTE); onClose() }
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, "Settings") },
            label = { Text("Ajustes") },
            selected = currentRoute == AppDestinations.SETTINGS_ROUTE,
            onClick = { onNavigate(AppDestinations.SETTINGS_ROUTE); onClose() }
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Info, "About") },
            label = { Text("Acerca de") },
            selected = currentRoute == AppDestinations.ABOUT_ROUTE,
            onClick = { onNavigate(AppDestinations.ABOUT_ROUTE); onClose() }
        )
    }
}
