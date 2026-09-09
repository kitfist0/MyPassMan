package my.passman.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import my.passman.data.RecordDao
import my.passman.data.RecordWithTag
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import javax.inject.Inject

@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val recordDao: RecordDao,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)

    private val sortOrder =
        settingsRepository.sortOrder
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.BY_NAME)

    // null means the initial load from the database hasn't completed yet.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val records: StateFlow<List<RecordWithTag>?> =
        combine(
            _searchQuery,
            sortOrder,
        ) { query, sort ->
            query to sort
        }.flatMapLatest { (query, sort) ->
            val flow =
                if (query.isBlank()) {
                    recordDao.getAllRecords()
                } else {
                    recordDao.searchRecords(query)
                }
            flow.map { list ->
                when (sort) {
                    SortOrder.BY_NAME -> list.sortedBy { it.record.name.lowercase() }
                    SortOrder.BY_CREATED -> list.sortedByDescending { it.record.created }
                    SortOrder.BY_MODIFIED -> list.sortedByDescending { it.record.modified }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val uiState: StateFlow<RecordListScreenState> =
        combine(
            records,
            _searchQuery,
            _isSearchActive,
        ) { recordsList, query, isSearchActive ->
            RecordListScreenState(
                records = recordsList ?: emptyList(),
                searchQuery = query,
                isSearchActive = isSearchActive,
                isLoading = recordsList == null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordListScreenState())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onSearchClick() {
        _isSearchActive.value = true
    }

    fun onCloseSearch() {
        _isSearchActive.value = false
        _searchQuery.value = ""
    }
}
