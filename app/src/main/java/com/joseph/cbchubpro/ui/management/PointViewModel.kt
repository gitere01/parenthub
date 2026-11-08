package com.joseph.cbchubpro.ui.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.firestore.SetOptions
import com.joseph.cbchubpro.network.PesapalAuthRequest
import com.joseph.cbchubpro.network.PesapalService
import com.joseph.cbchubpro.network.PesapalIpnRequest
import com.joseph.cbchubpro.network.PesapalIpnResponse
import com.joseph.cbchubpro.network.PesapalOrderResponse
import com.joseph.cbchubpro.network.PesapalDisbursementRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class PointViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _pointBalance = MutableStateFlow<PointBalance?>(null)
    val pointBalance: StateFlow<PointBalance?> = _pointBalance.asStateFlow()

    private val _transactionStatus = MutableStateFlow<ActionResultState>(ActionResultState.Idle)
    val transactionStatus: StateFlow<ActionResultState> = _transactionStatus.asStateFlow()

    private var _platformSettings = MutableStateFlow<PlatformSettings?>(null)

    private fun getPesapalService(): PesapalService {
        val logging = HttpLoggingInterceptor { message -> 
            Log.d("PesaPal-API", message) 
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // Production URL for V3
        val isLive = _platformSettings.value?.liveMode ?: true
        val baseUrl = if (isLive) "https://pay.pesapal.com/v3/" else "https://cybqa.pesapal.com/pesapalv3/"
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PesapalService::class.java)
    }

    init {
        fetchPointBalance()
        fetchPlatformSettings()
    }

    private fun fetchPlatformSettings() {
        viewModelScope.launch {
            try {
                // Looking in 'settings' collection as requested
                val snapshot = firestore.collection("settings").document("pesapal").get().await()
                if (snapshot.exists()) {
                    _platformSettings.value = snapshot.toObject(PlatformSettings::class.java)
                    Log.d("PesaPal", "Production settings loaded from Firestore")
                } else {
                    Log.w("PesaPal", "Settings document 'pesapal' not found in 'settings' collection")
                }
            } catch (e: Exception) {
                Log.e("PesaPal", "Failed to load settings from Firestore", e)
            }
        }
    }

    fun fetchPointBalance() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("point_balances").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    _pointBalance.value = snapshot.toObject(PointBalance::class.java)
                }
            }
    }

    fun buyPoints(amount: Double, email: String, phoneNumber: String, firstName: String, lastName: String) {
        val userId = auth.currentUser?.uid ?: return
        val settings = _platformSettings.value ?: PlatformSettings()
        
        // Use the credentials you provided as defaults
        val consumerKey = settings.pesapalConsumerKey.trim().ifEmpty { "VdiTLwH6c9jD8vVZqlvVoS29QfPv/saf" }
        val consumerSecret = settings.pesapalConsumerSecret.trim().ifEmpty { "MCy2o8/hKrV7UfPSnpdYAXUFxSg=" }
        var ipnId = settings.pesapalIpnId.trim()
        val ipnUrl = "https://pesapalipn-vs7fnli2hq-uc.a.run.app"

        _transactionStatus.value = ActionResultState.Loading

        viewModelScope.launch {
            try {
                val service = getPesapalService()
                
                // 1. Authenticate
                Log.d("PesaPal", "Step 1: Authenticating with Production URL...")
                val authResponse = try {
                    service.getAuthToken(PesapalAuthRequest(consumerKey, consumerSecret))
                } catch (e: HttpException) {
                    val errorBody = e.response()?.errorBody()?.string() ?: ""
                    Log.e("PesaPal", "Auth Error: $errorBody")
                    _transactionStatus.value = ActionResultState.Error("Auth Failed: Check PesaPal Credentials.")
                    return@launch
                }

                if (authResponse.token.isEmpty()) {
                    _transactionStatus.value = ActionResultState.Error("Auth Failed: No token returned.")
                    return@launch
                }
                val token = "Bearer ${authResponse.token}"

                // 2. Resolve IPN ID
                if (ipnId.isEmpty()) {
                    Log.d("PesaPal", "Step 2: Resolving IPN ID...")
                    try {
                        val ipnResponse = service.registerIPN(token, PesapalIpnRequest(url = ipnUrl))
                        ipnId = ipnResponse.ipnId ?: ipnResponse.ipnIdGuid ?: ""
                    } catch (e: HttpException) {
                        val errorBody = e.response()?.errorBody()?.string() ?: ""
                        if (e.code() == 400 || errorBody.contains("exists", true)) {
                             val registeredIPNs = service.getRegisteredIPNs(token)
                             val matchingIpn = registeredIPNs.find { it.url?.contains("vs7fnli2hq") == true } 
                                              ?: registeredIPNs.firstOrNull()
                             ipnId = matchingIpn?.ipnId ?: matchingIpn?.ipnIdGuid ?: ""
                        }
                    }
                }

                if (ipnId.isEmpty()) {
                    _transactionStatus.value = ActionResultState.Error("Could not resolve IPN ID.")
                    return@launch
                }
                
                // Update Firestore with the found IPN ID
                if (settings.pesapalIpnId != ipnId) {
                    firestore.collection("settings").document("pesapal").update("pesapalIpnId", ipnId)
                }

                // 3. Submit Order
                Log.d("PesaPal", "Step 3: Submitting Order...")
                val orderId = firestore.collection("point_transactions").document().id
                val orderRequest = PesapalOrderRequest(
                    id = orderId,
                    amount = amount,
                    description = "Purchase of gift points",
                    callback_url = ipnUrl, 
                    notification_id = ipnId,
                    billing_address = PesapalBillingAddress(
                        email_address = email,
                        phone_number = phoneNumber,
                        first_name = firstName,
                        last_name = lastName
                    )
                )

                val orderResponse = service.submitOrder(token, orderRequest)
                if (orderResponse.redirect_url.isNullOrEmpty()) {
                    _transactionStatus.value = ActionResultState.Error("Order Failed: ${orderResponse.message}")
                    return@launch
                }

                // 4. Record Transaction
                val transaction = PointTransaction(
                    id = orderId,
                    userId = userId,
                    amountPaid = amount,
                    pointsAllocated = amount * 0.85,
                    pesapalOrderTrackingId = orderResponse.order_tracking_id ?: "",
                    status = "PENDING",
                    type = PointTransactionType.PURCHASE
                )
                firestore.collection("point_transactions").document(orderId).set(transaction).await()

                _transactionStatus.value = ActionResultState.Success(orderResponse.redirect_url)
            } catch (e: Exception) {
                Log.e("PesaPal", "Critical Error", e)
                _transactionStatus.value = ActionResultState.Error("An error occurred: ${e.message}")
            }
        }
    }

    /**
     * Call this when the user returns to the app to verify payment manually and add points
     */
    fun verifyPayment(orderTrackingId: String) {
        val settings = _platformSettings.value ?: PlatformSettings()
        val consumerKey = settings.pesapalConsumerKey.trim().ifEmpty { "VdiTLwH6c9jD8vVZqlvVoS29QfPv/saf" }
        val consumerSecret = settings.pesapalConsumerSecret.trim().ifEmpty { "MCy2o8/hKrV7UfPSnpdYAXUFxSg=" }

        _transactionStatus.value = ActionResultState.Loading

        viewModelScope.launch {
            try {
                val service = getPesapalService()
                
                // 1. Get Auth Token
                val authResponse = service.getAuthToken(PesapalAuthRequest(consumerKey, consumerSecret))
                val token = "Bearer ${authResponse.token}"

                // 2. Query PesaPal for the current status
                val statusResponse = service.getTransactionStatus(token, orderTrackingId)
                Log.d("PesaPal", "Manual Verification Status: ${statusResponse.status}")

                if (statusResponse.status == "Completed") {
                    // 3. Find the transaction record in Firestore
                    val transactions = firestore.collection("point_transactions")
                        .whereEqualTo("pesapalOrderTrackingId", orderTrackingId)
                        .get().await()

                    if (transactions.isEmpty) {
                        _transactionStatus.value = ActionResultState.Error("Transaction record not found.")
                        return@launch
                    }

                    val transDoc = transactions.documents.first()
                    val transaction = transDoc.toObject(PointTransaction::class.java)!!

                    if (transaction.status == "COMPLETED") {
                        _transactionStatus.value = ActionResultState.Success("Points already added.")
                        return@launch
                    }

                    // 4. Update status and add points atomically
                    firestore.runTransaction { fireTrans ->
                        // Mark transaction as COMPLETED
                        fireTrans.update(transDoc.reference, "status", "COMPLETED")
                        
                        // Increment user balance
                        val balanceRef = firestore.collection("point_balances").document(transaction.userId)
                        fireTrans.set(
                            balanceRef, 
                            mapOf("totalPoints" to FieldValue.increment(transaction.pointsAllocated), "userId" to transaction.userId),
                            SetOptions.merge()
                        )
                    }.await()

                    _transactionStatus.value = ActionResultState.Success("Success! ${transaction.pointsAllocated} points added.")
                    fetchPointBalance()
                } else {
                    val statusMsg = statusResponse.payment_status_description ?: statusResponse.status
                    _transactionStatus.value = ActionResultState.Error("Payment status: $statusMsg")
                }
            } catch (e: Exception) {
                Log.e("PesaPal", "Verification Error", e)
                _transactionStatus.value = ActionResultState.Error("Verification failed: ${e.message}")
            }
        }
    }

    fun giftPoints(receiverId: String, receiverName: String, points: Double, contentId: String) {
        val senderId = auth.currentUser?.uid ?: return
        val senderName = auth.currentUser?.displayName ?: "User"
        _transactionStatus.value = ActionResultState.Loading

        viewModelScope.launch {
            try {
                firestore.runTransaction { transaction ->
                    val senderRef = firestore.collection("point_balances").document(senderId)
                    val receiverRef = firestore.collection("point_balances").document(receiverId)
                    val senderBalance = transaction.get(senderRef).toObject(PointBalance::class.java)
                    if (senderBalance == null || senderBalance.totalPoints < points) throw Exception("Insufficient points balance")
                    transaction.update(senderRef, "totalPoints", FieldValue.increment(-points))
                    transaction.set(receiverRef, mapOf("userId" to receiverId, "totalPoints" to FieldValue.increment(points)), SetOptions.merge())
                    val giftRef = firestore.collection("point_gifts").document()
                    transaction.set(giftRef, PointGift(giftRef.id, senderId, senderName, receiverId, receiverName, contentId, points))
                }.await()
                _transactionStatus.value = ActionResultState.Success("Gifted successfully")
            } catch (e: Exception) {
                _transactionStatus.value = ActionResultState.Error(e.message ?: "Failed to gift points")
            }
        }
    }

    fun requestRedemption(points: Double, paymentDetails: String) {
        val userId = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: "Teacher"
        _transactionStatus.value = ActionResultState.Loading

        viewModelScope.launch {
            try {
                firestore.runTransaction { transaction ->
                    val balanceRef = firestore.collection("point_balances").document(userId)
                    val balance = transaction.get(balanceRef).toObject(PointBalance::class.java)
                    if (balance == null || balance.totalPoints < points) throw Exception("Insufficient points for redemption")
                    transaction.update(balanceRef, "totalPoints", FieldValue.increment(-points))
                    val redemptionRef = firestore.collection("redemption_requests").document()
                    transaction.set(redemptionRef, RedemptionRequest(redemptionRef.id, userId, userName, points, points, "PENDING", paymentDetails))
                }.await()
                _transactionStatus.value = ActionResultState.Success("Redemption request submitted")
            } catch (e: Exception) {
                _transactionStatus.value = ActionResultState.Error(e.message ?: "Failed to request redemption")
            }
        }
    }

    /**
     * Admin/System process to disburse money to teacher via PesaPal
     */
    fun processDisbursement(redemptionId: String) {
        val settings = _platformSettings.value ?: PlatformSettings()
        val consumerKey = settings.pesapalConsumerKey.trim().ifEmpty { "VdiTLwH6c9jD8vVZqlvVoS29QfPv/saf" }
        val consumerSecret = settings.pesapalConsumerSecret.trim().ifEmpty { "MCy2o8/hKrV7UfPSnpdYAXUFxSg=" }

        _transactionStatus.value = ActionResultState.Loading

        viewModelScope.launch {
            try {
                val doc = firestore.collection("redemption_requests").document(redemptionId).get().await()
                val request = doc.toObject(RedemptionRequest::class.java) ?: throw Exception("Request not found")
                
                if (request.status != "PENDING") {
                    _transactionStatus.value = ActionResultState.Error("Request is already ${request.status}")
                    return@launch
                }

                val service = getPesapalService()
                val authResponse = service.getAuthToken(PesapalAuthRequest(consumerKey, consumerSecret))
                val token = "Bearer ${authResponse.token}"

                val disbursementRequest = PesapalDisbursementRequest(
                    source_account_id = settings.pesapalSourceAccountId,
                    destination_channel = "MPESA", 
                    destination_account = request.paymentDetails,
                    amount = request.amountInCash,
                    description = "Point Redemption for ${request.teacherName}",
                    reference = request.id,
                    callback_url = "https://pesapalipn-vs7fnli2hq-uc.a.run.app"
                )

                val response = service.submitDisbursement(token, disbursementRequest)

                if (response.status == "200" || response.disbursement_tracking_id != null) {
                    firestore.collection("redemption_requests").document(redemptionId)
                        .update("status", "COMPLETED").await()
                    _transactionStatus.value = ActionResultState.Success("Disbursement initiated successfully")
                } else {
                    _transactionStatus.value = ActionResultState.Error("Disbursement Failed: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e("PesaPal", "Disbursement Error", e)
                _transactionStatus.value = ActionResultState.Error("Error: ${e.message}")
            }
        }
    }

    fun resetStatus() { _transactionStatus.value = ActionResultState.Idle }
}
