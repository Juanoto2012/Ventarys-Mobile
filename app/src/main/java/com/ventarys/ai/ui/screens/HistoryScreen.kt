package com.ventarys.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ventarys.ai.ChatViewModel

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
fun HistoryScreen(viewModel: ChatViewModel, onMenuClick: () -> Unit, onChatClicked: (String) -> Unit) {
    val chatHistory by viewModel.chatHistories.collectAsState()

    GenericScreen(title = "Historial", onMenuClick = onMenuClick) {
        if (chatHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                Text("No hay chats guardados", color = MaterialTheme.colorScheme.onBackground) 
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(chatHistory, key = { it.id }) {
                    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { direction ->
                        if (direction == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.deleteChat(it.id)
                            true
                        } else false
                    })

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = when(dismissState.targetValue) {
                                SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                                else -> Color.Transparent
                            }
                            Box(
                                Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                            }
                        }
                    ) {
                        ListItem(
                            headlineContent = { 
                                Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface) 
                            },
                            supportingContent = { 
                                Text("${it.messages.size} mensajes", maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) 
                            },
                            modifier = Modifier.selectable(selected = false, onClick = { onChatClicked(it.id) })
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}