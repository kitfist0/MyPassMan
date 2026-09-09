package my.passman.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.R
import my.passman.data.AppTheme
import my.passman.data.SortOrder
import my.passman.util.BiometricAuthenticator
import my.passman.util.PasswordValidator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onManageTags: () -> Unit,
    onSetupNewPin: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    BackHandler {
        onBack()
    }

    val sortOrderLabel =
        when (state.sortOrder) {
            SortOrder.BY_NAME -> stringResource(R.string.sort_alphabetical)
            SortOrder.BY_CREATED -> stringResource(R.string.sort_creation_time)
            SortOrder.BY_MODIFIED -> stringResource(R.string.sort_modified_time)
        }

    val themeLabel =
        when (state.theme) {
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
                        },
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.theme_dark),
                        selected = state.theme == AppTheme.DARK,
                        onClick = {
                            viewModel.onThemeChange(AppTheme.DARK)
                            viewModel.dismissThemeDialog()
                        },
                    )
                    ThemeOptionRow(
                        label = stringResource(R.string.theme_system),
                        selected = state.theme == AppTheme.SYSTEM,
                        onClick = {
                            viewModel.onThemeChange(AppTheme.SYSTEM)
                            viewModel.dismissThemeDialog()
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissThemeDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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
                        },
                    )
                    SortOptionRow(
                        label = stringResource(R.string.sort_creation_time),
                        selected = state.sortOrder == SortOrder.BY_CREATED,
                        onClick = {
                            viewModel.onSortOrderChange(SortOrder.BY_CREATED)
                            viewModel.dismissSortDialog()
                        },
                    )
                    SortOptionRow(
                        label = stringResource(R.string.sort_modified_time),
                        selected = state.sortOrder == SortOrder.BY_MODIFIED,
                        onClick = {
                            viewModel.onSortOrderChange(SortOrder.BY_MODIFIED)
                            viewModel.dismissSortDialog()
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSortDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.showAboutDialog) {
        val packageInfo =
            remember {
                try {
                    activity.packageManager.getPackageInfo(activity.packageName, 0)
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
            },
        )
    }

    if (state.showDisablePinDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDisablePinDialog() },
            title = { Text(stringResource(R.string.disable_pin_title)) },
            text = { Text(stringResource(R.string.disable_pin_text)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDisablePin() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.disable))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDisablePinDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.showSyncPassphraseDialog) {
        var passphrase by remember { mutableStateOf("") }
        var passphraseVisible by remember { mutableStateOf(false) }
        val missingRequirements = remember(passphrase) { PasswordValidator.validate(passphrase) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissSyncPassphraseDialog() },
            title = { Text(stringResource(R.string.sync_passphrase_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.sync_passphrase_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    PasswordRequirementsList(missingRequirements)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.label_secret)) },
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passphraseVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description =
                                if (passphraseVisible) {
                                    stringResource(R.string.hide_secret)
                                } else {
                                    stringResource(R.string.show_secret)
                                }
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = passphrase.isNotEmpty() && missingRequirements.isNotEmpty(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = missingRequirements.isEmpty(),
                    onClick = { viewModel.enableDriveSync(passphrase) },
                ) {
                    Text(stringResource(R.string.enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSyncPassphraseDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.showBackupPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        val missingRequirements = remember(password) { PasswordValidator.validate(password) }
        val isExport = state.backupMode == BackupMode.EXPORT
        val canConfirm = if (isExport) missingRequirements.isEmpty() else password.isNotBlank()

        AlertDialog(
            onDismissRequest = { viewModel.dismissBackupPasswordDialog() },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_password_message))
                    if (isExport) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PasswordRequirementsList(missingRequirements)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.label_secret)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            val description =
                                if (passwordVisible) {
                                    stringResource(
                                        R.string.hide_secret,
                                    )
                                } else {
                                    stringResource(R.string.show_secret)
                                }

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = isExport && password.isNotEmpty() && missingRequirements.isNotEmpty(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirm,
                    onClick = { viewModel.onBackupPasswordEntered(password) },
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBackupPasswordDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.showResetBackupDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissResetBackupDialog() },
            title = { Text(stringResource(R.string.reset_backup_title)) },
            text = { Text(stringResource(R.string.reset_backup_text)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmResetBackup() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.reset_backup_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResetBackupDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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
                },
            )
        },
    ) { padding ->
        val useTwoColumns = LocalConfiguration.current.screenWidthDp >= 600

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            if (useTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GeneralSettingsCard(
                        modifier = Modifier.weight(1f),
                        viewModel = viewModel,
                        state = state,
                        sortOrderLabel = sortOrderLabel,
                        themeLabel = themeLabel,
                        onManageTags = onManageTags,
                        onSetupNewPin = onSetupNewPin,
                        isFingerprintAvailable = viewModel.isFingerprintAvailable,
                        activity = activity,
                    )
                    BackupSettingsCard(modifier = Modifier.weight(1f), viewModel = viewModel, state = state)
                }
            } else {
                GeneralSettingsCard(
                    modifier = Modifier.fillMaxWidth(),
                    viewModel = viewModel,
                    state = state,
                    sortOrderLabel = sortOrderLabel,
                    themeLabel = themeLabel,
                    onManageTags = onManageTags,
                    onSetupNewPin = onSetupNewPin,
                    isFingerprintAvailable = viewModel.isFingerprintAvailable,
                    activity = activity,
                )
                Spacer(modifier = Modifier.height(16.dp))
                BackupSettingsCard(modifier = Modifier.fillMaxWidth(), viewModel = viewModel, state = state)
            }
        }
    }
}

@Composable
private fun GeneralSettingsCard(
    modifier: Modifier,
    viewModel: SettingsViewModel,
    state: SettingsScreenState,
    sortOrderLabel: String,
    themeLabel: String,
    onManageTags: () -> Unit,
    onSetupNewPin: () -> Unit,
    isFingerprintAvailable: Boolean,
    activity: FragmentActivity,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sorting)) },
                supportingContent = { Text(sortOrderLabel) },
                modifier = Modifier.clickable { viewModel.showSortDialog() },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = { Text(themeLabel) },
                modifier = Modifier.clickable { viewModel.showThemeDialog() },
            )
            val onPinToggle: (Boolean) -> Unit = { checked ->
                if (checked) {
                    onSetupNewPin()
                } else {
                    viewModel.showDisablePinDialog()
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_pin)) },
                trailingContent = {
                    Switch(
                        checked = state.isPinEnabled,
                        onCheckedChange = onPinToggle,
                    )
                },
                modifier = Modifier.clickable { onPinToggle(!state.isPinEnabled) },
            )
            if (state.isPinEnabled && isFingerprintAvailable) {
                val enableTitle = stringResource(R.string.fingerprint_enable_title)
                val enableMessage = stringResource(R.string.fingerprint_enable_message)
                val skipText = stringResource(R.string.skip)
                val onFingerprintToggle: (Boolean) -> Unit = { checked ->
                    if (checked) {
                        BiometricAuthenticator.authenticate(
                            activity = activity,
                            title = enableTitle,
                            subtitle = enableMessage,
                            negativeButtonText = skipText,
                            onSuccess = { viewModel.setFingerprintEnabled(true) },
                            onError = {},
                            onFailed = {},
                        )
                    } else {
                        viewModel.setFingerprintEnabled(false)
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_fingerprint)) },
                    trailingContent = {
                        Switch(
                            checked = state.isFingerprintEnabled,
                            onCheckedChange = onFingerprintToggle,
                        )
                    },
                    modifier = Modifier.clickable { onFingerprintToggle(!state.isFingerprintEnabled) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_manage_tags)) },
                modifier = Modifier.clickable { onManageTags() },
            )
        }
    }
}

@Composable
private fun BackupSettingsCard(
    modifier: Modifier,
    viewModel: SettingsViewModel,
    state: SettingsScreenState,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            val onDriveSyncToggle: (Boolean) -> Unit = { checked ->
                if (checked) {
                    viewModel.showSyncPassphraseDialog()
                } else {
                    viewModel.disableDriveSync()
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_drive_sync)) },
                supportingContent = {
                    if (state.driveSyncEnabled) {
                        val lastSyncedLabel =
                            state.lastSyncedAt?.let {
                                stringResource(
                                    R.string.settings_drive_sync_last_synced,
                                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it)),
                                )
                            } ?: stringResource(R.string.settings_drive_sync_never)
                        Text(lastSyncedLabel)
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.driveSyncEnabled) {
                            IconButton(onClick = { viewModel.syncNow() }) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = stringResource(R.string.settings_drive_sync_now),
                                )
                            }
                        }
                        Switch(
                            checked = state.driveSyncEnabled,
                            onCheckedChange = onDriveSyncToggle,
                        )
                    }
                },
                modifier = Modifier.clickable { onDriveSyncToggle(!state.driveSyncEnabled) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                modifier = Modifier.clickable { viewModel.onExportClick() },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                modifier = Modifier.clickable { viewModel.onImportClick() },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                modifier = Modifier.clickable { viewModel.showAboutDialog() },
            )
        }
    }
}

@Composable
private fun PasswordRequirementsList(missingRequirements: List<PasswordValidator.PasswordRequirement>) {
    RequirementItem(
        label = stringResource(R.string.password_requirement_min_length),
        isMet = PasswordValidator.PasswordRequirement.MIN_LENGTH !in missingRequirements,
    )
    RequirementItem(
        label = stringResource(R.string.password_requirement_letters_digits),
        isMet = PasswordValidator.PasswordRequirement.LETTERS_AND_DIGITS !in missingRequirements,
    )
    RequirementItem(
        label = stringResource(R.string.password_requirement_symbol),
        isMet = PasswordValidator.PasswordRequirement.HAS_SYMBOL !in missingRequirements,
    )
}

@Composable
private fun RequirementItem(
    label: String,
    isMet: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isMet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
