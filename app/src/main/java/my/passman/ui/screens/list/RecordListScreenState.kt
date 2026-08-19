package my.passman.ui.screens.list

import my.passman.data.Record
import my.passman.data.Tag

data class RecordListScreenState(
    val records: List<Record> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val searchQuery: String = "",
)
