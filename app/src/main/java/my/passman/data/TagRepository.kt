package my.passman.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao,
    private val changeTracker: VaultChangeTracker,
) {
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun saveTag(tag: Tag) {
        changeTracker.recordChange { tagDao.upsertTag(tag) }
    }

    /** Records using the tag lose it (the foreign key sets their tag to null). */
    suspend fun deleteTag(tag: Tag) {
        changeTracker.recordChange { tagDao.deleteTag(tag) }
    }
}
