package com.joseph.cbchubpro.child.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joseph.cbchubpro.child.data.model.AssignmentSubmission
import com.joseph.cbchubpro.child.data.model.LessonContent
import com.joseph.cbchubpro.child.data.model.LessonContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LessonViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _lesson = MutableStateFlow<LessonContent?>(null)
    val lesson = _lesson.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    fun loadLesson(lessonId: String) {
        if (lessonId.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            db.collection("lessons").document(lessonId).get()
                .addOnSuccessListener { snapshot ->
                    val l = snapshot.toObject(LessonContent::class.java)?.copy(id = snapshot.id)
                    _lesson.value = l
                    _isLoading.value = false
                }
                .addOnFailureListener { e ->
                    Log.e("LessonView", "Error: ${e.message}")
                    _isLoading.value = false
                }
        }
    }

    private suspend fun logInteraction(teacherId: String, teacherName: String, contentId: String, contentTitle: String, type: String, points: Long) {
        val uid = auth.currentUser?.uid ?: return
        val interaction = mapOf(
            "teacherId" to teacherId,
            "teacherName" to teacherName,
            "contentId" to contentId,
            "contentTitle" to contentTitle,
            "contentType" to type,
            "pointsEarned" to points,
            "timestamp" to FieldValue.serverTimestamp()
        )
        // Correct path: users/{childUid}/interactions
        db.collection("users").document(uid).collection("interactions").add(interaction).await()
    }

    fun submitAssignment(lessonId: String, text: String, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Student"
        val currentLesson = _lesson.value ?: return
        
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val submission = AssignmentSubmission(
                    assignmentId = lessonId,
                    studentId = uid,
                    studentName = name,
                    textSubmission = text,
                    submittedAt = com.google.firebase.Timestamp.now()
                )
                db.collection("submissions").add(submission).await()
                
                // Log interaction for parent dashboard
                logInteraction(
                    teacherId = currentLesson.teacherId,
                    teacherName = currentLesson.teacherName,
                    contentId = lessonId,
                    contentTitle = currentLesson.title,
                    type = "ASSIGNMENT",
                    points = 10L
                )

                // Reward student with points
                db.collection("users").document(uid).update("pointsBalance", FieldValue.increment(10))
                onComplete()
            } catch (e: Exception) {
                Log.e("LessonView", "Submit error: ${e.message}")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun askTeacherQuestion(questionText: String, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Student"
        val currentLesson = _lesson.value ?: return

        viewModelScope.launch {
            try {
                val messageId = db.collection("messages").document().id
                val message = mapOf(
                    "id" to messageId,
                    "senderId" to uid,
                    "senderName" to name,
                    "receiverId" to currentLesson.teacherId,
                    "message" to questionText,
                    "lessonId" to currentLesson.id,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                db.collection("messages").document(messageId).set(message).await()

                // Log interaction as a question
                logInteraction(
                    teacherId = currentLesson.teacherId,
                    teacherName = currentLesson.teacherName,
                    contentId = currentLesson.id,
                    contentTitle = "Question: ${questionText.take(30)}...",
                    type = "QUESTION",
                    points = 2L
                )

                db.collection("users").document(uid).update("pointsBalance", FieldValue.increment(2))
                onComplete()
            } catch (e: Exception) {
                Log.e("LessonView", "Question error: ${e.message}")
            }
        }
    }

    fun castVote(lessonId: String, option: String, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val vote = mapOf("studentId" to uid, "option" to option, "timestamp" to FieldValue.serverTimestamp())
                db.collection("lessons").document(lessonId).collection("votes").document(uid).set(vote).await()
                onComplete()
            } catch (e: Exception) {
                Log.e("LessonView", "Vote error: ${e.message}")
            }
        }
    }

    fun markAsCompleted(lessonId: String, onComplete: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val currentLesson = _lesson.value ?: return

        viewModelScope.launch {
            try {
                val activityId = db.collection("learning_activity").document().id
                val activity = mapOf(
                    "id" to activityId,
                    "lessonId" to lessonId,
                    "studentId" to uid,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "type" to "COMPLETED"
                )
                db.collection("learning_activity").document(activityId).set(activity).await()

                // Log interaction for parent dashboard
                logInteraction(
                    teacherId = currentLesson.teacherId,
                    teacherName = currentLesson.teacherName,
                    contentId = lessonId,
                    contentTitle = currentLesson.title,
                    type = "LESSON",
                    points = 5L
                )

                db.collection("users").document(uid).update("pointsBalance", FieldValue.increment(5))
                onComplete()
            } catch (e: Exception) {
                Log.e("LessonView", "Activity error: ${e.message}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonViewScreen(
    lessonId: String,
    onBack: () -> Unit,
    viewModel: LessonViewModel = viewModel()
) {
    val lesson by viewModel.lesson.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val context = LocalContext.current

    var submissionText by remember { mutableStateOf("") }
    var hasSubmitted by remember { mutableStateOf(false) }
    var selectedPollOption by remember { mutableStateOf<String?>(null) }
    var showAskTeacherDialog by remember { mutableStateOf(false) }

    LaunchedEffect(lessonId) {
        viewModel.loadLesson(lessonId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lesson?.title ?: "Lesson") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAskTeacherDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpCenter, contentDescription = "Ask Teacher")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (lesson == null) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Lesson not found")
            }
        } else {
            lesson?.let { l ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(Color(0xFFF8F9FA))
                ) {
                    item { LessonHeader(l) }
                    
                    // --- MEDIA VIEWER SECTION ---
                    val videoId = extractYoutubeVideoId(l.videoUrl)
                    if (videoId != null) {
                        item { YouTubePlayer(videoId) }
                    } else if (l.type == LessonContentType.IMAGE_BASED && !l.imageUrl.isNullOrBlank()) {
                        item { ZoomableImageCard(l.imageUrl!!) }
                    } else if (l.type == LessonContentType.AUDIO && !l.audioUrl.isNullOrBlank()) {
                        item { AudioPlayerUI(l.audioUrl!!) }
                    } else if (!l.fileUrl.isNullOrBlank() && (l.type == LessonContentType.PDF || l.type == LessonContentType.SLIDES)) {
                        item {
                            val viewerUrl = "https://docs.google.com/gview?embedded=true&url=${Uri.encode(l.fileUrl)}"
                            WebViewContainer(viewerUrl, Modifier.height(500.dp))
                        }
                    }

                    // --- CONTENT SECTION ---
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Announcements / Alerts
                            if (l.type == LessonContentType.ANNOUNCEMENT) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer, 
                                    shape = RoundedCornerShape(12.dp), 
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.error, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Campaign, null, tint = Color.White)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("IMPORTANT NOTICE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                            Text(l.notes ?: "Check the notes for details.", style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            } else if (l.type == LessonContentType.REAL_LIFE_APP) {
                                RealLifeMissionCard(l.notes ?: "Apply what you learned in the real world!")
                                Spacer(Modifier.height(16.dp))
                            } else if (!l.notes.isNullOrBlank()) {
                                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Teacher's Notes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(l.notes!!, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            
                            // Poll Interaction
                            if (l.type == LessonContentType.POLL && l.pollOptions.isNotEmpty()) {
                                Text("Class Poll: Tap to vote", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                l.pollOptions.forEach { option ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                            selectedPollOption = option
                                            viewModel.castVote(l.id, option) {
                                                Toast.makeText(context, "Vote cast: $option", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = CardDefaults.cardColors(containerColor = if (selectedPollOption == option) MaterialTheme.colorScheme.primaryContainer else Color.White),
                                        border = if (selectedPollOption == option) null else BorderStroke(1.dp, Color.LightGray)
                                    ) {
                                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = selectedPollOption == option, onClick = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(option)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            if (!l.practicalTask.isNullOrBlank()) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Practical Activity", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(l.practicalTask!!)
                                    }
                                }
                            }

                            // Interactive Submission
                            if (l.type == LessonContentType.ASSIGNMENT || l.type == LessonContentType.PROJECT) {
                                if (!hasSubmitted) {
                                    Text("Submit Your Work", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = submissionText,
                                        onValueChange = { submissionText = it },
                                        modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp),
                                        placeholder = { Text("Type your answer here...") },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Button(
                                        onClick = { 
                                            viewModel.submitAssignment(l.id, submissionText) {
                                                hasSubmitted = true
                                                Toast.makeText(context, "Submitted! +10 Points earned!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = submissionText.isNotBlank() && !isSubmitting,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isSubmitting) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                                        else {
                                            Icon(Icons.AutoMirrored.Filled.Send, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Submit & Earn Points")
                                        }
                                    }
                                } else {
                                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Stars, null, tint = Color(0xFF4CAF50))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Well done! Assignment submitted.", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                            
                            Button(
                                onClick = { 
                                    viewModel.markAsCompleted(l.id) {
                                        Toast.makeText(context, "Lesson Completed! +5 Points.", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("I've finished this lesson")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAskTeacherDialog) {
        AskTeacherDialog(
            onDismiss = { showAskTeacherDialog = false },
            onSend = { question ->
                viewModel.askTeacherQuestion(question) {
                    Toast.makeText(context, "Question sent to teacher! +2 Points", Toast.LENGTH_SHORT).show()
                    showAskTeacherDialog = false
                }
            }
        )
    }
}

@Composable
fun AskTeacherDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var question by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask Private Teacher") },
        text = {
            Column {
                Text("Stuck? Ask your teacher a question and they will get back to you.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Your Question") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("How do I solve the second problem?") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSend(question) }, enabled = question.isNotBlank()) {
                Text("Send Question")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RealLifeMissionCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)), // Light Yellow
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(2.dp, Color(0xFFFBC02D))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(Color(0xFFFBC02D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.RocketLaunch, null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("REAL-LIFE MISSION", fontWeight = FontWeight.ExtraBold, color = Color(0xFF7F6000), fontSize = 12.sp)
                    Text("Can you do this at home?", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.DarkGray)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text("Mission Challenge!", fontWeight = FontWeight.Bold, color = Color(0xFFFBC02D))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFBC02D))
            }
        }
    }
}

@Composable
fun ZoomableImageCard(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        offset += offsetChange
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray)
                    .transformable(state = state)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "Lesson Chart",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale.coerceIn(1f, 5f),
                            scaleY = scale.coerceIn(1f, 5f),
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
                
                if (scale == 1f) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(4.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(
                "Pinch to zoom into details • Double tap to reset",
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun AudioPlayerUI(url: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Teacher's Voice Lesson", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Tap to play audio instructions", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun LessonHeader(lesson: LessonContent) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lesson.subject.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                Text(lesson.type.name.replace("_", " "), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(lesson.topic, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
            Spacer(Modifier.width(4.dp))
            Text("Teacher ${lesson.teacherName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
    }
}

@Composable
fun YouTubePlayer(videoId: String) {
    val htmlData = """
        <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>body { margin: 0; background: black; }.vc { position: relative; padding-bottom: 56.25%; height: 0; }
        .vc iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }</style></head>
        <body><div class="vc"><div id="player"></div></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>function onYouTubeIframeAPIReady() { new YT.Player('player', { height: '100%', width: '100%', videoId: '$videoId',
        playerVars: { 'rel': 0, 'modestbranding': 1, 'playsinline': 1, 'origin': 'https://www.youtube.com' } }); }</script>
        </body></html>
    """.trimIndent()
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                settings.apply { javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false }
                webChromeClient = WebChromeClient(); webViewClient = WebViewClient()
                loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)
    )
}

@Composable
fun WebViewContainer(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply { javaScriptEnabled = true; domStorageEnabled = true; loadWithOverviewMode = true; useWideViewPort = true; builtInZoomControls = true; displayZoomControls = false }
                webChromeClient = WebChromeClient(); webViewClient = WebViewClient()
            }
        },
        update = { it.loadUrl(url) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

fun extractYoutubeVideoId(url: String?): String? {
    if (url == null || url.isBlank()) return null
    val cleanUrl = url.trim()
    val pattern = "(?i)(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([\\w-]{11})"
    val matcher = java.util.regex.Pattern.compile(pattern).matcher(cleanUrl)
    return if (matcher.find()) matcher.group(1) else if (cleanUrl.length == 11) cleanUrl else null
}
