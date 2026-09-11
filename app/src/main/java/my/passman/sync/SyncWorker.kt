package my.passman.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import my.passman.data.SettingsRepository
import my.passman.data.SyncProvider
import my.passman.sync.yandex.YandexSyncManager

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val yandexSyncManager: YandexSyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result =
            when (settingsRepository.syncProvider.first()) {
                SyncProvider.GOOGLE_DRIVE -> syncManager.sync()
                SyncProvider.YANDEX_DISK -> yandexSyncManager.sync()
                SyncProvider.NONE -> SyncResult.Disabled
            }
        return when (result) {
            is SyncResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }
}
