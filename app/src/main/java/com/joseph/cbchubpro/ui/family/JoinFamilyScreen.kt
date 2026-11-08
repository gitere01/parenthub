package com.joseph.cbchubpro.ui.family

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinFamilyScreen(
    familyViewModel: FamilyViewModel = viewModel(),
    onBackClicked: () -> Unit,
    onJoinedSuccessfully: (String) -> Unit
) {
    var inviteId by remember { mutableStateOf("") }
    val actionResultState by familyViewModel.actionResultState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Family Circle") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Enter Invite ID",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Paste the invitation ID or link shared by the family admin to join their circle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = inviteId,
                onValueChange = { inviteId = it },
                label = { Text("Invite ID / Code") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. ABC123XYZ") },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { 
                    // Extract inviteId if a full URL was pasted
                    val actualId = if (inviteId.contains("inviteId=")) {
                        inviteId.substringAfter("inviteId=")
                    } else {
                        inviteId
                    }
                    familyViewModel.joinFamilyWithInvite(actualId) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = inviteId.isNotBlank() && actionResultState !is ActionResultState.Loading,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                if (actionResultState is ActionResultState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Join Circle", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LaunchedEffect(actionResultState) {
        when (val state = actionResultState) {
            is ActionResultState.Success -> {
                Toast.makeText(context, state.message ?: "Joined successfully!", Toast.LENGTH_SHORT).show()
                // Fetch family ID again to trigger navigation in Dashboard
                familyViewModel.getFamilyId()
            }
            is ActionResultState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                familyViewModel.resetActionResultState()
            }
            else -> {}
        }
    }
    
    val familyCheckState by familyViewModel.familyCheckState.collectAsState()
    LaunchedEffect(familyCheckState) {
        if (familyCheckState is FamilyCheckState.HasFamily) {
            onJoinedSuccessfully((familyCheckState as FamilyCheckState.HasFamily).familyId)
        }
    }
}
