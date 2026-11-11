package com.joseph.cbchubpro.ui.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
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
    private val functions = FirebaseFunctions.getInstance("us-central1")

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
        if (auth.currentUser == null) return
        _transactionStatus.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                if (!amount.isFinite() || amount < 10.0) throw IllegalArgumentException("Buy at least KES 10 of points.")
                val result = functions.getHttpsCallable("createPointsCheckout").call(mapOf(
                    "amount" to amount,
                    "email" to email,
                    "phone" to phoneNumber,
                    "firstName" to firstName,
                    "lastName" to lastName
                )).await()
                val payload = result.data as? Map<*, *> ?: throw IllegalStateException("Payment service returned an invalid response.")
                val redirectUrl = payload["redirectUrl"] as? String ?: throw IllegalStateException("No PesaPal checkout URL was returned.")
                _transactionStatus.value = ActionResultState.Success(redirectUrl)
            } catch (e: Exception) {
                _transactionStatus.value = ActionResultState.Error(e.message ?: "Could not start point checkout.")
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
                    val sentRef = firestore.collection("point_transactions").document()
                    val receivedRef = firestore.collection("point_transactions").document()
                    transaction.set(sentRef, PointTransaction(id = sentRef.id, userId = senderId, pointsAllocated = points,
                        status = "COMPLETED", type = PointTransactionType.GIFT_SENT))
                    transaction.set(receivedRef, PointTransaction(id = receivedRef.id, userId = receiverId, pointsAllocated = points,
                        status = "COMPLETED", type = PointTransactionType.GIFT_RECEIVED))
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
        if (!points.isFinite() || points <= 0.0 || paymentDetails.isBlank()) {
            _transactionStatus.value = ActionResultState.Error("Enter a valid points amount and M-Pesa number.")
            return
        }
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
