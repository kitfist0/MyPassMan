package my.passman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.passman.data.Record
import my.passman.data.RecordDao

@HiltViewModel(assistedFactory = EditRecordViewModel.Factory::class)
class EditRecordViewModel @AssistedInject constructor(
    private val dao: RecordDao,
    @Assisted val recordId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditRecordScreenState(recordId = recordId, isLoading = recordId != null)
    )
    val uiState = _uiState.asStateFlow()

    val hasChanges = _uiState.map { state ->
        !state.isLoading && (
            state.name != originalName ||
                state.secret != originalSecret ||
                state.comment != originalComment
        )
    }

    private var originalName: String = ""
    private var originalSecret: String = ""
    private var originalComment: String = ""
    private var originalCreated: Long = 0

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val record = dao.getRecordById(recordId)
                if (record != null) {
                    originalName = record.name
                    originalSecret = record.secret
                    originalComment = record.comment
                    originalCreated = record.created
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = record.name,
                            secret = record.secret,
                            comment = record.comment
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, recordId = null) }
                }
            }
        }
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

    fun save(): Long? {
        val state = _uiState.value
        return if (state.recordId == null) {
            val now = System.currentTimeMillis()
            val record = Record(
                created = now,
                modified = now,
                name = state.name,
                secret = state.secret,
                comment = state.comment
            )
            viewModelScope.launch { dao.insertRecord(record) }
            null
        } else {
            viewModelScope.launch {
                dao.updateRecord(
                    Record(
                        id = state.recordId,
                        created = originalCreated,
                        modified = System.currentTimeMillis(),
                        name = state.name,
                        secret = state.secret,
                        comment = state.comment
                    )
                )
            }
            state.recordId
        }
    }

    fun delete() {
        val id = _uiState.value.recordId ?: return
        viewModelScope.launch {
            dao.getRecordById(id)?.let { dao.deleteRecord(it) }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(recordId: Long?): EditRecordViewModel
    }
}
