package my.passman.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Upsert
    suspend fun upsertTag(tag: Tag): Long

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): Tag?

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getTagByName(name: String): Tag?

    @Query("SELECT * FROM tags")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags")
    suspend fun getTagsList(): List<Tag>

    @Upsert
    suspend fun upsertTags(tags: List<Tag>)

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Delete
    suspend fun deleteTag(tag: Tag)
}
