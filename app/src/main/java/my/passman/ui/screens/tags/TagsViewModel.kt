package my.passman.ui.screens.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.passman.data.Tag
import my.passman.data.TagDao
import javax.inject.Inject

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagDao: TagDao
) : ViewModel() {

    private val _dialogState = MutableStateFlow(DialogState())

    val uiState: StateFlow<TagsScreenState> = combine(
        tagDao.getAllTags(),
        _dialogState
    ) { tags, dialogState ->
        TagsScreenState(
            tags = tags,
            enteredTagName = dialogState.enteredTagName,
            showAddDialog = dialogState.showAddDialog,
            tagToDelete = dialogState.tagToDelete,
            tagToEdit = dialogState.tagToEdit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TagsScreenState()
    )

    private data class DialogState(
        val enteredTagName: String = "",
        val showAddDialog: Boolean = false,
        val tagToDelete: Tag? = null,
        val tagToEdit: Tag? = null
    )

    fun onTagNameChange(name: String) {
        if (name.length <= Tag.MAX_NAME_LENGTH) {
            _dialogState.update { it.copy(enteredTagName = name) }
        }
    }

    fun showAddDialog() {
        _dialogState.update { it.copy(showAddDialog = true, enteredTagName = "") }
    }

    fun dismissAddDialog() {
        _dialogState.update { it.copy(showAddDialog = false, enteredTagName = "") }
    }

    fun showDeleteConfirmation(tag: Tag) {
        _dialogState.update { it.copy(tagToDelete = tag) }
    }

    fun dismissDeleteConfirmation() {
        _dialogState.update { it.copy(tagToDelete = null) }
    }

    fun showEditDialog(tag: Tag) {
        _dialogState.update { it.copy(tagToEdit = tag, enteredTagName = tag.name) }
    }

    fun dismissEditDialog() {
        _dialogState.update { it.copy(tagToEdit = null, enteredTagName = "") }
    }

    fun addTag() {
        val name = _dialogState.value.enteredTagName
        if (name.isBlank()) return
        viewModelScope.launch {
            tagDao.upsertTag(Tag(name = name.trim()))
        }
    }

    fun updateTag() {
        val state = _dialogState.value
        val tag = state.tagToEdit ?: return
        val newName = state.enteredTagName
        if (newName.isBlank() || newName == tag.name) return
        viewModelScope.launch {
            tagDao.upsertTag(tag.copy(name = newName.trim()))
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            tagDao.deleteTag(tag)
        }
    }
}
