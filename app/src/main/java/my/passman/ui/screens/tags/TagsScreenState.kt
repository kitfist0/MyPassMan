package my.passman.ui.screens.tags

import my.passman.data.Tag
import my.passman.data.TagColor

data class TagsScreenState(
    val tags: List<Tag> = emptyList(),
    val dialog: TagsDialogState = TagsDialogState(),
)

data class TagsDialogState(
    val activeDialog: TagsDialog? = null,
    val tagName: String = "",
    val tagColor: TagColor = TagColor.DEFAULT,
    /** Another tag already has [tagName], so it cannot be saved. */
    val isNameTaken: Boolean = false,
)

sealed interface TagsDialog {
    data object Add : TagsDialog

    data class Edit(
        val tag: Tag,
    ) : TagsDialog

    data class Delete(
        val tag: Tag,
    ) : TagsDialog
}
