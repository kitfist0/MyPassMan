package my.passman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import my.passman.data.Record

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun EditRecordScreen(
    record: Record?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSave: (name: String, secret: String, comment: String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(record?.name.orEmpty()) }
    var secret by remember { mutableStateOf(record?.secret.orEmpty()) }
    var comment by remember { mutableStateOf(record?.comment.orEmpty()) }
    var secretVisible by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val hasChanges = remember(name, secret, comment) {
        val originalName = record?.name.orEmpty()
        val originalSecret = record?.secret.orEmpty()
        val originalComment = record?.comment.orEmpty()
        name != originalName || secret != originalSecret || comment != originalComment
    }

    BackHandler {
        if (hasChanges) {
            showExitDialog = true
        } else {
            onCancel()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Save changes?") },
            text = { Text("You have unsaved changes. Do you want to save them before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    onSave(name, secret, comment)
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Record?") },
            text = { Text("Are you sure you want to delete this record? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (record == null) "New Record" else "Edit Record") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) showExitDialog = true else onCancel()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (record != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
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
                    onClick = { onSave(name, secret, comment) },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text("Save") }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedElement(
                            rememberSharedContentState(key = if (record != null) "name-${record.id}" else "new-name"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ),
                    singleLine = true
                )
            }

            with(sharedTransitionScope) {
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Secret") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedElement(
                            rememberSharedContentState(key = if (record != null) "secret-${record.id}" else "new-secret"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ),
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (secretVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(image, contentDescription = if (secretVisible) "Hide secret" else "Show secret")
                        }
                    },
                    singleLine = true
                )
            }

            with(sharedTransitionScope) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.replace("\n", "") },
                    label = { Text("Comment") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedElement(
                            rememberSharedContentState(key = if (record != null) "comment-${record.id}" else "new-comment"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ),
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }
    }
}
