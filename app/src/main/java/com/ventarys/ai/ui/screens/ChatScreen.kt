package com.ventarys.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            MessageInput(
                isProcessing = isLoading, 
                onSend = { text, files -> viewModel.sendMessage(text, files) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 850.dp)
            ) {
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
fun MessageInput(isProcessing: Boolean, onSend: (String, List<Uri>) -> Unit) {
    var text by remember { mutableStateOf("") }
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedFiles = selectedFiles + uris
    }
    
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 800.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                AnimatedVisibility(visible = selectedFiles.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedFiles) { uri ->
                            FilePreviewItem(uri = uri) {
                                selectedFiles = selectedFiles.filter { it != uri }
                            }
                        }
                    }
                }

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Envía un mensaje a Ventarys...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 6
                )
                
                Spacer(Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { launcher.launch("*/*") }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Attach",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { /* More options */ }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ENTER para enviar",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    
                    IconButton(
                        onClick = { 
                            if ((text.isNotBlank() || selectedFiles.isNotEmpty()) && !isProcessing) { 
                                onSend(text, selectedFiles)
                                text = ""
                                selectedFiles = emptyList()
                            } 
                        },
                        enabled = (text.isNotBlank() || selectedFiles.isNotEmpty()) && !isProcessing,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if ((text.isNotBlank() || selectedFiles.isNotEmpty()) && !isProcessing) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            )
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = if ((text.isNotBlank() || selectedFiles.isNotEmpty()) && !isProcessing) MaterialTheme.colorScheme.background
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        Text(
            text = "Ventarys AI puede cometer errores. Verifica la información importante.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
fun FilePreviewItem(uri: Uri, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(height = 40.dp, width = 120.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Archivo",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.widthIn(max = 650.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (!message.isFromUser) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = "AI",
                    modifier = Modifier.size(32.dp).padding(top = 4.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
                )
                Spacer(Modifier.width(12.dp))
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