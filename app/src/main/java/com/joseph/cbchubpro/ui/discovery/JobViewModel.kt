package com.joseph.cbchubpro.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class BoostLevel {
    NONE,
    HIGHLIGHTED,
    FEATURED,
    TOP_PRIORITY
}

data class JobPost(
    val id: String = "",
    val title: String = "",
    val company: String = "",
    val location: String = "",
    val description: String = "",
    val requirements: String = "",
    val salaryRange: String = "",
    val website: String = "",
    val phoneNumber: String = "",
    val deadline: String = "",
    val imageUrl: String = "",
    val posterId: String = "",
    val posterName: String = "",
    val isVerified: Boolean = false,
    val verificationPending: Boolean = false,
    val boostLevel: BoostLevel = BoostLevel.NONE,
    val boostExpiry: com.google.firebase.Timestamp? = null,
    val createdAt: com.google.firebase.Timestamp? = null
)

class JobViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _jobs = MutableStateFlow<List<JobPost>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _locationQuery = MutableStateFlow("")
    val locationQuery = _locationQuery.asStateFlow()

    val jobs: StateFlow<List<JobPost>> = combine(
        _jobs, _searchQuery, _locationQuery
    ) { list, search, location ->
        list.filter { job ->
            val matchesSearch = search.isEmpty() || 
                job.title.contains(search, ignoreCase = true) || 
                job.company.contains(search, ignoreCase = true)
            val matchesLocation = location.isEmpty() || 
                job.location.contains(location, ignoreCase = true)
            matchesSearch && matchesLocation
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        fetchJobs()
    }

    private fun fetchJobs() {
        db.collection("jobs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                _jobs.value = snapshot?.documents?.mapNotNull { it.toObject(JobPost::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    fun postJob(title: String, company: String, location: String, desc: String, salary: String, reqs: String, phone: String, web: String, deadline: String, logoUrl: String) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "User"
        viewModelScope.launch {
            try {
                val job = JobPost(
                    title = title,
                    company = company,
                    location = location,
                    description = desc,
                    salaryRange = salary,
                    requirements = reqs,
                    phoneNumber = phone,
                    website = web,
                    deadline = deadline,
                    imageUrl = logoUrl,
                    posterId = uid,
                    posterName = name,
                    createdAt = com.google.firebase.Timestamp.now()
                )
                db.collection("jobs").add(job).await()
                _statusMessage.value = "Job posted successfully!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun updateJob(jobId: String, title: String, company: String, location: String, desc: String, salary: String, reqs: String, phone: String, web: String, deadline: String, logoUrl: String) {
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "title" to title,
                    "company" to company,
                    "location" to location,
                    "description" to desc,
                    "salaryRange" to salary,
                    "requirements" to reqs,
                    "phoneNumber" to phone,
                    "website" to web,
                    "deadline" to deadline,
                    "imageUrl" to logoUrl
                )
                db.collection("jobs").document(jobId).update(updates).await()
                _statusMessage.value = "Job updated!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            try {
                db.collection("jobs").document(jobId).delete().await()
                _statusMessage.value = "Job deleted!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun initiateJobVerification(jobId: String, amount: Double) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val paymentRef = db.collection("payment_requests").document()
                val payload = mapOf(
                    "id" to paymentRef.id,
                    "userId" to uid,
                    "amount" to amount,
                    "type" to "JOB_VERIFICATION",
                    "jobId" to jobId,
                    "status" to "PENDING",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                paymentRef.set(payload).await()
                db.collection("jobs").document(jobId).update("verificationPending", true).await()
                _statusMessage.value = "Verification request sent!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun initiateJobBoost(jobId: String, level: BoostLevel, amount: Double) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val paymentRef = db.collection("payment_requests").document()
                val payload = mapOf(
                    "id" to paymentRef.id,
                    "userId" to uid,
                    "amount" to amount,
                    "type" to "JOB_BOOST",
                    "jobId" to jobId,
                    "boostLevel" to level.name,
                    "status" to "PENDING",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                paymentRef.set(payload).await()
                _statusMessage.value = "Boost request sent!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setLocationQuery(query: String) { _locationQuery.value = query }
    fun clearStatusMessage() { _statusMessage.value = null }
}
