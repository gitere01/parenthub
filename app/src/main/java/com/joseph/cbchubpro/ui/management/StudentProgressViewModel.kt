package com.joseph.cbchubpro.ui.management

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LinkedStudent(
    val studentId: String = "",
    val name: String = "",
    val grade: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val feeBalance: Double = 0.0,
    val attendance: List<AttendanceRecord> = emptyList(),
    val grades: List<GradeRecord> = emptyList()
)

class StudentProgressViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _linkedStudents = MutableStateFlow<List<LinkedStudent>>(emptyList())
    val linkedStudents = _linkedStudents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        fetchLinkedStudents()
    }

    fun fetchLinkedStudents() {
        val user = auth.currentUser ?: return
        val email = user.email ?: ""
        // Use email or phone to find links. Since we used phone for verification, let's look for both.
        // For simplicity in this step, we'll check 'student_links' collection.
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val links = db.collection("student_links")
                    .whereEqualTo("parentEmail", email)
                    .get().await()

                val studentList = mutableListOf<LinkedStudent>()
                
                for (doc in links.documents) {
                    val sId = doc.getString("studentId") ?: ""
                    val schId = doc.getString("schoolId") ?: ""
                    
                    val studentDoc = db.collection("schools").document(schId)
                        .collection("students").document(sId).get().await()
                    
                    val schoolDoc = db.collection("users").document(schId).get().await()
                    val schoolName = schoolDoc.getString("schoolName") ?: "Unknown School"

                    if (studentDoc.exists()) {
                        val student = studentDoc.toObject(StudentRecord::class.java)
                        if (student != null) {
                            // Fetch Attendance
                            val attSnapshot = db.collection("schools").document(schId)
                                .collection("attendance").whereEqualTo("studentId", sId)
                                .orderBy("date", Query.Direction.DESCENDING).limit(10).get().await()
                            val attendance = attSnapshot.toObjects(AttendanceRecord::class.java)

                            // Fetch Grades
                            val gradeSnapshot = db.collection("schools").document(schId)
                                .collection("grades").whereEqualTo("studentId", sId)
                                .orderBy("timestamp", Query.Direction.DESCENDING).get().await()
                            val grades = gradeSnapshot.toObjects(GradeRecord::class.java)

                            studentList.add(LinkedStudent(
                                studentId = sId,
                                name = student.name,
                                grade = student.grade,
                                schoolId = schId,
                                schoolName = schoolName,
                                feeBalance = student.feeBalance,
                                attendance = attendance,
                                grades = grades
                            ))
                        }
                    }
                }
                _linkedStudents.value = studentList
            } catch (e: Exception) {
                Log.e("StudentProgress", "Error fetching data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun linkStudentByPhone(phone: String) {
        val user = auth.currentUser ?: return
        val parentEmail = user.email ?: return
        
        viewModelScope.launch {
            try {
                // Search for students across all schools with this parent phone
                // Note: This is an expensive operation without a top-level student index.
                // We'll assume a collection 'students_by_phone' exists or use a collection group query.
                val studentsQuery = db.collectionGroup("students")
                    .whereEqualTo("parentPhone", phone)
                    .get().await()

                if (studentsQuery.isEmpty) {
                    Log.d("LinkStudent", "No student found with phone $phone")
                    return@launch
                }

                for (doc in studentsQuery.documents) {
                    val schoolId = doc.reference.parent.parent?.id ?: ""
                    val studentId = doc.id
                    
                    val linkData = mapOf(
                        "parentEmail" to parentEmail,
                        "studentId" to studentId,
                        "schoolId" to schoolId,
                        "parentPhone" to phone,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    
                    db.collection("student_links").add(linkData).await()
                }
                fetchLinkedStudents()
            } catch (e: Exception) {
                Log.e("LinkStudent", "Error linking student", e)
            }
        }
    }
}
