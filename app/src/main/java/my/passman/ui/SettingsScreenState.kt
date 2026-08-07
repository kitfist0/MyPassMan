package my.passman.ui

import my.passman.data.AppTheme
import my.passman.data.SortOrder

data class SettingsScreenState(
    val sortOrder: SortOrder = SortOrder.BY_NAME,
    val theme: AppTheme = AppTheme.SYSTEM,
    val showSortDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
)
