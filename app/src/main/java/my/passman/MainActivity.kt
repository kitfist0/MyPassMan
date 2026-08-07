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
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import my.passman.data.Record
import my.passman.ui.EditRecordScreen
import my.passman.ui.EditRecordViewModel
import my.passman.ui.RecordListScreen
import my.passman.ui.RecordViewModel
import my.passman.ui.SettingsScreen
import my.passman.ui.theme.MyPassManTheme
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen {
    object List : Screen()
    data class Edit(val record: Record? = null) : Screen()
    object Settings : Screen()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: RecordViewModel = hiltViewModel()
            val appTheme by viewModel.appTheme.collectAsState()

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
                                    RecordListScreen(
                                        viewModel = viewModel,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        onAddRecord = { currentScreen = Screen.Edit() },
                                        onEditRecord = { record ->
                                            currentScreen = Screen.Edit(record)
                                        },
                                        onNavigateToSettings = { currentScreen = Screen.Settings }
                                    )
                                }

                                is Screen.Edit -> {
                                    val viewModelStoreOwner = remember {
                                        object : ViewModelStoreOwner {
                                            override val viewModelStore = ViewModelStore()
                                        }
                                    }
                                    DisposableEffect(viewModelStoreOwner) {
                                        onDispose { viewModelStoreOwner.viewModelStore.clear() }
                                    }
                                    CompositionLocalProvider(
                                        LocalViewModelStoreOwner provides viewModelStoreOwner
                                    ) {
                                        val editViewModel: EditRecordViewModel = viewModel(
                                            initializer = { EditRecordViewModel(targetScreen.record) }
                                        )
                                        EditRecordScreen(
                                            viewModel = editViewModel,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            onSave = { name, secret, comment ->
                                                if (targetScreen.record == null) {
                                                    viewModel.addRecord(name, secret, comment)
                                                } else {
                                                    viewModel.updateRecord(
                                                        targetScreen.record.copy(
                                                            name = name,
                                                            secret = secret,
                                                            comment = comment
                                                        )
                                                    )
                                                }
                                                currentScreen = Screen.List
                                            },
                                            onDelete = {
                                                targetScreen.record?.let { viewModel.deleteRecord(it) }
                                                currentScreen = Screen.List
                                            },
                                            onCancel = { currentScreen = Screen.List }
                                        )
                                    }
                                }

                                is Screen.Settings -> {
                                    val sortOrder by viewModel.sortOrder.collectAsState()
                                    SettingsScreen(
                                        currentSortOrder = sortOrder,
                                        onSortOrderChange = { viewModel.setSortOrder(it) },
                                        currentTheme = appTheme,
                                        onThemeChange = { viewModel.setAppTheme(it) },
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
