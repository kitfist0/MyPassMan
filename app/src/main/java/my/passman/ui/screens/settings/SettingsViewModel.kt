package my.passman.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.AppTheme
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import my.passman.data.SyncProvider
import my.passman.sync.SyncResult
import my.passman.sync.SyncScheduler
import my.passman.sync.google.GoogleSyncManager
import my.passman.sync.yandex.YandexAuthManager
import my.passman.sync.yandex.YandexSyncManager
import my.passman.ui.AppEvent
import my.passman.ui.AppEventBus
import my.passman.util.BackupManager
import my.passman.util.BiometricAvailability
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.BadPaddingException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val googleSyncManager: GoogleSyncManager,
    private val yandexSyncManager: YandexSyncManager,
    private val yandexAuthManager: YandexAuthManager,
    private val syncScheduler: SyncScheduler,
    private val biometricAvailability: BiometricAvailability,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _dialogState = MutableStateFlow(DialogState())

    val isFingerprintAvailable: Boolean = biometricAvailability.isAvailable()

    val yandexAuthorizeUrl: String get() = yandexAuthManager.authorizeUrl
    val yandexRedirectUri: String get() = yandexAuthManager.redirectUri

    private data class SyncSettings(
        val provider: SyncProvider,
        val lastSyncedAt: Long?,
    )

    private data class PinSettings(
        val isPinEnabled: Boolean,
        val isFingerprintEnabled: Boolean,
    )

    private val syncSettings: Flow<SyncSettings> =
        combine(
            settingsRepository.syncProvider,
            settingsRepository.lastSyncedAt,
        ) { provider, lastSyncedAt -> SyncSettings(provider, lastSyncedAt) }

    private val pinSettings: Flow<PinSettings> =
        combine(
            settingsRepository.pinHash,
            settingsRepository.fingerprintEnabled,
        ) { pinHash, fingerprintEnabled -> PinSettings(pinHash != null, fingerprintEnabled) }

    val uiState: StateFlow<SettingsScreenState> =
        combine(
            settingsRepository.sortOrder,
            settingsRepository.appTheme,
            pinSettings,
            _dialogState,
            syncSettings,
        ) { sortOrder, theme, pin, dialogState, sync ->
            SettingsScreenState(
                sortOrder = sortOrder,
                theme = theme,
                isPinEnabled = pin.isPinEnabled,
                isFingerprintEnabled = pin.isFingerprintEnabled,
                showSortDialog = dialogState.showSortDialog,
                showThemeDialog = dialogState.showThemeDialog,
                showAboutDialog = dialogState.showAboutDialog,
                showBackupPasswordDialog = dialogState.showBackupPasswordDialog,
                showDisablePinDialog = dialogState.showDisablePinDialog,
                showSyncProviderDialog = dialogState.showSyncProviderDialog,
                showSyncPassphraseDialog = dialogState.showSyncPassphraseDialog,
                showYandexLoginDialog = dialogState.showYandexLoginDialog,
                showResetBackupDialog = dialogState.showResetBackupDialog,
                backupMode = dialogState.backupMode,
                syncProvider = sync.provider,
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
        val showSyncProviderDialog: Boolean = false,
        val showSyncPassphraseDialog: Boolean = false,
        val showYandexLoginDialog: Boolean = false,
        val showResetBackupDialog: Boolean = false,
        val backupMode: BackupMode? = null,
    )

    private var pendingPassword = charArrayOf()

    fun onExportClick() {
        _dialogState.update {
            it.copy(
                showBackupPasswordDialog = true,
                backupMode = BackupMode.EXPORT,
            )
        }
    }

    fun onImportClick() {
        viewModelScope.launch {
            eventBus.send(AppEvent.RequestImportFile)
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

    fun setFingerprintEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFingerprintEnabled(enabled)
        }
    }

    fun onImportFileSelected(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                pendingImportData = inputStream.use { it.readBytes() }
                _dialogState.update {
                    it.copy(
                        showBackupPasswordDialog = true,
                        backupMode = BackupMode.IMPORT,
                    )
                }
            } catch (e: Exception) {
                eventBus.send(AppEvent.ShowToast("Failed to read file: ${e.message}"))
            }
        }
    }

    fun onBackupPasswordEntered(password: String) {
        pendingPassword = password.toCharArray()
        val mode = _dialogState.value.backupMode
        _dialogState.update { it.copy(showBackupPasswordDialog = false, backupMode = null) }

        viewModelScope.launch {
            if (mode == BackupMode.EXPORT) {
                eventBus.send(AppEvent.RequestExportFile)
            } else if (mode == BackupMode.IMPORT) {
                executeImport()
            }
        }
    }

    fun executeExport(outputStream: OutputStream) {
        viewModelScope.launch {
            try {
                backupManager.exportDatabase(outputStream, pendingPassword)
                eventBus.send(AppEvent.ShowToast("Export successful"))
            } catch (e: Exception) {
                eventBus.send(AppEvent.ShowToast("Export failed: ${e.message}"))
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
                eventBus.send(AppEvent.ShowToast("Import successful"))
            } catch (_: BadPaddingException) {
                eventBus.send(AppEvent.ShowToast("Import failed: incorrect password"))
            } catch (e: Exception) {
                eventBus.send(AppEvent.ShowToast("Import failed: ${e.message}"))
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

    fun showSyncProviderDialog() {
        _dialogState.update { it.copy(showSyncProviderDialog = true) }
    }

    fun dismissSyncProviderDialog() {
        _dialogState.update { it.copy(showSyncProviderDialog = false) }
    }

    fun dismissSyncPassphraseDialog() {
        _dialogState.update { it.copy(showSyncPassphraseDialog = false) }
        pendingSyncProvider = SyncProvider.NONE
    }

    fun dismissYandexLoginDialog() {
        _dialogState.update { it.copy(showYandexLoginDialog = false) }
        viewModelScope.launch { abortSyncSetupIfPending() }
    }

    private var isSettingUpSync = false
    private var pendingSyncProvider: SyncProvider = SyncProvider.NONE

    /** Called after the user picks a provider from the sync provider dialog. */
    fun onSyncProviderSelected(provider: SyncProvider) {
        _dialogState.update { it.copy(showSyncProviderDialog = false) }
        if (provider == SyncProvider.NONE) {
            disableSync()
        } else {
            pendingSyncProvider = provider
            _dialogState.update { it.copy(showSyncPassphraseDialog = true) }
        }
    }

    /** Called once the local backup encryption passphrase has been entered. */
    fun enableSync(passphrase: String) {
        val provider = pendingSyncProvider
        _dialogState.update { it.copy(showSyncPassphraseDialog = false) }
        viewModelScope.launch {
            settingsRepository.setSyncPassphrase(passphrase)
            settingsRepository.setSyncProvider(provider)
            syncScheduler.enablePeriodicSync()
            isSettingUpSync = true
            runSync()
        }
    }

    fun disableSync() {
        viewModelScope.launch {
            syncScheduler.disablePeriodicSync()
            settingsRepository.clearSyncState()
        }
    }

    fun syncNow() {
        viewModelScope.launch { runSync() }
    }

    /** Called once the Yandex OAuth WebView captures an access token from the redirect. */
    fun onYandexTokenReceived(accessToken: String) {
        _dialogState.update { it.copy(showYandexLoginDialog = false) }
        viewModelScope.launch {
            yandexAuthManager.saveToken(accessToken)
            runSync()
        }
    }

    fun dismissResetBackupDialog() {
        _dialogState.update { it.copy(showResetBackupDialog = false) }
        viewModelScope.launch { abortSyncSetupIfPending() }
    }

    fun confirmResetBackup() {
        _dialogState.update { it.copy(showResetBackupDialog = false) }
        viewModelScope.launch {
            val result =
                when (settingsRepository.syncProvider.first()) {
                    SyncProvider.GOOGLE_DRIVE -> googleSyncManager.resetRemoteBackup()
                    SyncProvider.YANDEX_DISK -> yandexSyncManager.resetRemoteBackup()
                    SyncProvider.NONE -> SyncResult.Disabled
                }
            when (result) {
                is SyncResult.ConsentRequired ->
                    eventBus.send(AppEvent.RequestDriveConsent(result.pendingIntent))

                SyncResult.LoginRequired ->
                    _dialogState.update { it.copy(showYandexLoginDialog = true) }

                is SyncResult.Uploaded -> {
                    isSettingUpSync = false
                    eventBus.send(AppEvent.ShowToast("Old backup deleted — uploaded a fresh copy"))
                }

                is SyncResult.Failed -> {
                    abortSyncSetupIfPending()
                    eventBus.send(AppEvent.ShowToast("Reset failed: ${result.message}"))
                }

                else -> Unit
            }
        }
    }

    fun onDriveConsentResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) {
                runSync()
            } else {
                abortSyncSetupIfPending()
                eventBus.send(AppEvent.ShowToast("Google Drive access was not granted"))
            }
        }
    }

    private suspend fun abortSyncSetupIfPending() {
        if (isSettingUpSync) {
            syncScheduler.disablePeriodicSync()
            settingsRepository.clearSyncState()
        }
        isSettingUpSync = false
    }

    private suspend fun runSync() {
        val result =
            when (settingsRepository.syncProvider.first()) {
                SyncProvider.GOOGLE_DRIVE -> googleSyncManager.sync()
                SyncProvider.YANDEX_DISK -> yandexSyncManager.sync()
                SyncProvider.NONE -> SyncResult.Disabled
            }
        when (result) {
            is SyncResult.ConsentRequired ->
                eventBus.send(AppEvent.RequestDriveConsent(result.pendingIntent))

            SyncResult.LoginRequired ->
                _dialogState.update { it.copy(showYandexLoginDialog = true) }

            is SyncResult.Uploaded -> {
                isSettingUpSync = false
                eventBus.send(AppEvent.ShowToast("Synced — uploaded to the cloud"))
            }

            is SyncResult.Downloaded -> {
                isSettingUpSync = false
                eventBus.send(AppEvent.ShowToast("Synced — downloaded from the cloud"))
            }

            is SyncResult.UpToDate -> {
                isSettingUpSync = false
                eventBus.send(AppEvent.ShowToast("Already up to date"))
            }

            is SyncResult.Disabled -> isSettingUpSync = false

            is SyncResult.InvalidPassphrase -> {
                _dialogState.update { it.copy(showResetBackupDialog = true) }
            }

            is SyncResult.Failed -> {
                abortSyncSetupIfPending()
                eventBus.send(AppEvent.ShowToast("Sync failed: ${result.message}"))
            }
        }
    }
}
