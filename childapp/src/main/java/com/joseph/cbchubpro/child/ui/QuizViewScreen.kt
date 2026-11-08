package com.joseph.cbchubpro.child.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Timer
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.joseph.cbchubpro.child.data.model.Question
import com.joseph.cbchubpro.child.data.model.QuestionType
import com.joseph.cbchubpro.child.data.model.Quiz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class QuizViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _quiz = MutableStateFlow<Quiz?>(null)
    val quiz = _quiz.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isSubmitted = MutableStateFlow(false)
    val isSubmitted = _isSubmitted.asStateFlow()

    private val _quizResult = MutableStateFlow<QuizSummary?>(null)
    val quizResult = _quizResult.asStateFlow()

    fun loadQuiz(quizId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = db.collection("quizzes").document(quizId).get().await()
                _quiz.value = snapshot.toObject(Quiz::class.java)?.copy(id = snapshot.id)
            } catch (e: Exception) {
                Log.e("QuizVM", "Error loading quiz: ${e.message}")
            } finally {
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
        db.collection("users").document(uid).collection("interactions").add(interaction).await()
    }

    fun submitQuiz(answers: Map<String, List<String>>) {
        val currentQuiz = _quiz.value ?: return
        val uid = auth.currentUser?.uid ?: return
        
        var totalPointsEarned = 0
        var correctCount = 0
        
        currentQuiz.questions.forEach { question ->
            val userAns = answers[question.id]?.sorted() ?: emptyList()
            val correctAns = question.correctAnswers.sorted()
            
            if (userAns == correctAns) {
                correctCount++
                totalPointsEarned += question.points
            }
        }

        _quizResult.value = QuizSummary(
            score = correctCount,
            totalQuestions = currentQuiz.questions.size,
            pointsEarned = totalPointsEarned
        )

        viewModelScope.launch {
            try {
                // Reward student with points
                db.collection("users").document(uid).update("pointsBalance", FieldValue.increment(totalPointsEarned.toLong()))
                
                // Log interaction for parent dashboard
                logInteraction(
                    teacherId = currentQuiz.teacherId,
                    teacherName = currentQuiz.teacherName,
                    contentId = currentQuiz.id,
                    contentTitle = currentQuiz.title,
                    type = "QUIZ",
                    points = totalPointsEarned.toLong()
                )

                // Log the attempt
                val attempt = mapOf(
                    "quizId" to currentQuiz.id,
                    "studentId" to uid,
                    "score" to correctCount,
                    "totalQuestions" to currentQuiz.questions.size,
                    "pointsEarned" to totalPointsEarned,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                db.collection("quiz_attempts").add(attempt).await()
                _isSubmitted.value = true
            } catch (e: Exception) {
                Log.e("QuizVM", "Error saving result: ${e.message}")
            }
        }
    }
}

data class QuizSummary(val score: Int, val totalQuestions: Int, val pointsEarned: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizViewScreen(
    quizId: String,
    onBack: () -> Unit,
    viewModel: QuizViewModel = viewModel()
) {
    val quiz by viewModel.quiz.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSubmitted by viewModel.isSubmitted.collectAsState()
    val result by viewModel.quizResult.collectAsState()

    val selectedAnswers = remember { mutableStateMapOf<String, List<String>>() }

    LaunchedEffect(quizId) {
        viewModel.loadQuiz(quizId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quiz?.title ?: "Interactive Quiz") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8F9FA))) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (isSubmitted && result != null) {
                QuizSummaryView(result!!, onBack)
            } else {
                quiz?.let { q ->
                    Column {
                        // Progress Header
                        LinearProgressIndicator(
                            progress = { selectedAnswers.size.toFloat() / q.questions.size.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = Color.LightGray
                        )
                        
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Complete all questions to earn points!", fontSize = 14.sp)
                                    }
                                }
                            }

                            itemsIndexed(q.questions) { index, question ->
                                QuestionCard(
                                    index = index,
                                    question = question,
                                    selectedOptions = selectedAnswers[question.id] ?: emptyList(),
                                    onOptionSelected = { option ->
                                        val current = selectedAnswers[question.id] ?: emptyList()
                                        if (question.type == QuestionType.MCQ) {
                                            selectedAnswers[question.id] = listOf(option)
                                        } else {
                                            if (current.contains(option)) {
                                                selectedAnswers[question.id] = current - option
                                            } else {
                                                selectedAnswers[question.id] = current + option
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.submitQuiz(selectedAnswers) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selectedAnswers.size == q.questions.size
                        ) {
                            Text("Finish Quiz & Collect Points", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionCard(
    index: Int,
    question: Question,
    selectedOptions: List<String>,
    onOptionSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text("Question", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            Spacer(Modifier.height(8.dp))
            Text(question.questionText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            
            if (!question.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = question.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(16.dp))
            
            question.options.forEach { option ->
                val isSelected = selectedOptions.contains(option)
                Surface(
                    onClick = { onOptionSelected(option) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF1F3F4),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (question.type == QuestionType.MCQ) {
                            RadioButton(selected = isSelected, onClick = null)
                        } else {
                            Checkbox(checked = isSelected, onCheckedChange = null)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(option, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun QuizSummaryView(result: QuizSummary, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn()
        ) {
            Icon(
                Icons.Default.EmojiEvents, 
                contentDescription = null, 
                tint = Color(0xFFFFB300), 
                modifier = Modifier.size(120.dp)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        Text("AWESOME WORK!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text("Quiz Completed Successfully", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        
        Spacer(Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your Score", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Text("${result.score} / ${result.totalQuestions}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFE8F5E9), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("REWARD EARNED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Text("+${result.pointsEarned} Stars Added", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Back to Lessons", fontWeight = FontWeight.Bold)
        }
    }
}
