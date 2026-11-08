package com.joseph.cbchubpro.child.data.repository

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.joseph.cbchubpro.child.data.model.Child
import kotlinx.coroutines.tasks.await

class PairingRepository {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    suspend fun pairWithParent(email: String): Result<Child> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))

            val query = db.collectionGroup("children")
                .whereEqualTo("email", email)
                .whereEqualTo("paired", false)
                .limit(1)
                .get().await()

            if (query.isEmpty) {
                return Result.failure(Exception("No pending invitation found for this email."))
            }

            val childDoc = query.documents.first()
            val updates = hashMapOf(
                "paired" to true,
                "pairedAt" to FieldValue.serverTimestamp(),
                "userId" to currentUser.uid
            )

            childDoc.reference.update(updates as Map<String, Any?>).await()

            val updatedDoc = childDoc.reference.get().await()
            val child = updatedDoc.toObject(Child::class.java)
            if (child != null) {
                Result.success(child)
            } else {
                Result.failure(Exception("Failed to retrieve child data after pairing."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
