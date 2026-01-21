package com.ventarys.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VentarysChatTheme {
                VentarysChatApp(viewModel = chatViewModel)
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
fun VentarysChatTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) CoffeePeachDarkColorScheme else CoffeePeachLightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}

// --- DATA & VIEWMODEL --- //
data class Message(val text: String, val isFromUser: Boolean)
data class APIMessage(val role: String, val content: String)

val SYSTEM_MESSAGE = APIMessage(
    role = "system",
    content = "Eres Ventarys AI. Responde de forma útil, precisa y concisa. Usa Markdown solo para negritas (**texto**) y listas (* elemento)."
)

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val client = OkHttpClient.Builder()
        .callTimeout(4, TimeUnit.MINUTES) // Increased timeout to 4 minutes
        .build()

    fun sendMessage(userInput: String) {
        viewModelScope.launch {
            val userMessage = Message(userInput, true)
            _messages.value = _messages.value + userMessage
            _isLoading.value = true

            try {
                val history = mutableListOf(SYSTEM_MESSAGE) + _messages.value.map { m -> APIMessage(if (m.isFromUser) "user" else "assistant", m.text) }
                val responseText = generatePrivateText(history)
                _messages.value = _messages.value + Message(responseText, false)
            } catch (e: Exception) {
                _messages.value = _messages.value + Message("Error: ${e.message}", false)
            } finally {
                _isLoading.value = false
            }
        }
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

                val request = Request.Builder()
                    .url("https://text.pollinations.ai/")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    "API Error: ${response.code} ${response.message}"
                } else {
                    response.body?.string() ?: "No response from the API"
                }
            } catch (e: IOException) {
                e.message ?: "Unknown error"
            }
        }
    }
}

// --- COMPOSABLES --- //
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentarysChatApp(viewModel: ChatViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(onClose = { scope.launch { drawerState.close() } })
        }
    ) {
        val listState = rememberLazyListState()
        val messages by viewModel.messages.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size)
            }
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Ventarys AI") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Sidebar Menu"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                MessageInput(isProcessing = isLoading, onSend = { viewModel.sendMessage(it) })
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                state = listState
            ) {
                items(messages) {
                    message ->
                    MessageBubble(message = message)
                }
                if (isLoading) {
                    item { LoadingIndicator() }
                }
            }
        }
    }
}

@Composable
fun AppDrawer(onClose: () -> Unit) {
    ModalDrawerSheet {
        Text("Ventarys AI", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        Divider()
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
            label = { Text("Historial") },
            selected = false,
            onClick = { onClose() }
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
            label = { Text("Ajustes") },
            selected = false,
            onClick = { onClose() }
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Info, contentDescription = "About") },
            label = { Text("Acerca de") },
            selected = false,
            onClick = { onClose() }
        )
    }
}


@Composable
fun MessageInput(isProcessing: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Envía un mensaje a Ventarys...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = { onSend(text); text = "" },
                        enabled = text.isNotBlank() && !isProcessing,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    if (message.isFromUser) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(message.text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.StarOutline, "AI Icon",
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            ManualMarkdownText(text = message.text, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ManualMarkdownText(text: String, modifier: Modifier = Modifier) {
    val boldRegex = """\*\*(.*?)\*\*""".toRegex()
    val listPrefixRegex = """^\s*([*\-])\s+(.*)""".toRegex()

    Column(modifier = modifier) {
        text.lines().forEach { line ->
            val listMatch = listPrefixRegex.find(line)
            val isListItem = listMatch != null
            val lineContent = listMatch?.groupValues?.get(2) ?: line

            val annotatedString = buildAnnotatedString {
                var lastIndex = 0
                boldRegex.findAll(lineContent).forEach { matchResult ->
                    val startIndex = matchResult.range.first
                    if (startIndex > lastIndex) {
                        append(lineContent.substring(lastIndex, startIndex))
                    }
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(matchResult.groupValues[1])
                    }
                    lastIndex = matchResult.range.last + 1
                }
                if (lastIndex < lineContent.length) {
                    append(lineContent.substring(lastIndex))
                }
            }

            if (isListItem) {
                Row(Modifier.padding(bottom = 4.dp)) {
                    Text("•", modifier = Modifier.padding(end = 8.dp))
                    Text(annotatedString)
                }
            } else {
                Text(annotatedString, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Pensando...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}
