package com.anitail.music.services

import android.annotation.SuppressLint
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.hilt.work.WorkerAssistedFactory
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anitail.music.utils.SyncUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic Google Drive sync.
 * Runs every 6 hours in the background when connected to internet.
 * Uses throttling built into SyncUtils.syncCloud() to prevent excessive syncs.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncUtils: SyncUtils
) : CoroutineWorker(context, params) {

    // Factory for creating SyncWorker instances with Hilt
    @SuppressLint("RestrictedApi")
    @AssistedFactory
    interface Factory : WorkerAssistedFactory<SyncWorker> {
        override fun create(
            appContext: Context,
            params: WorkerParameters
        ): SyncWorker
    }

    companion object {
        private const val SYNC_WORK_NAME = "periodic_cloud_sync"
        private const val SYNC_INTERVAL_HOURS = 6L
        private const val WORKER_TIMEOUT_MS = 120_000L // 2 minutes max

        /**
         * Schedule periodic cloud sync.
         * Uses KEEP policy to avoid resetting the schedule on every app launch.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                SYNC_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.MINUTES) // Wait 30 min after app start
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Timber.d("SyncWorker: Scheduled periodic sync every $SYNC_INTERVAL_HOURS hours")
        }

        /**
         * Cancel scheduled sync work.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
            Timber.d("SyncWorker: Cancelled periodic sync")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.d("SyncWorker: Starting background sync...")

        return@withContext try {
            val result = withTimeoutOrNull(WORKER_TIMEOUT_MS) {
                syncUtils.syncCloud()
            }

            when {
                result == null -> {
                    Timber.e("SyncWorker: Sync timed out or database unavailable")
                    Result.success() // Don't retry if database was closed
                }

                result.contains("failed", ignoreCase = true) ||
                        result.contains("fallo", ignoreCase = true) -> {
                    Timber.e("SyncWorker: Sync failed - $result")
                    Result.retry()
                }

                else -> {
                    Timber.d("SyncWorker: Sync completed - $result")
                    Result.success()
                }
            }
        } catch (e: IllegalStateException) {
            // Handle database closed errors gracefully - don't retry
            if (e.message?.contains("already-closed") == true) {
                Timber.w("SyncWorker: Database was closed, aborting sync")
                Result.success()
            } else {
                Timber.e(e, "SyncWorker: IllegalStateException during sync")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker: Unexpected error during sync")
            Result.retry()
        }
    }
}
