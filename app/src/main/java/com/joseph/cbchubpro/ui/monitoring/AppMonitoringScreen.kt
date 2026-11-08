package com.joseph.cbchubpro.ui.monitoring

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppMonitoringScreen(childId: String) {
    val viewModel: AppUsageViewModel = viewModel()
    val appUsage by viewModel.appUsage.collectAsState()

    LaunchedEffect(childId) {
        viewModel.listenForAppUsage(childId)
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        appUsage?.let {
            Text(text = "Currently running app: ${it.packageName}")
        } ?: Text(text = "Waiting for app usage data...")
    }
}
