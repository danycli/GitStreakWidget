package com.danycli.gitstreakwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal const val KEY_USERNAME = "github_username"
internal const val KEY_TOKEN = "github_pat"
internal const val KEY_CACHED_DATA = "cached_streak_data"
internal const val KEY_COUNTED_DATE = "streak_counted_date"

internal const val SYNC_TAG = "GitStreakSync"
internal const val LEGACY_PERIODIC_WORK = "GitStreakSync"
internal const val IMMEDIATE_WORK = "GitStreakSyncImmediate"

/**
 * Hours of the day (local time) at which the streak is re-checked.
 * Morning, afternoon and night.
 */
internal val CHECK_HOURS = listOf(8, 14, 20)

class GitStreakWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Credentials and cache live in EncryptedSharedPreferences; reading them with a plain
        // getSharedPreferences() call would hand back encrypted blobs instead of the real values.
        val prefs = PreferencesHelper.getEncryptedSharedPreferences(applicationContext)
        val today = LocalDate.now(ZoneId.systemDefault())

        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val token = prefs.getString(KEY_TOKEN, "") ?: ""

        if (username.isBlank()) {
            Timber.i("GitStreakWorker skipped: no username configured")
            scheduleStreakChecks(applicationContext, skipRestOfToday = false)
            return@withContext Result.success()
        }

        // The streak for today is already counted, so abandon the remaining checks of the day.
        if (prefs.getString(KEY_COUNTED_DATE, null) == today.toString()) {
            Timber.i("GitStreakWorker skipped: streak already counted for $today")
            scheduleStreakChecks(applicationContext, skipRestOfToday = true)
            return@withContext Result.success()
        }

        when (val result = StreakRepository.fetchAccurateStreak(username, token)) {
            is FetchResult.Success -> {
                val countedToday = result.data.committedToday
                prefs.edit()
                    .putString(KEY_CACHED_DATA, result.data.toJson())
                    .putString(KEY_COUNTED_DATE, if (countedToday) today.toString() else null)
                    .apply()

                notifyWidgets()

                scheduleStreakChecks(applicationContext, skipRestOfToday = countedToday)
                Timber.i(
                    "GitStreakWorker succeeded: streak=${result.data.streakCount}, " +
                        "countedToday=$countedToday"
                )
                Result.success()
            }
            is FetchResult.Error -> {
                Timber.w("GitStreakWorker error: ${result.message} (canRetry=${result.canRetry})")
                scheduleStreakChecks(applicationContext, skipRestOfToday = false)
                if (result.canRetry) Result.retry() else Result.failure()
            }
        }
    }

    private fun notifyWidgets() {
        val ids = AppWidgetManager.getInstance(applicationContext).getAppWidgetIds(
            ComponentName(applicationContext, GitWidget::class.java)
        )
        val intent = Intent(applicationContext, GitWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        applicationContext.sendBroadcast(intent)
    }
}

/**
 * Slots still worth checking: the remaining hours of today, or the next day's slots
 * when today is exhausted (or [skipRestOfToday] is set because the streak is already counted).
 */
internal fun nextCheckSlots(now: LocalDateTime, skipRestOfToday: Boolean = false): List<LocalDateTime> {
    val today = now.toLocalDate()
    val remainingToday = if (skipRestOfToday) {
        emptyList()
    } else {
        CHECK_HOURS.map { today.atTime(it, 0) }.filter { it.isAfter(now) }
    }
    return remainingToday.ifEmpty { CHECK_HOURS.map { today.plusDays(1).atTime(it, 0) } }
}

/** (Re)arms the morning / afternoon / night checks for today and beyond. */
fun scheduleStreakWorker(context: Context) {
    scheduleStreakChecks(context, skipRestOfToday = false)
}

internal fun scheduleStreakChecks(context: Context, skipRestOfToday: Boolean) {
    val now = LocalDateTime.now(ZoneId.systemDefault())
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val workManager = WorkManager.getInstance(context)

    // Drop the old 30-minute periodic job from previous versions, if it is still around.
    workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK)

    for (slot in nextCheckSlots(now, skipRestOfToday)) {
        val delayMillis = Duration.between(now, slot).toMillis().coerceAtLeast(0L)
        val workRequest = OneTimeWorkRequestBuilder<GitStreakWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(SYNC_TAG)
            .build()
        workManager.enqueueUniqueWork(
            "${SYNC_TAG}_$slot",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        Timber.i("GitStreakWorker scheduled for $slot (in ${delayMillis / 60000} min)")
    }
}

fun triggerImmediateSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<GitStreakWorker>()
        .setConstraints(constraints)
        .addTag(SYNC_TAG)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        IMMEDIATE_WORK,
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}

/** Tears down all pending streak checks, used when the last widget is removed. */
fun cancelStreakWork(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelAllWorkByTag(SYNC_TAG)
    workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK)
    workManager.cancelUniqueWork(IMMEDIATE_WORK)
}
