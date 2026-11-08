package com.joseph.cbchubpro.ui.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.joseph.cbchubpro.ui.management.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    onBackClicked: () -> Unit,
    onCheckoutSuccess: () -> Unit
) {
    val cartItems by viewModel.cartItems.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val checkoutStatus by viewModel.checkoutStatus.collectAsState()
    val pickupPoints by viewModel.pickupPoints.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(checkoutStatus) {
        checkoutStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    var phoneNumber by remember { mutableStateOf("") }
    var selectedPoint by remember { mutableStateOf<PickupPoint?>(null) }

    val subtotal = cartItems.sumOf { it.price * it.quantity }
    val deliveryFee = if (selectedPoint != null) {
        selectedPoint!!.baseFee + FinanceConstants.PLATFORM_HUB_REVENUE
    } else {
        0.0
    }
    val total = subtotal + deliveryFee

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pickup Mtaani Checkout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (cartItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your cart is empty", color = Color.Gray)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    items(cartItems) { item ->
                        CartItemRow(item) {
                            viewModel.removeFromCart(item.productId, item.purchaseType)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text("Delivery: Pickup Mtaani (COD)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Pay at the agent when picking up your package. A platform service fee of KES 50.00 is included.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        
                        Spacer(Modifier.height(16.dp))
                        Text("Select Pickup Point:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        
                        if (pickupPoints.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    pickupPoints.forEach { point ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedPoint = point }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = selectedPoint == point, onClick = { selectedPoint = point })
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(point.name, style = MaterialTheme.typography.bodyMedium)
                                                Text("Agent Fee: KES ${point.baseFee}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Recipient Phone Number") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                            placeholder = { Text("0712345678") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp).padding(bottom = 8.dp)) {
                        SummaryRow("Subtotal", "KES ${String.format(Locale.getDefault(), "%.2f", subtotal)}")
                        SummaryRow("Delivery Fee", "KES ${String.format(Locale.getDefault(), "%.2f", deliveryFee)}")
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        SummaryRow("Total to Pay at Agent", "KES ${String.format(Locale.getDefault(), "%.2f", total)}", isTotal = true)
                        
                        Spacer(Modifier.height(24.dp))
                        
                        val isReady = selectedPoint != null && phoneNumber.isNotBlank()

                        Button(
                            onClick = { 
                                selectedPoint?.let {
                                    viewModel.checkoutWithPickupMtaani(
                                        phoneNumber = phoneNumber, 
                                        selectedPoint = it, 
                                        onSuccess = onCheckoutSuccess
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = isReady && !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Place Order (Pay on Delivery)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(item: CartItem, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.productName, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                when(item.purchaseType) {
                    PurchaseType.RETAIL -> "Retail"
                    PurchaseType.WHOLESALE -> "Wholesale"
                    PurchaseType.GROUP -> "Group"
                    else -> item.purchaseType.name
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text("KES ${item.price} x ${item.quantity}", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, isTotal: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal, style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal, style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = if (isTotal) MaterialTheme.colorScheme.primary else Color.Unspecified)
    }
}
