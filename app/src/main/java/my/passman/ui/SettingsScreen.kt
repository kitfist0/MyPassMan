package my.passman.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import my.passman.data.AppTheme
import my.passman.data.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onBack: () -> Unit
) {
    var showSortDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val sortOrderLabel = when (currentSortOrder) {
        SortOrder.BY_NAME -> "Alphabetical (A-Z)"
        SortOrder.BY_CREATED -> "Creation Time (Newest First)"
    }

    val themeLabel = when (currentTheme) {
        AppTheme.LIGHT -> "Light"
        AppTheme.DARK -> "Dark"
        AppTheme.SYSTEM -> "System Default"
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    ThemeOptionRow(
                        label = "Light",
                        selected = currentTheme == AppTheme.LIGHT,
                        onClick = {
                            onThemeChange(AppTheme.LIGHT)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionRow(
                        label = "Dark",
                        selected = currentTheme == AppTheme.DARK,
                        onClick = {
                            onThemeChange(AppTheme.DARK)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionRow(
                        label = "System Default",
                        selected = currentTheme == AppTheme.SYSTEM,
                        onClick = {
                            onThemeChange(AppTheme.SYSTEM)
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Select Sort Order") },
            text = {
                Column {
                    SortOptionRow(
                        label = "Alphabetical (A-Z)",
                        selected = currentSortOrder == SortOrder.BY_NAME,
                        onClick = {
                            onSortOrderChange(SortOrder.BY_NAME)
                            showSortDialog = false
                        }
                    )
                    SortOptionRow(
                        label = "Creation Time (Newest First)",
                        selected = currentSortOrder == SortOrder.BY_CREATED,
                        onClick = {
                            onSortOrderChange(SortOrder.BY_CREATED)
                            showSortDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Sorting") },
                        supportingContent = { Text(sortOrderLabel) },
                        modifier = Modifier.clickable { showSortDialog = true }
                    )
                    ListItem(
                        headlineContent = { Text("App Theme") },
                        supportingContent = { Text(themeLabel) },
                        modifier = Modifier.clickable { showThemeDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
