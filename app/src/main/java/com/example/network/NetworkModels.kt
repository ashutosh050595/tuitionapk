package com.example.network

import com.squareup.moshi.JsonClass
import com.example.data.AppConfig
import com.example.data.AppNotification

@JsonClass(generateAdapter = true)
data class ConfigAndNotifications(
    val config: AppConfig?,
    val notifications: List<AppNotification> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupRecord(
    val email: String,
    val passcode: String,
    val tutor_name: String?,
    val tuition_name: String?,
    val last_backup_time: String?,
    val students_json: String?,
    val attendance_json: String?,
    val transactions_json: String?,
    val config_json: String?
)

@JsonClass(generateAdapter = true)
data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String
)

@JsonClass(generateAdapter = true)
data class ResendEmailResponse(
    val id: String?
)
