package my.passman

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import my.passman.data.SettingsRepository
import my.passman.ui.screens.edit.EditRecordScreen
import my.passman.ui.screens.edit.EditRecordViewModel
import my.passman.ui.screens.list.RecordListScreen
import my.passman.ui.screens.list.RecordListViewModel
import my.passman.ui.screens.settings.SettingsScreen
import my.passman.ui.screens.pin.PinMode
import my.passman.ui.screens.pin.PinScreen
import my.passman.ui.screens.pin.PinViewModel
import my.passman.ui.screens.settings.SettingsViewModel
import my.passman.ui.screens.tags.TagsScreen
import my.passman.ui.screens.tags.TagsViewModel
import my.passman.ui.theme.MyPassManTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.UUID

sealed class Screen {
    data object List : Screen()
    data class Edit(val recordId: Long? = null, val sessionKey: String = UUID.randomUUID().toString()) : Screen()
    data object Settings : Screen()
    data object Tags : Screen()
    data class Pin(val mode: PinMode) : Screen()
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
            val context = LocalContext.current
            val appTheme by settingsRepository.appTheme.collectAsState(initial = my.passman.data.AppTheme.SYSTEM)

            MyPassManTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.List) }

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
                                        key = "edit-${targetScreen.recordId}-${targetScreen.sessionKey}",
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

                                    val createDocumentLauncher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.CreateDocument("application/octet-stream")
                                    ) { uri ->
                                        uri?.let {
                                            context.contentResolver.openOutputStream(it)?.let { os ->
                                                settingsViewModel.executeExport(os)
                                            }
                                        }
                                    }

                                    val openDocumentLauncher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.OpenDocument()
                                    ) { uri ->
                                        uri?.let {
                                            context.contentResolver.openInputStream(it)?.let { isStream ->
                                                settingsViewModel.onImportFileSelected(isStream)
                                            }
                                        }
                                    }

                                    LaunchedEffect(settingsViewModel) {
                                        for (event in settingsViewModel.events) {
                                            when (event) {
                                                SettingsViewModel.SettingsEvent.RequestExportFile -> {
                                                    createDocumentLauncher.launch("mypassman_backup.pman")
                                                }
                                                SettingsViewModel.SettingsEvent.RequestImportFile -> {
                                                    openDocumentLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                                }
                                                is SettingsViewModel.SettingsEvent.ShowToast -> {
                                                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }

                                    SettingsScreen(
                                        viewModel = settingsViewModel,
                                        onManageTags = { currentScreen = Screen.Tags },
                                        onBack = { currentScreen = Screen.List }
                                    )
                                }

                                is Screen.Tags -> {
                                    val tagsViewModel: TagsViewModel = hiltViewModel()
                                    TagsScreen(
                                        viewModel = tagsViewModel,
                                        onBack = { currentScreen = Screen.Settings }
                                    )
                                }

                                is Screen.Pin -> {
                                    val pinViewModel: PinViewModel = hiltViewModel(
                                        creationCallback = { factory: PinViewModel.Factory ->
                                            factory.create(targetScreen.mode)
                                        }
                                    )
                                    PinScreen(
                                        viewModel = pinViewModel,
                                        onSuccess = { currentScreen = Screen.List }
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
