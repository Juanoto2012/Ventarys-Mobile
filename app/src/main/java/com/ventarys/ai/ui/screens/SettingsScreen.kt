package com.ventarys.ai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ventarys.ai.AIProvider
import com.ventarys.ai.ChatViewModel
import com.ventarys.ai.ThemeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onMenuClick: () -> Unit,
    themeOption: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit,
    onDeleteHistory: () -> Unit
) {
    val currentProvider by viewModel.currentProvider.collectAsState()
    val apiKeys by viewModel.apiKeys.collectAsState()
    val selectedModels by viewModel.selectedModels.collectAsState()
    val dynamicModels by viewModel.dynamicModels.collectAsState()
    val isFetchingModels by viewModel.isFetchingModels.collectAsState()

    GenericScreen(title = "Ajustes", onMenuClick = onMenuClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Configuración de IA",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.selectableGroup()) {
                    AIProvider.values().forEachIndexed { index, provider ->
                        val isSelected = provider == currentProvider
                        
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { viewModel.setProvider(provider) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            if (isSelected) {
                                Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp)) {
                                    // Model Selector
                                    var expanded by remember { mutableStateOf(false) }
                                    val availableModels = dynamicModels[provider.name] ?: provider.defaultModels
                                    val currentModel = selectedModels[provider.name] ?: availableModels.first()
                                    
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded }
                                    ) {
                                        OutlinedTextField(
                                            value = currentModel,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Modelo")
                                                    if (isFetchingModels) {
                                                        Spacer(Modifier.width(8.dp))
                                                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                                                    }
                                                }
                                            },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            availableModels.forEach { model ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            text = model,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontSize = 14.sp
                                                        ) 
                                                    },
                                                    onClick = {
                                                        viewModel.setModel(provider, model)
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    // API Key Input
                                    OutlinedTextField(
                                        value = apiKeys[provider.name] ?: "",
                                        onValueChange = { viewModel.setApiKey(provider, it) },
                                        label = { Text("API Key") },
                                        placeholder = { Text("Introduce tu clave...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = MaterialTheme.typography.bodyMedium
                                    )
                                    
                                    if (provider == AIProvider.VENTARYS) {
                                        Text(
                                            "Ventarys funciona sin clave por defecto.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                        )
                                    } else if ((apiKeys[provider.name] ?: "").isBlank()) {
                                        Text(
                                            "Se requiere una API Key para consultar modelos.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (index < AIProvider.values().size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Apariencia", 
                style = MaterialTheme.typography.titleSmall, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.selectableGroup()) {
                    ThemeOption.values().forEachIndexed { index, option ->
                        val label = when(option) {
                            ThemeOption.System -> "Predeterminado del sistema"
                            ThemeOption.Light -> "Claro"
                            ThemeOption.Dark -> "Oscuro"
                        }
                        
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
                            RadioButton(selected = (option == themeOption), onClick = null)
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (index < ThemeOption.values().size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "Datos", 
                style = MaterialTheme.typography.titleSmall, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Button(
                onClick = onDeleteHistory, 
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Borrar todo el historial")
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}