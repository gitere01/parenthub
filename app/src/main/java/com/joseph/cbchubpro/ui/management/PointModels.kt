package com.joseph.cbchubpro.ui.management

import com.google.firebase.Timestamp
import com.google.gson.annotations.SerializedName

sealed class ActionResultState {
    object Idle : ActionResultState()
    object Loading : ActionResultState()
    data class Success(val message: String? = null) : ActionResultState()
    data class Error(val message: String) : ActionResultState()
}

data class PointBalance(
    val userId: String = "",
    val totalPoints: Double = 0.0,
    val pendingRevenue: Double = 0.0,
    val lastUpdated: Timestamp = Timestamp.now()
)

data class PointTransaction(
    val id: String = "",
    val userId: String = "",
    val amountPaid: Double = 0.0,
    val pointsAllocated: Double = 0.0,
    val platformRevenue: Double = 0.0,
    val status: String = "PENDING",
    val pesapalOrderTrackingId: String = "",
    val type: PointTransactionType = PointTransactionType.PURCHASE,
    val timestamp: Timestamp = Timestamp.now()
)

enum class PointTransactionType {
    PURCHASE, GIFT_SENT, GIFT_RECEIVED, REDEMPTION
}

data class PointGift(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val contentId: String = "",
    val points: Double = 0.0,
    val timestamp: Timestamp = Timestamp.now()
)

data class RedemptionRequest(
    val id: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val pointsToRedeem: Double = 0.0,
    val amountInCash: Double = 0.0,
    val status: String = "PENDING",
    val paymentDetails: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

data class PesapalAuthResponse(
    @SerializedName("token") val token: String = "",
    @SerializedName("expiryDate") val expiryDate: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("message") val message: String? = null
)

data class PesapalOrderRequest(
    @SerializedName("id") val id: String = "",
    @SerializedName("currency") val currency: String = "KES",
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("description") val description: String = "Buy Gift Points",
    @SerializedName("callback_url") val callback_url: String = "",
    @SerializedName("notification_id") val notification_id: String = "",
    @SerializedName("billing_address") val billing_address: PesapalBillingAddress = PesapalBillingAddress()
)

data class PesapalBillingAddress(
    @SerializedName("email_address") val email_address: String = "",
    @SerializedName("phone_number") val phone_number: String = "",
    @SerializedName("country_code") val country_code: String = "KE",
    @SerializedName("first_name") val first_name: String = "",
    @SerializedName("middle_name") val middle_name: String = "",
    @SerializedName("last_name") val last_name: String = "",
    @SerializedName("line_1") val line_1: String = "",
    @SerializedName("line_2") val line_2: String = "",
    @SerializedName("city") val city: String = "",
    @SerializedName("state") val state: String = "",
    @SerializedName("postal_code") val postal_code: String = "",
    @SerializedName("zip_code") val zip_code: String = ""
)
