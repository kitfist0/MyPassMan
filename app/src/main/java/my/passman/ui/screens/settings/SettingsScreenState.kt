package my.passman.ui.screens.settings

import my.passman.data.AppTheme
import my.passman.data.SortOrder
import my.passman.data.SyncProvider

data class SettingsScreenState(
    val sortOrder: SortOrder = SortOrder.BY_NAME,
    val theme: AppTheme = AppTheme.SYSTEM,
    val isPinEnabled: Boolean = false,
    val isFingerprintEnabled: Boolean = false,
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
    val syncProvider: SyncProvider = SyncProvider.NONE,
    val lastSyncedAt: Long? = null,
)

enum class BackupMode {
    EXPORT,
    IMPORT,
}
