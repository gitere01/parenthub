package com.joseph.cbchubpro.child.ui.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.joseph.cbchubpro.child.data.local.ChildDataRepository
import com.joseph.cbchubpro.child.data.model.Child
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class PairingUiState {
    object Idle : PairingUiState()
    object Loading : PairingUiState()
    object Success : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val repository = ChildDataRepository(application)

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun pairWithCode(inputCode: String, name: String) {
        val code = inputCode.trim()
        if (code.length != 6) {
            _uiState.value = PairingUiState.Error("Please enter a 6-digit code.")
            return
        }

        _uiState.value = PairingUiState.Loading
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
                val currentChildId = auth.currentUser?.uid ?: throw Exception("Authentication failed.")

                val codeDoc = firestore.collection("pairing_codes").document(code).get().await()
                if (!codeDoc.exists()) {
                    _uiState.value = PairingUiState.Error("Invalid or expired code.")
                    return@launch
                }

                val familyId = codeDoc.getString("familyId") ?: throw Exception("Invalid code data.")
                val parentId = codeDoc.getString("parentId")
                val gradeLevel = codeDoc.getString("gradeLevel") ?: "Grade 1"
                val parentPassword = codeDoc.getString("parentPassword") ?: "1234"
                val targetChildId = codeDoc.getString("targetChildId") // For re-pairing

                val batch = firestore.batch()
                
                var finalName = name
                var finalGrade = gradeLevel
                val carryOverData: MutableMap<String, Any> = mutableMapOf()

                if (targetChildId != null && targetChildId != currentChildId) {
                    // Re-pairing: Transfer identity and data from old device record to new one
                    val oldUserDoc = firestore.collection("users").document(targetChildId).get().await()
                    if (oldUserDoc.exists()) {
                        val oldData = oldUserDoc.data ?: emptyMap()
                        finalName = oldUserDoc.getString("name") ?: name
                        finalGrade = oldUserDoc.getString("gradeLevel") ?: gradeLevel
                        
                        // Collect all non-system fields to carry over (points, logs, etc.)
                        oldData.forEach { (k, v) -> 
                            if (k !in listOf("id", "familyId", "role", "paired", "pairedParentId", "createdAt", "updatedAt")) {
                                carryOverData[k] = v
                            }
                        }
                    }
                    
                    // Disconnect old device record from family and deactivate it
                    val oldUserRef = firestore.collection("users").document(targetChildId)
                    batch.update(oldUserRef, mapOf(
                        "familyId" to FieldValue.delete(),
                        "paired" to false,
                        "status" to "DECOMMISSIONED_DEVICE"
                    ))

                    // Remove old child UID from family members sub-collection
                    val oldMemberRef = firestore.collection("families").document(familyId)
                        .collection("members").document(targetChildId)
                    batch.delete(oldMemberRef)
                }

                // Setup the current device's user document
                val currentUserRef = firestore.collection("users").document(currentChildId)
                val newUserData = mutableMapOf(
                    "name" to finalName,
                    "familyId" to familyId,
                    "role" to "child",
                    "gradeLevel" to finalGrade,
                    "paired" to true, 
                    "pairedParentId" to parentId,
                    "parentPassword" to parentPassword,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                
                // Merge carried over data
                newUserData.putAll(carryOverData)

                batch.set(currentUserRef, newUserData, SetOptions.merge())

                // Add the current device UID to family members
                val memberRef = firestore.collection("families").document(familyId)
                    .collection("members").document(currentChildId)
                batch.set(memberRef, mapOf(
                    "role" to "child",
                    "name" to finalName,
                    "gradeLevel" to finalGrade,
                    "joinedAt" to FieldValue.serverTimestamp()
                ))

                // Clean up pairing code
                batch.delete(codeDoc.reference)
                batch.commit().await()

                // Save pairing status locally
                repository.saveChildData(
                    Child(
                        id = currentChildId,
                        name = finalName,
                        gradeLevel = finalGrade,
                        paired = true
                    )
                )
                repository.setParentPassword(parentPassword)

                _uiState.value = PairingUiState.Success
            } catch (e: Exception) {
                _uiState.value = PairingUiState.Error(e.localizedMessage ?: "Pairing failed.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            repository.clearData()
            _uiState.value = PairingUiState.Idle
        }
    }

    fun resetState() { _uiState.value = PairingUiState.Idle }
}
