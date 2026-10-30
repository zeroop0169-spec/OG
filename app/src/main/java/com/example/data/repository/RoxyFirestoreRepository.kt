package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.FirestoreHabit
import com.example.data.firestore.FirestoreMemory
import com.example.data.firestore.FirestoreNote
import com.example.data.firestore.UserProfile
import com.example.data.local.entity.NoteEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RoxyFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // Convenience constructor: resolves named database ID from string resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    fun requireUserId(): String {
        return auth.currentUser?.uid ?: error("User must be authenticated for Firestore operations")
    }

    // ----------------------------------------------------
    // User Profile
    // ----------------------------------------------------
    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(UserProfile::class.java)
                docRef.set(
                    profile.copy(
                        userId = uid,
                        createdAt = existing?.createdAt,
                        updatedAt = null
                    )
                ).await()
            } else {
                docRef.set(
                    profile.copy(
                        userId = uid,
                        createdAt = null,
                        updatedAt = null
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        val docRef = db.collection("users").document(userId)
        return try {
            val snapshot = docRef.get().await()
            snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    // ----------------------------------------------------
    // Notes Collection
    // ----------------------------------------------------
    fun observeNotes(userId: String): Flow<List<FirestoreNote>> = callbackFlow {
        val collectionRef = db.collection("users").document(userId).collection("notes")
        val listener: ListenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collectionRef.path)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val notes = snapshot.toObjects(FirestoreNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                trySend(notes.sortedByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) })
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveNote(userId: String, note: FirestoreNote) {
        val noteId = if (note.id.isNotBlank()) note.id else UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId).collection("notes").document(noteId)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(FirestoreNote::class.java)
                requireNotNull(existing?.createdAt) { "Existing note createdAt must not be null" }
                docRef.set(
                    note.copy(
                        id = noteId,
                        userId = userId,
                        createdAt = existing.createdAt,
                        updatedAt = null
                    )
                ).await()
            } else {
                docRef.set(
                    note.copy(
                        id = noteId,
                        userId = userId,
                        createdAt = null,
                        updatedAt = null
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteNote(userId: String, noteId: String) {
        val docRef = db.collection("users").document(userId).collection("notes").document(noteId)
        try {
            docRef.delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // ----------------------------------------------------
    // Habits Collection
    // ----------------------------------------------------
    fun observeHabits(userId: String): Flow<List<FirestoreHabit>> = callbackFlow {
        val collectionRef = db.collection("users").document(userId).collection("habits")
        val listener: ListenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collectionRef.path)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val habits = snapshot.toObjects(FirestoreHabit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                trySend(habits.sortedByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) })
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveHabit(userId: String, habit: FirestoreHabit) {
        val habitId = if (habit.id.isNotBlank()) habit.id else UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId).collection("habits").document(habitId)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(FirestoreHabit::class.java)
                requireNotNull(existing?.createdAt) { "Existing habit createdAt must not be null" }
                docRef.set(
                    habit.copy(
                        id = habitId,
                        userId = userId,
                        createdAt = existing.createdAt,
                        updatedAt = null
                    )
                ).await()
            } else {
                docRef.set(
                    habit.copy(
                        id = habitId,
                        userId = userId,
                        createdAt = null,
                        updatedAt = null
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // ----------------------------------------------------
    // Memories Collection
    // ----------------------------------------------------
    fun observeMemories(userId: String): Flow<List<FirestoreMemory>> = callbackFlow {
        val collectionRef = db.collection("users").document(userId).collection("memories")
        val listener: ListenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, collectionRef.path)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val memories = snapshot.toObjects(FirestoreMemory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                trySend(memories.sortedByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) })
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveMemory(userId: String, memory: FirestoreMemory) {
        val memoryId = if (memory.id.isNotBlank()) memory.id else UUID.randomUUID().toString()
        val docRef = db.collection("users").document(userId).collection("memories").document(memoryId)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(FirestoreMemory::class.java)
                requireNotNull(existing?.createdAt) { "Existing memory createdAt must not be null" }
                docRef.set(
                    memory.copy(
                        id = memoryId,
                        userId = userId,
                        createdAt = existing.createdAt,
                        updatedAt = null
                    )
                ).await()
            } else {
                docRef.set(
                    memory.copy(
                        id = memoryId,
                        userId = userId,
                        createdAt = null,
                        updatedAt = null
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // ----------------------------------------------------
    // Local to Cloud Sync
    // ----------------------------------------------------
    suspend fun syncLocalNotesToCloud(userId: String, localNotes: List<NoteEntity>) {
        localNotes.forEach { note ->
            val noteId = "note_${note.id}"
            saveNote(
                userId = userId,
                note = FirestoreNote(
                    id = noteId,
                    userId = userId,
                    title = note.title,
                    content = note.content,
                    category = note.tags.ifBlank { "general" }
                )
            )
        }
    }
}
