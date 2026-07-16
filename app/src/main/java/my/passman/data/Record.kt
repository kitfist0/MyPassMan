package my.passman.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class Record(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val created: Long,
    val modified: Long,
    val name: String,
    val secret: String,
    val comment: String,
)
