package com.joseph.cbchubpro.child.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.joseph.cbchubpro.child.data.model.LessonContent
import com.joseph.cbchubpro.child.data.model.ProfessionalProfile
import com.joseph.cbchubpro.child.data.model.Quiz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TeacherDetailsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _teacher = MutableStateFlow<ProfessionalProfile?>(null)
    val teacher = _teacher.asStateFlow()

    private val _lessons = MutableStateFlow<List<LessonContent>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _quizzes = MutableStateFlow<List<Quiz>>(emptyList())
    val quizzes = _quizzes.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    fun loadTeacherDetails(teacherId: String) {
        if (teacherId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            
            // Fetch Teacher Profile
            db.collection("users").document(teacherId).get()
                .addOnSuccessListener { snapshot ->
                    val prof = snapshot.toObject(ProfessionalProfile::class.java)?.copy(uid = snapshot.id)
                    _teacher.value = prof
                    Log.d("TeacherDetailsVM", "Teacher loaded: ${prof?.name}, Role: ${prof?.role}")
                }
                .addOnFailureListener { e ->
                    Log.e("TeacherDetailsVM", "Error fetching profile: ${e.message}")
                }

            // Fetch Teacher's Lessons
            // We use a SnapshotListener to get real-time updates
            db.collection("lessons")
                .whereEqualTo("teacherId", teacherId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("TeacherDetailsVM", "Error fetching lessons: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { 
                        it.toObject(LessonContent::class.java)?.copy(id = it.id) 
                    } ?: emptyList()
                    
                    Log.d("TeacherDetailsVM", "Fetched ${list.size} lessons for teacher $teacherId")
                    _lessons.value = list.sortedByDescending { it.createdAt }
                    _isLoading.value = false
                }

            // Fetch Teacher's Quizzes
            db.collection("quizzes")
                .whereEqualTo("teacherId", teacherId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("TeacherDetailsVM", "Error fetching quizzes: ${error.message}")
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { 
                        it.toObject(Quiz::class.java)?.copy(id = it.id) 
                    } ?: emptyList()
                    
                    Log.d("TeacherDetailsVM", "Fetched ${list.size} quizzes for teacher $teacherId")
                    _quizzes.value = list.sortedByDescending { it.createdAt }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDetailsScreen(
    teacherId: String,
    onBack: () -> Unit,
    onNavigateToLesson: (String) -> Unit,
    onNavigateToQuiz: (String) -> Unit,
    viewModel: TeacherDetailsViewModel = viewModel()
) {
    val teacher by viewModel.teacher.collectAsState()
    val lessons by viewModel.lessons.collectAsState()
    val quizzes by viewModel.quizzes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(teacherId) {
        viewModel.loadTeacherDetails(teacherId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(teacher?.name ?: "Teacher Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
                    teacher?.let { prof ->
                        TeacherHeader(prof)
                    } ?: Card(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Teacher profile not found", Modifier.padding(16.dp))
                    }
                }

                if (lessons.isNotEmpty()) {
                    item { SectionHeader("Lessons") }
                    items(lessons) { lesson ->
                        LessonItem(lesson) { onNavigateToLesson(lesson.id) }
                    }
                }

                if (quizzes.isNotEmpty()) {
                    item { SectionHeader("Quizzes") }
                    items(quizzes) { quiz ->
                        QuizItem(quiz) { onNavigateToQuiz(quiz.id) }
                    }
                }

                if (lessons.isEmpty() && quizzes.isEmpty()) {
                    item {
                        EmptyState("This teacher hasn't posted any content yet.")
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherHeader(teacher: ProfessionalProfile) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = teacher.profileImageUrl,
                contentDescription = null,
                modifier = Modifier.size(100.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                fallback = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image)
            )
            Spacer(Modifier.height(16.dp))
            Text(teacher.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(teacher.role.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            if (teacher.description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    teacher.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
