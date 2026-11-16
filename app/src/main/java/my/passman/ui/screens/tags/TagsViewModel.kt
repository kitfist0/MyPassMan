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
import my.passman.data.SaveTagResult
import my.passman.data.Tag
import my.passman.data.TagColor
import my.passman.data.TagRepository
import javax.inject.Inject

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val _dialogState = MutableStateFlow(TagsDialogState())

    val uiState: StateFlow<TagsScreenState> =
        combine(
            tagRepository.getAllTags(),
            _dialogState,
        ) { tags, dialogState ->
            TagsScreenState(
                tags = tags,
                dialog = dialogState.copy(isNameTaken = isNameTaken(tags, dialogState)),
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

    fun onTagColorChange(color: TagColor) {
        _dialogState.update { it.copy(tagColor = color) }
    }

    fun showAddDialog() {
        _dialogState.update { it.copy(activeDialog = TagsDialog.Add, tagName = "", tagColor = TagColor.DEFAULT) }
    }

    fun dismissDialog() {
        _dialogState.update { it.copy(activeDialog = null, tagName = "", tagColor = TagColor.DEFAULT) }
    }

    fun showDeleteConfirmation(tag: Tag) {
        _dialogState.update { it.copy(activeDialog = TagsDialog.Delete(tag)) }
    }

    fun showEditDialog(tag: Tag) {
        _dialogState.update { it.copy(activeDialog = TagsDialog.Edit(tag), tagName = tag.name, tagColor = tag.color) }
    }

    fun onAddDialogConfirmButtonClick() {
        val state = _dialogState.value
        if (state.tagName.isBlank()) return
        save(Tag(uuid = Tag.newUuid(), name = state.tagName.trim(), color = state.tagColor))
    }

    fun onEditDialogConfirmButtonClick() {
        val state = _dialogState.value
        val activeDialog = state.activeDialog
        if (activeDialog is TagsDialog.Edit) {
            val tag = activeDialog.tag
            val newName = state.tagName
            if (newName.isBlank() || (newName == tag.name && state.tagColor == tag.color)) return
            save(tag.copy(name = newName.trim(), color = state.tagColor))
        }
    }

    /** Closes the dialog once saved; a taken name keeps it open, where the error is shown. */
    private fun save(tag: Tag) {
        if (uiState.value.dialog.isNameTaken) return
        viewModelScope.launch {
            if (tagRepository.saveTag(tag) == SaveTagResult.Saved) dismissDialog()
        }
    }

    private fun isNameTaken(
        tags: List<Tag>,
        dialogState: TagsDialogState,
    ): Boolean {
        val editedId =
            when (val dialog = dialogState.activeDialog) {
                TagsDialog.Add -> null
                is TagsDialog.Edit -> dialog.tag.id
                else -> return false
            }
        val name = dialogState.tagName.trim()
        return tags.any { it.name == name && it.id != editedId }
    }

    fun onDeleteDialogConfirmButtonClick(tag: Tag) {
        viewModelScope.launch {
            tagRepository.deleteTag(tag)
        }
    }
}
