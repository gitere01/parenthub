package com.joseph.cbchubpro.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.joseph.cbchubpro.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpClicked: (String, String, String, String, Map<String, Any>) -> Unit,
    onSignInClicked: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    
    // UI Labels for roles
    val publicRoles = listOf(
        "school", 
        "parent/guardian", 
        "vendor", 
        "service provider", 
        "professional", 
        "Private Teacher (Content Creator)",
        "Institution (POS, Visitors, Queueing)",
        "Property Agent (Real Estate)"
    )

    // Mapping UI labels to database roles
    val roleMap = mapOf(
        "school" to "school",
        "parent/guardian" to "parent/guardian",
        "vendor" to "vendor",
        "service provider" to "service provider",
        "professional" to "professional",
        "Private Teacher (Content Creator)" to "teacher",
        "Institution (POS, Visitors, Queueing)" to "institution",
        "Property Agent (Real Estate)" to "property_agent"
    )

    var selectedRoleLabel by remember { mutableStateOf(publicRoles[1]) } // Default to parent/guardian

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "Logo",
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedRoleLabel,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        publicRoles.forEach { roleLabel ->
                            DropdownMenuItem(
                                text = { Text(roleLabel) },
                                onClick = {
                                    selectedRoleLabel = roleLabel
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                // SECURITY: Secretly assign super_admin ONLY if the owner's email is used
                val finalRole = if (email.trim().lowercase() == "gitere01@gmail.com") {
                    "super_admin"
                } else {
                    roleMap[selectedRoleLabel] ?: "parent/guardian"
                }
                onSignUpClicked(name, email, password, finalRole, emptyMap())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Up")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSignInClicked) {
            Text("Already have an account? Sign In")
        }
    }
}
