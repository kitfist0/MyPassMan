package my.passman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import my.passman.data.SettingsRepository
import my.passman.ui.EditRecordScreen
import my.passman.ui.EditRecordViewModel
import my.passman.ui.RecordListScreen
import my.passman.ui.RecordListViewModel
import my.passman.ui.SettingsScreen
import my.passman.ui.SettingsViewModel
import my.passman.ui.theme.MyPassManTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed class Screen {
    object List : Screen()
    data class Edit(val recordId: Long? = null) : Screen()
    object Settings : Screen()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appTheme by settingsRepository.appTheme.collectAsState(initial = my.passman.data.AppTheme.SYSTEM)

            MyPassManTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.List) }

                    BackHandler(enabled = currentScreen is Screen.Edit) {
                        currentScreen = Screen.List
                    }

                    SharedTransitionLayout {
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
                            label = "ScreenTransition"
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
                                        onAddRecord = { currentScreen = Screen.Edit(null) },
                                        onEditRecord = { id ->
                                            currentScreen = Screen.Edit(id)
                                        },
                                        onNavigateToSettings = { currentScreen = Screen.Settings }
                                    )
                                }

                                is Screen.Edit -> {
                                    val editRecordViewModel: EditRecordViewModel = hiltViewModel(
                                        key = targetScreen.recordId?.let { "edit-$it" } ?: "create",
                                        creationCallback = { factory: EditRecordViewModel.Factory ->
                                            factory.create(targetScreen.recordId)
                                        }
                                    )
                                    EditRecordScreen(
                                        viewModel = editRecordViewModel,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        onSave = { currentScreen = Screen.List },
                                        onDelete = { currentScreen = Screen.List },
                                        onCancel = { currentScreen = Screen.List }
                                    )
                                }

                                is Screen.Settings -> {
                                    val settingsViewModel: SettingsViewModel = hiltViewModel()
                                    SettingsScreen(
                                        viewModel = settingsViewModel,
                                        onBack = { currentScreen = Screen.List }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
