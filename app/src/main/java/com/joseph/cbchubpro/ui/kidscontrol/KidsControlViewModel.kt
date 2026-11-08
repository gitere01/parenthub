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

data class ChildWithCode(val id: String, val name: String, val code: String?, val online: Boolean)

data class PairingInputState(
    val childName: String = "",
    val isGeneratingCode: Boolean = false
)

class KidsControlViewModel : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference

    private val _childrenWithCodes = MutableStateFlow<List<ChildWithCode>>(emptyList())
    val childrenWithCodes: StateFlow<List<ChildWithCode>> = _childrenWithCodes

    private val _pairingInputState = MutableStateFlow(PairingInputState())
    val pairingInputState: StateFlow<PairingInputState> = _pairingInputState

    fun onChildNameChange(name: String) {
        _pairingInputState.value = _pairingInputState.value.copy(childName = name)
    }

    fun listenForChildrenAndCodes(parentId: String) {
        if (parentId.isBlank()) return

        val parentRef = db.child("parents").child(parentId).child("children")
        parentRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val children = snapshot.children.mapNotNull { it.getValue(ChildDevice::class.java) }
                val combinedList = children.map { child ->
                    ChildWithCode(child.id, child.name, child.code, child.online)
                }
                _childrenWithCodes.value = combinedList.sortedBy { it.name }
            }
            override fun onCancelled(error: DatabaseError) { /* Handle error */ }
        })
    }

    fun generateNewChildAndCode(parentId: String) {
        viewModelScope.launch {
            val childName = _pairingInputState.value.childName
            if (childName.isBlank() || parentId.isBlank()) return@launch

            _pairingInputState.value = _pairingInputState.value.copy(isGeneratingCode = true)

            val newPairingCode = Random.nextInt(100000, 999999).toString()
            val childId = "${parentId}_${childName.replace(" ", "_")}"
            val pairingData = mapOf("parentId" to parentId, "childName" to childName)
            val childData = ChildDevice(id = childId, name = childName, online = false, code = newPairingCode)

            val updates = mapOf(
                "/pairingCodes/$newPairingCode" to pairingData,
                "/parents/$parentId/children/$childId" to childData
            )
            db.updateChildren(updates).await()

            _pairingInputState.value = _pairingInputState.value.copy(childName = "", isGeneratingCode = false)
        }
    }

    fun deleteChild(parentId: String, child: ChildWithCode) {
        viewModelScope.launch {
            val updates = mutableMapOf<String, Any?>(
                "/parents/$parentId/children/${child.id}" to null,
                "/locations/${child.id}" to null,
                "/appUsage/${child.id}" to null,
                "/installedApps/${child.id}" to null
            )
            child.code?.let { updates["/pairingCodes/$it"] = null }

            db.updateChildren(updates).await()
        }
    }
}
