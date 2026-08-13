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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import java.time.LocalDate
import java.time.ZoneId

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
    LaunchedEffect(Unit) {
        scheduleStreakWorker(context)
    }
    val sharedPreferences = PreferencesHelper.getEncryptedSharedPreferences(context)
    
    var savedUsername by remember { 
        mutableStateOf(sharedPreferences.getString("github_username", "") ?: "") 
    }
    var savedToken by remember {
        mutableStateOf(sharedPreferences.getString("github_pat", "") ?: "")
    }
    
    var showLogin by remember { mutableStateOf(savedUsername.isEmpty()) }

    Scaffold(
        topBar = {
            if (!showLogin) {
                TopAppBar(
                    title = { Text("GitStreak", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
                    actions = {
                        IconButton(onClick = { showLogin = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Change Username", tint = Color(0xFF8b949e))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
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
                    currentToken = savedToken,
                    onSave = { newUsername, newToken ->
                        sharedPreferences.edit()
                            .putString("github_username", newUsername)
                            .putString("github_pat", newToken)
                            .apply()
                        savedUsername = newUsername
                        savedToken = newToken
                        showLogin = false
                        scheduleStreakWorker(context)
                        updateWidget(context)
                    }
                )
            } else {
                GuideScreen(username = savedUsername, token = savedToken)
            }
        }
    }
}

@Composable
fun LoginScreen(currentUsername: String, currentToken: String, onSave: (String, String) -> Unit) {
    var username by remember { mutableStateOf(currentUsername) }
    var token by remember { mutableStateOf(currentToken) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeroGraphic()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Welcome to GitStreak",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Track and maintain your daily GitHub\ncontribution streaks effortlessly.",
            fontSize = 15.sp,
            color = Color(0xFF8b949e),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { 
                username = it
                errorMessage = null 
            },
            label = { Text("GitHub Username", color = Color(0xFF8b949e)) },
            leadingIcon = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF8b949e)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorMessage != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161b22),
                unfocusedContainerColor = Color(0xFF161b22),
                focusedBorderColor = Color(0xFF3fb950),
                unfocusedBorderColor = Color(0xFF30363d),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                errorBorderColor = Color.Red
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token (Optional)", color = Color(0xFF8b949e)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF8b949e)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF161b22),
                unfocusedContainerColor = Color(0xFF161b22),
                focusedBorderColor = Color(0xFF3fb950),
                unfocusedBorderColor = Color(0xFF30363d),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start).padding(top = 8.dp, start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                val regex = "^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$".toRegex()
                if (username.isBlank()) {
                    errorMessage = "Username cannot be empty"
                } else if (!regex.matches(username.trim())) {
                    errorMessage = "Invalid GitHub username format"
                } else if (!isLoading) {
                    isLoading = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            StreakRepository.fetchAccurateStreak(username.trim(), token.trim())
                        }
                        if (result is FetchResult.Success) {
                            onSave(username.trim(), token.trim())
                        } else if (result is FetchResult.Error) {
                            errorMessage = result.message
                        } else {
                            errorMessage = "Could not fetch data"
                        }
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp).shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF3fb950), ambientColor = Color(0xFF3fb950)),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
            } else {
                Text("Launch Tracker 🔥", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HeroGraphic() {
    val boxSize = 14.dp
    val spacing = 4.dp
    val columns = 11
    val rows = 5
    
    val pattern = listOf(
        0.0f, 0.0f, 0.2f, 0.5f, 0.1f, 0.0f, 0.8f, 0.9f, 0.4f, 0.0f, 0.0f,
        0.0f, 0.3f, 0.6f, 0.9f, 0.8f, 0.2f, 0.4f, 0.7f, 1.0f, 0.5f, 0.0f,
        0.2f, 0.7f, 1.0f, 0.8f, 0.4f, 0.1f, 0.1f, 0.5f, 0.9f, 0.8f, 0.2f,
        0.0f, 0.4f, 0.8f, 0.6f, 0.2f, 0.1f, 0.1f, 0.2f, 0.6f, 0.4f, 0.0f,
        0.0f, 0.0f, 0.3f, 0.2f, 0.1f, 0.0f, 0.1f, 0.1f, 0.2f, 0.0f, 0.0f
    )
    
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (c in 0 until columns) {
                        val alpha = pattern.getOrElse(r * columns + c) { 0.1f }
                        Box(
                            modifier = Modifier
                                .size(boxSize)
                                .background(
                                    color = if (alpha > 0f) Color(0xFF3fb950).copy(alpha = alpha) else Color.Transparent,
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun GuideScreen(username: String, token: String) {
    val context = LocalContext.current
    val sharedPrefs = PreferencesHelper.getEncryptedSharedPreferences(context)

    var streakCount by remember {
        mutableStateOf<Int?>(
            try {
                sharedPrefs.getString("cached_streak_data", null)?.let {
                    JSONObject(it).getInt("streakCount")
                }
            } catch (e: Exception) {
                null
            }
        )
    }
    var isFetching by remember { mutableStateOf(streakCount == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username, token) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                StreakRepository.fetchAccurateStreak(username, token)
            }
            when (result) {
                is FetchResult.Success -> {
                    streakCount = result.data.streakCount
                    sharedPrefs.edit().putString("cached_streak_data", result.data.toJson()).apply()
                    errorMessage = null
                }
                is FetchResult.Error -> {
                    if (streakCount == null) {
                        errorMessage = result.message
                    }
                }
            }
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
        Spacer(modifier = Modifier.height(10.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161b22)),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF21262d))
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Connected: $username",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Ready to fire up your streak!",
                            color = Color(0xFF8b949e),
                            fontSize = 13.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF3fb950), shape = androidx.compose.foundation.shape.CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161b22)),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF21262d))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "How to Add Widget",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalProgressSteps()
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color(0xFF3fb950),
                    ambientColor = Color(0xFF3fb950)
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1c2128), Color(0xFF0d1117))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0x333fb950),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isFetching) {
                CircularProgressIndicator(color = Color(0xFF3fb950))
            } else if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${streakCount ?: 0} day streak", 
                        color = Color.White, 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(0xFF3fb950), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF0d1117),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System: Operational", color = Color(0xFF3fb950), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun HorizontalProgressSteps() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .padding(top = 15.dp)
                .fillMaxWidth(0.66f)
                .height(2.dp)
                .background(Color(0xFF30363d))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ProgressStep(step = "1", title = "Long Press", modifier = Modifier.weight(1f))
            ProgressStep(step = "2", title = "Select Widgets", modifier = Modifier.weight(1f))
            ProgressStep(step = "3", title = "Find GitStreak", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ProgressStep(step: String, title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF161b22), CircleShape)
                .border(1.dp, Color(0xFF30363d), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color(0xFF8b949e), CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "$step. ${title.split(" ").first()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (title.contains(" ")) {
            Text(text = title.split(" ", limit = 2)[1], color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
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



// Local logic removed to reuse fetchAccurateStreak from GitWidget.kt
