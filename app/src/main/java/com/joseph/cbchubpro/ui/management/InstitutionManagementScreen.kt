package com.joseph.cbchubpro.ui.management

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionManagementScreen(
    onLogoutClicked: () -> Unit,
    viewModel: InstitutionViewModel = viewModel()
) {
    val myInstitutions by viewModel.myInstitutions.collectAsState()
    val selectedInstitution by viewModel.selectedInstitution.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val selectedBranchId by viewModel.selectedBranchId.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentStaff by viewModel.currentStaff.collectAsState()
    
    var showRegisterDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(selectedInstitution?.name ?: "Institution Hub", style = MaterialTheme.typography.titleMedium)
                        if (selectedInstitution != null) {
                            Text(selectedInstitution!!.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    if (branches.size > 1) {
                        var showBranchMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showBranchMenu = true }) {
                                Icon(Icons.Default.LocationOn, "Switch Branch")
                            }
                            DropdownMenu(expanded = showBranchMenu, onDismissRequest = { showBranchMenu = false }) {
                                branches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name) },
                                        onClick = { viewModel.setBranch(branch.id); showBranchMenu = false },
                                        trailingIcon = { if (selectedBranchId == branch.id) Icon(Icons.Default.Check, null) }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { showRegisterDialog = true }) {
                        Icon(Icons.Default.AddBusiness, contentDescription = "Register New")
                    }
                    IconButton(onClick = onLogoutClicked) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (myInstitutions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Welcome! Start by registering your institution.", textAlign = TextAlign.Center)
                        Button(onClick = { showRegisterDialog = true }, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Register Institution")
                        }
                    }
                }
            } else {
                val settings = selectedInstitution?.settings ?: InstitutionSettings()
                
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {}
                ) {
                    val tabs = mutableListOf<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, Int>>()
                    
                    // Business Logic Tabs
                    if (settings.hasRestaurant) {
                        if (currentStaff?.role != "Chef") {
                            tabs.add(Triple("Tables", Icons.Default.TableBar, 10))
                        }
                        if (currentStaff?.role in listOf("Chef", "Admin", "Manager")) {
                            tabs.add(Triple("KDS", Icons.Default.SoupKitchen, 11))
                        }
                        if (currentStaff?.role in listOf("Admin", "Manager")) {
                            tabs.add(Triple("Menu", Icons.Default.RestaurantMenu, 12))
                        }
                    }

                    if (settings.hasPOS) {
                        tabs.add(Triple("POS", Icons.Default.PointOfSale, 0))
                    }

                    if (settings.hasRentalManagement) {
                        tabs.add(Triple("Rentals", Icons.Default.Key, 20))
                    }

                    if (settings.hasServiceBooking) {
                        tabs.add(Triple("Booking", Icons.Default.Event, 21))
                    }

                    if (settings.hasVisitorManagement) {
                        tabs.add(Triple("Visitors", Icons.Default.PersonSearch, 22))
                    }

                    if (settings.hasQueueSystem) {
                        tabs.add(Triple("Queue", Icons.Default.Queue, 23))
                    }
                    
                    // Common Admin/Management Tabs
                    tabs.add(Triple("Analysis", Icons.Default.BarChart, 3))
                    tabs.add(Triple("Inventory", Icons.Default.Inventory, 4))
                    tabs.add(Triple("Finance", Icons.Default.Payments, 5))
                    tabs.add(Triple("Staff", Icons.Default.Badge, 6))
                    tabs.add(Triple("Settings", Icons.Default.Settings, 7))

                    // Handle edge case where selectedTab might be out of bounds if tabs changed
                    if (selectedTab >= tabs.size) {
                        LaunchedEffect(tabs.size) { selectedTab = 0 }
                    }

                    tabs.forEachIndexed { index, (title, icon, _) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icon, null) },
                            text = { Text(title, fontSize = 10.sp) }
                        )
                    }
                }

                val currentTabId = if (selectedTab < 0 || selectedTab >= (selectedInstitution?.let { 
                    // This is a bit complex to calculate perfectly reactively without a more stable tab structure
                    // But let's assume we can find the ID by index
                    val list = mutableListOf<Int>()
                    val s = it.settings
                    if (s.hasRestaurant) {
                        if (currentStaff?.role != "Chef") list.add(10)
                        if (currentStaff?.role in listOf("Chef", "Admin", "Manager")) list.add(11)
                        if (currentStaff?.role in listOf("Admin", "Manager")) list.add(12)
                    }
                    if (s.hasPOS) list.add(0)
                    if (s.hasRentalManagement) list.add(20)
                    if (s.hasServiceBooking) list.add(21)
                    if (s.hasVisitorManagement) list.add(22)
                    if (s.hasQueueSystem) list.add(23)
                    list.addAll(listOf(3, 4, 5, 6, 7))
                    list
                } ?: emptyList<Int>()).size) 0 else {
                    val list = mutableListOf<Int>()
                    val s = settings
                    if (s.hasRestaurant) {
                        if (currentStaff?.role != "Chef") list.add(10)
                        if (currentStaff?.role in listOf("Chef", "Admin", "Manager")) list.add(11)
                        if (currentStaff?.role in listOf("Admin", "Manager")) list.add(12)
                    }
                    if (s.hasPOS) list.add(0)
                    if (s.hasRentalManagement) list.add(20)
                    if (s.hasServiceBooking) list.add(21)
                    if (s.hasVisitorManagement) list.add(22)
                    if (s.hasQueueSystem) list.add(23)
                    list.addAll(listOf(3, 4, 5, 6, 7))
                    list[selectedTab]
                }

                when (currentTabId) {
                    0 -> POSTab(viewModel)
                    3 -> AnalysisTab(viewModel)
                    4 -> InventoryManagementTab(viewModel)
                    5 -> FinanceTab(viewModel)
                    6 -> StaffTab(viewModel)
                    7 -> InstitutionSettingsTab(selectedInstitution, viewModel)
                    10 -> RestaurantTablesTab(viewModel)
                    11 -> RestaurantKDSTab(viewModel)
                    12 -> RestaurantMenuTab(viewModel)
                    20 -> RentalsTab(viewModel)
                    21 -> ServiceBookingTab(viewModel)
                    22 -> VisitorManagementTab(viewModel)
                    23 -> QueueManagementTab(viewModel)
                }
            }
        }
    }

    if (showRegisterDialog) {
        RegisterInstitutionDialog(
            onDismiss = { showRegisterDialog = false },
            onConfirm = { name, cat, loc, settings ->
                viewModel.registerInstitution(name, cat, loc, settings)
                showRegisterDialog = false
            }
        )
    }
}

@Composable
fun RentalsTab(viewModel: InstitutionViewModel) {
    val items by viewModel.rentalItems.collectAsState()
    val bookings by viewModel.activeRentals.collectAsState()
    var showAddItem by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Rental Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddItem = true }) {
                Icon(Icons.Default.Add, null)
                Text("Add Item")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                items) { item ->
                val booking = bookings.find { it.itemId == item.id && it.status == "ACTIVE" }
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = { Text("${item.category} | ${item.status}") },
                        trailingContent = {
                            if (item.status == "AVAILABLE") {
                                Button(onClick = { /* Book */ }) { Text("Rent Out") }
                            } else {
                                Text("Occupied", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceBookingTab(viewModel: InstitutionViewModel) {
    val appointments by viewModel.serviceAppointments.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Service Bookings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(appointments) { appt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(appt.customerName) },
                        supportingContent = { Text("${appt.serviceName} at ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(appt.scheduledTime.toDate())}") },
                        trailingContent = { Text(appt.status, color = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }
    }
}

@Composable
fun VisitorManagementTab(viewModel: InstitutionViewModel) {
    val visitors by viewModel.visitors.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Visitor Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visitors) { visitor ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(visitor.name) },
                        supportingContent = { Text("Visiting: ${visitor.hostName} | ${visitor.purpose}") },
                        trailingContent = { Text(visitor.status) }
                    )
                }
            }
        }
    }
}

@Composable
fun QueueManagementTab(viewModel: InstitutionViewModel) {
    val queueServices by viewModel.queueServices.collectAsState()
    val activeTickets by viewModel.activeTickets.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Queue System", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            queueServices.forEach { service ->
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(service.name, style = MaterialTheme.typography.labelSmall)
                        Text("${service.prefix}${service.currentTicket}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { /* Call Next */ }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantTablesTab(viewModel: InstitutionViewModel) {
    val tables by viewModel.restaurantTables.collectAsState()
    val activeOrders by viewModel.activeRestaurantOrders.collectAsState()
    var selectedTable by remember { mutableStateOf<RestaurantTable?>(null) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showAddTableDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Table Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showAddTableDialog = true }) {
                Icon(Icons.Default.Add, "Add Table")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tables) { table ->
                val order = activeOrders.find { it.id == table.currentOrderId }
                val color = when (table.status) {
                    "AVAILABLE" -> Color(0xFF4CAF50)
                    "OCCUPIED" -> Color(0xFFF44336)
                    "DIRTY" -> Color(0xFFFF9800)
                    else -> Color.Gray
                }
                
                Card(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable {
                            selectedTable = table
                            if (table.status == "AVAILABLE" || table.status == "OCCUPIED") {
                                showOrderDialog = true
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                    border = BorderStroke(2.dp, color)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("T-${table.tableNumber}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color)
                        Text("${table.capacity} Seats", style = MaterialTheme.typography.labelSmall)
                        if (order != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("KES ${order.totalAmount}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(order.status, style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                }
            }
        }
    }

    if (showAddTableDialog) {
        var tableNum by remember { mutableStateOf("") }
        var cap by remember { mutableStateOf("4") }
        AlertDialog(
            onDismissRequest = { showAddTableDialog = false },
            title = { Text("Add New Table") },
            text = {
                Column {
                    OutlinedTextField(value = tableNum, onValueChange = { tableNum = it }, label = { Text("Table Number") })
                    OutlinedTextField(value = cap, onValueChange = { cap = it }, label = { Text("Capacity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.addRestaurantTable(tableNum, cap.toIntOrNull() ?: 4); showAddTableDialog = false }) { Text("Add") }
            }
        )
    }

    if (showOrderDialog && selectedTable != null) {
        val existingOrder = activeOrders.find { it.id == selectedTable!!.currentOrderId }
        RestaurantOrderDialog(
            table = selectedTable!!,
            existingOrder = existingOrder,
            viewModel = viewModel,
            onDismiss = { showOrderDialog = false; selectedTable = null },
            onCheckout = { 
                showOrderDialog = false
                showCheckoutDialog = true 
            }
        )
    }

    if (showCheckoutDialog && selectedTable != null) {
        val order = activeOrders.find { it.id == selectedTable!!.currentOrderId }
        if (order != null) {
            RestaurantCheckoutDialog(
                order = order,
                viewModel = viewModel,
                onDismiss = { showCheckoutDialog = false; selectedTable = null },
                onConfirm = { method, amount ->
                    viewModel.processRestaurantPayment(order.id, method, amount)
                    if (amount >= (order.totalAmount - order.paidAmount - 0.1)) {
                        showCheckoutDialog = false
                        selectedTable = null
                    }
                }
            )
        }
    }
}

@Composable
fun RestaurantOrderDialog(
    table: RestaurantTable,
    existingOrder: RestaurantOrder?,
    viewModel: InstitutionViewModel,
    onDismiss: () -> Unit,
    onCheckout: () -> Unit
) {
    val menu by viewModel.restaurantMenu.collectAsState()
    val categories by viewModel.menuCategories.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var currentItems by remember { mutableStateOf(mutableListOf<RestaurantOrderItem>()) }
    var searchQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                    Text("Order: Table ${table.tableNumber}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (existingOrder != null) {
                        Button(onClick = onCheckout, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                            Icon(Icons.Default.Payments, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Bill: KES ${existingOrder.totalAmount}")
                        }
                    }
                }

                Row(modifier = Modifier.weight(1f)) {
                    // Menu Category Sidebar
                    Column(modifier = Modifier.width(100.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant).verticalScroll(rememberScrollState())) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = null }
                                .background(if (selectedCategory == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(16.dp)
                        ) {
                            Text("All Items", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        categories.forEach { cat ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = cat.id }
                                    .background(if (selectedCategory == cat.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(16.dp)
                            ) {
                                Text(cat.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Menu Grid
                    Column(modifier = Modifier.weight(1f).padding(8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search menu...") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        val filteredMenu = menu.filter { 
                            (selectedCategory == null || it.categoryId == selectedCategory) &&
                            (searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true))
                        }
                        
                        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredMenu) { item ->
                                Card(
                                    onClick = { 
                                        val existing = currentItems.find { it.itemId == item.id }
                                        if (existing != null) {
                                            val index = currentItems.indexOf(existing)
                                            currentItems[index] = existing.copy(quantity = existing.quantity + 1)
                                        } else {
                                            currentItems.add(RestaurantOrderItem(itemId = item.id, name = item.name, price = item.price, quantity = 1))
                                        }
                                        currentItems = currentItems.toMutableList()
                                    },
                                    colors = CardDefaults.cardColors(containerColor = if (item.available) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                    enabled = item.available
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("KES ${item.price}", style = MaterialTheme.typography.labelSmall)
                                        if (!item.available) Text("OUT OF STOCK", color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Current Selection
                    Surface(modifier = Modifier.width(300.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Basket", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            
                            if (existingOrder != null) {
                                Text("On Kitchen Ticket:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                existingOrder.items.forEach { item ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${item.quantity}x ${item.name}", fontSize = 12.sp)
                                        Text(item.status, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                itemsIndexed(currentItems) { index, item ->
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                            IconButton(onClick = {
                                                if (item.quantity > 1) {
                                                    currentItems[index] = item.copy(quantity = item.quantity - 1)
                                                } else {
                                                    currentItems.removeAt(index)
                                                }
                                                currentItems = currentItems.toMutableList()
                                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.RemoveCircleOutline, null, Modifier.size(16.dp)) }
                                            Text("${item.quantity}", fontSize = 12.sp)
                                            IconButton(onClick = {
                                                currentItems[index] = item.copy(quantity = item.quantity + 1)
                                                currentItems = currentItems.toMutableList()
                                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.AddCircleOutline, null, Modifier.size(16.dp)) }
                                        }
                                        OutlinedTextField(
                                            value = item.notes,
                                            onValueChange = { 
                                                currentItems[index] = item.copy(notes = it)
                                                currentItems = currentItems.toMutableList()
                                            },
                                            placeholder = { Text("Add modifiers (e.g. no onions)", fontSize = 10.sp) },
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { 
                                    if (existingOrder != null) viewModel.updateRestaurantOrder(existingOrder.id, currentItems)
                                    else viewModel.createRestaurantOrder(table, currentItems)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                enabled = currentItems.isNotEmpty()
                            ) {
                                Text(if (existingOrder != null) "Update Order" else "Send to Kitchen")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantKDSTab(viewModel: InstitutionViewModel) {
    val orders by viewModel.kdsOrders.collectAsState()
    val context = LocalContext.current
    
    // Play alert for new orders
    LaunchedEffect(orders.size) {
        if (orders.any { it.status == "NEW" }) {
            try {
                val mp = MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                mp.start()
            } catch (e: Exception) { Log.e("KDS", "Sound failed") }
        }
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val statuses = listOf("NEW", "PREPARING", "READY")
        statuses.forEach { status ->
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = when(status) {
                        "NEW" -> MaterialTheme.colorScheme.errorContainer
                        "PREPARING" -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(status, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(orders.filter { it.status == status }) { order ->
                        KDSOrderCard(order, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun KDSOrderCard(order: RestaurantOrder, viewModel: InstitutionViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Table ${order.tableNumber}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(order.createdAt.toDate()), style = MaterialTheme.typography.labelSmall)
            }
            Text("Waiter: ${order.waiterName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            
            order.items.forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.status in listOf("READY", "SERVED"),
                        onCheckedChange = { 
                            val next = if (it) "READY" else "PREPARING"
                            viewModel.updateOrderItemStatus(order.id, index, next)
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.quantity}x ${item.name}", fontWeight = FontWeight.Medium)
                        if (item.notes.isNotEmpty()) {
                            Text(item.notes, color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.Yellow.copy(alpha = 0.3f)))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.status == "NEW") {
                    Button(onClick = { viewModel.updateOrderStatus(order.id, "PREPARING") }, modifier = Modifier.weight(1f)) { Text("Start Cooking") }
                } else if (order.status == "PREPARING") {
                    Button(onClick = { viewModel.updateOrderStatus(order.id, "READY") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Order Ready") }
                } else if (order.status == "READY") {
                    Button(onClick = { viewModel.updateOrderStatus(order.id, "SERVED") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Mark Served") }
                }
            }
        }
    }
}

@Composable
fun RestaurantCheckoutDialog(
    order: RestaurantOrder,
    viewModel: InstitutionViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("Cash") }
    var payAmount by remember { mutableStateOf(String.format("%.2f", order.totalAmount - order.paidAmount)) }
    val remaining = order.totalAmount - order.paidAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Checkout: Table ${order.tableNumber}")
                IconButton(onClick = { viewModel.printReceipt(order) }) {
                    Icon(Icons.Default.Print, "Print Receipt")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Bill:")
                    Text("KES ${order.totalAmount}", fontWeight = FontWeight.Bold)
                }
                if (order.paidAmount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid Previously:")
                        Text("KES ${order.paidAmount}", color = Color(0xFF4CAF50))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Remaining Balance:", fontWeight = FontWeight.Bold)
                    Text("KES ${String.format("%.2f", remaining)}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                }

                OutlinedTextField(
                    value = payAmount,
                    onValueChange = { payAmount = it },
                    label = { Text("Payment Amount (Split Bill)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = { Text("KES", modifier = Modifier.padding(end = 8.dp)) }
                )

                Text("Payment Method", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Cash", "M-Pesa", "Card").forEach { method ->
                        FilterChip(selected = selectedMethod == method, onClick = { selectedMethod = method }, label = { Text(method) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMethod, payAmount.toDoubleOrNull() ?: remaining) }) {
                Text("Record Payment")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun RestaurantMenuTab(viewModel: InstitutionViewModel) {
    val menu by viewModel.restaurantMenu.collectAsState()
    val categories by viewModel.menuCategories.collectAsState()
    var showAddCategory by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Menu Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAddCategory = true }) { Text("+ Category") }
                Button(onClick = { showAddItem = true }) { Text("+ Menu Item") }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            categories.forEach { cat ->
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(4.dp)) {
                        Text(cat.name, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                items(menu.filter { it.categoryId == cat.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        ListItem(
                            headlineContent = { Text(item.name) },
                            supportingContent = { Text("KES ${item.price}") },
                            trailingContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (item.available) "Available" else "Sold Out", style = MaterialTheme.typography.labelSmall, color = if (item.available) Color(0xFF4CAF50) else Color.Red)
                                    Spacer(Modifier.width(8.dp))
                                    Switch(checked = item.available, onCheckedChange = { viewModel.restockItem(item.id, if (it) 100.0 else -item.stockQuantity) })
                                }
                            }
                        )
                    }
                }
            }
            if (menu.any { it.categoryId.isEmpty() }) {
                item { Text("Uncategorized", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
                items(menu.filter { it.categoryId.isEmpty() }) { item ->
                    ListItem(headlineContent = { Text(item.name) }, supportingContent = { Text("KES ${item.price}") })
                }
            }
        }
    }

    if (showAddCategory) {
        var catName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategory = false },
            title = { Text("Add Category") },
            text = { OutlinedTextField(value = catName, onValueChange = { catName = it }, label = { Text("Category Name") }) },
            confirmButton = { Button(onClick = { viewModel.addMenuCategory(catName); showAddCategory = false }) { Text("Add") } }
        )
    }

    if (showAddItem) {
        AddInventoryDialog(
            isUploading = false,
            onDismiss = { showAddItem = false },
            onConfirm = { item, uri ->
                viewModel.addToInventory(context, item.copy(isMenuItem = true), uri)
                showAddItem = false
            }
        )
    }
}

@Composable
fun POSTab(viewModel: InstitutionViewModel) {
    val inventory by viewModel.inventory.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val inst by viewModel.selectedInstitution.collectAsState()
    
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf("SALE") } 

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = viewMode == "SALE",
                onClick = { viewMode = "SALE" },
                label = { Text("New Sale") },
                leadingIcon = { Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = viewMode == "HISTORY",
                onClick = { viewMode = "HISTORY" },
                label = { Text("Log") },
                leadingIcon = { Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp)) }
            )
        }

        if (viewMode == "SALE") {
            Row(modifier = Modifier.fillMaxSize()) {
                // Product Grid
                Column(modifier = Modifier.weight(1.2f).padding(8.dp)) {
                    OutlinedTextField(
                        value = "", onValueChange = {},
                        placeholder = { Text("Search items...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(inventory) { item ->
                            Card(
                                onClick = { viewModel.addToCart(item) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("KES ${item.price} / ${item.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    if (item.pointsValue > 0) {
                                        Text("+${item.pointsValue} Pts", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                    }
                                    Text("Stock: ${item.stockQuantity} ${item.unit}", 
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.stockQuantity < 5) MaterialTheme.colorScheme.error else Color.Unspecified
                                    )
                                }
                            }
                        }
                    }
                }

                // Cart Section
                Surface(
                    modifier = Modifier.weight(0.8f).fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Sale Terminal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(cart) { item ->
                                val invItem = inventory.find { it.id == item.productId }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.productName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                        Text("KES ${item.price * item.quantity}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { viewModel.updateCartQuantity(item.productId, -1.0) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.RemoveCircleOutline, null, modifier = Modifier.size(18.dp))
                                    }
                                    Text("${item.quantity} ${invItem?.unit ?: ""}", modifier = Modifier.padding(horizontal = 4.dp), fontSize = 12.sp)
                                    IconButton(onClick = { viewModel.updateCartQuantity(item.productId, 1.0) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        val subtotal = cart.sumOf { it.price * it.quantity }
                        val tax = subtotal * (inst?.settings?.taxPercentage ?: 0.0) / 100.0
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal", style = MaterialTheme.typography.labelMedium)
                                Text("KES $subtotal")
                            }
                            if (tax > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tax (${inst?.settings?.taxPercentage}%)", style = MaterialTheme.typography.labelMedium)
                                    Text("KES $tax")
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", fontWeight = FontWeight.Bold)
                                Text("KES ${subtotal + tax}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        Button(
                            onClick = { showCheckoutDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            enabled = cart.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Checkout")
                        }
                        TextButton(onClick = { viewModel.clearCart() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } else {
            // Transactions History
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(transactions) { tx ->
                    val date = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(tx.timestamp.toDate())
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        ListItem(
                            headlineContent = { Text("KES ${tx.totalAmount}") },
                            supportingContent = { 
                                Column {
                                    Text("$date | ${tx.paymentMethod}")
                                    Text("By: ${tx.cashierName}", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) },
                            overlineContent = { if (tx.customerName != null) Text("Cust: ${tx.customerName}") }
                        )
                    }
                }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutDialog(
            subtotal = cart.sumOf { it.price * it.quantity },
            taxRate = inst?.settings?.taxPercentage ?: 0.0,
            onDismiss = { showCheckoutDialog = false },
            onConfirm = { method, discount, custName ->
                viewModel.checkout(method, discount, custName)
                showCheckoutDialog = false
            }
        )
    }
}

@Composable
fun InventoryManagementTab(viewModel: InstitutionViewModel) {
    val inventory by viewModel.inventory.collectAsState()
    var showAddInventory by remember { mutableStateOf(false) }
    var selectedItemForRestock by remember { mutableStateOf<InventoryItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Stock Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddInventory = true }) {
                Icon(Icons.Default.Add, null)
                Text("New Item")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search inventory...") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )

        LazyColumn {
            items(inventory.filter { it.name.contains(searchQuery, ignoreCase = true) }) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(item.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { 
                            Column {
                                Text("Cost: KES ${item.costPrice} | Price: KES ${item.price}")
                                Text("In Stock: ${item.stockQuantity} ${item.unit}", 
                                    color = if (item.stockQuantity < item.reorderLevel) MaterialTheme.colorScheme.error else Color.Unspecified
                                )
                            }
                        },
                        trailingContent = {
                            Button(onClick = { selectedItemForRestock = item }) {
                                Text("Restock")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddInventory) {
        val context = LocalContext.current
        AddInventoryDialog(
            isUploading = false,
            onDismiss = { showAddInventory = false },
            onConfirm = { item, uri ->
                viewModel.addToInventory(context, item, uri)
                showAddInventory = false
            }
        )
    }

    if (selectedItemForRestock != null) {
        RestockDialog(
            item = selectedItemForRestock!!,
            onDismiss = { selectedItemForRestock = null },
            onConfirm = { qty, newCost ->
                viewModel.restockItem(selectedItemForRestock!!.id, qty, newCost)
                selectedItemForRestock = null
            }
        )
    }
}

@Composable
fun AnalysisTab(viewModel: InstitutionViewModel) {
    val salesAnalysis by viewModel.salesAnalysis.collectAsState()
    val productAnalysis by viewModel.productAnalysis.collectAsState()
    var analysisType by remember { mutableStateOf("SALES") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = analysisType == "SALES", onClick = { analysisType = "SALES" }, label = { Text("Daily Sales") })
            FilterChip(selected = analysisType == "PRODUCTS", onClick = { analysisType = "PRODUCTS" }, label = { Text("Product Performance") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (analysisType == "SALES") {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    val totalRev = salesAnalysis.sumOf { it.totalSales }
                    val totalProf = salesAnalysis.sumOf { it.totalProfit }
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Monthly Overview", fontWeight = FontWeight.Bold)
                            Text("Total Revenue: KES $totalRev", style = MaterialTheme.typography.titleLarge)
                            Text("Total Profit: KES $totalProf", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(salesAnalysis) { day ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day.date, fontWeight = FontWeight.Bold)
                                Text("${day.transactionCount} Sales", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Revenue", style = MaterialTheme.typography.labelSmall)
                                    Text("KES ${day.totalSales}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Est. Profit", style = MaterialTheme.typography.labelSmall)
                                    Text("KES ${day.totalProfit}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                if (salesAnalysis.isEmpty()) {
                    item { Text("No data yet.", modifier = Modifier.padding(24.dp), color = Color.Gray) }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(productAnalysis) { prod ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = { Text(prod.productName, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("${prod.unitsSold} units sold") },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("KES ${prod.revenue}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text("Profit: KES ${prod.profit}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                }
                            }
                        )
                    }
                }
                if (productAnalysis.isEmpty()) {
                    item { Text("No data yet.", modifier = Modifier.padding(24.dp), color = Color.Gray) }
                }
            }
        }
    }
}

@Composable
fun FinanceTab(viewModel: InstitutionViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    var showAddExpense by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Expense Tracking", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddExpense = true }) {
                Icon(Icons.Default.Add, null)
                Text("Record Expense")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(expenses) { expense ->
                val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(expense.timestamp.toDate())
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(expense.title) },
                        supportingContent = { Text("${expense.category} | $date") },
                        trailingContent = { Text("KES ${expense.amount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }

    if (showAddExpense) {
        AddExpenseDialog(
            onDismiss = { showAddExpense = false },
            onConfirm = { title, amount, cat ->
                viewModel.addExpense(title, amount, cat)
                showAddExpense = false
            }
        )
    }
}

@Composable
fun StaffTab(viewModel: InstitutionViewModel) {
    val staffList by viewModel.staff.collectAsState()
    val branches by viewModel.branches.collectAsState()
    val staffLogs by viewModel.staffLogs.collectAsState()
    val currentUser by viewModel.currentStaff.collectAsState()
    
    var showAddStaff by remember { mutableStateOf(false) }
    var selectedView by remember { mutableStateOf("LIST") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Staff Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (currentUser?.role == "Admin") {
                Button(onClick = { showAddStaff = true }) {
                    Icon(Icons.Default.PersonAdd, null)
                    Text("Add Staff")
                }
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            FilterChip(selected = selectedView == "LIST", onClick = { selectedView = "LIST" }, label = { Text("Staff List") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = selectedView == "LOGS", onClick = { selectedView = "LOGS" }, label = { Text("Audit Logs") })
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (selectedView == "LIST") {
            LazyColumn {
                items(staffList) { staff ->
                    val branchName = branches.find { it.id == staff.branchId }?.name ?: "All"
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        ListItem(
                            headlineContent = { Text(staff.name) },
                            supportingContent = { Text("${staff.role} | $branchName") },
                            leadingContent = { Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp)) },
                            trailingContent = {
                                if (currentUser?.role == "Admin") IconButton(onClick = { /* Delete/Edit Staff */ }) {
                                    Icon(Icons.Default.MoreVert, null)
                                }
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn {
                items(staffLogs) { log ->
                    val date = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(log.timestamp.toDate())
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                        ListItem(
                            headlineContent = { Text(log.action, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(log.details) },
                            overlineContent = { Text("${log.staffName} | $date") }
                        )
                    }
                }
            }
        }
    }

    if (showAddStaff) {
        AddStaffDialog(
            branches = branches,
            onDismiss = { showAddStaff = false },
            onConfirm = { name, email, role, branchId ->
                viewModel.addStaff(name, email, role, branchId)
                showAddStaff = false
            }
        )
    }
}

@Composable
fun InstitutionSettingsTab(institution: Institution?, viewModel: InstitutionViewModel) {
    val branches by viewModel.branches.collectAsState()
    var showAddBranch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Management Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Institution: ${institution?.name}", fontWeight = FontWeight.Bold)
                Text("Base Location: ${institution?.location}")
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Branches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showAddBranch = true }) {
                Icon(Icons.Default.Add, null)
                Text("Add Branch")
            }
        }
        
        branches.forEach { branch ->
            ListItem(
                headlineContent = { Text(branch.name) },
                supportingContent = { Text(branch.location) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Restaurant Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        var tax by remember(institution) { mutableStateOf(institution?.settings?.taxPercentage?.toString() ?: "0.0") }
        var printerIp by remember(institution) { mutableStateOf(institution?.settings?.printerIpAddress ?: "") }

        OutlinedTextField(
            value = tax,
            onValueChange = { tax = it },
            label = { Text("Tax Percentage (%)") },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text("% ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        
        OutlinedTextField(
            value = printerIp,
            onValueChange = { printerIp = it },
            label = { Text("Kitchen Printer IP Address") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("192.168.1.100") }
        )
        
        Button(
            onClick = { 
                institution?.settings?.copy(taxPercentage = tax.toDoubleOrNull() ?: 0.0, printerIpAddress = printerIp)?.let {
                    viewModel.updateInstitutionSettings(it)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Save Settings")
        }
    }

    if (showAddBranch) {
        AddBranchDialog(onDismiss = { showAddBranch = false }, onConfirm = { n, l, p -> viewModel.addBranch(n, l, p); showAddBranch = false })
    }
}

@Composable
fun CheckoutDialog(subtotal: Double, taxRate: Double, onDismiss: () -> Unit, onConfirm: (String, Double, String?) -> Unit) {
    var selectedMethod by remember { mutableStateOf("Cash") }
    var discount by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    
    val tax = subtotal * (taxRate / 100.0)
    val total = subtotal + tax - (discount.toDoubleOrNull() ?: 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Due:", fontWeight = FontWeight.Bold)
                    Text("KES $total", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                }
                
                OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("Customer Name (For Loyalty)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Discount Amount (KES)") }, modifier = Modifier.fillMaxWidth())
                
                Text("Payment Method", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cash", "M-Pesa", "Card").forEach { method ->
                        FilterChip(selected = selectedMethod == method, onClick = { selectedMethod = method }, label = { Text(method) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMethod, discount.toDoubleOrNull() ?: 0.0, customerName.ifBlank { null }) }) { Text("Pay KES $total") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddInventoryDialog(onDismiss: () -> Unit, onConfirm: (InventoryItem, Uri?) -> Unit, isUploading: Boolean) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("pcs") }
    var points by remember { mutableStateOf("0") }
    
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it }
    
    val units = listOf("pcs", "kg", "g", "ml", "ltr", "box", "strip", "vial")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                
                Box(
                    modifier = Modifier.size(100.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray)
                            Text("Add Photo", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name*") }, modifier = Modifier.fillMaxWidth())
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Brand") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU/Barcode") }, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("Cost Price") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Sale Price*") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Initial Stock") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(0.6f)) {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(unit)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            units.forEach { u ->
                                DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; expanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onConfirm(InventoryItem(
                        name = name, 
                        brand = brand,
                        sku = sku,
                        price = price.toDoubleOrNull() ?: 0.0, 
                        costPrice = costPrice.toDoubleOrNull() ?: 0.0,
                        stockQuantity = qty.toDoubleOrNull() ?: 0.0,
                        unit = unit,
                        pointsValue = points.toIntOrNull() ?: 0
                    ), imageUri)
                },
                enabled = name.isNotEmpty() && price.isNotEmpty() && !isUploading
            ) { 
                Text(if(isUploading) "Saving..." else "Save Product") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) { Text("Cancel") }
        }
    )
}

@Composable
fun RestockDialog(item: InventoryItem, onDismiss: () -> Unit, onConfirm: (Double, Double?) -> Unit) {
    var qty by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("${item.costPrice}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restock: ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current Stock: ${item.stockQuantity} ${item.unit}")
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Additional Quantity (${item.unit})") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("New Cost Price (Optional)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(qty.toDoubleOrNull() ?: 0.0, costPrice.toDoubleOrNull()) }) { Text("Update Stock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (KES)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                
                val categories = listOf("Stock", "Rent", "Salaries", "Utilities", "Other")
                Text("Category")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat, fontSize = 10.sp) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, amount.toDoubleOrNull() ?: 0.0, category) }) { Text("Record") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddStaffDialog(branches: List<Branch>, onDismiss: () -> Unit, onConfirm: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Cashier") }
    var branchId by remember { mutableStateOf(branches.firstOrNull()?.id ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Staff Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                
                Text("Role")
                val roles = listOf("Cashier", "Manager", "Chef", "Waiter", "Admin")
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    roles.forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r, fontSize = 10.sp) })
                    }
                }

                if (branches.isNotEmpty()) {
                    Text("Assign to Branch")
                    branches.forEach { b ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { branchId = b.id }) {
                            RadioButton(selected = branchId == b.id, onClick = { branchId = b.id })
                            Text(b.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, email, role, branchId) }) { Text("Add Staff") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddBranchDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Branch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Branch Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Physical Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, location, phone) }) { Text("Create Branch") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterInstitutionDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, InstitutionSettings) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Retail Shop") }
    var location by remember { mutableStateOf("") }
    
    var hasPOS by remember { mutableStateOf(true) }
    var hasRestaurant by remember { mutableStateOf(false) }
    var hasRental by remember { mutableStateOf(false) }
    var hasBooking by remember { mutableStateOf(false) }
    var hasVisitor by remember { mutableStateOf(false) }
    var hasQueue by remember { mutableStateOf(false) }

    val categories = listOf("Restaurant", "Pharmacy/Chemist", "Hospital", "Bank", "Retail Shop", "Office", "School", "Government", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Institution") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth())
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expanded = false })
                        }
                    }
                }
                
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Primary Location") }, modifier = Modifier.fillMaxWidth())
                
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Select Features to Enable", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                
                FeatureSelectionRow("Point of Sale (Retail/General)", hasPOS) { hasPOS = it }
                FeatureSelectionRow("Restaurant (Tables, KDS, Menu)", hasRestaurant) { hasRestaurant = it }
                FeatureSelectionRow("Rental Management (Cars, Rooms, Tools)", hasRental) { hasRental = it }
                FeatureSelectionRow("Service Booking (Appointments)", hasBooking) { hasBooking = it }
                FeatureSelectionRow("Visitor Management (Security/Front Office)", hasVisitor) { hasVisitor = it }
                FeatureSelectionRow("Queue Management System", hasQueue) { hasQueue = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val settings = InstitutionSettings(
                        hasPOS = hasPOS,
                        hasRestaurant = hasRestaurant,
                        hasRentalManagement = hasRental,
                        hasServiceBooking = hasBooking,
                        hasVisitorManagement = hasVisitor,
                        hasQueueSystem = hasQueue
                    )
                    onConfirm(name, category, location, settings) 
                }, 
                enabled = name.isNotBlank() && location.isNotBlank()
            ) { Text("Register Business") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FeatureSelectionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
