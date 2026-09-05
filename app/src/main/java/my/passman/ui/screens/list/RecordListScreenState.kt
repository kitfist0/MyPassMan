package my.passman.ui.screens.list

import my.passman.data.RecordWithTag

data class RecordListScreenState(
    val records: List<RecordWithTag> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
)
