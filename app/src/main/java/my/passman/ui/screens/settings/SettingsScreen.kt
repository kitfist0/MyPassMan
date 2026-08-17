package my.passman.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.data.AppTheme
import my.passman.data.SortOrder
import my.passman.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val sortOrderLabel = when (state.sortOrder) {
        SortOrder.BY_NAME -> stringResource(R.string.sort_alphabetical)
        SortOrder.BY_CREATED -> stringResource(R.string.sort_creation_time)
    }

    val themeLabel = when (state.theme) {
        AppTheme.LIGHT -> stringResource(R.string.theme_light)
        AppTheme.DARK -> stringResource(R.string.theme_dark)
        AppTheme.SYSTEM -> stringResource(R.string.theme_system)
    }

    if (state.showThemeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissThemeDialog() },
            title = { Text(stringResource(R.string.select_theme)) },
            text = {
                Column {
                    ThemeOptionRow(
                        label = stringResource(R.string.theme_light),
                        selected = state.theme == AppTheme.LIGHT,
                        onClick = {
                            viewModel.onThemeChange(AppTheme.LIGHT)
                            viewModel.dismissThemeDialog()
                        }
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.theme_dark),
                        selected = state.theme == AppTheme.DARK,
                        onClick = {
                            viewModel.onThemeChange(AppTheme.DARK)
                            viewModel.dismissThemeDialog()
                        }
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.theme_system),
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
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (state.showSortDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSortDialog() },
            title = { Text(stringResource(R.string.select_sort)) },
            text = {
                Column {
                    SortOptionRow(
                        label = stringResource(R.string.sort_alphabetical),
                        selected = state.sortOrder == SortOrder.BY_NAME,
                        onClick = {
                            viewModel.onSortOrderChange(SortOrder.BY_NAME)
                            viewModel.dismissSortDialog()
                        }
                    )
                    SortOptionRow(
                        label = stringResource(R.string.sort_creation_time),
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
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (state.showAboutDialog) {
        val packageInfo = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (_: Exception) {
                null
            }
        }
        val versionName = packageInfo?.versionName ?: "Unknown"

        AlertDialog(
            onDismissRequest = { viewModel.dismissAboutDialog() },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_version, versionName))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.about_description))
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAboutDialog() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                        headlineContent = { Text(stringResource(R.string.settings_sorting)) },
                        supportingContent = { Text(sortOrderLabel) },
                        modifier = Modifier.clickable { viewModel.showSortDialog() }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_theme)) },
                        supportingContent = { Text(themeLabel) },
                        modifier = Modifier.clickable { viewModel.showThemeDialog() }
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_about)) },
                        modifier = Modifier.clickable { viewModel.showAboutDialog() }
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
