package com.joseph.cbchubpro.ui.discovery

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.joseph.cbchubpro.ui.management.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BuyerOrdersViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders = _orders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _trackingInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val trackingInfo = _trackingInfo.asStateFlow()

    private var platformSettings: PlatformSettings? = null
    private var listenerRegistration: ListenerRegistration? = null

    init {
        fetchPlatformSettings()
        fetchOrders()
    }

    private fun fetchPlatformSettings() {
        db.collection("platform").document("settings").addSnapshotListener { snapshot, _ ->
            platformSettings = snapshot?.toObject(PlatformSettings::class.java)
        }
    }

    fun fetchOrders() {
        val uid = auth.currentUser?.uid ?: return
        _isLoading.value = true
        
        listenerRegistration?.remove()
        
        listenerRegistration = db.collection("orders")
            .whereEqualTo("buyerId", uid)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("BuyerOrders", "Error fetching orders: ${error.message}", error)
                    return@addSnapshotListener
                }
                
                val ordersList = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Order::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("BuyerOrders", "Error deserializing order ${doc.id}: ${e.message}")
                        null
                    }
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                
                _orders.value = ordersList
                
                // Automatically fetch tracking for PM orders
                ordersList.filter { it.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD && it.status != OrderStatus.COMPLETED && it.status != OrderStatus.CANCELLED }
                    .forEach { fetchLiveTracking(it.id) }
            }
    }

    private fun fetchLiveTracking(orderId: String) {
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
                    val responseBody = response.body?.string() ?: "{}"
                    val json = JSONObject(responseBody)
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

    fun confirmReceipt(orderId: String) {
        viewModelScope.launch {
            try {
                db.collection("orders").document(orderId).update("status", OrderStatus.COMPLETED).await()
            } catch (e: Exception) {
                Log.e("BuyerOrders", "Error confirming receipt: ${e.message}")
            }
        }
    }

    fun deleteOrder(orderId: String) {
        viewModelScope.launch {
            try {
                db.collection("orders").document(orderId).delete().await()
            } catch (e: Exception) {
                Log.e("BuyerOrders", "Error deleting order: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerOrdersScreen(
    onBackClicked: () -> Unit,
    viewModel: BuyerOrdersViewModel = viewModel()
) {
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val trackingInfo by viewModel.trackingInfo.collectAsState()
    var orderToDelete by remember { mutableStateOf<Order?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchOrders()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Orders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading && orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("You haven't placed any orders yet.", color = Color.Gray)
                    Button(onClick = { viewModel.fetchOrders() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Refresh")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    BuyerOrderCard(
                        order = order,
                        liveTrackingStatus = trackingInfo[order.id],
                        onConfirmReceipt = { viewModel.confirmReceipt(order.id) },
                        onDeleteRequest = { orderToDelete = order }
                    )
                }
            }
        }

        if (orderToDelete != null) {
            AlertDialog(
                onDismissRequest = { orderToDelete = null },
                title = { Text("Delete Order") },
                text = { Text("Are you sure you want to delete this order record? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            orderToDelete?.let { viewModel.deleteOrder(it.id) }
                            orderToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { orderToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun BuyerOrderCard(
    order: Order,
    liveTrackingStatus: String?,
    onConfirmReceipt: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Order #${order.id.take(8).uppercase()}", fontWeight = FontWeight.Bold)
                    Text(order.createdAt.toDate().toString().take(16), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(order.status)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDeleteRequest, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Vendor: ${order.vendorName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            
            Spacer(Modifier.height(8.dp))
            
            order.items.forEach { item ->
                Text("${item.quantity}x ${item.productName}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Text("Total: KES ${order.totalAmount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            if (order.status != OrderStatus.CANCELLED) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) Icons.Default.Store else Icons.Default.LocalShipping,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) "Pickup Mtaani Logistics" else "Logistics Information",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        
                        // Live Tracking Info from API
                        if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD && liveTrackingStatus != null) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF4CAF50)))
                                Spacer(Modifier.width(8.dp))
                                Text("Live Status: $liveTrackingStatus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                        }

                        if (order.trackingNumber.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text("Tracking Number", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(order.trackingNumber, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                                IconButton(onClick = { clipboardManager.setText(AnnotatedString(order.trackingNumber)) }) {
                                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Store, null, modifier = Modifier.size(16.dp).padding(top = 2.dp), tint = Color(0xFFE91E63))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Selected Pickup Point", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(order.pickupPoint, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    if (order.status == OrderStatus.AWAITING_COD) {
                                        Text("Pay KES ${order.totalAmount} upon arrival at this point.", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE91E63))
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Shipping Address", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text(order.shippingAddress, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (order.status == OrderStatus.DELIVERED) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onConfirmReceipt,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm Receipt")
                }
                Text(
                    "Click only when you have received the item. This releases funds to the vendor.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
