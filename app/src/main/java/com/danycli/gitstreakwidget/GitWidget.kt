package com.danycli.gitstreakwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale


class GitWidget : AppWidgetProvider() {
    
    companion object {
        const val ACTION_MANUAL_REFRESH = "com.danycli.gitstreakwidget.ACTION_MANUAL_REFRESH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MANUAL_REFRESH) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                updateAppWidget(context, appWidgetManager, appWidgetId, isSyncing = true)
            }
            triggerImmediateSync(context)
        }
    }
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scheduleStreakWorker(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleStreakWorker(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("GitStreakSync")
        PreferencesHelper.getEncryptedSharedPreferences(context).edit().clear().apply()
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    isSyncing: Boolean = false
) {
    val views = RemoteViews(context.packageName, R.layout.git_widget)
    val sharedPreferences = PreferencesHelper.getEncryptedSharedPreferences(context)
    val username = sharedPreferences.getString("github_username", "") ?: ""
    val token = sharedPreferences.getString("github_pat", "") ?: ""

    // Always set click intent to open configuration
    val intent = Intent(context, WidgetConfigActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Add flags to ensure a new instance is created if needed
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context, appWidgetId, intent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    val refreshIntent = Intent(context, GitWidget::class.java).apply {
        action = GitWidget.ACTION_MANUAL_REFRESH
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
    val refreshPendingIntent = PendingIntent.getBroadcast(
        context, appWidgetId, refreshIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.refresh_icon, refreshPendingIntent)

    if (username.isBlank()) {
        // Show "Setup Required" state
        views.setTextViewText(R.id.streak_count_text, "Setup App")
        views.setViewVisibility(R.id.streak_label_text, View.GONE)
        views.setTextViewText(R.id.status_text, "Tap to enter username")
        views.setViewVisibility(R.id.weekly_view, View.GONE)
        views.setViewVisibility(R.id.divider, View.GONE)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        return
    }

    // Normal functionality
    views.setViewVisibility(R.id.weekly_view, View.VISIBLE)
    views.setViewVisibility(R.id.divider, View.VISIBLE)
    views.setViewVisibility(R.id.streak_label_text, View.VISIBLE)

    fun renderData(streakData: StreakData, isOffline: Boolean = false, offlineError: String? = null) {
        views.setTextViewText(R.id.streak_count_text, streakData.streakCount.toString())
        
        if (streakData.committedToday) {
            views.setTextViewText(R.id.status_text, "Committed today 🔥 Keep it up!" + if (isOffline) " (Offline)" else "")
        } else {
            val suffix = if (offlineError != null) " ($offlineError)" else if (isOffline) " (Offline)" else ""
            views.setTextViewText(R.id.status_text, "No commit today yet 😿$suffix")
        }

        val localZone = ZoneId.systemDefault()
        val today = LocalDate.now(localZone)
        for (i in 0 until 6) {
            val date = today.minusDays(i.toLong())
            updateDayView(views, i, date, streakData.history[date] ?: false)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    val cachedJson = sharedPreferences.getString("cached_streak_data", null)
    if (cachedJson != null) {
        StreakData.fromJson(cachedJson)?.let {
            renderData(it, isOffline = false)
        }
    } else {
        views.setTextViewText(R.id.status_text, "Syncing...")
        appWidgetManager.updateAppWidget(appWidgetId, views)
        if (!isSyncing) triggerImmediateSync(context)
    }

    if (isSyncing) {
        views.setTextViewText(R.id.status_text, "Syncing...")
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

private fun updateDayView(views: RemoteViews, index: Int, date: LocalDate, hasCommits: Boolean) {
    val dayLetter = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    
    val containerId = when(index) {
        0 -> R.id.day_0_container
        1 -> R.id.day_1_container
        2 -> R.id.day_2_container
        3 -> R.id.day_3_container
        4 -> R.id.day_4_container
        else -> R.id.day_5_container
    }
    
    val letterId = when(index) {
        0 -> R.id.day_0_letter
        1 -> R.id.day_1_letter
        2 -> R.id.day_2_letter
        3 -> R.id.day_3_letter
        4 -> R.id.day_4_letter
        else -> R.id.day_5_letter
    }
    
    val iconId = when(index) {
        0 -> R.id.day_0_icon
        1 -> R.id.day_1_icon
        2 -> R.id.day_2_icon
        3 -> R.id.day_3_icon
        4 -> R.id.day_4_icon
        else -> R.id.day_5_icon
    }

    views.setTextViewText(letterId, dayLetter)
    
    if (hasCommits) {
        views.setInt(containerId, "setBackgroundResource", R.drawable.day_box_active)
        views.setImageViewResource(iconId, R.drawable.ic_fire_mascot)
        views.setViewVisibility(iconId, View.VISIBLE)
    } else {
        views.setInt(containerId, "setBackgroundResource", R.drawable.day_box_inactive)
        views.setImageViewResource(iconId, android.R.color.transparent)
        views.setViewVisibility(iconId, View.INVISIBLE)
    }
}

