package com.joseph.cbchubpro.ui.management

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class CartViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems = _cartItems.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _checkoutStatus = MutableStateFlow<String?>(null)
    val checkoutStatus = _checkoutStatus.asStateFlow()

    private val _platformSettings = MutableStateFlow(PlatformSettings())
    val platformSettings = _platformSettings.asStateFlow()

    private val _pickupPoints = MutableStateFlow<List<PickupPoint>>(emptyList())
    val pickupPoints = _pickupPoints.asStateFlow()

    init {
        fetchPlatformConfig()
    }

    private fun fetchPlatformConfig() {
        db.collection("platform").document("settings").addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            snapshot?.toObject(PlatformSettings::class.java)?.let {
                _platformSettings.value = it
                Log.d("Config", "Settings Synced")
                fetchPickupMtaaniLocations()
            }
        }
    }

    fun fetchPickupMtaaniLocations() {
        val config = _platformSettings.value
        if (config.pickupMtaaniApiKey.isEmpty()) {
            // Fallback to some defaults if API key is missing
            _pickupPoints.value = listOf(
                PickupPoint("Westlands - Near Mall", 100.0, "Nairobi"),
                PickupPoint("CBD - Archives", 100.0, "Nairobi"),
                PickupPoint("Kasarani - Mwiki Stage", 150.0, "Nairobi"),
                PickupPoint("Eldoret - Main Stage", 250.0, "Upcountry"),
                PickupPoint("Kisumu - City Center", 250.0, "Upcountry")
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.pickupmtaani.com/v1/locations")
                    .addHeader("Authorization", "Bearer ${config.pickupMtaaniApiSecret}")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonData = response.body?.string() ?: ""
                    val array = JSONArray(jsonData)
                    val points = mutableListOf<PickupPoint>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        points.add(PickupPoint(
                            name = obj.getString("name"),
                            baseFee = obj.getDouble("fee"),
                            zone = obj.optString("zone", "General")
                        ))
                    }
                    _pickupPoints.value = points
                }
            } catch (e: Exception) {
                Log.e("PickupMtaani", "Failed to fetch locations", e)
            }
        }
    }

    fun checkoutWithPickupMtaani(phoneNumber: String, selectedPoint: PickupPoint, onSuccess: () -> Unit) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val orderId = "PM-" + UUID.randomUUID().toString().take(8).uppercase()
                val currentCart = _cartItems.value
                val subtotal = currentCart.sumOf { it.price * it.quantity }
                
                // Use API fee + 50 bob revenue
                val deliveryFee = selectedPoint.baseFee + FinanceConstants.PLATFORM_HUB_REVENUE
                val total = subtotal + deliveryFee
                
                val order = Order(
                    id = orderId,
                    buyerId = currentUser.uid,
                    buyerName = currentUser.displayName ?: "User",
                    vendorId = currentCart.firstOrNull()?.vendorId ?: "",
                    vendorName = currentCart.firstOrNull()?.productName?.substringBefore(" ") ?: "Vendor", 
                    items = currentCart,
                    subtotal = subtotal,
                    deliveryFee = deliveryFee,
                    platformRevenue = FinanceConstants.PLATFORM_HUB_REVENUE,
                    totalAmount = total,
                    pickupPoint = selectedPoint.name,
                    phoneNumber = phoneNumber,
                    deliveryMethod = DeliveryMethod.PICKUP_MTAANI_COD,
                    status = OrderStatus.AWAITING_COD
                )

                // Update Group Deals if applicable
                currentCart.forEach { item ->
                    if (item.dealId.isNotEmpty()) {
                        updateGroupDealBuyerInfo(item.dealId, currentUser.uid, phoneNumber, selectedPoint.name, DeliveryMethod.PICKUP_MTAANI_COD)
                    }
                }

                registerWithPickupMtaani(orderId, total, selectedPoint.name)

                db.collection("orders").document(orderId).set(order).await()

                _checkoutStatus.value = "✅ Order Placed! Total KES $total (Includes KES $deliveryFee delivery). Pay at ${selectedPoint.name} agent."
                clearCart()
                onSuccess()
            } catch (e: Exception) {
                _checkoutStatus.value = "Error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun updateGroupDealBuyerInfo(dealId: String, userId: String, phone: String, location: String, method: DeliveryMethod) {
        try {
            val dealRef = db.collection("group_deals").document(dealId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(dealRef)
                val deal = snapshot.toObject(GroupDeal::class.java) ?: return@runTransaction
                val details = deal.buyerDetails.toMutableMap()
                val current = details[userId] ?: GroupBuyerInfo(name = auth.currentUser?.displayName ?: "User")
                
                if (!current.isPaid) {
                    transaction.update(dealRef, "totalCollected", deal.totalCollected + deal.pricePerPerson)
                }

                details[userId] = current.copy(
                    phoneNumber = phone,
                    deliveryMethod = method,
                    shippingAddress = if(method == DeliveryMethod.DOOR_DELIVERY) location else "",
                    pickupPoint = if(method == DeliveryMethod.PICKUP_MTAANI_COD) location else "",
                    isPaid = true // In COD, checkout means commitment
                )
                transaction.update(dealRef, "buyerDetails", details)
            }.await()
        } catch (e: Exception) {
            Log.e("CartViewModel", "Failed to update group deal buyer info", e)
        }
    }

    private suspend fun registerWithPickupMtaani(orderId: String, amount: Double, pickupPoint: String): Boolean {
        val config = _platformSettings.value
        if (config.pickupMtaaniApiKey.isEmpty()) {
            Log.d("PickupMtaani", "No API Key configured. Skipping network call.")
            return true
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("apiKey", config.pickupMtaaniApiKey)
                    put("orderId", orderId)
                    put("amount", amount)
                    put("pickupPoint", pickupPoint)
                    put("paymentMethod", "COD")
                }
                
                val request = Request.Builder()
                    .url("https://api.pickupmtaani.com/v1/cod/register") 
                    .addHeader("Authorization", "Bearer ${config.pickupMtaaniApiSecret}")
                    .post(body.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                val response = client.newCall(request).execute()
                Log.d("PickupMtaani", "API Response: ${response.code}")
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("PickupMtaani", "Registration failed", e)
                true 
            }
        }
    }

    fun addToCart(product: ProductItem, type: PurchaseType, dealId: String = "") {
        val price = when(type) {
            PurchaseType.RETAIL -> product.price
            PurchaseType.GROUP -> product.groupPrice
            PurchaseType.WHOLESALE -> product.wholesalePrice
            PurchaseType.SERVICE -> product.price
            PurchaseType.PROFESSIONAL -> product.price
        }
        val quantity: Double = if (type == PurchaseType.WHOLESALE) product.minWholesaleQuantity else 1.0

        _cartItems.update { current ->
            val existing = current.find { it.productId == product.id && it.purchaseType == type && it.dealId == dealId }
            if (existing != null) {
                current.map { if (it.productId == product.id && it.purchaseType == type && it.dealId == dealId) it.copy(quantity = it.quantity + quantity) else it }
            } else {
                current + CartItem(
                    productId = product.id,
                    productName = product.name,
                    price = price,
                    quantity = quantity,
                    imageUrl = product.imageUrls.firstOrNull() ?: "",
                    vendorId = product.vendorId,
                    purchaseType = type,
                    dealId = dealId
                )
            }
        }
    }

    fun removeFromCart(productId: String, type: PurchaseType) {
        _cartItems.update { current -> current.filterNot { it.productId == productId && it.purchaseType == type } }
    }

    fun clearCart() { _cartItems.value = emptyList() }
    fun clearStatus() { _checkoutStatus.value = null }
}
