package com.ventarys.ai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ventarys.ai.ChatViewModel
import com.ventarys.ai.Message
import com.ventarys.ai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onMenuClick: () -> Unit) {
    val listState = rememberLazyListState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ventarys AI",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            MessageInput(isProcessing = isLoading, onSend = { text -> viewModel.sendMessage(text) })
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (messages.isEmpty()) {
                WelcomeScreen()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                ) {
                    items(messages) { message -> MessageBubble(message = message) }
                    if (isLoading) {
                        item { LoadingIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "App Logo",
                modifier = Modifier.size(100.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "¿En qué puedo ayudarte?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun MessageInput(isProcessing: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { /* Add functionality */ },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Attachments",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    placeholder = { 
                        Text(
                            "Mensaje", 
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            fontSize = 16.sp
                        ) 
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 5
                )
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(
                    onClick = { if (text.isNotBlank()) { onSend(text); text = "" } },
                    enabled = text.isNotBlank() && !isProcessing,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank() && !isProcessing) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        ),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Icon(
                        Icons.Default.ArrowUpward, 
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (!message.isFromUser) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "AI",
                    modifier = Modifier.size(32.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (message.isFromUser) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 22.sp,
                            fontSize = 16.sp
                        )
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    ManualMarkdownText(text = message.text)
                }
            }
        }
    }
}

@Composable
fun Dot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
    )
}

@Composable
fun ThreeDotLoading() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Dot(alpha1)
        Dot(alpha2)
        Dot(alpha3)
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = "AI",
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
        )
        Spacer(Modifier.width(12.dp))
        ThreeDotLoading()
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
                        withStyle(style = SpanStyle(color = Color(0xFF007AFF), textDecoration = TextDecoration.Underline)) {
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
                Row(Modifier.padding(bottom = 6.dp)) {
                    Text("•", modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 24.sp,
                            fontSize = 16.sp
                        ),
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
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 24.sp,
                        fontSize = 16.sp
                    ),
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