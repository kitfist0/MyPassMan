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

    /**
     * Saves [tag] unless another tag already has its name. Tag names are unique in the database,
     * and an upsert hitting that constraint either silently does nothing (new tag) or throws
     * (rename), so the name is checked up front instead.
     */
    suspend fun saveTag(tag: Tag): SaveTagResult {
        val sameName = tagDao.getTagByName(tag.name)
        if (sameName != null && sameName.id != tag.id) return SaveTagResult.NameTaken
        changeTracker.recordChange { tagDao.upsertTag(tag) }
        return SaveTagResult.Saved
    }

    /** Records using the tag lose it (the foreign key sets their tag to null). */
    suspend fun deleteTag(tag: Tag) {
        changeTracker.recordChange { tagDao.deleteTag(tag) }
    }
}

sealed interface SaveTagResult {
    data object Saved : SaveTagResult

    data object NameTaken : SaveTagResult
}
