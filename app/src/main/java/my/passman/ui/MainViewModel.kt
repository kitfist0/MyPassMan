package my.passman.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.R
import my.passman.data.AppTheme
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.sync.SyncScheduler
import my.passman.sync.google.GoogleSyncManager
import my.passman.sync.yandex.YandexSyncManager
import my.passman.ui.screens.pin.PinMode
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val googleSyncManager: GoogleSyncManager,
    private val yandexSyncManager: YandexSyncManager,
    private val syncScheduler: SyncScheduler,
    private val eventBus: AppEventBus,
) : ViewModel() {
    private val _currentScreen = MutableStateFlow<Screen>(Screen.List)
    val currentScreen = _currentScreen.asStateFlow()

    private var isAuthorized = false
    private var isPinEnabled = false
    private var backgroundedAt: Long? = null

    /** Shown over [currentScreen] while the vault is locked, so the screen underneath keeps its state. */
    private val _lockScreen = MutableStateFlow<Screen.Pin?>(null)
    val lockScreen = _lockScreen.asStateFlow()

    /** False until the first unlock, so the vault is not composed behind the lock screen on a cold start. */
    private val _hasBeenUnlocked = MutableStateFlow(value = false)
    val hasBeenUnlocked = _hasBeenUnlocked.asStateFlow()

    val appTheme: StateFlow<AppTheme> =
        settingsRepository.appTheme
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    private val _isLoading = MutableStateFlow(value = true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.pinHash.collect { hash ->
                isPinEnabled = hash != null
                if (hash != null && (!isAuthorized)) {
                    _lockScreen.value = Screen.Pin(PinMode.UNLOCK)
                } else {
                    isAuthorized = true
                    _hasBeenUnlocked.value = true
                }
                _isLoading.value = false
            }
        }

        viewModelScope.launch {
            when (settingsRepository.syncProvider.first()) {
                SyncProvider.GOOGLE_DRIVE -> {
                    syncScheduler.enablePeriodicSync()
                    googleSyncManager.sync()
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

    fun onImportDatabaseClick() {
        _currentScreen.value = Screen.Settings
        viewModelScope.launch {
            eventBus.send(AppEvent.ShowToast(textOf(R.string.import_hint_toast)))
        }
    }

    fun onPinSuccess(mode: PinMode) {
        isAuthorized = true
        if (mode == PinMode.UNLOCK) {
            _lockScreen.value = null
            _hasBeenUnlocked.value = true
        } else {
            _currentScreen.value = Screen.Settings
        }
    }

    fun onAppBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        // elapsedRealtime is monotonic, so changing the wall clock cannot skip the lock.
        val timeInBackground = (SystemClock.elapsedRealtime() - since).milliseconds
        if (isPinEnabled && isAuthorized && timeInBackground >= AUTO_LOCK_TIMEOUT) {
            isAuthorized = false
            _lockScreen.value = Screen.Pin(PinMode.UNLOCK)
        }
    }

    private companion object {
        val AUTO_LOCK_TIMEOUT = 30.seconds
    }
}
