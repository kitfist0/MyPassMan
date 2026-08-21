package my.passman.ui.screens.tags

import my.passman.data.Tag

data class TagsScreenState(
    val tags: List<Tag> = emptyList(),
    val dialog: TagsDialogState = TagsDialogState(),
)

data class TagsDialogState(
    val activeDialog: TagsDialog? = null,
    val tagName: String = ""
)

sealed interface TagsDialog {
    data object Add : TagsDialog
    data class Edit(val tag: Tag) : TagsDialog
    data class Delete(val tag: Tag) : TagsDialog
}
