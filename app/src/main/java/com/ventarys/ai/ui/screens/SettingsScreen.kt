package com.ventarys.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ventarys.ai.ThemeOption

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
fun SettingsScreen(
    onMenuClick: () -> Unit,
    themeOption: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit,
    onDeleteHistory: () -> Unit
) {
    GenericScreen(title = "Ajustes", onMenuClick = onMenuClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Tema de la aplicación", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
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
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onDeleteHistory, 
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Borrar todo el historial")
            }
        }
    }
}