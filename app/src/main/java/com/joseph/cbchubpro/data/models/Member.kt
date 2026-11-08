package com.joseph.cbchubpro.data.models

/**
 * Represents a member of a family.
 * Stored in the 'families/{familyId}/members' subcollection.
 */
data class Member(
    val role: String = "" // "admin" or "guardian"
)
