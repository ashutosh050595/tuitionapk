package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fee_transactions")
data class FeeTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val receiptNumber: String,
    val amount: Double,
    val monthsPaid: List<String>, // e.g. ["June 2026", "July 2026"]
    val paymentDate: String, // YYYY-MM-DD
    val paymentMode: String, // "cash" | "upi" | "bank_transfer" | "cheque"
    val transactionRef: String? = null,
    val receiptUrl: String? = null,
    val emailSent: Boolean = false,
    val notes: String? = null
)
