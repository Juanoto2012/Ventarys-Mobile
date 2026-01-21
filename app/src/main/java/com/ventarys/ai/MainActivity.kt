package com.ventarys.ai

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
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ventarys.ai.R
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
        .callTimeout(0, TimeUnit.SECONDS) // Indefinite timeout
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

    fun clearChatHistory() {
        _messages.value = emptyList()
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
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        NavHost(navController = navController, startDestination = AppDestinations.CHAT_ROUTE) {
            composable(AppDestinations.CHAT_ROUTE) {
                ChatScreen(viewModel = viewModel, onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(AppDestinations.HISTORY_ROUTE) {
                HistoryScreen(onMenuClick = { scope.launch { drawerState.open() } })
            }
            composable(AppDestinations.SETTINGS_ROUTE) {
                SettingsScreen(
                    viewModel = viewModel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    themeOption = themeOptionState.value,
                    onThemeChange = { themeOptionState.value = it }
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
    onClose: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "App Logo",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Ventarys AI", style = MaterialTheme.typography.titleLarge)
        }
        Divider()
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Forum, contentDescription = "Chat") },
            label = { Text("Chat") },
            selected = currentRoute == AppDestinations.CHAT_ROUTE,
            onClick = { onNavigate(AppDestinations.CHAT_ROUTE); onClose() }
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
            label = { Text("Historial") },
            selected = currentRoute == AppDestinations.HISTORY_ROUTE,
            onClick = { onNavigate(AppDestinations.HISTORY_ROUTE); onClose() }
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
            label = { Text("Ajustes") },
            selected = currentRoute == AppDestinations.SETTINGS_ROUTE,
            onClick = { onNavigate(AppDestinations.SETTINGS_ROUTE); onClose() }
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Info, contentDescription = "About") },
            label = { Text("Acerca de") },
            selected = currentRoute == AppDestinations.ABOUT_ROUTE,
            onClick = { onNavigate(AppDestinations.ABOUT_ROUTE); onClose() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericScreen(title: String, onMenuClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = "App Logo", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "Sidebar Menu")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            content()
        }
    }
}

@Composable
fun HistoryScreen(onMenuClick: () -> Unit) {
    GenericScreen(title = "Historial", onMenuClick = onMenuClick) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay chats guardados")
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onMenuClick: () -> Unit,
    themeOption: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit
) {
    GenericScreen(title = "Ajustes", onMenuClick = onMenuClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tema de la aplicación", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                ThemeOption.values().forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (option == themeOption),
                                onClick = { onThemeChange(option) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == themeOption),
                            onClick = null // null recommended for accessibility with screenreaders
                        )
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.clearChatHistory() }, modifier = Modifier.fillMaxWidth()) {
                Text("Borrar historial del chat actual")
            }
        }
    }
}

@Composable
fun AboutScreen(onMenuClick: () -> Unit) {
    val context = LocalContext.current
    GenericScreen(title = "Acerca de", onMenuClick = onMenuClick) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "App Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Versión 1.0.0", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Creado por JNTX Studio", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Juanoto2012"))
                context.startActivity(intent)
            }) {
                Text("GitHub: Juanoto2012")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onMenuClick: () -> Unit) {
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painter = painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = "App Logo", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ventarys AI")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "Sidebar Menu")
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
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            state = listState
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
            if (isLoading) {
                item { LoadingIndicator() }
            }
        }
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
