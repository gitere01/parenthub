package com.joseph.cbchubpro.ui.management

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Institution(
    @Exclude val id: String = "",
    val name: String = "",
    val category: String = "",
    val location: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val ownerId: String = "",
    val imageUrl: String = "",
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val openingHours: String = "", 
    val description: String = "",
    val amenities: List<String> = emptyList(),
    val businessTags: List<String> = emptyList(),
    val isVerified: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
    val settings: InstitutionSettings = InstitutionSettings()
)

data class InstitutionSettings(
    val hasSchoolManagement: Boolean = false,
    val hasInventory: Boolean = false,
    val hasPOS: Boolean = false,
    val hasRestaurant: Boolean = false,
    val hasKDS: Boolean = false,
    val hasDelivery: Boolean = false,
    val hasRentalManagement: Boolean = false,
    val hasPropertyManagement: Boolean = false,
    val hasHousekeeping: Boolean = false,
    val hasMarketplaceListing: Boolean = false,
    val hasServiceBooking: Boolean = false,
    val hasVisitorManagement: Boolean = false,
    val hasQueueSystem: Boolean = false,
    val hasExpenseTracking: Boolean = false,
    val hasLoyaltyProgram: Boolean = false,
    val hasAnalytics: Boolean = false,
    val hasMpesaIntegration: Boolean = false,
    val hasOnlineSales: Boolean = false,
    val hasSocialMediaSales: Boolean = false,
    val showSaleOriginPrompt: Boolean = false,
    val promptForSaleSource: Boolean = false,
    val currency: String = "KES",
    val taxPercentage: Double = 0.0,
    val darajaShortCode: String = "",
    val darajaConsumerKey: String = "",
    val darajaConsumerSecret: String = "",
    val printerIpAddress: String = "", 
    val printerMacAddress: String = ""
)

data class Branch(
    @Exclude val id: String = "",
    val name: String = "",
    val location: String = "",
    val phone: String = ""
)

data class InstitutionStaff(
    @Exclude val id: String = "",
    val uid: String = "",
    val name: String = "",
    val role: String = "Cashier",
    val branchId: String = "",
    val email: String = "",
    val phone: String = "",
    val assignedTables: List<String> = emptyList(),
    val addedAt: Timestamp = Timestamp.now(),
    val fcmToken: String = ""
)

@IgnoreExtraProperties
data class InventoryItem(
    @Exclude val id: String = "",
    val name: String = "",
    val description: String = "",
    val brand: String = "",
    val sku: String = "",
    val price: Double = 0.0, 
    val costPrice: Double = 0.0,
    val wholesalePrice: Double = 0.0,
    val minWholesaleQuantity: Double = 0.0,
    val stockQuantity: Double = 0.0,
    val reorderLevel: Double = 5.0,
    val unit: String = "pcs",
    val categoryId: String = "",
    val categoryName: String = "",
    val imageUrl: String = "",
    val available: Boolean = true,
    val isMenuItem: Boolean = false,
    val isPublic: Boolean = true,
    val tags: List<String> = emptyList(),
    val ingredients: List<String> = emptyList(),
    val isService: Boolean = false,
    val pointsValue: Int = 0,
    val branchId: String = ""
)

// --- RENTAL & PROPERTY MODELS ---

data class RentalItem(
    @Exclude val id: String = "",
    val name: String = "",
    val category: String = "",
    val status: String = "AVAILABLE", // AVAILABLE, RENTED, MAINTENANCE
    val price: Double = 0.0,
    val branchId: String = ""
)

data class RentalBooking(
    @Exclude val id: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val status: String = "ACTIVE",
    val branchId: String = ""
)

data class PropertyListing(
    @Exclude val id: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val title: String = "",
    val description: String = "",
    val type: String = "RENT",
    val propertyCategory: String = "Apartment",
    val price: Double = 0.0,
    val deposit: Double = 0.0,
    val location: String = "",
    val address: String = "",
    val bedrooms: Int = 0,
    val bathrooms: Int = 0,
    val size: String = "",
    val amenities: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val isAvailable: Boolean = true,
    val isPublic: Boolean = true,
    val housekeepingStatus: String = "CLEAN",
    val isFeatured: Boolean = false,
    val createdAt: Timestamp = Timestamp.now(),
    val branchId: String = "",
    val rules: String = "",
    val floorNumber: String = ""
)

data class PropertyLease(
    @Exclude val id: String = "",
    val propertyId: String = "",
    val propertyTitle: String = "",
    val tenantId: String = "",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val startDate: Timestamp = Timestamp.now(),
    val endDate: Timestamp? = null,
    val monthlyRent: Double = 0.0,
    val depositPaid: Double = 0.0,
    val status: String = "ACTIVE",
    val leaseDocumentUrl: String = "",
    val nextRentDueDate: Timestamp? = null,
    val branchId: String = ""
)

// --- SERVICE & QUEUE MODELS ---

data class ServiceAppointment(
    @Exclude val id: String = "",
    val serviceId: String = "",
    val serviceName: String = "",
    val category: String = "Barber",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val providerId: String = "",
    val providerName: String = "",
    val scheduledTime: Timestamp = Timestamp.now(),
    val estimatedStartTime: Timestamp? = null,
    val peopleAhead: Int = 0,
    val durationMinutes: Int = 30,
    val status: String = "SCHEDULED",
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val notes: String = "",
    val branchId: String = "",
    val isWalkIn: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)

data class QueueTicket(
    @Exclude val id: String = "",
    val ticketNumber: Int = 0,
    val serviceCategory: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val status: String = "WAITING",
    val peopleInFront: Int = 0,
    val estimatedWaitTimeMinutes: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
    val servedAt: Timestamp? = null,
    val isOnlineBooking: Boolean = false,
    val scheduledTime: Timestamp? = null,
    val providerId: String = ""
)

data class QueueService(
    @Exclude val id: String = "",
    val name: String = "",
    val prefix: String = "",
    val currentTicket: Int = 0,
    val averageServiceTimeMinutes: Int = 20,
    val isActive: Boolean = true
)

// --- RESTAURANT MODELS ---

data class RestaurantTable(
    @Exclude val id: String = "",
    val tableNumber: String = "",
    val status: String = "AVAILABLE", 
    val capacity: Int = 4,
    val currentOrderId: String? = null,
    val branchId: String = "",
    val waiterId: String? = null
)

data class MenuCategory(
    @Exclude val id: String = "",
    val name: String = "",
    val branchId: String = ""
)

data class RestaurantOrder(
    @Exclude val id: String = "",
    val tableId: String = "",
    val tableNumber: String = "",
    val createdBy: String = "", 
    val waiterName: String = "",
    val orderType: String = "DINE_IN",
    val status: String = "NEW",
    val paymentStatus: String = "UNPAID", 
    val totalAmount: Double = 0.0,
    val subtotal: Double = 0.0,
    val taxAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val specialInstructions: String = "",
    val deliveryAddress: String = "",
    val deliveryFee: Double = 0.0,
    val deliveryDistance: Double = 0.0,
    val riderId: String = "",
    val riderName: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val items: List<RestaurantOrderItem> = emptyList(),
    val branchId: String = ""
)

data class RestaurantOrderItem(
    val itemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
    val notes: String = "", 
    val status: String = "NEW"
)

data class RestaurantPayment(
    @Exclude val id: String = "",
    val orderId: String = "",
    val method: String = "Cash", 
    val amount: Double = 0.0,
    val paidAt: Timestamp = Timestamp.now(),
    val cashierId: String = ""
)

// --- SCHOOL MODELS ---

data class Student(
    @Exclude val id: String = "",
    val name: String = "",
    val admissionNumber: String = "",
    val classroomId: String = "",
    val guardianId: String = "",
    val guardianPhone: String = "",
    val balance: Double = 0.0,
    val branchId: String = ""
)

data class Classroom(
    @Exclude val id: String = "",
    val name: String = "",
    val teacherId: String = "",
    val branchId: String = ""
)

data class FeePayment(
    @Exclude val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val amount: Double = 0.0,
    val method: String = "Mpesa",
    val transactionId: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val branchId: String = ""
)

// --- TRANSACTION & AUDIT MODELS ---

data class POSTransaction(
    @Exclude val id: String = "",
    val items: List<InstitutionCartItem> = emptyList(),
    val subtotal: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val profit: Double = 0.0,
    val paymentMethod: String = "Cash",
    val paymentStatus: String = "PAID", 
    val saleType: String = "PHYSICAL", 
    val isPhysicalSale: Boolean = true,
    val cashierId: String = "",
    val cashierName: String = "",
    val branchId: String = "",
    val customerId: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerAddress: String? = null,
    val socialMediaHandle: String? = null,
    val deliveryFee: Double = 0.0,
    val trackingNumber: String = "",
    val transactionType: String = "SALE", 
    val orderSource: String = "POS", 
    val orderStatus: String = "COMPLETED",
    val timestamp: Timestamp = Timestamp.now()
)

data class InstitutionCartItem(
    val productId: String = "",
    val productName: String = "",
    val price: Double = 0.0,
    val quantity: Double = 1.0,
    val imageUrl: String = "",
    val selectedVariation: String = ""
)

data class Expense(
    @Exclude val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val branchId: String = "",
    val recordedBy: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class StaffActivityLog(
    @Exclude val id: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val action: String = "",
    val details: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

@IgnoreExtraProperties
data class InstitutionVisitor(
    @Exclude val id: String = "",
    val name: String = "",
    val idNumber: String = "",
    val phoneNumber: String = "",
    val purpose: String = "",
    val hostName: String = "",
    val entryTime: Timestamp = Timestamp.now(),
    val exitTime: Timestamp? = null,
    val status: String = "IN"
)

data class CustomerLoyalty(
    val customerId: String = "",
    val name: String = "",
    val totalPoints: Int = 0,
    val lastUpdated: Timestamp = Timestamp.now()
)

data class SalesAnalysis(
    val date: String = "",
    val totalSales: Double = 0.0,
    val totalProfit: Double = 0.0,
    val transactionCount: Int = 0,
    val bestSellingItem: String = ""
)

data class ProductAnalysis(
    val productId: String = "",
    val productName: String = "",
    val unitsSold: Double = 0.0,
    val revenue: Double = 0.0,
    val profit: Double = 0.0
)

data class BusinessReview(
    @Exclude val id: String = "",
    val institutionId: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Int = 5,
    val comment: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

data class BusinessPromotion(
    @Exclude val id: String = "",
    val title: String = "",
    val description: String = "",
    val discountPercentage: Double = 0.0,
    val couponCode: String = "",
    val expiryDate: Timestamp? = null,
    val imageUrl: String = "",
    val isActive: Boolean = true
)
