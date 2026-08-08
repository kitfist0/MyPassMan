package my.passman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun EditRecordScreen(
    viewModel: EditRecordViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasChanges.collectAsStateWithLifecycle(false)

    val recordId = state.recordId

    BackHandler(enabled = !state.isLoading) {
        if (hasChanges) {
            viewModel.showExitDialog()
        } else {
            onCancel()
        }
    }

    if (state.showExitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExitDialog() },
            title = { Text("Save changes?") },
            text = { Text("You have unsaved changes. Do you want to save them before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.save()
                    onSave()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onCancel()
                }) {
                    Text("No")
                }
            }
        )
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Record?") },
            text = { Text("Are you sure you want to delete this record? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDeleteDialog()
                        viewModel.delete()
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recordId == null) "New Record" else "Edit Record") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) viewModel.showExitDialog() else onCancel()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (recordId != null) {
                        IconButton(onClick = { viewModel.showDeleteDialog() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            with(sharedTransitionScope) {
                ExtendedFloatingActionButton(
                    modifier = Modifier
                        .imePadding()
                        .sharedElement(
                            rememberSharedContentState(key = "fab"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ),
                    onClick = {
                        viewModel.save()
                        onSave()
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text("Save") }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                with(sharedTransitionScope) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedElement(
                                rememberSharedContentState(key = if (recordId != null) "name-$recordId" else "new-name"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ),
                        singleLine = true
                    )
                }

                with(sharedTransitionScope) {
                    OutlinedTextField(
                        value = state.secret,
                        onValueChange = viewModel::onSecretChange,
                        label = { Text("Secret") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedElement(
                                rememberSharedContentState(key = if (recordId != null) "secret-$recordId" else "new-secret"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ),
                        visualTransformation = if (state.secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image =
                                if (state.secretVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = viewModel::toggleSecretVisibility) {
                                Icon(
                                    image,
                                    contentDescription = if (state.secretVisible) "Hide secret" else "Show secret"
                                )
                            }
                        },
                        singleLine = true
                    )
                }

                with(sharedTransitionScope) {
                    OutlinedTextField(
                        value = state.comment,
                        onValueChange = viewModel::onCommentChange,
                        label = { Text("Comment") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedElement(
                                rememberSharedContentState(key = if (recordId != null) "comment-$recordId" else "new-comment"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ),
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }
        }
    }
}
