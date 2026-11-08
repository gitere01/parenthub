package com.joseph.cbchubpro.ui.management

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class InstitutionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://cbchubpro.firebasestorage.app")

    private val _myInstitutions = MutableStateFlow<List<Institution>>(emptyList())
    val myInstitutions = _myInstitutions.asStateFlow()

    private val _selectedInstitution = MutableStateFlow<Institution?>(null)
    val selectedInstitution = _selectedInstitution.asStateFlow()

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches = _branches.asStateFlow()
    
    private val _selectedBranchId = MutableStateFlow<String>("")
    val selectedBranchId = _selectedBranchId.asStateFlow()

    private val _staff = MutableStateFlow<List<InstitutionStaff>>(emptyList())
    val staff = _staff.asStateFlow()

    private val _currentStaff = MutableStateFlow<InstitutionStaff?>(null)
    val currentStaff = _currentStaff.asStateFlow()

    // POS State
    private val _inventory = MutableStateFlow<List<InventoryItem>>(emptyList())
    val inventory = _inventory.asStateFlow()

    private val _cart = MutableStateFlow<List<InstitutionCartItem>>(emptyList())
    val cart = _cart.asStateFlow()

    private val _transactions = MutableStateFlow<List<POSTransaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses = _expenses.asStateFlow()
    
    private val _loyalty = MutableStateFlow<List<CustomerLoyalty>>(emptyList())
    val loyalty = _loyalty.asStateFlow()

    // Restaurant State
    private val _restaurantTables = MutableStateFlow<List<RestaurantTable>>(emptyList())
    val restaurantTables = _restaurantTables.asStateFlow()

    private val _activeRestaurantOrders = MutableStateFlow<List<RestaurantOrder>>(emptyList())
    val activeRestaurantOrders = _activeRestaurantOrders.asStateFlow()

    private val _menuCategories = MutableStateFlow<List<MenuCategory>>(emptyList())
    val menuCategories = _menuCategories.asStateFlow()

    private val _restaurantMenu = MutableStateFlow<List<InventoryItem>>(emptyList())
    val restaurantMenu = _restaurantMenu.asStateFlow()

    private val _kdsOrders = MutableStateFlow<List<RestaurantOrder>>(emptyList())
    val kdsOrders = _kdsOrders.asStateFlow()

    // Rental & Property State
    private val _rentalItems = MutableStateFlow<List<RentalItem>>(emptyList())
    val rentalItems = _rentalItems.asStateFlow()

    private val _activeRentals = MutableStateFlow<List<RentalBooking>>(emptyList())
    val activeRentals = _activeRentals.asStateFlow()

    private val _propertyListings = MutableStateFlow<List<PropertyListing>>(emptyList())
    val propertyListings = _propertyListings.asStateFlow()

    // Service Booking State
    private val _serviceAppointments = MutableStateFlow<List<ServiceAppointment>>(emptyList())
    val serviceAppointments = _serviceAppointments.asStateFlow()

    private val _staffLogs = MutableStateFlow<List<StaffActivityLog>>(emptyList())
    val staffLogs = _staffLogs.asStateFlow()

    // Analysis State
    private val _salesAnalysis = MutableStateFlow<List<SalesAnalysis>>(emptyList())
    val salesAnalysis = _salesAnalysis.asStateFlow()

    private val _productAnalysis = MutableStateFlow<List<ProductAnalysis>>(emptyList())
    val productAnalysis = _productAnalysis.asStateFlow()

    // Visitor State
    private val _visitors = MutableStateFlow<List<InstitutionVisitor>>(emptyList())
    val visitors = _visitors.asStateFlow()

    // Queue State
    private val _queueServices = MutableStateFlow<List<QueueService>>(emptyList())
    val queueServices = _queueServices.asStateFlow()

    private val _activeTickets = MutableStateFlow<List<QueueTicket>>(emptyList())
    val activeTickets = _activeTickets.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        fetchMyInstitutions()
        checkCurrentUserStaffRole()
    }

    private fun fetchMyInstitutions() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("institutions").whereEqualTo("ownerId", uid)
            .addSnapshotListener { snapshot, _ ->
                _myInstitutions.value = snapshot?.documents?.mapNotNull { 
                    it.toObject(Institution::class.java)?.copy(id = it.id) 
                } ?: emptyList()
                
                if (_selectedInstitution.value == null && _myInstitutions.value.isNotEmpty()) {
                    selectInstitution(_myInstitutions.value.first())
                }
            }
    }

    private fun checkCurrentUserStaffRole() {
        val uid = auth.currentUser?.uid ?: return
        db.collectionGroup("staff").whereEqualTo("uid", uid).addSnapshotListener { snapshot, _ ->
            val staffMember = snapshot?.documents?.firstOrNull()?.toObject(InstitutionStaff::class.java)
            _currentStaff.value = staffMember
            if (staffMember != null && _selectedInstitution.value == null) {
                val instId = snapshot.documents.firstOrNull()?.reference?.parent?.parent?.id
                if (instId != null) {
                    db.collection("institutions").document(instId).get().addOnSuccessListener { doc ->
                        doc.toObject(Institution::class.java)?.let { selectInstitution(it.copy(id = doc.id)) }
                    }
                }
            }
        }
    }

    fun selectInstitution(institution: Institution) {
        _selectedInstitution.value = institution
        startSyncingInstitutionData(institution.id)
    }

    fun setBranch(branchId: String) {
        _selectedBranchId.value = branchId
        val instId = _selectedInstitution.value?.id ?: return
        syncInventory(instId, branchId)
        syncRestaurantData(instId, branchId)
        syncRentalData(instId, branchId)
        syncServiceData(instId, branchId)
        syncPropertyData(instId, branchId)
    }

    private fun startSyncingInstitutionData(institutionId: String) {
        db.collection("institutions").document(institutionId).collection("branches")
            .addSnapshotListener { s, _ ->
                val list = s?.documents?.mapNotNull { it.toObject(Branch::class.java)?.copy(id = it.id) } ?: emptyList()
                _branches.value = list
                if (_selectedBranchId.value.isEmpty() && list.isNotEmpty()) {
                    setBranch(list.first().id)
                }
            }

        db.collection("institutions").document(institutionId).collection("staff")
            .addSnapshotListener { s, _ ->
                _staff.value = s?.documents?.mapNotNull { it.toObject(InstitutionStaff::class.java)?.copy(id = it.id) } ?: emptyList()
            }

        db.collection("institutions").document(institutionId).collection("staff_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { s, _ ->
                _staffLogs.value = s?.documents?.mapNotNull { it.toObject(StaffActivityLog::class.java)?.copy(id = it.id) } ?: emptyList()
            }

        db.collection("institutions").document(institutionId).collection("expenses")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { s, _ ->
                _expenses.value = s?.documents?.mapNotNull { it.toObject(Expense::class.java)?.copy(id = it.id) } ?: emptyList()
            }
            
        db.collection("institutions").document(institutionId).collection("loyalty")
            .orderBy("totalPoints", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { s, _ ->
                _loyalty.value = s?.documents?.mapNotNull { it.toObject(CustomerLoyalty::class.java) } ?: emptyList()
            }

        syncInventory(institutionId, _selectedBranchId.value)
        syncRestaurantData(institutionId, _selectedBranchId.value)
        syncRentalData(institutionId, _selectedBranchId.value)
        syncServiceData(institutionId, _selectedBranchId.value)
        syncPropertyData(institutionId, _selectedBranchId.value)

        db.collection("institutions").document(institutionId).collection("transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { s, _ ->
                val txs = s?.documents?.mapNotNull { it.toObject(POSTransaction::class.java)?.copy(id = it.id) } ?: emptyList()
                _transactions.value = txs
                runAnalysis(txs)
            }

        db.collection("institutions").document(institutionId).collection("visitors")
            .orderBy("entryTime", Query.Direction.DESCENDING)
            .addSnapshotListener { s, _ ->
                _visitors.value = s?.documents?.mapNotNull { it.toObject(InstitutionVisitor::class.java)?.copy(id = it.id) } ?: emptyList()
            }

        db.collection("institutions").document(institutionId).collection("queue_services")
            .addSnapshotListener { s, _ ->
                _queueServices.value = s?.documents?.mapNotNull { it.toObject(QueueService::class.java)?.copy(id = it.id) } ?: emptyList()
            }

        db.collection("institutions").document(institutionId).collection("queue_tickets")
            .whereIn("status", listOf("WAITING", "SERVING"))
            .addSnapshotListener { s, _ ->
                _activeTickets.value = s?.documents?.mapNotNull { it.toObject(QueueTicket::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    private fun syncInventory(institutionId: String, branchId: String) {
        val baseQuery: Query = db.collection("institutions").document(institutionId).collection("inventory")
        val query = if (branchId.isNotEmpty()) baseQuery.whereEqualTo("branchId", branchId) else baseQuery
        
        query.addSnapshotListener { s, _ ->
            val items = s?.documents?.mapNotNull { it.toObject(InventoryItem::class.java)?.copy(id = it.id) } ?: emptyList()
            _inventory.value = items
            _restaurantMenu.value = items.filter { it.isMenuItem && it.available }
        }
    }

    private fun syncRestaurantData(institutionId: String, branchId: String) {
        if (branchId.isEmpty()) return
        
        db.collection("institutions").document(institutionId).collection("restaurant_tables")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { s, _ ->
                _restaurantTables.value = s?.documents?.mapNotNull { it.toObject(RestaurantTable::class.java)?.copy(id = it.id) } ?: emptyList()
            }

        db.collection("institutions").document(institutionId).collection("restaurant_orders")
            .whereEqualTo("branchId", branchId)
            .whereNotIn("status", listOf("COMPLETED", "CANCELLED"))
            .addSnapshotListener { s, _ ->
                val orders = s?.documents?.mapNotNull { it.toObject(RestaurantOrder::class.java)?.copy(id = it.id) } ?: emptyList()
                _activeRestaurantOrders.value = orders
                _kdsOrders.value = orders.filter { it.status in listOf("NEW", "PREPARING", "READY") }.sortedBy { it.createdAt }
            }
            
        db.collection("institutions").document(institutionId).collection("menu_categories")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { s, _ ->
                _menuCategories.value = s?.documents?.mapNotNull { it.toObject(MenuCategory::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    private fun syncRentalData(institutionId: String, branchId: String) {
        if (branchId.isEmpty()) return
        db.collection("institutions").document(institutionId).collection("rental_items")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { s, _ ->
                _rentalItems.value = s?.documents?.mapNotNull { it.toObject(RentalItem::class.java)?.copy(id = it.id) } ?: emptyList()
            }
        db.collection("institutions").document(institutionId).collection("rental_bookings")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { s, _ ->
                _activeRentals.value = s?.documents?.mapNotNull { it.toObject(RentalBooking::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    private fun syncPropertyData(institutionId: String, branchId: String) {
        if (branchId.isEmpty()) return
        db.collection("institutions").document(institutionId).collection("property_listings")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { s, _ ->
                _propertyListings.value = s?.documents?.mapNotNull { it.toObject(PropertyListing::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    private fun syncServiceData(institutionId: String, branchId: String) {
        if (branchId.isEmpty()) return
        db.collection("institutions").document(institutionId).collection("service_appointments")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { s, _ ->
                _serviceAppointments.value = s?.documents?.mapNotNull { it.toObject(ServiceAppointment::class.java)?.copy(id = it.id) } ?: emptyList()
            }
    }

    private fun runAnalysis(txs: List<POSTransaction>) {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val dailyGroups = txs.groupBy { sdf.format(it.timestamp.toDate()) }
        _salesAnalysis.value = dailyGroups.map { (date, list) ->
            SalesAnalysis(
                date = date, 
                totalSales = list.sumOf { it.totalAmount }, 
                totalProfit = list.sumOf { it.profit }, 
                transactionCount = list.size
            )
        }.sortedByDescending { it.date }

        val prodMap = mutableMapOf<String, ProductAnalysis>()
        txs.forEach { tx ->
            tx.items.forEach { item ->
                val current = prodMap.getOrDefault(item.productId, ProductAnalysis(productId = item.productId, productName = item.productName))
                val invItem = _inventory.value.find { it.id == item.productId }
                val cost = invItem?.costPrice ?: 0.0
                prodMap[item.productId] = current.copy(
                    unitsSold = current.unitsSold + item.quantity,
                    revenue = current.revenue + (item.price * item.quantity),
                    profit = current.profit + (item.price - cost) * item.quantity
                )
            }
        }
        _productAnalysis.value = prodMap.values.toList().sortedByDescending { it.revenue }
    }

    // --- PROPERTY ACTIONS ---
    fun addPropertyListing(context: Context, listing: PropertyListing, imageUris: List<Uri>) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val urls = imageUris.map { uploadFile(context, it, "properties") }
                db.collection("institutions").document(instId).collection("property_listings")
                    .add(listing.copy(branchId = branchId, imageUrls = urls)).await()
                _statusMessage.value = "Listing added successfully"
                logStaffActivity("Property Add", listing.title)
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun updatePropertyAvailability(propertyId: String, isAvailable: Boolean) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("property_listings")
                .document(propertyId).update("isAvailable", isAvailable).await()
        }
    }

    // --- RENTAL ACTIONS ---
    fun addRentalItem(item: RentalItem) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("rental_items")
                .add(item.copy(branchId = branchId)).await()
            logStaffActivity("Rental Item Added", item.name)
        }
    }

    fun bookRental(booking: RentalBooking) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        viewModelScope.launch {
            try {
                db.runTransaction { ft ->
                    val bookingRef = db.collection("institutions").document(instId).collection("rental_bookings").document()
                    ft.set(bookingRef, booking.copy(branchId = branchId))
                    ft.update(db.collection("institutions").document(instId).collection("rental_items").document(booking.itemId), "status", "RENTED")
                }.await()
                _statusMessage.value = "Rental booked successfully"
            } catch (e: Exception) { _statusMessage.value = "Rental error: ${e.message}" }
        }
    }

    fun completeRental(bookingId: String, itemId: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.runTransaction { ft ->
                ft.update(db.collection("institutions").document(instId).collection("rental_bookings").document(bookingId), "status", "COMPLETED", "actualReturnTime", Timestamp.now())
                ft.update(db.collection("institutions").document(instId).collection("rental_items").document(itemId), "status", "AVAILABLE")
            }.await()
            _statusMessage.value = "Rental marked completed"
        }
    }

    // --- SERVICE ACTIONS ---
    fun scheduleService(appointment: ServiceAppointment) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("service_appointments")
                .add(appointment.copy(branchId = branchId)).await()
            _statusMessage.value = "Appointment scheduled"
        }
    }

    fun updateAppointmentStatus(appointmentId: String, status: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("service_appointments").document(appointmentId)
                .update("status", status).await()
        }
    }

    // --- VISITOR ACTIONS ---
    fun registerVisitor(visitor: InstitutionVisitor) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("visitors")
                .add(visitor).await()
            _statusMessage.value = "Visitor Registered"
        }
    }

    fun checkOutVisitor(visitorId: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("visitors").document(visitorId)
                .update("status", "OUT", "exitTime", Timestamp.now()).await()
        }
    }

    // --- QUEUE ACTIONS ---
    fun addQueueService(name: String, prefix: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("queue_services")
                .add(QueueService(name = name, prefix = prefix)).await()
        }
    }

    fun issueTicket(service: QueueService, customerName: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.runTransaction { ft ->
                val nextNum = service.currentTicket + 1
                val ticketRef = db.collection("institutions").document(instId).collection("queue_tickets").document()
                ft.set(ticketRef, QueueTicket(ticketNumber = nextNum, serviceCategory = service.name, customerName = customerName))
                ft.update(db.collection("institutions").document(instId).collection("queue_services").document(service.id), "currentTicket", nextNum)
            }.await()
            _statusMessage.value = "Ticket Issued"
        }
    }

    fun serveNext(serviceName: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            val nextTicket = _activeTickets.value.filter { it.serviceCategory == serviceName && it.status == "WAITING" }
                .minByOrNull { it.ticketNumber }
            if (nextTicket != null) {
                db.collection("institutions").document(instId).collection("queue_tickets").document(nextTicket.id)
                    .update("status", "SERVING", "servedAt", Timestamp.now()).await()
            }
        }
    }

    fun completeTicket(ticketId: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("queue_tickets").document(ticketId)
                .update("status", "COMPLETED").await()
        }
    }

    // --- RESTAURANT ACTIONS ---

    fun addRestaurantTable(tableNumber: String, capacity: Int) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("restaurant_tables")
                .add(RestaurantTable(tableNumber = tableNumber, capacity = capacity, branchId = branchId)).await()
            logStaffActivity("Table Added", "Table $tableNumber for $capacity seats")
        }
    }

    fun addMenuCategory(name: String) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("menu_categories")
                .add(MenuCategory(name = name, branchId = branchId)).await()
        }
    }

    fun createRestaurantOrder(table: RestaurantTable, items: List<RestaurantOrderItem>) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        val user = auth.currentUser ?: return
        val staff = _currentStaff.value
        
        val subtotal = items.sumOf { it.price * it.quantity }
        val tax = subtotal * (_selectedInstitution.value?.settings?.taxPercentage ?: 0.0) / 100.0
        
        val order = RestaurantOrder(
            tableId = table.id,
            tableNumber = table.tableNumber,
            createdBy = user.uid,
            waiterName = staff?.name ?: "Waiter",
            status = "NEW",
            subtotal = subtotal,
            taxAmount = tax,
            totalAmount = subtotal + tax,
            items = items,
            branchId = branchId
        )

        viewModelScope.launch {
            try {
                db.runTransaction { ft ->
                    val orderRef = db.collection("institutions").document(instId).collection("restaurant_orders").document()
                    ft.set(orderRef, order)
                    ft.update(db.collection("institutions").document(instId).collection("restaurant_tables").document(table.id), 
                        "status", "OCCUPIED", "currentOrderId", orderRef.id)
                }.await()
                _statusMessage.value = "Order sent to kitchen!"
                logStaffActivity("Order Created", "Table ${table.tableNumber}, items: ${items.size}")
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun updateRestaurantOrder(orderId: String, newItems: List<RestaurantOrderItem>) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { ft ->
                    val orderRef = db.collection("institutions").document(instId).collection("restaurant_orders").document(orderId)
                    val order = ft.get(orderRef).toObject(RestaurantOrder::class.java) ?: return@runTransaction
                    val combinedItems = order.items + newItems
                    val subtotal = combinedItems.sumOf { it.price * it.quantity }
                    val tax = subtotal * (_selectedInstitution.value?.settings?.taxPercentage ?: 0.0) / 100.0
                    
                    ft.update(orderRef, "items", combinedItems, "subtotal", subtotal, "taxAmount", tax, "totalAmount", subtotal + tax)
                }.await()
                _statusMessage.value = "Order updated!"
                logStaffActivity("Order Updated", "Added ${newItems.size} items to order $orderId")
            } catch (e: Exception) {
                _statusMessage.value = "Failed to update: ${e.message}"
            }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("restaurant_orders").document(orderId)
                .update("status", newStatus).await()
            logStaffActivity("Order Status", "$orderId -> $newStatus")
        }
    }

    fun updateOrderItemStatus(orderId: String, itemIndex: Int, newStatus: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { ft ->
                    val orderRef = db.collection("institutions").document(instId).collection("restaurant_orders").document(orderId)
                    val order = ft.get(orderRef).toObject(RestaurantOrder::class.java) ?: return@runTransaction
                    val updatedItems = order.items.toMutableList()
                    updatedItems[itemIndex] = updatedItems[itemIndex].copy(status = newStatus)
                    
                    val overallStatus = when {
                        updatedItems.all { it.status == "SERVED" } -> "SERVED"
                        updatedItems.all { it.status == "READY" || it.status == "SERVED" } -> "READY"
                        updatedItems.any { it.status == "PREPARING" || it.status == "READY" } -> "PREPARING"
                        else -> "NEW"
                    }
                    ft.update(orderRef, "items", updatedItems, "status", overallStatus)
                }.await()
            } catch (e: Exception) {
                Log.e("POS", "Item status update failed", e)
            }
        }
    }

    fun processRestaurantPayment(orderId: String, method: String, amount: Double) {
        val instId = _selectedInstitution.value?.id ?: return
        val staff = _currentStaff.value
        viewModelScope.launch {
            try {
                db.runTransaction { ft ->
                    val orderRef = db.collection("institutions").document(instId).collection("restaurant_orders").document(orderId)
                    val order = ft.get(orderRef).toObject(RestaurantOrder::class.java) ?: return@runTransaction
                    
                    val newPaidAmount = order.paidAmount + amount
                    val isFullyPaid = newPaidAmount >= (order.totalAmount - 0.05)
                    
                    ft.update(orderRef, 
                        "paidAmount", newPaidAmount, 
                        "paymentStatus", if (isFullyPaid) "PAID" else "PARTIAL",
                        "status", if (isFullyPaid) "COMPLETED" else order.status
                    )
                    
                    if (isFullyPaid) {
                        ft.update(db.collection("institutions").document(instId).collection("restaurant_tables").document(order.tableId), 
                            "status", "AVAILABLE", "currentOrderId", null)
                        
                        val txRef = db.collection("institutions").document(instId).collection("transactions").document()
                        ft.set(txRef, POSTransaction(
                            items = order.items.map { InstitutionCartItem(productId = it.itemId, productName = it.name, price = it.price, quantity = it.quantity.toDouble()) },
                            totalAmount = order.totalAmount,
                            paymentMethod = method,
                            branchId = order.branchId,
                            cashierId = staff?.uid ?: "",
                            cashierName = staff?.name ?: "System",
                            timestamp = Timestamp.now()
                        ))
                    }
                    
                    val paymentRef = db.collection("institutions").document(instId).collection("restaurant_payments").document()
                    ft.set(paymentRef, RestaurantPayment(orderId = orderId, method = method, amount = amount, cashierId = staff?.uid ?: ""))
                }.await()
                _statusMessage.value = "Payment recorded!"
                logStaffActivity("Payment", "Order $orderId: $amount via $method")
            } catch (e: Exception) {
                _statusMessage.value = "Payment error: ${e.message}"
            }
        }
    }

    fun printReceipt(order: RestaurantOrder) {
        val settings = _selectedInstitution.value?.settings
        if (settings?.printerIpAddress.isNullOrBlank() && settings?.printerMacAddress.isNullOrBlank()) {
            _statusMessage.value = "Printer not configured in Settings."
            return
        }
        _statusMessage.value = "Printing receipt to ${settings?.printerIpAddress ?: settings?.printerMacAddress}..."
    }

    private fun logStaffActivity(action: String, details: String) {
        val instId = _selectedInstitution.value?.id ?: return
        val staff = _currentStaff.value ?: return
        db.collection("institutions").document(instId).collection("staff_logs")
            .add(StaffActivityLog(staffId = staff.uid, staffName = staff.name, action = action, details = details))
    }

    // --- OTHER ACTIONS ---

    fun addStaff(name: String, email: String, role: String, branchId: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            try {
                db.collection("institutions").document(instId).collection("staff")
                    .add(InstitutionStaff(name = name, email = email, role = role, branchId = branchId)).await()
                _statusMessage.value = "Staff member added."
                logStaffActivity("Staff Added", "Name: $name, Role: $role")
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun addExpense(title: String, amount: Double, category: String) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        val uid = auth.currentUser?.uid ?: ""
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("expenses")
                .add(Expense(title = title, amount = amount, category = category, branchId = branchId, recordedBy = uid)).await()
            logStaffActivity("Expense Recorded", "$title: $amount")
        }
    }

    fun addToInventory(context: Context, item: InventoryItem, imageUri: Uri?) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        _isUploading.value = true
        viewModelScope.launch {
            try {
                var finalImageUrl = item.imageUrl
                if (imageUri != null) finalImageUrl = uploadFile(context, imageUri, "inventory")
                db.collection("institutions").document(instId).collection("inventory")
                    .add(item.copy(branchId = branchId, imageUrl = finalImageUrl)).await()
                _statusMessage.value = "${item.name} added"
                logStaffActivity("Inventory Add", item.name)
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun restockItem(productId: String, additionalQty: Double, newCostPrice: Double? = null) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            try {
                val itemRef = db.collection("institutions").document(instId).collection("inventory").document(productId)
                val updates = mutableMapOf<String, Any>("stockQuantity" to FieldValue.increment(additionalQty))
                newCostPrice?.let { updates["costPrice"] = it }
                itemRef.update(updates).await()
                _statusMessage.value = "Stock updated"
                logStaffActivity("Restock", "Product: $productId, qty: $additionalQty")
            } catch (e: Exception) {
                _statusMessage.value = "Restock failed: ${e.message}"
            }
        }
    }

    fun updateInstitutionSettings(settings: InstitutionSettings) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).update("settings", settings).await()
            _statusMessage.value = "Settings updated"
        }
    }

    private suspend fun uploadFile(context: Context, uri: Uri, folder: String): String = withContext(Dispatchers.IO) {
        val instId = _selectedInstitution.value?.id ?: "unknown"
        val fileName = "${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child("institutions/$instId/$folder/$fileName")
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        val outputStream = ByteArrayOutputStream()
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        ref.putBytes(outputStream.toByteArray()).await()
        return@withContext ref.downloadUrl.await().toString()
    }

    fun addToCart(item: InventoryItem, variation: String? = null) {
        val currentCart = _cart.value.toMutableList()
        val varText = variation ?: ""
        val existing = currentCart.find { it.productId == item.id && it.selectedVariation == varText }
        if (existing != null) {
            val updated = existing.copy(quantity = existing.quantity + 1.0)
            currentCart[currentCart.indexOf(existing)] = updated
        } else {
            currentCart.add(InstitutionCartItem(productId = item.id, productName = if (variation != null) "${item.name} ($variation)" else item.name, price = item.price, quantity = 1.0, imageUrl = item.imageUrl, selectedVariation = varText))
        }
        _cart.value = currentCart
    }

    fun updateCartQuantity(productId: String, delta: Double, variation: String = "") {
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.productId == productId && it.selectedVariation == variation }
        if (index != -1) {
            val invItem = _inventory.value.find { it.id == productId }
            val newQty = currentCart[index].quantity + delta
            if (newQty <= 0) currentCart.removeAt(index)
            else {
                var priceToUse = currentCart[index].price
                if (invItem != null && invItem.minWholesaleQuantity > 0 && newQty >= invItem.minWholesaleQuantity) priceToUse = invItem.wholesalePrice
                currentCart[index] = currentCart[index].copy(quantity = newQty, price = priceToUse)
            }
            _cart.value = currentCart
        }
    }

    fun checkout(method: String, discount: Double, customerName: String?) {
        val instId = _selectedInstitution.value?.id ?: return
        val branchId = _selectedBranchId.value
        val staff = _currentStaff.value
        val items = _cart.value
        if (items.isEmpty()) return

        viewModelScope.launch {
            try {
                val subtotal = items.sumOf { it.price * it.quantity }
                val tax = subtotal * (_selectedInstitution.value?.settings?.taxPercentage ?: 0.0) / 100.0
                val total = subtotal + tax - discount
                
                db.runTransaction { ft ->
                    val txRef = db.collection("institutions").document(instId).collection("transactions").document()
                    ft.set(txRef, POSTransaction(
                        items = items,
                        subtotal = subtotal,
                        taxAmount = tax,
                        discountAmount = discount,
                        totalAmount = total,
                        paymentMethod = method,
                        cashierId = staff?.uid ?: "",
                        cashierName = staff?.name ?: "System",
                        branchId = branchId,
                        customerName = customerName,
                        timestamp = Timestamp.now()
                    ))
                    
                    items.forEach { cartItem ->
                        val itemRef = db.collection("institutions").document(instId).collection("inventory").document(cartItem.productId)
                        ft.update(itemRef, "stockQuantity", FieldValue.increment(-cartItem.quantity))
                    }
                }.await()
                
                _cart.value = emptyList()
                _statusMessage.value = "Transaction Complete"
                logStaffActivity("Retail Sale", "Total: $total via $method")
            } catch (e: Exception) {
                _statusMessage.value = "Checkout failed: ${e.message}"
            }
        }
    }

    fun clearCart() { _cart.value = emptyList() }

    fun registerInstitution(name: String, category: String, location: String, settings: InstitutionSettings) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val institution = Institution(
                    name = name, 
                    category = category, 
                    location = location, 
                    ownerId = uid,
                    settings = settings
                )
                val doc = db.collection("institutions").add(institution).await()
                db.collection("institutions").document(doc.id).collection("branches")
                    .add(Branch(name = "Main Branch", location = location)).await()
                _statusMessage.value = "Institution Registered"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun addBranch(name: String, location: String, phone: String) {
        val instId = _selectedInstitution.value?.id ?: return
        viewModelScope.launch {
            db.collection("institutions").document(instId).collection("branches")
                .add(Branch(name = name, location = location, phone = phone)).await()
            _statusMessage.value = "Branch added"
        }
    }

    fun clearStatus() { _statusMessage.value = null }
}
