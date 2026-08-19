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
import my.passman.data.TagDao
import javax.inject.Inject

@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val dao: RecordDao,
    tagDao: TagDao,
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

    private val allTags = tagDao.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<RecordListScreenState> = combine(
        records,
        allTags,
        _searchQuery
    ) { recordsList, tagsList, query ->
        RecordListScreenState(
            records = recordsList,
            tags = tagsList,
            searchQuery = query
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordListScreenState())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
