package com.danycli.gitstreakwidget

import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // MockWebServer works better with explicit SDK in Robolectric sometimes
class GitWidgetTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        StreakRepository.okHttpClientForTest = okhttp3.OkHttpClient.Builder().build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        StreakRepository.okHttpClientForTest = null
    }

    @Test
    fun `fetchAccurateStreak with REST API returns correct data`() {
        val today = LocalDate.now().toString()
        val mockJsonResponse = """
            {
              "contributions": [
                { "date": "$today", "count": 5 }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockJsonResponse))

        val baseUrl = mockWebServer.url("/").toString()
        
        val result = StreakRepository.fetchAccurateStreak(
            username = "testuser",
            token = null,
            restBaseUrl = baseUrl
        )

        assertTrue(result is FetchResult.Success)
        val successData = (result as FetchResult.Success).data
        assertTrue(successData.committedToday)
        assertEquals(1, successData.streakCount)
    }

    @Test
    fun `fetchAccurateStreak with REST API handles 404 User Not Found`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val baseUrl = mockWebServer.url("/").toString()
        
        val result = StreakRepository.fetchAccurateStreak(
            username = "unknownuser",
            token = null,
            restBaseUrl = baseUrl
        )

        assertTrue(result is FetchResult.Error)
        val errorResult = result as FetchResult.Error
        assertEquals("User not found", errorResult.message)
    }

    @Test
    fun `fetchAccurateStreak with GraphQL API returns correct data`() {
        val today = LocalDate.now().toString()
        val mockJsonResponse = """
            {
              "data": {
                "user": {
                  "contributionsCollection": {
                    "contributionCalendar": {
                      "weeks": [
                        {
                          "contributionDays": [
                            {
                              "contributionCount": 2,
                              "date": "$today"
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockJsonResponse))

        val baseUrl = mockWebServer.url("/").toString()
        
        val result = StreakRepository.fetchAccurateStreak(
            username = "testuser",
            token = "dummy_token",
            graphQlBaseUrl = baseUrl
        )

        assertTrue(result is FetchResult.Success)
        val successData = (result as FetchResult.Success).data
        assertTrue(successData.committedToday)
        assertEquals(1, successData.streakCount)
    }
}
