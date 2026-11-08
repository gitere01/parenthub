package com.joseph.cbchubpro.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joseph.cbchubpro.ui.management.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BursarDashboard(onLogoutClicked: () -> Unit, viewModel: SchoolViewModel = viewModel()) {
    val status by viewModel.statusMessage.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Fee Balances", "Expenses", "Payroll", "SMS Credits")

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
                title = { Text("Bursar Control Panel", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogoutClicked) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            when (tabs[selectedTab]) {
                "Overview" -> BursarOverviewTab(viewModel)
                "Fee Balances" -> BursarFeeBalancesTab(viewModel)
                "Expenses" -> BursarExpensesTab(viewModel)
                "Payroll" -> BursarPayrollTab(viewModel)
                "SMS Credits" -> BursarSMSCreditsTab(viewModel)
            }
        }
    }
}

@Composable
fun BursarOverviewTab(viewModel: SchoolViewModel) {
    val students by viewModel.students.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val payroll by viewModel.payroll.collectAsState()

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        val totalFeesDue = students.sumOf { it.feeBalance }
        val totalExpenses = expenses.sumOf { it.amount }
        val totalPayroll = payroll.sumOf { it.netSalary }

        FinancialStatCard("Total Fee Balance", "KES $totalFeesDue", Color.Red)
        FinancialStatCard("Total Expenses", "KES $totalExpenses", MaterialTheme.colorScheme.primary)
        FinancialStatCard("Monthly Payroll", "KES $totalPayroll", Color.Blue)

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.runDarajaReconciliation() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Icon(Icons.Default.Sync, null)
            Spacer(Modifier.width(8.dp))
            Text("Reconcile M-Pesa Statement (Daraja)")
        }
    }
}

@Composable
fun FinancialStatCard(label: String, value: String, color: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun BursarFeeBalancesTab(viewModel: SchoolViewModel) {
    val students by viewModel.students.collectAsState()
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Student Fee Records", fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(students) { s ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text("Grade: ${s.grade}") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("KES ${s.feeBalance}", color = if(s.feeBalance > 0) Color.Red else Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { 
                                    val msg = "Dear Parent, ${s.name} has a fee balance of KES ${s.feeBalance}. Please settle at your earliest convenience."
                                    viewModel.sendBulkSMS(msg, "Fee Reminder", listOf(s.parentPhone))
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, "Send SMS", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BursarExpensesTab(viewModel: SchoolViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Log New Expense")
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            items(expenses) { e ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(e.category) },
                        supportingContent = { Text("${e.description} | M-Pesa: ${e.mpesaReceipt}") },
                        trailingContent = { Text("KES ${e.amount}", color = Color.Red, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        var cat by remember { mutableStateOf("") }
        var amt by remember { mutableStateOf("") }
        var receipt by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Record Expense") },
            text = {
                Column {
                    OutlinedTextField(value = cat, onValueChange = { cat = it }, label = { Text("Category (e.g. Electricity)") })
                    OutlinedTextField(value = amt, onValueChange = { amt = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = receipt, onValueChange = { receipt = it }, label = { Text("M-Pesa Receipt Number") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addExpense(ExpenseRecord(category = cat, amount = amt.toDoubleOrNull() ?: 0.0, description = desc, mpesaReceipt = receipt))
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BursarPayrollTab(viewModel: SchoolViewModel) {
    val payroll by viewModel.payroll.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Staff Payroll History", fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(payroll) { p ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text(p.staffName) },
                        supportingContent = { Text("${p.month} | Net: KES ${p.netSalary}") },
                        trailingContent = { Badge(containerColor = if(p.status == "PAID") Color(0xFF4CAF50) else Color.Gray) { Text(p.status) } }
                    )
                }
            }
        }
    }
}

@Composable
fun BursarSMSCreditsTab(viewModel: SchoolViewModel) {
    val settings by viewModel.schoolSettings.collectAsState()
    var payPhone by remember { mutableStateOf("") }
    val credits = settings["smsCredits"] ?: 0

    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Bulk SMS Inventory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("These credits are used by Principal, Owner, and Teachers for school-wide updates.", style = MaterialTheme.typography.bodySmall)
        
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Available SMS Credits", style = MaterialTheme.typography.labelMedium)
                Text("$credits SMS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Text("Top Up SMS Credits", fontWeight = FontWeight.Bold)
        OutlinedTextField(value = payPhone, onValueChange = { payPhone = it }, label = { Text("M-Pesa Phone for Payment") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))

        val smsPackages = listOf(
            Triple("Starter", 1000, 1500),
            Triple("Growth", 5000, 6000),
            Triple("Pro", 10000, 10000)
        )

        smsPackages.forEach { (name, count, price) ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                if(payPhone.isNotBlank()) viewModel.buySMSPackage(name, count, price, payPhone)
            }) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text("$count SMS Credits")
                    }
                    Text("KES $price", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
