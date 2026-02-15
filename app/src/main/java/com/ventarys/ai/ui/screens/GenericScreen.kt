package com.ventarys.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

@Composable
fun GenericScreen(
    title: String,
    onMenuClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top app bar with Monet theme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.scale(1.1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "Volver al menú",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(48.dp)) // Placeholder para mantener el centrado
        }
        
        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        
        // Content with padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            content()
        }
    }
}