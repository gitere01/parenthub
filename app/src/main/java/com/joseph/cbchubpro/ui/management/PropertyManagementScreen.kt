package com.joseph.cbchubpro.ui.management

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

class PropertyViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _properties = MutableStateFlow<List<PropertyListing>>(emptyList())
    val properties = _properties.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        fetchMyProperties()
    }

    private fun fetchMyProperties() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("properties").whereEqualTo("agentId", uid)
            .addSnapshotListener { snapshot, _ ->
                _properties.value = snapshot?.documents?.mapNotNull { 
                    it.toObject(PropertyListing::class.java)?.copy(id = it.id) 
                } ?: emptyList()
            }
    }

    fun addProperty(context: Context, property: PropertyListing, images: List<Any>) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Agent"
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val urls = images.map { 
                    if (it is Uri) uploadFile(context, it) else it as String
                }
                val p = property.copy(
                    agentId = uid, 
                    agentName = name, 
                    imageUrls = urls,
                    isPublic = true,
                    isAvailable = true
                )
                db.collection("properties").add(p).await()
                _statusMessage.value = "Property listed successfully!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun updateProperty(context: Context, propertyId: String, property: PropertyListing, images: List<Any>) {
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val finalUrls = images.map { 
                    if (it is Uri) uploadFile(context, it) else it as String
                }
                val p = property.copy(imageUrls = finalUrls)
                db.collection("properties").document(propertyId).set(p).await()
                _statusMessage.value = "Property updated successfully!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun deleteProperty(propertyId: String) {
        viewModelScope.launch {
            try {
                db.collection("properties").document(propertyId).delete().await()
                _statusMessage.value = "Property removed."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    private suspend fun uploadFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: ""
        val fileName = "${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child("properties/$uid/$fileName")
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        originalBitmap?.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val compressedData = outputStream.toByteArray()
        ref.putBytes(compressedData).await()
        inputStream?.close()
        return@withContext ref.downloadUrl.await().toString()
    }

    fun clearStatus() { _statusMessage.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyManagementScreen(onLogoutClicked: () -> Unit, viewModel: PropertyViewModel = viewModel()) {
    val properties by viewModel.properties.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProperty by remember { mutableStateOf<PropertyListing?>(null) }
    val context = LocalContext.current

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Property Manager", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogoutClicked) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Property")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (properties.isEmpty()) {
                item { Box(Modifier.fillParentMaxSize(), Alignment.Center) { Text("No properties listed yet.", color = Color.Gray) } }
            }
            items(properties) { property ->
                PropertyListingCard(
                    property = property,
                    onEdit = { editingProperty = property },
                    onDelete = { viewModel.deleteProperty(property.id) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddPropertyDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { property, images ->
                viewModel.addProperty(context, property, images)
                showAddDialog = false
            },
            isUploading = isUploading
        )
    }

    if (editingProperty != null) {
        AddPropertyDialog(
            existingProperty = editingProperty,
            onDismiss = { editingProperty = null },
            onConfirm = { property, images ->
                viewModel.updateProperty(context, editingProperty!!.id, property, images)
                editingProperty = null
            },
            isUploading = isUploading
        )
    }
}

@Composable
fun PropertyListingCard(property: PropertyListing, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.height(180.dp).fillMaxWidth()) {
                if (property.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = property.imageUrls.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.background(Color.Black.copy(0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color.White)
                    }
                }
                
                if (property.imageUrls.size > 1) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        color = Color.Black.copy(0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${property.imageUrls.size} Photos",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text(property.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("KES ${property.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${property.type} - ${property.propertyCategory}", style = MaterialTheme.typography.bodySmall)
                Text(property.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPropertyDialog(
    existingProperty: PropertyListing? = null,
    onDismiss: () -> Unit,
    onConfirm: (PropertyListing, List<Any>) -> Unit,
    isUploading: Boolean
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingProperty?.title ?: "") }
    var description by remember { mutableStateOf(existingProperty?.description ?: "") }
    var price by remember { mutableStateOf(existingProperty?.price?.toString() ?: "") }
    var deposit by remember { mutableStateOf(existingProperty?.deposit?.toString() ?: "") }
    var location by remember { mutableStateOf(existingProperty?.location ?: "") }
    var address by remember { mutableStateOf(existingProperty?.address ?: "") }
    var type by remember { mutableStateOf(existingProperty?.type ?: "RENT") }
    var category by remember { mutableStateOf(existingProperty?.propertyCategory ?: "Apartment") }
    var bedrooms by remember { mutableStateOf(existingProperty?.bedrooms?.toString() ?: "") }
    var bathrooms by remember { mutableStateOf(existingProperty?.bathrooms?.toString() ?: "") }
    var size by remember { mutableStateOf(existingProperty?.size ?: "") }
    var amenitiesStr by remember { mutableStateOf(existingProperty?.amenities?.joinToString(", ") ?: "") }
    
    var selectedImages by remember { 
        mutableStateOf<List<Any>>(existingProperty?.imageUrls ?: emptyList()) 
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = (selectedImages + uris).take(30)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingProperty == null) "Add New Property" else "Edit Property") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                Text("Property Photos (${selectedImages.size}/30)", style = MaterialTheme.typography.labelMedium)
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { galleryPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Add", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    itemsIndexed(selectedImages) { index, source ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val mutable = selectedImages.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(0, item)
                                    selectedImages = mutable
                                }
                                .border(
                                    width = if (index == 0) 2.dp else 1.dp,
                                    color = if (index == 0) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            AsyncImage(
                                model = source,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (index == 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                ) {
                                    Text("MAIN", style = MaterialTheme.typography.labelSmall, color = Color.White, textAlign = TextAlign.Center, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(
                                onClick = { selectedImages = selectedImages.filterIndexed { i, _ -> i != index } },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                
                HorizontalDivider()

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title (e.g. Modern 2BR Apartment)") }, modifier = Modifier.fillMaxWidth())
                
                Text("Transaction Type:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("RENT", "SALE", "LEASE", "STAY").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    }
                }

                Text("Category:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Apartment", "House", "Office", "Land", "Hostel", "Hotel Room").forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (KES)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = deposit, onValueChange = { deposit = it }, label = { Text("Deposit (KES)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Town/Area (e.g. Syokimau)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Specific Address/Street") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bedrooms, onValueChange = { bedrooms = it }, label = { Text("Bedrooms") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = bathrooms, onValueChange = { bathrooms = it }, label = { Text("Bathrooms") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                OutlinedTextField(value = size, onValueChange = { size = it }, label = { Text("Size (e.g. 1200 sqft or 50x100)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amenitiesStr, onValueChange = { amenitiesStr = it }, label = { Text("Amenities (Wifi, Parking, Gym - comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Detailed Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val p = (existingProperty ?: PropertyListing()).copy(
                        title = title,
                        description = description,
                        price = price.toDoubleOrNull() ?: 0.0,
                        deposit = deposit.toDoubleOrNull() ?: 0.0,
                        location = location,
                        address = address,
                        type = type,
                        propertyCategory = category,
                        bedrooms = bedrooms.toIntOrNull() ?: 0,
                        bathrooms = bathrooms.toIntOrNull() ?: 0,
                        size = size,
                        amenities = amenitiesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    onConfirm(p, selectedImages)
                },
                enabled = !isUploading && title.isNotEmpty() && price.isNotEmpty() && selectedImages.isNotEmpty()
            ) {
                Text(if (isUploading) "Uploading..." else if (existingProperty == null) "Post Property" else "Update Property")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
