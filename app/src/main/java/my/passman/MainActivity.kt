package my.passman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import my.passman.data.Record
import my.passman.ui.EditRecordScreen
import my.passman.ui.RecordListScreen
import my.passman.ui.RecordViewModel
import my.passman.ui.theme.MyPassManTheme

sealed class Screen {
    object List : Screen()
    data class Edit(val record: Record? = null) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyPassManTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: RecordViewModel = viewModel()
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.List) }

                    when (val screen = currentScreen) {
                        is Screen.List -> {
                            RecordListScreen(
                                viewModel = viewModel,
                                onAddRecord = { currentScreen = Screen.Edit() },
                                onEditRecord = { record -> currentScreen = Screen.Edit(record) }
                            )
                        }
                        is Screen.Edit -> {
                            EditRecordScreen(
                                record = screen.record,
                                onSave = { name, secret ->
                                    if (screen.record == null) {
                                        viewModel.addRecord(name, secret, "")
                                    } else {
                                        viewModel.updateRecord(screen.record.copy(name = name, secret = secret))
                                    }
                                    currentScreen = Screen.List
                                },
                                onDelete = {
                                    screen.record?.let { viewModel.deleteRecord(it) }
                                    currentScreen = Screen.List
                                },
                                onCancel = { currentScreen = Screen.List }
                            )
                        }
                    }
                }
            }
        }
    }
}
