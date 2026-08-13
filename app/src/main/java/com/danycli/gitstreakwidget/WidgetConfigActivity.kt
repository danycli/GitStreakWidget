package com.danycli.gitstreakwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_config)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val sharedPreferences = PreferencesHelper.getEncryptedSharedPreferences(this)
        
        val usernameInput = findViewById<EditText>(R.id.username_input)
        val tokenInput = findViewById<EditText>(R.id.token_input)
        val saveButton = findViewById<Button>(R.id.save_button)
        val errorText = findViewById<TextView>(R.id.error_text)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        usernameInput.setText(sharedPreferences.getString("github_username", ""))
        tokenInput.setText(sharedPreferences.getString("github_pat", ""))

        saveButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val token = tokenInput.text.toString().trim()

            val regex = "^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$".toRegex()
            if (username.isBlank()) {
                errorText.text = "Username cannot be empty"
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            } else if (!regex.matches(username)) {
                errorText.text = "Invalid GitHub username format"
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            errorText.visibility = View.GONE
            saveButton.isEnabled = false
            saveButton.text = ""
            progressBar.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                val result = StreakRepository.fetchAccurateStreak(username, token)
                
                withContext(Dispatchers.Main) {
                    if (result is FetchResult.Success) {
                        // Cache the data immediately so it's ready for the widget
                        sharedPreferences.edit()
                            .putString("github_username", username)
                            .putString("github_pat", token)
                            .putString("cached_streak_data", result.data.toJson())
                            .apply()
            
                        // Update the widget
                        val appWidgetManager = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                        updateAppWidget(this@WidgetConfigActivity, appWidgetManager, appWidgetId)
            
                        // Start the periodic worker
                        scheduleStreakWorker(this@WidgetConfigActivity)
            
                        // Pass back original widgetId
                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(RESULT_OK, resultValue)
                        finish()
                    } else if (result is FetchResult.Error) {
                        errorText.text = result.message
                        errorText.visibility = View.VISIBLE
                        saveButton.isEnabled = true
                        saveButton.text = "Launch Tracker"
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }
}
