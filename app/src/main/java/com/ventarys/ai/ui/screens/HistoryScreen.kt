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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: ChatViewModel, onMenuClick: () -> Unit, onChatClicked: (String) -> Unit) {
    val chatHistory by viewModel.chatHistories.collectAsState()

    GenericScreen(title = "Historial", onMenuClick = onMenuClick) {
        if (chatHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                Text(
                    "No hay chats guardados", 
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(chatHistory, key = { it.id }) { chat ->
                    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { direction ->
                        if (direction == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.deleteChat(chat.id)
                            true
                        } else false
                    })

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color = when(dismissState.targetValue) {
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                else -> Color.Transparent
                            }
                            Box(
                                Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    "Borrar", 
                                    tint = if (color != Color.Transparent) MaterialTheme.colorScheme.onErrorContainer else Color.Transparent
                                )
                            }
                        }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.selectable(selected = false, onClick = { onChatClicked(chat.id) })
                        ) {
                            Column {
                                ListItem(
                                    headlineContent = { 
                                        Text(
                                            chat.title, 
                                            maxLines = 1, 
                                            overflow = TextOverflow.Ellipsis, 
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ) 
                                    },
                                    supportingContent = { 
                                        Text(
                                            "${chat.messages.size} mensajes", 
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ) 
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}