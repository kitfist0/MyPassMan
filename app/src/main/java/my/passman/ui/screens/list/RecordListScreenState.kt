package my.passman.ui.screens.list

import my.passman.data.RecordWithTag
import my.passman.data.Tag

data class RecordListScreenState(
    val records: List<RecordWithTag> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = true,
    val isPinEnabled: Boolean = false,
    val availableTags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val showLongPressHint: Boolean = false,
)
