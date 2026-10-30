package com.example.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class FirestoreMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val summary: String = "",
    val keyFacts: String = "",
    val mood: String = "Sassy",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
