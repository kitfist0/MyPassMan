package my.passman.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current

            val viewModel: MainViewModel = hiltViewModel()
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

            MyPassManTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (isLoading) {
                        return@Surface
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

                                    val createDocumentLauncher =
                                        rememberLauncherForActivityResult(
                                            ActivityResultContracts.CreateDocument("application/octet-stream"),
                                        ) { uri ->
                                            uri?.let {
                                                context.contentResolver.openOutputStream(it)?.let { os ->
                                                    settingsViewModel.executeExport(os)
                                                }
                                            }
                                        }

                                    val openDocumentLauncher =
                                        rememberLauncherForActivityResult(
                                            ActivityResultContracts.OpenDocument(),
                                        ) { uri ->
                                            uri?.let {
                                                context.contentResolver.openInputStream(it)?.let { isStream ->
                                                    settingsViewModel.onImportFileSelected(isStream)
                                                }
                                            }
                                        }

                                    val driveConsentLauncher =
                                        rememberLauncherForActivityResult(
                                            ActivityResultContracts.StartIntentSenderForResult(),
                                        ) { result ->
                                            settingsViewModel.onDriveConsentResult(result.resultCode == Activity.RESULT_OK)
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
                                                is SettingsViewModel.SettingsEvent.RequestDriveConsent -> {
                                                    driveConsentLauncher.launch(
                                                        IntentSenderRequest.Builder(event.pendingIntent.intentSender).build(),
                                                    )
                                                }
                                                is SettingsViewModel.SettingsEvent.ShowToast -> {
                                                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }

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
            }
        }
    }
}
