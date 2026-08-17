package my.passman.ui.screens.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.R
import my.passman.util.ClipboardUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current

    val loginLabel = stringResource(R.string.label_login).lowercase()
    val loginCopiedToast = stringResource(R.string.login_copied_toast)
    val passwordLabel = stringResource(R.string.clipboard_password_label)
    val passwordCopiedToast = stringResource(R.string.password_copied_toast)

    val recordId = state.recordId
    val isNewRecord = recordId == null

    LaunchedEffect(Unit) {
        viewModel.exitEvent.receive()
        onCancel()
    }

    BackHandler(enabled = !state.isLoading) {
        viewModel.onBackPressed()
    }

    if (state.showExitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExitDialog() },
            title = { Text(stringResource(R.string.save_changes_title)) },
            text = { Text(stringResource(R.string.save_changes_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.save()
                        onSave()
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissExitDialog()
                    onCancel()
                }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text(stringResource(R.string.delete_record_title)) },
            text = { Text(stringResource(R.string.delete_record_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDeleteDialog()
                        viewModel.delete()
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewRecord) stringResource(R.string.new_record) else stringResource(R.string.edit_record)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onBackPressed() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_description))
                    }
                },
                actions = {
                    if (recordId != null) {
                        IconButton(onClick = { viewModel.showDeleteDialog() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_description))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = state.canSave,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.imePadding(),
                    onClick = {
                        viewModel.save()
                        onSave()
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text(stringResource(R.string.save)) }
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
                contentAlignment = Alignment.Center
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
                        label = { Text(stringResource(R.string.label_name)) },
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
                        value = state.login,
                        onValueChange = viewModel::onLoginChange,
                        label = { Text(stringResource(R.string.label_login)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedElement(
                                rememberSharedContentState(key = if (recordId != null) "login-$recordId" else "new-login"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ),
                        trailingIcon = {
                            if (!isNewRecord && state.login.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        ClipboardUtils.copyToClipboard(
                                            context = context,
                                            text = state.login,
                                            label = loginLabel,
                                            toastMessage = loginCopiedToast
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_login)
                                    )
                                }
                            }
                        },
                        singleLine = true
                    )
                }

                with(sharedTransitionScope) {
                    OutlinedTextField(
                        value = state.secret,
                        onValueChange = viewModel::onSecretChange,
                        label = { Text(stringResource(R.string.label_secret)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedElement(
                                rememberSharedContentState(key = if (recordId != null) "secret-$recordId" else "new-secret"),
                                animatedVisibilityScope = animatedVisibilityScope
                            ),
                        visualTransformation = if (state.secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = viewModel::toggleSecretVisibility) {
                                    Icon(
                                        if (state.secretVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (state.secretVisible) stringResource(R.string.hide_secret) else stringResource(R.string.show_secret)
                                    )
                                }
                                if (!isNewRecord && state.secret.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            ClipboardUtils.copyToClipboard(
                                                context = context,
                                                text = state.secret,
                                                label = passwordLabel,
                                                isSensitive = true,
                                                toastMessage = passwordCopiedToast
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.copy_secret)
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true
                    )
                }

                with(sharedTransitionScope) {
                    OutlinedTextField(
                        value = state.comment,
                        onValueChange = viewModel::onCommentChange,
                        label = { Text(stringResource(R.string.label_comment)) },
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

                if (recordId != null) {
                    val dateFormat = remember {
                        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    state.created?.let {
                        Text(
                            text = stringResource(R.string.created_format, dateFormat.format(Date(it))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    state.modified?.let {
                        Text(
                            text = stringResource(R.string.modified_format, dateFormat.format(Date(it))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
