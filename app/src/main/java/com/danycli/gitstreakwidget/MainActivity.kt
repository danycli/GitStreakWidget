package com.danycli.gitstreakwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danycli.gitstreakwidget.ui.theme.GitStreakWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
//This app is fully vibe coded
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitStreakWidgetTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("GitStreakPrefs", Context.MODE_PRIVATE)
    
    var savedUsername by remember { 
        mutableStateOf(sharedPreferences.getString("github_username", "") ?: "") 
    }
    
    var showLogin by remember { mutableStateOf(savedUsername.isEmpty()) }

    Scaffold(
        topBar = {
            if (!showLogin) {
                TopAppBar(
                    title = { Text("GitStreak", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showLogin = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Change Username", tint = Color(0xFF3fb950))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1c2128),
                        titleContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color(0xFF0d1117)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (showLogin) {
                LoginScreen(
                    currentUsername = savedUsername,
                    onSave = { newUsername ->
                        sharedPreferences.edit().putString("github_username", newUsername).apply()
                        savedUsername = newUsername
                        showLogin = false
                        updateWidget(context)
                    }
                )
            } else {
                GuideScreen(username = savedUsername)
            }
        }
    }
}

@Composable
fun LoginScreen(currentUsername: String, onSave: (String) -> Unit) {
    var username by remember { mutableStateOf(currentUsername) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to GitStreak",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Enter your GitHub username to begin tracking your progress.",
            fontSize = 16.sp,
            color = Color(0xFF8b949e),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { 
                username = it
                errorMessage = null 
            },
            label = { Text("GitHub Username", color = Color(0xFF8b949e)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorMessage != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3fb950),
                unfocusedBorderColor = Color(0xFF30363d),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                errorBorderColor = Color.Red
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start).padding(top = 4.dp, start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { 
                if (username.isNotBlank() && !isLoading) {
                    isLoading = true
                    scope.launch {
                        val exists = checkUserExists(username.trim())
                        if (exists) {
                            onSave(username.trim())
                        } else {
                            errorMessage = "Username not found on GitHub"
                        }
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Launch Tracker 🔥", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

suspend fun checkUserExists(username: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/users/$username")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.responseCode == 200
    } catch (e: Exception) {
        false
    }
}

@Composable
fun GuideScreen(username: String) {
    var streakCount by remember { mutableStateOf<Int?>(null) }
    var isFetching by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        scope.launch {
            val data = fetchAccurateStreakLocal(username)
            streakCount = data?.streakCount
            isFetching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1c2128)),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363d))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connected: $username",
                    color = Color(0xFF3fb950),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ready to fire up your streak!",
                    color = Color(0xFF8b949e),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "HOW TO ADD WIDGET",
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF8b949e),
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        GuideItem(step = "1", title = "Long Press", description = "Go to your home screen and long press on any empty space.")
        GuideItem(step = "2", title = "Select Widgets", description = "Tap on the 'Widgets' button from the menu.")
        GuideItem(step = "3", title = "Find GitStreak", description = "Drag the 3x2 widget to your home screen.")

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    brush = Brush.linearGradient(listOf(Color(0xFF667eea), Color(0xFF764ba2))),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isFetching) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 50.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("${streakCount ?: 0} day streak", color = Color(0xFFFFCB2B), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("System: Operational", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun GuideItem(step: String, title: String, description: String) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF238636), shape = RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(description, color = Color(0xFF8b949e), fontSize = 14.sp)
        }
    }
}

fun updateWidget(context: Context) {
    val intent = Intent(context, GitWidget::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    }
    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, GitWidget::class.java)
    )
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    context.sendBroadcast(intent)
}

// Re-implementing the streak logic here to avoid Unresolved Reference if it wasn't exported
data class StreakDataLocal(val streakCount: Int)

suspend fun fetchAccurateStreakLocal(username: String): StreakDataLocal? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://github-contributions-api.deno.dev/$username.json")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000

        if (connection.responseCode != 200) return@withContext null

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

        StreakDataLocal(streak)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
