package com.joseph.cbchubpro.ui.screentime

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppScreenTime(val packageName: String, val totalTimeInForeground: Long)

class ScreenTimeViewModel : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference

    private val _screenTimeList = MutableStateFlow<List<AppScreenTime>>(emptyList())
    val screenTimeList: StateFlow<List<AppScreenTime>> = _screenTimeList

    fun listenForScreenTime(childId: String) {
        val screenTimeRef = db.child("screenTime").child(childId)
        screenTimeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val screenTimeData = snapshot.children.mapNotNull {
                    val packageName = it.key
                    val totalTime = it.getValue(Long::class.java)
                    if (packageName != null && totalTime != null) {
                        AppScreenTime(packageName, totalTime)
                    } else {
                        null
                    }
                }
                _screenTimeList.value = screenTimeData.sortedByDescending { it.totalTimeInForeground }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }
}
