package com.joseph.cbchubpro.ui.management

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class RevenueSummary(
    val schoolSubscriptionRevenue: Double = 0.0,
    val parentSubscriptionRevenue: Double = 0.0,
    val marketplaceServiceFees: Double = 0.0,
    val verificationFees: Double = 0.0,
    val pickupMtaaniFees: Double = 0.0,
    val pointsRevenueShare: Double = 0.0,
    val totalRevenue: Double = 0.0
)

class AdminViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _users = MutableStateFlow<List<AppUser>>(emptyList())
    val users = _users.asStateFlow()

    private val _allOrders = MutableStateFlow<List<Order>>(emptyList())
    val allOrders = _allOrders.asStateFlow()

    private val _pointTransactions = MutableStateFlow<List<PointTransaction>>(emptyList())
    val pointTransactions = _pointTransactions.asStateFlow()

    private val _platformSettings = MutableStateFlow(PlatformSettings())
    val platformSettings = _platformSettings.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    val revenue: StateFlow<RevenueSummary> = combine(_users, _allOrders, _pointTransactions, _platformSettings) { usersList, orders, points, settings ->
        val schoolRevenue = usersList.filter { it.role == "school" && it.subscriptionPaid }
            .sumOf { (if (it.subscriptionTier == "Premium") settings.premiumSchoolFee else settings.basicSchoolFee) * it.studentCount }

        val parentRevenue = usersList.filter { it.role == "parent/guardian" && it.childrenCount > 1 }
            .sumOf { (it.childrenCount - 1).toDouble() * settings.parentExtraChildFee }
            
        val marketplaceFees = orders.filter { it.status == OrderStatus.COMPLETED && it.deliveryMethod != DeliveryMethod.PICKUP_MTAANI_COD }.sumOf { it.totalAmount * 0.02 }
        
        val pmFees = orders.filter { it.status == OrderStatus.COMPLETED && it.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD }.sumOf { it.platformRevenue }
        
        val verifiedRevenue = usersList.count { it.isVerified }.toDouble() * settings.vendorVerificationFee

        val pointsShare = points.filter { it.status == "COMPLETED" }.sumOf { it.platformRevenue }

        RevenueSummary(
            schoolSubscriptionRevenue = schoolRevenue,
            parentSubscriptionRevenue = parentRevenue,
            marketplaceServiceFees = marketplaceFees,
            verificationFees = verifiedRevenue,
            pickupMtaaniFees = pmFees,
            pointsRevenueShare = pointsShare,
            totalRevenue = schoolRevenue + parentRevenue + marketplaceFees + verifiedRevenue + pmFees + pointsShare
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RevenueSummary())

    init {
        fetchUsers()
        fetchAllOrders()
        fetchPlatformSettings()
        fetchPointTransactions()
    }

    private fun fetchUsers() {
        db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("Admin", "Error fetching users", error)
                return@addSnapshotListener
            }
            _users.value = snapshot?.documents?.mapNotNull { doc ->
                try { doc.toObject(AppUser::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
            } ?: emptyList()
        }
    }

    private fun fetchPlatformSettings() {
        db.collection("settings").document("platform").addSnapshotListener { snapshot, _ ->
            snapshot?.toObject(PlatformSettings::class.java)?.let {
                _platformSettings.value = it
            }
        }
    }

    private fun fetchAllOrders() {
        db.collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _allOrders.value = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(Order::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
            }
    }

    private fun fetchPointTransactions() {
        db.collection("point_transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                _pointTransactions.value = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(PointTransaction::class.java)?.copy(id = doc.id) } catch (_: Exception) { null }
                } ?: emptyList()
            }
    }

    fun updatePlatformSettings(settings: PlatformSettings) {
        viewModelScope.launch {
            _isUpdating.value = true
            try {
                db.collection("settings").document("platform").set(settings).await()
                _statusMessage.value = "✅ Platform settings updated"
            } catch (e: Exception) {
                _statusMessage.value = "❌ Error: ${e.message}"
            } finally {
                _isUpdating.value = false
            }
        }
    }

    fun verifyUser(userId: String, isVerified: Boolean) {
        viewModelScope.launch {
            try {
                db.collection("users").document(userId).update(
                    mapOf(
                        "isVerified" to isVerified,
                        "verificationPending" to false
                    )
                ).await()
                _statusMessage.value = if (isVerified) "✅ Account Verified" else "❌ Verification Rejected"
            } catch (e: Exception) {
                _statusMessage.value = "❌ Error: ${e.message}"
            }
        }
    }

    fun toggleUserMetric(userId: String, metric: String, value: Boolean) {
        viewModelScope.launch {
            try {
                db.collection("users").document(userId).update(metric, value).await()
                _statusMessage.value = "✅ $metric updated"
            } catch (e: Exception) {
                _statusMessage.value = "❌ Error: ${e.message}"
            }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            try {
                db.collection("orders").document(orderId).update("status", newStatus).await()
                _statusMessage.value = "✅ Order status updated to ${newStatus.name}"
            } catch (e: Exception) {
                _statusMessage.value = "❌ Error: ${e.message}"
            }
        }
    }

    fun signOut() { auth.signOut() }
    fun clearStatus() { _statusMessage.value = null }

    fun formatCurrency(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("sw", "KE"))
        formatter.currency = Currency.getInstance("KES")
        return formatter.format(amount)
    }

    fun getUserName(userId: String): String {
        return users.value.find { it.id == userId }?.name ?: "Unknown User ($userId)"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManagementScreen(onLogoutClicked: () -> Unit, viewModel: AdminViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Revenue", "Point Sales", "Verification", "Orders", "Users", "Settings")

    val users by viewModel.users.collectAsState()
    val revenue by viewModel.revenue.collectAsState()
    val settings by viewModel.platformSettings.collectAsState()
    val allOrders by viewModel.allOrders.collectAsState()
    val pointTransactions by viewModel.pointTransactions.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        status?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("CBC Hub Control", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.signOut(); onLogoutClicked() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> RevenueTab(revenue, viewModel)
                1 -> AdminPointSalesTab(pointTransactions, viewModel)
                2 -> AdminVerificationTab(users.filter { it.verificationPending }, viewModel)
                3 -> AdminOrdersTab(allOrders, viewModel)
                4 -> AdminUsersTab(users, viewModel)
                5 -> AdminPlatformSettingsTab(settings, viewModel)
            }
        }
    }
}

@Composable
fun RevenueTab(revenue: RevenueSummary, viewModel: AdminViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Total Platform Revenue", style = MaterialTheme.typography.titleMedium)
                    Text(viewModel.formatCurrency(revenue.totalRevenue), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { AdminRevenueItem("School Subscriptions", revenue.schoolSubscriptionRevenue, viewModel) }
        item { AdminRevenueItem("Parent Extra Child Fees", revenue.parentSubscriptionRevenue, viewModel) }
        item { AdminRevenueItem("Marketplace Service Fees (2%)", revenue.marketplaceServiceFees, viewModel) }
        item { AdminRevenueItem("Points Revenue Share (Commission)", revenue.pointsRevenueShare, viewModel) }
        item { AdminRevenueItem("Verification Fees", revenue.verificationFees, viewModel) }
        item { AdminRevenueItem("Pickup Mtaani Hub Revenue", revenue.pickupMtaaniFees, viewModel) }
    }
}

@Composable
fun AdminPointSalesTab(transactions: List<PointTransaction>, viewModel: AdminViewModel) {
    val completed = transactions.filter { it.status == "COMPLETED" }
    
    Column(Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Total Point Sales", style = MaterialTheme.typography.labelSmall)
                    Text(viewModel.formatCurrency(completed.sumOf { it.amountPaid }), fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Platform Share", style = MaterialTheme.typography.labelSmall)
                    Text(viewModel.formatCurrency(completed.sumOf { it.platformRevenue }), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions) { trans ->
                val date = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(trans.timestamp.toDate())
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(viewModel.getUserName(trans.userId), fontWeight = FontWeight.Bold)
                            Text("Date: $date", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("ID: ${trans.id.take(8).uppercase()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(viewModel.formatCurrency(trans.amountPaid), fontWeight = FontWeight.Bold)
                            Text("Share: ${viewModel.formatCurrency(trans.platformRevenue)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(trans.status, color = if(trans.status == "COMPLETED") Color(0xFF4CAF50) else Color.Gray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminRevenueItem(label: String, amount: Double, viewModel: AdminViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(viewModel.formatCurrency(amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AdminVerificationTab(pendingUsers: List<AppUser>, viewModel: AdminViewModel) {
    if (pendingUsers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pending verification requests") }
    } else {
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(pendingUsers) { user ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Badge { Text(user.role.uppercase()) }
                        }
                        Text("Email: ${user.email}")
                        Text("Location: ${user.location}")
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.verifyUser(user.id, true) }, Modifier.weight(1f)) { Text("Approve") }
                            OutlinedButton(onClick = { viewModel.verifyUser(user.id, false) }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Reject") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminOrdersTab(orders: List<Order>, viewModel: AdminViewModel) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (orders.isEmpty()) item { Text("No orders found", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        items(orders) { order ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Order #${order.id.take(8).uppercase()}", fontWeight = FontWeight.Bold)
                        StatusBadge(order.status)
                    }
                    Text("Total: ${viewModel.formatCurrency(order.totalAmount)}")
                    Text("Buyer: ${order.buyerName}")
                    Text("Vendor: ${order.vendorName}")
                    Text("Method: ${order.deliveryMethod}")
                    if (order.pickupPoint.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, null, modifier = Modifier.size(14.dp), tint = Color(0xFFE91E63))
                            Spacer(Modifier.width(4.dp))
                            Text("Pickup Point: ${order.pickupPoint}", color = Color(0xFFE91E63), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    
                    if (order.deliveryMethod == DeliveryMethod.PICKUP_MTAANI_COD && order.status != OrderStatus.PAID && order.status != OrderStatus.COMPLETED) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.updateOrderStatus(order.id, OrderStatus.PAID) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Default.Payments, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm COD Cash Received")
                        }
                        Text("Click once Pickup Mtaani remits the cash for this order.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUsersTab(users: List<AppUser>, viewModel: AdminViewModel) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(users) { user ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.name, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        if (user.isVerified) Icon(Icons.Default.Verified, "Verified", tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                        else Badge(containerColor = Color.Gray) { Text("UNVERIFIED", fontSize = 8.sp) }
                    }
                    Text("${user.role} | ${user.location}", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(8.dp))
                    Text("Trust Badges & Verification", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = user.physicalLocationVerified,
                            onClick = { viewModel.toggleUserMetric(user.id, "physicalLocationVerified", !user.physicalLocationVerified) },
                            label = { Text("Loc. Verified", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = user.phoneVerified,
                            onClick = { viewModel.toggleUserMetric(user.id, "phoneVerified", !user.phoneVerified) },
                            label = { Text("Phone Verified", fontSize = 10.sp) }
                        )
                    }
                    
                    Text("Rating: ${user.rating} (${user.reviewCount} reviews) | Response: ${user.sellerResponseTime}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun AdminPlatformSettingsTab(settings: PlatformSettings, viewModel: AdminViewModel) {
    var s by remember(settings) { mutableStateOf(settings) }
    
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Marketplace Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(value = s.vendorListingFee.toString(), onValueChange = { s = s.copy(vendorListingFee = it.toDoubleOrNull() ?: 0.0) }, label = { Text("Vendor Listing Fee") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = s.itemVerificationFee.toString(), onValueChange = { s = s.copy(itemVerificationFee = it.toDoubleOrNull() ?: 0.0) }, label = { Text("Item Verification Fee") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = s.vendorVerificationFee.toString(), onValueChange = { s = s.copy(vendorVerificationFee = it.toDoubleOrNull() ?: 0.0) }, label = { Text("Vendor Verification Fee") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        OutlinedTextField(value = s.pickupMtaaniAgentFee.toString(), onValueChange = { s = s.copy(pickupMtaaniAgentFee = it.toDoubleOrNull() ?: 0.0) }, label = { Text("Default Pickup Mtaani Fee (Fallback)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        Spacer(modifier = Modifier.height(16.dp))
        Text("PesaPal API Credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = s.pesapalConsumerKey, onValueChange = { s = s.copy(pesapalConsumerKey = it) }, label = { Text("PesaPal Consumer Key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s.pesapalConsumerSecret, onValueChange = { s = s.copy(pesapalConsumerSecret = it) }, label = { Text("PesaPal Consumer Secret") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(value = s.pesapalIpnId, onValueChange = { s = s.copy(pesapalIpnId = it) }, label = { Text("PesaPal IPN ID") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(8.dp))
        Text("Pickup Mtaani API Credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = s.pickupMtaaniApiKey, onValueChange = { s = s.copy(pickupMtaaniApiKey = it) }, label = { Text("Pickup Mtaani API Key") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = s.pickupMtaaniApiSecret, onValueChange = { s = s.copy(pickupMtaaniApiSecret = it) }, label = { Text("Pickup Mtaani Secret") }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.updatePlatformSettings(s) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save Settings")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
