package com.ventarys.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- MODELS & CONSTANTS --- //
object AppDestinations {
    const val CHAT_ROUTE = "chat"
    const val HISTORY_ROUTE = "history"
    const val SETTINGS_ROUTE = "settings"
    const val ABOUT_ROUTE = "about"
}

enum class ThemeOption { System, Light, Dark }

enum class AIProvider(
    val displayName: String,
    val baseUrl: String,
    val defaultModels: List<String>,
    val apiKeyUrl: String? = null,
    val timeoutSeconds: Long = 60L,
    val supportsVision: Boolean = false
) {
    VENTARYS("Ventarys (Free)", "https://api.llm7.io/v1", listOf("codestral-latest", "llama-3-70b", "gpt-4o-mini"), null, 60L, true),
    GROQ("Groq Cloud", "https://api.groq.com/openai/v1", listOf("llama-3.1-8b-instant", "llama-3.1-70b-versatile", "gemma2-9b-it", "mixtral-8x7b-32768", "llama-3.2-11b-vision-preview"), "https://console.groq.com/keys", 30L, true),
    AQUA_AI("Aqua AI", "https://api.aquadevs.com/v1", listOf("gpt-4o-mini", "gpt-4o", "meta-llama-3.1-70b"), "https://aquadevs.com/dashboard", 300L, true),
    HUGGING_FACE("Hugging Face", "https://api-inference.huggingface.co/v1", listOf("mistralai/Mistral-7B-Instruct-v0.2", "meta-llama/Meta-Llama-3-8B-Instruct"), "https://huggingface.co/settings/tokens", 60L, false)
}

data class ChatFile(
    val name: String,
    val type: String,
    val size: Long,
    val base64: String? = null
)

data class Message(
    val role: String,
    val content: String,
    val files: List<ChatFile>? = null
) {
    val isFromUser: Boolean get() = role == "user"
}

data class APIMessage(val role: String, val content: Any)

data class ChatHistory(
    val id: String,
    val title: String,
    val messages: MutableList<Message>
)

// Backup structure to match web version
data class BackupChat(
    val title: String,
    val messages: List<Message>
)

data class BackupData(
    val allChats: Map<String, BackupChat>,
    val currentChatId: String?
)

val SYSTEM_MESSAGE = APIMessage(
    role = "system",
    content = "Eres Ventarys AI. Responde de forma útil, precisa y concisa. Usa Markdown solo para negritas (**texto**) y listas (* elemento)."
)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private val chatViewModel: ChatViewModel by viewModels()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        enableEdgeToEdge()
        setContent {
            val themeOptionState = remember { mutableStateOf(ThemeOption.System) }
            VentarysChatTheme(themeOption = themeOptionState.value) {
                val navController = rememberNavController()
                VentarysNavHost(
                    navController = navController,
                    viewModel = chatViewModel,
                    themeOptionState = themeOptionState,
                    onSpeak = { text -> speak(text) }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun VentarysNavHost(
    navController: NavHostController,
    viewModel: ChatViewModel,
    themeOptionState: MutableState<ThemeOption>,
    onSpeak: (String) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp,
                windowInsets = WindowInsets.systemBars
            ) {
                // Header - "Old style" but modern and full
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = "Logo",
                                modifier = Modifier.size(48.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            text = "Ventarys AI",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "IA Generativa Multimodal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "MENÚ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Nuevo Chat", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.startNewChat()
                        navController.navigate(AppDestinations.CHAT_ROUTE) {
                            popUpTo(AppDestinations.CHAT_ROUTE) { inclusive = true }
                        }
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Chat Actual", fontWeight = FontWeight.Medium) },
                    selected = currentRoute == AppDestinations.CHAT_ROUTE,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AppDestinations.CHAT_ROUTE) {
                            popUpTo(AppDestinations.CHAT_ROUTE) { inclusive = true }
                        }
                    },
                    icon = { 
                        Icon(
                            if (currentRoute == AppDestinations.CHAT_ROUTE) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat, 
                            null 
                        ) 
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                
                NavigationDrawerItem(
                    label = { Text("Historial", fontWeight = FontWeight.Medium) },
                    selected = currentRoute == AppDestinations.HISTORY_ROUTE,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AppDestinations.HISTORY_ROUTE)
                    },
                    icon = { 
                        Icon(
                            if (currentRoute == AppDestinations.HISTORY_ROUTE) Icons.Default.History else Icons.Outlined.History, 
                            null 
                        ) 
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                
                Spacer(Modifier.weight(1f))
                
                HorizontalDivider(
                    Modifier.padding(horizontal = 28.dp, vertical = 8.dp), 
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                
                NavigationDrawerItem(
                    label = { Text("Ajustes", fontWeight = FontWeight.Medium) },
                    selected = currentRoute == AppDestinations.SETTINGS_ROUTE,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AppDestinations.SETTINGS_ROUTE)
                    },
                    icon = { 
                        Icon(
                            if (currentRoute == AppDestinations.SETTINGS_ROUTE) Icons.Default.Settings else Icons.Outlined.Settings, 
                            null 
                        ) 
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                NavigationDrawerItem(
                    label = { Text("Acerca de", fontWeight = FontWeight.Medium) },
                    selected = currentRoute == AppDestinations.ABOUT_ROUTE,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AppDestinations.ABOUT_ROUTE)
                    },
                    icon = { 
                        Icon(
                            if (currentRoute == AppDestinations.ABOUT_ROUTE) Icons.Default.Info else Icons.Outlined.Info, 
                            null 
                        ) 
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                Spacer(Modifier.height(16.dp))
            }
        }
    ) {
        NavHost(navController = navController, startDestination = AppDestinations.CHAT_ROUTE) {
            composable(AppDestinations.CHAT_ROUTE) {
                ChatScreen(
                    viewModel = viewModel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSpeak = onSpeak
                )
            }
            composable(AppDestinations.HISTORY_ROUTE) {
                HistoryScreen(
                    viewModel = viewModel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onChatClicked = { chatId ->
                        viewModel.loadChat(chatId)
                        navController.navigate(AppDestinations.CHAT_ROUTE) {
                            popUpTo(AppDestinations.CHAT_ROUTE) { inclusive = true }
                        }
                    }
                )
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
                AboutScreen(
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
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
    outline = Color(0xFFE5E5E5),
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF000000)
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
    outline = Color(0xFF3E3E3E),
    primaryContainer = Color(0xFF3E3E3E),
    onPrimaryContainer = Color(0xFFFFFFFF)
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
    private val historyFile = File(application.filesDir, "chat_history_v2.json")
    private val settingsFile = File(application.filesDir, "settings_v3.json")

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

    private val _selectedModels = MutableStateFlow(
        AIProvider.entries.associate { it.name to it.defaultModels.first() }
    )
    val selectedModels: StateFlow<Map<String, String>> = _selectedModels.asStateFlow()

    private val _dynamicModels = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val dynamicModels: StateFlow<Map<String, List<String>>> = _dynamicModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    private var currentChatId: String? = null
    private val baseClient = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()

    init {
        loadChatHistory()
        loadSettings()
        AIProvider.entries.forEach { fetchModels(it) }
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

                    val client = baseClient.newBuilder()
                        .callTimeout(30, TimeUnit.SECONDS)
                        .build()

                    val response = client.newCall(requestBuilder.build()).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val data = json.getJSONArray("data")
                        val modelList = mutableListOf<String>()
                        for (i in 0 until data.length()) {
                            val id = data.getJSONObject(i).getString("id")
                            modelList.add(id)
                        }
                        modelList.sorted()
                    } else null
                }
                if (!fetched.isNullOrEmpty()) {
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

    fun sendMessage(userInput: String, attachedFiles: List<Uri> = emptyList()) {
        viewModelScope.launch {
            if (currentChatId == null) {
                currentChatId = Date().time.toString()
                val title = if (userInput.isNotBlank()) {
                    if (userInput.length > 30) userInput.take(30) + "..." else userInput
                } else {
                    "Chat con archivos"
                }
                val newChat = ChatHistory(currentChatId!!, title, mutableListOf())
                _chatHistories.value += newChat
            }

            val chatFiles = attachedFiles.map { uri ->
                val info = getFileInfo(uri)
                ChatFile(
                    name = info.name,
                    type = info.type,
                    size = info.size,
                    base64 = if (info.type.startsWith("image/")) encodeImageToBase64(uri) else null
                )
            }

            val userMessage = Message("user", userInput, if (chatFiles.isEmpty()) null else chatFiles)
            addMessageToCurrentChat(userMessage)
            _isLoading.value = true

            try {
                val provider = _currentProvider.value
                val apiHistory = mutableListOf<APIMessage>()
                apiHistory.add(SYSTEM_MESSAGE)
                
                _messages.value.dropLast(1).forEach { m ->
                    apiHistory.add(APIMessage(m.role, m.content))
                }

                if (provider.supportsVision && chatFiles.any { it.type.startsWith("image/") }) {
                    val contentArray = JSONArray()
                    if (userInput.isNotBlank()) {
                        contentArray.put(JSONObject().put("type", "text").put("text", userInput))
                    }
                    
                    chatFiles.filter { it.type.startsWith("image/") }.forEach { file ->
                        contentArray.put(JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", "data:${file.type};base64,${file.base64}")))
                    }
                    apiHistory.add(APIMessage("user", contentArray))
                } else {
                    var finalInput = userInput
                    if (chatFiles.isNotEmpty()) {
                        val filesContent = extractFilesContent(attachedFiles)
                        finalInput += "\n\n[Contenido de archivos adjuntos]:\n$filesContent"
                    }
                    apiHistory.add(APIMessage("user", finalInput))
                }

                val responseText = generateText(apiHistory)
                addMessageToCurrentChat(Message("assistant", responseText))
            } catch (e: Exception) {
                addMessageToCurrentChat(Message("assistant", "Error: ${e.message}"))
            } finally {
                _isLoading.value = false
                saveChatHistory()
            }
        }
    }

    private data class FileInfo(val name: String, val type: String, val size: Long)

    private fun getFileInfo(uri: Uri): FileInfo {
        val contentResolver = getApplication<Application>().contentResolver
        var name = "Archivo"
        var size = 0L
        val type = contentResolver.getType(uri) ?: "application/octet-stream"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
                size = cursor.getLong(sizeIndex)
            }
        }
        return FileInfo(name, type, size)
    }

    private suspend fun encodeImageToBase64(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    private suspend fun extractFilesContent(uris: List<Uri>): String {
        return withContext(Dispatchers.IO) {
            uris.joinToString("\n\n---\n\n") { uri ->
                val info = getFileInfo(uri)
                val text = when {
                    info.type.startsWith("text/") -> extractTextFromUri(uri)
                    info.type == "application/pdf" -> extractTextFromPdf(uri)
                    else -> "[Archivo no soportado para análisis de texto: ${info.name}]"
                }
                "Archivo: ${info.name}\nContenido:\n$text"
            }
        }
    }

    private fun extractTextFromPdf(uri: Uri): String {
        return try {
            val pfd: ParcelFileDescriptor? = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                val textBuilder = StringBuilder()
                // Nota: PdfRenderer no extrae texto directamente, pero para propósitos de análisis simple 
                // indicaremos al menos que se ha procesado. Una extracción de texto real en PDF requiere librerías como PDFBox.
                // Como solución nativa ligera, indicamos la metadata básica.
                textBuilder.append("[Análisis de PDF nativo: ${renderer.pageCount} páginas]\n")
                textBuilder.append("(La extracción de texto completa de PDF requiere OCR o PDFBox, el sistema ha leído la estructura básica)")
                renderer.close()
                pfd.close()
                textBuilder.toString()
            } else "[No se pudo abrir el PDF]"
        } catch (e: Exception) {
            "[Error leyendo PDF: ${e.message}]"
        }
    }

    private fun extractTextFromUri(uri: Uri): String {
        return try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { 
                it.bufferedReader().readText() 
            } ?: "[No se pudo leer el archivo]"
        } catch (e: Exception) {
            "[Error leyendo archivo: ${e.message}]"
        }
    }

    private fun addMessageToCurrentChat(message: Message) {
        _messages.value += message
        val chat = _chatHistories.value.find { it.id == currentChatId }
        chat?.messages?.add(message)
    }

    private fun saveChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backup = getBackupData()
                historyFile.writeText(gson.toJson(backup))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            if (historyFile.exists()) {
                try {
                    val type = object : TypeToken<BackupData>() {}.type
                    val backup: BackupData = gson.fromJson(historyFile.readText(), type)
                    _chatHistories.value = backup.allChats.map { (id, chat) ->
                        ChatHistory(id, chat.title, chat.messages.toMutableList())
                    }
                    currentChatId = backup.currentChatId
                    if (currentChatId != null) {
                        _messages.value = backup.allChats[currentChatId]?.messages ?: emptyList()
                    }
                } catch (e: Exception) { 
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getBackupData(): BackupData {
        return BackupData(
            allChats = _chatHistories.value.associate { it.id to BackupChat(it.title, it.messages) },
            currentChatId = currentChatId
        )
    }

    fun getBackupJson(): String {
        return gson.toJson(getBackupData())
    }

    fun restoreBackup(json: String): Boolean {
        return try {
            val type = object : TypeToken<BackupData>() {}.type
            val backup: BackupData = gson.fromJson(json, type)
            _chatHistories.value = backup.allChats.map { (id, chat) ->
                ChatHistory(id, chat.title, chat.messages.toMutableList())
            }
            currentChatId = backup.currentChatId
            if (currentChatId != null) {
                _messages.value = backup.allChats[currentChatId]?.messages ?: emptyList()
            } else {
                _messages.value = emptyList()
            }
            saveChatHistory()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
                        _currentProvider.value = try { AIProvider.valueOf(name) } catch (ignore: Exception) { AIProvider.VENTARYS }
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    (settings["apiKeys"] as? Map<String, String>)?.let { keys ->
                        _apiKeys.value = keys
                    }
                    
                    @Suppress("UNCHECKED_CAST")
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
                
                val messagesArray = JSONArray()
                history.forEach { msg ->
                    val obj = JSONObject()
                    obj.put("role", msg.role)
                    obj.put("content", msg.content)
                    messagesArray.put(obj)
                }

                val jsonBody = JSONObject()
                jsonBody.put("model", model)
                jsonBody.put("messages", messagesArray)
                jsonBody.put("stream", false)

                val request = Request.Builder()
                    .url("${provider.baseUrl}/chat/completions")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    
                if (apiKey.isNotBlank()) {
                    request.addHeader("Authorization", "Bearer $apiKey")
                }

                val client = baseClient.newBuilder()
                    .callTimeout(provider.timeoutSeconds, TimeUnit.SECONDS)
                    .readTimeout(provider.timeoutSeconds, TimeUnit.SECONDS)
                    .build()

                val response = client.newCall(request.build()).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(body)
                    jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else {
                    val error = response.body?.string() ?: "Unknown error"
                    "Error API (${response.code}): $error"
                }
            } catch (e: Exception) {
                "Error de red: ${e.message}"
            }
        }
    }
}
