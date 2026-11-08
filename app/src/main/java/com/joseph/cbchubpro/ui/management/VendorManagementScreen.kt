package com.joseph.cbchubpro.ui.management

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class VendorViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    // Use default instance to avoid URL mismatch errors
    private val storage = FirebaseStorage.getInstance()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _items = MutableStateFlow<List<ProductItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _activeDeals = MutableStateFlow<List<GroupDeal>>(emptyList())
    val activeDeals = _activeDeals.asStateFlow()

    private val _allOrders = MutableStateFlow<List<Order>>(emptyList())
    val allOrders = _allOrders.asStateFlow()

    private val _disputedOrders = MutableStateFlow<List<Order>>(emptyList())
    val disputedOrders = _disputedOrders.asStateFlow()

    private val _swapRequests = MutableStateFlow<List<SwapRequest>>(emptyList())
    val swapRequests = _swapRequests.asStateFlow()
    private var receivedSwapListener: ListenerRegistration? = null
    private var sentSwapListener: ListenerRegistration? = null
    private var receivedSwapRequests: List<SwapRequest> = emptyList()
    private var sentSwapRequests: List<SwapRequest> = emptyList()

    private val _vendorName = MutableStateFlow("")

    private val _userRole = MutableStateFlow("")
    val userRole = _userRole.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _trackingInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val trackingInfo = _trackingInfo.asStateFlow()

    private var platformSettings: PlatformSettings? = null

    init {
        fetchPlatformSettings()
        setupAuth()
    }

    private fun fetchPlatformSettings() {
        db.collection("platform").document("settings").addSnapshotListener { snapshot, _ ->
            platformSettings = snapshot?.toObject(PlatformSettings::class.java)
        }
    }

    private fun setupAuth() {
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                startListening(uid)
            }
        }
    }

    private fun startListening(uid: String) {
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            _vendorName.value = snapshot?.getString("name") ?: ""
            _userRole.value = snapshot?.getString("role") ?: ""
        }
        
        db.collection("users").document(uid).collection("products")
            .addSnapshotListener { snapshot, _ ->
                _items.value = snapshot?.documents?.mapNotNull { 
                    it.toObject(ProductItem::class.java)?.copy(id = it.id) 
                } ?: emptyList()
            }

        db.collection("group_deals").whereEqualTo("vendorId", uid)
            .addSnapshotListener { snapshot, _ ->
                _activeDeals.value = snapshot?.documents?.mapNotNull { it.toObject(GroupDeal::class.java)?.copy(id = it.id) } ?: emptyList()
            }

        db.collection("orders").whereEqualTo("vendorId", uid)
            .addSnapshotListener { snapshot, _ ->
                val orders = snapshot?.documents?.mapNotNull { it.toObject(Order::class.java)?.copy(id = it.id) } ?: emptyList()
                _allOrders.value = orders.sortedByDescending { it.createdAt }
                _disputedOrders.value = orders.filter { it.status == OrderStatus.DISPUTED }
                
                orders.filter { it.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD && it.status != OrderStatus.COMPLETED && it.status != OrderStatus.CANCELLED }
                    .forEach { fetchLiveTracking(it.id) }
            }

        receivedSwapListener?.remove()
        sentSwapListener?.remove()
        receivedSwapListener = db.collection("swap_requests").whereEqualTo("receiverId", uid)
            .addSnapshotListener { snapshot, _ ->
                receivedSwapRequests = snapshot?.documents?.mapNotNull {
                    it.toObject(SwapRequest::class.java)?.copy(id = it.id)
                }.orEmpty()
                publishSwapRequests()
            }
        sentSwapListener = db.collection("swap_requests").whereEqualTo("senderId", uid)
            .addSnapshotListener { snapshot, _ ->
                sentSwapRequests = snapshot?.documents?.mapNotNull {
                    it.toObject(SwapRequest::class.java)?.copy(id = it.id)
                }.orEmpty()
                publishSwapRequests()
            }
    }

    private fun publishSwapRequests() {
        _swapRequests.value = (receivedSwapRequests + sentSwapRequests)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
    }

    fun fetchLiveTracking(orderId: String) {
        val settings = platformSettings ?: return
        if (settings.pickupMtaaniApiKey.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.pickupmtaani.com/v1/track?orderId=$orderId")
                    .addHeader("Authorization", "Bearer ${settings.pickupMtaaniApiSecret}")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val statusText = json.optString("status_description", "Order Processed")
                    withContext(Dispatchers.Main) {
                        _trackingInfo.value = _trackingInfo.value + (orderId to statusText)
                    }
                }
            } catch (e: Exception) {
                Log.e("Tracking", "Failed to fetch PM tracking for $orderId", e)
            }
        }
    }

    fun requestVerification() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update("verificationPending", true).await()
                _statusMessage.value = "Shop verification requested. Badge will appear after review."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun acceptRefund(orderId: String) {
        viewModelScope.launch {
            db.collection("orders").document(orderId).update("status", OrderStatus.REFUNDED).await()
            _statusMessage.value = "Refund processed successfully."
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            try {
                db.collection("orders").document(orderId).update("status", newStatus).await()
                _statusMessage.value = "Order updated to ${newStatus.name}"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun shipOrder(orderId: String, trackingNumber: String, courier: String) {
        viewModelScope.launch {
            try {
                db.collection("orders").document(orderId).update(
                    "status", OrderStatus.SHIPPED,
                    "trackingNumber", trackingNumber,
                    "courierName", courier
                ).await()
                _statusMessage.value = "Order marked as Shipped with tracking info."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun updateDealStatus(dealId: String, newStatus: DealStatus) {
        viewModelScope.launch {
            db.collection("group_deals").document(dealId).update("status", newStatus).await()
        }
    }

    fun updateSwapStatus(requestId: String, newStatus: SwapRequestStatus) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { transaction ->
                    val ref = db.collection("swap_requests").document(requestId)
                    val request = transaction.get(ref).toObject(SwapRequest::class.java)
                        ?: throw IllegalStateException("Swap request not found")
                    when (newStatus) {
                        SwapRequestStatus.ACCEPTED, SwapRequestStatus.REJECTED -> {
                            if (request.receiverId != uid || request.status != SwapRequestStatus.PENDING) {
                                throw IllegalStateException("Only the recipient can respond to a pending request")
                            }
                            if (newStatus == SwapRequestStatus.ACCEPTED) {
                                val requestedItemRef = db.collection("users").document(request.receiverId)
                                    .collection("products").document(request.requestedItemId)
                                val requestedItem = transaction.get(requestedItemRef)
                                val requestedData = requestedItem.data
                                    ?: throw IllegalStateException("The requested item is no longer listed")
                                if (!(requestedData["status"] as? String ?: "Available").equals("Available", ignoreCase = true) || requestedData["isForSwap"] == false) {
                                    throw IllegalStateException("The requested item is no longer available")
                                }

                                val offeredItemRef = request.offeredItemId?.let { id ->
                                    db.collection("users").document(request.senderId).collection("products").document(id)
                                }
                                val offeredItem = offeredItemRef?.let { transaction.get(it) }
                                if (offeredItemRef != null) {
                                    val offeredData = offeredItem?.data
                                        ?: throw IllegalStateException("The offered item is no longer listed")
                                    if (!(offeredData["status"] as? String ?: "Available").equals("Available", ignoreCase = true) || offeredData["isForSwap"] == false) {
                                        throw IllegalStateException("The offered item is no longer available")
                                    }
                                }
                                transaction.update(requestedItemRef, mapOf("status" to "Reserved"))
                                if (offeredItemRef != null) transaction.update(offeredItemRef, mapOf("status" to "Reserved"))
                            }
                        }
                        SwapRequestStatus.CANCELLED -> {
                            val senderCancelsPending = request.senderId == uid && request.status == SwapRequestStatus.PENDING
                            val participantCancelsAccepted =
                                (request.senderId == uid || request.receiverId == uid) &&
                                    request.status == SwapRequestStatus.ACCEPTED &&
                                    !request.senderConfirmed && !request.receiverConfirmed
                            if (!senderCancelsPending && !participantCancelsAccepted) {
                                throw IllegalStateException("This exchange can no longer be cancelled")
                            }
                            if (participantCancelsAccepted) {
                                val requestedItemRef = db.collection("users").document(request.receiverId)
                                    .collection("products").document(request.requestedItemId)
                                val requestedItem = transaction.get(requestedItemRef)
                                val offeredItemRef = request.offeredItemId?.let { id ->
                                    db.collection("users").document(request.senderId).collection("products").document(id)
                                }
                                val offeredItem = offeredItemRef?.let { transaction.get(it) }
                                if (requestedItem.exists() && requestedItem.getString("status") == "Reserved") {
                                    transaction.update(requestedItemRef, mapOf("status" to "Available"))
                                }
                                if (offeredItemRef != null && offeredItem?.exists() == true && offeredItem.getString("status") == "Reserved") {
                                    transaction.update(offeredItemRef, mapOf("status" to "Available"))
                                }
                            }
                        }
                        else -> throw IllegalArgumentException("Use the exchange confirmation action for this status")
                    }
                    transaction.update(ref, mapOf("status" to newStatus.name, "updatedAt" to Timestamp.now()))
                }.await()
                _statusMessage.value = when (newStatus) {
                    SwapRequestStatus.ACCEPTED -> "Swap accepted. You can now message each other."
                    SwapRequestStatus.REJECTED -> "Swap request declined."
                    SwapRequestStatus.CANCELLED -> "Swap request cancelled."
                    else -> "Swap request updated."
                }
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Could not update swap request."
            }
        }
    }

    fun confirmSwapCompleted(requestId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val completed = db.runTransaction { transaction ->
                    val ref = db.collection("swap_requests").document(requestId)
                    val request = transaction.get(ref).toObject(SwapRequest::class.java)
                        ?: throw IllegalStateException("Swap request not found")
                    if (request.status != SwapRequestStatus.ACCEPTED) {
                        throw IllegalStateException("Only an accepted swap can be confirmed")
                    }
                    val senderConfirmed = request.senderConfirmed || request.senderId == uid
                    val receiverConfirmed = request.receiverConfirmed || request.receiverId == uid
                    if (!senderConfirmed && !receiverConfirmed) throw IllegalStateException("You are not part of this swap")
                    val bothConfirmed = senderConfirmed && receiverConfirmed
                    val requestedItemRef = db.collection("users").document(request.receiverId)
                        .collection("products").document(request.requestedItemId)
                    val requestedItemSnapshot = transaction.get(requestedItemRef)
                    val offeredItemRef = request.offeredItemId?.let { id ->
                        db.collection("users").document(request.senderId).collection("products").document(id)
                    }
                    val offeredItemSnapshot = offeredItemRef?.let { transaction.get(it) }
                    if (bothConfirmed) {
                        if (!requestedItemSnapshot.exists()) throw IllegalStateException("The requested listing could not be found")
                        if (offeredItemRef != null && offeredItemSnapshot?.exists() != true) {
                            throw IllegalStateException("The offered listing could not be found")
                        }
                    }
                    transaction.update(ref, mapOf(
                        "senderConfirmed" to senderConfirmed,
                        "receiverConfirmed" to receiverConfirmed,
                        "status" to if (bothConfirmed) SwapRequestStatus.COMPLETED.name else SwapRequestStatus.ACCEPTED.name,
                        "updatedAt" to Timestamp.now()
                    ))
                    if (bothConfirmed) {
                        transaction.update(requestedItemRef, mapOf("status" to "Exchanged", "isForSwap" to false))
                        if (offeredItemRef != null) transaction.update(offeredItemRef, mapOf("status" to "Exchanged", "isForSwap" to false))
                    }
                    bothConfirmed
                }.await()
                _statusMessage.value = if (completed) "Both parents confirmed. Swap completed!" else "Your confirmation is saved; waiting for the other parent."
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Could not confirm this exchange."
            }
        }
    }

    fun sendSwapMessage(request: SwapRequest, message: String) {
        val uid = auth.currentUser?.uid ?: return
        val cleanMessage = message.trim().take(2000)
        if (cleanMessage.isEmpty()) return
        viewModelScope.launch {
            try {
                val currentRequest = db.collection("swap_requests").document(request.id).get().await()
                    .toObject(SwapRequest::class.java)
                    ?: throw IllegalStateException("Swap request not found")
                if (currentRequest.status != SwapRequestStatus.ACCEPTED && currentRequest.status != SwapRequestStatus.COMPLETED) {
                    throw IllegalStateException("Messaging is available after a swap is accepted")
                }
                if (uid != currentRequest.senderId && uid != currentRequest.receiverId) {
                    throw IllegalStateException("You are not part of this swap")
                }
                db.collection("swap_requests").document(request.id).collection("messages").add(mapOf(
                    "senderId" to uid,
                    "senderName" to _vendorName.value.ifBlank { auth.currentUser?.displayName ?: "Parent" },
                    "text" to cleanMessage,
                    "createdAt" to Timestamp.now()
                )).await()
            } catch (e: Exception) {
                _statusMessage.value = e.message ?: "Could not send message."
            }
        }
    }

    fun createGroupDeal(product: ProductItem, minRequired: Int, deadlineHours: Int) {
        val uid = auth.currentUser?.uid ?: return
        val deal = GroupDeal(
            productId = product.id,
            productName = product.name,
            vendorId = uid,
            vendorName = _vendorName.value,
            minRequired = minRequired,
            pricePerPerson = product.groupPrice,
            originalPrice = product.price,
            deadline = com.google.firebase.Timestamp(Date(System.currentTimeMillis() + (deadlineHours * 3600000))),
            status = DealStatus.ACTIVE
        )
        viewModelScope.launch {
            try {
                db.collection("group_deals").add(deal).await()
                db.collection("users").document(uid).collection("products").document(product.id)
                    .update("isGroupDeal", true, "listingType", ListingType.GROUP_DEAL.name).await()
                _statusMessage.value = "Group Buying Deal Launched!"
            } catch (e: Exception) {
                _statusMessage.value = "Error launching deal: ${e.message}"
            }
        }
    }

    fun addProduct(context: Context, product: ProductItem, images: List<Any>) {
        val uid = auth.currentUser?.uid ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val urls = images.map { 
                    if (it is Uri) uploadFile(context, it) else it as String
                }
                val p = product.copy(
                    imageUrls = urls, 
                    vendorId = uid,
                    verificationStatus = VerificationStatus.UNVERIFIED 
                )
                db.collection("users").document(uid).collection("products").add(p).await()
                _statusMessage.value = "Product added successfully!"
            } catch (e: Exception) {
                Log.e("VendorViewModel", "Add product error", e)
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun updateProduct(context: Context, productId: String, product: ProductItem, images: List<Any>) {
        val uid = auth.currentUser?.uid ?: return
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val finalUrls = images.map { 
                    if (it is Uri) uploadFile(context, it) else it as String
                }.take(5)
                
                val p = product.copy(
                    imageUrls = finalUrls,
                    vendorId = uid
                )
                db.collection("users").document(uid).collection("products").document(productId).set(p).await()
                _statusMessage.value = "Product updated successfully!"
            } catch (e: Exception) {
                Log.e("VendorViewModel", "Update product error", e)
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun deleteProduct(productId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("products").document(productId).delete().await()
                _statusMessage.value = "Product deleted."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun updateAdSettings(productId: String, level: AdLevel) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val updates = mapOf(
                    "adLevel" to level,
                    "adStatus" to if (level == AdLevel.FREE) AdStatus.INACTIVE else AdStatus.REQUESTED
                )
                db.collection("users").document(uid).collection("products").document(productId).update(updates).await()
                _statusMessage.value = "Advertisement settings updated."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    private suspend fun uploadFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: ""
        val fileName = "${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child("products/$uid/$fileName")
        
        val inputStream = context.contentResolver.openInputStream(uri) 
            ?: throw Exception("Could not open input stream")
        
        try {
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val compressedData = outputStream.toByteArray()
            
            // Upload the data
            ref.putBytes(compressedData).await()
            
            // Get the public URL
            return@withContext ref.downloadUrl.await().toString()
        } finally {
            inputStream.close()
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun clearStatusMessage() { _statusMessage.value = null }

    override fun onCleared() {
        receivedSwapListener?.remove()
        sentSwapListener?.remove()
        super.onCleared()
    }
}

private data class SwapChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val createdAt: Timestamp?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorManagementScreen(onLogoutClicked: () -> Unit, viewModel: VendorViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var chattingSwap by remember { mutableStateOf<SwapRequest?>(null) }
    val userRole by viewModel.userRole.collectAsState()
    val trackingInfo by viewModel.trackingInfo.collectAsState()
    
    val tabs = remember(userRole) {
        if (userRole == "parent/guardian") {
            listOf("My Items", "Swap Requests", "Chat", "Account")
        } else {
            listOf("Inventory", "Orders", "Group Deals", "Finance", "Disputes")
        }
    }
    
    val items by viewModel.items.collectAsState()
    val deals by viewModel.activeDeals.collectAsState()
    val orders by viewModel.allOrders.collectAsState()
    val disputes by viewModel.disputedOrders.collectAsState()
    val swaps by viewModel.swapRequests.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { 
            TopAppBar(
                title = { Text(if (userRole == "parent/guardian") "Parent Swap Hub" else "Vendor Dashboard", fontWeight = FontWeight.Bold) },
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
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { 
                        if (title == "Disputes" && disputes.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text(disputes.size.toString()) } }) { Text(title) }
                        } else if (title == "Swap Requests" && swaps.any { it.status == SwapRequestStatus.PENDING }) {
                            BadgedBox(badge = { Badge { Text(swaps.count { it.status == SwapRequestStatus.PENDING }.toString()) } }) { Text(title) }
                        } else if (title == "Orders" && orders.any { it.status == OrderStatus.PAID || it.status == OrderStatus.AWAITING_COD }) {
                            BadgedBox(badge = { Badge { Text(orders.count { it.status == OrderStatus.PAID || it.status == OrderStatus.AWAITING_COD }.toString()) } }) { Text(title) }
                        } else {
                            Text(title)
                        }
                    })
                }
            }

            if (userRole == "parent/guardian") {
                when (selectedTab) {
                    0 -> ParentInventoryTab(items, viewModel, isUploading)
                    1 -> SwapRequestsTab(swaps, viewModel)
                    2 -> SwapChatsTab(swaps) { chattingSwap = it }
                    3 -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Account Settings") }
                }
            } else {
                when (selectedTab) {
                    0 -> VendorInventoryTab(items, viewModel, isUploading)
                    1 -> OrdersTab(orders, trackingInfo, viewModel)
                    2 -> LiveDealsTab(deals, viewModel)
                    3 -> FinanceTab(deals, disputes, viewModel)
                    4 -> DisputesTab(disputes, viewModel)
                }
            }
        }
    }

    chattingSwap?.let { request ->
        SwapChatDialog(request, viewModel) { chattingSwap = null }
    }
}

@Composable
fun ParentInventoryTab(items: List<ProductItem>, viewModel: VendorViewModel, isUploading: Boolean) {
    var showAddItemDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    val context = LocalContext.current
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items.isEmpty()) {
                item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No items listed yet", color = Color.Gray) } }
            }
            items(items) { item ->
                ProductInventoryCard(
                    item = item,
                    isParent = true,
                    onEdit = { editingProduct = item },
                    onDelete = { viewModel.deleteProduct(item.id) },
                    onAdRequest = { viewModel.updateAdSettings(item.id, it) }
                )
            }
        }
        
        FloatingActionButton(
            onClick = { showAddItemDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Item")
        }
    }

    if (showAddItemDialog) {
        AddItemDialog(isParent = true, onDismiss = { showAddItemDialog = false }, onAdd = { product, images ->
            viewModel.addProduct(context, product, images)
            showAddItemDialog = false
        }, isUploading = isUploading)
    }

    if (editingProduct != null) {
        AddItemDialog(
            isParent = true,
            existingProduct = editingProduct,
            onDismiss = { editingProduct = null },
            onAdd = { product, images ->
                viewModel.updateProduct(context, editingProduct!!.id, product, images)
                editingProduct = null
            },
            isUploading = isUploading
        )
    }
}

@Composable
fun ProductInventoryCard(
    item: ProductItem,
    isParent: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLaunchDeal: (() -> Unit)? = null,
    onAdRequest: (AdLevel) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showAdDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.imageUrls.getOrNull(0),
                contentDescription = null,
                modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                if (isParent) {
                    Text("Value: KES ${item.price}", style = MaterialTheme.typography.bodySmall)
                    Badge(containerColor = when (item.status.lowercase()) {
                        "reserved" -> Color(0xFFFFF3E0)
                        "exchanged" -> MaterialTheme.colorScheme.surfaceVariant
                        else -> Color(0xFFE8F5E9)
                    }) {
                        Text(
                            item.status.uppercase(),
                            color = when (item.status.lowercase()) {
                                "reserved" -> Color(0xFFE65100)
                                "exchanged" -> Color.Gray
                                else -> Color(0xFF2E7D32)
                            },
                            fontSize = 8.sp
                        )
                    }
                    VerificationBadge(item.verificationStatus)
                } else {
                    Text(item.listingType.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    when(item.listingType) {
                        ListingType.RETAIL -> Text("Retail: KES ${item.price}", style = MaterialTheme.typography.bodySmall)
                        ListingType.WHOLESALE -> Text("Wholesale: KES ${item.wholesalePrice} (Min ${item.minWholesaleQuantity})", style = MaterialTheme.typography.bodySmall)
                        ListingType.GROUP_DEAL -> Text("Group Price: KES ${item.groupPrice}", style = MaterialTheme.typography.bodySmall)
                        ListingType.SERVICE -> Text("Service Fee: KES ${item.price}", style = MaterialTheme.typography.bodySmall)
                        ListingType.PROFESSIONAL -> Text("Prof. Fee: KES ${item.price}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.promoLabel.isNotEmpty()) {
                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                            Text(item.promoLabel, fontSize = 8.sp)
                        }
                    }
                    if (item.adLevel != AdLevel.FREE) {
                        AdStatusBadge(item.adLevel, item.adStatus)
                    }
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }, enabled = !item.status.equals("Reserved", ignoreCase = true)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Advertise Product") },
                        onClick = {
                            showMenu = false
                            showAdDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.Campaign, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    )
                    if (onLaunchDeal != null && item.listingType == ListingType.GROUP_DEAL && !item.isGroupDeal) {
                        DropdownMenuItem(
                            text = { Text("Launch Deal") },
                            onClick = {
                                showMenu = false
                                onLaunchDeal()
                            },
                            leadingIcon = { Icon(Icons.Default.RocketLaunch, null) }
                        )
                    }
                }
            }
        }
    }

    if (showAdDialog) {
        AdSelectionDialog(
            currentLevel = item.adLevel,
            onDismiss = { showAdDialog = false },
            onConfirm = { level ->
                onAdRequest(level)
                showAdDialog = false
            }
        )
    }
}

@Composable
fun AdStatusBadge(level: AdLevel, status: AdStatus) {
    val (text, color) = when (status) {
        AdStatus.REQUESTED -> "Ad Requested" to Color.Gray
        AdStatus.WAITING_FOR_TRACTION -> "Queued" to Color(0xFF2196F3)
        AdStatus.ACTIVE -> "LIVE: ${level.name}" to Color(0xFF4CAF50)
        else -> "" to Color.Gray
    }
    if (text.isNotEmpty()) {
        Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color)) {
            Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AdSelectionDialog(currentLevel: AdLevel, onDismiss: () -> Unit, onConfirm: (AdLevel) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Ad Level") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ads will be reviewed and activated to reach more parents.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                AdLevel.entries.forEach { level ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onConfirm(level) },
                        colors = CardDefaults.cardColors(containerColor = if(level == currentLevel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(level.name, fontWeight = FontWeight.Bold)
                                val price = when(level) {
                                    AdLevel.FREE -> "Free"
                                    AdLevel.BASIC -> "KES 50"
                                    AdLevel.PREMIUM -> "KES 150"
                                    AdLevel.FEATURED -> "KES 500"
                                }
                                Text(price, style = MaterialTheme.typography.labelSmall)
                            }
                            if (level == currentLevel) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun VerificationBadge(status: VerificationStatus) {
    val (text, color) = when (status) {
        VerificationStatus.VERIFIED -> "Verified Product" to Color(0xFF4CAF50)
        VerificationStatus.PENDING -> "Verification Pending" to Color(0xFFFFA000)
        VerificationStatus.REJECTED -> "Rejected" to Color.Red
        VerificationStatus.UNVERIFIED -> "Unverified Product" to Color.Gray
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color)) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OrdersTab(orders: List<Order>, trackingInfo: Map<String, String>, viewModel: VendorViewModel) {
    var selectedOrderCategory by remember { mutableStateOf(PurchaseType.RETAIL) }
    
    val filteredOrders = remember(orders, selectedOrderCategory) {
        orders.filter { order ->
            if (selectedOrderCategory == PurchaseType.GROUP) {
                order.items.any { it.purchaseType == PurchaseType.GROUP }
            } else {
                order.items.any { it.purchaseType == selectedOrderCategory }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = when(selectedOrderCategory) {
                PurchaseType.RETAIL -> 0
                PurchaseType.GROUP -> 1
                PurchaseType.WHOLESALE -> 2
                else -> 0
            },
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            listOf(PurchaseType.RETAIL, PurchaseType.GROUP, PurchaseType.WHOLESALE).forEach { type ->
                Tab(
                    selected = selectedOrderCategory == type,
                    onClick = { selectedOrderCategory = type },
                    text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (filteredOrders.isEmpty()) {
                item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No $selectedOrderCategory orders found", color = Color.Gray) } }
            }
            items(filteredOrders, key = { it.id }) { order ->
                OrderCard(
                    order = order, 
                    liveTracking = trackingInfo[order.id],
                    onUpdateStatus = { viewModel.updateOrderStatus(order.id, it) }, 
                    onShipOrder = { tracking, courier -> viewModel.shipOrder(order.id, tracking, courier) },
                    onRefreshTracking = { viewModel.fetchLiveTracking(order.id) }
                )
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order, 
    liveTracking: String?, 
    onUpdateStatus: (OrderStatus) -> Unit, 
    onShipOrder: (String, String) -> Unit,
    onRefreshTracking: () -> Unit
) {
    var showShipDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Order #${order.id.take(8).uppercase()}", fontWeight = FontWeight.Bold)
                    Text("Date: ${sdf.format(order.createdAt.toDate())}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                StatusBadge(order.status)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Text("Buyer Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(order.buyerName, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(order.phoneNumber, style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text("Shipping/Logistics Information", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) Icons.Default.Store else Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) "Pickup Mtaani (COD)" else "Door Delivery",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) "Drop-off Agent: ${order.pickupPoint}" else "Address: ${order.shippingAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )

                if (order.status != OrderStatus.PENDING && order.status != OrderStatus.PAID && order.status != OrderStatus.AWAITING_COD) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Courier: ${order.courierName.ifEmpty { "N/A" }}", style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tracking: ", style = MaterialTheme.typography.labelSmall)
                                Text(order.trackingNumber.ifEmpty { "N/A" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                if (order.trackingNumber.isNotEmpty()) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { clipboardManager.setText(AnnotatedString(order.trackingNumber)) },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) {
                            IconButton(onClick = onRefreshTracking, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Tracking", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (liveTracking != null) {
                        Spacer(Modifier.height(4.dp))
                        Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                Spacer(Modifier.width(6.dp))
                                Text("Logistics Status: $liveTracking", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Items Ordered", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            order.items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.quantity}x ${item.productName}", style = MaterialTheme.typography.bodySmall)
                    Text("KES ${item.price * item.quantity}", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (order.status == OrderStatus.AWAITING_COD) "Payment Due (Agent)" else "Total Payment", fontWeight = FontWeight.Bold)
                Text("KES ${order.totalAmount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            if (order.status == OrderStatus.PAID || order.status == OrderStatus.AWAITING_COD) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showShipDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) Color(0xFFE91E63) else Color(0xFF2196F3))
                ) {
                    Icon(if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) Icons.Default.Store else Icons.Default.LocalShipping, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD -> "Taken to Pickup Mtaani Agent"
                            order.status == OrderStatus.AWAITING_COD -> "Ship COD Order"
                            else -> "Mark as Shipped"
                        }
                    )
                }
            } else if (order.status == OrderStatus.SHIPPED) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { onUpdateStatus(OrderStatus.DELIVERED) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirm Delivery to Agent/Buyer")
                }
            } else if (order.status == OrderStatus.DELIVERED) {
                Spacer(Modifier.height(8.dp))
                val message = if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) 
                    "Waiting for Pickup Mtaani to confirm cash collection." 
                    else "Waiting for Buyer to confirm receipt."
                Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), textAlign = TextAlign.Center)
                }
            }
        }
    }

    if (showShipDialog) {
        ShipOrderDialog(
            deliveryMethod = order.deliveryMethod,
            onDismiss = { showShipDialog = false },
            onConfirm = { tracking, courier ->
                onShipOrder(tracking, courier)
                showShipDialog = false
            }
        )
    }
}

@Composable
fun ShipOrderDialog(deliveryMethod: DeliveryMethod, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var trackingNumber by remember { mutableStateOf("") }
    var courierName by remember { mutableStateOf(if (deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) "Pickup Mtaani" else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) "Handover to Pickup Mtaani" else "Ship Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) 
                        "Take the package to your nearest Pickup Mtaani agent. Enter the tracking/reference they provide to trigger buyer notification." 
                    else "Enter tracking details so the buyer can monitor the package.", 
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    label = { Text("Tracking/Waybill Number") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = courierName,
                    onValueChange = { courierName = it },
                    label = { Text("Courier/Agent Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(trackingNumber, courierName) }) {
                Text(if (deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) "Handed Over" else "Confirm Shipment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SwapRequestsTab(swaps: List<SwapRequest>, viewModel: VendorViewModel) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var chattingSwap by remember { mutableStateOf<SwapRequest?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (swaps.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No swap requests yet. Browse the Swap Hub to find an item to exchange.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
        items(swaps) { request ->
            val isReceived = request.receiverId == uid
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (isReceived) "Request from ${request.senderName}" else "Sent to ${request.receiverName}", fontWeight = FontWeight.Bold)
                        Text(request.status.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (request.requestedItemImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = request.requestedItemImageUrl,
                                contentDescription = request.requestedItemName,
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text("Requested: ${request.requestedItemName.ifBlank { "Item details unavailable" }}", fontWeight = FontWeight.SemiBold)
                            if (request.requestedItemValue > 0) Text("Listed value: KES ${request.requestedItemValue}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    if (request.offeredItemId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                            if (request.offeredItemImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = request.offeredItemImageUrl,
                                    contentDescription = request.offeredItemName,
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column {
                                Text("Offered: ${request.offeredItemName.ifBlank { "Item details unavailable" }}", fontWeight = FontWeight.SemiBold)
                                if (request.offeredItemValue > 0) Text("Offered item value: KES ${request.offeredItemValue}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    } else {
                        Text("No physical item offered")
                    }
                    if (request.cashOffered > 0) {
                        Text("Cash top-up: KES ${request.cashOffered}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                    
                    if (isReceived && request.status == SwapRequestStatus.PENDING) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.updateSwapStatus(request.id, SwapRequestStatus.ACCEPTED) }, modifier = Modifier.weight(1f)) { Text("Accept") }
                            OutlinedButton(onClick = { viewModel.updateSwapStatus(request.id, SwapRequestStatus.REJECTED) }, modifier = Modifier.weight(1f)) { Text("Decline") }
                        }
                    } else if (!isReceived && request.status == SwapRequestStatus.PENDING) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.updateSwapStatus(request.id, SwapRequestStatus.CANCELLED) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel request")
                        }
                    }

                    if (request.status == SwapRequestStatus.ACCEPTED || request.status == SwapRequestStatus.COMPLETED) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { chattingSwap = request }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Chat")
                        }
                        if (request.status == SwapRequestStatus.ACCEPTED) {
                            val selfConfirmed = if (isReceived) request.receiverConfirmed else request.senderConfirmed
                            val otherConfirmed = if (isReceived) request.senderConfirmed else request.receiverConfirmed
                            Text(
                                when {
                                    selfConfirmed && otherConfirmed -> "Both parents confirmed the exchange."
                                    selfConfirmed -> "Your confirmation is saved. Waiting for the other parent."
                                    otherConfirmed -> "The other parent confirmed. Confirm when your exchange is complete."
                                    else -> "Confirm only after you have exchanged the items."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            if (!selfConfirmed) {
                                OutlinedButton(
                                    onClick = { viewModel.confirmSwapCompleted(request.id) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) { Text("Confirm items exchanged") }
                            }
                            if (!request.senderConfirmed && !request.receiverConfirmed) {
                                TextButton(
                                    onClick = { viewModel.updateSwapStatus(request.id, SwapRequestStatus.CANCELLED) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Cancel this exchange") }
                            }
                        } else {
                            Text("Exchange completed by both parents", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
    chattingSwap?.let { request -> SwapChatDialog(request, viewModel) { chattingSwap = null } }
}

@Composable
private fun SwapChatsTab(swaps: List<SwapRequest>, onOpenChat: (SwapRequest) -> Unit) {
    val activeSwaps = swaps.filter { it.status == SwapRequestStatus.ACCEPTED || it.status == SwapRequestStatus.COMPLETED }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (activeSwaps.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Chats appear here after a parent accepts your swap request.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
        items(activeSwaps, key = { it.id }) { request ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val otherParent = if (request.senderId == uid) request.receiverName else request.senderName
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpenChat(request) }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(otherParent, fontWeight = FontWeight.Bold)
                        Text(request.requestedItemName.ifBlank { "Swap discussion" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text("Message", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SwapChatDialog(request: SwapRequest, viewModel: VendorViewModel, onDismiss: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var messages by remember(request.id) { mutableStateOf<List<SwapChatMessage>>(emptyList()) }
    var draft by remember(request.id) { mutableStateOf("") }
    val db = remember { FirebaseFirestore.getInstance() }

    DisposableEffect(request.id) {
        val listener = db.collection("swap_requests").document(request.id).collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, _ ->
                messages = snapshot?.documents?.mapNotNull { document ->
                    val text = document.getString("text") ?: return@mapNotNull null
                    SwapChatMessage(
                        id = document.id,
                        senderId = document.getString("senderId").orEmpty(),
                        senderName = document.getString("senderName").orEmpty(),
                        text = text,
                        createdAt = document.getTimestamp("createdAt")
                    )
                }.orEmpty()
            }
        onDispose { listener.remove() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp, max = 680.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Swap chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${request.senderName} ↔ ${request.receiverName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close chat") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (messages.isEmpty()) {
                        item { Text("Agree on a safe time and public place to exchange items.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
                    }
                    items(messages, key = { it.id }) { message ->
                        val isMine = message.senderId == uid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
                            Surface(
                                color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    if (!isMine) Text(message.senderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 2000) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message the other parent") },
                        maxLines = 4
                    )
                    IconButton(onClick = {
                        val message = draft.trim()
                        if (message.isNotEmpty()) {
                            viewModel.sendSwapMessage(request, message)
                            draft = ""
                        }
                    }, enabled = draft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AddItemDialog(
    isParent: Boolean,
    existingProduct: ProductItem? = null,
    onDismiss: () -> Unit,
    onAdd: (ProductItem, List<Any>) -> Unit,
    isUploading: Boolean
) {
    var name by remember { mutableStateOf(existingProduct?.name ?: "") }
    var category by remember { mutableStateOf(existingProduct?.category ?: if (isParent) "Uniforms" else "School Supplies") }
    var listingType by remember { mutableStateOf(existingProduct?.listingType ?: ListingType.RETAIL) }
    var condition by remember { mutableStateOf(existingProduct?.condition ?: "New") }
    var retailPrice by remember { mutableStateOf(existingProduct?.price?.toString() ?: "") }
    var wholesalePrice by remember { mutableStateOf(existingProduct?.wholesalePrice?.toString() ?: "") }
    var minWholesaleQty by remember { mutableStateOf(existingProduct?.minWholesaleQuantity?.toString() ?: "") }
    var groupPrice by remember { mutableStateOf(existingProduct?.groupPrice?.toString() ?: "") }
    var minGroupQty by remember { mutableStateOf(existingProduct?.minGroupQuantity?.toString() ?: "") }
    var description by remember { mutableStateOf(existingProduct?.description ?: "") }
    var swapPref by remember { mutableStateOf(existingProduct?.swapPreference ?: "") }
    
    // New Fields for POS/Marketplace Differentiation
    var promoLabel by remember { mutableStateOf(existingProduct?.promoLabel ?: "") }
    var stockQty by remember { mutableStateOf(if (existingProduct?.stockQuantity == -1) "" else existingProduct?.stockQuantity?.toString() ?: "") }
    var colorsStr by remember { mutableStateOf(existingProduct?.colors?.joinToString(", ") ?: "") }
    var sku by remember { mutableStateOf(existingProduct?.sku ?: "") }
    var brand by remember { mutableStateOf(existingProduct?.brand ?: "") }
    
    var images by remember { 
        mutableStateOf(existingProduct?.imageUrls?.map { it as Any } ?: emptyList<Any>()) 
    }
    
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val spaceLeft = 5 - images.size
        if (spaceLeft > 0) {
            images = images + uris.take(spaceLeft).map { it as Any }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingProduct != null) "Edit Item" else if (isParent) "List New Item for Swap" else "Add New Product/Service") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                if (images.isNotEmpty()) {
                    Text("Product Photos (${images.size}/5) - Tap to set as Display Image", style = MaterialTheme.typography.labelMedium)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(images) { index, source ->
                            PhotoPreviewItem(
                                source = source, 
                                isMain = index == 0,
                                onDelete = {
                                    images = images.filterIndexed { i, _ -> i != index }
                                },
                                onSetMain = {
                                    val mutable = images.toMutableList()
                                    val item = mutable.removeAt(index)
                                    mutable.add(0, item)
                                    images = mutable
                                }
                            )
                        }
                    }
                }

                if (images.size < 5) {
                    Button(
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Photos")
                    }
                }
                
                if (!isParent) {
                    Text("Listing Category \u0026 Style:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListingType.entries.forEach { type ->
                            FilterChip(
                                selected = listingType == type,
                                onClick = { listingType = type },
                                label = { Text(type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = promoLabel, 
                        onValueChange = { promoLabel = it }, 
                        label = { Text("Promo Label (e.g. Best Seller, Sale)") }, 
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Appears as a badge on the product") }
                    )
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Specific Category") }, modifier = Modifier.fillMaxWidth())
                
                if (isParent) {
                    OutlinedTextField(value = condition, onValueChange = { condition = it }, label = { Text("Condition") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = retailPrice, onValueChange = { retailPrice = it }, label = { Text("Estimated Value (KES)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = swapPref, onValueChange = { swapPref = it }, label = { Text("What do you want in return?") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(
                        value = brand, 
                        onValueChange = { brand = it }, 
                        label = { Text("Brand Name") }, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sku, 
                        onValueChange = { sku = it }, 
                        label = { Text("SKU/Serial Number") }, 
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("For POS inventory tracking") }
                    )
                    OutlinedTextField(
                        value = stockQty, 
                        onValueChange = { stockQty = it }, 
                        label = { Text("Stock Quantity (Leave blank for unlimited)") }, 
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = colorsStr, 
                        onValueChange = { colorsStr = it }, 
                        label = { Text("Variations/Colors (Comma separated)") }, 
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Red, Blue, Large, Small") }
                    )
                    
                    when(listingType) {
                        ListingType.RETAIL, ListingType.SERVICE, ListingType.PROFESSIONAL -> {
                            OutlinedTextField(value = retailPrice, onValueChange = { retailPrice = it }, label = { Text("Price/Fee (KES)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        ListingType.WHOLESALE -> {
                            OutlinedTextField(value = wholesalePrice, onValueChange = { wholesalePrice = it }, label = { Text("Wholesale Price (KES)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = minWholesaleQty, onValueChange = { minWholesaleQty = it }, label = { Text("Min Wholesale Qty") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        ListingType.GROUP_DEAL -> {
                            OutlinedTextField(value = groupPrice, onValueChange = { groupPrice = it }, label = { Text("Group Price (KES)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = minGroupQty, onValueChange = { minGroupQty = it }, label = { Text("Min Buyers") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = retailPrice, onValueChange = { retailPrice = it }, label = { Text("Original Price (KES)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    }
                }
                
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        },
        confirmButton = {
            Button(onClick = { 
                val product = (existingProduct ?: ProductItem()).copy(
                    name = name,
                    category = category,
                    condition = condition,
                    price = retailPrice.toDoubleOrNull() ?: 0.0,
                    wholesalePrice = wholesalePrice.toDoubleOrNull() ?: 0.0,
                    minWholesaleQuantity = minWholesaleQty.toDoubleOrNull() ?: 0.0,
                    groupPrice = groupPrice.toDoubleOrNull() ?: 0.0,
                    minGroupQuantity = minGroupQty.toIntOrNull() ?: 0,
                    description = description,
                    swapPreference = swapPref,
                    isForSwap = isParent,
                    listingType = if (isParent) ListingType.RETAIL else listingType,
                    promoLabel = promoLabel,
                    stockQuantity = stockQty.toIntOrNull() ?: -1,
                    colors = colorsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    sku = sku,
                    brand = brand
                )
                onAdd(product, images)
            }, enabled = !isUploading && name.isNotEmpty() && images.isNotEmpty()) {
                Text(if (isUploading) "Uploading..." else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PhotoPreviewItem(
    source: Any, 
    isMain: Boolean,
    onDelete: () -> Unit,
    onSetMain: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSetMain() }
            .then(if (isMain) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        AsyncImage(
            model = source,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        if (isMain) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Text(
                    "Main Image", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = Color.White, 
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 2.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .padding(4.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun DisputesTab(disputes: List<Order>, viewModel: VendorViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (disputes.isEmpty()) {
            item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No active disputes", color = Color.Gray) } }
        }
        items(disputes) { order ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dispute on #${order.id.take(6)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("Buyer: ${order.buyerName}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Reason: ${order.disputeReason.ifEmpty { "No reason provided" }}", style = MaterialTheme.typography.bodyMedium)
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.acceptRefund(order.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Accept Refund") }
                        
                        OutlinedButton(
                            onClick = { /* Contact Admin */ },
                            modifier = Modifier.weight(1f)
                        ) { Text("Contact Support") }
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceTab(deals: List<GroupDeal>, disputes: List<Order>, viewModel: VendorViewModel) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Financial Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Trust Badge (Verified Seller)", fontWeight = FontWeight.Bold)
                Text("Get verified to increase buyer confidence and reach more users.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.requestVerification() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Verified, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply for Verification Badge")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Payout Balance", style = MaterialTheme.typography.labelSmall)
                val total = deals.filter { it.status != DealStatus.CANCELLED }.sumOf { it.totalCollected }
                Text("KES $total", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                
                if (disputes.isNotEmpty()) {
                    Text("Held in Disputes: KES ${disputes.sumOf { it.totalAmount }}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = { }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Request Payout to M-Pesa") }
            }
        }
    }
}

@Composable fun LiveDealsTab(deals: List<GroupDeal>, viewModel: VendorViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (deals.isEmpty()) {
            item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No active group deals", color = Color.Gray) } }
        }
        items(deals) { deal ->
            DealMonitoringCard(deal, onUpdateStatus = { viewModel.updateDealStatus(deal.id, it) })
        }
    }
}

@Composable fun VendorInventoryTab(items: List<ProductItem>, viewModel: VendorViewModel, isUploading: Boolean) { 
    var showCreateDealDialog by remember { mutableStateOf<ProductItem?>(null) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items.isEmpty()) {
                item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No products added yet", color = Color.Gray) } }
            }
            items(items) { item ->
                ProductInventoryCard(
                    item = item,
                    isParent = false,
                    onEdit = { editingProduct = item },
                    onDelete = { viewModel.deleteProduct(item.id) },
                    onLaunchDeal = { showCreateDealDialog = item },
                    onAdRequest = { viewModel.updateAdSettings(item.id, it) }
                )
            }
        }

        FloatingActionButton(
            onClick = { showAddProductDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Product")
        }
    }

    if (showAddProductDialog) {
        AddItemDialog(isParent = false, onDismiss = { showAddProductDialog = false }, onAdd = { product, images ->
            viewModel.addProduct(context, product, images)
            showAddProductDialog = false
        }, isUploading = isUploading)
    }

    if (editingProduct != null) {
        AddItemDialog(
            isParent = false,
            existingProduct = editingProduct,
            onDismiss = { editingProduct = null },
            onAdd = { product, images ->
                viewModel.updateProduct(context, editingProduct!!.id, product, images)
                editingProduct = null
            },
            isUploading = isUploading
        )
    }

    if (showCreateDealDialog != null) {
        CreateDealDialog(product = showCreateDealDialog!!, onDismiss = { showCreateDealDialog = null }, onCreate = { min, hours ->
            viewModel.createGroupDeal(showCreateDealDialog!!, min, hours)
            showCreateDealDialog = null
        })
    }
}

@Composable fun DealMonitoringCard(deal: GroupDeal, onUpdateStatus: (DealStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val paidCount = deal.buyerDetails.values.count { it.isPaid }
    val unpaidCount = deal.buyerDetails.size - paidCount

    Card(modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(deal.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("KES ${deal.pricePerPerson} in group", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                DealStatusBadge(deal.status)
            }
            Spacer(Modifier.height(12.dp))
            val progress = (deal.currentJoined.toFloat() / deal.minRequired.toFloat()).coerceAtMost(1f)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), color = if (progress >= 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${deal.currentJoined} / ${deal.minRequired} Participants Joined", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Collected: KES ${deal.totalCollected}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text("$paidCount Checked Out", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
                Surface(color = Color(0xFFFFA000).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text("$unpaidCount Committed", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA000), fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("👥 Participants \u0026 Shipping Info", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    deal.buyerDetails.values.forEach { buyer ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(buyer.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(text = if (buyer.isPaid) "PAID" else "PENDING", color = if (buyer.isPaid) Color(0xFF4CAF50) else Color(0xFFFFA000), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                if (buyer.isPaid || buyer.shippingAddress.isNotEmpty() || buyer.pickupPoint.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if(buyer.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) Icons.Default.Store else Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (buyer.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) "Pickup: ${buyer.pickupPoint}" else "Ship: ${buyer.shippingAddress}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                    Text("Tel: ${buyer.phoneNumber}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            if (deal.status == DealStatus.LOCKED) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onUpdateStatus(DealStatus.SHIPPED) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocalShipping, null); Spacer(Modifier.width(8.dp)); Text("Ready to Ship")
                }
            }
        }
    }
}

@Composable fun DealStatusBadge(status: DealStatus) {
    val color = when(status) {
        DealStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        DealStatus.LOCKED -> Color(0xFF2196F3)
        DealStatus.SHIPPED -> Color(0xFFFFA000)
        DealStatus.COMPLETED -> Color(0xFF4CAF50)
        else -> Color.Gray
    }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color)) {
        Text(status.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable fun CreateDealDialog(product: ProductItem, onDismiss: () -> Unit, onCreate: (Int, Int) -> Unit) {
    var minRequired by remember { mutableStateOf(product.minGroupQuantity.toString()) }
    var duration by remember { mutableStateOf("24") }
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("Launch Group Buying") }, 
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.name, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = minRequired, onValueChange = { minRequired = it }, label = { Text("Min Buyers Required") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration (Hours)") }, modifier = Modifier.fillMaxWidth())
            }
        }, 
        confirmButton = { 
            Button(onClick = { onCreate(minRequired.toIntOrNull() ?: 5, duration.toIntOrNull() ?: 24) }) { 
                Text("Launch") 
            } 
        }, 
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel") 
            } 
        }
    )
}
