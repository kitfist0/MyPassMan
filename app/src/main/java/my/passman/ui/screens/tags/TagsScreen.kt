package my.passman.ui.screens.tags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.R
import my.passman.data.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: TagsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogState = state.dialog

    when (val dialog = dialogState.activeDialog) {
        TagsDialog.Add -> {
            val isValid = remember(dialogState.tagName) { Tag.isValidName(dialogState.tagName) }

            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.add_tag_title)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = dialogState.tagName,
                            onValueChange = viewModel::onTagNameChange,
                            label = { Text(stringResource(R.string.label_tag_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = dialogState.tagName.isNotEmpty() && !isValid,
                            supportingText = {
                                if (dialogState.tagName.isNotEmpty() && !isValid) {
                                    Text(
                                        text = stringResource(R.string.tag_name_error, Tag.MAX_NAME_LENGTH),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = isValid,
                        onClick = {
                            viewModel.onAddDialogConfirmButtonClick()
                            viewModel.dismissDialog()
                        },
                    ) {
                        Text(stringResource(R.string.add))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        is TagsDialog.Edit -> {
            val isValid = remember(dialogState.tagName) { Tag.isValidName(dialogState.tagName) }

            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.edit_tag_title)) },
                text = {
                    OutlinedTextField(
                        value = dialogState.tagName,
                        onValueChange = viewModel::onTagNameChange,
                        label = { Text(stringResource(R.string.label_tag_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = dialogState.tagName.isNotEmpty() && !isValid,
                        supportingText = {
                            if (dialogState.tagName.isNotEmpty() && !isValid) {
                                Text(
                                    text = stringResource(R.string.tag_name_error, Tag.MAX_NAME_LENGTH),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = isValid && dialogState.tagName != dialog.tag.name,
                        onClick = {
                            viewModel.onEditDialogConfirmButtonClick()
                            viewModel.dismissDialog()
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        is TagsDialog.Delete -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.delete_tag_title)) },
                text = { Text(stringResource(R.string.delete_tag_text, dialog.tag.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.onDeleteDialogConfirmButtonClick(dialog.tag)
                            viewModel.dismissDialog()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        null -> { /* No dialog */ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_description))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_tag_description))
            }
        },
    ) { padding ->
        if (state.tags.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_tags),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.tags, key = { it.id }) { tag ->
                    TagItem(
                        tag = tag,
                        onDelete = { viewModel.showDeleteConfirmation(tag) },
                        onClick = { viewModel.showEditDialog(tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagItem(
    tag: Tag,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_description),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
