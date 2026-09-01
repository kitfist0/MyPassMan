package my.passman.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import my.passman.data.AppDatabase
import my.passman.data.RecordDao
import my.passman.data.TagDao
import my.passman.util.BackupManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideBackupManager(
        recordDao: RecordDao,
        tagDao: TagDao,
    ): BackupManager = BackupManager(recordDao, tagDao)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "pass_man_database",
            ).build()

    @Provides
    fun provideRecordDao(database: AppDatabase): RecordDao = database.recordDao()

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao = database.tagDao()
}
