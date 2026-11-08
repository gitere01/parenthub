package com.joseph.cbchubpro.data.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a family circle in Firestore.
 * Stored in the 'families' collection.
 */
data class Family(
    val name: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    // Invite code for adults
    val inviteCode: String? = null,
    val inviteExpiry: Date? = null
)
