package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long?, // null means broadcast (for all students), otherwise specific to a student
    val title: String,
    val message: String,
    val timestamp: Long,
    val senderName: String,
    val isRead: Boolean = false
)
