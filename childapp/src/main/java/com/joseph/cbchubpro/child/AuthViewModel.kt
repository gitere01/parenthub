package com.joseph.cbchubpro.child

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    fun signOut() {
        auth.signOut()
    }
}
