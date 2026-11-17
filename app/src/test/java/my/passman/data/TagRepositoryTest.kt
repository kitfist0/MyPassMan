package my.passman.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TagRepositoryTest {
    private val tag = Tag(id = 3, uuid = "tag-3", name = "work")

    private val tagDao =
        mockk<TagDao>(relaxUnitFun = true) {
            coEvery { upsertTag(any()) } returns tag.id
            coEvery { getTagsList() } returns listOf(tag)
        }
    private val changeTracker =
        mockk<VaultChangeTracker> {
            coEvery { recordChange(any<suspend () -> Any?>()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        }

    private val repository = TagRepository(tagDao, changeTracker)

    @Test
    fun getAllTags_delegatesToDao() {
        val tags = flowOf(listOf(tag))
        every { tagDao.getAllTags() } returns tags

        assertSame(tags, repository.getAllTags())
    }

    @Test
    fun saveTag_isTrackedAsChange() =
        runTest {
            assertEquals(SaveTagResult.Saved, repository.saveTag(tag))

            coVerifyOrder {
                changeTracker.recordChange(any<suspend () -> Any?>())
                tagDao.upsertTag(tag)
            }
        }

    @Test
    fun saveTag_keepingItsOwnName_saves() =
        runTest {
            assertEquals(SaveTagResult.Saved, repository.saveTag(tag.copy(color = TagColor.RED)))

            coVerify(exactly = 1) { tagDao.upsertTag(tag.copy(color = TagColor.RED)) }
        }

    @Test
    fun saveTag_changingOnlyTheCaseOfItsOwnName_saves() =
        runTest {
            assertEquals(SaveTagResult.Saved, repository.saveTag(tag.copy(name = "Work")))

            coVerify(exactly = 1) { tagDao.upsertTag(tag.copy(name = "Work")) }
        }

    @Test
    fun saveTag_withNameOfAnotherTag_isRejectedWithoutWriting() =
        runTest {
            assertEquals(SaveTagResult.NameTaken, repository.saveTag(Tag(uuid = "tag-new", name = "work")))
            assertEquals(SaveTagResult.NameTaken, repository.saveTag(Tag(id = 4, uuid = "tag-4", name = "work")))

            coVerify(exactly = 0) { tagDao.upsertTag(any()) }
            coVerify(exactly = 0) { changeTracker.recordChange(any<suspend () -> Any?>()) }
        }

    @Test
    fun saveTag_withNameOfAnotherTagInAnotherCase_isRejected() =
        runTest {
            coEvery { tagDao.getTagsList() } returns listOf(tag, Tag(id = 5, uuid = "tag-5", name = "Работа"))

            assertEquals(SaveTagResult.NameTaken, repository.saveTag(Tag(uuid = "tag-new", name = "WORK")))
            assertEquals(SaveTagResult.NameTaken, repository.saveTag(Tag(uuid = "tag-new", name = "работа")))

            coVerify(exactly = 0) { tagDao.upsertTag(any()) }
        }

    @Test
    fun deleteTag_isTrackedAsChange() =
        runTest {
            repository.deleteTag(tag)

            coVerifyOrder {
                changeTracker.recordChange(any<suspend () -> Any?>())
                tagDao.deleteTag(tag)
            }
        }
}
