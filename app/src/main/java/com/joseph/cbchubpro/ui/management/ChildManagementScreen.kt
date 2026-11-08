package com.joseph.cbchubpro.ui.management

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.joseph.cbchubpro.ui.discovery.DiscoveryViewModel
import com.joseph.cbchubpro.ui.discovery.ProfessionalProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildManagementScreen(
    childId: String,
    onBackClicked: () -> Unit,
    discoveryViewModel: DiscoveryViewModel = viewModel()
) {
    val parentPoints by discoveryViewModel.pointsBalance.collectAsState()
    val childPoints by discoveryViewModel.childPointsBalance.collectAsState()
    val interactions by discoveryViewModel.interactions.collectAsState()
    val professionals by discoveryViewModel.professionals.collectAsState()
    val status by discoveryViewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showBuyPoints by remember { mutableStateOf(false) }

    LaunchedEffect(childId) {
        discoveryViewModel.fetchChildPoints(childId)
        discoveryViewModel.fetchInteractions(childId)
    }

    LaunchedEffect(status) {
        status?.let {
            snackbarHostState.showSnackbar(it)
            discoveryViewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Child Control Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 0. Child Account Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Face, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Child Account Balance", style = MaterialTheme.typography.labelMedium)
                        Text("$childPoints Points", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 1. Parent's Gifting Wallet
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My Gifting Wallet", style = MaterialTheme.typography.labelMedium)
                        Text("$parentPoints", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = { showBuyPoints = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Text(" Recharge")
                    }
                }
            }

            // 2. Interaction History
            Text("Learning Interaction History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (interactions.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No learning interactions recorded yet.", color = Color.Gray)
                }
            } else {
                interactions.forEach { record ->
                    val teacher = professionals.find { it.uid == record.teacherId }
                    InteractionCard(record, teacher, onGiftClick = {
                        teacher?.let { discoveryViewModel.sendGift(it, 10) }
                    })
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showBuyPoints) {
        BuyPointsDialog(onDismiss = { showBuyPoints = false }, onConfirm = { amount, phone ->
            discoveryViewModel.buyPoints(amount, phone)
        })
    }
}

@Composable
fun InteractionCard(record: InteractionRecord, teacher: ProfessionalProfile?, onGiftClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(record.contentType) {
                        "QUIZ" -> Icons.Default.Quiz
                        "LESSON" -> Icons.Default.MenuBook
                        "QUESTION" -> Icons.Default.HelpCenter
                        else -> Icons.Default.History
                    }, 
                    null, 
                    tint = MaterialTheme.colorScheme.primary, 
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(record.teacherName, fontWeight = FontWeight.Bold)
                    teacher?.let { Text(it.role, style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
                }
                Spacer(Modifier.weight(1f))
                if (record.pointsEarned > 0) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "+${record.pointsEarned} pts", 
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Content: ${record.contentTitle}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("Type: ${record.contentType}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onGiftClick) {
                    Icon(Icons.Default.CardGiftcard, null, modifier = Modifier.size(18.dp))
                    Text(" Send Appreciation (10 pts)")
                }
                Text(record.timestamp.toDate().toString().take(16), fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun BuyPointsDialog(onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.phoneNumber ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recharge Gifting Points") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Top up your wallet to send gifts to teachers and reward your child.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (KES)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("M-Pesa Number") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(amount.toDoubleOrNull() ?: 0.0, phone)
                onDismiss()
            }) { Text("Confirm Payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
