package com.joseph.cbchubpro.ui.discovery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.joseph.cbchubpro.ui.management.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class VendorWithProducts(
    val vendorId: String,
    val vendorName: String,
    val location: String,
    val contact: String,
    val profileImageUrl: String,
    val products: List<ProductItem>,
    val isVerified: Boolean = false,
    val role: String = "vendor",
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val sellerResponseTime: String = "Fast",
    val yearsActive: Int = 1,
    val physicalLocationVerified: Boolean = false,
    val transactionHistoryCount: Int = 0,
    val phoneVerified: Boolean = false
)

data class ProfessionalProfile(
    val uid: String = "",
    val name: String = "",
    val role: String = "",
    val description: String = "",
    val location: String = "",
    val contact: String = "",
    val profileImageUrl: String = "",
    val bookingPrice: Double = 0.0,
    val groupBookingPrice: Double = 0.0,
    val basicSessionPrice: Double = 0.0,
    val fullPackagePrice: Double = 0.0,
    val isVerified: Boolean = false,
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0
)

class DiscoveryViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _marketplaceVendors = MutableStateFlow<List<VendorWithProducts>>(emptyList())
    private val _swapItems = MutableStateFlow<List<VendorWithProducts>>(emptyList())
    
    private val _institutionalProperties = MutableStateFlow<List<PropertyListing>>(emptyList())
    private val _independentProperties = MutableStateFlow<List<PropertyListing>>(emptyList())

    val properties: StateFlow<List<PropertyListing>> = combine(
        _institutionalProperties, _independentProperties
    ) { inst, indep ->
        (inst + indep).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _restaurantItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val restaurantItemsData = _restaurantItems.asStateFlow()

    val activeGroupDeals: StateFlow<List<GroupDeal>> = activeDealsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _locationQuery = MutableStateFlow("")
    val locationQuery = _locationQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NONE)
    val sortOrder = _sortOrder.asStateFlow()

    private val _showOnlyVerified = MutableStateFlow(false)
    val showOnlyVerified = _showOnlyVerified.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _reviews = MutableStateFlow<Map<String, List<Review>>>(emptyMap())
    val reviews = _reviews.asStateFlow()

    private val _pointsBalance = MutableStateFlow(0L)
    val pointsBalance = _pointsBalance.asStateFlow()

    private val _childPointsBalance = MutableStateFlow(0L)
    val childPointsBalance = _childPointsBalance.asStateFlow()

    private val _interactions = MutableStateFlow<List<InteractionRecord>>(emptyList())
    val interactions = _interactions.asStateFlow()

    private var interactionsListener: ListenerRegistration? = null
    private var propertiesListener: ListenerRegistration? = null
    private var institutionalPropertiesListener: ListenerRegistration? = null

    // Separate flows for different marketplace sections
    val retailItems: StateFlow<List<VendorWithProducts>> = combine(
        _marketplaceVendors, _searchQuery, _locationQuery, _sortOrder, _showOnlyVerified
    ) { vendors, search, location, sort, verified ->
        filterVendorsByType(vendors, ListingType.RETAIL, search, location, sort, verified)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupDealItems: StateFlow<List<VendorWithProducts>> = combine(
        _marketplaceVendors, _searchQuery, _locationQuery, _sortOrder, _showOnlyVerified
    ) { vendors, search, location, sort, verified ->
        filterVendorsByType(vendors, ListingType.GROUP_DEAL, search, location, sort, verified)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wholesaleItems: StateFlow<List<VendorWithProducts>> = combine(
        _marketplaceVendors, _searchQuery, _locationQuery, _sortOrder, _showOnlyVerified
    ) { vendors, search, location, sort, verified ->
        filterVendorsByType(vendors, ListingType.WHOLESALE, search, location, sort, verified)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serviceItems: StateFlow<List<VendorWithProducts>> = combine(
        _marketplaceVendors, _searchQuery, _locationQuery, _sortOrder, _showOnlyVerified
    ) { vendors, search, location, sort, verified ->
        filterVendorsByType(vendors, ListingType.SERVICE, search, location, sort, verified)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val professionalItems: StateFlow<List<VendorWithProducts>> = combine(
        _marketplaceVendors, _searchQuery, _locationQuery, _sortOrder, _showOnlyVerified
    ) { vendors, search, location, sort, verified ->
        filterVendorsByType(vendors, ListingType.PROFESSIONAL, search, location, sort, verified)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val swapHubItems: StateFlow<List<VendorWithProducts>> = combine(
        _swapItems, _searchQuery, _locationQuery, _sortOrder, _showOnlyVerified
    ) { parents, search, location, sort, verified ->
        filterVendors(parents, search, location, sort, verified)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVendors: StateFlow<List<VendorWithProducts>> = combine(
        _marketplaceVendors, _swapItems
    ) { marketplace, swap ->
        marketplace + swap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _rawProfessionals = MutableStateFlow<List<ProfessionalProfile>>(emptyList())
    val professionals: StateFlow<List<ProfessionalProfile>> = combine(
        _rawProfessionals, _searchQuery, _locationQuery
    ) { list, search, location ->
        list.filter { prof ->
            val matchesSearch = search.isEmpty() || 
                prof.name.contains(search, ignoreCase = true) || 
                prof.role.contains(search, ignoreCase = true)
            val matchesLocation = location.isEmpty() || 
                prof.location.contains(location, ignoreCase = true)
            matchesSearch && matchesLocation
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        fetchMarketplace()
        fetchSwapHub()
        fetchProfessionals()
        fetchUserPoints()
        fetchInteractions()
        startPropertiesListeners()
        fetchRestaurantMenus()
    }

    private fun activeDealsFlow(): Flow<List<GroupDeal>> = callbackFlow {
        val listener = db.collection("group_deals")
            .whereIn("status", listOf(DealStatus.ACTIVE.name, DealStatus.LOCKED.name))
            .addSnapshotListener { snapshot, _ ->
                val deals = snapshot?.documents?.mapNotNull { it.toObject(GroupDeal::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(deals)
            }
        awaitClose { listener.remove() }
    }

    private fun filterVendorsByType(
        vendors: List<VendorWithProducts>, type: ListingType, search: String, location: String, sort: SortOrder, verified: Boolean
    ): List<VendorWithProducts> {
        return vendors.asSequence()
            .filter { vendor ->
                val matchesLocation = location.isEmpty() || vendor.location.contains(location, ignoreCase = true)
                val matchesVerified = !verified || vendor.isVerified
                matchesLocation && matchesVerified
            }
            .map { vendor ->
                val filteredProducts = vendor.products.filter { product ->
                    product.listingType == type && (search.isEmpty() || product.name.contains(search, ignoreCase = true))
                }.let { products ->
                    when (sort) {
                        SortOrder.PRICE_LOW_HIGH -> products.sortedBy { it.price }
                        SortOrder.PRICE_HIGH_LOW -> products.sortedByDescending { it.price }
                        SortOrder.NONE -> products
                    }
                }
                vendor.copy(products = filteredProducts)
            }
            .filter { it.products.isNotEmpty() }
            .toList()
    }

    private fun filterVendors(
        vendors: List<VendorWithProducts>, search: String, location: String, sort: SortOrder, verified: Boolean
    ): List<VendorWithProducts> {
        return vendors.asSequence()
            .filter { vendor ->
                val matchesLocation = location.isEmpty() || vendor.location.contains(location, ignoreCase = true)
                val matchesVerified = !verified || vendor.isVerified
                matchesLocation && matchesVerified
            }
            .map { vendor ->
                val filteredProducts = vendor.products.filter { product ->
                    search.isEmpty() || product.name.contains(search, ignoreCase = true)
                }.let { products ->
                    when (sort) {
                        SortOrder.PRICE_LOW_HIGH -> products.sortedBy { it.price }
                        SortOrder.PRICE_HIGH_LOW -> products.sortedByDescending { it.price }
                        SortOrder.NONE -> products
                    }
                }
                vendor.copy(products = filteredProducts)
            }
            .filter { it.products.isNotEmpty() }
            .toList()
    }

    fun fetchMarketplace() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _marketplaceVendors.value = fetchByRole("vendor")
            } catch (e: Exception) {
                Log.e("Discovery", "Error fetching marketplace", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchSwapHub() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _swapItems.value = fetchByRole("parent/guardian")
            } catch (e: Exception) {
                Log.e("Discovery", "Error fetching swap hub", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchByRole(role: String): List<VendorWithProducts> {
        val snapshots = db.collection("users")
            .whereEqualTo("role", role)
            .get().await()

        return snapshots.documents.map { doc ->
            val products = db.collection("users").document(doc.id)
                .collection("products").get().await()
                .documents.mapNotNull { pDoc ->
                    try {
                        pDoc.toObject(ProductItem::class.java)?.copy(id = pDoc.id)
                    } catch (e: Exception) { null }
                }

            VendorWithProducts(
                vendorId = doc.id,
                vendorName = doc.getString("name") ?: "Unknown User",
                location = doc.getString("location") ?: "",
                contact = doc.getString("contact") ?: "",
                profileImageUrl = doc.getString("profileImageUrl") ?: "",
                products = products,
                isVerified = doc.getBoolean("isVerified") ?: false,
                role = role,
                rating = doc.getDouble("rating") ?: 0.0,
                reviewCount = doc.getLong("reviewCount")?.toInt() ?: 0,
                sellerResponseTime = doc.getString("sellerResponseTime") ?: "Fast",
                yearsActive = doc.getLong("yearsActive")?.toInt() ?: 1,
                physicalLocationVerified = doc.getBoolean("physicalLocationVerified") ?: false,
                transactionHistoryCount = doc.getLong("transactionHistoryCount")?.toInt() ?: 0,
                phoneVerified = doc.getBoolean("phoneVerified") ?: false
            )
        }.filter { it.products.isNotEmpty() }
    }

    fun fetchProfessionals() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Include "private_teacher" in the filtered roles
                val profSnapshots = db.collection("users")
                    .whereIn("role", listOf("professional", "service provider", "teacher", "private_teacher"))
                    .get().await()

                val profList = profSnapshots.documents.mapNotNull { doc ->
                    doc.toObject(ProfessionalProfile::class.java)?.copy(
                        uid = doc.id,
                        isVerified = doc.getBoolean("isVerified") ?: false
                    )
                }
                _rawProfessionals.value = profList
            } catch (e: Exception) {
                Log.e("Discovery", "Error fetching professionals", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startPropertiesListeners() {
        // Listener for institutional properties
        institutionalPropertiesListener = db.collectionGroup("property_listings")
            .whereEqualTo("isPublic", true)
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Discovery", "Error listening to institutional properties", error)
                    return@addSnapshotListener
                }
                _institutionalProperties.value = snapshot?.documents?.mapNotNull { 
                    it.toObject(PropertyListing::class.java)?.copy(id = it.id)
                } ?: emptyList()
            }
        
        // Listener for independent agent properties
        propertiesListener = db.collection("properties")
            .whereEqualTo("isPublic", true)
            .whereEqualTo("isAvailable", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Discovery", "Error listening to independent properties", error)
                    return@addSnapshotListener
                }
                _independentProperties.value = snapshot?.documents?.mapNotNull { 
                    it.toObject(PropertyListing::class.java)?.copy(id = it.id)
                } ?: emptyList()
            }
    }

    fun fetchRestaurantMenus() {
        viewModelScope.launch {
            try {
                // Fetch public menu items from all institutions
                val snapshots = db.collectionGroup("inventory")
                    .whereEqualTo("isMenuItem", true)
                    .whereEqualTo("isPublic", true)
                    .whereEqualTo("available", true)
                    .get().await()
                
                _restaurantItems.value = snapshots.documents.mapNotNull { 
                    it.toObject(InventoryItem::class.java)?.copy(id = it.id)
                }
            } catch (e: Exception) {
                Log.e("Discovery", "Error fetching restaurant menus", e)
            }
        }
    }

    fun fetchReviews(targetId: String) {
        db.collection("users").document(targetId).collection("reviews")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val reviewList = snapshot?.documents?.mapNotNull { it.toObject(Review::class.java)?.copy(id = it.id) } ?: emptyList()
                _reviews.update { current -> current + (targetId to reviewList) }
            }
    }

    fun addReview(targetId: String, rating: Int, comment: String) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Anonymous"
        viewModelScope.launch {
            try {
                val review = Review(reviewerId = uid, reviewerName = name, rating = rating, comment = comment)
                db.collection("users").document(targetId).collection("reviews").add(review).await()
                _statusMessage.value = "Review submitted! Thank you."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    private fun fetchUserPoints() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            _pointsBalance.value = snapshot?.getLong("pointsBalance") ?: 0L
        }
    }

    fun fetchChildPoints(childId: String) {
        db.collection("users").document(childId).addSnapshotListener { snapshot, _ ->
            _childPointsBalance.value = snapshot?.getLong("pointsBalance") ?: 0L
        }
    }

    fun fetchInteractions(userId: String? = null) {
        val uid = userId ?: auth.currentUser?.uid ?: return
        
        // Remove existing listener to avoid race conditions and duplicates
        interactionsListener?.remove()
        
        interactionsListener = db.collection("users").document(uid).collection("interactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Discovery", "Error fetching interactions", error)
                    return@addSnapshotListener
                }
                _interactions.value = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(InteractionRecord::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("Discovery", "Error mapping interaction", e)
                        null
                    }
                } ?: emptyList()
            }
    }

    fun logInteraction(prof: ProfessionalProfile, contentId: String, contentTitle: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val record = InteractionRecord(teacherId = prof.uid, teacherName = prof.name, contentId = contentId, contentTitle = contentTitle)
            db.collection("users").document(uid).collection("interactions").add(record).await()
        }
    }

    fun buyPoints(amount: Double, phone: String, targetUserId: String? = null) {
        val uid = targetUserId ?: auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val checkoutRef = db.collection("payment_requests").document()
                val payload = mapOf(
                    "id" to checkoutRef.id,
                    "userId" to uid,
                    "amount" to amount,
                    "phone" to phone,
                    "type" to "GIFT_POINTS",
                    "status" to "PENDING",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                checkoutRef.set(payload).await()
                _statusMessage.value = "STK Push sent to $phone"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun sendGift(prof: ProfessionalProfile, giftPoints: Long) {
        val uid = auth.currentUser?.uid ?: return
        if (_pointsBalance.value < giftPoints) {
            _statusMessage.value = "Insufficient points! Please buy more."
            return
        }

        viewModelScope.launch {
            try {
                val batch = db.batch()
                // Deduct from parent
                batch.update(db.collection("users").document(uid), "pointsBalance", FieldValue.increment(-giftPoints))
                // Add to teacher
                val teacherCredits = (giftPoints * 0.85).toLong()
                batch.update(db.collection("users").document(prof.uid), "agentPoints", FieldValue.increment(teacherCredits))
                
                batch.commit().await()
                _statusMessage.value = "Gift of $giftPoints points sent to ${prof.name}!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun joinGroupDeal(dealId: String) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Anonymous Buyer"
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                val address = userDoc.getString("location") ?: "No address provided"
                val phone = userDoc.getString("contact") ?: ""

                val dealRef = db.collection("group_deals").document(dealId)
                db.runTransaction { transaction ->
                    val snapshot = transaction.get(dealRef)
                    val deal = snapshot.toObject(GroupDeal::class.java) ?: return@runTransaction
                    
                    if (deal.status != DealStatus.ACTIVE) {
                        throw Exception("This deal is no longer accepting joins.")
                    }

                    if (deal.buyerDetails.containsKey(uid)) {
                        throw Exception("You have already joined this deal!")
                    }

                    val updatedBuyers = deal.buyerDetails.toMutableMap()
                    updatedBuyers[uid] = GroupBuyerInfo(
                        name = name, 
                        isPaid = false,
                        shippingAddress = address,
                        phoneNumber = phone
                    )
                    
                    val newCount = deal.currentJoined + 1
                    transaction.update(dealRef, "buyerDetails", updatedBuyers)
                    transaction.update(dealRef, "currentJoined", newCount)

                    if (newCount >= deal.minRequired) {
                        transaction.update(dealRef, "status", DealStatus.LOCKED.name)
                    }
                }.await()
                _statusMessage.value = "Joined deal! Check your cart to complete the order."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun hireAgent(productId: String, vendorId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val request = InspectionRequest(
                    buyerId = uid,
                    productId = productId,
                    vendorId = vendorId,
                    fee = 50.0
                )
                db.collection("inspection_requests").add(request).await()
                db.collection("users").document(vendorId).update("agentPoints", FieldValue.increment(10))
                _statusMessage.value = "Agent inspection requested! You will be notified when it's done."
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun requestSwap(targetVendorId: String, targetVendorName: String, targetProductId: String, offeredProductId: String? = null, cashTopUp: Double = 0.0) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Anonymous"
        
        viewModelScope.launch {
            val swapRequest = SwapRequest(
                senderId = uid,
                senderName = name,
                receiverId = targetVendorId,
                receiverName = targetVendorName,
                requestedItemId = targetProductId,
                offeredItemId = offeredProductId,
                cashOffered = cashTopUp,
                status = SwapRequestStatus.PENDING
            )
            db.collection("swap_requests").add(swapRequest).await()
            _statusMessage.value = "Swap request sent successfully!"
        }
    }

    fun initiateBookingPayment(prof: ProfessionalProfile, phone: String, onSuccess: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "User"
        
        viewModelScope.launch {
            try {
                val checkoutRef = db.collection("payment_requests").document()
                val paymentPayload = mapOf(
                    "id" to checkoutRef.id,
                    "userId" to uid,
                    "amount" to prof.bookingPrice,
                    "phone" to phone,
                    "type" to "PROFESSIONAL_BOOKING",
                    "status" to "PENDING",
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "professionalId" to prof.uid,
                    "professionalName" to prof.name
                )
                checkoutRef.set(paymentPayload).await()

                val order = Order(
                    id = checkoutRef.id,
                    buyerId = uid,
                    buyerName = name,
                    vendorId = prof.uid,
                    vendorName = prof.name,
                    items = listOf(CartItem(
                        productId = prof.uid,
                        productName = "Booking: ${prof.role}",
                        price = prof.bookingPrice,
                        quantity = 1.0,
                        purchaseType = PurchaseType.PROFESSIONAL
                    )),
                    subtotal = prof.bookingPrice,
                    totalAmount = prof.bookingPrice,
                    phoneNumber = phone,
                    status = OrderStatus.PENDING
                )
                db.collection("orders").document(checkoutRef.id).set(order).await()

                _statusMessage.value = "STK Push sent to $phone"
                onSuccess(checkoutRef.id)
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setLocationQuery(query: String) { _locationQuery.value = query }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun toggleVerifiedOnly() { _showOnlyVerified.value = !_showOnlyVerified.value }
    fun clearStatus() { _statusMessage.value = null }
    
    override fun onCleared() {
        super.onCleared()
        interactionsListener?.remove()
        propertiesListener?.remove()
        institutionalPropertiesListener?.remove()
    }
}
