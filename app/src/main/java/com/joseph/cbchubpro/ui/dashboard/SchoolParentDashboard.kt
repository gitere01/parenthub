package com.joseph.cbchubpro.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import com.joseph.cbchubpro.ui.management.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class SchoolParentViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _studentData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val studentData = _studentData.asStateFlow()

    private val _grades = MutableStateFlow<List<GradeRecord>>(emptyList())
    val grades = _grades.asStateFlow()

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements = _announcements.asStateFlow()

    private val _lessons = MutableStateFlow<List<LessonContent>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _activityLogs = MutableStateFlow<List<LearningActivityLog>>(emptyList())
    val activityLogs = _activityLogs.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun fetchData(schoolId: String, studentId: String) {
        if (schoolId.isBlank() || studentId.isBlank()) {
            _errorMessage.value = "Missing link to school/student."
            return
        }
        _isLoading.value = true
        _errorMessage.value = null
        
        db.collection("schools").document(schoolId).collection("students").document(studentId).addSnapshotListener { s, e ->
            if (e != null) { _errorMessage.value = "Student data access error: ${e.localizedMessage}"; return@addSnapshotListener }
            val data = s?.data ?: emptyMap()
            _studentData.value = data
            val grade = data["grade"]?.toString() ?: ""
            if(grade.isNotEmpty()) {
                fetchLearning(schoolId, grade)
                fetchAnnouncements(schoolId, grade)
            }
            _isLoading.value = false
        }

        db.collection("schools").document(schoolId).collection("grades").whereEqualTo("studentId", studentId).addSnapshotListener { s, _ ->
            _grades.value = s?.documents?.mapNotNull { it.toObject(GradeRecord::class.java) } ?: emptyList()
        }

        db.collection("schools").document(schoolId).collection("activity_logs").whereEqualTo("studentId", studentId).orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener { s, _ ->
            _activityLogs.value = s?.documents?.mapNotNull { it.toObject(LearningActivityLog::class.java) } ?: emptyList()
        }

        db.collection("schools").document(schoolId).collection("chats").whereEqualTo("studentId", studentId).orderBy("timestamp", Query.Direction.ASCENDING).addSnapshotListener { s, _ ->
            _chats.value = s?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList()
        }
    }

    private fun fetchLearning(schoolId: String, grade: String) {
        db.collection("schools").document(schoolId).collection("lessons").whereEqualTo("gradeLevel", grade).addSnapshotListener { s, _ ->
            _lessons.value = s?.documents?.mapNotNull { it.toObject(LessonContent::class.java) } ?: emptyList()
        }
    }

    private fun fetchAnnouncements(schoolId: String, grade: String) {
        db.collection("schools").document(schoolId).collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e ->
                if (e != null) {
                    db.collection("schools").document(schoolId).collection("announcements").get().addOnSuccessListener { snap ->
                        val all = snap.documents.mapNotNull { it.toObject(Announcement::class.java)?.copy(id = it.id) }
                        _announcements.value = all.filter { (it.target == "Parents" || it.target == "All") && (it.gradeLevel == "All" || it.gradeLevel == grade) }
                    }
                    return@addSnapshotListener
                }
                val allDocs = s?.documents?.mapNotNull { it.toObject(Announcement::class.java)?.copy(id = it.id) } ?: emptyList()
                _announcements.value = allDocs.filter { 
                    (it.target == "Parents" || it.target == "All") && 
                    (it.gradeLevel == "All" || it.gradeLevel == grade) 
                }
            }
    }

    fun deleteAnnouncement(schoolId: String, annId: String) {
        viewModelScope.launch {
            try {
                db.collection("schools").document(schoolId).collection("announcements").document(annId).delete().await()
            } catch (e: Exception) { }
        }
    }

    fun logInteraction(schoolId: String, studentId: String, contentId: String, title: String, type: String) {
        viewModelScope.launch {
            try {
                db.collection("schools").document(schoolId).collection("activity_logs").add(
                    LearningActivityLog(schoolId=schoolId, studentId=studentId, contentId=contentId, contentTitle=title, contentType=type)
                )
            } catch (e: Exception) { }
        }
    }

    fun sendMessage(schoolId: String, studentId: String, msg: String) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                db.collection("schools").document(schoolId).collection("chats").add(
                    ChatMessage(
                        schoolId = schoolId,
                        studentId = studentId,
                        senderId = user?.uid ?: "",
                        senderName = user?.displayName ?: "Parent",
                        senderRole = "parent",
                        message = msg
                    )
                )
            } catch (e: Exception) { }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolParentDashboard(
    schoolId: String, 
    studentId: String, 
    onLogoutClicked: () -> Unit, 
    onMarketplaceClicked: () -> Unit = {},
    onPropertyListingClicked: () -> Unit = {},
    onRestaurantBookingClicked: () -> Unit = {},
    onSwapHubClicked: () -> Unit = {},
    viewModel: SchoolParentViewModel = viewModel()
) {
    val student by viewModel.studentData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Announcements", "Learning", "Performance", "Discovery", "Messages")

    LaunchedEffect(schoolId, studentId) { viewModel.fetchData(schoolId, studentId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("School Parent Dashboard") }, actions = { IconButton(onClick = onLogoutClicked) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) } }) }
    ) { padding ->
        if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
            }
        } else if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) }) }
                }
                when(tabs[selectedTab]) {
                    "Overview" -> ParentOverviewTab(student, viewModel) { selectedTab = 1 }
                    "Announcements" -> ParentAnnouncementsTab(schoolId, viewModel)
                    "Learning" -> ParentLearningTab(schoolId, studentId, viewModel)
                    "Performance" -> ParentPerformanceTab(viewModel)
                    "Discovery" -> ParentDiscoveryTab(
                        onMarketplaceClicked,
                        onPropertyListingClicked,
                        onRestaurantBookingClicked,
                        onSwapHubClicked
                    )
                    "Messages" -> ParentMessagesTab(schoolId, studentId, viewModel)
                }
            }
        }
    }
}

@Composable
fun ParentDiscoveryTab(
    onMarketplaceClicked: () -> Unit,
    onPropertyListingClicked: () -> Unit,
    onRestaurantBookingClicked: () -> Unit,
    onSwapHubClicked: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Explore Services", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Marketplace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DashboardButton(text = "Vendor Marketplace", icon = Icons.Default.Storefront, onClick = onMarketplaceClicked)
                Spacer(Modifier.height(8.dp))
                DashboardButton(text = "Swap Hub", icon = Icons.Default.SwapCalls, onClick = onSwapHubClicked, containerColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Property \u0026 Hospitality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DashboardButton(text = "Property Listings", icon = Icons.Default.Apartment, onClick = onPropertyListingClicked)
                Spacer(Modifier.height(8.dp))
                DashboardButton(text = "Restaurant \u0026 Rooms", icon = Icons.Default.Restaurant, onClick = onRestaurantBookingClicked, containerColor = MaterialTheme.colorScheme.secondaryContainer)
            }
        }
    }
}

@Composable
fun ParentAnnouncementsTab(schoolId: String, viewModel: SchoolParentViewModel) {
    val announcements by viewModel.announcements.collectAsState()
    val df = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    LazyColumn(Modifier.padding(16.dp)) {
        if (announcements.isEmpty()) {
            item { 
                Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                    Text("No announcements from school.", color = Color.Gray) 
                }
            }
        }
        items(announcements) { ann ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(ann.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { viewModel.deleteAnnouncement(schoolId, ann.id) }) {
                            Icon(Icons.Default.Delete, "Dismiss", tint = Color.Red.copy(alpha = 0.6f))
                        }
                    }
                    Text(ann.content, Modifier.padding(top = 8.dp))
                    Text("Posted: ${df.format(ann.createdAt.toDate())}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp), color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun ParentOverviewTab(student: Map<String, Any>, viewModel: SchoolParentViewModel, onAnnounceClick: () -> Unit) {
    val logs by viewModel.activityLogs.collectAsState()
    val ann by viewModel.announcements.collectAsState()
    
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        if (ann.isNotEmpty()) {
            val latest = ann.first()
            Card(Modifier.fillMaxWidth().clickable { onAnnounceClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Color.Red)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Latest School Alert", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 12.sp)
                        Text(latest.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text(student["name"]?.toString() ?: "Child Name", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Grade: ${student["grade"] ?: "N/A"}")
                Text("Fee Balance: KES ${student["feeBalance"] ?: 0}", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Recent Activity", fontWeight = FontWeight.Bold)
        logs.take(5).forEach { log ->
            ListItem(headlineContent = { Text(log.contentTitle) }, supportingContent = { Text("Interacted with ${log.contentType}") }, leadingContent = { Icon(Icons.Default.History, null) })
        }
    }
}

@Composable
fun ParentLearningTab(schoolId: String, studentId: String, viewModel: SchoolParentViewModel) {
    val lessons by viewModel.lessons.collectAsState()
    var viewingUrl by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.padding(16.dp)) {
        if (lessons.isEmpty()) {
            item { Box(Modifier.fillParentMaxSize(), Alignment.Center) { Text("No learning materials posted for this grade.", color = Color.Gray) } }
        }
        items(lessons) { lesson ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                viewModel.logInteraction(schoolId, studentId, lesson.id, lesson.title, lesson.type.name)
                viewingUrl = lesson.videoUrl ?: lesson.audioUrl ?: lesson.fileUrl
            }) {
                ListItem(
                    headlineContent = { Text(lesson.title) },
                    supportingContent = { Text(lesson.topic) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                )
            }
        }
    }

    if (viewingUrl != null) {
        UrlEmbedDialog(url = viewingUrl!!, onDismiss = { viewingUrl = null })
    }
}

@Composable
fun ParentPerformanceTab(viewModel: SchoolParentViewModel) {
    val grades by viewModel.grades.collectAsState()
    LazyColumn(Modifier.padding(16.dp)) {
        if (grades.isEmpty()) {
            item { Box(Modifier.fillParentMaxSize(), Alignment.Center) { Text("No grades recorded yet.", color = Color.Gray) } }
        }
        items(grades) { g ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                ListItem(
                    headlineContent = { Text(g.subject) },
                    supportingContent = { Text(g.remarks) },
                    trailingContent = { Text("${g.score}%", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary) }
                )
            }
        }
    }
}

@Composable
fun ParentMessagesTab(schoolId: String, studentId: String, viewModel: SchoolParentViewModel) {
    val chats by viewModel.chats.collectAsState()
    var msg by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        LazyColumn(Modifier.weight(1f)) {
            items(chats) { c ->
                val isMe = c.senderRole == "parent"
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = if(isMe) Alignment.End else Alignment.Start) {
                    Surface(color = if(isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(c.message, Modifier.padding(8.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Ask a question...") }, modifier = Modifier.weight(1f))
            IconButton(onClick = { if(msg.isNotBlank()) { viewModel.sendMessage(schoolId, studentId, msg); msg = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, null) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlEmbedDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                TopAppBar(title = { Text("Content Viewer") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) } })
                AndroidView(factory = { context -> WebView(context).apply { 
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }}, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
