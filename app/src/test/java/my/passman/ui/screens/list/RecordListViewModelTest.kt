package my.passman.ui.screens.list

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.passman.data.Record
import my.passman.data.RecordDao
import my.passman.data.RecordWithTag
import my.passman.data.SettingsRepository
import my.passman.data.SortOrder
import my.passman.data.Tag
import my.passman.data.TagDao
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordListViewModelTest {
    private val work = Tag(id = 1, name = "work")
    private val home = Tag(id = 2, name = "home")

    private val bank = item(id = 1, name = "bank", login = "john", created = 300, modified = 100, tag = home)
    private val alpha = item(id = 2, name = "Alpha", login = "a.smith", created = 100, modified = 300, tag = work)
    private val mail = item(id = 3, name = "Mail", login = "john@example.com", created = 200, modified = 200, tag = null)

    private val records = MutableStateFlow(listOf(bank, alpha, mail))
    private val tags = MutableStateFlow(listOf(work, home))
    private val sortOrder = MutableStateFlow(SortOrder.BY_NAME)
    private val pinHash = MutableStateFlow<String?>(null)
    private val hasShownLongPressHint = MutableStateFlow(false)

    private val recordDao =
        mockk<RecordDao> {
            every { getAllRecords() } returns records
            every { searchRecords(any()) } answers {
                val query = firstArg<String>()
                records.map { list -> list.filter { query in it.record.name || query in it.record.login } }
            }
        }
    private val tagDao =
        mockk<TagDao> {
            every { getAllTags() } returns tags
        }
    private val settingsRepository =
        mockk<SettingsRepository>(relaxUnitFun = true) {
            every { sortOrder } returns this@RecordListViewModelTest.sortOrder
            every { pinHash } returns this@RecordListViewModelTest.pinHash
            every { hasShownLongPressHint } returns this@RecordListViewModelTest.hasShownLongPressHint
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Creates the view model and keeps [RecordListViewModel.uiState] subscribed, as the screen does. */
    private fun TestScope.viewModel(): RecordListViewModel {
        val vm = RecordListViewModel(recordDao, tagDao, settingsRepository)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private fun RecordListViewModel.names() = uiState.value.records.map { it.record.name }

    @Test
    fun isLoading_untilRecordsArrive() =
        runTest {
            val pending = MutableSharedFlow<List<RecordWithTag>>()
            every { recordDao.getAllRecords() } returns pending
            val vm = viewModel()
            assertTrue(vm.uiState.value.isLoading)

            pending.emit(listOf(bank))
            advanceUntilIdle()

            assertFalse(vm.uiState.value.isLoading)
            assertEquals(listOf(bank), vm.uiState.value.records)
        }

    @Test
    fun records_followDatabase() =
        runTest {
            val vm = viewModel()

            records.value = listOf(mail)
            advanceUntilIdle()

            assertEquals(listOf("Mail"), vm.names())
        }

    @Test
    fun availableTags_followTagDao() =
        runTest {
            val vm = viewModel()
            assertEquals(listOf(work, home), vm.uiState.value.availableTags)

            tags.value = listOf(home)
            advanceUntilIdle()

            assertEquals(listOf(home), vm.uiState.value.availableTags)
        }

    @Test
    fun isPinEnabled_reflectsStoredPin() =
        runTest {
            val vm = viewModel()
            assertFalse(vm.uiState.value.isPinEnabled)

            pinHash.value = "hash"
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isPinEnabled)
        }

    @Nested
    inner class Sorting {
        @Test
        fun byName_ignoresCase() =
            runTest {
                assertEquals(listOf("Alpha", "bank", "Mail"), viewModel().names())
            }

        @Test
        fun byCreated_newestFirst() =
            runTest {
                sortOrder.value = SortOrder.BY_CREATED

                assertEquals(listOf("bank", "Mail", "Alpha"), viewModel().names())
            }

        @Test
        fun byModified_newestFirst() =
            runTest {
                sortOrder.value = SortOrder.BY_MODIFIED

                assertEquals(listOf("Alpha", "Mail", "bank"), viewModel().names())
            }

        @Test
        fun sortOrderChange_resortsList() =
            runTest {
                val vm = viewModel()

                sortOrder.value = SortOrder.BY_CREATED
                advanceUntilIdle()

                assertEquals(listOf("bank", "Mail", "Alpha"), vm.names())
            }
    }

    @Nested
    inner class Search {
        @Test
        fun onSearchClick_activatesSearch() =
            runTest {
                val vm = viewModel()

                vm.onSearchClick()
                advanceUntilIdle()

                assertTrue(vm.uiState.value.isSearchActive)
            }

        @Test
        fun query_usesSearchAndKeepsSorting() =
            runTest {
                val vm = viewModel()

                vm.updateSearchQuery("john")
                advanceUntilIdle()

                assertEquals("john", vm.uiState.value.searchQuery)
                assertEquals(listOf("bank", "Mail"), vm.names())
                verify { recordDao.searchRecords("john") }
            }

        @Test
        fun blankQuery_showsAllRecords() =
            runTest {
                val vm = viewModel()

                vm.updateSearchQuery("   ")
                advanceUntilIdle()

                assertEquals(3, vm.uiState.value.records.size)
                verify(exactly = 0) { recordDao.searchRecords(any()) }
            }

        @Test
        fun onCloseSearch_resetsQueryAndTagFilter() =
            runTest {
                val vm = viewModel()
                vm.onSearchClick()
                vm.updateSearchQuery("john")
                vm.onTagClick(home.id)
                advanceUntilIdle()

                vm.onCloseSearch()
                advanceUntilIdle()

                val state = vm.uiState.value
                assertFalse(state.isSearchActive)
                assertEquals("", state.searchQuery)
                assertNull(state.selectedTagId)
                assertEquals(3, state.records.size)
            }
    }

    @Nested
    inner class TagFilter {
        @Test
        fun onTagClick_showsOnlyRecordsWithThatTag() =
            runTest {
                val vm = viewModel()

                vm.onTagClick(work.id)
                advanceUntilIdle()

                assertEquals(work.id, vm.uiState.value.selectedTagId)
                assertEquals(listOf("Alpha"), vm.names())
            }

        @Test
        fun onTagClick_sameTagAgain_clearsFilter() =
            runTest {
                val vm = viewModel()
                vm.onTagClick(work.id)
                advanceUntilIdle()

                vm.onTagClick(work.id)
                advanceUntilIdle()

                assertNull(vm.uiState.value.selectedTagId)
                assertEquals(3, vm.uiState.value.records.size)
            }

        @Test
        fun onTagClick_otherTag_switchesFilter() =
            runTest {
                val vm = viewModel()
                vm.onTagClick(work.id)

                vm.onTagClick(home.id)
                advanceUntilIdle()

                assertEquals(home.id, vm.uiState.value.selectedTagId)
                assertEquals(listOf("bank"), vm.names())
            }

        @Test
        fun tagFilter_combinesWithSearch() =
            runTest {
                val vm = viewModel()

                vm.updateSearchQuery("john")
                vm.onTagClick(home.id)
                advanceUntilIdle()

                assertEquals(listOf("bank"), vm.names())
            }
    }

    @Nested
    inner class LongPressHint {
        @Test
        fun shown_whenRecordsExistAndNotSeenYet() =
            runTest {
                assertTrue(viewModel().uiState.value.showLongPressHint)
            }

        @Test
        fun hidden_whenListIsEmpty() =
            runTest {
                records.value = emptyList()

                assertFalse(viewModel().uiState.value.showLongPressHint)
            }

        @Test
        fun hidden_onceSeen() =
            runTest {
                val vm = viewModel()

                hasShownLongPressHint.value = true
                advanceUntilIdle()

                assertFalse(vm.uiState.value.showLongPressHint)
            }

        @Test
        fun dismissLongPressHint_persistsFlag() =
            runTest {
                val vm = viewModel()

                vm.dismissLongPressHint()
                advanceUntilIdle()

                coVerify { settingsRepository.setHasShownLongPressHint() }
            }
    }

    private companion object {
        fun item(
            id: Long,
            name: String,
            login: String,
            created: Long,
            modified: Long,
            tag: Tag?,
        ) = RecordWithTag(
            record =
                Record(
                    id = id,
                    created = created,
                    modified = modified,
                    name = name,
                    login = login,
                    secret = "secret",
                    comment = "",
                    tagId = tag?.id,
                ),
            tag = tag,
        )
    }
}
