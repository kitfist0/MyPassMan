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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.data.AppTheme
import my.passman.data.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val sortOrderLabel = when (state.sortOrder) {
        SortOrder.BY_NAME -> "Alphabetical (A-Z)"
        SortOrder.BY_CREATED -> "Creation Time (Newest First)"
    }

    val themeLabel = when (state.theme) {
        AppTheme.LIGHT -> "Light"
        AppTheme.DARK -> "Dark"
        AppTheme.SYSTEM -> "System Default"
    }

    if (state.showThemeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissThemeDialog() },
            title = { Text("Select Theme") },
            text = {
                Column {
                    ThemeOptionRow(
                        label = "Light",
                        selected = state.theme == AppTheme.LIGHT,
                        onClick = {
                            viewModel.onThemeChange(AppTheme.LIGHT)
                            viewModel.dismissThemeDialog()
                        }
                    )
                    ThemeOptionRow(
                        label = "Dark",
                        selected = state.theme == AppTheme.DARK,
                        onClick = {
                            viewModel.onThemeChange(AppTheme.DARK)
                            viewModel.dismissThemeDialog()
                        }
                    )
                    ThemeOptionRow(
                        label = "System Default",
                        selected = state.theme == AppTheme.SYSTEM,
                        onClick = {
                            viewModel.onThemeChange(AppTheme.SYSTEM)
                            viewModel.dismissThemeDialog()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissThemeDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.showSortDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSortDialog() },
            title = { Text("Select Sort Order") },
            text = {
                Column {
                    SortOptionRow(
                        label = "Alphabetical (A-Z)",
                        selected = state.sortOrder == SortOrder.BY_NAME,
                        onClick = {
                            viewModel.onSortOrderChange(SortOrder.BY_NAME)
                            viewModel.dismissSortDialog()
                        }
                    )
                    SortOptionRow(
                        label = "Creation Time (Newest First)",
                        selected = state.sortOrder == SortOrder.BY_CREATED,
                        onClick = {
                            viewModel.onSortOrderChange(SortOrder.BY_CREATED)
                            viewModel.dismissSortDialog()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSortDialog() }) {
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
                        modifier = Modifier.clickable { viewModel.showSortDialog() }
                    )
                    ListItem(
                        headlineContent = { Text("App Theme") },
                        supportingContent = { Text(themeLabel) },
                        modifier = Modifier.clickable { viewModel.showThemeDialog() }
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
