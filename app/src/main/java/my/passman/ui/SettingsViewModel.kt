package my.passman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.AppTheme
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _dialogState = MutableStateFlow(DialogState())

    val uiState: StateFlow<SettingsScreenState> = combine(
        settingsRepository.sortOrder,
        settingsRepository.appTheme,
        _dialogState
    ) { sortOrder, theme, dialogState ->
        SettingsScreenState(
            sortOrder = sortOrder,
            theme = theme,
            showSortDialog = dialogState.showSortDialog,
            showThemeDialog = dialogState.showThemeDialog
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsScreenState()
    )

    private data class DialogState(
        val showSortDialog: Boolean = false,
        val showThemeDialog: Boolean = false,
    )

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
}
