package com.ventarys.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ventarys.ai.ThemeOption

@Composable
fun SettingsScreen(
    onMenuClick: () -> Unit,
    themeOption: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit,
    onDeleteHistory: () -> Unit
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
            Button(onClick = onDeleteHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Borrar todo el historial")
            }
        }
    }
}