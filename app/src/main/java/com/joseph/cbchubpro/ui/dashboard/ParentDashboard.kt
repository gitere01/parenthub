package com.joseph.cbchubpro.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joseph.cbchubpro.R
import com.joseph.cbchubpro.ui.family.FamilyCheckState
import com.joseph.cbchubpro.ui.family.FamilyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboard(
    username: String,
    onLogoutClicked: () -> Unit,
    onCreateFamilyClicked: () -> Unit,
    onJoinFamilyClicked: () -> Unit,
    onManageFamilyClicked: (String) -> Unit,
    onChildControlClicked: () -> Unit,
    onCareerGuardianClicked: () -> Unit,
    onMarketplaceClicked: () -> Unit,
    onSwapHubClicked: () -> Unit,
    onMySwapsClicked: () -> Unit,
    onMyOrdersClicked: () -> Unit,
    onJobBoardClicked: () -> Unit,
    onPropertyListingClicked: () -> Unit = {},
    onRestaurantBookingClicked: () -> Unit = {},
    familyViewModel: FamilyViewModel = viewModel()
) {
    val familyCheckState by familyViewModel.familyCheckState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parent Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogoutClicked) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Welcome back, $username!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            // Family Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Family Circle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    when (val state = familyCheckState) {
                        is FamilyCheckState.HasFamily -> {
                            DashboardButton(
                                text = "Manage Family Circle",
                                icon = Icons.Default.Groups,
                                onClick = { onManageFamilyClicked(state.familyId) }
                            )
                        }
                        is FamilyCheckState.NoFamily -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DashboardButton(
                                    text = "Create Family Circle",
                                    icon = Icons.Default.AddHome,
                                    onClick = onCreateFamilyClicked
                                )
                                DashboardButton(
                                    text = "Join Family Circle",
                                    icon = Icons.Default.Login,
                                    onClick = onJoinFamilyClicked,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            }
                        }
                        FamilyCheckState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        else -> {}
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    DashboardButton(
                        text = "Child Device Control",
                        icon = Icons.Default.PhonelinkLock,
                        onClick = onChildControlClicked
                    )
                }
            }

            // Marketplace & Swapping Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Marketplace & Swapping", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    
                    DashboardButton(
                        text = "Vendor Marketplace",
                        icon = Icons.Default.Storefront,
                        onClick = onMarketplaceClicked
                    )
                    Text("Buy retail, wholesale or group deals from verified vendors", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                    
                    DashboardButton(
                        text = "My Orders & Tracking",
                        icon = Icons.Default.LocalShipping,
                        onClick = onMyOrdersClicked,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    Text("View your orders and track delivery status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))

                    DashboardButton(
                        text = "Browse Swap Hub",
                        icon = Icons.Default.SwapCalls,
                        onClick = onSwapHubClicked,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text("Exchange books/uniforms with other parents", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                    
                    DashboardButton(
                        text = "My Swap Items & Requests",
                        icon = Icons.Default.ListAlt,
                        onClick = onMySwapsClicked,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Property & Hospitality Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Property & Hospitality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    
                    DashboardButton(
                        text = "Property Listings",
                        icon = Icons.Default.Apartment,
                        onClick = onPropertyListingClicked
                    )
                    Text("Rent apartments, buy land or find student hostels", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                    
                    DashboardButton(
                        text = "Restaurant & Room Booking",
                        icon = Icons.Default.Hotel,
                        onClick = onRestaurantBookingClicked,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    Text("Order food online or book a stay at verified hotels", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
                }
            }

            // Career Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Professional Services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    DashboardButton(
                        text = "Career Guardian (Discovery)",
                        icon = Icons.Default.School,
                        onClick = onCareerGuardianClicked
                    )
                    Spacer(Modifier.height(12.dp))
                    DashboardButton(
                        text = "Job Board & Postings",
                        icon = Icons.Default.Work,
                        onClick = onJobBoardClicked,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DashboardButton(
    text: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    onClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
