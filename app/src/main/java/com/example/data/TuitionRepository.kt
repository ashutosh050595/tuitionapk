package com.example.data

import com.example.network.BackupRecord
import com.example.network.NetworkClient
import com.example.network.ResendEmailRequest
import com.example.network.ResendAttachment
import com.example.network.ConfigAndNotifications
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TuitionRepository(private val tuitionDao: TuitionDao) {
    val allStudents: Flow<List<Student>> = tuitionDao.getAllActiveAndInactiveStudents()
    val allTransactions: Flow<List<FeeTransaction>> = tuitionDao.getAllTransactions()
    val appConfig: Flow<AppConfig?> = tuitionDao.getAppConfig()
    val allNotifications: Flow<List<AppNotification>> = tuitionDao.getAllNotifications()

    fun getNotificationsForStudent(studentId: Long): Flow<List<AppNotification>> = 
        tuitionDao.getNotificationsForStudent(studentId)

    suspend fun insertNotification(notification: AppNotification): Long = 
        tuitionDao.insertNotification(notification)

    suspend fun getStudentById(id: Long): Student? = tuitionDao.getStudentById(id)
    suspend fun insertStudent(student: Student): Long = tuitionDao.insertStudent(student)
    suspend fun updateStudent(student: Student) = tuitionDao.updateStudent(student)

    fun getAttendanceForDate(date: String): Flow<List<Attendance>> = tuitionDao.getAttendanceForDate(date)
    fun getAttendanceForStudent(studentId: Long): Flow<List<Attendance>> = tuitionDao.getAttendanceForStudent(studentId)
    suspend fun insertAttendance(attendance: Attendance) = tuitionDao.insertAttendance(attendance)
    suspend fun insertAttendanceList(attendanceList: List<Attendance>) = tuitionDao.insertAttendanceList(attendanceList)
    suspend fun deleteAttendance(studentId: Long, date: String) = tuitionDao.deleteAttendance(studentId, date)

    fun getTransactionsForStudent(studentId: Long): Flow<List<FeeTransaction>> = tuitionDao.getTransactionsForStudent(studentId)
    suspend fun getTransactionById(id: Long): FeeTransaction? = tuitionDao.getTransactionById(id)
    suspend fun insertTransaction(transaction: FeeTransaction): Long = tuitionDao.insertTransaction(transaction)

    suspend fun getAppConfigDirect(): AppConfig? = tuitionDao.getAppConfigDirect()
    suspend fun insertAppConfig(config: AppConfig) = tuitionDao.insertAppConfig(config)

    // Supabase Cloud Backup & Sync Flow
    suspend fun performSupabaseBackup(email: String, passcode: String): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val supabaseService = NetworkClient.supabaseService 
            ?: return@withContext Result.failure(Exception("Supabase service is not configured. Please verify your Supabase URL in environment variables."))
        
        val apiKey = NetworkClient.getSupabaseKey()
        if (apiKey.isEmpty() || apiKey.startsWith("your-supabase")) {
            return@withContext Result.failure(Exception("Supabase anon key is missing or is set to a placeholder."))
        }

        try {
            val students = tuitionDao.getAllStudentsDirect()
            val attendance = tuitionDao.getAllAttendanceDirect()
            val transactions = tuitionDao.getAllTransactionsDirect()
            val config = tuitionDao.getAppConfigDirect()
            val notifications = tuitionDao.getAllNotificationsDirect()

            val moshi = NetworkClient.moshi
            val studentAdapter = moshi.adapter<List<Student>>(Types.newParameterizedType(List::class.java, Student::class.java))
            val attendanceAdapter = moshi.adapter<List<Attendance>>(Types.newParameterizedType(List::class.java, Attendance::class.java))
            val transactionAdapter = moshi.adapter<List<FeeTransaction>>(Types.newParameterizedType(List::class.java, FeeTransaction::class.java))
            val wrapperAdapter = moshi.adapter(ConfigAndNotifications::class.java)

            val studentsJson = studentAdapter.toJson(students)
            val attendanceJson = attendanceAdapter.toJson(attendance)
            val transactionsJson = transactionAdapter.toJson(transactions)
            
            val wrapperObj = ConfigAndNotifications(config = config, notifications = notifications)
            val configJson = wrapperAdapter.toJson(wrapperObj)

            val backupTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

            val record = BackupRecord(
                email = email.trim().lowercase(),
                passcode = passcode,
                tutor_name = config?.tutorName,
                tuition_name = config?.tuitionName,
                last_backup_time = backupTime,
                students_json = studentsJson,
                attendance_json = attendanceJson,
                transactions_json = transactionsJson,
                config_json = configJson
            )

            val authHeader = "Bearer $apiKey"
            val response = supabaseService.upsertBackup(apiKey, authHeader, listOf(record))
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown Supabase API Error"
                Result.failure(Exception("Supabase Backup Failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun performSupabaseRestore(email: String, passcode: String): Result<Boolean> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val supabaseService = NetworkClient.supabaseService 
            ?: return@withContext Result.failure(Exception("Supabase service is not configured. Please verify your Supabase URL in environment variables."))
        
        val apiKey = NetworkClient.getSupabaseKey()
        if (apiKey.isEmpty() || apiKey.startsWith("your-supabase")) {
            return@withContext Result.failure(Exception("Supabase anon key is missing or is set to a placeholder."))
        }

        try {
            val authHeader = "Bearer $apiKey"
            val emailQuery = "eq.${email.trim().lowercase()}"
            val response = supabaseService.getBackup(apiKey, authHeader, emailQuery)

            if (response.isSuccessful) {
                val backups = response.body()
                if (backups.isNullOrEmpty()) {
                    return@withContext Result.success(false) // No backup found
                }

                val record = backups.first()
                if (record.passcode != passcode) {
                    return@withContext Result.failure(Exception("Incorrect passcode! Please provide the correct passcode for this backup account."))
                }

                val moshi = NetworkClient.moshi
                val studentAdapter = moshi.adapter<List<Student>>(Types.newParameterizedType(List::class.java, Student::class.java))
                val attendanceAdapter = moshi.adapter<List<Attendance>>(Types.newParameterizedType(List::class.java, Attendance::class.java))
                val transactionAdapter = moshi.adapter<List<FeeTransaction>>(Types.newParameterizedType(List::class.java, FeeTransaction::class.java))
                val wrapperAdapter = moshi.adapter(ConfigAndNotifications::class.java)
                val configAdapter = moshi.adapter(AppConfig::class.java)

                val students = record.students_json?.let { studentAdapter.fromJson(it) } ?: emptyList()
                val attendance = record.attendance_json?.let { attendanceAdapter.fromJson(it) } ?: emptyList()
                val transactions = record.transactions_json?.let { transactionAdapter.fromJson(it) } ?: emptyList()
                
                var config: AppConfig? = null
                var restoredNotifications: List<AppNotification> = emptyList()

                record.config_json?.let { json ->
                    try {
                        val wrapper = wrapperAdapter.fromJson(json)
                        if (wrapper != null) {
                            config = wrapper.config
                            restoredNotifications = wrapper.notifications
                        }
                    } catch (e: Exception) {
                        try {
                            config = configAdapter.fromJson(json)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }

                // Clear current local databases
                tuitionDao.clearAllStudents()
                tuitionDao.clearAllAttendance()
                tuitionDao.clearAllTransactions()
                tuitionDao.clearAppConfig()
                tuitionDao.clearAllNotifications()

                // Insert restored databases
                if (students.isNotEmpty()) tuitionDao.insertStudentsList(students)
                if (attendance.isNotEmpty()) tuitionDao.insertAttendanceList(attendance)
                if (transactions.isNotEmpty()) tuitionDao.insertTransactionsList(transactions)
                if (config != null) tuitionDao.insertAppConfig(config)
                if (restoredNotifications.isNotEmpty()) {
                    for (notif in restoredNotifications) {
                        tuitionDao.insertNotification(notif)
                    }
                }

                Result.success(true)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown Supabase API Error"
                Result.failure(Exception("Supabase Restore Failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Resend Email Helper
    suspend fun sendResendEmail(
        toEmail: String,
        subject: String,
        htmlBody: String,
        attachments: List<ResendAttachment>? = null
    ): Result<String> {
        val resendService = NetworkClient.resendService
            ?: return Result.failure(Exception("Resend service is not initialized."))
        
        val apiKey = NetworkClient.getResendKey()
        if (apiKey.isEmpty() || apiKey.startsWith("re_your")) {
            return Result.failure(Exception("Resend API key is missing or is set to a placeholder."))
        }

        val fromEmail = NetworkClient.getResendFromEmail()
        if (fromEmail.isEmpty()) {
            return Result.failure(Exception("Resend 'From' email address is missing in configuration."))
        }

        return try {
            val request = ResendEmailRequest(
                from = fromEmail,
                to = listOf(toEmail.trim()),
                subject = subject,
                html = htmlBody,
                attachments = attachments
            )
            val authHeader = "Bearer $apiKey"
            val response = resendService.sendEmail(authHeader, request)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.id != null) {
                    Result.success(body.id)
                } else {
                    Result.failure(Exception("Resend sent email but returned an empty response body."))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown Resend API error"
                Result.failure(Exception("Resend Email Failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pingSupabase(): Result<Unit> {
        val supabaseService = NetworkClient.supabaseService 
            ?: return Result.failure(Exception("Supabase service is not configured."))
        
        val apiKey = NetworkClient.getSupabaseKey()
        if (apiKey.isEmpty() || apiKey.startsWith("your-supabase")) {
            return Result.failure(Exception("Supabase key is missing."))
        }

        return try {
            val authHeader = "Bearer $apiKey"
            val response = supabaseService.pingSupabase(apiKey, authHeader)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown Supabase API Error"
                Result.failure(Exception("Supabase Ping Failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
