package my.passman.ui.screens.list

import my.passman.data.Record

data class RecordListScreenState(
    val records: List<Record> = emptyList(),
    val searchQuery: String = "",
)
