package com.joseph.cbchubpro.ui.management

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ManagementViewModel : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference

    private val _childLocation = MutableStateFlow<LatLng?>(null)
    val childLocation: StateFlow<LatLng?> = _childLocation

    fun listenForChildLocation(childId: String) {
        val locationRef = db.child("locations").child(childId)
        locationRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    _childLocation.value = LatLng(lat, lng)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }
}
