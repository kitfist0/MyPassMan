package my.passman.ui.screens.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.passman.data.Record
import my.passman.data.RecordDao

@HiltViewModel(assistedFactory = EditRecordViewModel.Factory::class)
class EditRecordViewModel @AssistedInject constructor(
    private val recordDao: RecordDao,
    @Assisted val recordId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditRecordScreenState(recordId = recordId, isLoading = recordId != null)
    )
    val uiState = _uiState.asStateFlow()

    private val _exitEvent = Channel<Unit>(Channel.BUFFERED)
    val exitEvent: ReceiveChannel<Unit> = _exitEvent

    private var originalName: String = ""
    private var originalLogin: String = ""
    private var originalSecret: String = ""
    private var originalComment: String = ""
    private var originalCreated: Long = 0

    private fun EditRecordScreenState.hasChanges(): Boolean {
        if (isLoading || recordId == null && name.isEmpty() && login.isEmpty() && secret.isEmpty() && comment.isEmpty()) {
            return false
        }
        return name != originalName || login != originalLogin || secret != originalSecret || comment != originalComment
    }

    private fun update(transform: (EditRecordScreenState) -> EditRecordScreenState) {
        _uiState.update { state ->
            val newState = transform(state)
            newState.copy(
                canSave = newState.name.isNotBlank() && newState.secret.isNotBlank() &&
                        (newState.recordId == null || newState.hasChanges())
            )
        }
    }

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val record = recordDao.getRecordById(recordId)
                if (record != null) {
                    originalName = record.name
                    originalLogin = record.login
                    originalSecret = record.secret
                    originalComment = record.comment
                    originalCreated = record.created
                    // Сначала обновляем данные, isLoading станет false и hasChanges() вернет false
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = record.name,
                            login = record.login,
                            secret = record.secret,
                            comment = record.comment,
                            canSave = false,
                            created = record.created,
                            modified = record.modified
                        )
                    }
                } else {
                    update { it.copy(isLoading = false, recordId = null) }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        update { it.copy(name = value) }
    }

    fun onLoginChange(value: String) {
        update { it.copy(login = value) }
    }

    fun onSecretChange(value: String) {
        update { it.copy(secret = value) }
    }

    fun onCommentChange(value: String) {
        update { it.copy(comment = value.replace("\n", "")) }
    }

    fun toggleSecretVisibility() {
        update { it.copy(secretVisible = !it.secretVisible) }
    }

    fun onBackPressed() {
        val state = _uiState.value
        if (state.hasChanges() && state.canSave) {
            showExitDialog()
        } else {
            _exitEvent.trySend(Unit)
        }
    }

    fun showExitDialog() {
        update { it.copy(showExitDialog = true) }
    }

    fun dismissExitDialog() {
        update { it.copy(showExitDialog = false) }
    }

    fun showDeleteDialog() {
        update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        update { it.copy(showDeleteDialog = false) }
    }

    fun save(): Long? {
        val state = _uiState.value
        return if (state.recordId == null) {
            val now = System.currentTimeMillis()
            val record = Record(
                created = now,
                modified = now,
                name = state.name,
                login = state.login,
                secret = state.secret,
                comment = state.comment
            )
            viewModelScope.launch { recordDao.insertRecord(record) }
            null
        } else {
            viewModelScope.launch {
                recordDao.updateRecord(
                    Record(
                        id = state.recordId,
                        created = originalCreated,
                        modified = System.currentTimeMillis(),
                        name = state.name,
                        login = state.login,
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
            recordDao.getRecordById(id)?.let { recordDao.deleteRecord(it) }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(recordId: Long?): EditRecordViewModel
    }
}
