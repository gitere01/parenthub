package com.joseph.cbchubpro.child.data.model

import com.google.firebase.Timestamp
import java.util.UUID

// --- Teacher Specific Learning Models (Unified with Main App) ---

enum class LessonContentType {
    TYPED_NOTES,
    PDF,
    SLIDES,
    IMAGE_BASED,
    AUDIO,
    VIDEO_URL,
    ASSIGNMENT,
    PROJECT,
    QUIZ,
    ANNOUNCEMENT,
    REAL_LIFE_APP,
    POLL
}

enum class QuestionType { MCQ, MULTI_SELECT, SHORT_ANSWER }

data class Question(
    val id: String = UUID.randomUUID().toString(),
    val questionText: String = "",
    val options: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList(),
    val type: QuestionType = QuestionType.MCQ, 
    val imageUrl: String? = null,
    val points: Int = 1
)

data class Quiz(
    val id: String = "",
    val title: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val gradeLevel: String = "",
    val subject: String = "",
    val topic: String = "",
    val questions: List<Question> = emptyList(),
    val autoMarkingEnabled: Boolean = true,
    val createdAt: Timestamp = Timestamp.now()
)

data class LessonContent(
    val id: String = "",
    val title: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val gradeLevel: String = "",
    val subject: String = "",
    val topic: String = "",
    val type: LessonContentType = LessonContentType.TYPED_NOTES,
    val notes: String? = null,
    val fileUrl: String? = null, 
    val imageUrl: String? = null,
    val videoUrl: String? = null, 
    val audioUrl: String? = null,
    val practicalTask: String? = null, 
    val pollOptions: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)

data class AssignmentSubmission(
    val id: String = "",
    val assignmentId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val fileUrl: String? = null, 
    val textSubmission: String? = null,
    val teacherComment: String? = null,
    val personalizedFeedback: String? = null,
    val score: Double? = null,
    val submittedAt: Timestamp = Timestamp.now(),
    val graded: Boolean = false
)

data class ProfessionalProfile(
    val uid: String = "",
    val name: String = "",
    val role: String = "",
    val description: String = "",
    val profileImageUrl: String = "",
    val isVerified: Boolean = false
)

data class StudentBadge(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val badgeType: String = "", 
    val reason: String = "",
    val teacherId: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class BehaviorLog(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val type: String = "Positive",
    val note: String = "",
    val teacherId: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
