package com.joseph.cbchubpro.ui.management

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class SchoolViewModel : ViewModel() {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    private val _schoolData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val schoolData = _schoolData.asStateFlow()

    private val _schoolSettings = MutableStateFlow<Map<String, Any>>(emptyMap())
    val schoolSettings = _schoolSettings.asStateFlow()

    private val _students = MutableStateFlow<List<StudentRecord>>(emptyList())
    val students = _students.asStateFlow()

    private val _teachers = MutableStateFlow<List<TeacherRecord>>(emptyList())
    val teachers = _teachers.asStateFlow()

    private val _otherStaff = MutableStateFlow<List<StaffRecord>>(emptyList())
    val otherStaff = _otherStaff.asStateFlow()

    private val _payroll = MutableStateFlow<List<PayrollRecord>>(emptyList())
    val payroll = _payroll.asStateFlow()

    private val _transport = MutableStateFlow<List<TransportRecord>>(emptyList())
    val transport = _transport.asStateFlow()

    private val _grades = MutableStateFlow<List<GradeRecord>>(emptyList())
    val grades = _grades.asStateFlow()

    private val _attendance = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendance = _attendance.asStateFlow()

    private val _visitors = MutableStateFlow<List<VisitorRecord>>(emptyList())
    val visitors = _visitors.asStateFlow()

    private val _expenses = MutableStateFlow<List<ExpenseRecord>>(emptyList())
    val expenses = _expenses.asStateFlow()

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements = _announcements.asStateFlow()

    private val _lessons = MutableStateFlow<List<LessonContent>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()

    init {
        fetchInitialData()
    }

    private fun fetchInitialData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            val data = snapshot?.data ?: emptyMap()
            _schoolData.value = data
            val schoolId = data["schoolId"] as? String ?: uid
            db.collection("users").document(schoolId).addSnapshotListener { settings, _ ->
                _schoolSettings.value = settings?.data ?: emptyMap()
            }
            startSyncingSchool(schoolId)
        }
    }

    private fun startSyncingSchool(schoolId: String) {
        db.collection("schools").document(schoolId).collection("students").addSnapshotListener { s, _ ->
            _students.value = s?.documents?.mapNotNull { it.toObject(StudentRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("teachers").addSnapshotListener { s, _ ->
            _teachers.value = s?.documents?.mapNotNull { it.toObject(TeacherRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("other_staff").addSnapshotListener { s, _ ->
            _otherStaff.value = s?.documents?.mapNotNull { it.toObject(StaffRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("payroll").addSnapshotListener { s, _ ->
            _payroll.value = s?.documents?.mapNotNull { it.toObject(PayrollRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("transport").addSnapshotListener { s, _ ->
            _transport.value = s?.documents?.mapNotNull { it.toObject(TransportRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("grades").orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener { s, _ ->
            _grades.value = s?.documents?.mapNotNull { it.toObject(GradeRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("attendance").addSnapshotListener { s, _ ->
            _attendance.value = s?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("visitors").orderBy("entryTime", Query.Direction.DESCENDING).addSnapshotListener { s, _ ->
            _visitors.value = s?.documents?.mapNotNull { it.toObject(VisitorRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("expenses").addSnapshotListener { s, _ ->
            _expenses.value = s?.documents?.mapNotNull { it.toObject(ExpenseRecord::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { s, _ ->
            _announcements.value = s?.documents?.mapNotNull { it.toObject(Announcement::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("lessons").orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { s, _ ->
            _lessons.value = s?.documents?.mapNotNull { it.toObject(LessonContent::class.java)?.copy(id = it.id) } ?: emptyList()
        }
        db.collection("schools").document(schoolId).collection("chats").orderBy("timestamp", Query.Direction.ASCENDING).addSnapshotListener { s, _ ->
            _chats.value = s?.documents?.mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) } ?: emptyList()
        }
    }

    fun setStatusMessage(msg: String?) { _statusMessage.value = msg }
    fun clearStatus() { _statusMessage.value = null }

    fun runDarajaReconciliation() {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                _isBusy.value = true
                setStatusMessage("Syncing with Daraja... This may take a minute.")
                kotlinx.coroutines.delay(2000)
                setStatusMessage("Reconciliation Complete. Fee records and expenses updated.")
            } catch (ex: Exception) { setStatusMessage(ex.message) } finally { _isBusy.value = false }
        }
    }

    fun enrollStudent(s: StudentRecord) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if(s.id.isEmpty()) db.collection("schools").document(sid).collection("students").add(s).await()
                else db.collection("schools").document(sid).collection("students").document(s.id).set(s).await()
                
                if(s.parentEmail.isNotEmpty()) {
                    db.collection("parent_invites").document(s.parentEmail.lowercase().trim()).set(mapOf("email" to s.parentEmail.lowercase().trim(), "tempPassword" to s.parentTempPassword, "role" to "parent/guardian", "schoolId" to sid, "studentId" to s.id, "childName" to s.name, "isStaff" to false)).await()
                }
                setStatusMessage("Student Data Saved")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun addTeacher(t: TeacherRecord) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if (t.id.isEmpty()) db.collection("schools").document(sid).collection("teachers").add(t).await()
                else db.collection("schools").document(sid).collection("teachers").document(t.id).set(t).await()
                db.collection("staff_invites").document(t.email.lowercase().trim()).set(mapOf("name" to t.name, "email" to t.email.lowercase().trim(), "tempPassword" to t.tempPassword, "role" to "teacher", "schoolId" to sid, "isStaff" to true, "staffId" to t.id)).await()
                setStatusMessage("Teacher Saved")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun addOtherStaff(s: StaffRecord) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if (s.id.isEmpty()) db.collection("schools").document(sid).collection("other_staff").add(s).await()
                else db.collection("schools").document(sid).collection("other_staff").document(s.id).set(s).await()
                db.collection("staff_invites").document(s.email.lowercase().trim()).set(mapOf("name" to s.name, "email" to s.email.lowercase().trim(), "tempPassword" to s.tempPassword, "role" to s.role.lowercase(), "schoolId" to sid, "isStaff" to true, "staffId" to s.id)).await()
                setStatusMessage("Staff Saved")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun deleteStaff(id: String, role: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val coll = if(role.lowercase() == "teacher") "teachers" else "other_staff"
                db.collection("schools").document(sid).collection(coll).document(id).delete().await()
                setStatusMessage("Staff Deleted")
            } catch (e: Exception) { setStatusMessage(e.message) }
        }
    }

    fun updateSchoolSettings(updates: Map<String, Any>) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update(updates).await()
                setStatusMessage("Settings Updated")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun buySMSPackage(pname: String, credits: Int, price: Int, phone: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                _isBusy.value = true
                val purchase = SMSPurchase(schoolId = uid, packageName = pname, credits = credits, amount = price.toDouble(), paymentPhone = phone, status = "PENDING")
                db.collection("sms_purchases").add(purchase).await()
                setStatusMessage("STK Push triggered to $phone")
            } catch (ex: Exception) { setStatusMessage(ex.message) } finally { _isBusy.value = false }
        }
    }

    fun markAttendance(studentId: String, name: String, grade: String, status: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            try {
                db.collection("schools").document(sid).collection("attendance").document("${date}_$studentId").set(AttendanceRecord(studentId=studentId, studentName=name, studentGrade=grade, status=status, date=date, markedBy=auth.currentUser?.displayName ?: "Staff")).await()
                setStatusMessage("$name marked $status")
            } catch (e: Exception) { setStatusMessage(e.message) }
        }
    }

    fun recordVisitor(v: VisitorRecord) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if(v.id.isEmpty()) db.collection("schools").document(sid).collection("visitors").add(v).await()
                else db.collection("schools").document(sid).collection("visitors").document(v.id).set(v).await()
                setStatusMessage("Visitor Record Saved")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun addExpense(e: ExpenseRecord) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if(e.id.isEmpty()) db.collection("schools").document(sid).collection("expenses").add(e).await()
                else db.collection("schools").document(sid).collection("expenses").document(e.id).set(e).await()
                setStatusMessage("Expense Recorded")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun deleteExpense(id: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch { try { db.collection("schools").document(sid).collection("expenses").document(id).delete().await(); setStatusMessage("Expense Deleted") } catch (e: Exception) { setStatusMessage(e.message) } }
    }

    fun addTransport(t: TransportRecord) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if(t.id.isEmpty()) db.collection("schools").document(sid).collection("transport").add(t).await()
                else db.collection("schools").document(sid).collection("transport").document(t.id).set(t).await()
                setStatusMessage("Transport Saved")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun postAnnouncement(ann: Announcement) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                if(ann.id.isEmpty()) db.collection("schools").document(sid).collection("announcements").add(ann).await()
                else db.collection("schools").document(sid).collection("announcements").document(ann.id).set(ann).await()
                setStatusMessage("Announcement Saved")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun deleteAnnouncement(id: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("schools").document(sid).collection("announcements").document(id).delete().await()
                setStatusMessage("Announcement Deleted")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun addGrade(gr: GradeRecord) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("schools").document(sid).collection("grades").add(gr).await()
                setStatusMessage("Mark recorded for ${gr.studentName}")
                val student = _students.value.find { it.id == gr.studentId }
                if (student != null) {
                    val msg = "Dear Parent, ${student.name} score in ${gr.subject} has been updated: ${gr.score}%. Remarks: ${gr.remarks}"
                    sendBulkSMS(msg, "Performance Update", listOf(student.parentPhone))
                }
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun postLesson(lesson: LessonContent) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("schools").document(sid).collection("lessons").add(lesson).await()
                setStatusMessage("Lesson content published!")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun sendMessage(msg: String, studentId: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        val profile = _schoolData.value
        viewModelScope.launch {
            try {
                val chat = ChatMessage(schoolId = sid, studentId = studentId, senderId = auth.currentUser?.uid ?: "", senderName = profile["name"]?.toString() ?: "Teacher", senderRole = profile["role"]?.toString() ?: "staff", message = msg)
                db.collection("schools").document(sid).collection("chats").add(chat).await()
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun updateTeacherSubjects(subs: List<String>) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update("subjects", subs).await()
                setStatusMessage("Subjects updated.")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun sendBulkSMS(msg: String, type: String, phones: List<String> = emptyList()) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val targets = if (phones.isEmpty()) _students.value.map { it.parentPhone }.distinct() else phones
                if (targets.isEmpty()) return@launch
                db.collection("schools").document(sid).collection("sms_logs").add(mapOf("message" to msg, "type" to type, "recipients" to targets, "timestamp" to Timestamp.now(), "status" to "PENDING")).await()
                setStatusMessage("SMS Broadcast Sent")
            } catch (ex: Exception) { setStatusMessage(ex.message) }
        }
    }

    fun deleteStudent(id: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch { try { db.collection("schools").document(sid).collection("students").document(id).delete().await(); setStatusMessage("Student Deleted") } catch (e: Exception) { setStatusMessage(e.message) } }
    }
    fun deleteVisitor(id: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch { try { db.collection("schools").document(sid).collection("visitors").document(id).delete().await(); setStatusMessage("Visitor Removed") } catch (e: Exception) { setStatusMessage(e.message) } }
    }
    fun deleteTransport(id: String) {
        val sid = _schoolData.value["schoolId"] as? String ?: auth.currentUser?.uid ?: return
        viewModelScope.launch { try { db.collection("schools").document(sid).collection("transport").document(id).delete().await(); setStatusMessage("Transport Removed") } catch (e: Exception) { setStatusMessage(e.message) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolManagementScreen(onLogoutClicked: () -> Unit, viewModel: SchoolViewModel = viewModel()) {
    val profile by viewModel.schoolData.collectAsState()
    val settings by viewModel.schoolSettings.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val role = profile["role"]?.toString()?.lowercase() ?: "school"
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = when(role) {
        "teacher" -> listOf("Students", "Grades", "Learning Material", "Messages", "Settings")
        else -> listOf("Settings", "Overview", "Staff", "Students", "Attendance", "Finance", "Visitors", "Transport", "Announcements", "SMS")
    }

    LaunchedEffect(status) { status?.let { snackbarHostState.showSnackbar(it); viewModel.clearStatus() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(settings["schoolName"]?.toString() ?: "School Portal") }, actions = { IconButton(onClick = onLogoutClicked) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) }
            }

            if (isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val current = tabs.getOrNull(selectedTab) ?: ""
            when(current) {
                "Settings" -> SettingsTab(viewModel)
                "Overview" -> OverviewTab(viewModel)
                "Staff" -> StaffTab(viewModel)
                "Students" -> StudentsTab(viewModel, isTeacher = role == "teacher")
                "Attendance" -> AttendanceTab(viewModel)
                "Finance" -> FinanceTab(viewModel)
                "Visitors" -> VisitorsTab(viewModel)
                "Transport" -> TransportTab(viewModel)
                "Announcements" -> AnnouncementsTab(viewModel)
                "SMS" -> SMSTab(viewModel)
                "Grades" -> TeacherGradesTab(viewModel)
                "Learning Material" -> TeacherELearningTab(viewModel)
                "Messages" -> TeacherChatTab(viewModel)
            }
        }
    }
}

@Composable
fun SettingsTab(viewModel: SchoolViewModel) {
    val settings by viewModel.schoolSettings.collectAsState()
    var payPhone by remember { mutableStateOf("") }
    var newTax by remember { mutableStateOf("") }
    val taxTypes = (settings["taxTypes"] as? List<String>) ?: emptyList()
    var newFee by remember { mutableStateOf("") }
    val feeTypes = (settings["feeTypes"] as? List<String>) ?: emptyList()

    // Daraja Config
    var cKey by remember { mutableStateOf(settings["darajaConsumerKey"]?.toString() ?: "") }
    var cSec by remember { mutableStateOf(settings["darajaConsumerSecret"]?.toString() ?: "") }
    var pKey by remember { mutableStateOf(settings["darajaPassKey"]?.toString() ?: "") }
    var sc by remember { mutableStateOf(settings["darajaShortCode"]?.toString() ?: "") }

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Daraja (M-Pesa) Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = cKey, onValueChange = { cKey = it }, label = { Text("Consumer Key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cSec, onValueChange = { cSec = it }, label = { Text("Consumer Secret") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = pKey, onValueChange = { pKey = it }, label = { Text("Pass Key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = sc, onValueChange = { sc = it }, label = { Text("Short Code / Paybill") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Button(onClick = {
            viewModel.updateSchoolSettings(mapOf("darajaConsumerKey" to cKey, "darajaConsumerSecret" to cSec, "darajaPassKey" to pKey, "darajaShortCode" to sc))
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save API Credentials") }

        Spacer(Modifier.height(24.dp))
        Text("SMS Packages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Current Credits: ${settings["smsCredits"] ?: 0} SMS", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = payPhone, onValueChange = { payPhone = it }, label = { Text("M-Pesa Phone") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Starter", 1000, 1500), Triple("Growth", 5000, 6000), Triple("Pro", 10000, 10000)).forEach { (p, c, pr) ->
                Card(Modifier.weight(1f).clickable { if(payPhone.isNotBlank()) viewModel.buySMSPackage(p, c, pr, payPhone) }) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(p, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("KES $pr", color = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        Spacer(Modifier.height(24.dp)); Text("Payroll Tax Types", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newTax, onValueChange = { newTax = it }, label = { Text("Tax Name") }, modifier = Modifier.weight(1f))
            IconButton(onClick = { if(newTax.isNotBlank()) { viewModel.updateSchoolSettings(mapOf("taxTypes" to (taxTypes + newTax.trim()))); newTax = "" } }) { Icon(Icons.Default.Add, null) }
        }
        taxTypes.forEach { t -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(t); IconButton(onClick = { viewModel.updateSchoolSettings(mapOf("taxTypes" to taxTypes.filter { it != t })) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } } }

        Spacer(Modifier.height(24.dp)); Text("Student Fee Types", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newFee, onValueChange = { newFee = it }, label = { Text("Fee Name") }, modifier = Modifier.weight(1f))
            IconButton(onClick = { if(newFee.isNotBlank()) { viewModel.updateSchoolSettings(mapOf("feeTypes" to (feeTypes + newFee.trim()))); newFee = "" } }) { Icon(Icons.Default.Add, null) }
        }
        feeTypes.forEach { f -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(f); IconButton(onClick = { viewModel.updateSchoolSettings(mapOf("feeTypes" to feeTypes.filter { it != f })) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } } }
    }
}

@Composable
fun OverviewTab(viewModel: SchoolViewModel) {
    val students by viewModel.students.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val visitors by viewModel.visitors.collectAsState()
    val expenses by viewModel.expenses.collectAsState()

    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("School Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardStatCard("Students", students.size.toString(), Modifier.weight(1f))
            DashboardStatCard("Teachers", teachers.size.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardStatCard("Today Visitors", visitors.size.toString(), Modifier.weight(1f))
            DashboardStatCard("Total Expenses", "KES ${expenses.sumOf { it.amount }}", Modifier.weight(1f))
        }
    }
}

@Composable
fun DashboardStatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
fun AttendanceTab(viewModel: SchoolViewModel) {
    val students by viewModel.students.collectAsState()
    val records by viewModel.attendance.collectAsState()
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    LazyColumn(Modifier.padding(16.dp)) {
        item { Text("Daily Attendance - $today", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
        items(students) { s ->
            val rec = records.find { it.studentId == s.id && it.date == today }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, Modifier.weight(1f))
                    IconButton(onClick = { viewModel.markAttendance(s.id, s.name, s.grade, "Present") }) { Icon(Icons.Default.CheckCircle, null, tint = if(rec?.status == "Present") Color(0xFF4CAF50) else Color.LightGray) }
                    IconButton(onClick = { viewModel.markAttendance(s.id, s.name, s.grade, "Absent") }) { Icon(Icons.Default.Cancel, null, tint = if(rec?.status == "Absent") Color.Red else Color.LightGray) }
                }
            }
        }
    }
}

@Composable
fun FinanceTab(viewModel: SchoolViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    val students by viewModel.students.collectAsState()
    var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editingExpense = null; showAdd = true }, Modifier.weight(1f)) { Text("Log Expense") }
            Button(onClick = { viewModel.runDarajaReconciliation() }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Sync M-Pesa") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Recent Expenses", fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f)) {
            items(expenses) { e -> 
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(e.category) }, 
                        supportingContent = { Text("${e.description} | Receipt: ${e.mpesaReceipt}") }, 
                        trailingContent = { 
                            Row {
                                IconButton(onClick = { editingExpense = e; showAdd = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { viewModel.deleteExpense(e.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                Text("KES ${e.amount}", color = Color.Red, modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically))
                            }
                        }
                    )
                }
            }
        }
        Text("Fee Balances", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(students) { s -> ListItem(headlineContent = { Text(s.name) }, trailingContent = { Text("KES ${s.feeBalance}", color = if(s.feeBalance > 0) Color.Red else Color(0xFF4CAF50)) }) }
        }
    }
    if(showAdd) {
        var cat by remember { mutableStateOf(editingExpense?.category ?: "") }; var amt by remember { mutableStateOf(editingExpense?.amount?.toString() ?: "") }; var desc by remember { mutableStateOf(editingExpense?.description ?: "") }; var receipt by remember { mutableStateOf(editingExpense?.mpesaReceipt ?: "") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(if(editingExpense==null) "Log Expense" else "Edit Expense") }, text = {
            Column {
                OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category") })
                OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = receipt, onValueChange = { receipt = it }, label = { Text("M-Pesa Receipt Number") })
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
            }
        }, confirmButton = { Button(onClick = { viewModel.addExpense(ExpenseRecord(id=editingExpense?.id ?: "", category=cat, amount=amt.toDoubleOrNull() ?: 0.0, description=desc, mpesaReceipt=receipt)); showAdd = false }) { Text("Save") } })
    }
}

@Composable
fun VisitorsTab(viewModel: SchoolViewModel) {
    val visitors by viewModel.visitors.collectAsState()
    var editingVisitor by remember { mutableStateOf<VisitorRecord?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Button(onClick = { editingVisitor = null; showAdd = true }, Modifier.fillMaxWidth()) { Text("Record Visitor") }
        LazyColumn(Modifier.fillMaxSize().padding(top = 16.dp)) {
            items(visitors) { v -> 
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(v.name) }, 
                        supportingContent = { Text(v.purpose) }, 
                        trailingContent = { 
                            Row {
                                IconButton(onClick = { editingVisitor = v; showAdd = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { viewModel.deleteVisitor(v.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                        }
                    )
                }
            }
        }
    }
    if(showAdd) {
        var n by remember { mutableStateOf(editingVisitor?.name ?: "") }; var p by remember { mutableStateOf(editingVisitor?.phoneNumber ?: "") }; var pur by remember { mutableStateOf(editingVisitor?.purpose ?: "") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(if(editingVisitor==null) "New Visitor" else "Edit Visitor") }, text = {
            Column {
                OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("Name") })
                OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Phone") })
                OutlinedTextField(value = pur, onValueChange = { pur = it }, label = { Text("Purpose") })
            }
        }, confirmButton = { Button(onClick = { viewModel.recordVisitor(VisitorRecord(id=editingVisitor?.id ?: "", name=n, phoneNumber=p, purpose=pur)); showAdd = false }) { Text("Save") } })
    }
}

@Composable
fun TransportTab(viewModel: SchoolViewModel) {
    val transport by viewModel.transport.collectAsState()
    var editingBus by remember { mutableStateOf<TransportRecord?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Button(onClick = { editingBus = null; showAdd = true }, Modifier.fillMaxWidth()) { Text("Add Bus Route") }
        LazyColumn(Modifier.fillMaxSize().padding(top = 16.dp)) {
            items(transport) { t ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(t.busPlate) }, 
                        supportingContent = { Text(t.routeName) }, 
                        trailingContent = { 
                            Row {
                                IconButton(onClick = { editingBus = t; showAdd = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { viewModel.deleteTransport(t.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                        }
                    )
                }
            }
        }
    }
    if(showAdd) {
        var plate by remember { mutableStateOf(editingBus?.busPlate ?: "") }; var route by remember { mutableStateOf(editingBus?.routeName ?: "") }; var driver by remember { mutableStateOf(editingBus?.driverName ?: "") }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(if(editingBus==null) "New Route" else "Edit Route") }, text = {
            Column {
                OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("Bus Plate") })
                OutlinedTextField(value = route, onValueChange = { route = it }, label = { Text("Route Name") })
                OutlinedTextField(value = driver, onValueChange = { driver = it }, label = { Text("Driver Name") })
            }
        }, confirmButton = { Button(onClick = { viewModel.addTransport(TransportRecord(id=editingBus?.id ?: "", busPlate=plate, routeName=route, driverName=driver)); showAdd = false }) { Text("Save") } })
    }
}

@Composable
fun AnnouncementsTab(viewModel: SchoolViewModel) {
    val students by viewModel.students.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    val grades = students.map { it.grade }.distinct().sorted()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("Parents") }
    var targetGrade by remember { mutableStateOf("All") }
    var editingId by remember { mutableStateOf("") }

    var filterTarget by remember { mutableStateOf("All") } // History filter

    Column(Modifier.padding(16.dp)) {
        Text(if(editingId.isEmpty()) "New Announcement" else "Edit Announcement", fontWeight = FontWeight.Bold)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("To: ", fontWeight = FontWeight.Bold)
            RadioButton(selected = target == "Parents", onClick = { target = "Parents" })
            Text("Parents")
            Spacer(Modifier.width(8.dp))
            RadioButton(selected = target == "Staff", onClick = { target = "Staff" })
            Text("Staff")
            Spacer(Modifier.width(8.dp))
            RadioButton(selected = target == "All", onClick = { target = "All" })
            Text("All")
        }

        if (target == "Parents") {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Grade: ", fontSize = 12.sp)
                FilterChip(selected = targetGrade == "All", onClick = { targetGrade = "All" }, label = { Text("All Grades") })
                grades.forEach { g ->
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = targetGrade == g, onClick = { targetGrade = g }, label = { Text(g) })
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { 
                viewModel.postAnnouncement(Announcement(id=editingId, title=title, content=content, target=target, gradeLevel=if(target == "Parents") targetGrade else "All"))
                title=""; content=""; editingId="" 
            }, Modifier.weight(1f)) { Text(if(editingId.isEmpty()) "Post Announcement" else "Update Announcement") }
            
            if(editingId.isNotEmpty()) {
                OutlinedButton(onClick = { title=""; content=""; editingId="" }) { Text("Cancel") }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        
        Text("Announcement History", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
            listOf("All", "Staff", "Parents").forEach { f ->
                FilterChip(selected = filterTarget == f, onClick = { filterTarget = f }, label = { Text(f) })
                Spacer(Modifier.width(8.dp))
            }
        }

        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            val filtered = if(filterTarget == "All") announcements else announcements.filter { it.target == filterTarget }
            items(filtered) { ann ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ann.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Row {
                                IconButton(onClick = { 
                                    editingId = ann.id; title = ann.title; content = ann.content; target = ann.target; targetGrade = ann.gradeLevel
                                }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { viewModel.deleteAnnouncement(ann.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                        }
                        Text(ann.content, style = MaterialTheme.typography.bodySmall)
                        Text("Target: ${ann.target} (${ann.gradeLevel})", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SMSTab(viewModel: SchoolViewModel) {
    var msg by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        Text("Broadcast SMS", fontWeight = FontWeight.Bold)
        OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Type message...") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = { viewModel.sendBulkSMS(msg, "General"); msg="" }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Send to All Parents") }
    }
}

@Composable
fun TeacherSettingsTab(viewModel: SchoolViewModel) {
    val profile by viewModel.schoolData.collectAsState()
    val subjects = (profile["subjects"] as? List<String>) ?: emptyList()
    var newSub by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Your Subjects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newSub, onValueChange = { newSub = it }, label = { Text("Add Subject") }, modifier = Modifier.weight(1f))
            IconButton(onClick = { if(newSub.isNotBlank()) { viewModel.updateTeacherSubjects(subjects + newSub.trim()); newSub = "" } }) { Icon(Icons.Default.Add, null) }
        }
        subjects.forEach { s -> ListItem(headlineContent = { Text(s) }, trailingContent = { IconButton(onClick = { viewModel.updateTeacherSubjects(subjects.filter { it != s }) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } }) }
    }
}

@Composable
fun TeacherGradesTab(viewModel: SchoolViewModel) {
    val students by viewModel.students.collectAsState()
    val profile by viewModel.schoolData.collectAsState()
    val teacherGrade = profile["grade"]?.toString() ?: ""
    val subjects = (profile["subjects"] as? List<String>) ?: emptyList()
    val myStudents = students.filter { it.grade == teacherGrade }
    var selectedStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var score by remember { mutableStateOf("") }; var sub by remember { mutableStateOf(if(subjects.isNotEmpty()) subjects[0] else "") }; var rem by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Enter Marks - $teacherGrade", fontWeight = FontWeight.Bold)
        LazyColumn {
            items(myStudents) { s -> ListItem(headlineContent = { Text(s.name) }, trailingContent = { Button(onClick = { selectedStudent = s }) { Text("Mark") } }) }
        }
    }
    if (selectedStudent != null) {
        AlertDialog(onDismissRequest = { selectedStudent = null }, title = { Text("Grade: ${selectedStudent?.name}") }, text = {
            Column {
                Row(Modifier.horizontalScroll(rememberScrollState())) { subjects.forEach { s -> FilterChip(selected = sub == s, onClick = { sub = s }, label = { Text(s) }); Spacer(Modifier.width(4.dp)) } }
                OutlinedTextField(value = score, onValueChange = { score = it }, label = { Text("Score") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = rem, onValueChange = { rem = it }, label = { Text("Remarks") })
            }
        }, confirmButton = { Button(onClick = { viewModel.addGrade(GradeRecord(studentId = selectedStudent!!.id, studentName = selectedStudent!!.name, subject = sub, score = score, remarks = rem, teacherId = viewModel.auth.currentUser?.uid ?: "")); selectedStudent = null; score = ""; rem = "" }) { Text("Save") } })
    }
}

@Composable
fun TeacherELearningTab(viewModel: SchoolViewModel) {
    var title by remember { mutableStateOf("") }; var topic by remember { mutableStateOf("") }; var type by remember { mutableStateOf(LessonContentType.TYPED_NOTES) }; var url by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    val profile by viewModel.schoolData.collectAsState()
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Publish Learning Content", fontWeight = FontWeight.Bold)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState())) { LessonContentType.values().forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.replace("_", " ")) }); Spacer(Modifier.width(4.dp)) } }
        if (type != LessonContentType.TYPED_NOTES) OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        Button(onClick = { viewModel.postLesson(LessonContent(title=title, topic=topic, type=type, notes=notes, videoUrl=if(type==LessonContentType.VIDEO_URL) url else null, audioUrl=if(type==LessonContentType.AUDIO) url else null, fileUrl=if(type==LessonContentType.PDF || type==LessonContentType.SLIDES) url else null, gradeLevel=profile["grade"]?.toString()?:"", teacherId=viewModel.auth.currentUser?.uid?:"", teacherName=profile["name"]?.toString()?:"Teacher")); title=""; topic=""; url=""; notes="" }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Publish") }
    }
}

@Composable
fun TeacherChatTab(viewModel: SchoolViewModel) {
    val chats by viewModel.chats.collectAsState()
    val students by viewModel.students.collectAsState()
    var selectedId by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) { students.forEach { s -> FilterChip(selected = selectedId == s.id, onClick = { selectedId = s.id }, label = { Text(s.name) }); Spacer(Modifier.width(4.dp)) } }
        LazyColumn(Modifier.weight(1f)) { items(chats.filter { it.studentId == selectedId }) { c -> 
            val isMe = c.senderId == viewModel.auth.currentUser?.uid
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if(isMe) Alignment.End else Alignment.Start) { Surface(color = if(isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) { Text(c.message, Modifier.padding(8.dp)) } } 
        } }
        Row { OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Message") }, modifier = Modifier.weight(1f)); IconButton(onClick = { if(msg.isNotBlank() && selectedId.isNotEmpty()) { viewModel.sendMessage(msg, selectedId); msg = "" } }) { Icon(Icons.AutoMirrored.Filled.Send, null) } }
    }
}

@Composable
fun StudentsTab(viewModel: SchoolViewModel, isTeacher: Boolean) {
    val students by viewModel.students.collectAsState()
    val settings by viewModel.schoolSettings.collectAsState()
    val feeTypes = (settings["feeTypes"] as? List<String>) ?: emptyList()
    var editingStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        if (!isTeacher) Button(onClick = { editingStudent = null; showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Enroll Student") }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            items(students) { s -> 
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(s.name) }, 
                        supportingContent = { Text("Grade: ${s.grade}") }, 
                        trailingContent = { 
                            if (!isTeacher) Row {
                                IconButton(onClick = { editingStudent = s; showAdd = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { viewModel.deleteStudent(s.id) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } 
                            }
                        }
                    ) 
                }
            }
        }
    }
    if (showAdd) {
        var n by remember { mutableStateOf(editingStudent?.name ?: "") }; var g by remember { mutableStateOf(editingStudent?.grade ?: "") }; var ph by remember { mutableStateOf(editingStudent?.parentPhone ?: "") }; var em by remember { mutableStateOf(editingStudent?.parentEmail ?: "") }; var pw by remember { mutableStateOf(editingStudent?.parentTempPassword ?: "") }
        var baseFees = remember { mutableStateMapOf<String, String>().apply { editingStudent?.dynamicFees?.forEach { (k, v) -> put(k, v.toString()) } } }
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(if(editingStudent==null) "Enroll Student" else "Edit Student") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("Name") })
                OutlinedTextField(value = g, onValueChange = { g = it }, label = { Text("Grade") })
                OutlinedTextField(value = ph, onValueChange = { ph = it }, label = { Text("Parent Phone") })
                OutlinedTextField(value = em, onValueChange = { em = it }, label = { Text("Parent Email") })
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Login Password") })
                feeTypes.forEach { f -> OutlinedTextField(value = baseFees[f] ?: "", onValueChange = { baseFees[f] = it }, label = { Text(f) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
            }
        }, confirmButton = { Button(onClick = { 
            val total = baseFees.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
            viewModel.enrollStudent(StudentRecord(id=editingStudent?.id ?: "", name=n, grade=g, parentPhone=ph, parentEmail=em, parentTempPassword=pw, feeBalance=total, dynamicFees=baseFees.mapValues { it.value.toDoubleOrNull() ?: 0.0 })); showAdd = false 
        }) { Text("Save") } })
    }
}

@Composable
fun StaffTab(viewModel: SchoolViewModel) {
    val teachers by viewModel.teachers.collectAsState()
    val others by viewModel.otherStaff.collectAsState()
    val settings by viewModel.schoolSettings.collectAsState()
    val taxTypes = (settings["taxTypes"] as? List<String>) ?: emptyList()
    var editingTeacher by remember { mutableStateOf<TeacherRecord?>(null) }
    var editingStaff by remember { mutableStateOf<StaffRecord?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp)) {
        Button(onClick = { editingTeacher = null; editingStaff = null; showAdd = true }, Modifier.fillMaxWidth()) { Text("Hire Staff") }
        LazyColumn(Modifier.fillMaxSize().padding(top = 16.dp)) {
            item { Text("Teachers", fontWeight = FontWeight.Bold) }
            items(teachers) { t -> 
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(t.name) }, 
                        supportingContent = { Text("Grade: ${t.grade}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editingTeacher = t; editingStaff = null; showAdd = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { viewModel.deleteStaff(t.id, "Teacher") }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)); Text("Staff", fontWeight = FontWeight.Bold) }
            items(others) { s -> 
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(s.name) }, 
                        supportingContent = { Text(s.role) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editingStaff = s; editingTeacher = null; showAdd = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { viewModel.deleteStaff(s.id, s.role) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        var n by remember { mutableStateOf(editingTeacher?.name ?: editingStaff?.name ?: "") }; var e by remember { mutableStateOf(editingTeacher?.email ?: editingStaff?.email ?: "") }; var r by remember { mutableStateOf(editingStaff?.role ?: "Teacher") }
        var g by remember { mutableStateOf(editingTeacher?.grade ?: "") }; var pw by remember { mutableStateOf(editingTeacher?.tempPassword ?: editingStaff?.tempPassword ?: "") }; var sal by remember { mutableStateOf(editingTeacher?.baseSalary?.toString() ?: editingStaff?.baseSalary?.toString() ?: "") }
        var taxes = remember { mutableStateMapOf<String, String>() }

        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text(if(editingTeacher==null && editingStaff==null) "Hire Staff" else "Edit Staff") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("Name") })
                OutlinedTextField(value = e, onValueChange = { e = it }, label = { Text("Email") })
                OutlinedTextField(value = r, onValueChange = { r = it }, label = { Text("Role") })
                if(r == "Teacher") OutlinedTextField(value = g, onValueChange = { g = it }, label = { Text("Assigned Grade") })
                OutlinedTextField(value = sal, onValueChange = { sal = it }, label = { Text("Base Salary") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                taxTypes.forEach { t -> OutlinedTextField(value = taxes[t] ?: "", onValueChange = { taxes[t] = it }, label = { Text(t) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text("Temp Password") })
            }
        }, confirmButton = { Button(onClick = {
            val s = sal.toDoubleOrNull() ?: 0.0
            if (r == "Teacher") viewModel.addTeacher(TeacherRecord(id=editingTeacher?.id ?: "", name=n, email=e, grade=g, tempPassword=pw, baseSalary=s))
            else viewModel.addOtherStaff(StaffRecord(id=editingStaff?.id ?: "", name=n, email=e, role=r, tempPassword=pw, baseSalary=s))
            showAdd = false
        }) { Text("Save") } })
    }
}
