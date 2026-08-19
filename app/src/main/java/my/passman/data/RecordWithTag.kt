package my.passman.data

import androidx.room.Embedded
import androidx.room.Relation

data class RecordWithTag(
    @Embedded val record: Record,
    @Relation(
        parentColumn = "tag_id",
        entityColumn = "id"
    )
    val tag: Tag?
)
