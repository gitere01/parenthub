package com.joseph.cbchubpro.ui.screentime

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
import java.util.concurrent.TimeUnit

@Composable
fun ScreenTimeScreen(childId: String) {
    val viewModel: ScreenTimeViewModel = viewModel()
    val screenTimeList by viewModel.screenTimeList.collectAsState()

    LaunchedEffect(childId) {
        viewModel.listenForScreenTime(childId)
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (screenTimeList.isEmpty()) {
            Text(text = "Waiting for screen time data...")
        } else {
            LazyColumn {
                items(screenTimeList) { appScreenTime ->
                    val formattedTime = formatDuration(appScreenTime.totalTimeInForeground)
                    Text(
                        text = "${appScreenTime.packageName}: $formattedTime",
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours > 0 -> String.format("%d h %02d m", hours, minutes)
        minutes > 0 -> String.format("%d m", minutes)
        else -> "< 1 m"
    }
}
