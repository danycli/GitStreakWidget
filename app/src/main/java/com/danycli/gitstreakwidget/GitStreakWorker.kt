package com.danycli.gitstreakwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class GitStreakWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.i("GitStreakWorker started: Checking for updates")
        
        val sharedPreferences =
            applicationContext.getSharedPreferences("GitStreakPrefs", Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("github_username", "") ?: ""
        val token = sharedPreferences.getString("github_pat", "") ?: ""

        if (username.isBlank()) {
            Timber.i("GitStreakWorker skipped: No username configured")
            return@withContext Result.success()
        }

        val result = StreakRepository.fetchAccurateStreak(username, token)
        when (result) {
            is FetchResult.Success -> {
                sharedPreferences.edit().putString("cached_streak_data", result.data.toJson()).apply()

                // Broadcast update to widget
                val intent = Intent(applicationContext, GitWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                }
                val ids = AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(
                    ComponentName(applicationContext, GitWidget::class.java)
                )
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                applicationContext.sendBroadcast(intent)

                Timber.i("GitStreakWorker succeeded: Updated widget")
                Result.success()
            }
            is FetchResult.Error -> {
                if (result.canRetry) {
                    Timber.w("GitStreakWorker retrying: ${result.message}")
                    Result.retry()
                } else {
                    Timber.e("GitStreakWorker failed: ${result.message}")
                    Result.failure()
                }
            }
        }
    }
}

fun scheduleStreakWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workRequest = PeriodicWorkRequestBuilder<GitStreakWorker>(30, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(
            androidx.work.BackoffPolicy.EXPONENTIAL,
            androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "GitStreakSync",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

fun triggerImmediateSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<GitStreakWorker>()
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "GitStreakSyncImmediate",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}
