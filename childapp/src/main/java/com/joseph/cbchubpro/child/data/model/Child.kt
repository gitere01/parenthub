package com.joseph.cbchubpro.child.data.model

data class Child(
    val id: String = "",
    val name: String = "",
    val gradeLevel: String = "",
    val pairingCode: String = "",
    val paired: Boolean = false,
    val isOnline: Boolean = false
)
