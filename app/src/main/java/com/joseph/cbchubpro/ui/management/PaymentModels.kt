package com.joseph.cbchubpro.ui.management

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@IgnoreExtraProperties
data class ProductItem(
    @Exclude val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val wholesalePrice: Double = 0.0,
    val minWholesaleQuantity: Double = 0.0,
    val groupPrice: Double = 0.0,
    val minGroupQuantity: Int = 0,
    val category: String = "",
    val imageUrls: List<String> = emptyList(),
    val description: String = "",
    val condition: String = "New",
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val location: String = "",
    val status: String = "Available",
    val vendorId: String = "",
    val listingType: ListingType = ListingType.RETAIL,
    val isForSwap: Boolean = false,
    val swapPreference: String = "",
    
    // Differentiation & POS/Inventory Metrics
    val promoLabel: String = "", // e.g., "Best Seller", "Limited Edition"
    val stockQuantity: Int = -1, // -1 means unlimited
    val colors: List<String> = emptyList(), 
    val sku: String = "", 
    val brand: String = "",
    val tags: List<String> = emptyList(),
    
    // Badges & Trust Metrics
    val isNew: Boolean = true,
    val agentInspected: Boolean = false,
    val physicalLocationVerified: Boolean = false,
    val transactionCount: Int = 0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val sellerResponseTime: String = "Fast",
    val yearsActive: Int = 1,
    val phoneVerified: Boolean = false,
    val isGroupDeal: Boolean = false,
    val adLevel: AdLevel = AdLevel.FREE,
    val adStatus: AdStatus = AdStatus.INACTIVE,
    val adExpiresAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppUser(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val isVerified: Boolean = false,
    val profileImageUrl: String? = null,
    val subscriptionPaid: Boolean = false,
    val subscriptionTier: String = "Basic",
    val studentCount: Int = 0,
    val childrenCount: Int = 0,
    val verificationPending: Boolean = false,
    val idNumber: String? = null,
    val schoolName: String? = null,
    val phoneVerified: Boolean = false,
    val yearsActive: Int = 0,
    val sellerResponseTime: String = "Fast",
    val transactionHistoryCount: Int = 0,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val physicalLocationVerified: Boolean = false,
    val agentPoints: Int = 0,
    val location: String = "",
    val contact: String = ""
)

enum class ListingType {
    RETAIL, WHOLESALE, GROUP_DEAL, SERVICE, PROFESSIONAL
}

enum class PurchaseType {
    RETAIL, WHOLESALE, GROUP, SERVICE, PROFESSIONAL
}

enum class VerificationStatus {
    UNVERIFIED, PENDING, VERIFIED, REJECTED
}

data class Order(
    val id: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val vendorId: String = "",
    val vendorName: String = "",
    val items: List<CartItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val platformRevenue: Double = 0.0, 
    val totalAmount: Double = 0.0, 
    val shippingAddress: String = "",
    val phoneNumber: String = "", 
    val status: OrderStatus = OrderStatus.PENDING,
    val deliveryMethod: DeliveryMethod = DeliveryMethod.DOOR_DELIVERY,
    val pickupPoint: String = "",
    val courierName: String = "",
    val trackingNumber: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val paidAt: Timestamp? = null,
    val deliveredAt: Timestamp? = null,
    val confirmedAt: Timestamp? = null,
    val disputeReason: String = "",
    val dealId: String? = null,
    val isReviewed: Boolean = false
)

data class VendorPayoutRequest(
    @Exclude val id: String = "",
    val vendorId: String = "",
    val vendorName: String = "",
    val amount: Double = 0.0,
    val phone: String = "",
    val status: String = "PENDING",
    val createdAt: Timestamp = Timestamp.now()
)

enum class DeliveryMethod {
    DOOR_DELIVERY,
    PICKUP_MTAANI_COD
}

data class CartItem(
    val productId: String = "",
    val productName: String = "",
    val price: Double = 0.0,
    val quantity: Double = 1.0, // Double for fractional quantities
    val imageUrl: String = "",
    val vendorId: String = "",
    val purchaseType: PurchaseType = PurchaseType.RETAIL,
    val dealId: String = "",
    val selectedVariation: String = "" // Added for differentiation in POS/Marketplace
)

enum class OrderStatus {
    PENDING,      
    PAID,
    AWAITING_COD,
    SHIPPED,      
    DELIVERED,    
    COMPLETED,    
    CANCELLED,    
    REFUNDED,
    DISPUTED
}

data class PlatformSettings(
    val darajaConsumerKey: String = "",
    val darajaConsumerSecret: String = "",
    val darajaPassKey: String = "",
    val darajaShortCode: String = "", 
    val darajaTillNumber: String = "", 
    val pesapalConsumerKey: String = "",
    val pesapalConsumerSecret: String = "",
    val pesapalIpnId: String = "",
    val pesapalSourceAccountId: String = "",
    val pickupMtaaniApiKey: String = "",
    val pickupMtaaniApiSecret: String = "",
    val pickupMtaaniAgentFee: Double = 100.0,
    val liveMode: Boolean = false,
    val basicSchoolFee: Double = 20.0,
    val premiumSchoolFee: Double = 45.0,
    val vendorListingFee: Double = 500.0,
    val itemVerificationFee: Double = 200.0,
    val vendorVerificationFee: Double = 1000.0,
    val jobListingFee: Double = 1000.0,
    val parentBaseFee: Double = 0.0,
    val parentExtraChildFee: Double = 100.0,
    val agentInspectionFee: Double = 50.0,
    val platformEscrowCutPercent: Double = 0.05,
    val pointsCommissionPercent: Double = 0.1
)

object FinanceConstants {
    const val DEFAULT_DELIVERY_FEE = 200.0
    const val PLATFORM_HUB_REVENUE = 50.0
    const val AGENT_POINTS_PER_INSPECTION = 10
}

data class Review(
    val id: String = "",
    val targetId: String = "", 
    val reviewerId: String = "",
    val reviewerName: String = "",
    val rating: Int = 5,
    val comment: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class InspectionRequest(
    val id: String = "",
    val buyerId: String = "",
    val productId: String = "",
    val productName: String = "",
    val vendorId: String = "",
    val agentId: String? = null,
    val status: String = "PENDING", 
    val fee: Double = 50.0,
    val createdAt: Timestamp = Timestamp.now(),
    val pointsEarned: Int = 0
)

data class SwapRequest(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val requestedItemId: String = "",
    val offeredItemId: String? = null,
    val requestedItemName: String = "",
    val requestedItemImageUrl: String = "",
    val requestedItemValue: Double = 0.0,
    val offeredItemName: String = "",
    val offeredItemImageUrl: String = "",
    val offeredItemValue: Double = 0.0,
    val senderConfirmed: Boolean = false,
    val receiverConfirmed: Boolean = false,
    val cashOffered: Double = 0.0,
    val status: SwapRequestStatus = SwapRequestStatus.PENDING,
    val createdAt: Timestamp = Timestamp.now()
)

enum class SwapRequestStatus {
    PENDING, ACCEPTED, REJECTED, COMPLETED, CANCELLED
}

enum class AdLevel {
    FREE, BASIC, PREMIUM, FEATURED
}

enum class AdStatus {
    INACTIVE, REQUESTED, WAITING_FOR_TRACTION, ACTIVE
}

data class GroupDeal(
    val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val vendorId: String = "",
    val vendorName: String = "",
    val minRequired: Int = 0,
    val currentJoined: Int = 0,
    val pricePerPerson: Double = 0.0,
    val originalPrice: Double = 0.0,
    val deadline: Timestamp = Timestamp.now(),
    val status: DealStatus = DealStatus.ACTIVE,
    val totalCollected: Double = 0.0,
    val buyerDetails: Map<String, GroupBuyerInfo> = emptyMap()
)

data class GroupBuyerInfo(
    val name: String = "",
    val isPaid: Boolean = false,
    val shippingAddress: String = "",
    val phoneNumber: String = "",
    val deliveryMethod: DeliveryMethod = DeliveryMethod.DOOR_DELIVERY,
    val pickupPoint: String = ""
)

enum class DealStatus {
    ACTIVE, LOCKED, SHIPPED, DELIVERED, COMPLETED, CANCELLED
}

enum class BoostLevel {
    NONE,
    HIGHLIGHTED,
    FEATURED,
    TOP_PRIORITY
}

data class JobPost(
    val id: String = "",
    val title: String = "",
    val company: String = "",
    val location: String = "",
    val description: String = "",
    val requirements: String = "",
    val salaryRange: String = "",
    val website: String = "",
    val phoneNumber: String = "",
    val deadline: String = "",
    val imageUrl: String = "",
    val posterId: String = "",
    val posterName: String = "",
    val isVerified: Boolean = false,
    val verificationPending: Boolean = false,
    val boostLevel: BoostLevel = BoostLevel.NONE,
    val boostExpiry: Timestamp? = null,
    val createdAt: Timestamp? = null
)

enum class SortOrder {
    NONE, PRICE_LOW_HIGH, PRICE_HIGH_LOW
}

fun getStatusColor(status: OrderStatus): Color = when (status) {
    OrderStatus.PENDING -> Color.Gray
    OrderStatus.PAID -> Color(0xFF2196F3)
    OrderStatus.AWAITING_COD -> Color(0xFFE91E63)
    OrderStatus.SHIPPED -> Color(0xFFFFA000)
    OrderStatus.DELIVERED -> Color(0xFF4CAF50)
    OrderStatus.COMPLETED -> Color(0xFF4CAF50)
    OrderStatus.CANCELLED -> Color.DarkGray
    OrderStatus.REFUNDED -> Color.Magenta
    OrderStatus.DISPUTED -> Color.Red
}

@Composable
fun StatusBadge(status: OrderStatus) {
    val color = getStatusColor(status)
    Surface(
        color = color.copy(alpha = 0.1f), 
        shape = RoundedCornerShape(4.dp), 
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = status.name.replace("_", " "), 
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), 
            style = MaterialTheme.typography.labelSmall, 
            color = color, 
            fontWeight = FontWeight.Bold
        )
    }
}

data class PickupPoint(
    val name: String = "",
    val baseFee: Double = 0.0,
    val zone: String = ""
)

data class InteractionRecord(
    val id: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val contentId: String = "",
    val contentTitle: String = "",
    val contentType: String = "",
    val pointsEarned: Long = 0,
    val timestamp: Timestamp = Timestamp.now()
)
