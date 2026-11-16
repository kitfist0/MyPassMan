package my.passman.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["uuid"],
            childColumns = ["tag_uuid"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["tag_uuid"]), Index(value = ["uuid"], unique = true)],
)
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identifies the record across devices and backups; [id] only means something on this device. */
    val uuid: String,
    val created: Long,
    val modified: Long,
    val name: String,
    val login: String,
    val secret: String,
    val comment: String,
    /** The record's tag, by [Tag.uuid]. */
    @ColumnInfo(name = "tag_uuid")
    val tagUuid: String? = null,
) {
    companion object {
        fun newUuid(): String = UUID.randomUUID().toString()
    }
}
