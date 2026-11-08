package my.passman.ui.screens.edit

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.passman.data.Record
import my.passman.data.RecordDao
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
class EditRecordViewModelTest {
    private val tags = MutableStateFlow(listOf(Tag(id = 1, name = "work"), Tag(id = 2, name = "home")))
    private val existing =
        Record(
            id = 7,
            created = 1_000,
            modified = 2_000,
            name = "Mail",
            login = "me@example.com",
            secret = "s3cret",
            comment = "personal",
            tagId = 1,
        )

    private val recordDao =
        mockk<RecordDao>(relaxUnitFun = true) {
            coEvery { getRecordById(any()) } returns null
            coEvery { getRecordById(existing.id) } returns existing
        }
    private val tagDao =
        mockk<TagDao> {
            every { getAllTags() } returns tags
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(recordId: Long?): EditRecordViewModel =
        EditRecordViewModel(recordDao, tagDao, recordId).also { advanceUntilIdle() }

    private fun EditRecordViewModel.exitRequested() = exitEvent.tryReceive().isSuccess

    @Test
    fun allTags_followTagDao() =
        runTest {
            val vm = viewModel(null)
            assertEquals(tags.value, vm.uiState.value.allTags)

            val updated = listOf(Tag(id = 3, name = "bank"))
            tags.value = updated
            advanceUntilIdle()

            assertEquals(updated, vm.uiState.value.allTags)
        }

    @Test
    fun onCommentChange_stripsLineBreaks() =
        runTest {
            val vm = viewModel(null)

            vm.onCommentChange("line one\nline two\n")

            assertEquals("line oneline two", vm.uiState.value.comment)
        }

    @Test
    fun fieldChanges_areReflectedInState() =
        runTest {
            val vm = viewModel(null)

            vm.onNameChange("Bank")
            vm.onLoginChange("john")
            vm.onSecretChange("pa55")
            vm.onTagChange(2)

            val state = vm.uiState.value
            assertEquals("Bank", state.name)
            assertEquals("john", state.login)
            assertEquals("pa55", state.secret)
            assertEquals(2L, state.selectedTagId)
        }

    @Test
    fun toggleSecretVisibility_flipsFlag() =
        runTest {
            val vm = viewModel(null)

            vm.toggleSecretVisibility()
            assertTrue(vm.uiState.value.secretVisible)

            vm.toggleSecretVisibility()
            assertFalse(vm.uiState.value.secretVisible)
        }

    @Test
    fun dialogs_canBeShownAndDismissed() =
        runTest {
            val vm = viewModel(existing.id)

            vm.showDeleteDialog()
            assertTrue(vm.uiState.value.showDeleteDialog)
            vm.dismissDeleteDialog()
            assertFalse(vm.uiState.value.showDeleteDialog)

            vm.showExitDialog()
            assertTrue(vm.uiState.value.showExitDialog)
            vm.dismissExitDialog()
            assertFalse(vm.uiState.value.showExitDialog)
        }

    @Nested
    inner class NewRecord {
        @Test
        fun initialState_isEmptyAndNotLoading() =
            runTest {
                val state = viewModel(null).uiState.value

                assertNull(state.recordId)
                assertFalse(state.isLoading)
                assertFalse(state.canSave)
                assertEquals("", state.name)
                coVerify(exactly = 0) { recordDao.getRecordById(any()) }
            }

        @Test
        fun canSave_requiresNonBlankNameAndSecret() =
            runTest {
                val vm = viewModel(null)

                vm.onNameChange("Bank")
                assertFalse(vm.uiState.value.canSave)

                vm.onSecretChange("   ")
                assertFalse(vm.uiState.value.canSave)

                vm.onSecretChange("pa55")
                assertTrue(vm.uiState.value.canSave)

                vm.onNameChange(" ")
                assertFalse(vm.uiState.value.canSave)
            }

        @Test
        fun save_insertsTrimmedRecordWithFreshTimestamps() =
            runTest {
                val saved = slot<Record>()
                coEvery { recordDao.upsertRecord(capture(saved)) } returns Unit
                val vm = viewModel(null)
                vm.onNameChange("  Bank ")
                vm.onLoginChange(" john ")
                vm.onSecretChange(" pa55 ")
                vm.onCommentChange(" main account ")
                vm.onTagChange(2)

                val before = System.currentTimeMillis()
                val returnedId = vm.save()
                advanceUntilIdle()
                val after = System.currentTimeMillis()

                assertNull(returnedId)
                with(saved.captured) {
                    assertEquals(0L, id)
                    assertEquals("Bank", name)
                    assertEquals("john", login)
                    assertEquals(" pa55 ", secret, "the secret must be stored verbatim")
                    assertEquals("main account", comment)
                    assertEquals(2L, tagId)
                    assertTrue(created in before..after)
                    assertEquals(created, modified)
                }
            }

        @Test
        fun delete_doesNothing() =
            runTest {
                val vm = viewModel(null)

                vm.delete()
                advanceUntilIdle()

                coVerify(exactly = 0) { recordDao.deleteRecord(any()) }
            }

        @Test
        fun onBackPressed_withoutInput_exits() =
            runTest {
                val vm = viewModel(null)

                vm.onBackPressed()

                assertTrue(vm.exitRequested())
                assertFalse(vm.uiState.value.showExitDialog)
            }

        @Test
        fun onBackPressed_withSavableInput_asksForConfirmation() =
            runTest {
                val vm = viewModel(null)
                vm.onNameChange("Bank")
                vm.onSecretChange("pa55")

                vm.onBackPressed()

                assertTrue(vm.uiState.value.showExitDialog)
                assertFalse(vm.exitRequested())
            }

        @Test
        fun onBackPressed_withUnsavableInput_exitsWithoutDialog() =
            runTest {
                val vm = viewModel(null)
                vm.onNameChange("Bank")

                vm.onBackPressed()

                assertTrue(vm.exitRequested())
                assertFalse(vm.uiState.value.showExitDialog)
            }
    }

    @Nested
    inner class ExistingRecord {
        @Test
        fun loading_showsSpinnerThenFillsFields() =
            runTest {
                val vm = EditRecordViewModel(recordDao, tagDao, existing.id)

                vm.uiState.test {
                    assertTrue(awaitItem().isLoading)

                    // The tag list may arrive first, while the record is still loading.
                    var loaded = awaitItem()
                    while (loaded.isLoading) loaded = awaitItem()
                    assertEquals(existing.id, loaded.recordId)
                    assertEquals(existing.name, loaded.name)
                    assertEquals(existing.login, loaded.login)
                    assertEquals(existing.secret, loaded.secret)
                    assertEquals(existing.comment, loaded.comment)
                    assertEquals(existing.tagId, loaded.selectedTagId)
                    assertEquals(existing.created, loaded.created)
                    assertEquals(existing.modified, loaded.modified)
                    assertFalse(loaded.canSave)
                    cancelAndIgnoreRemainingEvents()
                }
            }

        @Test
        fun missingRecord_fallsBackToNewRecord() =
            runTest {
                val vm = viewModel(42)

                val state = vm.uiState.value
                assertFalse(state.isLoading)
                assertNull(state.recordId)
                assertEquals("", state.name)
            }

        @Test
        fun canSave_onlyWhenSomethingChanged() =
            runTest {
                val vm = viewModel(existing.id)

                vm.onNameChange("Mail 2")
                assertTrue(vm.uiState.value.canSave)

                vm.onNameChange(existing.name)
                assertFalse(vm.uiState.value.canSave)

                vm.onTagChange(null)
                assertTrue(vm.uiState.value.canSave)
            }

        @Test
        fun canSave_isFalseWhenSecretCleared() =
            runTest {
                val vm = viewModel(existing.id)

                vm.onSecretChange("")

                assertFalse(vm.uiState.value.canSave)
            }

        @Test
        fun tagListUpdate_doesNotEnableSaving() =
            runTest {
                val vm = viewModel(existing.id)

                tags.value = tags.value + Tag(id = 3, name = "bank")
                advanceUntilIdle()

                assertFalse(vm.uiState.value.canSave)
            }

        @Test
        fun save_keepsIdAndCreationTime() =
            runTest {
                val saved = slot<Record>()
                coEvery { recordDao.upsertRecord(capture(saved)) } returns Unit
                val vm = viewModel(existing.id)
                vm.onLoginChange("new@example.com")

                val before = System.currentTimeMillis()
                val returnedId = vm.save()
                advanceUntilIdle()

                assertEquals(existing.id, returnedId)
                with(saved.captured) {
                    assertEquals(existing.id, id)
                    assertEquals(existing.created, created)
                    assertTrue(modified >= before)
                    assertEquals("new@example.com", login)
                    assertEquals(existing.name, name)
                }
            }

        @Test
        fun delete_removesStoredRecord() =
            runTest {
                val vm = viewModel(existing.id)

                vm.delete()
                advanceUntilIdle()

                coVerify(exactly = 1) { recordDao.deleteRecord(existing) }
            }

        @Test
        fun delete_whenRecordVanished_doesNothing() =
            runTest {
                val vm = viewModel(existing.id)
                coEvery { recordDao.getRecordById(existing.id) } returns null

                vm.delete()
                advanceUntilIdle()

                coVerify(exactly = 0) { recordDao.deleteRecord(any()) }
            }

        @Test
        fun onBackPressed_withoutChanges_exits() =
            runTest {
                val vm = viewModel(existing.id)

                vm.onBackPressed()

                assertTrue(vm.exitRequested())
            }

        @Test
        fun onBackPressed_withChanges_asksForConfirmation() =
            runTest {
                val vm = viewModel(existing.id)
                vm.onCommentChange("updated")

                vm.onBackPressed()

                assertTrue(vm.uiState.value.showExitDialog)
                assertFalse(vm.exitRequested())
            }

        @Test
        fun onBackPressed_whileLoading_exits() =
            runTest {
                val vm = EditRecordViewModel(recordDao, tagDao, existing.id)
                assertTrue(vm.uiState.value.isLoading)

                vm.onBackPressed()

                assertTrue(vm.exitRequested())
            }
    }
}
