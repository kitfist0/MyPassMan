package my.passman.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import javax.inject.Inject

@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val dao: RecordDao,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    private val sortOrder = settingsRepository.sortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.BY_NAME)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val records: StateFlow<List<Record>> = combine(
        _searchQuery,
        sortOrder
    ) { query, sort ->
        query to sort
    }.flatMapLatest { (query, sort) ->
        val flow = if (query.isBlank()) {
            dao.getAllRecords()
        } else {
            dao.searchRecords(query)
        }
        flow.map { list ->
            when (sort) {
                SortOrder.BY_NAME -> list.sortedBy { it.name.lowercase() }
                SortOrder.BY_CREATED -> list.sortedByDescending { it.created }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<RecordListScreenState> = combine(
        records,
        _searchQuery
    ) { recordsList, query ->
        RecordListScreenState(
            records = recordsList,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordListScreenState())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
