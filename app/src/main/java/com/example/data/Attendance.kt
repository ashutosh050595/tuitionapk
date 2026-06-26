package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [Index(value = ["studentId", "date"], unique = true)]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val date: String, // YYYY-MM-DD
    val status: String, // "present" | "absent" | "leave"
    val notes: String? = null
)
