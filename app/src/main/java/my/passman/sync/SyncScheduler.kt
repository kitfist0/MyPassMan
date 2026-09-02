package my.passman.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Manages the background periodic sync work. Interactive "sync now" calls go through [SyncManager] directly. */
@Singleton
class SyncScheduler
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) {
        fun enablePeriodicSync() {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun disablePeriodicSync() {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        private companion object {
            const val PERIODIC_WORK_NAME = "drive_periodic_sync"
        }
    }
