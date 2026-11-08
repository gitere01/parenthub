package com.joseph.cbchubpro.ui.childcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.joseph.cbchubpro.ui.family.FamilyCheckState
import com.joseph.cbchubpro.ui.family.FamilyViewModel

@Composable
fun ChildControlScreen(navController: NavController, familyViewModel: FamilyViewModel = viewModel()) {
    val familyCheckState by familyViewModel.familyCheckState.collectAsState()

    // Fetch the family ID when the screen is first composed
    LaunchedEffect(Unit) {
        familyViewModel.getFamilyId()
    }

    when (val state = familyCheckState) {
        is FamilyCheckState.Loading -> {
            // Show a loading indicator while checking for a family
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is FamilyCheckState.HasFamily -> {
            // When a family is found, navigate to the management screen.
            // This will only execute once when the state changes to HasFamily.
            LaunchedEffect(state.familyId) {
                navController.navigate("family_management/${state.familyId}") {
                    popUpTo("child_control") { inclusive = true }
                }
            }
        }
        is FamilyCheckState.NoFamily -> {
            // If there's no family, show a button to create one
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { navController.navigate("create_family") }) {
                    Text("Create a Family")
                }
            }
        }
        is FamilyCheckState.Error -> {
            // Show an error message if something went wrong
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}")
            }
        }
    }
}
