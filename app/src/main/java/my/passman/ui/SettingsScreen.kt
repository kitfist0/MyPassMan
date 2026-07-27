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
import my.passman.data.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    onBack: () -> Unit
) {
    var showSortDialog by remember { mutableStateOf(false) }

    val sortOrderLabel = when (currentSortOrder) {
        SortOrder.BY_NAME -> "Alphabetical (A-Z)"
        SortOrder.BY_CREATED -> "Creation Time (Newest First)"
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
                ListItem(
                    headlineContent = { Text("Sorting") },
                    supportingContent = { Text(sortOrderLabel) },
                    modifier = Modifier.clickable { showSortDialog = true }
                )
            }
        }
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
