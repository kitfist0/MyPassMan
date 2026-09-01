package my.passman.ui.screens.settings

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.AppTheme
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import my.passman.sync.SyncManager
import my.passman.sync.SyncResult
import my.passman.sync.SyncScheduler
import my.passman.util.BackupManager
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val backupManager: BackupManager,
        private val syncManager: SyncManager,
        private val syncScheduler: SyncScheduler,
    ) : ViewModel() {
        private val _dialogState = MutableStateFlow(DialogState())

        private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
        val events: ReceiveChannel<SettingsEvent> = _events

        private data class SyncSettings(
            val enabled: Boolean,
            val lastSyncedAt: Long?,
        )

        private val syncSettings: Flow<SyncSettings> =
            combine(
                settingsRepository.driveSyncEnabled,
                settingsRepository.lastSyncedAt,
            ) { enabled, lastSyncedAt -> SyncSettings(enabled, lastSyncedAt) }

        val uiState: StateFlow<SettingsScreenState> =
            combine(
                settingsRepository.sortOrder,
                settingsRepository.appTheme,
                settingsRepository.pinHash,
                _dialogState,
                syncSettings,
            ) { sortOrder, theme, pinHash, dialogState, sync ->
                SettingsScreenState(
                    sortOrder = sortOrder,
                    theme = theme,
                    isPinEnabled = pinHash != null,
                    showSortDialog = dialogState.showSortDialog,
                    showThemeDialog = dialogState.showThemeDialog,
                    showAboutDialog = dialogState.showAboutDialog,
                    showBackupPasswordDialog = dialogState.showBackupPasswordDialog,
                    showDisablePinDialog = dialogState.showDisablePinDialog,
                    showSyncPassphraseDialog = dialogState.showSyncPassphraseDialog,
                    backupMode = dialogState.backupMode,
                    driveSyncEnabled = sync.enabled,
                    lastSyncedAt = sync.lastSyncedAt,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                SettingsScreenState(),
            )

        private data class DialogState(
            val showSortDialog: Boolean = false,
            val showThemeDialog: Boolean = false,
            val showAboutDialog: Boolean = false,
            val showBackupPasswordDialog: Boolean = false,
            val showDisablePinDialog: Boolean = false,
            val showSyncPassphraseDialog: Boolean = false,
            val backupMode: BackupMode? = null,
        )

        sealed class SettingsEvent {
            data object RequestExportFile : SettingsEvent()

            data object RequestImportFile : SettingsEvent()

            data class RequestDriveConsent(
                val pendingIntent: PendingIntent,
            ) : SettingsEvent()

            data class ShowToast(
                val message: String,
            ) : SettingsEvent()
        }

        private var pendingPassword = charArrayOf()

        fun onExportClick() {
            _dialogState.update { it.copy(showBackupPasswordDialog = true, backupMode = BackupMode.EXPORT) }
        }

        fun onImportClick() {
            viewModelScope.launch {
                _events.send(SettingsEvent.RequestImportFile)
            }
        }

        private var pendingImportData: ByteArray? = null

        fun clearPin() {
            viewModelScope.launch {
                settingsRepository.clearPin()
            }
        }

        fun showDisablePinDialog() {
            _dialogState.update { it.copy(showDisablePinDialog = true) }
        }

        fun dismissDisablePinDialog() {
            _dialogState.update { it.copy(showDisablePinDialog = false) }
        }

        fun confirmDisablePin() {
            _dialogState.update { it.copy(showDisablePinDialog = false) }
            clearPin()
        }

        fun onImportFileSelected(inputStream: InputStream) {
            viewModelScope.launch {
                try {
                    pendingImportData = inputStream.use { it.readBytes() }
                    _dialogState.update { it.copy(showBackupPasswordDialog = true, backupMode = BackupMode.IMPORT) }
                } catch (e: Exception) {
                    _events.send(SettingsEvent.ShowToast("Failed to read file: ${e.message}"))
                }
            }
        }

        fun onBackupPasswordEntered(password: String) {
            pendingPassword = password.toCharArray()
            val mode = _dialogState.value.backupMode
            _dialogState.update { it.copy(showBackupPasswordDialog = false, backupMode = null) }

            viewModelScope.launch {
                if (mode == BackupMode.EXPORT) {
                    _events.send(SettingsEvent.RequestExportFile)
                } else if (mode == BackupMode.IMPORT) {
                    executeImport()
                }
            }
        }

        fun executeExport(outputStream: OutputStream) {
            viewModelScope.launch {
                try {
                    backupManager.exportDatabase(outputStream, pendingPassword)
                    _events.send(SettingsEvent.ShowToast("Export successful"))
                } catch (e: Exception) {
                    _events.send(SettingsEvent.ShowToast("Export failed: ${e.message}"))
                } finally {
                    pendingPassword = charArrayOf()
                }
            }
        }

        private fun executeImport() {
            val data = pendingImportData ?: return
            viewModelScope.launch {
                try {
                    backupManager.importDatabase(data, pendingPassword)
                    _events.send(SettingsEvent.ShowToast("Import successful"))
                } catch (e: Exception) {
                    _events.send(SettingsEvent.ShowToast("Import failed: ${e.message}"))
                } finally {
                    pendingPassword = charArrayOf()
                    pendingImportData = null
                }
            }
        }

        fun onSortOrderChange(sortOrder: SortOrder) {
            viewModelScope.launch {
                settingsRepository.setSortOrder(sortOrder)
            }
        }

        fun onThemeChange(theme: AppTheme) {
            viewModelScope.launch {
                settingsRepository.setAppTheme(theme)
            }
        }

        fun showSortDialog() {
            _dialogState.update { it.copy(showSortDialog = true) }
        }

        fun dismissSortDialog() {
            _dialogState.update { it.copy(showSortDialog = false) }
        }

        fun showThemeDialog() {
            _dialogState.update { it.copy(showThemeDialog = true) }
        }

        fun dismissThemeDialog() {
            _dialogState.update { it.copy(showThemeDialog = false) }
        }

        fun showAboutDialog() {
            _dialogState.update { it.copy(showAboutDialog = true) }
        }

        fun dismissAboutDialog() {
            _dialogState.update { it.copy(showAboutDialog = false) }
        }

        fun dismissBackupPasswordDialog() {
            _dialogState.update { it.copy(showBackupPasswordDialog = false, backupMode = null) }
            pendingPassword = charArrayOf()
        }

        fun showSyncPassphraseDialog() {
            _dialogState.update { it.copy(showSyncPassphraseDialog = true) }
        }

        fun dismissSyncPassphraseDialog() {
            _dialogState.update { it.copy(showSyncPassphraseDialog = false) }
        }

        fun enableDriveSync(passphrase: String) {
            _dialogState.update { it.copy(showSyncPassphraseDialog = false) }
            viewModelScope.launch {
                settingsRepository.setSyncPassphrase(passphrase)
                settingsRepository.setDriveSyncEnabled(true)
                syncScheduler.enablePeriodicSync()
                runSync()
            }
        }

        fun disableDriveSync() {
            viewModelScope.launch {
                syncScheduler.disablePeriodicSync()
                settingsRepository.clearSyncState()
            }
        }

        fun syncNow() {
            viewModelScope.launch { runSync() }
        }

        fun onDriveConsentResult(granted: Boolean) {
            viewModelScope.launch {
                if (granted) {
                    runSync()
                } else {
                    _events.send(SettingsEvent.ShowToast("Google Drive access was not granted"))
                }
            }
        }

        private suspend fun runSync() {
            when (val result = syncManager.sync()) {
                is SyncResult.ConsentRequired -> _events.send(SettingsEvent.RequestDriveConsent(result.pendingIntent))
                is SyncResult.Uploaded -> _events.send(SettingsEvent.ShowToast("Synced — uploaded to Drive"))
                is SyncResult.Downloaded -> _events.send(SettingsEvent.ShowToast("Synced — downloaded from Drive"))
                is SyncResult.UpToDate -> _events.send(SettingsEvent.ShowToast("Already up to date"))
                is SyncResult.Disabled -> Unit
                is SyncResult.Failed -> _events.send(SettingsEvent.ShowToast("Sync failed: ${result.message}"))
            }
        }
    }
