package com.joseph.cbchubpro.ui.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun LiveLocationScreen(childId: String) {
    val viewModel: ManagementViewModel = viewModel()
    val childLocation by viewModel.childLocation.collectAsState()

    // Start listening for location updates when the screen is shown
    LaunchedEffect(childId) {
        viewModel.listenForChildLocation(childId)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 1f) // Default position
    }

    // Animate the camera to the child's location when it updates
    LaunchedEffect(childLocation) {
        childLocation?.let {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(it, 15f)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // Add a marker for the child's location
            childLocation?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = "Child's Location"
                )
            }
        }
    }
}
