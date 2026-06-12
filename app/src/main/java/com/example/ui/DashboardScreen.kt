package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppConfig
import com.example.data.Settings
import com.example.service.TakeASecAccessibilityService
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("dashboard") }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showPermissionHelper by remember { mutableStateOf(false) }

    // State bindings
    val uiState by viewModel.uiState.collectAsState()

    // Permissions check
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isUsagePermissionEnabled by remember { mutableStateOf(false) }

    // Periodically refresh permissions state
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = isAccessibilityServiceEnabled(context, TakeASecAccessibilityService::class.java)
            isUsagePermissionEnabled = isUsageStatsPermissionGranted(context)
            delay(2000)
        }
    }

    val parentBackgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFB10582), // top pink/magenta
            Color(0xFF4C0B74), // purple
            Color(0xFF1C0130), // deep violet
            Color(0xFF05000C)  // pitch black at the bottom
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(parentBackgroundGradient)
    ) {
        @Composable
        fun NavigationHeader(title: String, showSettingsBtn: Boolean, onBack: () -> Unit = {}) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (showSettingsBtn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_logo_transparent),
                            contentDescription = "Take a Sec Logo",
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick = { currentScreen = "settings" },
                        modifier = Modifier.testTag("settings_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Mindfulness Customization Settings",
                            tint = Color.White
                        )
                    }
                } else {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        // Animated Screen Transitions
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == "settings") {
                    slideInHorizontally { width -> width / 3 } + fadeIn() with
                            slideOutHorizontally { width -> -width / 3 } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width / 3 } + fadeIn() with
                            slideOutHorizontally { width -> width / 3 } + fadeOut()
                }.using(SizeTransform(clip = false))
            },
            label = "screen_trans"
        ) { screen ->
            when (screen) {
                "dashboard" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        NavigationHeader("Take a Sec", showSettingsBtn = true)
                        
                        DashboardScreen(
                            uiState = uiState,
                            isAccessibilityActive = isAccessibilityEnabled,
                            isUsageActive = isUsagePermissionEnabled,
                            onRemoveApp = { viewModel.removeMonitoredApp(it) },
                            onShowPermissionHelp = { showPermissionHelper = true },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
                "settings" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        NavigationHeader("Customization Settings", showSettingsBtn = false, onBack = {
                            currentScreen = "dashboard"
                        })
                        
                        SettingsScreen(
                            settings = uiState.settings,
                            onMindfulnessTextChange = { viewModel.updateMindfulnessText(it) },
                            onDelayDurationChange = { viewModel.updateDelayDuration(it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Floating Action Button
        if (currentScreen == "dashboard") {
            FloatingActionButton(
                onClick = {
                    viewModel.scanInstalledLauncherApps(context)
                    showAddAppDialog = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .testTag("add_app_fab")
                    .border(1.dp, Color(0xFFEC3872), RoundedCornerShape(18.dp)),
                containerColor = Color(0xFFEC3872),
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Monitor an App"
                )
            }
        }

        // Add App Screen / Selector Overlay
        if (showAddAppDialog) {
            val installedApps by viewModel.installedApps.collectAsState()
            val isScanning by viewModel.isScanningApps.collectAsState()

            AppSelectionOverlay(
                installedApps = installedApps,
                monitoredApps = uiState.monitoredAppStats.map { it.packageName },
                isScanning = isScanning,
                onDismiss = { showAddAppDialog = false },
                onToggleApp = { packageName, appName, isSelected ->
                    viewModel.toggleAppMonitoring(packageName, appName, isSelected)
                }
            )
        }

        if (showPermissionHelper) {
            PermissionHelperOverlay(
                onDismiss = { showPermissionHelper = false }
            )
        }
    }
}

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    isAccessibilityActive: Boolean,
    isUsageActive: Boolean,
    onRemoveApp: (String) -> Unit,
    onShowPermissionHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Permissions setup card if not active
        if (!isAccessibilityActive || !isUsageActive) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1F2026).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF33353D), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "System Permissions Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enable the service below to intercept monitored social apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0B3BC)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Accessibility service switcher bar
                    PermissionRow(
                        label = "Accessibility Service",
                        isActive = isAccessibilityActive,
                        onClick = {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Usage Statistics helper
                    PermissionRow(
                        label = "App Usage Statistics",
                        isActive = isUsageActive,
                        onClick = {
                            try {
                                val intent = Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    TextButton(
                        onClick = onShowPermissionHelp,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDCC1FF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Can't find Take a Sec in the menu?",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Metrics Summary Module (Frosted Glass Effect)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF141021).copy(alpha = 0.45f)) // macOS liquid glass style semi-transparent dark container
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp)) // Crisp translucent border
                    .padding(24.dp)
            ) {
                // Key Hero statistic: Minutes saved
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${uiState.estimatedMinutesSaved}m",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Estimated Time Saved Today",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Minutes Saved",
                        tint = Color(0xFF64ECD3), // Pop color mint matching the theme beautifully
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Multi column counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${uiState.interventionsToday}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Interventions Today",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "${uiState.interventionsThisWeek}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Interventions Week",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.mostInterruptedAppName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Most Interrupted",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Monitored Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Monitored Apps Grid
        if (uiState.monitoredAppStats.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No apps monitored yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the + button to select apps to monitor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0B3BC),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Group our stats in pairs of 2 for a neat two-column layout
            val pairs = uiState.monitoredAppStats.chunked(2)
            pairs.forEach { pair ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { stats ->
                            Box(modifier = Modifier.weight(1f)) {
                                AppMonitoredCard(
                                    stats = stats,
                                    onDelete = { onRemoveApp(stats.packageName) }
                                )
                            }
                        }
                        // Handle odd count in the last row to maintain spacing
                        if (pair.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppMonitoredCard(
    stats: AppStats,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    val appIconDrawable = remember(stats.packageName) {
        try {
            pm.getApplicationIcon(stats.packageName)
        } catch (e: Exception) {
            null
        }
    }

    val dominantColor = remember(appIconDrawable) {
        if (appIconDrawable != null) {
            getDominantColorFromDrawable(appIconDrawable)
        } else {
            // Beautiful curated fallback colors
            val colors = listOf(
                Color(0xFFEC3872), // Hot Magenta pink
                Color(0xFF2979FF), // Cool Neon Blue
                Color(0xFF00E676), // Refreshing Bright Green
                Color(0xFFFFD600), // Sunshine Gold
                Color(0xFFAA00FF), // Violet Indigo
                Color(0xFFFF6D00)  // Deep Orange
            )
            val index = Math.abs(stats.packageName.hashCode()) % colors.size
            colors[index]
        }
    }

    val dynamicBrush = remember(dominantColor) {
        val darkEnd = Color(
            red = (dominantColor.red * 0.40f),
            green = (dominantColor.green * 0.40f),
            blue = (dominantColor.blue * 0.40f),
            alpha = 1f
        )
        Brush.linearGradient(
            colors = listOf(
                dominantColor,
                darkEnd
            )
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .testTag("monitored_card_${stats.packageName}")
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dynamicBrush)
                .padding(12.dp)
        ) {
            // Header Row: App Icon & App Title & Small Delete Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (appIconDrawable != null) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.ImageView(ctx).apply {
                                    setImageDrawable(appIconDrawable)
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = stats.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("delete_btn_${stats.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove app monitoring",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Stats row at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "${stats.totalInterventions} ints",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${stats.minutesSaved}m saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64ECD3), // Dynamic mint pop color matching MacOS premium style
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Dominant color helper function to draw the app's drawable onto a scaled bitmap and sample dynamic dominant pixels
fun getDominantColorFromDrawable(drawable: Drawable): Color {
    try {
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
        
        val scaledWidth = 5
        val scaledHeight = 5
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false)
        
        var totalRed = 0L
        var totalGreen = 0L
        var totalBlue = 0L
        var pixelCount = 0
        
        for (x in 0 until scaledWidth) {
            for (y in 0 until scaledHeight) {
                val color = scaledBitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(color)
                if (alpha > 120) { // filter out highly translucent background fragments
                    totalRed += android.graphics.Color.red(color)
                    totalGreen += android.graphics.Color.green(color)
                    totalBlue += android.graphics.Color.blue(color)
                    pixelCount++
                }
            }
        }
        
        if (pixelCount > 0) {
            val avgRed = (totalRed / pixelCount).toInt()
            val avgGreen = (totalGreen / pixelCount).toInt()
            val avgBlue = (totalBlue / pixelCount).toInt()
            // Boost low-light or washed-out pixels slightly for dynamic card backgrounds
            return Color(avgRed.coerceIn(40, 220), avgGreen.coerceIn(40, 220), avgBlue.coerceIn(40, 220))
        }
    } catch (e: Exception) {
         e.printStackTrace()
    }
    return Color(0xFFEC3872) // Fallback to hot magenta
}

@Composable
fun PermissionRow(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0x1969F0AE) else Color(0x19FF8A80))
            .border(1.dp, if (isActive) Color(0x5569F0AE) else Color(0x55FF8A80), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isActive) "Enabled" else "Tap to Enable",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isActive) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SettingsScreen(
    settings: Settings,
    onMindfulnessTextChange: (String) -> Unit,
    onDelayDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf(settings.mindfulnessText) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Mindfulness Greeting Input Text
        item {
            Column {
                Text(
                    text = "Mindfulness Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter the custom phrase displayed in fullscreen during interventions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B3BC)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        onMindfulnessTextChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .testTag("mindfulness_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEC3872),
                        unfocusedBorderColor = Color(0xFF4C4D56),
                        unfocusedContainerColor = Color(0xFF14151B),
                        focusedContainerColor = Color(0xFF14151B)
                    )
                )
            }
        }

        // Delay timer control
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Breathing Delay Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${settings.delayDurationSeconds} seconds",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEC3872)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Specifies how long the countdown and breathing cycle persists before letting you open apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B3BC)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = settings.delayDurationSeconds.toFloat(),
                    onValueChange = { onDelayDurationChange(it.toInt()) },
                    valueRange = 1f..30f,
                    steps = 28,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFEC3872),
                        activeTrackColor = Color(0xFFEC3872),
                        inactiveTrackColor = Color(0xFF33353D)
                    ),
                    modifier = Modifier.testTag("delay_slider")
                )
            }
        }

        // Author's Note card (Standalone section)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF141021).copy(alpha = 0.45f)) // Styled to match overall theme
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "Author's Note",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Hey, I'm Shireesh.\n\nAfter accidentally opening Instagram for the 83rd time in a day, I decided my phone needed a speed bump.\n\nThat's what Take a Sec is.\n\nIt won't judge you, lock you out, or lecture you. It just asks you to take a breath before diving into the infinite scroll. What you do after that is between you and your algorithm.\n\nThanks for giving Take a Sec a try.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = Color(0xFFB0B3BC)
                )
            }
        }
    }
}

@Composable
fun AppSelectionOverlay(
    installedApps: List<AppConfig>,
    monitoredApps: List<String>,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onToggleApp: (String, String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isEmpty()) {
            installedApps
        } else {
            installedApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .align(Alignment.Center)
                .border(1.dp, Color(0xFF33353D), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14151B)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select App to Monitor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay",
                            tint = Color(0xFFB0B3BC)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Query search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search system apps...", color = Color(0xFF8A8F9E)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEC3872),
                        unfocusedBorderColor = Color(0xFF33353D),
                        unfocusedContainerColor = Color(0xFF1F2026),
                        focusedContainerColor = Color(0xFF1F2026)
                    ),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF8A8F9E)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFEC3872))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Scanning device applications...", color = Color(0xFFB0B3BC))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
                            val isCurrentlyMonitored = monitoredApps.contains(app.packageName)
                            var localSelected by remember(isCurrentlyMonitored) { mutableStateOf(isCurrentlyMonitored) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (localSelected) Color(0x33EC3872) else Color.Transparent)
                                    .clickable {
                                        val newState = !localSelected
                                        localSelected = newState
                                        onToggleApp(app.packageName, app.appName, newState)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF8A8F9E)
                                    )
                                }

                                Checkbox(
                                    checked = localSelected,
                                    onCheckedChange = { newState ->
                                        localSelected = newState
                                        onToggleApp(app.packageName, app.appName, newState)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFFEC3872),
                                        checkmarkColor = Color.White
                                    )
                                )
                            }
                        }
                        
                        if (filteredApps.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No matching apps found.",
                                        color = Color(0xFF74777F),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility Permission Evaluators
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
    for (enabledService in enabledServices) {
        val serviceInfo = enabledService.resolveInfo.serviceInfo
        if (serviceInfo.packageName == context.packageName && serviceInfo.name == serviceClass.name) {
            return true
        }
    }
    return false
}

fun isUsageStatsPermissionGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.noteOpNoThrow(
        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

@Composable
fun PermissionHelperOverlay(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = false) { }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .align(Alignment.Center)
                .border(1.dp, Color(0xFF33353D), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14151B)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Permission Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close overlay",
                            tint = Color(0xFFB0B3BC)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x19FF8A80)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF8A80)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Are settings grayed out or missing?",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF8A80)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Android 13+ restricts sensitive permissions for sideloaded/developer apps by default, preventing them from showing up in system lists until they are manually unlocked:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFEAEE),
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "1. Open your Android device Settings.\n" +
                                           "2. Navigate to 'Apps' -> 'See all apps'.\n" +
                                           "3. Select 'Take a Sec' from the list.\n" +
                                           "4. Tap the three-dot menu icon in the top-right corner.\n" +
                                           "5. Select 'Allow restricted settings' and confirm your PIN.\n" +
                                           "6. Return to this app and tap the toggle buttons above to grant permissions successfully!",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFFE1E6),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Standard Setup Details",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF33353D), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "💡 App Usage Statistics",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enables Take a Sec to check when social apps are opened in the foreground. Tap 'App Usage Statistics' above, find 'Take a Sec' in the system list, and enable 'Permit usage access'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0B3BC),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF33353D), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "💡 Accessibility Service",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Required to instantaneously intercept monitored social apps before they display fully. Tap 'Accessibility Service' above, look for 'Downloaded apps' or 'Installed services', tap 'Take a Sec', and switch it 'ON'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0B3BC),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC3872))
                ) {
                    Text("Got it, search settings")
                }
            }
        }
    }
}
