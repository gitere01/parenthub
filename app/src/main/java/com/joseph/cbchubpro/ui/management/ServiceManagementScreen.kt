package com.joseph.cbchubpro.ui.management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class ServiceViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://cbchubpro.firebasestorage.app")

    private val _profileData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val profileData = _profileData.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()

    private val _bookings = MutableStateFlow<List<ServiceBooking>>(emptyList())
    val bookings = _bookings.asStateFlow()

    init {
        fetchProfileData()
        fetchBookings()
    }

    private fun fetchProfileData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            _profileData.value = snapshot?.data ?: emptyMap()
        }
    }

    private fun fetchBookings() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("service_bookings")
            .whereEqualTo("serviceProviderId", uid)
            .addSnapshotListener { snapshot, _ ->
                _bookings.value = snapshot?.documents?.mapNotNull { 
                    it.toObject(ServiceBooking::class.java)?.copy(id = it.id) 
                }?.sortedByDescending { it.createdAt } ?: emptyList()
            }
    }

    fun updateBookingStatus(bookingId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            try {
                db.collection("service_bookings").document(bookingId).update("status", newStatus).await()
            } catch (e: Exception) {}
        }
    }

    suspend fun uploadFile(uri: Uri, path: String): String {
        val uid = auth.currentUser?.uid ?: return ""
        val fileName = UUID.randomUUID().toString()
        val ref = storage.reference.child("users/$uid/$path/$fileName")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    fun saveFullProfile(
        description: String,
        location: String,
        contact: String,
        hours: String,
        bookingPrice: String,
        groupBookingPrice: String,
        minGroupBooking: String,
        basicSessionPrice: String,
        fullPackagePrice: String,
        socialLinks: Map<String, String>, 
        profileImageUri: Uri?,
        certUri: Uri?
    ) {
        val uid = auth.currentUser?.uid ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>(
                    "description" to description,
                    "location" to location,
                    "contact" to contact,
                    "bookingHours" to hours,
                    "bookingPrice" to bookingPrice,
                    "groupBookingPrice" to groupBookingPrice,
                    "minGroupBooking" to minGroupBooking,
                    "basicSessionPrice" to basicSessionPrice,
                    "fullPackagePrice" to fullPackagePrice,
                    "socialLinks" to socialLinks 
                )
                
                profileImageUri?.let {
                    val url = uploadFile(it, "profile")
                    updates["profileImageUrl"] = url
                }
                
                certUri?.let {
                    val url = uploadFile(it, "certs")
                    updates["certificationUrl"] = url
                }

                db.collection("users").document(uid).update(updates).await()
            } catch (e: Exception) {
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }
}

data class ServiceBooking(
    val id: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val buyerContact: String = "",
    val serviceProviderId: String = "",
    val serviceName: String = "",
    val amount: Double = 0.0,
    val status: OrderStatus = OrderStatus.PENDING,
    val createdAt: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val notes: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceManagementScreen(onLogoutClicked: () -> Unit, viewModel: ServiceViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val profile by viewModel.profileData.collectAsState()
    val bookings by viewModel.bookings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Professional Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        viewModel.signOut()
                        onLogoutClicked()
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("My Profile") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { 
                    BadgedBox(badge = { if(bookings.any { it.status == OrderStatus.PAID }) Badge { Text(bookings.count { it.status == OrderStatus.PAID }.toString()) } }) {
                        Text("Bookings")
                    }
                })
            }

            when (selectedTab) {
                0 -> ProfileEditorTab(profile, viewModel)
                1 -> BookingsTab(bookings, viewModel)
            }
        }
    }
}

@Composable
fun BookingsTab(bookings: List<ServiceBooking>, viewModel: ServiceViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (bookings.isEmpty()) {
            item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No bookings yet", color = Color.Gray) } }
        }
        items(bookings) { booking ->
            BookingCard(booking, onUpdateStatus = { viewModel.updateBookingStatus(booking.id, it) })
        }
    }
}

@Composable
fun BookingCard(booking: ServiceBooking, onUpdateStatus: (OrderStatus) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(booking.serviceName, fontWeight = FontWeight.Bold)
                    Text(booking.createdAt.toDate().toString().take(16), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                StatusBadge(booking.status)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Text("Client Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(booking.buyerName, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(booking.buyerContact, style = MaterialTheme.typography.bodySmall)
            }

            if (booking.notes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Notes: ${booking.notes}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Payment", fontWeight = FontWeight.Bold)
                Text("KES ${booking.amount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            if (booking.status == OrderStatus.PAID) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onUpdateStatus(OrderStatus.COMPLETED) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Service Delivered")
                }
                Text("Funds have been paid and are awaiting delivery confirmation.", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            } else if (booking.status == OrderStatus.COMPLETED) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Service completed!", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun ProfileEditorTab(profile: Map<String, Any>, viewModel: ServiceViewModel) {
    val isUploading by viewModel.isUploading.collectAsState()

    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    
    var bookingPrice by remember { mutableStateOf("") }
    var groupBookingPrice by remember { mutableStateOf("") }
    var minGroupBooking by remember { mutableStateOf("") }
    var basicSessionPrice by remember { mutableStateOf("") }
    var fullPackagePrice by remember { mutableStateOf("") }

    var tiktokLink by remember { mutableStateOf("") }
    var youtubeLink by remember { mutableStateOf("") }
    var facebookLink by remember { mutableStateOf("") }
    var instagramLink by remember { mutableStateOf("") }
    var linkedinLink by remember { mutableStateOf("") }
    
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var certUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(profile) {
        description = profile["description"] as? String ?: ""
        location = profile["location"] as? String ?: ""
        contact = profile["contact"] as? String ?: ""
        hours = profile["bookingHours"] as? String ?: ""
        bookingPrice = profile["bookingPrice"] as? String ?: ""
        groupBookingPrice = profile["groupBookingPrice"] as? String ?: ""
        minGroupBooking = profile["minGroupBooking"] as? String ?: ""
        basicSessionPrice = profile["basicSessionPrice"] as? String ?: ""
        fullPackagePrice = profile["fullPackagePrice"] as? String ?: ""

        val social = profile["socialLinks"] as? Map<String, String> ?: emptyMap()
        tiktokLink = social["tiktok"] ?: ""
        youtubeLink = social["youtube"] ?: ""
        facebookLink = social["facebook"] ?: ""
        instagramLink = social["instagram"] ?: ""
        linkedinLink = social["linkedin"] ?: ""
    }

    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { profileImageUri = it }
    val certPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { certUri = it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Profile Photo Section
        Card(
            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .size(130.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .clickable { profilePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val imageToDisplay = profileImageUri ?: profile["profileImageUrl"]?.toString()
                if (imageToDisplay != null) {
                    AsyncImage(
                        model = imageToDisplay,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, modifier = Modifier.size(64.dp), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Icon(Icons.Default.CameraAlt, tint = Color.White, modifier = Modifier.size(24.dp).padding(bottom = 8.dp), contentDescription = null)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Profile & Trust Metrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("About Me / Service Description") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 3)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Physical Location") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) })
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Verified Phone Number") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary) })
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Working Hours") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) })

        Spacer(modifier = Modifier.height(32.dp))
        Text("Pricing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = bookingPrice, onValueChange = { bookingPrice = it }, label = { Text("Base Fee") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = basicSessionPrice, onValueChange = { basicSessionPrice = it }, label = { Text("Service Rate") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Verification Documents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth().clickable { certPicker.launch("*/*") },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Badge, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(if (certUri != null || profile["certificationUrl"] != null) "Document Uploaded ✅" else "Upload ID/License for Verification", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                val social = mapOf("tiktok" to tiktokLink, "youtube" to youtubeLink, "facebook" to facebookLink, "instagram" to instagramLink, "linkedin" to linkedinLink)
                viewModel.saveFullProfile(description, location, contact, hours, bookingPrice, groupBookingPrice, minGroupBooking, basicSessionPrice, fullPackagePrice, social, profileImageUri, certUri)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isUploading
        ) {
            Text(if (isUploading) "Saving..." else "Update Professional Profile")
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}
