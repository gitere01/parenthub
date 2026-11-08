package com.joseph.cbchubpro.network

import com.joseph.cbchubpro.ui.management.PesapalAuthResponse
import com.joseph.cbchubpro.ui.management.PesapalOrderRequest
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface PesapalService {
    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("api/Auth/RequestToken")
    suspend fun getAuthToken(
        @Body request: PesapalAuthRequest
    ): PesapalAuthResponse

    // FIXED: PesaPal V3 uses URLSetup/RegisterIPN
    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("api/URLSetup/RegisterIPN")
    suspend fun registerIPN(
        @Header("Authorization") token: String,
        @Body request: PesapalIpnRequest
    ): PesapalIpnResponse

    // FIXED: PesaPal V3 uses URLSetup/GetIpnList
    @Headers("Accept: application/json")
    @GET("api/URLSetup/GetIpnList")
    suspend fun getRegisteredIPNs(
        @Header("Authorization") token: String
    ): List<PesapalIpnResponse>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("api/Transactions/SubmitOrderRequest")
    suspend fun submitOrder(
        @Header("Authorization") token: String,
        @Body request: PesapalOrderRequest
    ): PesapalOrderResponse

    @Headers("Accept: application/json")
    @GET("api/Transactions/GetTransactionStatus")
    suspend fun getTransactionStatus(
        @Header("Authorization") token: String,
        @Query("orderTrackingId") orderTrackingId: String
    ): PesapalTransactionStatusResponse

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("api/Transactions/SubmitDisbursement")
    suspend fun submitDisbursement(
        @Header("Authorization") token: String,
        @Body request: PesapalDisbursementRequest
    ): PesapalDisbursementResponse
}

data class PesapalAuthRequest(
    @SerializedName("consumer_key") val consumer_key: String,
    @SerializedName("consumer_secret") val consumer_secret: String
)

data class PesapalIpnRequest(
    @SerializedName("url") val url: String,
    @SerializedName("ipn_notification_type") val ipn_notification_type: String = "GET"
)

data class PesapalIpnResponse(
    @SerializedName("ipn_id", alternate = ["IpnId", "IPN_id"]) val ipnId: String? = null,
    @SerializedName("url", alternate = ["Url"]) val url: String? = null,
    @SerializedName("status", alternate = ["Status"]) val status: String? = null,
    @SerializedName("ipn_id_guid") val ipnIdGuid: String? = null,
    @SerializedName("error", alternate = ["Error"]) val error: String? = null
)

data class PesapalOrderResponse(
    @SerializedName("order_tracking_id", alternate = ["OrderTrackingId"]) val order_tracking_id: String? = null,
    @SerializedName("merchant_reference", alternate = ["MerchantReference"]) val merchant_reference: String? = null,
    @SerializedName("redirect_url", alternate = ["RedirectUrl"]) val redirect_url: String? = null,
    @SerializedName("status", alternate = ["Status"]) val status: String? = null,
    @SerializedName("message", alternate = ["Message"]) val message: String? = null
)

data class PesapalTransactionStatusResponse(
    @SerializedName("payment_method") val payment_method: String? = null,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("created_date") val created_date: String? = null,
    @SerializedName("confirmation_code") val confirmation_code: String? = null,
    @SerializedName("payment_status_description") val payment_status_description: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("payment_account") val payment_account: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("status_code") val status_code: Int = 0,
    @SerializedName("merchant_reference") val merchant_reference: String? = null,
    @SerializedName("currency") val currency: String? = null
)

data class PesapalDisbursementRequest(
    @SerializedName("source_account_id") val source_account_id: String,
    @SerializedName("destination_channel") val destination_channel: String,
    @SerializedName("destination_account") val destination_account: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("description") val description: String,
    @SerializedName("reference") val reference: String,
    @SerializedName("callback_url") val callback_url: String
)

data class PesapalDisbursementResponse(
    @SerializedName("disbursement_tracking_id") val disbursement_tracking_id: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null
)
