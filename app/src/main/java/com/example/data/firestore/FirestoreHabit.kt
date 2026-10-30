package com.example.data.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class FirestoreHabit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val streak: Long = 0L,
    val frequency: String = "Daily",
    val lastCompletedDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
