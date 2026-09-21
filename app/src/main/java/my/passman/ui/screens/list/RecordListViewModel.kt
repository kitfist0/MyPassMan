package my.passman.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.RecordDao
import my.passman.data.RecordWithTag
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import my.passman.data.Tag
import my.passman.data.TagDao
import javax.inject.Inject

@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val recordDao: RecordDao,
    tagDao: TagDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _selectedTagId = MutableStateFlow<Long?>(null)

    private val sortOrder =
        settingsRepository.sortOrder
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.BY_NAME)

    private val allTags: StateFlow<List<Tag>> =
        tagDao
            .getAllTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // null means the initial load from the database hasn't completed yet.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val records: StateFlow<List<RecordWithTag>?> =
        combine(
            _searchQuery,
            sortOrder,
            _selectedTagId,
        ) { query, sort, tagId ->
            Triple(query, sort, tagId)
        }.flatMapLatest { (query, sort, tagId) ->
            val flow =
                if (query.isBlank()) {
                    recordDao.getAllRecords()
                } else {
                    recordDao.searchRecords(query)
                }
            flow.map { list ->
                val filtered = if (tagId != null) list.filter { it.tag?.id == tagId } else list
                when (sort) {
                    SortOrder.BY_NAME -> filtered.sortedBy { it.record.name.lowercase() }
                    SortOrder.BY_CREATED -> filtered.sortedByDescending { it.record.created }
                    SortOrder.BY_MODIFIED -> filtered.sortedByDescending { it.record.modified }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private data class ListExtras(
        val availableTags: List<Tag>,
        val selectedTagId: Long?,
        val hasShownLongPressHint: Boolean,
    )

    private val listExtras: Flow<ListExtras> =
        combine(
            allTags,
            _selectedTagId,
            settingsRepository.hasShownLongPressHint,
        ) { tags, selectedTagId, hasShownLongPressHint ->
            ListExtras(tags, selectedTagId, hasShownLongPressHint)
        }

    val uiState: StateFlow<RecordListScreenState> =
        combine(
            records,
            _searchQuery,
            _isSearchActive,
            settingsRepository.pinHash,
            listExtras,
        ) { recordsList, query, isSearchActive, pinHash, extras ->
            RecordListScreenState(
                records = recordsList ?: emptyList(),
                searchQuery = query,
                isSearchActive = isSearchActive,
                isLoading = recordsList == null,
                isPinEnabled = pinHash != null,
                availableTags = extras.availableTags,
                selectedTagId = extras.selectedTagId,
                showLongPressHint = recordsList?.isNotEmpty() == true && !extras.hasShownLongPressHint,
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
        _selectedTagId.value = null
    }

    fun onTagClick(tagId: Long) {
        _selectedTagId.value = if (_selectedTagId.value == tagId) null else tagId
    }

    fun dismissLongPressHint() {
        viewModelScope.launch {
            settingsRepository.setHasShownLongPressHint()
        }
    }
}
