package com.joseph.cbchubpro.ui.kidscontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class PairingViewModel : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference

    private val _pendingChildren = MutableStateFlow<List<PendingChild>>(emptyList())
    val pendingChildren: StateFlow<List<PendingChild>> = _pendingChildren

    fun listenForPendingChildren(parentId: String) {
        val pendingRef = db.child("parents").child(parentId).child("activePairingCodes")
        pendingRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pending = snapshot.children.mapNotNull {
                    val name = it.key
                    val code = it.getValue(String::class.java)
                    if (name != null && code != null) {
                        PendingChild(name, code)
                    } else {
                        null
                    }
                }
                _pendingChildren.value = pending
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun generatePairingCode(parentId: String, childName: String) {
        viewModelScope.launch {
            if (childName.isBlank() || parentId.isBlank()) return@launch

            val activeCodeRef = db.child("parents").child(parentId).child("activePairingCodes").child(childName)
            val oldCode = activeCodeRef.get().await().value as? String
            if (oldCode != null) {
                db.child("pairingCodes").child(oldCode).removeValue().await()
            }

            val newPairingCode = Random.nextInt(100000, 999999).toString()
            val pairingData = mapOf("parentId" to parentId, "childName" to childName)
            val updates = mapOf(
                "/pairingCodes/$newPairingCode" to pairingData,
                "/parents/$parentId/activePairingCodes/$childName" to newPairingCode
            )
            db.updateChildren(updates).await()
        }
    }
}
