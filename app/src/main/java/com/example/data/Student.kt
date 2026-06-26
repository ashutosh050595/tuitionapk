package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val guardianName: String,
    val parentEmail: String,
    val phone: String,
    val whatsapp: String,
    val address: String,
    val className: String, // e.g. "Class 9"
    val subjects: List<String>,
    val feePerMonth: Double,
    val joiningDate: String, // YYYY-MM-DD
    val status: String = "active", // "active" | "inactive" | "removed" (soft delete)
    val photoUrl: String? = null,
    val notes: String? = null
)
