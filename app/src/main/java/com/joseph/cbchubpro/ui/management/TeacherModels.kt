package com.joseph.cbchubpro.ui.management

import com.google.firebase.Timestamp
import java.util.UUID

// --- Common Management Models ---

data class SecurityGuard(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val assignedPassword: String = "",
    val schoolId: String = ""
)

data class VisitorRecord(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val idNumber: String = "",
    val carNumber: String = "",
    val purpose: String = "",
    val status: String = "PENDING", // PENDING, CHECKED_IN, CHECKED_OUT
    val entryTime: Timestamp = Timestamp.now(),
    val exitTime: Timestamp? = null,
    val guardId: String = "",
    val guardName: String = "",
    val schoolId: String = ""
)

data class Installment(
    val amount: Double = 0.0,
    val status: String = "PENDING",
    val date: Timestamp = Timestamp.now()
)

data class StudentRecord(
    val id: String = "",
    val name: String = "",
    val grade: String = "",
    val parentName: String = "",
    val parentPhone: String = "",
    val parentEmail: String = "",
    val parentTempPassword: String = "", 
    val feeBalance: Double = 0.0,
    val tuitionFee: Double = 0.0,
    val foodFee: Double = 0.0,
    val transportFee: Double = 0.0,
    val examFee: Double = 0.0,
    val otherFee: Double = 0.0,
    val dynamicFees: Map<String, Double> = emptyMap(),
    val installmentPlan: List<Installment> = emptyList(),
    val feeDetails: Map<String, Double> = emptyMap(),
    val performanceSummary: String = "",
    val schoolId: String = "",
    val busRouteId: String = "",
    val dropOffLat: Double = 0.0,
    val dropOffLng: Double = 0.0
)

data class TeacherRecord(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val grade: String = "",
    val tscNumber: String = "",
    val tempPassword: String = "",
    val schoolId: String = "",
    val baseSalary: Double = 0.0,
    val nssf: Double = 0.0,
    val nhif: Double = 0.0,
    val otherDeductions: Double = 0.0,
    val netSalary: Double = 0.0,
    val subjects: List<String> = emptyList()
)

data class StaffRecord(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val phone: String = "",
    val tempPassword: String = "",
    val schoolId: String = "",
    val baseSalary: Double = 0.0,
    val nssf: Double = 0.0,
    val nhif: Double = 0.0,
    val otherDeductions: Double = 0.0,
    val netSalary: Double = 0.0
)

data class PayrollRecord(
    val id: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val month: String = "",
    val baseSalary: Double = 0.0,
    val nssf: Double = 0.0,
    val nhif: Double = 0.0,
    val otherDeductions: Double = 0.0,
    val netSalary: Double = 0.0,
    val status: String = "PENDING",
    val timestamp: Timestamp = Timestamp.now()
)

data class TransportRecord(
    val id: String = "",
    val busPlate: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val routeName: String = "",
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val bearing: Float = 0.0f,
    val status: String = "IN_TRANSIT",
    val lastUpdate: Timestamp = Timestamp.now()
)

data class GradeRecord(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val subject: String = "",
    val score: String = "",
    val remarks: String = "",
    val teacherId: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class AttendanceRecord(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentGrade: String = "",
    val status: String = "PRESENT", 
    val behaviorNote: String = "", 
    val date: String = "",
    val schoolId: String = "",
    val markedBy: String = ""
)

data class StaffAttendanceRecord(
    val id: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val date: String = "",
    val checkIn: Timestamp? = null,
    val checkOut: Timestamp? = null,
    val schoolId: String = ""
)

data class ExpenseRecord(
    val id: String = "",
    val category: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: Timestamp = Timestamp.now(),
    val schoolId: String = "",
    val mpesaReceipt: String = "",
    val status: String = "PAID" // PENDING, PAID
)

data class MpesaTransaction(
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val amount: Double = 0.0,
    val phoneNumber: String = "",
    val mpesaReceiptNumber: String = "",
    val transactionDate: Timestamp = Timestamp.now(),
    val type: String = "FEE_PAYMENT", // FEE_PAYMENT, EXPENSE
    val status: String = "COMPLETED", // COMPLETED, FAILED, PENDING
    val confirmedBy: String = "",
    val accountReference: String = "" // e.g., Admission Number
)

data class DarajaConfig(
    val consumerKey: String = "",
    val consumerSecret: String = "",
    val passKey: String = "",
    val shortCode: String = "",
    val callbackUrl: String = ""
)

data class SMSPurchase(
    val schoolId: String = "",
    val packageName: String = "",
    val credits: Int = 0,
    val amount: Double = 0.0,
    val paymentPhone: String = "",
    val status: String = "PENDING",
    val timestamp: Timestamp = Timestamp.now()
)

// --- Learning & Communication Models ---

enum class LessonContentType {
    TYPED_NOTES, PDF, SLIDES, IMAGE_BASED, AUDIO, VIDEO_URL, ASSIGNMENT, PROJECT, QUIZ, ANNOUNCEMENT, REAL_LIFE_APP, POLL
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
    val timeLimitMinutes: Int = 0,
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

data class Announcement(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val target: String = "Parents", // "Parents", "Staff", "All"
    val gradeLevel: String = "All",
    val teacherId: String = "",
    val teacherName: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

data class ChatMessage(
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "", // \"teacher\", \"parent\", \"student\"
    val message: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class LearningActivityLog(
    val id: String = "",
    val schoolId: String = "",
    val studentId: String = "",
    val contentId: String = "",
    val contentTitle: String = "",
    val contentType: String = "",
    val timestamp: Timestamp = Timestamp.now()
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
    val type: String = "", // \"Positive\" or \"Needs Improvement\"
    val note: String = "",
    val teacherId: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class EmergencyAlert(
    val id: String = "",
    val schoolId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val busPlate: String = "",
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val timestamp: Timestamp = Timestamp.now(),
    val resolved: Boolean = false
)
