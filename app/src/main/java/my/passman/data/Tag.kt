package my.passman.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true), Index(value = ["uuid"], unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identifies the tag across devices and backups; [id] only means something on this device. */
    val uuid: String,
    val name: String,
    // A default keeps backups made before tags had colors readable.
    val color: TagColor = TagColor.DEFAULT,
) {
    /**
     * Whether this tag is called [name], ignoring case: "Work" and "work" are the same tag. Done here
     * rather than by the database, whose case folding (NOCASE) only covers ASCII letters.
     */
    fun hasName(name: String): Boolean = this.name.equals(name, ignoreCase = true)

    companion object {
        const val MAX_NAME_LENGTH = 25

        fun newUuid(): String = UUID.randomUUID().toString()

        fun isValidName(name: String): Boolean =
            name.length in 1..MAX_NAME_LENGTH &&
                name.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    }
}
