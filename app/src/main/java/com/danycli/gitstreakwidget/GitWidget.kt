package com.danycli.gitstreakwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class GitWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.git_widget)
    val sharedPreferences = context.getSharedPreferences("GitStreakPrefs", Context.MODE_PRIVATE)
    val username = sharedPreferences.getString("github_username", "") ?: ""

    // Always set click intent to open our own app
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent, 
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

    if (username.isBlank()) {
        // Show "Setup Required" state
        views.setTextViewText(R.id.streak_count_text, "Setup App")
        views.setTextViewText(R.id.status_text, "Tap to enter username")
        views.setViewVisibility(R.id.weekly_view, View.GONE)
        views.setViewVisibility(R.id.divider, View.GONE)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        return
    }

    // Normal functionality
    views.setViewVisibility(R.id.weekly_view, View.VISIBLE)
    views.setViewVisibility(R.id.divider, View.VISIBLE)

    CoroutineScope(Dispatchers.IO).launch {
        val streakData = fetchAccurateStreak(username)
        
        withContext(Dispatchers.Main) {
            if (streakData != null) {
                views.setTextViewText(R.id.streak_count_text, "${streakData.streakCount} day streak")
                
                if (streakData.committedToday) {
                    views.setTextViewText(R.id.status_text, "Committed today 🔥 Keep it up!")
                } else {
                    views.setTextViewText(R.id.status_text, "No commit today yet 😿")
                }

                val localZone = ZoneId.systemDefault()
                val today = LocalDate.now(localZone)
                for (i in 0 until 6) {
                    val date = today.minusDays(i.toLong())
                    updateDayView(views, i, date, streakData.history[date] ?: false)
                }
            } else {
                views.setTextViewText(R.id.status_text, "Sync failed: Check Connection")
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
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

data class StreakData(
    val streakCount: Int, 
    val committedToday: Boolean,
    val history: Map<LocalDate, Boolean>
)

fun fetchAccurateStreak(username: String): StreakData? {
    return try {
        val url = URL("https://github-contributions-api.deno.dev/$username.json")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000

        if (connection.responseCode != 200) return null

        val response = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        val weeksArray = json.getJSONArray("contributions")
        
        val contributionMap = mutableMapOf<LocalDate, Boolean>()
        val localZone = ZoneId.systemDefault()
        val today = LocalDate.now(localZone)
        
        for (i in 0 until weeksArray.length()) {
            val week = weeksArray.getJSONArray(i)
            for (j in 0 until week.length()) {
                val dayData = week.getJSONObject(j)
                val dateStr = dayData.getString("date")
                val date = LocalDate.parse(dateStr)
                val count = dayData.getInt("contributionCount")
                contributionMap[date] = count > 0
            }
        }

        val committedToday = contributionMap[today] ?: false
        var streak = 0
        var checkDate = if (committedToday) today else today.minusDays(1)

        while (contributionMap[checkDate] == true) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        StreakData(streak, committedToday, contributionMap)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
