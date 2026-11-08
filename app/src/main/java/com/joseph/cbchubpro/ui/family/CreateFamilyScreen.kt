package com.joseph.cbchubpro.ui.family

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CreateFamilyScreen(familyViewModel: FamilyViewModel = viewModel(), onFamilyCreated: (String) -> Unit) {
    val actionResultState by familyViewModel.actionResultState.collectAsState()
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create a Family Circle", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Family Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { familyViewModel.createFamily(name) },
            enabled = name.isNotBlank() && actionResultState !is ActionResultState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (actionResultState is ActionResultState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Create Family")
            }
        }
    }

    LaunchedEffect(actionResultState) {
        when (val state = actionResultState) {
            is ActionResultState.Error -> {
                Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    val familyCheckState by familyViewModel.familyCheckState.collectAsState()
    LaunchedEffect(familyCheckState) {
        if (familyCheckState is FamilyCheckState.HasFamily) {
            onFamilyCreated((familyCheckState as FamilyCheckState.HasFamily).familyId)
        }
    }
}
