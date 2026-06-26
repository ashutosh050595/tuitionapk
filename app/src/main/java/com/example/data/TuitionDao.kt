package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TuitionDao {
    // Student Queries
    @Query("SELECT * FROM students WHERE status != 'removed' ORDER BY name ASC")
    fun getAllActiveAndInactiveStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getStudentById(id: Long): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Update
    suspend fun updateStudent(student: Student)

    // Attendance Queries
    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun getAttendanceForStudent(studentId: Long): Flow<List<Attendance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceList(attendanceList: List<Attendance>)

    @Query("DELETE FROM attendance WHERE studentId = :studentId AND date = :date")
    suspend fun deleteAttendance(studentId: Long, date: String)

    // Fee Transaction Queries
    @Query("SELECT * FROM fee_transactions ORDER BY paymentDate DESC, id DESC")
    fun getAllTransactions(): Flow<List<FeeTransaction>>

    @Query("SELECT * FROM fee_transactions WHERE studentId = :studentId ORDER BY paymentDate DESC")
    fun getTransactionsForStudent(studentId: Long): Flow<List<FeeTransaction>>

    @Query("SELECT * FROM fee_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): FeeTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FeeTransaction): Long

    // App Config Queries
    @Query("SELECT * FROM app_config WHERE id = 1")
    fun getAppConfig(): Flow<AppConfig?>

    @Query("SELECT * FROM app_config WHERE id = 1")
    suspend fun getAppConfigDirect(): AppConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppConfig(config: AppConfig)

    // Direct Sync / Backup Queries
    @Query("SELECT * FROM students")
    suspend fun getAllStudentsDirect(): List<Student>

    @Query("SELECT * FROM attendance")
    suspend fun getAllAttendanceDirect(): List<Attendance>

    @Query("SELECT * FROM fee_transactions")
    suspend fun getAllTransactionsDirect(): List<FeeTransaction>

    @Query("DELETE FROM students")
    suspend fun clearAllStudents()

    @Query("DELETE FROM attendance")
    suspend fun clearAllAttendance()

    @Query("DELETE FROM fee_transactions")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM app_config")
    suspend fun clearAppConfig()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentsList(students: List<Student>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsList(transactions: List<FeeTransaction>)

    // Notification Queries
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Query("SELECT * FROM notifications WHERE studentId IS NULL OR studentId = :studentId ORDER BY timestamp DESC")
    fun getNotificationsForStudent(studentId: Long): Flow<List<AppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAllNotificationsDirect(): List<AppNotification>
}
