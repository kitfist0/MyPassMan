package my.passman.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val created: Long,
    val modified: Long,
    val name: String,
    val login: String,
    val secret: String,
    val comment: String,
    @ColumnInfo(name = "tag_id")
    val tagId: Long? = null,
)
