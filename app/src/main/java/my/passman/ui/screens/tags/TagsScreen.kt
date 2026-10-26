package my.passman.ui.screens.tags

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.R
import my.passman.data.Tag
import my.passman.data.TagColor
import my.passman.ui.theme.scheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: TagsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogState = state.dialog

    BackHandler {
        onBack()
    }

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
                        TagColorPicker(
                            selected = dialogState.tagColor,
                            onSelect = viewModel::onTagColorChange,
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
                        TagColorPicker(
                            selected = dialogState.tagColor,
                            onSelect = viewModel::onTagColorChange,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled =
                            isValid &&
                                (dialogState.tagName != dialog.tag.name || dialogState.tagColor != dialog.tag.color),
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
            TagColorSwatch(color = tag.color, size = 20.dp)
            Spacer(modifier = Modifier.width(12.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagColorPicker(
    selected: TagColor,
    onSelect: (TagColor) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.label_tag_color),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagColor.entries.forEach { color ->
                val isSelected = color == selected
                val label = stringResource(color.labelRes())
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(color) },
                                role = Role.RadioButton,
                            ).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    TagColorSwatch(color = color, size = 36.dp)
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = color.scheme().content,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A filled circle in the tag's color, outlined so pale fills stay visible on light surfaces. */
@Composable
private fun TagColorSwatch(
    color: TagColor,
    size: Dp,
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = color.scheme().container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {}
}

private fun TagColor.labelRes(): Int =
    when (this) {
        TagColor.DEFAULT -> R.string.tag_color_default
        TagColor.RED -> R.string.tag_color_red
        TagColor.ORANGE -> R.string.tag_color_orange
        TagColor.YELLOW -> R.string.tag_color_yellow
        TagColor.GREEN -> R.string.tag_color_green
        TagColor.TEAL -> R.string.tag_color_teal
        TagColor.BLUE -> R.string.tag_color_blue
        TagColor.PURPLE -> R.string.tag_color_purple
        TagColor.PINK -> R.string.tag_color_pink
        TagColor.GRAY -> R.string.tag_color_gray
    }
