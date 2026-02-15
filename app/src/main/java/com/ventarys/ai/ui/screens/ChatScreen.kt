package com.ventarys.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ventarys.ai.ChatViewModel
import com.ventarys.ai.Message
import com.ventarys.ai.R

// --- THEME --- //
private val MonetLightColorScheme = lightColorScheme(
    primary = Color(0xFF8C7B2E),
    onPrimary = Color.White,
    secondary = Color(0xFF6A8EAF),
    onSecondary = Color.White,
    background = Color(0xFFFCF9E8),
    onBackground = Color(0xFF4A473A),
    surface = Color(0xFFFCF9E8),
    onSurface = Color(0xFF4A473A),
    surfaceVariant = Color(0xFFE8E4D3),
    onSurfaceVariant = Color(0xFF4A473A),
    outline = Color(0xFFD1CBB8)
)

private val MonetDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8C7B2E),
    onPrimary = Color.White,
    secondary = Color(0xFFA0B8D0),
    onSecondary = Color(0xFF202C39),
    background = Color(0xFF2A2820),
    onBackground = Color(0xFFE8E4D3),
    surface = Color(0xFF2A2820),
    onSurface = Color(0xFFE8E4D3),
    surfaceVariant = Color(0xFF4A473A),
    onSurfaceVariant = Color(0xFFE8E4D3),
    outline = Color(0xFF6F6A5B)
)

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
                        Image(painter = painterResource(id = R.mipmap.ic_launcher_foreground), contentDescription = "App Logo", modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ventarys AI")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "Sidebar Menu")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            MessageInput(isProcessing = isLoading, onSend = { viewModel.sendMessage(it) })
        }
    ) { paddingValues ->
        if (messages.isEmpty()) {
            WelcomeScreen(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                state = listState
            ) {
                items(messages) { message -> MessageBubble(message = message) }
                if (isLoading) {
                    item { LoadingIndicator() }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = "App Logo", modifier = Modifier.size(100.dp))
            Spacer(Modifier.height(16.dp))
            Text("Ventarys AI", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun MessageInput(isProcessing: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.navigationBarsPadding().imePadding()
    ) {
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
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
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
                        Icon(Icons.Default.ArrowUpward, "Send")
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), "AI Icon", Modifier
                .size(32.dp)
                .padding(end = 8.dp))
            ManualMarkdownText(text = message.text, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ManualMarkdownText(text: String, modifier: Modifier = Modifier) {
    val boldRegex = """\*\*(.*?)\*\*""".toRegex()
    val listPrefixRegex = """^\s*([*-])\s+(.*)""".toRegex()
    val linkRegex = """\[(.*?)\]\((.*?)\)""".toRegex()
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier) {
        text.lines().forEach { line ->
            val listMatch = listPrefixRegex.find(line)
            val isListItem = listMatch != null
            val lineContent = listMatch?.groupValues?.get(2) ?: line

            val annotatedString = buildAnnotatedString {
                var lastIndex = 0

                val allMatches = (boldRegex.findAll(lineContent).map { it to "bold" } +
                        linkRegex.findAll(lineContent).map { it to "link" })
                    .sortedBy { it.first.range.first }

                allMatches.forEach { (matchResult, type) ->
                    val startIndex = matchResult.range.first
                    if (startIndex > lastIndex) {
                        append(lineContent.substring(lastIndex, startIndex))
                    }

                    if (type == "bold") {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(matchResult.groupValues[1])
                        }
                    } else { // link
                        val linkText = matchResult.groupValues[1]
                        val url = matchResult.groupValues[2]
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append(linkText)
                        }
                        pop()
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
                    ClickableText(
                        text = annotatedString,
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    uriHandler.openUri(annotation.item)
                                }
                        }
                    )
                }
            } else {
                ClickableText(
                    text = annotatedString,
                    modifier = Modifier.padding(bottom = 4.dp),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .padding(vertical = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Pensando...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }
}