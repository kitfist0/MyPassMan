package my.passman.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import my.passman.data.RecordRepository
import my.passman.data.RecordWithTag
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import my.passman.data.Tag
import my.passman.data.TagRepository
import javax.inject.Inject

@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val recordRepository: RecordRepository,
    tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _selectedTagUuid = MutableStateFlow<String?>(null)

    private val sortOrder =
        settingsRepository.sortOrder
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.BY_NAME)

    private val allTags: StateFlow<List<Tag>> =
        tagRepository
            .getAllTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // null means the initial load from the database hasn't completed yet.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val records: StateFlow<List<RecordWithTag>?> =
        combine(
            _searchQuery,
            sortOrder,
            _selectedTagUuid,
        ) { query, sort, tagUuid ->
            Triple(query, sort, tagUuid)
        }.flatMapLatest { (query, sort, tagUuid) ->
            val flow =
                if (query.isBlank()) {
                    recordRepository.getAllRecords()
                } else {
                    recordRepository.searchRecords(query)
                }
            flow.map { list ->
                val filtered = if (tagUuid != null) list.filter { it.tag?.uuid == tagUuid } else list
                when (sort) {
                    SortOrder.BY_NAME -> filtered.sortedBy { it.record.name.lowercase() }
                    SortOrder.BY_CREATED -> filtered.sortedByDescending { it.record.created }
                    SortOrder.BY_MODIFIED -> filtered.sortedByDescending { it.record.modified }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private data class ListExtras(
        val availableTags: List<Tag>,
        val selectedTagUuid: String?,
        val hasShownLongPressHint: Boolean,
    )

    private val listExtras: Flow<ListExtras> =
        combine(
            allTags,
            _selectedTagUuid,
            settingsRepository.hasShownLongPressHint,
        ) { tags, selectedTagUuid, hasShownLongPressHint ->
            ListExtras(tags, selectedTagUuid, hasShownLongPressHint)
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
                selectedTagUuid = extras.selectedTagUuid,
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
        _selectedTagUuid.value = null
    }

    fun onTagClick(tagUuid: String) {
        _selectedTagUuid.value = if (_selectedTagUuid.value == tagUuid) null else tagUuid
    }

    fun dismissLongPressHint() {
        viewModelScope.launch {
            settingsRepository.setHasShownLongPressHint()
        }
    }
}
