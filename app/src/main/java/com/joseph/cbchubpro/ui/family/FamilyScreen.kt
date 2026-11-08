package com.joseph.cbchubpro.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FamilyScreen() {
    val viewModel: FamilyViewModel = viewModel()
    val childrenUiState by viewModel.childrenUiState.collectAsState()
    val familyCheckState by viewModel.familyCheckState.collectAsState()

    LaunchedEffect(familyCheckState) {
        if (familyCheckState is FamilyCheckState.HasFamily) {
            viewModel.fetchChildren((familyCheckState as FamilyCheckState.HasFamily).familyId)
        }
    }

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
        ) {
            when (val state = childrenUiState) {
                is ChildrenUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is ChildrenUiState.Success -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.children) { child ->
                            ChildItem(child = child, onApprove = { viewModel.approvePairing(child.id) })
                        }
                    }
                }
                is ChildrenUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
        }
    }
}

@Composable
fun ChildItem(child: Child, onApprove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = child.name, style = MaterialTheme.typography.titleMedium)
                Text(text = child.email, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(16.dp))
            if (child.isPaired) {
                Text("Paired", color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onApprove) {
                    Text("Approve")
                }
            }
        }
    }
}
