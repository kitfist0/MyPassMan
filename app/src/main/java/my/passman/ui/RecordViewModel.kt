package my.passman.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.Record
import my.passman.data.RecordDao
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val dao: RecordDao,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<Record>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                dao.getAllRecords()
            } else {
                dao.searchRecords(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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
