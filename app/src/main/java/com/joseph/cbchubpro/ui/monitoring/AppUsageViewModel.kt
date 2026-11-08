package com.joseph.cbchubpro.ui.monitoring

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppUsage(val packageName: String = "", val lastTimeUsed: Long = 0)

class AppUsageViewModel : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference

    private val _appUsage = MutableStateFlow<AppUsage?>(null)
    val appUsage: StateFlow<AppUsage?> = _appUsage

    fun listenForAppUsage(childId: String) {
        val appUsageRef = db.child("appUsage").child(childId)
        appUsageRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val packageName = snapshot.child("packageName").getValue(String::class.java)
                val lastTimeUsed = snapshot.child("lastTimeUsed").getValue(Long::class.java)
                if (packageName != null && lastTimeUsed != null) {
                    _appUsage.value = AppUsage(packageName, lastTimeUsed)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }
}
