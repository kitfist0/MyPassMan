package my.passman.ui.screens.settings

import my.passman.data.AppTheme
import my.passman.data.SortOrder

data class SettingsScreenState(
    val sortOrder: SortOrder = SortOrder.BY_NAME,
    val theme: AppTheme = AppTheme.SYSTEM,
    val isPinEnabled: Boolean = false,
    val showSortDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val showBackupPasswordDialog: Boolean = false,
    val backupMode: BackupMode? = null,
)

enum class BackupMode {
    EXPORT, IMPORT
}
