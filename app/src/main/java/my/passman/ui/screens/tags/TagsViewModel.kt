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
    private val tagDao: TagDao,
) : ViewModel() {
    private val _dialogState = MutableStateFlow(TagsDialogState())

    val uiState: StateFlow<TagsScreenState> =
        combine(
            tagDao.getAllTags(),
            _dialogState,
        ) { tags, dialogState ->
            TagsScreenState(
                tags = tags,
                dialog = dialogState,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TagsScreenState(),
        )

    fun onTagNameChange(name: String) {
        if (name.length <= Tag.MAX_NAME_LENGTH) {
            _dialogState.update { it.copy(tagName = name) }
        }
    }

    fun showAddDialog() {
        _dialogState.update { it.copy(activeDialog = TagsDialog.Add, tagName = "") }
    }

    fun dismissDialog() {
        _dialogState.update { it.copy(activeDialog = null, tagName = "") }
    }

    fun showDeleteConfirmation(tag: Tag) {
        _dialogState.update { it.copy(activeDialog = TagsDialog.Delete(tag)) }
    }

    fun showEditDialog(tag: Tag) {
        _dialogState.update { it.copy(activeDialog = TagsDialog.Edit(tag), tagName = tag.name) }
    }

    fun onAddDialogConfirmButtonClick() {
        val name = _dialogState.value.tagName
        if (name.isBlank()) return
        viewModelScope.launch {
            tagDao.upsertTag(Tag(name = name.trim()))
        }
    }

    fun onEditDialogConfirmButtonClick() {
        val state = _dialogState.value
        val activeDialog = state.activeDialog
        if (activeDialog is TagsDialog.Edit) {
            val tag = activeDialog.tag
            val newName = state.tagName
            if (newName.isBlank() || newName == tag.name) return
            viewModelScope.launch {
                tagDao.upsertTag(tag.copy(name = newName.trim()))
            }
        }
    }

    fun onDeleteDialogConfirmButtonClick(tag: Tag) {
        viewModelScope.launch {
            tagDao.deleteTag(tag)
        }
    }
}
