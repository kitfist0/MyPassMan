package my.passman.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
) {
    companion object {
        const val MAX_NAME_LENGTH = 25

        fun isValidName(name: String): Boolean {
            return name.length in 1..MAX_NAME_LENGTH && 
                   name.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
    }
}
