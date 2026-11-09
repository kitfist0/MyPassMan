package my.passman.ui.screens.tags

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.passman.data.Tag
import my.passman.data.TagColor
import my.passman.data.TagDao
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {
    private val work = Tag(id = 1, name = "work", color = TagColor.BLUE)
    private val home = Tag(id = 2, name = "home")

    private val tags = MutableStateFlow(listOf(work, home))

    private val tagDao =
        mockk<TagDao>(relaxUnitFun = true) {
            every { getAllTags() } returns tags
            coEvery { upsertTag(any()) } returns 0
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Creates the view model and keeps [TagsViewModel.uiState] subscribed, as the screen does. */
    private fun TestScope.viewModel(): TagsViewModel {
        val vm = TagsViewModel(tagDao)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private val TagsViewModel.dialog get() = uiState.value.dialog

    @Test
    fun tags_followTagDao() =
        runTest {
            val vm = viewModel()
            assertEquals(listOf(work, home), vm.uiState.value.tags)

            tags.value = listOf(home)
            advanceUntilIdle()

            assertEquals(listOf(home), vm.uiState.value.tags)
        }

    @Test
    fun initialState_hasNoDialog() =
        runTest {
            assertEquals(TagsDialogState(), viewModel().dialog)
        }

    @Test
    fun onTagNameChange_rejectsNamesOverMaxLength() =
        runTest {
            val vm = viewModel()
            val longest = "a".repeat(Tag.MAX_NAME_LENGTH)

            vm.onTagNameChange(longest)
            vm.onTagNameChange(longest + "b")
            advanceUntilIdle()

            assertEquals(longest, vm.dialog.tagName)
        }

    @Test
    fun dismissDialog_resetsDialogState() =
        runTest {
            val vm = viewModel()
            vm.showEditDialog(work)

            vm.dismissDialog()
            advanceUntilIdle()

            assertEquals(TagsDialogState(), vm.dialog)
        }

    @Nested
    inner class Add {
        @Test
        fun showAddDialog_startsWithEmptyNameAndDefaultColor() =
            runTest {
                val vm = viewModel()
                vm.onTagNameChange("leftover")
                vm.onTagColorChange(TagColor.RED)

                vm.showAddDialog()
                advanceUntilIdle()

                assertEquals(TagsDialogState(activeDialog = TagsDialog.Add), vm.dialog)
            }

        @Test
        fun confirm_savesTrimmedNameAndColor() =
            runTest {
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange(" bank ")
                vm.onTagColorChange(TagColor.GREEN)

                // The screen dismisses the dialog right after confirming.
                vm.onAddDialogConfirmButtonClick()
                vm.dismissDialog()
                advanceUntilIdle()

                coVerify(exactly = 1) { tagDao.upsertTag(Tag(name = "bank", color = TagColor.GREEN)) }
            }

        @Test
        fun confirm_withBlankName_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange("   ")

                vm.onAddDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagDao.upsertTag(any()) }
            }
    }

    @Nested
    inner class Edit {
        @Test
        fun showEditDialog_prefillsTag() =
            runTest {
                val vm = viewModel()

                vm.showEditDialog(work)
                advanceUntilIdle()

                assertEquals(
                    TagsDialogState(activeDialog = TagsDialog.Edit(work), tagName = "work", tagColor = TagColor.BLUE),
                    vm.dialog,
                )
            }

        @Test
        fun confirm_renamesKeepingId() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)
                vm.onTagNameChange(" job ")

                vm.onEditDialogConfirmButtonClick()
                vm.dismissDialog()
                advanceUntilIdle()

                coVerify(exactly = 1) { tagDao.upsertTag(work.copy(name = "job")) }
            }

        @Test
        fun confirm_withOnlyColorChanged_saves() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)
                vm.onTagColorChange(TagColor.PINK)

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 1) { tagDao.upsertTag(work.copy(color = TagColor.PINK)) }
            }

        @Test
        fun confirm_withoutChanges_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagDao.upsertTag(any()) }
            }

        @Test
        fun confirm_withBlankName_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)
                vm.onTagNameChange(" ")

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagDao.upsertTag(any()) }
            }

        @Test
        fun confirm_whenEditDialogNotOpen_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange("bank")

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagDao.upsertTag(any()) }
            }
    }

    @Nested
    inner class Delete {
        @Test
        fun showDeleteConfirmation_opensDialogForTag() =
            runTest {
                val vm = viewModel()

                vm.showDeleteConfirmation(home)
                advanceUntilIdle()

                assertEquals(TagsDialog.Delete(home), vm.dialog.activeDialog)
            }

        @Test
        fun confirm_deletesTag() =
            runTest {
                val vm = viewModel()
                vm.showDeleteConfirmation(home)

                vm.onDeleteDialogConfirmButtonClick(home)
                vm.dismissDialog()
                advanceUntilIdle()

                coVerify(exactly = 1) { tagDao.deleteTag(home) }
                assertNull(vm.dialog.activeDialog)
            }
    }
}
