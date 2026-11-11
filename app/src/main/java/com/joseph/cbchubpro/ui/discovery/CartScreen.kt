package com.joseph.cbchubpro.ui.discovery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val sellerContacts by viewModel.sellerContacts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(cartItems.map { it.vendorId }.distinct()) {
        viewModel.loadSellerContacts(cartItems.map { it.vendorId }.distinct())
    }
    LaunchedEffect(Unit) {
        viewModel.checkoutStatus.collect { message ->
            if (message != null) {
                snackbarHostState.showSnackbar(message)
                viewModel.clearStatus()
            }
        }
    }

    val groups = cartItems.groupBy { it.vendorId }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Your cart", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (cartItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Your cart is empty", color = Color.Gray)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "Message each seller to confirm availability, delivery and payment. Your order is not placed until you agree with the seller.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    groups.forEach { (vendorId, items) ->
                        item(key = "seller-$vendorId") {
                            val sellerName = sellerContacts[vendorId]?.first ?: "Seller"
                            val phone = sellerContacts[vendorId]?.second.orEmpty()
                            val subtotal = items.sumOf { it.price * it.quantity }
                            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(sellerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    items.forEach { item ->
                                        CartItemRow(item) { viewModel.removeFromCart(item.productId, item.purchaseType) }
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Estimated items total")
                                        Text("KES ${String.format(Locale.getDefault(), "%.2f", subtotal)}", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            val digits = phone.filter(Char::isDigit).let { raw ->
                                                when {
                                                    raw.startsWith("0") -> "254" + raw.drop(1)
                                                    raw.startsWith("254") -> raw
                                                    else -> raw
                                                }
                                            }
                                            val message = buildString {
                                                append("Hi $sellerName, I’m interested in these items:")
                                                items.forEach { item ->
                                                    append("\n• ${item.productName} — KES ${item.price} × ${item.quantity} (${item.purchaseType.name.lowercase(Locale.ROOT)})")
                                                }
                                                append("\nEstimated items total: KES ${String.format(Locale.getDefault(), "%.2f", subtotal)}")
                                                append("\nPlease confirm availability, delivery options and how I can pay you directly.")
                                            }
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}"))
                                            context.startActivity(intent)
                                        },
                                        enabled = phone.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Message, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (phone.isBlank()) "Seller WhatsApp unavailable" else "Message seller on WhatsApp")
                                    }
                                }
                            }
                        }
                    }
                }
                Text("WhatsApp opens a prepared message. Review it and tap Send in WhatsApp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
}

@Composable
fun CartItemRow(item: CartItem, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.imageUrl, contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.productName, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${item.purchaseType.name.lowercase(Locale.ROOT)} · KES ${item.price} × ${item.quantity}", style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f)) }
    }
}
