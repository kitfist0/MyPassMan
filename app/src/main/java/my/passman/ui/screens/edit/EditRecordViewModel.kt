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
    private var originalSecret: String = ""
    private var originalComment: String = ""
    private var originalCreated: Long = 0

    private fun EditRecordScreenState.hasChanges(): Boolean =
        !isLoading && (name != originalName || secret != originalSecret || comment != originalComment)

    private fun update(transform: (EditRecordScreenState) -> EditRecordScreenState) {
        _uiState.update { state ->
            transform(state).let {
                it.copy(
                    canSave = it.name.isNotBlank() && it.secret.isNotBlank() &&
                            (it.recordId == null || it.hasChanges())
                )
            }
        }
    }

    init {
        if (recordId != null) {
            viewModelScope.launch {
                val record = recordDao.getRecordById(recordId)
                if (record != null) {
                    originalName = record.name
                    originalSecret = record.secret
                    originalComment = record.comment
                    originalCreated = record.created
                    update {
                        it.copy(
                            isLoading = false,
                            name = record.name,
                            secret = record.secret,
                            comment = record.comment
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
