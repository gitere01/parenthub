package com.joseph.cbchubpro.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun signUp(name: String, email: String, password: String, role: String, extraData: Map<String, Any> = emptyMap()) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val emailKey = email.trim().lowercase()
            try {
                // Security check for super_admin role
                var finalRole = role
                if (finalRole == "super_admin") {
                    if (emailKey != "gitere01@gmail.com" || password != "jose@1234") {
                        finalRole = "parent/guardian"
                    }
                }

                val authResult = auth.createUserWithEmailAndPassword(emailKey, password).await()
                val user = authResult.user
                if (user != null) {
                    val profileUpdates = userProfileChangeRequest {
                        displayName = name
                    }
                    user.updateProfile(profileUpdates).await()
                    
                    // Check both collections for invites
                    val staffInvite = db.collection("staff_invites").document(emailKey).get().await()
                    val parentInvite = db.collection("parent_invites").document(emailKey).get().await()
                    val invitedDoc = if (staffInvite.exists()) staffInvite else if (parentInvite.exists()) parentInvite else null

                    var isStaff = false
                    val userData: MutableMap<String, Any> = mutableMapOf(
                        "uid" to user.uid,
                        "name" to name,
                        "email" to emailKey,
                        "createdAt" to System.currentTimeMillis()
                    )

                    if (invitedDoc != null) {
                        finalRole = invitedDoc.getString("role") ?: finalRole
                        isStaff = finalRole != "parent/guardian" && finalRole != "student"
                        invitedDoc.getString("schoolId")?.let { userData["schoolId"] = it }
                        invitedDoc.getString("grade")?.let { userData["grade"] = it }
                        invitedDoc.getString("studentId")?.let { userData["studentId"] = it }
                        invitedDoc.reference.delete().await()
                    }

                    userData["role"] = finalRole
                    userData["isStaff"] = isStaff
                    userData.putAll(extraData)

                    db.collection("users").document(user.uid).set(userData).await()
                    
                    auth.signOut()
                    _authState.value = AuthState.SignUpSuccess
                } else {
                    _authState.value = AuthState.Error("User creation failed.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val emailKey = email.lowercase().trim()
            val passKey = password.trim()
            
            if (emailKey.isEmpty() || passKey.isEmpty()) {
                _authState.value = AuthState.Error("Please enter email and password.")
                return@launch
            }

            // --- 1. Master Override for Platform Owner ---
            if (emailKey == "gitere01@gmail.com" && passKey == "jose@1234") {
                try {
                    val authResult = try {
                        auth.signInWithEmailAndPassword(emailKey, passKey).await()
                    } catch (e: Exception) {
                        try {
                            auth.createUserWithEmailAndPassword(emailKey, passKey).await()
                        } catch (collision: FirebaseAuthUserCollisionException) {
                            auth.signInWithEmailAndPassword(emailKey, passKey).await()
                        }
                    }
                    
                    val user = authResult.user!!
                    try {
                        val userDoc = db.collection("users").document(user.uid).get().await()
                        if (!userDoc.exists()) {
                            createDefaultProfile(user, "super_admin", isStaff = false)
                        }
                        _authState.value = AuthState.Success(user, "super_admin", false)
                    } catch (dbE: Exception) {
                        _authState.value = AuthState.Error("Owner profile load error: ${dbE.localizedMessage}")
                    }
                    return@launch
                } catch (e: Exception) {
                    _authState.value = AuthState.Error("Master login failed: ${e.localizedMessage}")
                    return@launch
                }
            }

            try {
                // --- 2. Authenticate or Activate ---
                // We try sign-in. If it fails with user-not-found, we try to create (claiming an invite).
                val authResult = try {
                    auth.signInWithEmailAndPassword(emailKey, passKey).await()
                } catch (signInE: Exception) {
                    try {
                        auth.createUserWithEmailAndPassword(emailKey, passKey).await()
                    } catch (createE: Exception) {
                        // If creation fails with collision, it means the password was just wrong.
                        if (createE is FirebaseAuthUserCollisionException) throw signInE
                        throw createE
                    }
                }

                val user = authResult.user!!
                
                // --- 3. Now Authenticated, check for Invitations ---
                // This read is now authorized because we are signed in.
                val staffInvite = db.collection("staff_invites").document(emailKey).get().await()
                val parentInvite = db.collection("parent_invites").document(emailKey).get().await()
                val invite = if (staffInvite.exists()) staffInvite else if (parentInvite.exists()) parentInvite else null
                
                if (invite != null) {
                    completeRegistration(user, invite)
                } else {
                    handleSuccessfulSignIn(user)
                }
                
            } catch (e: Exception) {
                Log.e("AuthViewModel", "SignIn Error", e)
                val msg = when {
                    e is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please double-check your credentials."
                    e.message?.contains("user-not-found") == true -> "No account found. If you were invited, ensure you use the temporary password."
                    e.message?.contains("password", ignoreCase = true) == true -> "Incorrect password. Note: Passwords must be at least 6 characters."
                    else -> e.localizedMessage ?: "Invalid credentials."
                }
                _authState.value = AuthState.Error(msg)
            }
        }
    }

    private suspend fun handleSuccessfulSignIn(user: com.google.firebase.auth.FirebaseUser) {
        try {
            val userDoc = db.collection("users").document(user.uid).get().await()
            if (userDoc.exists()) {
                val role = userDoc.getString("role") ?: "parent/guardian"
                val isStaff = userDoc.getBoolean("isStaff") ?: false
                val schoolId = userDoc.getString("schoolId")
                val studentId = userDoc.getString("studentId")
                _authState.value = AuthState.Success(user, role, isStaff, schoolId, studentId)
            } else {
                createDefaultProfile(user, "parent/guardian", isStaff = false)
                _authState.value = AuthState.Success(user, "parent/guardian", false)
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Login successful, but profile load failed: ${e.localizedMessage}")
        }
    }

    private suspend fun createDefaultProfile(user: com.google.firebase.auth.FirebaseUser, role: String, isStaff: Boolean) {
        val userData = mapOf(
            "uid" to user.uid,
            "name" to (user.displayName ?: "User"),
            "email" to (user.email ?: ""),
            "role" to role,
            "isStaff" to isStaff,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("users").document(user.uid).set(userData, SetOptions.merge()).await()
    }

    private suspend fun completeRegistration(
        user: com.google.firebase.auth.FirebaseUser,
        inviteDoc: com.google.firebase.firestore.DocumentSnapshot
    ) {
        val name = inviteDoc.getString("name") ?: inviteDoc.getString("childName")?.let { "Parent of $it" } ?: user.displayName ?: "User"
        val role = inviteDoc.getString("role") ?: "parent/guardian"
        val schoolId = inviteDoc.getString("schoolId") ?: ""
        val grade = inviteDoc.getString("grade") ?: ""
        val studentId = inviteDoc.getString("studentId")
        val email = user.email ?: ""
        
        val isStaff = role != "parent/guardian" && role != "student"

        try {
            user.updateProfile(userProfileChangeRequest { displayName = name }).await()

            val userData = mutableMapOf(
                "uid" to user.uid,
                "name" to name,
                "email" to email,
                "role" to role,
                "schoolId" to schoolId,
                "grade" to grade,
                "isStaff" to isStaff,
                "createdAt" to System.currentTimeMillis()
            )
            studentId?.let { userData["studentId"] = it }

            db.collection("users").document(user.uid).set(userData, SetOptions.merge()).await()
            inviteDoc.reference.delete().await()

            _authState.value = AuthState.Success(user, role, isStaff = isStaff, schoolId = schoolId, studentId = studentId)
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Profile setup failed: ${e.localizedMessage}")
        }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }
}
