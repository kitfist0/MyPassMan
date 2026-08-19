package my.passman.ui.screens.edit

import my.passman.data.Tag

data class EditRecordScreenState(
    val recordId: Long? = null,
    val isLoading: Boolean = false,
    val name: String = "",
    val login: String = "",
    val secret: String = "",
    val comment: String = "",
    val selectedTagId: Long? = null,
    val allTags: List<Tag> = emptyList(),
    val secretVisible: Boolean = false,
    val showExitDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val canSave: Boolean = false,
    val created: Long? = null,
    val modified: Long? = null,
)
