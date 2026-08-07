package my.passman.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import my.passman.data.Record

class EditRecordViewModel(val record: Record?) : ViewModel() {

    private val originalName = record?.name.orEmpty()
    private val originalSecret = record?.secret.orEmpty()
    private val originalComment = record?.comment.orEmpty()

    private val _uiState = MutableStateFlow(
        EditRecordScreenState(
            name = originalName,
            secret = originalSecret,
            comment = originalComment
        )
    )
    val uiState = _uiState.asStateFlow()

    val hasChanges = _uiState.map { state ->
        state.name != originalName ||
            state.secret != originalSecret ||
            state.comment != originalComment
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onSecretChange(value: String) {
        _uiState.update { it.copy(secret = value) }
    }

    fun onCommentChange(value: String) {
        _uiState.update { it.copy(comment = value.replace("\n", "")) }
    }

    fun toggleSecretVisibility() {
        _uiState.update { it.copy(secretVisible = !it.secretVisible) }
    }

    fun showExitDialog() {
        _uiState.update { it.copy(showExitDialog = true) }
    }

    fun dismissExitDialog() {
        _uiState.update { it.copy(showExitDialog = false) }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }
}
