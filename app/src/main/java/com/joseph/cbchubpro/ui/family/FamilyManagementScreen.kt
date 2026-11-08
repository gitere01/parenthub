package com.joseph.cbchubpro.ui.family

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joseph.cbchubpro.ui.management.ActionResultState as PointActionResult
import com.joseph.cbchubpro.ui.management.PointViewModel

@Composable
fun FamilyManagementScreen(
    familyId: String,
    familyViewModel: FamilyViewModel = viewModel(),
    pointViewModel: PointViewModel = viewModel(),
    onManageChildClicked: (String) -> Unit
) {
    val childrenState by familyViewModel.childrenUiState.collectAsState()
    val actionResultState by familyViewModel.actionResultState.collectAsState()
    val familyCheckState by familyViewModel.familyCheckState.collectAsState()
    val familyMembers by familyViewModel.familyMembers.collectAsState()
    val pairingCode by familyViewModel.pairingCode.collectAsState()
    val inviteLink by familyViewModel.inviteLink.collectAsState()
    
    val pointBalance by pointViewModel.pointBalance.collectAsState()
    val pointActionStatus by pointViewModel.transactionStatus.collectAsState()
    
    var showDeleteConfirmationDialog by remember { mutableStateOf<String?>(null) }
    var showAddChildDialog by remember { mutableStateOf(false) }
    var showEditFamilyNameDialog by remember { mutableStateOf(false) }
    var showEditChildNameDialog by remember { mutableStateOf<Child?>(null) }
    var showRePairDialog by remember { mutableStateOf<Child?>(null) }
    var showChangePasswordDialog by remember { mutableStateOf<Child?>(null) }
    var showBuyPointsDialog by remember { mutableStateOf(false) }
    
    var familyNameInput by remember { mutableStateOf("") }
    var editChildNameInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val familyInfo = familyCheckState as? FamilyCheckState.HasFamily
    val isAdmin = familyInfo?.isAdmin == true

    val grades = listOf("Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6", "Junior Secondary 1", "Junior Secondary 2", "Junior Secondary 3")

    LaunchedEffect(familyId) {
        familyViewModel.fetchChildren(familyId)
    }

    LaunchedEffect(actionResultState) {
        when (val state = actionResultState) {
            is ActionResultState.Success -> {
                state.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                familyViewModel.resetActionResultState()
            }
            is ActionResultState.Error -> {
                Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_SHORT).show()
                familyViewModel.resetActionResultState()
            }
            else -> {}
        }
    }

    LaunchedEffect(pointActionStatus) {
        when (val state = pointActionStatus) {
            is PointActionResult.Success -> {
                if (state.message?.startsWith("http") == true) {
                    val intent = Intent(Intent.ACTION_VIEW, state.message.toUri())
                    context.startActivity(intent)
                } else {
                    state.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                }
                pointViewModel.resetStatus()
            }
            is PointActionResult.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                pointViewModel.resetStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                Column(horizontalAlignment = Alignment.End) {
                    SmallFloatingActionButton(
                        onClick = { familyViewModel.generateAdultInviteLink(familyId) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Invite Adult")
                    }
                    FloatingActionButton(
                        onClick = { showAddChildDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Child")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = padding)
                .padding(all = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val familyName = familyInfo?.familyName ?: "Family Circle"
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = familyName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (isAdmin) {
                    IconButton(onClick = { 
                        familyNameInput = familyName
                        showEditFamilyNameDialog = true 
                    }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(size = 20.dp))
                    }
                }
            }
            Text(text = if (isAdmin) "Family Management (Admin)" else "Family Circle (Guardian)", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(height = 16.dp))

            // Points Summary Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(all = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(weight = 1f)) {
                        Text(text = "Available Points", style = MaterialTheme.typography.labelMedium)
                        Text(text = "${pointBalance?.totalPoints ?: 0.0}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(onClick = { showBuyPointsDialog = true }) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(size = 18.dp))
                        Spacer(modifier = Modifier.width(width = 4.dp))
                        Text(text = "Buy Points")
                    }
                }
            }

            // Display Codes/Invites
            pairingCode?.let { code ->
                CodeCard(title = "New Pairing Code", code = code, subtitle = "Enter this in the Child App. Expires in 15 mins.", onDismiss = { familyViewModel.clearPairingCode() })
                Spacer(modifier = Modifier.height(height = 8.dp))
            }
            inviteLink?.let { link ->
                CodeCard(title = "Adult Invite ID", code = link, subtitle = "Share ID with guardian", isShareable = true, onDismiss = { familyViewModel.clearInviteLink() })
                Spacer(modifier = Modifier.height(height = 8.dp))
            }

            // Tabs for Members vs Children
            var selectedTab by remember { mutableIntStateOf(0) }
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text(text = "Children", modifier = Modifier.padding(all = 16.dp), fontWeight = if(selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text(text = "Adults", modifier = Modifier.padding(all = 16.dp), fontWeight = if(selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                }
            }

            Spacer(modifier = Modifier.height(height = 16.dp))

            if (selectedTab == 0) {
                when (val state = childrenState) {
                    is ChildrenUiState.Success -> {
                        if (state.children.isEmpty()) {
                            EmptyFamilyState(title = "No children added yet.", subtitle = "Add a child to manage their safety.", icon = Icons.Default.ChildCare)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(items = state.children) { child ->
                                    ChildMemberItem(
                                        child = child, 
                                        isAdmin = isAdmin, 
                                        onEdit = {
                                            editChildNameInput = child.name
                                            showEditChildNameDialog = child
                                        }, 
                                        onManage = { onManageChildClicked(child.id) },
                                        onChangePassword = {
                                            newPasswordInput = ""
                                            showChangePasswordDialog = child
                                        }, 
                                        onRePair = { showRePairDialog = child },
                                        onDelete = { showDeleteConfirmationDialog = child.id },
                                        onApprove = { familyViewModel.approvePairing(child.id) }
                                    )
                                }
                            }
                        }
                    }
                    is ChildrenUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is ChildrenUiState.Error -> { Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error) }
                    else -> {}
                }
            } else {
                if (familyMembers.isEmpty()) {
                    EmptyFamilyState(title = "No other adults yet.", subtitle = "Invite a guardian to manage together.", icon = Icons.Default.Group)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = familyMembers) { member -> AdultMemberItem(member = member) }
                    }
                }
            }
        }
    }

    // Dialogs Implementation
    if (showBuyPointsDialog) {
        BuyPointsDialog(onDismiss = { showBuyPointsDialog = false }) { amt, email, ph, first, last ->
            pointViewModel.buyPoints(amt, email, ph, first, last)
            showBuyPointsDialog = false
        }
    }

    if (showAddChildDialog) {
        AddChildDialog(grades = grades, onDismiss = { showAddChildDialog = false }) { name, grade, pass ->
            familyViewModel.generatePairingCode(familyId, name, grade, pass)
            showAddChildDialog = false
        }
    }

    showChangePasswordDialog?.let { child ->
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = null },
            title = { Text(text = "Set Exit Password") },
            text = {
                OutlinedTextField(
                    value = newPasswordInput,
                    onValueChange = { input -> if(input.length <= 6 && input.all { it.isDigit() }) newPasswordInput = input },
                    label = { Text(text = "Numeric Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            },
            confirmButton = {
                Button(onClick = {
                    familyViewModel.updateParentPassword(child.id, newPasswordInput)
                    showChangePasswordDialog = null
                }, enabled = newPasswordInput.length >= 4) { Text(text = "Save") }
            },
            dismissButton = { TextButton(onClick = { showChangePasswordDialog = null }) { Text(text = "Cancel") } }
        )
    }

    showRePairDialog?.let { child ->
        var pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRePairDialog = null },
            title = { Text(text = "Re-pair Device") },
            text = {
                OutlinedTextField(
                    value = pass,
                    onValueChange = { input -> if(input.length <= 6 && input.all { it.isDigit() }) pass = input },
                    label = { Text(text = "Set Child Exit Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            },
            confirmButton = {
                Button(onClick = {
                    familyViewModel.generatePairingCode(familyId, child.name, child.gradeLevel, pass, targetChildId = child.id)
                    showRePairDialog = null
                }, enabled = pass.length >= 4) { Text(text = "Generate") }
            },
            dismissButton = { TextButton(onClick = { showRePairDialog = null }) { Text(text = "Cancel") } }
        )
    }

    if (showEditFamilyNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditFamilyNameDialog = false },
            title = { Text(text = "Edit Family Name") },
            text = { OutlinedTextField(value = familyNameInput, onValueChange = { familyNameInput = it }, label = { Text(text = "New Name") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    familyViewModel.updateFamilyName(familyId, familyNameInput)
                    showEditFamilyNameDialog = false
                }) { Text(text = "Update") }
            },
            dismissButton = { TextButton(onClick = { showEditFamilyNameDialog = false }) { Text(text = "Cancel") } }
        )
    }

    showEditChildNameDialog?.let { child ->
        AlertDialog(
            onDismissRequest = { showEditChildNameDialog = null },
            title = { Text(text = "Edit Child Name") },
            text = { OutlinedTextField(value = editChildNameInput, onValueChange = { editChildNameInput = it }, label = { Text(text = "Name") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    familyViewModel.updateChildName(child.id, editChildNameInput)
                    showEditChildNameDialog = null
                }) { Text(text = "Update") }
            },
            dismissButton = { TextButton(onClick = { showEditChildNameDialog = null }) { Text(text = "Cancel") } }
        )
    }

    if (showDeleteConfirmationDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = null },
            title = { Text(text = "Remove Child") },
            text = { Text(text = "Are you sure you want to remove this child from your family circle?") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmationDialog?.let { familyViewModel.deleteChild(it) }
                    showDeleteConfirmationDialog = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(text = "Remove") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmationDialog = null }) { Text(text = "Cancel") } }
        )
    }
}

@Composable
fun BuyPointsDialog(onDismiss: () -> Unit, onConfirm: (Double, String, String, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Buy Gift Points") },
        text = {
            Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
                Text(text = "85% points to your wallet, 15% platform support.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(height = 16.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(text = "Amount KES") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount.toDoubleOrNull() ?: 0.0, email, phone, firstName, lastName) }, enabled = amount.isNotEmpty() && phone.isNotEmpty()) { Text(text = "Pay with PesaPal") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } }
    )
}

@Composable
fun CodeCard(title: String, code: String, subtitle: String, isShareable: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(all = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, modifier = Modifier.weight(weight = 1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(size = 16.dp)) }
            }
            Text(text = code, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Text(text = subtitle, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            if (isShareable) {
                Button(onClick = {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Join my family circle on CBC Hub! Use ID: $code")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(size = 18.dp))
                    Spacer(modifier = Modifier.width(width = 8.dp))
                    Text(text = "Share")
                }
            }
        }
    }
}

@Composable
fun EmptyFamilyState(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier = Modifier.fillMaxSize().padding(all = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(size = 64.dp), tint = Color.LightGray)
        Spacer(modifier = Modifier.height(height = 16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = subtitle, textAlign = TextAlign.Center, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ChildMemberItem(
    child: Child, 
    isAdmin: Boolean, 
    onEdit: () -> Unit, 
    onManage: () -> Unit, 
    onChangePassword: () -> Unit, 
    onRePair: () -> Unit, 
    onDelete: () -> Unit, 
    onApprove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onManage) {
        Row(modifier = Modifier.padding(all = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(size = 48.dp).clip(shape = CircleShape).background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(width = 12.dp))
            Column(modifier = Modifier.weight(weight = 1f)) {
                Text(text = child.name, fontWeight = FontWeight.Bold)
                Text(text = child.gradeLevel, style = MaterialTheme.typography.bodySmall)
                if (child.isPending) Text(text = "Pending Pairing...", color = Color.Blue, style = MaterialTheme.typography.labelSmall)
            }
            if (isAdmin) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(text = "Edit") }, onClick = { expanded = false; onEdit() })
                        DropdownMenuItem(text = { Text(text = "Exit Password") }, onClick = { expanded = false; onChangePassword() })
                        DropdownMenuItem(text = { Text("Re-pair Device") }, onClick = { expanded = false; onRePair() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(text = "Remove", color = Color.Red) }, onClick = { expanded = false; onDelete() })
                        if (child.isPending) DropdownMenuItem(text = { Text(text = "Approve") }, onClick = { expanded = false; onApprove() })
                    }
                }
            }
        }
    }
}

@Composable
fun AdultMemberItem(member: FamilyMember) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(all = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(size = 40.dp).clip(shape = CircleShape).background(color = Color.Gray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Person, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(width = 12.dp))
            Column {
                Text(text = member.name, fontWeight = FontWeight.Bold)
                Text(text = member.role.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun AddChildDialog(grades: List<String>, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf(grades[0]) }
    var password by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add Child") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(text = "Name") }, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedTextField(value = selectedGrade, onValueChange = {}, label = { Text("Grade") }, modifier = Modifier.fillMaxWidth(), readOnly = true, trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null) } })
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        grades.forEach { g -> DropdownMenuItem(text = { Text(text = g) }, onClick = { selectedGrade = g; expanded = false }) }
                    }
                }
                OutlinedTextField(value = password, onValueChange = { input -> if(input.all { it.isDigit() }) password = input }, label = { Text("Exit Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, selectedGrade, password) }, enabled = name.isNotBlank() && password.length >= 4) { Text(text = "Generate Code") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } }
    )
}
