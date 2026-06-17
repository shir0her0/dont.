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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
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
    var mindfulnessText by remember { mutableStateOf("Its time to take a deep breathe.") }
    var delayDurationSeconds by remember { mutableIntStateOf(5) }
    var appLabel by remember { mutableStateOf(packageName) }
    var changedMindTimesToday by remember { mutableIntStateOf(0) }
    var lastUseText by remember { mutableStateOf("Never before") }

    // Countdown state
    var countdownRemaining by remember { mutableIntStateOf(5) }
    var isCountdownFinished by remember { mutableStateOf(false) }

    // Loading config from database
    LaunchedEffect(packageName) {
        // Load Settings
        val currentSettings = repository.settings.first()
        var text = currentSettings.mindfulnessText
        if (text == "Take a breath. Do you really want to open this app?") {
            text = "Its time to take a deep breathe."
            repository.saveSettings(currentSettings.copy(mindfulnessText = text))
        }
        mindfulnessText = text
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

        // Get count of interventions from start of today
        val logs = repository.allLogs.firstOrNull() ?: emptyList()
        val appLogs = logs.filter { it.packageName == packageName }

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val todayStartMs = calendar.timeInMillis

        changedMindTimesToday = appLogs.filter { it.timestamp >= todayStartMs }.count { it.wasCancelled }

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

    // Breath Progress Animatable to run exactly ONCE over the countdown duration
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(delayDurationSeconds) {
        val halfDurationMs = (delayDurationSeconds * 1000L / 2).coerceAtLeast(100L).toInt()
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = halfDurationMs, easing = EaseInOutSine)
        )
        animProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = halfDurationMs, easing = EaseInOutSine)
        )
    }

    val scale = 1.0f + 0.45f * animProgress.value
    val pulseAlpha = 0.25f + 0.45f * animProgress.value
    val breathingOffset = 15f + 60f * animProgress.value
    val rotationAngle = 60f * animProgress.value

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
        // App header guidance (Brought lower down from the top)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.ic_logo_transparent),
                contentDescription = "don't Logo",
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 12.dp)
            )
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

        // Overlapping breathing circles animation (Rosette flower resembling the premium Apple Breathe layout, slightly larger)
        Box(
            modifier = Modifier
                .size(350.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            val numCircles = 6
            for (i in 0 until numCircles) {
                // Compute the radian angle offset for each of the 6 petals
                val angleRad = ((i * (360f / numCircles)) + rotationAngle) * (PI / 180f)
                val dx = (breathingOffset * cos(angleRad)).toFloat()
                val dy = (breathingOffset * sin(angleRad)).toFloat()

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .offset(x = dx.dp, y = dy.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF528E).copy(alpha = 0.45f), // Glowing bright hot pink
                                    Color(0xFF860462).copy(alpha = 0.12f)  // Soft mysterious deep magenta
                                )
                            )
                        )
                )
            }
        }

        // Adaptive details: Attempts counter (Made slightly smaller as requested)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 185.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You changed your mind",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A8F9E),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$changedMindTimesToday times today.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Last use: $lastUseText",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6C717C)
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
                    text = if (isCountdownFinished) "Continue to $appLabel" else "Breathe for a sec ($countdownRemaining s)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
