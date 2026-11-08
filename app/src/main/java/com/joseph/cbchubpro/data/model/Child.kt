package com.joseph.cbchubpro.data.model

data class Child(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val pairingCode: String = "",
    val paired: Boolean = false,
    val isOnline: Boolean = false
)
