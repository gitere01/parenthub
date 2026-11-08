package com.joseph.cbchubpro.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.joseph.cbchubpro.data.model.Child
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.google.firebase.firestore.snapshots

class FamilyRepository {

    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    /**
     * Gets the family ID for the current user.
     */
    suspend fun getFamilyId(): String? {
        val currentUser = auth.currentUser ?: return null
        val userDoc = db.collection("users").document(currentUser.uid).get().await()
        return userDoc.getString("familyId")
    }

    /**
     * Creates a new family document and adds the current user as an admin.
     */
    suspend fun createFamily(name: String): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            val familyId = db.collection("families").document().id

            val family = hashMapOf(
                "name" to name,
                "createdBy" to currentUser.uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val member = hashMapOf("role" to "admin")

            db.runBatch { batch ->
                val familyRef = db.collection("families").document(familyId)
                val memberRef = familyRef.collection("members").document(currentUser.uid)
                batch.set(familyRef, family)
                batch.set(memberRef, member)
                batch.update(db.collection("users").document(currentUser.uid), "familyId", familyId)
            }.await()
            Result.success(familyId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates an invite link for an adult using Android App Links.
     */
    suspend fun generateInviteLink(familyId: String): Result<String> {
        return try {
            val inviteCode = (1..6)
                .map { "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }
                .joinToString("")
            val expiry = Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000) // 24 hours

            db.collection("families").document(familyId)
                .update(mapOf("inviteCode" to inviteCode, "inviteExpiry" to expiry))
                .await()

            // Using standard Android App Link URL instead of Firebase Dynamic Links
            val inviteUrl = "https://cbchubpro.com/invite?familyId=$familyId&code=$inviteCode"
            Result.success(inviteUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a pairing code for a child.
     */
    suspend fun generatePairingCode(familyId: String): Result<String> {
        return try {
            val pairingCode = (1..6)
                .map { "0123456789".random() }
                .joinToString("")
            
            db.collection("families").document(familyId)
                .update("pairingCode", pairingCode)
                .await()
            
            Result.success(pairingCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Allows the current user to join a family using an invite code.
     */
    suspend fun joinFamily(inviteCode: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))
            val now = Date()

            val query = db.collection("families")
                .whereEqualTo("inviteCode", inviteCode)
                .whereGreaterThan("inviteExpiry", now)
                .limit(1)
                .get().await()

            if (query.isEmpty) {
                return Result.failure(Exception("Invalid or expired invite code."))
            }

            val familyDoc = query.documents.first()
            val familyId = familyDoc.id
            val member = hashMapOf("role" to "guardian")

            familyDoc.reference.collection("members").document(currentUser.uid)
                .set(member).await()

            // Invalidate the invite code
            familyDoc.reference.update("inviteCode", null, "inviteExpiry", null).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adds a new child document with a generated pairing code.
     */
    suspend fun inviteChild(familyId: String, childName: String, childEmail: String): Result<Unit> {
        return try {
            val childId = db.collection("families").document(familyId).collection("children").document().id

            val child = Child(
                id = childId,
                name = childName,
                email = childEmail,
                paired = false
            )

            db.collection("families").document(familyId).collection("children").document(childId)
                .set(child).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets a real-time list of children for a given family.
     */
    fun getChildren(familyId: String): Flow<List<Child>> {
        return db.collection("families").document(familyId).collection("children").snapshots().map { snapshot ->
            snapshot.documents.map { document ->
                document.toObject(Child::class.java)!!.copy(id = document.id)
            }
        }
    }

    /**
     * Deletes a child document from a family.
     */
    suspend fun deleteChild(familyId: String, childId: String): Result<Unit> {
        return try {
            db.collection("families").document(familyId).collection("children").document(childId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
