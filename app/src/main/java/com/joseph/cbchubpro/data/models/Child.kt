package com.joseph.cbchubpro.data.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Represents a child device in a family.
 * Stored in the 'families/{familyId}/children' subcollection.
 */
data class Child(
    val name: String = "",
    val pairingCode: String? = null,
    val paired: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
    // Android device ID
    val deviceId: String? = null,
    @ServerTimestamp val pairedAt: Date? = null
)
