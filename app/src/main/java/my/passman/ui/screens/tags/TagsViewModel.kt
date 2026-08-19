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
            showAddDialog = dialogState.showAddDialog,
            tagToDelete = dialogState.tagToDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TagsScreenState()
    )

    private data class DialogState(
        val showAddDialog: Boolean = false,
        val tagToDelete: Tag? = null
    )

    fun showAddDialog() {
        _dialogState.update { it.copy(showAddDialog = true) }
    }

    fun dismissAddDialog() {
        _dialogState.update { it.copy(showAddDialog = false) }
    }

    fun showDeleteConfirmation(tag: Tag) {
        _dialogState.update { it.copy(tagToDelete = tag) }
    }

    fun dismissDeleteConfirmation() {
        _dialogState.update { it.copy(tagToDelete = null) }
    }

    fun addTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            tagDao.upsertTag(Tag(name = name.trim()))
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            tagDao.deleteTag(tag)
        }
    }
}
