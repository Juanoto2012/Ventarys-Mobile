package com.ventarys.ai

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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

// --- MODELS & CONSTANTS --- //
object AppDestinations {
    const val CHAT_ROUTE = "chat"
    const val HISTORY_ROUTE = "history"
    const val SETTINGS_ROUTE = "settings"
    const val ABOUT_ROUTE = "about"
}

enum class ThemeOption { System, Light, Dark }

enum class AIProvider(val displayName: String, val baseUrl: String, val defaultModels: List<String>) {
    VENTARYS("Ventarys (Free)", "https://api.llm7.io/v1", listOf("codestral-latest", "llama-3-70b", "gpt-4o-mini")),
    GROQ("Groq Cloud", "https://api.groq.com/openai/v1", listOf("llama-3.1-8b-instant", "llama-3.1-70b-versatile", "gemma2-9b-it", "mixtral-8x7b-32768")),
    OPEN_ROUTER("OpenRouter", "https://openrouter.ai/api/v1", listOf("google/gemma-2-9b-it:free", "mistralai/mistral-7b-instruct:free", "meta-llama/llama-3-8b-instruct:free")),
    HUGGING_FACE("Hugging Face", "https://api-inference.huggingface.co/v1", listOf("mistralai/Mistral-7B-Instruct-v0.2", "meta-llama/Meta-Llama-3-8B-Instruct"))
}

data class Message(val text: String, val isFromUser: Boolean)
data class APIMessage(val role: String, val content: String)
data class ChatHistory(val id: String, val title: String, val messages: MutableList<Message>)

val SYSTEM_MESSAGE = APIMessage(
    role = "system",
    content = "Eres Ventarys AI. Responde de forma útil, precisa y concisa. Usa Markdown solo para negritas (**texto**) y listas (* elemento)."
)

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
val GptLightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color.White,
    secondary = Color(0xFF676767),
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF7F7F8),
    onSurfaceVariant = Color(0xFF424242),
    outline = Color(0xFFE5E5E5)
)

val GptDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color.Black,
    secondary = Color(0xFFB4B4B4),
    onSecondary = Color.Black,
    background = Color(0xFF212121),
    onBackground = Color(0xFFECECF1),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color(0xFFD1D1D6),
    outline = Color(0xFF3E3E3E)
)

@Composable
fun VentarysChatTheme(themeOption: ThemeOption, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeOption) {
        ThemeOption.System -> isSystemInDarkTheme()
        ThemeOption.Light -> false
        ThemeOption.Dark -> true
    }
    val colorScheme = if (useDarkTheme) GptDarkColorScheme else GptLightColorScheme
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

// --- VIEWMODEL --- //
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val gson = Gson()
    private val historyFile = File(application.filesDir, "chat_history.json")
    private val settingsFile = File(application.filesDir, "settings_v2.json")

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentProvider = MutableStateFlow(AIProvider.VENTARYS)
    val currentProvider: StateFlow<AIProvider> = _currentProvider.asStateFlow()

    private val _apiKeys = MutableStateFlow<Map<String, String>>(emptyMap())
    val apiKeys: StateFlow<Map<String, String>> = _apiKeys.asStateFlow()

    private val _selectedModels = MutableStateFlow<Map<String, String>>(
        AIProvider.values().associate { it.name to it.defaultModels.first() }
    )
    val selectedModels: StateFlow<Map<String, String>> = _selectedModels.asStateFlow()

    private val _dynamicModels = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val dynamicModels: StateFlow<Map<String, List<String>>> = _dynamicModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private var currentChatId: String? = null
    private val client = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()

    init {
        loadChatHistory()
        loadSettings()
        // Fetch models for all providers that have keys
        AIProvider.values().forEach { fetchModels(it) }
    }

    fun setProvider(provider: AIProvider) {
        _currentProvider.value = provider
        saveSettings()
        fetchModels(provider)
    }

    fun setApiKey(provider: AIProvider, key: String) {
        _apiKeys.update { it + (provider.name to key) }
        saveSettings()
        fetchModels(provider)
    }

    fun setModel(provider: AIProvider, model: String) {
        _selectedModels.update { it + (provider.name to model) }
        saveSettings()
    }

    fun fetchModels(provider: AIProvider) {
        val apiKey = _apiKeys.value[provider.name] ?: ""
        if (provider != AIProvider.VENTARYS && apiKey.isBlank()) return

        viewModelScope.launch {
            _isFetchingModels.value = true
            try {
                val fetched = withContext(Dispatchers.IO) {
                    val requestBuilder = Request.Builder()
                        .url("${provider.baseUrl}/models")
                        .get()
                    
                    if (apiKey.isNotBlank()) {
                        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val data = json.getJSONArray("data")
                        val modelList = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val id = data.getJSONObject(i).getString("id")
                            // Filter for free models if OpenRouter
                            if (provider == AIProvider.OPEN_ROUTER) {
                                if (id.endsWith(":free")) modelList.add(id)
                            } else {
                                modelList.add(id)
                            }
                        }
                        modelList.sorted()
                    } else null
                }
                if (fetched != null && fetched.isNotEmpty()) {
                    _dynamicModels.update { it + (provider.name to fetched) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isFetchingModels.value = false
            }
        }
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
        if (currentChatId == chatId) startNewChat()
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
                val responseText = generateText(apiHistory)
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
            try { historyFile.writeText(gson.toJson(_chatHistories.value)) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            if (historyFile.exists()) {
                try {
                    val type = object : TypeToken<List<ChatHistory>>() {}.type
                    _chatHistories.value = gson.fromJson(historyFile.readText(), type)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun saveSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = mapOf(
                    "provider" to _currentProvider.value.name,
                    "apiKeys" to _apiKeys.value,
                    "selectedModels" to _selectedModels.value
                )
                settingsFile.writeText(gson.toJson(settings))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            if (settingsFile.exists()) {
                try {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val settings: Map<String, Any> = gson.fromJson(settingsFile.readText(), type)
                    
                    (settings["provider"] as? String)?.let { name ->
                        _currentProvider.value = try { AIProvider.valueOf(name) } catch (e: Exception) { AIProvider.VENTARYS }
                    }
                    (settings["apiKeys"] as? Map<String, String>)?.let { keys ->
                        _apiKeys.value = keys
                    }
                    (settings["selectedModels"] as? Map<String, String>)?.let { models ->
                        _selectedModels.value = models
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteAllChats() {
        _chatHistories.value = emptyList()
        startNewChat()
        saveChatHistory()
    }

    private suspend fun generateText(history: List<APIMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                val provider = _currentProvider.value
                val apiKey = _apiKeys.value[provider.name] ?: ""
                val model = _selectedModels.value[provider.name] ?: provider.defaultModels.first()
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val messagesJson = JSONArray(history.map { JSONObject().put("role", it.role).put("content", it.content) })
                val json = JSONObject()
                    .put("messages", messagesJson)
                    .put("model", model)
                    .put("stream", false)

                val requestBuilder = Request.Builder()
                    .url("${provider.baseUrl}/chat/completions")
                    .post(json.toString().toRequestBody(mediaType))
                
                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    "Error de Proveedor (${provider.displayName}): ${response.code}\n$errorBody"
                } else {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    } else "Sin respuesta"
                }
            } catch (e: Exception) { e.message ?: "Error desconocido" }
        }
    }
}

// --- NAVIGATION HOST --- //
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
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
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
                    viewModel = viewModel,
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
fun AppDrawer(currentRoute: String, onNavigate: (String) -> Unit, onNewChat: () -> Unit, onClose: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), "Logo", Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text("Ventarys AI", style = MaterialTheme.typography.titleLarge)
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, null) },
            label = { Text("Nuevo Chat") },
            selected = false,
            onClick = { onNewChat(); onClose() }
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        DrawerItem(Icons.Outlined.Forum, "Chat", currentRoute == AppDestinations.CHAT_ROUTE) { onNavigate(AppDestinations.CHAT_ROUTE); onClose() }
        DrawerItem(Icons.Outlined.History, "Historial", currentRoute == AppDestinations.HISTORY_ROUTE) { onNavigate(AppDestinations.HISTORY_ROUTE); onClose() }
        DrawerItem(Icons.Outlined.Settings, "Ajustes", currentRoute == AppDestinations.SETTINGS_ROUTE) { onNavigate(AppDestinations.SETTINGS_ROUTE); onClose() }
        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        DrawerItem(Icons.Outlined.Info, "Acerca de", currentRoute == AppDestinations.ABOUT_ROUTE) { onNavigate(AppDestinations.ABOUT_ROUTE); onClose() }
    }
}

@Composable
fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}