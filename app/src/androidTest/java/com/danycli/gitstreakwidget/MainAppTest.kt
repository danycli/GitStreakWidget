package com.danycli.gitstreakwidget

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreenValidatesEmptyUsername() {
        composeTestRule.setContent {
            LoginScreen(
                currentUsername = "",
                currentToken = "",
                onSave = { _, _ -> }
            )
        }

        // Initially no error message
        composeTestRule.onNodeWithText("Username cannot be empty").assertDoesNotExist()

        // Click the launch button without entering a username
        composeTestRule.onNodeWithText("Launch Tracker 🔥").performClick()

        // Error message should appear
        composeTestRule.onNodeWithText("Username cannot be empty").assertIsDisplayed()
    }

    @Test
    fun loginScreenValidatesInvalidUsernameFormat() {
        composeTestRule.setContent {
            LoginScreen(
                currentUsername = "",
                currentToken = "",
                onSave = { _, _ -> }
            )
        }

        // Enter an invalid username (starts with a hyphen)
        composeTestRule.onNodeWithText("GitHub Username").performTextInput("-invalid")
        composeTestRule.onNodeWithText("Launch Tracker 🔥").performClick()

        // Error message should appear
        composeTestRule.onNodeWithText("Invalid GitHub username format").assertIsDisplayed()
    }
}
