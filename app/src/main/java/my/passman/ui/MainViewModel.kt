package my.passman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.AppTheme
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.sync.SyncManager
import my.passman.sync.SyncScheduler
import my.passman.sync.yandex.YandexSyncManager
import my.passman.ui.screens.pin.PinMode
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val yandexSyncManager: YandexSyncManager,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {
    private val _currentScreen = MutableStateFlow<Screen>(Screen.List)
    val currentScreen = _currentScreen.asStateFlow()

    private var isAuthorized = false

    val appTheme: StateFlow<AppTheme> =
        settingsRepository.appTheme
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    private val _isLoading = MutableStateFlow(value = true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.pinHash.collect { hash ->
                if (hash != null && (!isAuthorized)) {
                    _currentScreen.value = Screen.Pin(PinMode.UNLOCK)
                } else {
                    isAuthorized = true
                }
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            when (settingsRepository.syncProvider.first()) {
                SyncProvider.GOOGLE_DRIVE -> {
                    syncScheduler.enablePeriodicSync()
                    syncManager.sync()
                }
                SyncProvider.YANDEX_DISK -> {
                    syncScheduler.enablePeriodicSync()
                    yandexSyncManager.sync()
                }
                SyncProvider.NONE -> Unit
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun onPinSuccess(mode: PinMode) {
        isAuthorized = true
        _currentScreen.value =
            if (mode == PinMode.SET || mode == PinMode.CONFIRM) {
                Screen.Settings()
            } else {
                Screen.List
            }
    }
}
