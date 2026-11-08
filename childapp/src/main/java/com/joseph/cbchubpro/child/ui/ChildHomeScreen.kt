package com.joseph.cbchubpro.child.ui

import android.app.AppOpsManager
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.joseph.cbchubpro.child.AppListFetcher
import com.joseph.cbchubpro.child.ScreenTimePolicy
import com.joseph.cbchubpro.child.data.local.ChildDataRepository
import com.joseph.cbchubpro.child.data.model.*
import com.joseph.cbchubpro.child.receiver.AppDeviceAdminReceiver
import com.joseph.cbchubpro.child.service.AppBlockerService
import com.joseph.cbchubpro.child.service.LocationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

class ChildHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repository = ChildDataRepository(application)
    private val appListFetcher = AppListFetcher(application)

    private val _childProfile = MutableStateFlow<Map<String, Any>>(emptyMap())
    val childProfile = _childProfile.asStateFlow()

    private val _lessons = MutableStateFlow<List<LessonContent>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _quizzes = MutableStateFlow<List<Quiz>>(emptyList())
    val quizzes = _quizzes.asStateFlow()

    private val _teachers = MutableStateFlow<List<ProfessionalProfile>>(emptyList())
    val teachers = _teachers.asStateFlow()

    private val _submissions = MutableStateFlow<List<AssignmentSubmission>>(emptyList())
    val submissions = _submissions.asStateFlow()

    private val _isUsageGranted = MutableStateFlow(false)
    val isUsageGranted = _isUsageGranted.asStateFlow()

    private val _isAccessibility = MutableStateFlow(false)
    val isAccessibility = _isAccessibility.asStateFlow()

    private val _badges = MutableStateFlow<List<StudentBadge>>(emptyList())
    val badges = _badges.asStateFlow()

    private val _behaviorLogs = MutableStateFlow<List<BehaviorLog>>(emptyList())
    val behaviorLogs = _behaviorLogs.asStateFlow()

    private val _pointsBalance = MutableStateFlow(0L)
    val pointsBalance = _pointsBalance.asStateFlow()

    private val _screenTimeUsedMillis = MutableStateFlow(0L)
    val screenTimeUsedMillis = _screenTimeUsedMillis.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isUnpaired = MutableStateFlow(false)
    val isUnpaired = _isUnpaired.asStateFlow()

    private val _showPermissionsSetup = MutableStateFlow(false)
    val showPermissionsSetup = _showPermissionsSetup.asStateFlow()

    private val _isLocationGrantedState = MutableStateFlow(false)
    val isLocationGrantedState = _isLocationGrantedState.asStateFlow()

    private val _isDeviceAdminActiveState = MutableStateFlow(false)
    val isDeviceAdminActiveState = _isDeviceAdminActiveState.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(true)
    val isGpsEnabled = _isGpsEnabled.asStateFlow()

    init {
        ensureAuthenticatedAndFetch()
        startDataReporters()
    }

    private fun ensureAuthenticatedAndFetch() {
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
                _statusMessage.value = "Welcome! Safety monitoring is active."
                startDataListeners()
                checkAndStartServices()
            } catch (e: Exception) {
                _statusMessage.value = "Auth error: ${e.message}"
            }
        }
    }

    private fun startDataListeners() {
        val uid = auth.currentUser?.uid ?: return
        
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data ?: emptyMap()
                _childProfile.value = data
                _pointsBalance.value = snapshot.getLong("pointsBalance") ?: 0L
                
                if (snapshot.getBoolean("paired") == false) {
                    _isUnpaired.value = true
                    logoutChildApp()
                    return@addSnapshotListener
                }

                if (data.containsKey("requestPermissionSetup")) {
                    _showPermissionsSetup.value = true
                    db.collection("users").document(uid).update("requestPermissionSetup", FieldValue.delete())
                }

                val grade = data["gradeLevel"]?.toString() ?: ""
                if (grade.isNotEmpty()) fetchEducationContent(grade)
                else _isLoading.value = false

                if (data.containsKey("tvSyncTrigger")) {
                    viewModelScope.launch {
                        try { appListFetcher.fetchAndUploadAppList() } catch (e: Exception) { Log.e("ChildHomeVM", "TV Sync failed", e) }
                    }
                    db.collection("users").document(uid).update(
                        mapOf(
                            "lastTvSync" to FieldValue.serverTimestamp(),
                            "tvSyncTrigger" to FieldValue.delete()
                        )
                    )
                }
            } else if (snapshot != null && !snapshot.exists() && repository.isPaired()) {
                _isUnpaired.value = true
                logoutChildApp()
            }
        }

        db.collection("users")
            .whereIn("role", listOf("professional", "teacher", "private_teacher"))
            .addSnapshotListener { snapshot, _ ->
                _teachers.value = snapshot?.documents?.mapNotNull { it.toObject(ProfessionalProfile::class.java)?.copy(uid = it.id) } ?: emptyList()
            }

        db.collection("student_badges").whereEqualTo("studentId", uid).addSnapshotListener { snapshot, _ ->
            _badges.value = snapshot?.documents?.mapNotNull { it.toObject(StudentBadge::class.java) } ?: emptyList()
        }
        db.collection("behavior_logs").whereEqualTo("studentId", uid).addSnapshotListener { snapshot, _ ->
            _behaviorLogs.value = snapshot?.documents?.mapNotNull { it.toObject(BehaviorLog::class.java) } ?: emptyList()
        }
        db.collection("submissions")
            .whereEqualTo("studentId", uid)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                _submissions.value = snapshot?.documents?.mapNotNull { it.toObject(AssignmentSubmission::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    private fun startDataReporters() {
        viewModelScope.launch {
            while (true) {
                reportPermissionsStatus()
                refreshScreenTimeUsage()
                delay(60000)
            }
        }
    }

    private fun refreshScreenTimeUsage() {
        if (_isUsageGranted.value) {
            _screenTimeUsedMillis.value = ScreenTimePolicy.todayUsageMillis(getApplication())
        }
    }

    fun reportPermissionsStatus() {
        val uid = auth.currentUser?.uid ?: return
        val context = getApplication<Application>()
        
        val adminActive = isDeviceAdminActive(context)
        val locationGranted = isLocationGranted(context)
        val usageGranted = isUsageStatsGranted(context)
        val accessibilityEnabled = isAccessibilityServiceEnabled(context)
        val gpsOn = isGpsEnabled(context)
        
        _isDeviceAdminActiveState.value = adminActive
        _isLocationGrantedState.value = locationGranted
        _isUsageGranted.value = usageGranted
        _isAccessibility.value = accessibilityEnabled
        _isGpsEnabled.value = gpsOn

        val status = mapOf(
            "deviceAdmin" to adminActive,
            "usageStats" to usageGranted,
            "accessibility" to accessibilityEnabled,
            "location" to locationGranted,
            "gpsEnabled" to gpsOn,
            "overlay" to Settings.canDrawOverlays(context),
            "batteryOptimization" to isBatteryOptimizationIgnored(context),
            "lastChecked" to FieldValue.serverTimestamp()
        )
        
        db.collection("users").document(uid)
            .set(mapOf("permissionStatus" to status), SetOptions.merge())

        checkAndStartServices()
    }

    fun checkAndStartServices() {
        val context = getApplication<Application>()
        if (auth.currentUser == null) return

        viewModelScope.launch {
            try { appListFetcher.fetchAndUploadAppList() } catch (e: Exception) { Log.e("ChildHomeVM", "App sync failed", e) }
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation) {
            val intent = Intent(context, LocationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("ChildHomeVM", "Failed to start location service", e)
            }
        }
    }

    private fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, AppDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    private fun isUsageStatsGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
                return true
            }
        }
        return false
    }

    private fun isLocationGranted(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return fineLocation && backgroundLocation
    }

    private fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun fetchEducationContent(grade: String) {
        db.collection("lessons")
            .whereEqualTo("gradeLevel", grade)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                _lessons.value = snapshot?.documents?.mapNotNull { it.toObject(LessonContent::class.java)?.copy(id = it.id) } ?: emptyList()
                _isLoading.value = false
            }

        db.collection("quizzes")
            .whereEqualTo("gradeLevel", grade)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                _quizzes.value = snapshot?.documents?.mapNotNull { it.toObject(Quiz::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    fun logoutWithPassword(password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val savedPassword = repository.getParentPassword() ?: "1234"
        if (password == savedPassword) {
            logoutChildApp()
            onSuccess()
        } else {
            onError("Incorrect parent password")
        }
    }

    fun logoutChildApp() {
        viewModelScope.launch {
            repository.clearData()
            auth.signOut()
            _isUnpaired.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildHomeScreen(
    viewModel: ChildHomeViewModel = viewModel(),
    onNavigateToLesson: (String) -> Unit,
    onNavigateToQuiz: (String) -> Unit,
    onNavigateToTeacher: (String) -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onLogout: () -> Unit
) {
    val profile by viewModel.childProfile.collectAsState()
    val lessons by viewModel.lessons.collectAsState()
    val quizzes by viewModel.quizzes.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val behaviors by viewModel.behaviorLogs.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val points by viewModel.pointsBalance.collectAsState()
    val screenTimeUsedMillis by viewModel.screenTimeUsedMillis.collectAsState()
    val isUsageGranted by viewModel.isUsageGranted.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isUnpaired by viewModel.isUnpaired.collectAsState()
    val showPermissionsSetup by viewModel.showPermissionsSetup.collectAsState()
    val context = LocalContext.current
    val isTvDevice = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isUnpaired) {
        if (isUnpaired) onLogout()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reportPermissionsStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showPermissionsSetup) {
        PermissionSetupDialog(
            onDismiss = { /* Should not dismiss until addressed usually */ },
            viewModel = viewModel
        )
    }

    if (showLogoutDialog) {
        ParentPasswordDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = { password ->
                viewModel.logoutWithPassword(password, { onLogout() }, { /* Handle error */ })
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CBC Hub Child", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* Help Info */ }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF8F9FA)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ChildHeaderCard(
                        name = profile["name"]?.toString() ?: "Student",
                        grade = profile["gradeLevel"]?.toString() ?: "N/A",
                        points = points,
                        onPointsClick = onNavigateToLeaderboard
                    )
                }

                item {
                    ChildScreenTimeCard(
                        title = if (isTvDevice) "My TV time" else "My screen time",
                        usedMillis = screenTimeUsedMillis,
                        usageAccessGranted = isUsageGranted,
                        dailyLimitMinutes = if (isTvDevice) {
                            (profile["tvDailyTimeLimit"] as? Number)?.toLong() ?: ScreenTimePolicy.DEFAULT_DAILY_LIMIT_MINUTES
                        } else {
                            (profile["dailyTimeLimit"] as? Number)?.toLong() ?: ScreenTimePolicy.DEFAULT_DAILY_LIMIT_MINUTES
                        },
                        schedule = profile[if (isTvDevice) "tvScreenTimeSchedule" else "screenTimeSchedule"] as? Map<*, *>
                    )
                }

                if (statusMessage != null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(12.dp))
                                Text(statusMessage!!, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item { PermissionStatusBar(viewModel) }

                if (teachers.isNotEmpty()) {
                    item {
                        SectionHeader("My Teachers")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(teachers) { teacher ->
                                TeacherSmallCard(teacher) {
                                    onNavigateToTeacher(teacher.uid)
                                }
                            }
                        }
                    }
                }

                if (badges.isNotEmpty()) {
                    item {
                        SectionHeader("Recent Badges")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(badges) { badge ->
                                BadgeCard(badge)
                            }
                        }
                    }
                }

                item { SectionHeader("Lessons for You") }
                if (lessons.isEmpty()) {
                    item { EmptyState("No lessons available yet.") }
                } else {
                    items(lessons) { lesson ->
                        LessonItem(lesson) { onNavigateToLesson(lesson.id) }
                    }
                }

                item { SectionHeader("Quizzes & Assessments") }
                if (quizzes.isEmpty()) {
                    item { EmptyState("No quizzes assigned.") }
                } else {
                    items(quizzes) { quiz ->
                        QuizItem(quiz) { onNavigateToQuiz(quiz.id) }
                    }
                }

                if (submissions.isNotEmpty()) {
                    item { SectionHeader("My Submissions") }
                    items(submissions) { submission ->
                        SubmissionItem(submission)
                    }
                }

                if (behaviors.isNotEmpty()) {
                    item { SectionHeader("Behavior Updates") }
                    items(behaviors) { log ->
                        BehaviorItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildScreenTimeCard(title: String, usedMillis: Long, usageAccessGranted: Boolean, dailyLimitMinutes: Long, schedule: Map<*, *>?) {
    val usedMinutes = usedMillis / 60_000L
    val remainingMinutes = (dailyLimitMinutes - usedMinutes).coerceAtLeast(0L)
    val scheduleActive = ScreenTimePolicy.isScheduleActive(schedule)
    val isUnlimited = dailyLimitMinutes <= 0L
    val progress = if (isUnlimited) 0f else (usedMinutes.toFloat() / dailyLimitMinutes).coerceIn(0f, 1f)
    val startMinute = (schedule?.get("startMinute") as? Number)?.toInt() ?: 22 * 60
    val endMinute = (schedule?.get("endMinute") as? Number)?.toInt() ?: 7 * 60

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                when {
                    scheduleActive -> "Scheduled downtime is active"
                    isUnlimited -> "No daily time limit set"
                    else -> "${formatScreenTime(remainingMinutes)} left today"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (scheduleActive || (!isUnlimited && remainingMinutes == 0L)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (!isUnlimited) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("${formatScreenTime(usedMinutes)} used of ${formatScreenTime(dailyLimitMinutes)}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("${formatScreenTime(usedMinutes)} used today", style = MaterialTheme.typography.bodySmall)
            }
            if (schedule?.get("enabled") == true) {
                Text("Downtime: ${formatScreenClockTime(startMinute)}–${formatScreenClockTime(endMinute)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (!usageAccessGranted) {
                Text("Usage updates when Usage access is enabled.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

private fun formatScreenClockTime(totalMinutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", totalMinutes / 60, totalMinutes % 60)

private fun formatScreenTime(minutes: Long): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
}

@Composable
fun ChildHeaderCard(name: String, grade: String, points: Long, onPointsClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onPointsClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Grade: $grade", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Total Points", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                Text(points.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                   Text("Leaderboard", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                   Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun PermissionStatusBar(viewModel: ChildHomeViewModel) {
    val admin by viewModel.isDeviceAdminActiveState.collectAsState()
    val location by viewModel.isLocationGrantedState.collectAsState()
    val usage by viewModel.isUsageGranted.collectAsState()
    val accessibility by viewModel.isAccessibility.collectAsState()
    val gps by viewModel.isGpsEnabled.collectAsState()

    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("System Status", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PermissionIcon(Icons.Default.AdminPanelSettings, "Admin", admin)
                PermissionIcon(Icons.Default.LocationOn, "Location", location)
                PermissionIcon(Icons.Default.BarChart, "Usage", usage)
                PermissionIcon(Icons.Default.Accessibility, "Access", accessibility)
                PermissionIcon(Icons.Default.GpsFixed, "GPS", gps)
            }
        }
    }
}

@Composable
fun PermissionIcon(icon: ImageVector, label: String, isGranted: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(24.dp)
        )
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun TeacherSmallCard(teacher: ProfessionalProfile, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = teacher.profileImageUrl,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Text(teacher.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(teacher.role, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun BadgeCard(badge: StudentBadge) {
    Card(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(badge.badgeType, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(badge.reason, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LessonItem(lesson: LessonContent, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (lesson.type) {
                LessonContentType.VIDEO_URL -> Icons.Default.PlayCircle
                LessonContentType.PDF -> Icons.Default.PictureAsPdf
                else -> Icons.Default.Description
            }
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, fontWeight = FontWeight.Bold)
                Text("${lesson.subject} • ${lesson.topic}", fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.LightGray)
        }
    }
}

@Composable
fun QuizItem(quiz: Quiz, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(quiz.title, fontWeight = FontWeight.Bold)
                Text("${quiz.questions.size} Questions", fontSize = 12.sp)
            }
            Button(onClick = onClick, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Start", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SubmissionItem(submission: AssignmentSubmission) {
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (submission.graded) Icons.Default.CheckCircle else Icons.Default.History,
                contentDescription = null,
                tint = if (submission.graded) Color(0xFF4CAF50) else Color.Gray
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Submission for ${submission.assignmentId.take(8)}...", fontWeight = FontWeight.SemiBold)
                Text("Submitted: ${sdf.format(submission.submittedAt.toDate())}", fontSize = 11.sp, color = Color.Gray)
            }
            if (submission.score != null) {
                Text("${submission.score}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun BehaviorItem(log: BehaviorLog) {
    val sdf = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val color = if (log.type == "Positive") Color(0xFF4CAF50) else Color(0xFFF44336)
        
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(log.note, fontSize = 14.sp)
            Text("Teacher Feedback • ${sdf.format(log.timestamp.toDate())}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun EmptyState(message: String) {
    Text(message, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun ParentPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent Control") },
        text = {
            Column {
                Text("Enter parent password to continue.")
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password); onDismiss() }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionSetupDialog(onDismiss: () -> Unit, viewModel: ChildHomeViewModel) {
    val context = LocalContext.current
    val admin by viewModel.isDeviceAdminActiveState.collectAsState()
    val accessibility by viewModel.isAccessibility.collectAsState()
    val usage by viewModel.isUsageGranted.collectAsState()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Safety Setup Required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Your parent has requested a security check. Please enable the following to keep your device safe and synchronized.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                Spacer(Modifier.height(16.dp))

                PermissionActionItem(
                    "Device Admin", 
                    "Prevents unauthorized uninstallation.", 
                    admin
                ) {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, AppDeviceAdminReceiver::class.java))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for parental control.")
                    }
                    context.startActivity(intent)
                }

                PermissionActionItem(
                    "Accessibility Service", 
                    "Helps in blocking restricted apps.", 
                    accessibility
                ) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }

                PermissionActionItem(
                    "Usage Access", 
                    "Tracks app usage for safety reports.", 
                    usage
                ) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }

                PermissionActionItem(
                    "Overlay Permission", 
                    "Allows showing block screens.", 
                    Settings.canDrawOverlays(context)
                ) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                    context.startActivity(intent)
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { viewModel.reportPermissionsStatus(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I've finished the setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PermissionActionItem(title: String, subtitle: String, isDone: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDone) Color(0xFFE8F5E9) else Color(0xFFF5F5F5))
            .clickable(enabled = !isDone) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        if (isDone) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF4CAF50))
        } else {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
