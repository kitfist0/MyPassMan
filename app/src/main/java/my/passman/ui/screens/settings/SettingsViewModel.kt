package my.passman.ui.screens.settings

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
    ) : ViewModel() {
        private val _dialogState = MutableStateFlow(DialogState())

        private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
        val events: ReceiveChannel<SettingsEvent> = _events

        val uiState: StateFlow<SettingsScreenState> =
            combine(
                settingsRepository.sortOrder,
                settingsRepository.appTheme,
                settingsRepository.pinHash,
                _dialogState,
            ) { sortOrder, theme, pinHash, dialogState ->
                SettingsScreenState(
                    sortOrder = sortOrder,
                    theme = theme,
                    isPinEnabled = pinHash != null,
                    showSortDialog = dialogState.showSortDialog,
                    showThemeDialog = dialogState.showThemeDialog,
                    showAboutDialog = dialogState.showAboutDialog,
                    showBackupPasswordDialog = dialogState.showBackupPasswordDialog,
                    backupMode = dialogState.backupMode,
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
            val backupMode: BackupMode? = null,
        )

        sealed class SettingsEvent {
            data object RequestExportFile : SettingsEvent()

            data object RequestImportFile : SettingsEvent()

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
    }
