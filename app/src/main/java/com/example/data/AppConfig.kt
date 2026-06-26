package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey val id: Int = 1,
    val tutorName: String,
    val tuitionName: String,
    val address: String,
    val phone: String,
    val email: String,
    val receiptPrefix: String = "RCP",
    val nextReceiptNo: Int = 1
)
