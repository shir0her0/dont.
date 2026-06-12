package com.example.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.TakeASecApplication
import com.example.data.InterventionLog
import com.example.data.Settings
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class InterventionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Disable enter window transition animation so the overlay displays instantly
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                0, 0
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        enableEdgeToEdge()

        val targetPackageName = intent.getStringExtra("TARGET_PACKAGE_NAME") ?: ""
        Log.d("InterventionActivity", "Showing intervention for: $targetPackageName")

        if (targetPackageName.isEmpty()) {
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF05000C) // Premium deep black background
                ) { innerPadding ->
                    InterventionScreenContent(
                        packageName = targetPackageName,
                        activity = this,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        // Disable exit transition animation so return is instant
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                0, 0
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

@Composable
fun InterventionScreenContent(
    packageName: String,
    activity: InterventionActivity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as TakeASecApplication
    val repository = app.repository

    // State loaded from DB
    var mindfulnessText by remember { mutableStateOf("Take a breath...") }
    var delayDurationSeconds by remember { mutableIntStateOf(5) }
    var appLabel by remember { mutableStateOf(packageName) }
    var attemptsLast24h by remember { mutableIntStateOf(1) }
    var lastUseText by remember { mutableStateOf("Never before") }

    // Countdown state
    var countdownRemaining by remember { mutableIntStateOf(5) }
    var isCountdownFinished by remember { mutableStateOf(false) }

    // Loading config from database
    LaunchedEffect(packageName) {
        // Load Settings
        val currentSettings = repository.settings.first()
        mindfulnessText = currentSettings.mindfulnessText
        delayDurationSeconds = currentSettings.delayDurationSeconds
        countdownRemaining = currentSettings.delayDurationSeconds

        // Get app readable name
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appLabel = pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            appLabel = packageName.split(".").lastOrNull() ?: packageName
        }

        // Get count of interventions in last 24h
        val logs = repository.allLogs.firstOrNull() ?: emptyList()
        val limit24h = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        
        val appLogs = logs.filter { it.packageName == packageName }
        attemptsLast24h = appLogs.count { it.timestamp >= limit24h } + 1 // Add 1 for current attempt

        val previousAttempt = appLogs.firstOrNull()
        if (previousAttempt != null) {
            val diffMs = System.currentTimeMillis() - previousAttempt.timestamp
            val diffHours = diffMs / (1000 * 60 * 60)
            val diffMins = diffMs / (1000 * 60)
            
            lastUseText = when {
                diffHours >= 1 -> "$diffHours hours ago"
                diffMins >= 1 -> "$diffMins minutes ago"
                else -> "Just now"
            }
        } else {
            lastUseText = "No previous interventions"
        }
    }

    // Secondary countdown timer
    LaunchedEffect(delayDurationSeconds) {
        countdownRemaining = delayDurationSeconds
        while (countdownRemaining > 0) {
            delay(1000)
            countdownRemaining--
        }
        isCountdownFinished = true
    }

    // Breathing pulse scale animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Smooth breathing scale
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Smooth breathing alpha for the background and halos
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // A secondary slower breathing scale for asynchronous multi-layer ripple depth!
    val depthScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "depthScale"
    )

    // Sleek interface breathing canvas background (radial gradient expansion)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF860462).copy(alpha = pulseAlpha * 0.6f), // hot magenta core gradient expansion
                        Color(0xFF1C0130), // deep violet
                        Color(0xFF05000C)  // pitch black at borders
                    ),
                    radius = 1400f
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // App header guidance
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.ic_logo_transparent),
                contentDescription = "Take a Sec Logo",
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 12.dp)
            )
            Text(
                text = "You're about to open $appLabel",
                color = Color(0xFFB0B3BC),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = mindfulnessText,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Multi-level expressive breathing halos
        Box(
            modifier = Modifier
                .size(320.dp)
                .scale(depthScale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x22EC3872),
                            Color(0x05EC3872),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner pulsing halo
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .scale(scale)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFEC3872).copy(alpha = pulseAlpha * 0.35f),
                                Color(0xFFEC3872).copy(alpha = pulseAlpha * 0.08f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Solid soft core
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEC3872).copy(alpha = 0.15f))
                )
            }
        }

        // Inner Countdown Indicator (Overlaid exactly inside circle)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isCountdownFinished) {
                Text(
                    text = "$countdownRemaining",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Breathe",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDCC1FF),
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFF72EDCB),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Adaptive details: Attempts counter
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$attemptsLast24h",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "attempts to open $appLabel within the last 24 hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0B3BC),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last use: $lastUseText",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A8F9E)
            )
        }

        // Cancel / Open Buttons
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cancel action (Prominent Mindfulness Button - Beautiful Brand Blue)
            Button(
                onClick = {
                    activity.lifecycleScope.launch {
                        // Register intervention as CANCELLED (Saved minutes!)
                        repository.logIntervention(packageName, wasCancelled = true)

                        // Safely redirect straight to back Home Launcher screen
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                        activity.finish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("cancel_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEC3872),
                    contentColor = Color.White
                ),
                shape = CircleShape
            ) {
                Text(
                    text = "I don't want to open $appLabel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // Open app action (Subtle outline style, only available after countdown!)
            OutlinedButton(
                onClick = {
                    if (isCountdownFinished) {
                        activity.lifecycleScope.launch {
                            // Register bypass BEFORE starting activity so we don't trigger again instantly!
                            repository.bypassAppForDuration(packageName, 15)

                            // Save non-cancelled log
                            repository.logIntervention(packageName, wasCancelled = false)

                            // Launch the package intent
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                            activity.finish()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("open_button"),
                enabled = isCountdownFinished,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.25f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isCountdownFinished) Color(0xFFBC6BBF) else Color(0x33BC6BBF)
                ),
                shape = CircleShape
            ) {
                Text(
                    text = if (isCountdownFinished) "Continue to $appLabel" else "Breathe for a sec...",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
