package com.joseph.cbchubpro.ui.management

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChildLearningViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var profileListener: ListenerRegistration? = null
    private var lessonsListener: ListenerRegistration? = null
    private val _grade = MutableStateFlow("")
    val grade = _grade.asStateFlow()
    private val _lessons = MutableStateFlow<List<LessonContent>>(emptyList())
    val lessons = _lessons.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init {
        val uid = auth.currentUser?.uid ?: return
        profileListener = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            val profile = snapshot?.data.orEmpty()
            _grade.value = (profile["gradeLevel"] ?: profile["grade"] ?: "").toString().trim()
            _loading.value = false
        }
        lessonsListener = db.collection("lessons").orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val grade = _grade.value
                _lessons.value = snapshot?.documents?.mapNotNull { doc ->
                    val lesson = doc.toObject(LessonContent::class.java)?.copy(id = doc.id) ?: return@mapNotNull null
                    val target = lesson.gradeLevel.trim()
                    lesson.takeIf { target.equals("All", true) || (grade.isNotBlank() && target.equals(grade, true)) }
                } ?: emptyList()
            }
    }

    fun recordOpen(lesson: LessonContent) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                db.collection("users").document(uid).collection("interactions").add(
                    InteractionRecord(teacherId = lesson.teacherId, teacherName = lesson.teacherName,
                        contentId = lesson.id, contentTitle = lesson.title, contentType = lesson.type.name)
                ).await()
            }
        }
    }

    override fun onCleared() {
        profileListener?.remove()
        lessonsListener?.remove()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildLearningScreen(onLogoutClicked: () -> Unit, viewModel: ChildLearningViewModel = viewModel()) {
    val grade by viewModel.grade.collectAsState()
    val lessons by viewModel.lessons.collectAsState()
    val loading by viewModel.loading.collectAsState()
    var selectedLesson by remember { mutableStateOf<LessonContent?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("My Learning", fontWeight = FontWeight.Bold) }, actions = {
            IconButton(onClick = onLogoutClicked) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(if (grade.isBlank()) "Learning content" else "Content for $grade", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                grade.isBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Ask your parent to add your grade so we can show lessons for your level.")
                }
                lessons.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No lessons are available for your grade yet.")
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(lessons, key = { it.id }) { lesson ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                            viewModel.recordOpen(lesson)
                            selectedLesson = lesson
                        }) {
                            ListItem(
                                leadingContent = { Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary) },
                                headlineContent = { Text(lesson.title, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("${lesson.subject} · ${lesson.topic} · ${lesson.teacherName}") },
                                trailingContent = { Icon(Icons.Default.PlayArrow, "Open lesson") }
                            )
                            lesson.notes?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) }
                        }
                    }
                }
            }
        }
    }
    selectedLesson?.let { lesson ->
        val resource = lesson.videoUrl ?: lesson.audioUrl ?: lesson.fileUrl ?: lesson.imageUrl
        if (!resource.isNullOrBlank()) UrlEmbedDialog(url = resource, onDismiss = { selectedLesson = null })
        else AlertDialog(onDismissRequest = { selectedLesson = null }, title = { Text(lesson.title) },
            text = { Text(lesson.notes ?: lesson.practicalTask ?: "This lesson has no text content.") },
            confirmButton = { TextButton(onClick = { selectedLesson = null }) { Text("Close") } })
    }
}
