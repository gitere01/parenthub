package com.joseph.cbchubpro.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Child(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val gradeLevel: String = "",
    val isPaired: Boolean = false,
    val isPending: Boolean = false,
    val parentPassword: String? = null
)

data class FamilyMember(
    val uid: String = "",
    val name: String = "",
    val role: String = "",
    val joinedAt: Any? = null
)

sealed class ChildrenUiState {
    object Idle : ChildrenUiState()
    object Loading : ChildrenUiState()
    data class Success(val children: List<Child>) : ChildrenUiState()
    data class Error(val message: String) : ChildrenUiState()
}

sealed class FamilyCheckState {
    object Loading : FamilyCheckState()
    data class HasFamily(val familyId: String, val familyName: String, val isAdmin: Boolean = false, val isSubscriptionActive: Boolean = false) : FamilyCheckState()
    object NoFamily : FamilyCheckState()
    data class Error(val message: String) : FamilyCheckState()
}

sealed class ActionResultState {
    object Idle : ActionResultState()
    object Loading : ActionResultState()
    data class Success(val message: String? = null) : ActionResultState()
    data class Error(val message: String) : ActionResultState()
}

class FamilyViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _childrenUiState = MutableStateFlow<ChildrenUiState>(ChildrenUiState.Idle)
    val childrenUiState: StateFlow<ChildrenUiState> = _childrenUiState.asStateFlow()

    private val _familyCheckState = MutableStateFlow<FamilyCheckState>(FamilyCheckState.Loading)
    val familyCheckState: StateFlow<FamilyCheckState> = _familyCheckState.asStateFlow()

    private val _actionResultState = MutableStateFlow<ActionResultState>(ActionResultState.Idle)
    val actionResultState: StateFlow<ActionResultState> = _actionResultState.asStateFlow()

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _inviteLink = MutableStateFlow<String?>(null)
    val inviteLink: StateFlow<String?> = _inviteLink.asStateFlow()

    private val _familyMembers = MutableStateFlow<List<FamilyMember>>(emptyList())
    val familyMembers: StateFlow<List<FamilyMember>> = _familyMembers.asStateFlow()

    private val _pairedChildren = MutableStateFlow<List<Child>>(emptyList())
    private val _pendingCodes = MutableStateFlow<List<Child>>(emptyList())

    init {
        getFamilyId()
    }

    fun getFamilyId() {
        val userId = auth.currentUser?.uid ?: return
        _familyCheckState.value = FamilyCheckState.Loading
        
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(userId).get().await()
                val familyId = userDoc.getString("familyId")
                val isSubscribed = userDoc.getBoolean("subscriptionPaid") ?: false
                
                if (familyId != null) {
                    val familyDoc = firestore.collection("families").document(familyId).get().await()
                    val familyName = familyDoc.getString("familyName") ?: "My Family Circle"
                    
                    val memberDoc = firestore.collection("families").document(familyId)
                        .collection("members").document(userId).get().await()
                    val isAdmin = memberDoc.getString("role") == "admin"
                    
                    _familyCheckState.value = FamilyCheckState.HasFamily(familyId, familyName, isAdmin, isSubscribed)
                    fetchFamilyMembers(familyId)
                } else {
                    _familyCheckState.value = FamilyCheckState.NoFamily
                }
            } catch (e: Exception) {
                _familyCheckState.value = FamilyCheckState.Error(e.message ?: "Failed to check family status.")
            }
        }
    }

    fun fetchFamilyMembers(familyId: String) {
        firestore.collection("families").document(familyId)
            .collection("members")
            .addSnapshotListener { snapshots, e ->
                if (e == null) {
                    val members = snapshots?.documents?.map { doc -> doc.id } ?: emptyList()
                    
                    if (members.isNotEmpty()) {
                        firestore.collection("users")
                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), members)
                            .get()
                            .addOnSuccessListener { userSnapshots ->
                                val memberList = userSnapshots.documents.map { userDoc ->
                                    val role = snapshots?.documents?.find { it.id == userDoc.id }?.getString("role") ?: "member"
                                    FamilyMember(
                                        uid = userDoc.id,
                                        name = userDoc.getString("name") ?: userDoc.getString("displayName") ?: "Guardian",
                                        role = role
                                    )
                                }
                                _familyMembers.value = memberList
                            }
                    }
                }
            }
    }

    fun initiateSubscription(phoneNumber: String) {
        val userId = auth.currentUser?.uid ?: return
        val childrenCount = _pairedChildren.value.size
        if (childrenCount == 0) {
            _actionResultState.value = ActionResultState.Error("You need to add a child before subscribing.")
            return
        }

        val amount = childrenCount * 100.0 // 100 KES per child
        _actionResultState.value = ActionResultState.Loading
        
        viewModelScope.launch {
            try {
                val checkoutRef = firestore.collection("payment_requests").document()
                val payload = mapOf(
                    "id" to checkoutRef.id,
                    "userId" to userId,
                    "amount" to amount,
                    "phone" to phoneNumber,
                    "type" to "CHILD_CONTROL_SUBSCRIPTION",
                    "status" to "PENDING",
                    "timestamp" to FieldValue.serverTimestamp()
                )
                checkoutRef.set(payload).await()
                _actionResultState.value = ActionResultState.Success("STK Push sent to $phoneNumber for KES $amount")
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error("Payment request failed: ${e.message}")
            }
        }
    }

    fun createFamily(familyName: String) {
        val adminUid = auth.currentUser?.uid ?: return
        _actionResultState.value = ActionResultState.Loading

        val familyDocRef = firestore.collection("families").document()
        val familyId = familyDocRef.id

        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                batch.set(familyDocRef, mapOf(
                    "familyName" to familyName,
                    "createdBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
                
                val memberDocRef = firestore.collection("families").document(familyId)
                    .collection("members").document(adminUid)
                batch.set(memberDocRef, mapOf("role" to "admin"))

                val userDocRef = firestore.collection("users").document(adminUid)
                batch.update(userDocRef, "familyId", familyId)
                batch.commit().await()
                
                _actionResultState.value = ActionResultState.Success("Family circle '$familyName' created!")
                getFamilyId() // Refresh state
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error(e.message ?: "Failed to create family circle.")
            }
        }
    }

    fun updateFamilyName(familyId: String, newName: String) {
        _actionResultState.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                firestore.collection("families").document(familyId)
                    .update("familyName", newName).await()
                _actionResultState.value = ActionResultState.Success("Family name updated to '$newName'")
                getFamilyId()
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error("Failed to update name: ${e.message}")
            }
        }
    }

    fun fetchChildren(familyId: String) {
        _childrenUiState.value = ChildrenUiState.Loading
        
        firestore.collection("users")
            .whereEqualTo("familyId", familyId)
            .whereEqualTo("role", "child")
            .addSnapshotListener { snapshots, e ->
                if (e == null) {
                    val list = snapshots?.mapNotNull { doc ->
                        val name = doc.getString("name") ?: doc.getString("displayName") ?: "Child"
                        val email = doc.getString("email") ?: ""
                        val grade = doc.getString("gradeLevel") ?: ""
                        val paired = doc.getBoolean("paired") ?: false
                        val pass = doc.getString("parentPassword")
                        Child(id = doc.id, name = name, email = email, gradeLevel = grade, isPaired = paired, isPending = false, parentPassword = pass)
                    } ?: emptyList()
                    _pairedChildren.value = list
                    combineChildren()
                }
            }

        firestore.collection("pairing_codes")
            .whereEqualTo("familyId", familyId)
            .addSnapshotListener { snapshots, e ->
                if (e == null) {
                    val list = snapshots?.mapNotNull { doc ->
                        val name = doc.getString("childName") ?: "Pending Child"
                        val grade = doc.getString("gradeLevel") ?: ""
                        val expiresAt = doc.getLong("expiresAt") ?: 0L
                        val pass = doc.getString("parentPassword")
                        if (System.currentTimeMillis() < expiresAt) {
                            Child(id = doc.id, name = name, gradeLevel = grade, isPaired = false, isPending = true, parentPassword = pass)
                        } else null
                    } ?: emptyList()
                    _pendingCodes.value = list
                    combineChildren()
                }
            }
    }

    private fun combineChildren() {
        val combined = (_pairedChildren.value + _pendingCodes.value).distinctBy { it.id }
        _childrenUiState.value = ChildrenUiState.Success(combined)
    }

    fun generatePairingCode(familyId: String, childName: String, gradeLevel: String, parentPassword: String, targetChildId: String? = null) {
        val parentId = auth.currentUser?.uid ?: return
        _actionResultState.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                val code = (100000..999999).random().toString()
                val expiresAt = System.currentTimeMillis() + (15 * 60 * 1000) // 15 mins
                
                val pairingData = mutableMapOf(
                    "familyId" to familyId,
                    "parentId" to parentId,
                    "childName" to childName,
                    "gradeLevel" to gradeLevel,
                    "parentPassword" to parentPassword,
                    "expiresAt" to expiresAt,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                
                if (targetChildId != null) {
                    pairingData["targetChildId"] = targetChildId
                }
                
                firestore.collection("pairing_codes").document(code).set(pairingData).await()
                
                _pairingCode.value = code
                val msg = if (targetChildId != null) "Re-pairing code generated for $childName. Use password to exit child app later." else "Pairing code generated for $childName. Use password to exit child app later."
                _actionResultState.value = ActionResultState.Success(msg)
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error(e.message ?: "Failed to generate pairing code.")
            }
        }
    }

    fun updateParentPassword(childId: String, newPassword: String) {
        _actionResultState.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                firestore.collection("users").document(childId)
                    .update("parentPassword", newPassword).await()
                _actionResultState.value = ActionResultState.Success("Child exit password updated.")
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error("Failed to update password: ${e.message}")
            }
        }
    }

    fun generateAdultInviteLink(familyId: String) {
        val userId = auth.currentUser?.uid ?: return
        _actionResultState.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                val inviteId = firestore.collection("invites").document().id
                val expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
                
                val inviteData = mapOf(
                    "familyId" to familyId,
                    "invitedBy" to userId,
                    "role" to "guardian",
                    "expiresAt" to expiresAt,
                    "used" to false
                )
                
                firestore.collection("invites").document(inviteId).set(inviteData).await()
                
                _inviteLink.value = inviteId
                _actionResultState.value = ActionResultState.Success("Invite ID: $inviteId")
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error(e.message ?: "Failed to generate invite.")
            }
        }
    }

    fun joinFamilyWithInvite(inviteId: String) {
        val userId = auth.currentUser?.uid ?: return
        _actionResultState.value = ActionResultState.Loading
        
        viewModelScope.launch {
            try {
                val inviteDoc = firestore.collection("invites").document(inviteId).get().await()
                if (!inviteDoc.exists()) {
                    _actionResultState.value = ActionResultState.Error("Invalid or expired invite ID.")
                    return@launch
                }
                
                val familyId = inviteDoc.getString("familyId")
                val expiresAt = inviteDoc.getLong("expiresAt") ?: 0L
                val used = inviteDoc.getBoolean("used") ?: false
                
                if (System.currentTimeMillis() > expiresAt || used) {
                    _actionResultState.value = ActionResultState.Error("This invite has expired or already been used.")
                    return@launch
                }
                
                if (familyId != null) {
                    val batch = firestore.batch()
                    batch.update(firestore.collection("users").document(userId), "familyId", familyId)
                    val memberRef = firestore.collection("families").document(familyId)
                        .collection("members").document(userId)
                    batch.set(memberRef, mapOf("role" to "guardian", "joinedAt" to FieldValue.serverTimestamp()))
                    batch.update(firestore.collection("invites").document(inviteId), "used", true)
                    
                    batch.commit().await()
                    
                    _actionResultState.value = ActionResultState.Success("Successfully joined family circle!")
                    getFamilyId()
                }
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error(e.message ?: "Failed to join family circle.")
            }
        }
    }

    fun updateChildName(childId: String, newName: String) {
        _actionResultState.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                if (childId.length == 6 && childId.all { it.isDigit() }) {
                    firestore.collection("pairing_codes").document(childId)
                        .update("childName", newName).await()
                } else {
                    firestore.collection("users").document(childId)
                        .update("name", newName).await()
                }
                _actionResultState.value = ActionResultState.Success("Child name updated to '$newName'")
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error("Failed to update child name: ${e.message}")
            }
        }
    }

    fun deleteChild(childId: String) {
        _actionResultState.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                if (childId.length == 6 && childId.all { it.isDigit() }) {
                    firestore.collection("pairing_codes").document(childId).delete().await()
                    _actionResultState.value = ActionResultState.Success("Pending pairing code cancelled.")
                } else {
                    firestore.collection("users").document(childId)
                        .update(mapOf("familyId" to null, "paired" to false))
                        .await()
                    
                    val currentState = _familyCheckState.value as? FamilyCheckState.HasFamily
                    currentState?.let {
                        firestore.collection("families").document(it.familyId)
                            .collection("members").document(childId).delete().await()
                    }

                    _actionResultState.value = ActionResultState.Success("Child removed from family circle.")
                }
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error(e.message ?: "Permission Denied or Connection Error.")
            }
        }
    }

    fun approvePairing(childId: String) {
        _actionResultState.value = ActionResultState.Loading
        viewModelScope.launch {
            try {
                firestore.collection("users").document(childId)
                    .update("paired", true).await()
                _actionResultState.value = ActionResultState.Success("Pairing approved.")
            } catch (e: Exception) {
                _actionResultState.value = ActionResultState.Error(e.message ?: "Failed to approve pairing.")
            }
        }
    }

    fun clearPairingCode() { _pairingCode.value = null }
    fun clearInviteLink() { _inviteLink.value = null }
    fun resetActionResultState() { _actionResultState.value = ActionResultState.Idle }
}
