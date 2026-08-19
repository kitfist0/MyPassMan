package my.passman.ui.screens.tags

import my.passman.data.Tag

data class TagsScreenState(
    val tags: List<Tag> = emptyList(),
    val showAddDialog: Boolean = false,
    val tagToDelete: Tag? = null,
)
