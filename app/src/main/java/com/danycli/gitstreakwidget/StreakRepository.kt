package com.danycli.gitstreakwidget

import androidx.annotation.VisibleForTesting
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object StreakRepository {
    @VisibleForTesting
    var okHttpClientForTest: OkHttpClient? = null

    private val okHttpClient by lazy {
        okHttpClientForTest ?: OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun fetchAccurateStreak(
        username: String,
        token: String? = null,
        graphQlBaseUrl: String = "https://api.github.com/graphql",
        restBaseUrl: String = "https://github-contributions-api.jogruber.de/v4/"
    ): FetchResult {
        return try {
            val startTime = System.currentTimeMillis()
            Timber.d("Starting streak fetch for user: $username")

            val contributionMap = mutableMapOf<LocalDate, Boolean>()
            val localZone = ZoneId.systemDefault()
            val today = LocalDate.now(localZone)

            if (!token.isNullOrBlank()) {
                var currentToDate = today
                var currentFromDate = today.minusYears(1)
                var streakBroken = false
                var checkDate = today
                var firstCheck = true

                while (!streakBroken) {
                    val jsonBody = JSONObject().apply {
                        val query = """
                            query(${'$'}userName: String!, ${'$'}from: DateTime!, ${'$'}to: DateTime!) {
                              user(login: ${'$'}userName) {
                                contributionsCollection(from: ${'$'}from, to: ${'$'}to) {
                                  contributionCalendar {
                                    weeks {
                                      contributionDays {
                                        contributionCount
                                        date
                                      }
                                    }
                                  }
                                }
                              }
                            }
                        """.trimIndent()
                        put("query", query)
                        put("variables", JSONObject()
                            .put("userName", username)
                            .put("from", "${currentFromDate}T00:00:00Z")
                            .put("to", "${currentToDate}T23:59:59Z")
                        )
                    }

                    val request = Request.Builder()
                        .url(graphQlBaseUrl)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/json")
                        .build()

                    try {
                        okHttpClient.newCall(request).execute().use { response ->
                            val responseCode = response.code
                            if (responseCode == 200) {
                                val responseBody = response.body?.string() ?: ""
                                val json = JSONObject(responseBody)

                                if (json.has("errors")) {
                                    Timber.e("GraphQL parsing error: json has errors field")
                                    return FetchResult.Error("GraphQL Error", canRetry = false)
                                }

                                val weeksArray = json.getJSONObject("data")
                                    .getJSONObject("user")
                                    .getJSONObject("contributionsCollection")
                                    .getJSONObject("contributionCalendar")
                                    .getJSONArray("weeks")

                                for (i in 0 until weeksArray.length()) {
                                    val week = weeksArray.getJSONObject(i)
                                    val days = week.getJSONArray("contributionDays")
                                    for (j in 0 until days.length()) {
                                        val dayData = days.getJSONObject(j)
                                        val dateStr = dayData.getString("date")
                                        val date = LocalDate.parse(dateStr)
                                        val count = dayData.getInt("contributionCount")
                                        contributionMap[date] = count > 0
                                    }
                                }
                            } else if (responseCode == 401) {
                                Timber.w("Auth failed (401) with PAT")
                                return FetchResult.Error("Auth failed: Invalid PAT", canRetry = false)
                            } else if (responseCode in 400..499) {
                                Timber.w("API Error: $responseCode")
                                return FetchResult.Error("API Error: $responseCode", canRetry = false)
                            } else {
                                Timber.w("GitHub Server Error: $responseCode")
                                return FetchResult.Error("GitHub Server Error: $responseCode", canRetry = true)
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        Timber.e(e, "Connection timed out fetching GraphQL")
                        return FetchResult.Error("Connection Timed Out", canRetry = true)
                    } catch (e: java.io.IOException) {
                        Timber.e(e, "Network error fetching GraphQL")
                        return FetchResult.Error("Network Error", canRetry = true)
                    }

                    if (firstCheck) {
                        val committedToday = contributionMap[today] ?: false
                        checkDate = if (committedToday) today else today.minusDays(1)
                        firstCheck = false
                    }

                    while (!streakBroken && checkDate >= currentFromDate) {
                        if (contributionMap[checkDate] == true) {
                            checkDate = checkDate.minusDays(1)
                        } else {
                            streakBroken = true
                        }
                    }

                    if (!streakBroken) {
                        Timber.i("Streak still alive at boundary ($currentFromDate)! Paginating to previous year...")
                        currentToDate = currentFromDate.minusDays(1)
                        currentFromDate = currentToDate.minusYears(1)
                    }
                }
            } else {
                val url = if (restBaseUrl.endsWith("/")) "$restBaseUrl$username" else "$restBaseUrl/$username"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        val responseCode = response.code
                        if (responseCode == 200) {
                            val responseBody = response.body?.string() ?: ""
                            val json = JSONObject(responseBody)
                            val weeksArray = json.getJSONArray("contributions")

                            for (i in 0 until weeksArray.length()) {
                                val dayData = weeksArray.getJSONObject(i)
                                val dateStr = dayData.getString("date")
                                val date = LocalDate.parse(dateStr)
                                val count = dayData.getInt("count")
                                contributionMap[date] = count > 0
                            }
                        } else if (responseCode == 404) {
                            Timber.w("User not found (404)")
                            return FetchResult.Error("User not found", canRetry = false)
                        } else if (responseCode in 400..499) {
                            Timber.w("API Error: $responseCode")
                            return FetchResult.Error("API Error: $responseCode", canRetry = false)
                        } else {
                            Timber.w("Server Error: $responseCode")
                            return FetchResult.Error("Server Error: $responseCode", canRetry = true)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    Timber.e(e, "Connection timed out fetching REST")
                    return FetchResult.Error("Connection Timed Out", canRetry = true)
                } catch (e: java.io.IOException) {
                    Timber.e(e, "Network error fetching REST")
                    return FetchResult.Error("Network Error", canRetry = true)
                }
            }

            val committedToday = contributionMap[today] ?: false
            var streak = 0
            var checkDate = if (committedToday) today else today.minusDays(1)

            while (contributionMap[checkDate] == true) {
                streak++
                checkDate = checkDate.minusDays(1)
            }

            val latency = System.currentTimeMillis() - startTime
            Timber.i("Fetched streak successfully in ${latency}ms (Streak: $streak, Today: $committedToday)")

            FetchResult.Success(StreakData(streak, committedToday, contributionMap))
        } catch (e: org.json.JSONException) {
            Timber.e(e, "JSON Parse Error while processing response")
            FetchResult.Error("Parse Error: Invalid Data", canRetry = false)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error fetching streak data")
            FetchResult.Error("Unexpected Error: ${e.localizedMessage}", canRetry = false)
        }
    }
}

data class StreakData(
    val streakCount: Int,
    val committedToday: Boolean,
    val history: Map<LocalDate, Boolean>
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("streakCount", streakCount)
        json.put("committedToday", committedToday)
        val historyJson = JSONObject()
        history.forEach { (date, committed) ->
            historyJson.put(date.toString(), committed)
        }
        json.put("history", historyJson)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): StreakData? {
            return try {
                val json = JSONObject(jsonStr)
                val streakCount = json.getInt("streakCount")
                val committedToday = json.getBoolean("committedToday")
                val historyJson = json.getJSONObject("history")
                val history = mutableMapOf<LocalDate, Boolean>()
                for (key in historyJson.keys()) {
                    history[LocalDate.parse(key)] = historyJson.getBoolean(key)
                }
                StreakData(streakCount, committedToday, history)
            } catch (e: Exception) {
                null
            }
        }
    }
}

sealed class FetchResult {
    data class Success(val data: StreakData) : FetchResult()
    data class Error(val message: String, val canRetry: Boolean) : FetchResult()
}
