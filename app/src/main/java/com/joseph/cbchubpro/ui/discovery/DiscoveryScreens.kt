package com.joseph.cbchubpro.ui.discovery

import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joseph.cbchubpro.ui.management.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: DiscoveryViewModel = viewModel(),
    cartViewModel: CartViewModel,
    onBackClicked: () -> Unit,
    onVendorClicked: (String) -> Unit,
    onCartClicked: () -> Unit
) {
    val retailVendors by viewModel.retailItems.collectAsState()
    val groupVendors by viewModel.groupDealItems.collectAsState()
    val wholesaleVendors by viewModel.wholesaleItems.collectAsState()
    val serviceVendors by viewModel.serviceItems.collectAsState()
    val professionalVendors by viewModel.professionalItems.collectAsState()
    val activeGroupDeals by viewModel.activeGroupDeals.collectAsState()
    
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Retail", "Group Deals", "Wholesale", "Services", "Professionals")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("CBC Hub Marketplace", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onCartClicked) {
                            val cartCount by cartViewModel.cartItems.collectAsState()
                            BadgedBox(badge = { if(cartCount.isNotEmpty()) Badge { Text(cartCount.size.toString()) } }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                            }
                        }
                    }
                )
                
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search ${tabs[selectedTab].lowercase(Locale.getDefault())}...") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val currentVendors = when(selectedTab) {
                0 -> retailVendors
                1 -> groupVendors
                2 -> wholesaleVendors
                3 -> serviceVendors
                4 -> professionalVendors
                else -> emptyList()
            }
            val liveDealProductIds = activeGroupDeals.map { it.productId }.toSet()
            val visibleVendors = if (selectedTab == 1) currentVendors.mapNotNull { vendor ->
                vendor.copy(products = vendor.products.filter { it.id in liveDealProductIds }).takeIf { it.products.isNotEmpty() }
            } else currentVendors

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (visibleVendors.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No items found in this category", color = Color.Gray)
                        }
                    }
                }
                items(visibleVendors) { entry ->
                    VendorSection(entry, onVendorClicked, isSwapHub = false, activeGroupDeals = activeGroupDeals, cartViewModel = cartViewModel, discoveryViewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDiscoveryScreen(
    viewModel: DiscoveryViewModel = viewModel(),
    onBackClicked: () -> Unit
) {
    val properties by viewModel.properties.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    var selectedType by remember { mutableStateOf("ALL") }
    var inquiryProperty by remember { mutableStateOf<PropertyListing?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearStatus() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Real Estate \u0026 Listings", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by location or type...") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                ScrollableTabRow(selectedTabIndex = listOf("ALL", "SALE", "RENT", "LEASE").indexOf(selectedType), edgePadding = 16.dp) {
                    listOf("ALL", "SALE", "RENT", "LEASE").forEach { type ->
                        Tab(selected = selectedType == type, onClick = { selectedType = type }, text = { Text(if (type == "ALL") "All listings" else type.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val filtered = properties.filter { 
                (it.propertyCategory != "Hotel Room" && it.propertyCategory != "Room") &&
                (selectedType == "ALL" || it.type.equals(selectedType, ignoreCase = true)) &&
                (searchQuery.isEmpty() || it.title.contains(searchQuery, true) || it.location.contains(searchQuery, true))
            }
            if (filtered.isEmpty()) {
                item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No properties found", color = Color.Gray) } }
            }
            items(filtered) { property ->
                PropertyCard(property) { inquiryProperty = property }
            }
        }
    }
    inquiryProperty?.let { property ->
        PropertyInquiryDialog(property, onDismiss = { inquiryProperty = null }) { contact, message ->
            viewModel.sendPropertyInquiry(property, contact, message)
            inquiryProperty = null
        }
    }
}

@Composable
fun PropertyCard(property: PropertyListing, onContact: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(modifier = Modifier.height(200.dp).fillMaxWidth()) {
                if (property.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = property.imageUrls.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.HomeWork, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    }
                }
                Surface(
                    modifier = Modifier.padding(12.dp).align(Alignment.TopEnd),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "KES ${property.price}${if(property.type == "RENT" || property.type == "LEASE") "/mo" else ""}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(property.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                SuggestionChip(onClick = {}, label = { Text(property.type.lowercase().replaceFirstChar { it.uppercase() }) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Text(property.location, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PropertyStat(Icons.Default.Bed, "${property.bedrooms} Beds")
                    PropertyStat(Icons.Default.Bathtub, "${property.bathrooms} Baths")
                    PropertyStat(Icons.Default.SquareFoot, property.size)
                }
            }
        }
    }

    if (showDialog) {
        PropertyDetailDialog(property, onDismiss = { showDialog = false }, onContact = { showDialog = false; onContact() })
    }
}

@Composable
fun PropertyStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDetailDialog(property: PropertyListing, onDismiss: () -> Unit, onContact: () -> Unit = {}) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                    if (property.imageUrls.isNotEmpty()) {
                        AsyncImage(model = property.imageUrls.first(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.padding(8.dp).background(Color.Black.copy(0.3f), CircleShape)) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(property.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("KES ${property.price}${if (property.type == "RENT" || property.type == "LEASE") "/ month" else ""}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    Text("${property.type} · ${property.propertyCategory} · ${property.location}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    if (property.deposit > 0) Text("Deposit: KES ${property.deposit}", style = MaterialTheme.typography.bodyMedium)
                    if (property.address.isNotBlank()) Text("Address: ${property.address}", style = MaterialTheme.typography.bodyMedium)
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Amenities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FlowRow(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        property.amenities.forEach { amenity ->
                            SuggestionChip(onClick = {}, label = { Text(amenity) })
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(property.description)
                    
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onContact, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (property.type == "SALE") "Enquire about this property" else "Request a viewing")
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyInquiryDialog(property: PropertyListing, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var contact by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.phoneNumber.orEmpty()) }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (property.type == "SALE") "Enquire about property" else "Request a viewing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your enquiry will go to ${property.agentName.ifBlank { "the listing agent" }}.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(contact, { contact = it }, label = { Text("Your phone number") }, singleLine = true)
                OutlinedTextField(message, { message = it }, label = { Text("Message (optional)") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(contact.trim(), message.trim()) }, enabled = contact.isNotBlank()) { Text("Send enquiry") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HospitalityDiscoveryScreen(
    viewModel: DiscoveryViewModel = viewModel(),
    onBackClicked: () -> Unit
) {
    val items by viewModel.restaurantItemsData.collectAsState()
    val properties by viewModel.properties.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Hospitality \u0026 Stay", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Food Menu") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Room Booking") })
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(if(selectedTab == 0) "Search for dishes..." else "Search for rooms...") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(if(selectedTab == 0) Icons.Default.Restaurant else Icons.Default.Bed, null) }
                )
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val filtered = items.filter { searchQuery.isEmpty() || it.name.contains(searchQuery, true) }
                if (filtered.isEmpty()) {
                    item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No dishes found", color = Color.Gray) } }
                }
                items(filtered) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(90.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.size(90.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Restaurant, null, tint = Color.Gray)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (item.description.isNotEmpty()) {
                                    Text(item.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("KES ${item.price}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                            }
                            Button(onClick = { /* Order Now */ }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text("Order", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val rooms = properties.filter { 
                    (it.propertyCategory == "Hotel Room" || it.propertyCategory == "Room" || it.type == "STAY") &&
                    (searchQuery.isEmpty() || it.title.contains(searchQuery, true) || it.location.contains(searchQuery, true))
                }
                if (rooms.isEmpty()) {
                    item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("No rooms available for booking", color = Color.Gray) } }
                }
                items(rooms) { room ->
                    RoomCard(room)
                }
            }
        }
    }
}

@Composable
fun RoomCard(room: PropertyListing) {
    var showDetail by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDetail = true },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                if (room.imageUrls.isNotEmpty()) {
                    AsyncImage(model = room.imageUrls.first(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Surface(
                    modifier = Modifier.padding(12.dp).align(Alignment.BottomEnd),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("KES ${room.price} / Night", modifier = Modifier.padding(6.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(room.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                
                Spacer(Modifier.height(8.dp))
                Text("Amenities:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    room.amenities.take(4).forEach { amenity ->
                        Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(amenity, fontSize = 9.sp)
                        }
                    }
                    if (room.amenities.size > 4) {
                        Badge { Text("+${room.amenities.size - 4}", fontSize = 9.sp) }
                    }
                }
            }
        }
    }

    if (showDetail) {
        PropertyDetailDialog(room, onDismiss = { showDetail = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapHubScreen(
    viewModel: DiscoveryViewModel = viewModel(),
    onBackClicked: () -> Unit,
    onParentClicked: (String) -> Unit
) {
    val items by viewModel.swapHubItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val locationQuery by viewModel.locationQuery.collectAsState()

    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Parent Swap Hub", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList, contentDescription = "Filters")
                        }
                    }
                )
                
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search items to swap (e.g. uniform)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    
                    AnimatedVisibility(visible = showFilters) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = locationQuery,
                                onValueChange = { viewModel.setLocationQuery(it) },
                                placeholder = { Text("Filter by location...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (items.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No items for swap found", color = Color.Gray)
                        }
                    }
                }
                items(items) { entry ->
                    VendorSection(entry, onParentClicked, isSwapHub = true, activeGroupDeals = emptyList(), cartViewModel = null, discoveryViewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun VendorSection(vendor: VendorWithProducts, onVendorClicked: (String) -> Unit, isSwapHub: Boolean, activeGroupDeals: List<GroupDeal>, cartViewModel: CartViewModel?, discoveryViewModel: DiscoveryViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (isSwapHub) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(2.dp)
                    .clip(CircleShape)
            ) {
                if (vendor.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = vendor.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (isSwapHub) Icons.Default.Person else Icons.Default.Store, 
                        contentDescription = null, 
                        tint = if (isSwapHub) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        vendor.vendorName, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (vendor.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.wrapContentWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                        Text("${vendor.rating} (${vendor.transactionHistoryCount})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    if (vendor.physicalLocationVerified) {
                        Badge(containerColor = Color(0xFF4CAF50)) { Text("LOC VERIFIED", fontSize = 7.sp, color = Color.White) }
                    }
                    if (vendor.phoneVerified) {
                        Icon(Icons.Default.PhonelinkRing, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                    }
                }
            }
            TextButton(onClick = { onVendorClicked(vendor.vendorId) }) {
                Text(if (isSwapHub) "View" else "Visit")
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(vendor.products) { product ->
                ProductCard(product, isSwapHub, activeGroupDeals, cartViewModel, discoveryViewModel)
            }
        }
    }
}

@Composable
fun ProductCard(product: ProductItem, isSwapHub: Boolean, activeGroupDeals: List<GroupDeal>, cartViewModel: CartViewModel?, discoveryViewModel: DiscoveryViewModel) {
    var showDetailDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(180.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp))
            .clickable { showDetailDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(modifier = Modifier.height(130.dp).fillMaxWidth()) {
                if (product.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = product.imageUrls[0],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                    }
                }
                
                // Badges overlay
                Column(modifier = Modifier.padding(8.dp).align(Alignment.TopEnd), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                    if (product.promoLabel.isNotEmpty()) {
                        Badge(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = Color.White) {
                            Text(product.promoLabel.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    if (!isSwapHub && product.condition.isNotBlank()) {
                        Badge(containerColor = if (product.condition.equals("New", ignoreCase = true)) Color(0xFFFF9800) else MaterialTheme.colorScheme.tertiary) {
                            Text(product.condition.uppercase(), fontSize = 8.sp, color = Color.White)
                        }
                    }
                    if (product.adStatus == AdStatus.ACTIVE && product.adLevel != AdLevel.FREE && (product.adExpiresAt == null || product.adExpiresAt.toDate().time > System.currentTimeMillis())) {
                        Badge(containerColor = Color(0xFFFFC107)) { Text("PROMOTED", fontSize = 8.sp, color = Color.Black) }
                    }
                    if (product.verificationStatus == VerificationStatus.VERIFIED) {
                        Badge(containerColor = Color(0xFF2196F3)) { Text("VERIFIED", fontSize = 8.sp, color = Color.White) }
                    }
                    if (product.agentInspected) {
                        Badge(containerColor = Color(0xFF9C27B0)) { Text("AGENT INSPECTED", fontSize = 8.sp, color = Color.White) }
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    product.name, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                    Text(" ${product.rating} (${product.reviewCount})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                
                Spacer(Modifier.height(4.dp))

                if (isSwapHub) {
                    Text("Cond: ${product.condition}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("Val: KES ${product.price}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                } else {
                    when(product.listingType) {
                        ListingType.RETAIL -> {
                            Text("KES ${product.price}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        ListingType.GROUP_DEAL -> {
                            val deal = activeGroupDeals.find { it.productId == product.id }
                            Column {
                                Text("KES ${product.groupPrice}", style = MaterialTheme.typography.titleSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                if (deal != null) {
                                            val progress = if (deal.minRequired > 0) deal.currentJoined.toFloat() / deal.minRequired.toFloat() else 0f
                                    val remaining = (deal.minRequired - deal.currentJoined).coerceAtLeast(0)
                                    
                                    LinearProgressIndicator(
                                        progress = { progress.coerceAtMost(1f) },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                        color = Color(0xFF4CAF50)
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${deal.currentJoined} joined", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color(0xFF4CAF50))
                                        Text("$remaining more to close", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                        ListingType.WHOLESALE -> {
                            Text("KES ${product.wholesalePrice}", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                            Text("Min: ${product.minWholesaleQuantity}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        ListingType.SERVICE, ListingType.PROFESSIONAL -> {
                            Text("KES ${product.price}", style = MaterialTheme.typography.titleSmall, color = Color(0xFF673AB7), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                if (product.stockQuantity != -1) {
                    Text(
                        text = if (product.stockQuantity > 0) "In Stock: ${product.stockQuantity}" else "Out of Stock",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (product.stockQuantity > 0) Color(0xFF4CAF50) else Color.Red
                    )
                }
            }
        }
    }

    if (showDetailDialog) {
        ProductDetailDialog(product, isSwapHub, cartViewModel, discoveryViewModel, activeGroupDeals, onDismiss = { showDetailDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProductDetailDialog(product: ProductItem, isSwapHub: Boolean, cartViewModel: CartViewModel?, discoveryViewModel: DiscoveryViewModel, activeGroupDeals: List<GroupDeal>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var sellerPhone by remember(product.vendorId) { mutableStateOf("") }
    var sellerName by remember(product.vendorId) { mutableStateOf("") }
    val reviews by discoveryViewModel.reviews.collectAsState()
    val productReviews = reviews[product.id] ?: emptyList()

    LaunchedEffect(product.id) {
        discoveryViewModel.fetchReviews(product.id)
    }
    LaunchedEffect(product.vendorId) {
        if (product.vendorId.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(product.vendorId).get()
                .addOnSuccessListener {
                    sellerPhone = it.getString("contact") ?: it.getString("phone") ?: ""
                    sellerName = it.getString("name") ?: ""
                }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    // Image Gallery
                    if (product.imageUrls.isNotEmpty()) {
                        val pagerState = rememberPagerState(pageCount = { product.imageUrls.size })
                        Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                AsyncImage(
                                    model = product.imageUrls[page],
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (product.promoLabel.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text(product.promoLabel.uppercase()) }
                            if (product.condition.isNotBlank()) Badge(containerColor = if (product.condition.equals("New", true)) Color(0xFFFF9800) else MaterialTheme.colorScheme.tertiary) { Text(product.condition.uppercase()) }
                            if (product.adStatus == AdStatus.ACTIVE && product.adLevel != AdLevel.FREE && (product.adExpiresAt == null || product.adExpiresAt.toDate().time > System.currentTimeMillis())) Badge(containerColor = Color(0xFFFFC107)) { Text("PROMOTED", color = Color.Black) }
                            if (product.verificationStatus == VerificationStatus.VERIFIED) Badge(containerColor = Color(0xFF2196F3)) { Text("VERIFIED BY CBC HUB") }
                            if (product.agentInspected) Badge(containerColor = Color(0xFF9C27B0)) { Text("AGENT INSPECTED") }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(product.description, style = MaterialTheme.typography.bodyLarge)
                        
                        if (product.colors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Available Options/Variations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                items(product.colors) { color ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text(color, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSwapHub) {
                            InfoRow(Icons.Default.Info, "Condition", product.condition)
                            InfoRow(Icons.Default.PriceCheck, "Estimated Value", "KES ${product.price}")
                            InfoRow(Icons.Default.SwapHoriz, "Swap Preference", product.swapPreference)
                        } else {
                            InfoRow(Icons.Default.Store, "Seller", sellerName.ifBlank { "Marketplace seller" })
                            if (product.condition.isNotBlank()) InfoRow(Icons.Default.Inventory2, "Condition", product.condition)
                            val priceText = when(product.listingType) {
                                ListingType.RETAIL -> "Retail Price: KES ${product.price}"
                                ListingType.GROUP_DEAL -> "Group Price: KES ${product.groupPrice}"
                                ListingType.WHOLESALE -> "Wholesale Price: KES ${product.wholesalePrice} (Min ${product.minWholesaleQuantity} items)"
                                ListingType.SERVICE -> "Service Fee: KES ${product.price}"
                                ListingType.PROFESSIONAL -> "Professional Fee: KES ${product.price}"
                            }
                            Text(priceText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            
                            if (product.listingType == ListingType.GROUP_DEAL) {
                                val deal = activeGroupDeals.find { it.productId == product.id }
                                if (deal != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("Group Buying Confidence", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                            Text("Real-time Joined: ${deal.currentJoined}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Remaining to hit target: ${deal.minRequired - deal.currentJoined}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            
                                            LinearProgressIndicator(
                                                progress = { (deal.currentJoined.toFloat() / deal.minRequired.toFloat()).coerceAtMost(1f) },
                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp).clip(CircleShape),
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Trust \u0026 Safety", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Gain confidence by hiring a certified agent to inspect this product/shop before you buy.", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = { 
                                            discoveryViewModel.hireAgent(product.id, product.vendorId)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Icon(Icons.Default.SupportAgent, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Hire Agent to Inspect (KES 50)")
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Buyer Ratings \u0026 Reviews", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        if (productReviews.isEmpty()) {
                            Text("No reviews yet. Be the first to share your experience!", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            productReviews.forEach { review ->
                                ReviewCard(review)
                            }
                        }
                    }
                }
                
                // Action Buttons
                Surface(
                    modifier = Modifier.fillMaxWidth().shadow(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (!isSwapHub && sellerPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${sellerPhone.trim()}"))) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Call, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Call seller")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (product.listingType == ListingType.GROUP_DEAL) {
                            val deal = activeGroupDeals.find { it.productId == product.id }
                            if (deal != null) {
                                val userHasJoined = deal.buyerDetails.containsKey(currentUserId)
                                
                                if (!userHasJoined && deal.status == DealStatus.ACTIVE) {
                                    Button(
                                        onClick = { discoveryViewModel.joinGroupDeal(deal.id) },
                                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                    ) {
                                        Icon(Icons.Default.GroupAdd, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Join Group Deal")
                                    }
                                } else if (userHasJoined) {
                                    Button(
                                        onClick = { 
                                            if (cartViewModel != null) {
                                                cartViewModel.addToCart(product, PurchaseType.GROUP, dealId = deal.id)
                                                Toast.makeText(context, "${product.name} added to cart with group discount", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ShoppingCart, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add to Cart (Group Price)")
                                    }
                                } else {
                                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                        Text("Group target reached · joining closed")
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = { 
                                    if (cartViewModel != null) {
                                        val pType = when(product.listingType) {
                                            ListingType.RETAIL -> PurchaseType.RETAIL
                                            ListingType.WHOLESALE -> PurchaseType.WHOLESALE
                                            ListingType.SERVICE -> PurchaseType.SERVICE
                                            ListingType.PROFESSIONAL -> PurchaseType.PROFESSIONAL
                                            else -> PurchaseType.RETAIL
                                        }
                                        cartViewModel.addToCart(product, pType)
                                        Toast.makeText(context, "${product.name} added to cart", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                enabled = cartViewModel != null
                            ) {
                                Text(if (isSwapHub) "Request Swap" else "Secure Order")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalDiscoveryScreen(
    viewModel: DiscoveryViewModel = viewModel(),
    onBackClicked: () -> Unit,
    onProfessionalClicked: (String) -> Unit,
    onViewJobsClicked: () -> Unit
) {
    val professionals by viewModel.professionals.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val locationQuery by viewModel.locationQuery.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Professionals \u0026 Services", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(if (showFilters) Icons.Default.FilterListOff else Icons.Default.FilterList, contentDescription = "Filters")
                        }
                        IconButton(onClick = onViewJobsClicked) {
                            Icon(Icons.Default.Work, contentDescription = "Job Board")
                        }
                    }
                )
                
                AnimatedVisibility(visible = showFilters) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search by name or role...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = locationQuery,
                            onValueChange = { viewModel.setLocationQuery(it) },
                            placeholder = { Text("Filter by location...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.LocationOn, null) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (professionals.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No professionals found", color = Color.Gray)
                        }
                    }
                }
                items(professionals) { prof ->
                    ProfessionalCard(prof, onProfessionalClicked)
                }
            }
        }
    }
}

@Composable
fun ProfessionalCard(prof: ProfessionalProfile, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
            .clickable { onClick(prof.uid) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = prof.profileImageUrl,
                contentDescription = null,
                modifier = Modifier.size(85.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentScale = ContentScale.Crop
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prof.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (prof.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, "Verified", tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        prof.role.uppercase(Locale.getDefault()), 
                        style = MaterialTheme.typography.labelSmall, 
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                    Text(" ${prof.averageRating} (${prof.reviewCount})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            
            IconButton(onClick = { onClick(prof.uid) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    viewModel: JobViewModel = viewModel(),
    onBackClicked: () -> Unit
) {
    val jobs by viewModel.jobs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val locationQuery by viewModel.locationQuery.collectAsState()
    val savedJobIds by viewModel.savedJobIds.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    var showPostDialog by remember { mutableStateOf(false) }
    var editingJob by remember { mutableStateOf<JobPost?>(null) }
    var showPayDialog by remember { mutableStateOf<JobPost?>(null) }
    var showBoostDialog by remember { mutableStateOf<JobPost?>(null) }
    var applyingToJob by remember { mutableStateOf<JobPost?>(null) }
    var viewingApplicationsFor by remember { mutableStateOf<JobPost?>(null) }
    var employmentFilter by remember { mutableStateOf("ALL") }
    var workplaceFilter by remember { mutableStateOf("ALL") }
    var showSavedOnly by remember { mutableStateOf(false) }
    val applications by viewModel.applications.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text("Job Board", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search by title or school...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = locationQuery,
                        onValueChange = { viewModel.setLocationQuery(it) },
                        placeholder = { Text("Filter by location...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = showSavedOnly, onClick = { showSavedOnly = !showSavedOnly }, label = { Text("Saved jobs") }, leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.size(16.dp)) })
                        listOf("ALL", "FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP").forEach { type ->
                            FilterChip(selected = employmentFilter == type, onClick = { employmentFilter = type }, label = { Text(if (type == "ALL") "Any type" else type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL", "ON_SITE", "HYBRID", "REMOTE").forEach { mode ->
                            FilterChip(selected = workplaceFilter == mode, onClick = { workplaceFilter = mode }, label = { Text(if (mode == "ALL") "Any workplace" else mode.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPostDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Post a Job") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        if (isLoading && jobs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val visibleJobs = jobs.filter { job ->
                    (!showSavedOnly || savedJobIds.contains(job.id)) &&
                        (employmentFilter == "ALL" || job.employmentType == employmentFilter) &&
                        (workplaceFilter == "ALL" || job.workplaceType == workplaceFilter)
                }
                if (visibleJobs.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (showSavedOnly) "No saved jobs match these filters." else "No jobs match these filters.", color = Color.Gray)
                        }
                    }
                }
                items(visibleJobs, key = { it.id }) { job ->
                    JobCard(
                        job = job, 
                        isOwner = job.posterId == currentUser?.uid,
                        onEdit = { editingJob = job },
                        onDelete = { viewModel.deleteJob(job.id) },
                        onVerify = { showPayDialog = job },
                        onBoost = { showBoostDialog = job },
                        onApply = { applyingToJob = job },
                        onViewApplications = { viewingApplicationsFor = job; viewModel.loadApplications(job.id) },
                        isSaved = savedJobIds.contains(job.id),
                        onSave = { viewModel.toggleSavedJob(job) }
                    )
                }
            }
        }
    }

    if (showPostDialog || editingJob != null) {
        JobFormDialog(
            job = editingJob,
            onDismiss = { 
                showPostDialog = false
                editingJob = null
            },
            onConfirm = { title, company, location, desc, salary, reqs, phone, web, deadline, logoUrl, employmentType, workplaceType, experienceLevel, applicationUrl ->
                if (editingJob == null) {
                    viewModel.postJob(title, company, location, desc, salary, reqs, phone, web, deadline, logoUrl, employmentType, workplaceType, experienceLevel, applicationUrl)
                } else {
                    viewModel.updateJob(editingJob!!.id, title, company, location, desc, salary, reqs, phone, web, deadline, logoUrl, employmentType, workplaceType, experienceLevel, applicationUrl)
                }
                showPostDialog = false
                editingJob = null
            }
        )
    }

    if (showPayDialog != null) {
        AlertDialog(
            onDismissRequest = { showPayDialog = null },
            title = { Text("Verify Job Listing") },
            text = { Text("Pay KES 100 to verify this job. A verified badge will appear after admin review.") } ,
            confirmButton = {
                Button(onClick = {
                    viewModel.initiateJobVerification(showPayDialog!!.id, 100.0)
                    showPayDialog = null
                }) { Text("Pay via M-Pesa") }
            },
            dismissButton = { TextButton(onClick = { showPayDialog = null }) { Text("Cancel") } }
        )
    }

    if (showBoostDialog != null) {
        BoostSelectionDialog(
            onDismiss = { showBoostDialog = null },
            onConfirm = { level, amount ->
                viewModel.initiateJobBoost(showBoostDialog!!.id, level, amount)
                showBoostDialog = null
            }
        )
    }
    applyingToJob?.let { job ->
        JobApplicationDialog(job, onDismiss = { applyingToJob = null }) { message ->
            viewModel.applyToJob(job, message)
            applyingToJob = null
        }
    }
    viewingApplicationsFor?.let { job ->
        AlertDialog(
            onDismissRequest = { viewingApplicationsFor = null },
            title = { Text("Applications · ${job.title}") },
            text = {
                if (applications.isEmpty()) Text("No applications yet.") else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    applications.forEach { application ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(application.applicantName, fontWeight = FontWeight.Bold)
                                Text(application.applicantEmail, style = MaterialTheme.typography.bodySmall)
                                if (application.message.isNotBlank()) Text(application.message, style = MaterialTheme.typography.bodyMedium)
                                Text(application.createdAt?.toDate()?.toString()?.take(16) ?: "Recently", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewingApplicationsFor = null }) { Text("Done") } }
        )
    }
}

@Composable
fun BoostSelectionDialog(onDismiss: () -> Unit, onConfirm: (BoostLevel, Double) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Boost Your Listing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a level to appear first in the list and get more applicants.", style = MaterialTheme.typography.bodySmall)
                
                BoostOptionCard("Top Priority", "Appears at the very top", "KES 500") { onConfirm(BoostLevel.TOP_PRIORITY, 500.0) }
                BoostOptionCard("Featured", "Highlighted with a border", "KES 250") { onConfirm(BoostLevel.FEATURED, 250.0) }
                BoostOptionCard("Highlighted", "Subtle visibility boost", "KES 100") { onConfirm(BoostLevel.HIGHLIGHTED, 100.0) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BoostOptionCard(title: String, desc: String, price: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(price, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun JobCard(job: JobPost, isOwner: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onVerify: () -> Unit, onBoost: () -> Unit, onApply: () -> Unit, onViewApplications: () -> Unit, isSaved: Boolean, onSave: () -> Unit) {
    val borderColor = when(job.boostLevel) {
        BoostLevel.TOP_PRIORITY -> Color(0xFFFFB300)
        BoostLevel.FEATURED -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(if(borderColor != Color.Transparent) Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if(job.boostLevel != BoostLevel.NONE) 8.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (job.boostLevel != BoostLevel.NONE) {
                Badge(containerColor = borderColor) { Text(job.boostLevel.name.replace("_", " "), color = Color.White, fontSize = 10.sp) }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (job.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = job.imageUrl,
                        contentDescription = "Logo",
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(job.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (job.isVerified) {
                            Icon(Icons.Default.Verified, "Verified", tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp).padding(start = 4.dp))
                        }
                    }
                    Text(job.company, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                    Text(job.salaryRange, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Text(job.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (job.deadline.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Until: ${job.deadline}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    job.employmentType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    job.workplaceType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                    job.experienceLevel.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                ).forEach { tag ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(tag, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(job.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Posted by: ${job.posterName}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isOwner) {
                        TextButton(onClick = onViewApplications) { Text("Applications") }
                        if (!job.isVerified && !job.verificationPending) {
                            TextButton(onClick = onVerify) { Text("Verify (100/-)", fontSize = 12.sp) }
                        }
                        if (job.boostLevel == BoostLevel.NONE) {
                            TextButton(onClick = onBoost) { Text("Boost", fontSize = 12.sp) }
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Job", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Job", tint = Color.Red, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = onSave) {
                            Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = if (isSaved) "Unsave job" else "Save job", tint = MaterialTheme.colorScheme.primary)
                        }
                        Button(onClick = onApply, shape = RoundedCornerShape(8.dp)) {
                            Text(if (job.applicationUrl.isNotBlank()) "Apply" else "Apply now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobApplicationDialog(job: JobPost, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply to ${job.company}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your name and email from your account will be shared with the employer.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(message, { message = it }, label = { Text("Short introduction (optional)") }, minLines = 3)
                if (job.applicationUrl.isNotBlank()) TextButton(onClick = {
                    val url = if (job.applicationUrl.startsWith("http://") || job.applicationUrl.startsWith("https://")) job.applicationUrl else "https://${job.applicationUrl}"
                    uriHandler.openUri(url)
                }) { Text("Open employer application site") }
            }
        },
        confirmButton = { Button(onClick = { onSubmit(message.trim()) }) { Text("Send application") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun JobFormDialog(
    job: JobPost? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String, String, String, String, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(job?.title ?: "") }
    var company by remember { mutableStateOf(job?.company ?: "") }
    var location by remember { mutableStateOf(job?.location ?: "") }
    var desc by remember { mutableStateOf(job?.description ?: "") }
    var salary by remember { mutableStateOf(job?.salaryRange ?: "") }
    var reqs by remember { mutableStateOf(job?.requirements ?: "") }
    var phone by remember { mutableStateOf(job?.phoneNumber ?: "") }
    var web by remember { mutableStateOf(job?.website ?: "") }
    var deadline by remember { mutableStateOf(job?.deadline ?: "") }
    var logoUrl by remember { mutableStateOf(job?.imageUrl ?: "") }
    var employmentType by remember { mutableStateOf(job?.employmentType ?: "FULL_TIME") }
    var workplaceType by remember { mutableStateOf(job?.workplaceType ?: "ON_SITE") }
    var experienceLevel by remember { mutableStateOf(job?.experienceLevel ?: "MID_LEVEL") }
    var applicationUrl by remember { mutableStateOf(job?.applicationUrl ?: "") }
    
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        logoUrl = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (job == null) "Post New Job" else "Edit Job Posting") },
        text = {
            @OptIn(ExperimentalLayoutApi::class)
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Company Logo")
                }
                if (logoUrl.isNotEmpty()) Text("Logo selected ✅", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Job Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company/School Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                Text("Employment type", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "TEMPORARY").forEach { value ->
                        FilterChip(selected = employmentType == value, onClick = { employmentType = value }, label = { Text(value.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Text("Workplace", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ON_SITE", "HYBRID", "REMOTE").forEach { value ->
                        FilterChip(selected = workplaceType == value, onClick = { workplaceType = value }, label = { Text(value.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                Text("Experience level", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ENTRY_LEVEL", "MID_LEVEL", "SENIOR", "MANAGER").forEach { value ->
                        FilterChip(selected = experienceLevel == value, onClick = { experienceLevel = value }, label = { Text(value.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("Salary Range") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("Deadline") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = web, onValueChange = { web = it }, label = { Text("Website Link") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = applicationUrl, onValueChange = { applicationUrl = it }, label = { Text("Application link (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reqs, onValueChange = { reqs = it }, label = { Text("Requirements") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Job Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(title, company, location, desc, salary, reqs, phone, web, deadline, logoUrl, employmentType, workplaceType, experienceLevel, applicationUrl)
            }, enabled = title.isNotBlank() && company.isNotBlank()) {
                Text(if (job == null) "Post" else "Update")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalDetailScreen(
    professionalId: String,
    viewModel: DiscoveryViewModel = viewModel(),
    onBackClicked: () -> Unit
) {
    val professionals by viewModel.professionals.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val prof = professionals.find { it.uid == professionalId }
    val profReviews = reviews[professionalId] ?: emptyList()

    var showReviewDialog by remember { mutableStateOf(false) }
    var showBookingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(professionalId) {
        viewModel.fetchReviews(professionalId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(prof?.name ?: "Professional Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (prof == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Professional not found")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = prof.profileImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(140.dp).clip(CircleShape).background(Color.LightGray),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(prof.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(prof.role.uppercase(Locale.getDefault()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                            Text(String.format(Locale.US, " %.1f (%d reviews)", prof.averageRating, prof.reviewCount), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(modifier = Modifier.padding(24.dp)) {
                    Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(prof.description, style = MaterialTheme.typography.bodyLarge)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    InfoRow(icon = Icons.Default.LocationOn, label = "Location", value = prof.location)
                    InfoRow(icon = Icons.Default.Phone, label = "Contact", value = prof.contact)
                    
                    if (prof.role == "teacher") {
                        Spacer(Modifier.height(24.dp))
                        TeacherPointsDisplay()
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Buyer Ratings \u0026 Reviews", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("What parents say", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        TextButton(onClick = { showReviewDialog = true }) { Text("Add Review") }
                    }
                    
                    if (profReviews.isEmpty()) {
                        Text("No reviews yet. Be the first to review!", modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray, textAlign = TextAlign.Center)
                    } else {
                        profReviews.forEach { review ->
                            ReviewCard(review)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { showBookingDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Book Appointment (KES ${prof.bookingPrice})")
                    }
                }
            }
        }
    }

    if (showReviewDialog) {
        AddReviewDialog(onDismiss = { showReviewDialog = false }, onConfirm = { rating, comment ->
            viewModel.addReview(professionalId, rating, comment)
            showReviewDialog = false
        })
    }
    if (showBookingDialog && prof != null) {
        AppointmentRequestDialog(
            providerContact = prof.contact,
            onDismiss = { showBookingDialog = false },
            onConfirm = { phone, date, time, notes ->
                viewModel.initiateBookingPayment(prof, phone, date, time, notes) { }
                showBookingDialog = false
            }
        )
    }
}

@Composable
private fun AppointmentRequestDialog(
    providerContact: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var phone by remember { mutableStateOf(providerContact) }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request an appointment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a preferred time. The provider will see your request and can confirm or decline it.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(phone, { phone = it }, label = { Text("Your phone number") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Preferred date (e.g. 20 Nov 2026)") }, singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Preferred time") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("What do you need? (optional)") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(phone.trim(), date.trim(), time.trim(), notes.trim()) }, enabled = phone.isNotBlank() && date.isNotBlank() && time.isNotBlank()) { Text("Send request") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ReviewCard(review: Review) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(review.reviewerName, fontWeight = FontWeight.Bold)
                Row {
                    repeat(review.rating) { Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp)) }
                }
            }
            Text(review.comment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun AddReviewDialog(onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate Service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Rating:")
                Row {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }) {
                            Icon(Icons.Default.Star, null, tint = if (i <= rating) Color(0xFFFFB300) else Color.Gray)
                        }
                    }
                }
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Your experience") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(rating, comment) }) { Text("Submit") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailScreen(
    vendorId: String,
    viewModel: DiscoveryViewModel = viewModel(),
    cartViewModel: CartViewModel,
    onBackClicked: () -> Unit
) {
    val allVendors by viewModel.allVendors.collectAsState()
    val activeGroupDeals by viewModel.activeGroupDeals.collectAsState()
    
    val vendor = allVendors.find { it.vendorId == vendorId }
    
    val isParent = vendor?.role == "parent/guardian"
    var showSwapDialog by remember { mutableStateOf<ProductItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vendor?.vendorName ?: "Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (vendor == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Not found")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isParent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            AsyncImage(
                                model = vendor.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            if (vendor.isVerified) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier
                                        .size(32.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(Color.White, CircleShape)
                                        .padding(2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(vendor.vendorName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(if (isParent) "Parent/Guardian" else "Marketplace Seller", color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        VendorStat("Rating", "★ ${vendor.rating}")
                        VendorStat("Active", "${vendor.yearsActive} Yrs")
                        VendorStat("Response", vendor.sellerResponseTime)
                        VendorStat("Orders", "${vendor.transactionHistoryCount}")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoRow(icon = Icons.Default.LocationOn, label = "Location", value = vendor.location)
                    if (vendor.physicalLocationVerified) InfoRow(icon = Icons.Default.GpsFixed, label = "Physical Location Verified", value = "Confirmed by GPS \u0026 Map")
                    InfoRow(icon = Icons.Default.Phone, label = "Phone Verification", value = if (vendor.phoneVerified) "Verified M-Pesa Number" else "Unverified")
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(if (isParent) "Items for Swap" else "Product Catalog", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    
                    vendor.products.forEach { product ->
                        ProductDetailCard(product, isParent, activeGroupDeals, cartViewModel, viewModel, onSwapRequest = { showSwapDialog = product })
                    }
                }
            }
        }
    }

    if (showSwapDialog != null) {
        SwapRequestDialog(
            targetProduct = showSwapDialog!!,
            onDismiss = { showSwapDialog = null },
            onSubmit = { offeredId, cash ->
                viewModel.requestSwap(vendor!!.vendorId, vendor.vendorName, showSwapDialog!!.id, offeredId, cash)
                showSwapDialog = null
            }
        )
    }
}

@Composable
fun VendorStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun ProductDetailCard(
    product: ProductItem, 
    isParent: Boolean, 
    activeGroupDeals: List<GroupDeal>, 
    cartViewModel: CartViewModel,
    discoveryViewModel: DiscoveryViewModel,
    onSwapRequest: () -> Unit
) {
    var showGalleryDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp).clickable { showGalleryDialog = true }) {
                    AsyncImage(
                        model = product.imageUrls.getOrNull(0),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (product.promoLabel.isNotEmpty()) Badge(containerColor = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 4.dp)) { Text(product.promoLabel.uppercase()) }
                        if (product.isNew) Badge(containerColor = Color(0xFFFF9800), modifier = Modifier.padding(end = 4.dp)) { Text("NEW") }
                        if (product.agentInspected) Badge(containerColor = Color(0xFF9C27B0), modifier = Modifier.padding(end = 4.dp)) { Text("INSPECTED") }
                    }
                    if (isParent) {
                        Text("Value: KES ${product.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        when(product.listingType) {
                            ListingType.RETAIL -> {
                                Text("Retail: KES ${product.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            ListingType.GROUP_DEAL -> {
                                Text("Group: KES ${product.groupPrice}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                val deal = activeGroupDeals.find { it.productId == product.id }
                                if (deal != null) {
                                    val remaining = (deal.minRequired - deal.currentJoined).coerceAtLeast(0)
                                    Text("${deal.currentJoined} joined • $remaining more to close", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                            ListingType.WHOLESALE -> {
                                Text("Wholesale: KES ${product.wholesalePrice}", color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                            }
                            ListingType.SERVICE -> {
                                Text("Service: KES ${product.price}", color = Color(0xFF673AB7), fontWeight = FontWeight.Bold)
                            }
                            ListingType.PROFESSIONAL -> {
                                Text("Professional: KES ${product.price}", color = Color(0xFF673AB7), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            if (isParent) {
                Button(onClick = onSwapRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("Request Swap")
                }
            } else {
                if (product.listingType == ListingType.GROUP_DEAL) {
                    val deal = activeGroupDeals.find { it.productId == product.id }
                    if (deal != null) {
                        val userHasJoined = deal.buyerDetails.containsKey(currentUserId)
                        if (!userHasJoined && deal.status == DealStatus.ACTIVE) {
                            Button(onClick = { discoveryViewModel.joinGroupDeal(deal.id) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                                Icon(Icons.Default.GroupAdd, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Join Group Deal")
                            }
                        } else if (userHasJoined) {
                            Button(onClick = { 
                                cartViewModel.addToCart(product, PurchaseType.GROUP, dealId = deal.id)
                                Toast.makeText(context, "Added to cart with discount!", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.ShoppingCart, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add to Cart (Group Price)")
                            }
                        } else {
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Group target reached · joining closed")
                            }
                        }
                    }
                } else {
                    val purchaseType = when(product.listingType) {
                        ListingType.RETAIL -> PurchaseType.RETAIL
                        ListingType.WHOLESALE -> PurchaseType.WHOLESALE
                        ListingType.SERVICE -> PurchaseType.SERVICE
                        ListingType.PROFESSIONAL -> PurchaseType.PROFESSIONAL
                        else -> PurchaseType.RETAIL
                    }
                    
                    Button(
                        onClick = { 
                            cartViewModel.addToCart(product, purchaseType)
                            Toast.makeText(context, "${product.name} added to cart", Toast.LENGTH_SHORT).show() 
                        }, 
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                        Text("Secure Order")
                    }
                }
            }
        }
    }

    if (showGalleryDialog) {
        ProductDetailDialog(product, isParent, cartViewModel, discoveryViewModel, activeGroupDeals, onDismiss = { showGalleryDialog = false })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SwapRequestDialog(
    targetProduct: ProductItem,
    onDismiss: () -> Unit,
    onSubmit: (String?, Double) -> Unit
) {
    var cashTopUp by remember { mutableStateOf("") }
    var offeredItems by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var selectedOfferedItem by remember { mutableStateOf<ProductItem?>(null) }
    var offeredMenuExpanded by remember { mutableStateOf(false) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    DisposableEffect(uid) {
        val listener = if (!uid.isNullOrBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(uid).collection("products")
                .whereEqualTo("isForSwap", true)
                .addSnapshotListener { snapshot, _ ->
                    val availableItems = snapshot?.documents?.mapNotNull { document ->
                        document.toObject(ProductItem::class.java)?.copy(id = document.id)
                    }?.filter { it.status.equals("Available", ignoreCase = true) }.orEmpty()
                    offeredItems = availableItems
                    val selectedId = selectedOfferedItem?.id
                    if (selectedId != null && availableItems.none { it.id == selectedId }) selectedOfferedItem = null
                }
        } else null
        onDispose { listener?.remove() }
    }
    val cashOffer = cashTopUp.toDoubleOrNull() ?: 0.0
    val hasOffer = selectedOfferedItem != null || cashOffer > 0.0
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Swap") },
        text = {
            @OptIn(ExperimentalLayoutApi::class)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Swapping for: ${targetProduct.name}", fontWeight = FontWeight.Bold)
                Text("Value: KES ${targetProduct.price}", style = MaterialTheme.typography.bodySmall)
                
                HorizontalDivider()

                Text("What are you offering?", style = MaterialTheme.typography.labelLarge)
                ExposedDropdownMenuBox(
                    expanded = offeredMenuExpanded,
                    onExpandedChange = { offeredMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedOfferedItem?.name ?: "Choose one of your items",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Your swap item") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = offeredMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = offeredMenuExpanded,
                        onDismissRequest = { offeredMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No item offer") },
                            onClick = { selectedOfferedItem = null; offeredMenuExpanded = false }
                        )
                        offeredItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.name} • KES ${item.price}") },
                                onClick = { selectedOfferedItem = item; offeredMenuExpanded = false }
                            )
                        }
                    }
                }
                if (offeredItems.isEmpty()) {
                    Text("You have no available swap listings yet. You can still offer a cash top-up.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                
                Text("Offer a cash top-up (optional):", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = cashTopUp,
                    onValueChange = { input -> if (input.isEmpty() || input.matches(Regex("^\\d{0,8}(\\.\\d{0,2})?$"))) cashTopUp = input },
                    label = { Text("Cash Amount (KES)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (!hasOffer) Text("Choose an item or add a cash offer to send a request.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedOfferedItem?.id, cashOffer) }, enabled = hasOffer) {
                Text("Send Request")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TeacherPointsDisplay() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFFFB300))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Teacher Recognition", fontWeight = FontWeight.Bold)
                Text("Highly rated by the community", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
