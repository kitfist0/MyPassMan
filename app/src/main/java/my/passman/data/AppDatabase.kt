package my.passman.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Record::class, Tag::class, VaultState::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    abstract fun tagDao(): TagDao

    abstract fun vaultStateDao(): VaultStateDao
}
