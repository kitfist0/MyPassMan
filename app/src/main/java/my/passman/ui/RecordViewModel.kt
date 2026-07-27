package my.passman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val dao: RecordDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val sortOrder = settingsRepository.sortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.BY_NAME)

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<Record>> = combine(
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            settingsRepository.setSortOrder(sortOrder)
        }
    }

    fun addRecord(name: String, secret: String, comment: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val record = Record(
                created = now,
                modified = now,
                name = name,
                secret = secret,
                comment = comment
            )
            dao.insertRecord(record)
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch {
            dao.updateRecord(record.copy(modified = System.currentTimeMillis()))
        }
    }

    fun deleteRecord(record: Record) {
        viewModelScope.launch {
            dao.deleteRecord(record)
        }
    }
}
