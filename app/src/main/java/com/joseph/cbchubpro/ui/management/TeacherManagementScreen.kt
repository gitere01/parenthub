package com.joseph.cbchubpro.ui.management

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

// Note: LessonContentType, QuestionType, Question, Quiz, LessonContent,
// AssignmentSubmission, StudentBadge, BehaviorLog, StudentRecord are imported
// from the management package (defined in TeacherModels.kt)

// --- ViewModel ---

class TeacherViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _teacherProfile = MutableStateFlow<Map<String, Any>>(emptyMap())
    val teacherProfile = _teacherProfile.asStateFlow()

    private val _lessons = MutableStateFlow<List<LessonContent>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _quizzes = MutableStateFlow<List<Quiz>>(emptyList())
    val quizzes = _quizzes.asStateFlow()

    private val _submissions = MutableStateFlow<List<AssignmentSubmission>>(emptyList())
    val submissions = _submissions.asStateFlow()

    private val _students = MutableStateFlow<List<StudentRecord>>(emptyList())
    val students = _students.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        fetchTeacherData()
    }

    private fun fetchTeacherData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            val profile = snapshot?.data ?: emptyMap()
            _teacherProfile.value = profile
            val schoolId = profile["schoolId"] as? String ?: ""
            if (schoolId.isNotEmpty()) {
                fetchSchoolStudents(schoolId)
            }
        }

        db.collection("lessons").whereEqualTo("teacherId", uid).addSnapshotListener { snapshot, _ ->
            _lessons.value = snapshot?.documents?.mapNotNull { it.toObject(LessonContent::class.java)?.copy(id = it.id) } ?: emptyList()
        }

        db.collection("quizzes").whereEqualTo("teacherId", uid).addSnapshotListener { snapshot, _ ->
            _quizzes.value = snapshot?.documents?.mapNotNull { it.toObject(Quiz::class.java)?.copy(id = it.id) } ?: emptyList()
        }

        db.collection("submissions").whereEqualTo("teacherId", uid).addSnapshotListener { snapshot, _ ->
            _submissions.value = snapshot?.documents?.mapNotNull { it.toObject(AssignmentSubmission::class.java)?.copy(id = it.id) } ?: emptyList()
        }
    }

    private fun fetchSchoolStudents(schoolId: String) {
        db.collection("schools").document(schoolId).collection("students").addSnapshotListener { snapshot, _ ->
            _students.value = snapshot?.documents?.mapNotNull { it.toObject(StudentRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
    }

    fun postLesson(content: LessonContent) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Teacher"
        viewModelScope.launch {
            try {
                val lesson = content.copy(teacherId = uid, teacherName = name)
                db.collection("lessons").add(lesson).await()
                _statusMessage.value = "${lesson.type.name} posted successfully."
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun updateLesson(lesson: LessonContent) {
        viewModelScope.launch {
            try {
                db.collection("lessons").document(lesson.id).set(lesson).await()
                _statusMessage.value = "Lesson updated!"
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun deleteLesson(lesson: LessonContent) {
        viewModelScope.launch {
            try {
                db.collection("lessons").document(lesson.id).delete().await()
                _statusMessage.value = "Lesson deleted!"
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun postQuiz(quiz: Quiz) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Teacher"
        viewModelScope.launch {
            try {
                val newQuiz = quiz.copy(teacherId = uid, teacherName = name)
                db.collection("quizzes").add(newQuiz).await()
                _statusMessage.value = "Quiz posted with automatic marking!"
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun updateQuiz(quiz: Quiz) {
        viewModelScope.launch {
            try {
                db.collection("quizzes").document(quiz.id).set(quiz).await()
                _statusMessage.value = "Quiz updated!"
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun deleteQuiz(quiz: Quiz) {
        viewModelScope.launch {
            try {
                db.collection("quizzes").document(quiz.id).delete().await()
                _statusMessage.value = "Quiz deleted!"
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun gradeSubmission(submissionId: String, score: Double, feedback: String, comment: String) {
        viewModelScope.launch {
            try {
                db.collection("submissions").document(submissionId).update(
                    "score", score,
                    "personalizedFeedback", feedback,
                    "teacherComment", comment,
                    "graded", true
                ).await()
                _statusMessage.value = "Submission graded and feedback sent."
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun awardBadge(studentId: String, studentName: String, badgeType: String, reason: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val badge = StudentBadge(studentId = studentId, studentName = studentName, badgeType = badgeType, reason = reason, teacherId = uid)
                db.collection("student_badges").add(badge).await()

                _statusMessage.value = "Badge awarded to $studentName!"
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun logBehavior(studentId: String, studentName: String, type: String, note: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val log = BehaviorLog(studentId = studentId, studentName = studentName, type = type, note = note, teacherId = uid)
                db.collection("behavior_logs").add(log).await()
                _statusMessage.value = "Behavior log saved."
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun submitVerification(tscNumber: String, idNumber: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val data = mapOf("tscNumber" to tscNumber, "idNumber" to idNumber, "isVerified" to false, "verificationPending" to true)
                db.collection("users").document(uid).set(data, SetOptions.merge()).await()
            } catch (e: Exception) { _statusMessage.value = e.message }
        }
    }

    fun clearStatus() { _statusMessage.value = null }
    fun signOut() { auth.signOut() }
}

// --- UI Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherManagementScreen(onLogoutClicked: () -> Unit, viewModel: TeacherViewModel = viewModel()) {
    val profile by viewModel.teacherProfile.collectAsState()
    val isVerified = profile["isVerified"] as? Boolean ?: false
    val status by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Create Content", "Grading", "Students")

    LaunchedEffect(status) {
        status?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Private Teacher", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.signOut(); onLogoutClicked() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            if (!isVerified && profile["verificationPending"] != true) {
                Box(Modifier.padding(16.dp)) { VerificationSection(profile["verificationPending"] as? Boolean ?: false, viewModel) }
            } else if (profile["verificationPending"] == true && !isVerified) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Verification in progress...", Modifier.padding(16.dp))
                    }
                }
            } else {
                when (tabs[selectedTab]) {
                    "Overview" -> TeacherOverview(viewModel)
                    "Create Content" -> CreateContentTab(viewModel)
                    "Grading" -> GradingTab(viewModel)
                    "Students" -> TeacherStudentsTab(viewModel)
                }
            }
        }
    }
}

@Composable
fun CreateContentTab(viewModel: TeacherViewModel) {
    val lessons by viewModel.lessons.collectAsState()
    val quizzes by viewModel.quizzes.collectAsState()

    var showDialog by remember { mutableStateOf<LessonContentType?>(null) }
    var editLesson by remember { mutableStateOf<LessonContent?>(null) }
    var showQuizBuilder by remember { mutableStateOf(false) }
    var editQuiz by remember { mutableStateOf<Quiz?>(null) }
    var viewingUrl by remember { mutableStateOf<String?>(null) }

    var selectedContentTab by remember { mutableIntStateOf(0) }
    val contentTabs = listOf("New Content", "My Lessons", "My Quizzes")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedContentTab) {
            contentTabs.forEachIndexed { index, title ->
                Tab(selected = selectedContentTab == index, onClick = { selectedContentTab = index }, text = { Text(title, fontSize = 12.sp) })
            }
        }

        when (selectedContentTab) {
            0 -> {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("What would you like to post?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    val contentOptions = listOf(
                        Triple("Typed Notes", Icons.Default.Description, LessonContentType.TYPED_NOTES),
                        Triple("PDF Material", Icons.Default.PictureAsPdf, LessonContentType.PDF),
                        Triple("Audio Lesson", Icons.Default.Mic, LessonContentType.AUDIO),
                        Triple("Video URL", Icons.Default.VideoLibrary, LessonContentType.VIDEO_URL),
                        Triple("PowerPoint Slides", Icons.Default.Slideshow, LessonContentType.SLIDES),
                        Triple("Assignment", Icons.AutoMirrored.Filled.Assignment, LessonContentType.ASSIGNMENT),
                        Triple("Real-life Practical", Icons.Default.Handyman, LessonContentType.REAL_LIFE_APP),
                        Triple("Interactive Poll", Icons.Default.Poll, LessonContentType.POLL),
                        Triple("Announcement", Icons.Default.Campaign, LessonContentType.ANNOUNCEMENT),
                        Triple("Automated Quiz", Icons.Default.Quiz, LessonContentType.QUIZ)
                    )

                    contentOptions.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, icon, type) ->
                                Card(
                                    modifier = Modifier.weight(1f).padding(vertical = 4.dp).clickable {
                                        if (type == LessonContentType.QUIZ) showQuizBuilder = true
                                        else showDialog = type
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    items(lessons) { lesson ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(when(lesson.type) {
                                    LessonContentType.TYPED_NOTES -> Icons.Default.Description
                                    LessonContentType.ASSIGNMENT -> Icons.AutoMirrored.Filled.Assignment
                                    LessonContentType.VIDEO_URL -> Icons.Default.VideoLibrary
                                    LessonContentType.PDF -> Icons.Default.PictureAsPdf
                                    LessonContentType.ANNOUNCEMENT -> Icons.Default.Campaign
                                    LessonContentType.SLIDES -> Icons.Default.Slideshow
                                    else -> Icons.Default.MenuBook
                                }, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(lesson.title, fontWeight = FontWeight.Bold)
                                    Text("${lesson.subject} - ${lesson.topic}", style = MaterialTheme.typography.bodySmall)
                                    Text("Grade: ${lesson.gradeLevel}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Row {
                                    val url = lesson.videoUrl ?: lesson.audioUrl ?: lesson.fileUrl
                                    if (!url.isNullOrBlank()) {
                                        IconButton(onClick = { viewingUrl = url }) { Icon(Icons.Default.Visibility, "View", tint = MaterialTheme.colorScheme.secondary) }
                                    }
                                    IconButton(onClick = { editLesson = lesson }) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { viewModel.deleteLesson(lesson) }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                                }
                            }
                        }
                    }
                    if (lessons.isEmpty()) {
                        item { Box(Modifier.fillParentMaxSize(), Alignment.Center) { Text("No lessons yet.", color = Color.Gray) } }
                    }
                }
            }
            2 -> {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    items(quizzes) { quiz ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Quiz, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(quiz.title, fontWeight = FontWeight.Bold)
                                    Text("${quiz.subject} - ${quiz.topic}", style = MaterialTheme.typography.bodySmall)
                                    Text("Grade: ${quiz.gradeLevel} | ${quiz.questions.size} questions", style = MaterialTheme.typography.labelSmall)
                                }
                                Row {
                                    IconButton(onClick = { editQuiz = quiz }) { Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { viewModel.deleteQuiz(quiz) }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                                }
                            }
                        }
                    }
                    if (quizzes.isEmpty()) {
                        item { Box(Modifier.fillParentMaxSize(), Alignment.Center) { Text("No quizzes yet.", color = Color.Gray) } }
                    }
                }
            }
        }
    }

    if (viewingUrl != null) {
        UrlEmbedDialog(url = viewingUrl!!, onDismiss = { viewingUrl = null })
    }

    if (showDialog != null || editLesson != null) {
        val lessonToEdit = editLesson
        AddContentDialog(
            type = lessonToEdit?.type ?: showDialog!!,
            existingLesson = lessonToEdit,
            onDismiss = { showDialog = null; editLesson = null },
            onConfirm = { 
                if (lessonToEdit != null) viewModel.updateLesson(it) else viewModel.postLesson(it)
                showDialog = null
                editLesson = null
            }
        )
    }

    if (showQuizBuilder || editQuiz != null) {
        QuizBuilderDialog(
            existingQuiz = editQuiz,
            onDismiss = { showQuizBuilder = false; editQuiz = null },
            onConfirm = { 
                if (editQuiz != null) viewModel.updateQuiz(it) else viewModel.postQuiz(it)
                showQuizBuilder = false
                editQuiz = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizBuilderDialog(existingQuiz: Quiz? = null, onDismiss: () -> Unit, onConfirm: (Quiz) -> Unit) {
    var title by remember { mutableStateOf(existingQuiz?.title ?: "") }
    var subject by remember { mutableStateOf(existingQuiz?.subject ?: "") }
    var grade by remember { mutableStateOf(existingQuiz?.gradeLevel ?: "") }
    val questions = remember { mutableStateListOf<Question>().apply { if (existingQuiz != null) addAll(existingQuiz.questions) } }
    var showQuestionDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingQuiz != null) "Edit Quiz" else "Build Automated Quiz") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        text = {
            Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Quiz Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade (e.g. Grade 4)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())

                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Questions (${questions.size})", fontWeight = FontWeight.Bold)

                questions.forEachIndexed { index, q ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Q${index+1}: ${q.questionText}", fontWeight = FontWeight.Medium)
                            Text("Type: ${q.type.name}", fontSize = 10.sp, color = Color.Gray)
                            Text("Correct: ${q.correctAnswers.joinToString(", ")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { questions.removeAt(index) }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("Remove") }
                        }
                    }
                }

                Button(onClick = { showQuestionDialog = true }, modifier = Modifier.padding(top = 16.dp)) {
                    Icon(Icons.Default.Add, null)
                    Text(" Add Multiple Choice Question")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val quiz = Quiz(
                    id = existingQuiz?.id ?: "",
                    title = title, subject = subject, gradeLevel = grade, questions = questions.toList(),
                    teacherId = existingQuiz?.teacherId ?: "",
                    teacherName = existingQuiz?.teacherName ?: "",
                    createdAt = existingQuiz?.createdAt ?: Timestamp.now()
                )
                onConfirm(quiz)
                onDismiss()
            }, enabled = title.isNotEmpty() && questions.isNotEmpty()) { Text(if (existingQuiz != null) "Update Quiz" else "Post Quiz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showQuestionDialog) {
        AddQuestionDialog(onDismiss = { showQuestionDialog = false }, onConfirm = { questions.add(it) })
    }
}

@Composable
fun AddQuestionDialog(onDismiss: () -> Unit, onConfirm: (Question) -> Unit) {
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(QuestionType.MCQ) }
    val options = remember { mutableStateListOf("", "", "", "") }
    val correctAnswers = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Question") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Question Text") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(16.dp))
                Text("Options (Check to mark as correct)", style = MaterialTheme.typography.labelSmall)
                options.forEachIndexed { index, opt ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = correctAnswers.contains(opt) && opt.isNotEmpty(), onCheckedChange = {
                            if (it) {
                                if (type == QuestionType.MCQ) correctAnswers.clear()
                                correctAnswers.add(opt)
                            } else {
                                correctAnswers.remove(opt)
                            }
                        })
                        OutlinedTextField(value = opt, onValueChange = { options[index] = it }, label = { Text("Option ${index+1}") }, modifier = Modifier.weight(1f))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = type == QuestionType.MULTI_SELECT, onCheckedChange = {
                        type = if (it) QuestionType.MULTI_SELECT else QuestionType.MCQ
                        if (!it && correctAnswers.size > 1) {
                            val first = correctAnswers.first()
                            correctAnswers.clear()
                            correctAnswers.add(first)
                        }
                    })
                    Text("Allow Multiple Correct Answers")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = Question(questionText = text, options = options.filter { it.isNotEmpty() }, correctAnswers = correctAnswers.filter { it.isNotEmpty() }.toList(), type = type)
                onConfirm(q)
                onDismiss()
            }, enabled = text.isNotEmpty() && correctAnswers.isNotEmpty()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContentDialog(type: LessonContentType, existingLesson: LessonContent? = null, onDismiss: () -> Unit, onConfirm: (LessonContent) -> Unit) {
    var title by remember { mutableStateOf(existingLesson?.title ?: "") }
    var subject by remember { mutableStateOf(existingLesson?.subject ?: "") }
    var topic by remember { mutableStateOf(existingLesson?.topic ?: "") }
    var grade by remember { mutableStateOf(existingLesson?.gradeLevel ?: "") }
    var url by remember { mutableStateOf(existingLesson?.fileUrl ?: existingLesson?.videoUrl ?: existingLesson?.audioUrl ?: "") }
    var bodyText by remember { mutableStateOf(existingLesson?.notes ?: existingLesson?.practicalTask ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingLesson != null) "Edit ${type.name.replace("_", " ")}" else "Post ${type.name.replace("_", " ")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade Level (e.g. Grade 4)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic") }, modifier = Modifier.fillMaxWidth())

                when (type) {
                    LessonContentType.TYPED_NOTES, LessonContentType.ANNOUNCEMENT -> {
                        OutlinedTextField(value = bodyText, onValueChange = { bodyText = it }, label = { Text("Content/Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                    }
                    LessonContentType.PDF, LessonContentType.SLIDES, LessonContentType.VIDEO_URL, LessonContentType.AUDIO, LessonContentType.IMAGE_BASED -> {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("${type.name} URL") }, modifier = Modifier.fillMaxWidth())
                        Text("Provide a direct link (e.g. Google Drive, YouTube, etc.) to minimize app size.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    LessonContentType.REAL_LIFE_APP -> {
                        OutlinedTextField(value = bodyText, onValueChange = { bodyText = it }, label = { Text("Practical Task Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val lesson = LessonContent(
                    id = existingLesson?.id ?: "",
                    title = title,
                    gradeLevel = grade,
                    subject = subject,
                    topic = topic,
                    type = type,
                    notes = if (type == LessonContentType.TYPED_NOTES || type == LessonContentType.ANNOUNCEMENT) bodyText else null,
                    fileUrl = if (type == LessonContentType.PDF || type == LessonContentType.SLIDES) url else null,
                    videoUrl = if (type == LessonContentType.VIDEO_URL) url else null,
                    audioUrl = if (type == LessonContentType.AUDIO) url else null,
                    practicalTask = if (type == LessonContentType.REAL_LIFE_APP) bodyText else null,
                    teacherId = existingLesson?.teacherId ?: "",
                    teacherName = existingLesson?.teacherName ?: "",
                    createdAt = existingLesson?.createdAt ?: Timestamp.now()
                )
                onConfirm(lesson)
                onDismiss()
            }) { Text(if (existingLesson != null) "Update" else "Post") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun GradingTab(viewModel: TeacherViewModel) {
    val submissions by viewModel.submissions.collectAsState()
    var selectedSubmission by remember { mutableStateOf<AssignmentSubmission?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Ungraded Work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.fillMaxSize()) {
            items(submissions.filter { !it.graded }) { sub ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedSubmission = sub }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PendingActions, null, tint = Color.Magenta)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(sub.studentName, fontWeight = FontWeight.Bold)
                            Text("Assignment ID: ${sub.assignmentId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    selectedSubmission?.let { sub ->
        GradingDialog(sub, onDismiss = { selectedSubmission = null }, onConfirm = { score, feed, comm ->
            viewModel.gradeSubmission(sub.id, score, feed, comm)
        })
    }
}

@Composable
fun GradingDialog(sub: AssignmentSubmission, onDismiss: () -> Unit, onConfirm: (Double, String, String) -> Unit) {
    var score by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grade: ${sub.studentName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Student's URL: ${sub.fileUrl ?: "N/A"}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { /* Open URL */ })
                if (!sub.textSubmission.isNullOrBlank()) Text("Submission: ${sub.textSubmission}", Modifier.padding(vertical = 8.dp))

                OutlinedTextField(value = score, onValueChange = { score = it }, label = { Text("Score (e.g. 85)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = feedback, onValueChange = { feedback = it }, label = { Text("Personalized Feedback (to student)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Private Comment (to parent)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(score.toDoubleOrNull() ?: 0.0, feedback, comment)
                onDismiss()
            }) { Text("Submit Grade") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TeacherStudentsTab(viewModel: TeacherViewModel) {
    val students by viewModel.students.collectAsState()
    var selectedStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var actionType by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        items(students) { s ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.Bold)
                        Text("Grade: ${s.grade}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { selectedStudent = s; actionType = "Badge" }) { Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700)) }
                    IconButton(onClick = { selectedStudent = s; actionType = "Behavior" }) { Icon(Icons.Default.AssignmentLate, null, tint = Color.Red) }
                }
            }
        }
    }

    selectedStudent?.let { s ->
        if (actionType == "Badge") {
            AwardBadgeDialog(s, onDismiss = { selectedStudent = null }, onConfirm = { type, reason -> viewModel.awardBadge(s.id, s.name, type, reason) })
        } else if (actionType == "Behavior") {
            LogBehaviorDialog(s, onDismiss = { selectedStudent = null }, onConfirm = { type, note -> viewModel.logBehavior(s.id, s.name, type, note) })
        }
    }
}

@Composable
fun AwardBadgeDialog(student: StudentRecord, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var type by remember { mutableStateOf("Top Performer") }
    var reason by remember { mutableStateOf("") }
    val badges = listOf("Top Performer", "Most Improved", "Disciplined", "Creative", "Helpful")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Award Badge: ${student.name}") },
        text = {
            Column {
                Text("Select Badge Type")
                badges.forEach { b ->
                    Row(Modifier.fillMaxWidth().clickable { type = b }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == b, onClick = { type = b })
                        Text(b)
                    }
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(type, reason); onDismiss() }) { Text("Award") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun LogBehaviorDialog(student: StudentRecord, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var type by remember { mutableStateOf("Positive") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Behavior: ${student.name}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = type == "Positive", onClick = { type = "Positive" })
                    Text("Positive")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = type == "Needs Improvement", onClick = { type = "Needs Improvement" })
                    Text("Needs Improvement")
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Behavior Note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(type, note); onDismiss() }) { Text("Log") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun VerificationSection(isPending: Boolean, viewModel: TeacherViewModel) {
    var tsc by remember { mutableStateOf("") }
    var idNum by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Verification Required", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (isPending) {
                Text("Your verification is currently being reviewed by admin. Please wait.")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            } else {
                Text("Submit your details to enable content posting features.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = tsc, onValueChange = { tsc = it }, label = { Text("TSC Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = idNum, onValueChange = { idNum = it }, label = { Text("National ID Number") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.submitVerification(tsc, idNum) }, modifier = Modifier.fillMaxWidth(), enabled = tsc.isNotBlank() && idNum.isNotBlank()) {
                    Text("Submit for Verification")
                }
            }
        }
    }
}

@Composable
fun TeacherOverview(viewModel: TeacherViewModel) {
    val profile by viewModel.teacherProfile.collectAsState()
    val points = profile["teacherPoints"] as? Long ?: 0L
    val lessons by viewModel.lessons.collectAsState()
    val submissions by viewModel.submissions.collectAsState()

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    AsyncImage(model = profile["profileImageUrl"], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (profile["profileImageUrl"] == null) Icon(Icons.Default.Person, null)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile["name"]?.toString() ?: "Teacher", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Verified Professional • $points Points", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Materials", lessons.size.toString(), Modifier.weight(1f))
            StatCard("Grading Pending", submissions.count { !it.graded }.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlEmbedDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Resource Viewer", fontSize = 16.sp) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
                )
            }
        ) { padding ->
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}
