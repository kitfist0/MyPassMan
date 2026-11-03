package my.passman.ui

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import my.passman.ui.screens.edit.EditRecordScreen
import my.passman.ui.screens.edit.EditRecordViewModel
import my.passman.ui.screens.list.RecordListScreen
import my.passman.ui.screens.list.RecordListViewModel
import my.passman.ui.screens.pin.PinMode
import my.passman.ui.screens.pin.PinScreen
import my.passman.ui.screens.pin.PinViewModel
import my.passman.ui.screens.settings.SettingsScreen
import my.passman.ui.screens.settings.SettingsViewModel
import my.passman.ui.screens.tags.TagsScreen
import my.passman.ui.screens.tags.TagsViewModel
import my.passman.ui.theme.MyPassManTheme
import my.passman.ui.theme.resolveDarkTheme
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var eventBus: AppEventBus

    private val viewModel: MainViewModel by viewModels()

    /** Content stays invisible until the splash has faded out, so the two never show through each other. */
    private var isSplashGone by mutableStateOf(false)

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // A recreated activity (e.g. after rotation) gets no splash screen.
        isSplashGone = savedInstanceState != null
        installSplashScreen().apply {
            // Hold the splash until settings are loaded, instead of flashing an empty screen.
            setKeepOnScreenCondition { viewModel.isLoading.value }
            setOnExitAnimationListener { splashScreenView ->
                // On a fast device the settings load long before the icon animation ends, so let it
                // play out instead of cutting it off. The start is wall-clock time, and both values
                // are 0 below API 31, so there is no delay there.
                val iconAnimationEnd =
                    splashScreenView.iconAnimationStartMillis + splashScreenView.iconAnimationDurationMillis
                val remaining = (iconAnimationEnd - System.currentTimeMillis()).coerceIn(0, MAX_SPLASH_HOLD_MS)
                splashScreenView.view
                    .animate()
                    .alpha(0f)
                    .setStartDelay(remaining)
                    .setDuration(SPLASH_EXIT_DURATION_MS)
                    .withEndAction {
                        splashScreenView.remove()
                        isSplashGone = true
                    }.start()
            }
        }
        super.onCreate(savedInstanceState)
        // Keep the vault out of screenshots, screen recordings and the recents preview.
        // Compose dialogs and popups inherit the flag from this window by default.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current

            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            val lockScreen by viewModel.lockScreen.collectAsStateWithLifecycle()
            val hasBeenUnlocked by viewModel.hasBeenUnlocked.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                // The system skips the splash for some launches, and then never calls the exit listener.
                delay(SPLASH_FALLBACK_TIMEOUT)
                isSplashGone = true
            }
            val contentAlpha by animateFloatAsState(
                targetValue = if (isSplashGone) 1f else 0f,
                animationSpec = tween(CONTENT_FADE_IN_DURATION_MS),
                label = "ContentFadeIn",
            )

            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppBackgrounded() }
            LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppForegrounded() }

            val focusManager = LocalFocusManager.current
            LaunchedEffect(lockScreen) {
                // Otherwise a text field under the lock screen keeps the keyboard and receives input.
                if (lockScreen != null) focusManager.clearFocus(force = true)
            }

            LaunchedEffect(eventBus) {
                for (event in eventBus.events) {
                    when (event) {
                        is AppEvent.ShowToast -> {
                            Toast.makeText(context, event.text.resolve(context), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            val darkTheme = resolveDarkTheme(appTheme)
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            MyPassManTheme(appTheme = appTheme) {
                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    // FLAG_SECURE makes recents show a blank placeholder in the task background
                    // color, which otherwise follows the XML theme and ignores the in-app theme.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setTaskDescription(
                            ActivityManager.TaskDescription
                                .Builder()
                                .setBackgroundColor(backgroundColor)
                                .setStatusBarColor(backgroundColor)
                                .setNavigationBarColor(backgroundColor)
                                .build(),
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (isLoading) {
                        return@Surface
                    }

                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
                        if (hasBeenUnlocked) {
                            SharedTransitionLayout(
                                modifier =
                                    if (lockScreen != null) {
                                        // Hide the locked vault from accessibility services.
                                        Modifier.clearAndSetSemantics {}
                                    } else {
                                        Modifier
                                    },
                            ) {
                                AnimatedContent(
                                    targetState = currentScreen,
                                    transitionSpec = {
                                        if (targetState is Screen.Edit) {
                                            (slideInHorizontally { it } + fadeIn())
                                                .togetherWith(slideOutHorizontally { -it } + fadeOut())
                                        } else {
                                            (slideInHorizontally { -it } + fadeIn())
                                                .togetherWith(slideOutHorizontally { it } + fadeOut())
                                        }
                                    },
                                    label = "ScreenTransition",
                                ) { targetScreen ->
                                    val animatedVisibilityScope = this
                                    val sharedTransitionScope = this@SharedTransitionLayout

                                    when (targetScreen) {
                                        is Screen.List -> {
                                            val recordListViewModel: RecordListViewModel = hiltViewModel()
                                            RecordListScreen(
                                                viewModel = recordListViewModel,
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                onAddRecord = { viewModel.navigateTo(Screen.Edit(null)) },
                                                onEditRecord = { id ->
                                                    viewModel.navigateTo(Screen.Edit(id))
                                                },
                                                onNavigateToSettings = { viewModel.navigateTo(Screen.Settings) },
                                            )
                                        }

                                        is Screen.Edit -> {
                                            val editRecordViewModel: EditRecordViewModel =
                                                hiltViewModel(
                                                    key = "edit-${targetScreen.recordId}-${targetScreen.sessionKey}",
                                                    creationCallback = { factory: EditRecordViewModel.Factory ->
                                                        factory.create(targetScreen.recordId)
                                                    },
                                                )
                                            EditRecordScreen(
                                                viewModel = editRecordViewModel,
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                onSave = { viewModel.navigateTo(Screen.List) },
                                                onDelete = { viewModel.navigateTo(Screen.List) },
                                                onCancel = { viewModel.navigateTo(Screen.List) },
                                            )
                                        }

                                        is Screen.Settings -> {
                                            val settingsViewModel: SettingsViewModel = hiltViewModel()
                                            SettingsScreen(
                                                viewModel = settingsViewModel,
                                                onManageTags = { viewModel.navigateTo(Screen.Tags) },
                                                onSetupNewPin = { viewModel.navigateTo(Screen.Pin(PinMode.SET)) },
                                            ) {
                                                viewModel.navigateTo(Screen.List)
                                            }
                                        }

                                        is Screen.Tags -> {
                                            val tagsViewModel: TagsViewModel = hiltViewModel()
                                            TagsScreen(
                                                viewModel = tagsViewModel,
                                                onBack = { viewModel.navigateTo(Screen.Settings) },
                                            )
                                        }

                                        is Screen.Pin -> {
                                            val pinViewModel: PinViewModel =
                                                hiltViewModel(
                                                    key = "pin-${targetScreen.mode}-${targetScreen.sessionKey}",
                                                    creationCallback = { factory: PinViewModel.Factory ->
                                                        factory.create(targetScreen.mode)
                                                    },
                                                )
                                            PinScreen(
                                                viewModel = pinViewModel,
                                                onBack = { viewModel.navigateTo(Screen.Settings) },
                                                onSuccess = { viewModel.onPinSuccess(targetScreen.mode) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        lockScreen?.let { screen ->
                            val pinViewModel: PinViewModel =
                                hiltViewModel(
                                    key = "pin-${screen.mode}-${screen.sessionKey}",
                                    creationCallback = { factory: PinViewModel.Factory ->
                                        factory.create(screen.mode)
                                    },
                                )
                            // Registered after the screens' handlers, so it takes priority over them.
                            BackHandler { moveTaskToBack(true) }
                            PinScreen(
                                viewModel = pinViewModel,
                                onBack = {},
                                onSuccess = { viewModel.onPinSuccess(screen.mode) },
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val SPLASH_EXIT_DURATION_MS = 200L

        /** Guards against a clock change mid-launch holding the splash for long. */
        const val MAX_SPLASH_HOLD_MS = 1000L
        const val CONTENT_FADE_IN_DURATION_MS = 200
        val SPLASH_FALLBACK_TIMEOUT = 2.seconds
    }
}
