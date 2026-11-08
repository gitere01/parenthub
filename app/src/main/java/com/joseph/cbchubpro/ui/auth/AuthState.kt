package com.joseph.cbchubpro.ui.auth

import com.google.firebase.auth.FirebaseUser

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(
        val user: FirebaseUser,
        val role: String,
        val isStaff: Boolean = false,
        val schoolId: String? = null,
        val studentId: String? = null
    ) : AuthState()
    object SignUpSuccess : AuthState()
    data class Error(val message: String) : AuthState()
}
