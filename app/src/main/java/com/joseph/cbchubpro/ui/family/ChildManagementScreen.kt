package com.joseph.cbchubpro.ui.family

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildManagementScreen(
    childId: String, 
    onBackClicked: () -> Unit,
    onLiveLocationClicked: (String) -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    var childData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(childId) {
        db.collection("users").document(childId).addSnapshotListener { snapshot, _ ->
            childData = snapshot?.data
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(childData?.get("name")?.toString() ?: "Manage Child") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                // Enhanced Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(childData?.get("name")?.toString() ?: "Unknown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(childData?.get("email")?.toString() ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val pointsBalance = (childData?.get("pointsBalance") as? Long) ?: 0L
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("Points: $pointsBalance", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                val rewardPoints = (childData?.get("rewardPoints") as? Long) ?: 0L
                                Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                    Text("Rewards: $rewardPoints", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tabs
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("Apps", "Web", "Security", "Location", "Time", "TV", "Tasks", "Interactions", "Family")
                
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                    when (selectedTab) {
                        0 -> AppManagementSection(childId, childData)
                        1 -> WebManagementSection(childId, childData)
                        2 -> SecuritySection(childId, childData)
                        3 -> LocationSection(childId, childData, onLiveLocationClicked)
                        4 -> ScreenTimeSection(childId, childData)
                        5 -> AndroidTvSection(childId, childData)
                        6 -> TaskSection(childId, childData)
                        7 -> InteractionsSection(childId, childData)
                        8 -> FamilySection(childId, childData)
                    }
                }
            }
        }
    }
}

@Composable
fun FamilySection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val familyId = childData?.get("familyId")?.toString() ?: ""
    val parentPassword = childData?.get("parentPassword")?.toString() ?: "Not Set"
    
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var familyMembers by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(familyId) {
        if (familyId.isNotEmpty()) {
            db.collection("families").document(familyId).collection("members")
                .addSnapshotListener { snapshots, _ ->
                    val memberIds = snapshots?.map { it.id } ?: emptyList()
                    if (memberIds.isNotEmpty()) {
                        db.collection("users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), memberIds)
                            .get().addOnSuccessListener { userDocs ->
                                familyMembers = userDocs.map { it.data + ("uid" to it.id) }
                            }
                    }
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Family & Access Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Manage who can monitor this child and view the device exit password.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))
        
        // Exit Password Guidance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Child App Exit Password", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "GUIDE: Use this password on the child's device to exit the CBC Hub app or log out. It prevents your child from bypassing parental controls while allowing you full device access when needed.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Current Exit Password", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(parentPassword, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { showChangePasswordDialog = true }) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Update")
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text("Authorized Guardians", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("All members of the Family Circle can view this child's activities.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        
        if (familyMembers.isEmpty()) {
            Text("Loading guardians...", color = Color.Gray, fontSize = 12.sp)
        } else {
            familyMembers.forEach { member ->
                val role = member["role"]?.toString() ?: "Guardian"
                val name = member["name"]?.toString() ?: member["displayName"]?.toString() ?: "Unknown Member"
                
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(role.replaceFirstChar { it.uppercase() }) },
                    leadingContent = {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountCircle, null, tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        OutlinedButton(
            onClick = { 
                Toast.makeText(context, "Go to Family Management to invite more adults", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.GroupAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Manage Family Circle")
        }
    }

    if (showChangePasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Update Exit Password") },
            text = {
                Column {
                    Text("Set a 4-6 digit numeric code. You will need this to log out from the child app on their device.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPass = it },
                        label = { Text("New Numeric Password") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPass.length >= 4) {
                        db.collection("users").document(childId).update("parentPassword", newPass)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                showChangePasswordDialog = false
                            }
                    } else {
                        Toast.makeText(context, "Password must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SecuritySection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val permissionStatus = childData?.get("permissionStatus") as? Map<*, *>
    val blockAppInstalls = childData?.get("blockAppInstalls") as? Boolean ?: false
    
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Device Security Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Monitor and manage child device protection settings.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        Spacer(Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionStatusRow("Anti-Uninstall (Admin)", permissionStatus?.get("deviceAdmin") == true)
                PermissionStatusRow("App Usage Monitoring", permissionStatus?.get("usageStats") == true)
                PermissionStatusRow("Smart App Blocking", permissionStatus?.get("accessibility") == true)
                PermissionStatusRow("Location Safety", permissionStatus?.get("location") == true)
                PermissionStatusRow("Screen Overlay", permissionStatus?.get("overlay") == true)
                PermissionStatusRow("Battery Protection", permissionStatus?.get("batteryOptimization") == true)
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Button(
                    onClick = { 
                        db.collection("users").document(childId).set(mapOf("requestPermissionSetup" to true), SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(context, "Setup request sent to child device", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed to send request: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Build, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Fix Missing Permissions")
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text("Remote Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block New App Installations", fontWeight = FontWeight.Bold)
                        Text("Prevents child from installing any new apps.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(
                        checked = blockAppInstalls,
                        onCheckedChange = { 
                            db.collection("users").document(childId).set(mapOf("blockAppInstalls" to it), SetOptions.merge()) 
                        }
                    )
                }
                
                HorizontalDivider()
                
                Button(
                    onClick = { db.collection("users").document(childId).set(mapOf("loudSignalTrigger" to FieldValue.serverTimestamp()), SetOptions.merge()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Icon(Icons.Default.VolumeUp, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send Loud Attention Signal")
                }
            }
        }
    }
}

@Composable
fun PermissionStatusRow(label: String, isGranted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (isGranted) {
            Badge(containerColor = Color(0xFF4CAF50)) {
                Text("PROTECTED", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
            }
        } else {
            Badge(containerColor = Color.Red) {
                Text("OFF / EXPOSED", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AndroidTvSection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    val tvLocked = childData?.get("tvLocked") as? Boolean ?: false
    var tvTimeLimit by remember(childId, childData?.get("tvDailyTimeLimit")) {
        mutableIntStateOf((childData?.get("tvDailyTimeLimit") as? Number)?.toInt() ?: 120)
    }
    val lastTvSync = childData?.get("lastTvSync") as? com.google.firebase.Timestamp
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val schedule = childData?.get("tvScreenTimeSchedule") as? Map<*, *>
    var scheduleEnabled by remember(childId, schedule?.get("enabled")) {
        mutableStateOf(schedule?.get("enabled") as? Boolean ?: false)
    }
    var startMinute by remember(childId, schedule?.get("startMinute")) {
        mutableIntStateOf((schedule?.get("startMinute") as? Number)?.toInt() ?: 22 * 60)
    }
    var endMinute by remember(childId, schedule?.get("endMinute")) {
        mutableIntStateOf((schedule?.get("endMinute") as? Number)?.toInt() ?: 7 * 60)
    }
    var scheduleDays by remember(childId, schedule?.get("days")) {
        mutableStateOf(
            (schedule?.get("days") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                ?: setOf(2, 3, 4, 5, 6)
        )
    }
    @Suppress("UNCHECKED_CAST")
    val tvApps = childData?.get("tvApps") as? List<Map<String, Any>> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val blockedTvApps = childData?.get("blockedTvApps") as? List<String> ?: emptyList()
    val context = LocalContext.current
    val tvUsageMinutes = (childData?.get("tvUsageMinutesToday") as? Number)?.toLong() ?: 0L

    fun saveTvSchedule() {
        db.collection("users").document(childId).set(
            mapOf("tvScreenTimeSchedule" to mapOf(
                "enabled" to scheduleEnabled,
                "startMinute" to startMinute,
                "endMinute" to endMinute,
                "days" to scheduleDays.sorted()
            )),
            SetOptions.merge()
        )
    }

    fun showTvTimePicker(initialMinute: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onSelected(hour * 60 + minute); saveTvSchedule() },
            initialMinute / 60,
            initialMinute % 60,
            true
        ).show()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Android TV Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("TV Status", fontWeight = FontWeight.Bold)
                        Text(if (tvLocked) "Locked" else "Active", color = if (tvLocked) Color.Red else Color(0xFF4CAF50))
                    }
                    Switch(
                        checked = !tvLocked,
                        onCheckedChange = { active ->
                            db.collection("users").document(childId).set(mapOf("tvLocked" to !active), SetOptions.merge())
                        }
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text("TV Daily Time Limit", fontWeight = FontWeight.Bold)
                Text(
                    if (tvTimeLimit == 0) "No daily limit" else "${tvTimeLimit / 60}h ${tvTimeLimit % 60}m allowed",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = tvTimeLimit.toFloat(),
                    onValueChange = { tvTimeLimit = it.toInt() },
                    valueRange = 0f..480f,
                    onValueChangeFinished = {
                        db.collection("users").document(childId).set(mapOf("tvDailyTimeLimit" to tvTimeLimit), SetOptions.merge())
                    }
                )
                Text("Set to 0 for no daily limit.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Text("TV usage today: ${tvUsageMinutes / 60}h ${tvUsageMinutes % 60}m", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Scheduled downtime", fontWeight = FontWeight.Bold)
                        Text("Pause TV apps during sleep or school.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(
                        checked = scheduleEnabled,
                        onCheckedChange = {
                            scheduleEnabled = it
                            saveTvSchedule()
                        }
                    )
                }
                if (scheduleEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { showTvTimePicker(startMinute) { startMinute = it } }, modifier = Modifier.weight(1f)) {
                            Text("Start ${formatClockTime(startMinute)}")
                        }
                        OutlinedButton(onClick = { showTvTimePicker(endMinute) { endMinute = it } }, modifier = Modifier.weight(1f)) {
                            Text("End ${formatClockTime(endMinute)}")
                        }
                    }
                    Text("Choose days", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("M" to 2, "T" to 3, "W" to 4, "T" to 5, "F" to 6, "S" to 7, "S" to 1).forEach { (label, day) ->
                            FilterChip(
                                selected = day in scheduleDays,
                                onClick = {
                                    scheduleDays = if (day in scheduleDays) scheduleDays - day else scheduleDays + day
                                    saveTvSchedule()
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text("Calls and text messages stay available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Allowed TV apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (tvApps.isEmpty()) {
            Text("Sync the TV to load its installed apps.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            tvApps.take(80).forEach { app ->
                val packageName = app["packageName"] as? String ?: return@forEach
                val appName = app["name"] as? String ?: packageName
                val isAllowed = packageName !in blockedTvApps
                ListItem(
                    headlineContent = { Text(appName) },
                    supportingContent = { Text(packageName, fontSize = 10.sp, color = Color.Gray) },
                    trailingContent = {
                        Switch(
                            checked = isAllowed,
                            onCheckedChange = { allowed ->
                                val newList = if (allowed) blockedTvApps.filter { it != packageName } else blockedTvApps + packageName
                                db.collection("users").document(childId).set(mapOf("blockedTvApps" to newList), SetOptions.merge())
                            }
                        )
                    }
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { 
                db.collection("users").document(childId).set(mapOf("tvSyncTrigger" to FieldValue.serverTimestamp()), SetOptions.merge())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sync & Update TV Settings")
        }
        
        if (lastTvSync != null) {
            Text("Last synced: ${dateFormat.format(lastTvSync.toDate())}", modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun AppManagementSection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    @Suppress("UNCHECKED_CAST")
    val installedApps = childData?.get("installedApps") as? List<Map<String, Any>> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val blockedApps = childData?.get("blockedApps") as? List<String> ?: emptyList()

    if (installedApps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No apps data synced yet", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(installedApps) { app ->
                val packageName = app["packageName"] as? String ?: ""
                val appName = app["name"] as? String ?: "Unknown App"
                val isBlocked = blockedApps.contains(packageName)
                val usageTime = (app["usageTime"] as? Number)?.toLong() ?: 0L
                
                ListItem(
                    headlineContent = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(appName, fontWeight = FontWeight.SemiBold)
                            if (usageTime > 0) {
                                Spacer(Modifier.width(8.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(formatUsageTime(usageTime), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    },
                    supportingContent = { Text(packageName, fontSize = 10.sp, color = Color.Gray) },
                    trailingContent = {
                        Switch(
                            checked = !isBlocked,
                            onCheckedChange = { allowed ->
                                val newList = if (allowed) blockedApps.filter { it != packageName } else blockedApps + packageName
                                db.collection("users").document(childId).set(mapOf("blockedApps" to newList), SetOptions.merge())
                            }
                        )
                    }
                )
            }
        }
    }
}

private fun formatUsageTime(millis: Long): String {
    val mins = millis / (1000 * 60)
    return if (mins >= 60) "${mins/60}h ${mins%60}m" else "${mins}m"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebManagementSection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    var history by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    @Suppress("UNCHECKED_CAST")
    val blockedWebsites = childData?.get("blockedWebsites") as? List<String> ?: emptyList()
    var showAddBlockDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    LaunchedEffect(childId) {
        db.collection("users").document(childId).collection("webHistory")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, _ -> history = snapshot?.documents?.mapNotNull { it.data } ?: emptyList() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Web History & Blocking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddBlockDialog = true }) {
                Icon(Icons.Default.Block, contentDescription = "Block Website", tint = Color.Red)
            }
        }

        if (blockedWebsites.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                blockedWebsites.forEach { site ->
                    AssistChip(onClick = { }, label = { Text(site) }, trailingIcon = {
                        IconButton(onClick = {
                            db.collection("users").document(childId).set(mapOf("blockedWebsites" to blockedWebsites.filter { it != site }), SetOptions.merge())
                        }, modifier = Modifier.size(16.dp)) { Icon(Icons.Default.Close, null) }
                    })
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history) { entry ->
                val domain = entry["domain"] as? String ?: ""
                val timestamp = entry["timestamp"] as? com.google.firebase.Timestamp
                ListItem(
                    headlineContent = { Text(domain) },
                    supportingContent = { timestamp?.let { Text(dateFormat.format(it.toDate()), fontSize = 10.sp) } },
                    trailingContent = {
                        TextButton(onClick = {
                            if (!blockedWebsites.contains(domain)) db.collection("users").document(childId).set(mapOf("blockedWebsites" to blockedWebsites + domain), SetOptions.merge())
                        }) { Text("Block", color = Color.Red) }
                    }
                )
            }
        }
    }

    if (showAddBlockDialog) {
        var domainInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            title = { Text("Block Website Domain") },
            text = { OutlinedTextField(value = domainInput, onValueChange = { domainInput = it }, label = { Text("Domain (e.g. facebook.com)") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    if (domainInput.isNotBlank()) {
                        db.collection("users").document(childId).set(mapOf("blockedWebsites" to blockedWebsites + domainInput.lowercase().trim()), SetOptions.merge())
                        showAddBlockDialog = false
                    }
                }) { Text("Block") }
            },
            dismissButton = { TextButton(onClick = { showAddBlockDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun LocationSection(childId: String, childData: Map<String, Any>?, onLiveLocationClicked: (String) -> Unit) {
    val context = LocalContext.current
    val location = childData?.get("location") as? GeoPoint
    val lastUpdate = childData?.get("lastLocationUpdate") as? com.google.firebase.Timestamp
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        
        if (location != null) {
            Text("Current Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Lat: ${location.latitude}, Lng: ${location.longitude}")
            if (lastUpdate != null) Text("Last updated: ${dateFormat.format(lastUpdate.toDate())}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onLiveLocationClicked(childId) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Map, null)
                Spacer(Modifier.width(8.dp))
                Text("View Live Location Map")
            }
        } else {
            Text("No location data available", color = Color.Gray)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ScreenTimeSection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    var timeLimit by remember(childId, childData?.get("dailyTimeLimit")) {
        mutableIntStateOf((childData?.get("dailyTimeLimit") as? Number)?.toInt() ?: 120)
    }
    val isLocked = childData?.get("locked") as? Boolean ?: false
    val schedule = childData?.get("screenTimeSchedule") as? Map<*, *>
    var scheduleEnabled by remember(childId, schedule?.get("enabled")) {
        mutableStateOf(schedule?.get("enabled") as? Boolean ?: false)
    }
    var startMinute by remember(childId, schedule?.get("startMinute")) {
        mutableIntStateOf((schedule?.get("startMinute") as? Number)?.toInt() ?: 22 * 60)
    }
    var endMinute by remember(childId, schedule?.get("endMinute")) {
        mutableIntStateOf((schedule?.get("endMinute") as? Number)?.toInt() ?: 7 * 60)
    }
    var scheduleDays by remember(childId, schedule?.get("days")) {
        mutableStateOf(
            (schedule?.get("days") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet()
                ?: setOf(2, 3, 4, 5, 6)
        )
    }
    val context = LocalContext.current

    fun saveSchedule() {
        db.collection("users").document(childId).set(
            mapOf("screenTimeSchedule" to mapOf(
                "enabled" to scheduleEnabled,
                "startMinute" to startMinute,
                "endMinute" to endMinute,
                "days" to scheduleDays.sorted()
            )),
            SetOptions.merge()
        )
    }

    fun showTimePicker(initialMinute: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onSelected(hour * 60 + minute); saveSchedule() },
            initialMinute / 60,
            initialMinute % 60,
            true
        ).show()
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Daily Time Limit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (timeLimit == 0) "No daily limit" else "${timeLimit / 60}h ${timeLimit % 60}m allowed per day",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Slider(
            value = timeLimit.toFloat(),
            onValueChange = { timeLimit = it.toInt() },
            valueRange = 0f..480f,
            onValueChangeFinished = { db.collection("users").document(childId).set(mapOf("dailyTimeLimit" to timeLimit), SetOptions.merge()) }
        )

        Text("Set to 0 for no daily limit. Calls and text messages remain available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scheduled downtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Pause entertainment apps during sleep or school.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(
                checked = scheduleEnabled,
                onCheckedChange = {
                    scheduleEnabled = it
                    saveSchedule()
                }
            )
        }

        if (scheduleEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showTimePicker(startMinute) { startMinute = it } }, modifier = Modifier.weight(1f)) {
                    Text("Start ${formatClockTime(startMinute)}")
                }
                OutlinedButton(onClick = { showTimePicker(endMinute) { endMinute = it } }, modifier = Modifier.weight(1f)) {
                    Text("End ${formatClockTime(endMinute)}")
                }
            }
            Text("Choose days", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("M" to 2, "T" to 3, "W" to 4, "T" to 5, "F" to 6, "S" to 7, "S" to 1).forEach { (label, day) ->
                    FilterChip(
                        selected = day in scheduleDays,
                        onClick = {
                            scheduleDays = if (day in scheduleDays) scheduleDays - day else scheduleDays + day
                            saveSchedule()
                        },
                        label = { Text(label) }
                    )
                }
            }
            Text("Overnight schedules can cross midnight. Calls and text messages stay available.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        
        Spacer(Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isLocked) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Immediate Lock", fontWeight = FontWeight.Bold)
                Button(
                    onClick = { db.collection("users").document(childId).set(mapOf("locked" to !isLocked), SetOptions.merge()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isLocked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                ) { Text(if (isLocked) "Unlock Device" else "Lock Device Now") }
            }
        }
    }
}

private fun formatClockTime(totalMinutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", totalMinutes / 60, totalMinutes % 60)

@Composable
fun TaskSection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    var assignments by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(childId) {
        db.collection("users").document(childId).collection("assignments").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ -> assignments = snapshot?.documents?.mapNotNull { it.data?.let { d -> d + ("id" to it.id) } } ?: emptyList() }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Active Assignments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(assignments) { task ->
                val taskId = task["id"] as? String ?: ""
                val title = task["title"] as? String ?: ""
                val reward = (task["points"] as? Long) ?: 0
                val status = task["status"] as? String ?: "pending"

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold)
                            Text("Reward: $reward pts", style = MaterialTheme.typography.labelSmall)
                        }
                        if (status == "pending") {
                            TextButton(onClick = {
                                db.collection("users").document(childId).collection("assignments").document(taskId).update("status", "completed")
                                db.collection("users").document(childId).update("rewardPoints", FieldValue.increment(reward))
                            }) { Text("Award") }
                        } else {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractionsSection(childId: String, childData: Map<String, Any>?) {
    val db = FirebaseFirestore.getInstance()
    var interactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    LaunchedEffect(childId) {
        db.collection("users").document(childId).collection("interactions").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ -> interactions = snapshot?.documents?.mapNotNull { it.data } ?: emptyList() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Teacher Interactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(interactions) { record ->
                val teacherName = record["teacherName"] as? String ?: "Unknown Teacher"
                val contentTitle = record["contentTitle"] as? String ?: "Content"
                val timestamp = record["timestamp"] as? com.google.firebase.Timestamp

                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(teacherName, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("Explored: $contentTitle") },
                        trailingContent = { timestamp?.let { Text(dateFormat.format(it.toDate()), fontSize = 10.sp) } }
                    )
                }
            }
        }
    }
}
