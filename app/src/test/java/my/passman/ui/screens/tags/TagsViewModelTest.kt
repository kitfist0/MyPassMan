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
import my.passman.data.SaveTagResult
import my.passman.data.Tag
import my.passman.data.TagColor
import my.passman.data.TagRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {
    private val work = Tag(id = 1, uuid = "tag-1", name = "work", color = TagColor.BLUE)
    private val home = Tag(id = 2, uuid = "tag-2", name = "home")

    private val tags = MutableStateFlow(listOf(work, home))

    private val tagRepository =
        mockk<TagRepository>(relaxUnitFun = true) {
            every { getAllTags() } returns tags
            coEvery { saveTag(any()) } returns SaveTagResult.Saved
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
        val vm = TagsViewModel(tagRepository)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    private val TagsViewModel.dialog get() = uiState.value.dialog

    @Test
    fun tags_followRepository() =
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

                coVerify(exactly = 1) {
                    tagRepository.saveTag(match { it.id == 0L && it.name == "bank" && it.color == TagColor.GREEN && it.uuid.isNotBlank() })
                }
            }

        @Test
        fun confirm_closesDialogOnceSaved() =
            runTest {
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange("bank")

                vm.onAddDialogConfirmButtonClick()
                advanceUntilIdle()

                assertEquals(TagsDialogState(), vm.dialog)
            }

        @Test
        fun nameOfExistingTag_isReportedAndNotSaved() =
            runTest {
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange(" work ")
                advanceUntilIdle()

                assertTrue(vm.dialog.isNameTaken)

                vm.onAddDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagRepository.saveTag(any()) }
                assertEquals(TagsDialog.Add, vm.dialog.activeDialog)
            }

        @Test
        fun nameTakenBySave_keepsDialogOpen() =
            runTest {
                // The tag list has not caught up with a tag that already got the name.
                coEvery { tagRepository.saveTag(any()) } returns SaveTagResult.NameTaken
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange("bank")

                vm.onAddDialogConfirmButtonClick()
                advanceUntilIdle()

                assertEquals(TagsDialog.Add, vm.dialog.activeDialog)
                assertEquals("bank", vm.dialog.tagName)
            }

        @Test
        fun confirm_withBlankName_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange("   ")

                vm.onAddDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagRepository.saveTag(any()) }
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

                coVerify(exactly = 1) { tagRepository.saveTag(work.copy(name = "job")) }
            }

        @Test
        fun ownName_isNotTaken() =
            runTest {
                val vm = viewModel()

                vm.showEditDialog(work)
                advanceUntilIdle()

                assertFalse(vm.dialog.isNameTaken)
            }

        @Test
        fun renameToAnotherTagsName_isReportedAndNotSaved() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)
                vm.onTagNameChange("home")
                advanceUntilIdle()

                assertTrue(vm.dialog.isNameTaken)

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagRepository.saveTag(any()) }
                assertEquals(TagsDialog.Edit(work), vm.dialog.activeDialog)
            }

        @Test
        fun confirm_withOnlyColorChanged_saves() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)
                vm.onTagColorChange(TagColor.PINK)

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 1) { tagRepository.saveTag(work.copy(color = TagColor.PINK)) }
            }

        @Test
        fun confirm_withoutChanges_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagRepository.saveTag(any()) }
            }

        @Test
        fun confirm_withBlankName_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showEditDialog(work)
                vm.onTagNameChange(" ")

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagRepository.saveTag(any()) }
            }

        @Test
        fun confirm_whenEditDialogNotOpen_doesNothing() =
            runTest {
                val vm = viewModel()
                vm.showAddDialog()
                vm.onTagNameChange("bank")

                vm.onEditDialogConfirmButtonClick()
                advanceUntilIdle()

                coVerify(exactly = 0) { tagRepository.saveTag(any()) }
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

                coVerify(exactly = 1) { tagRepository.deleteTag(home) }
                assertNull(vm.dialog.activeDialog)
            }
    }
}
