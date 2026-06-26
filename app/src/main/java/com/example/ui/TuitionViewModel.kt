package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

data class StudentDues(
    val student: Student,
    val joiningDate: String,
    val monthsSinceJoining: List<String>,
    val monthsPaid: List<String>,
    val unpaidMonths: List<String>,
    val dueAmount: Double
)

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val error: String) : SyncState()
}

sealed class EmailState {
    object Idle : EmailState()
    object Loading : EmailState()
    data class Success(val message: String) : EmailState()
    data class Error(val error: String) : EmailState()
}

sealed class UserSession {
    object Splash : UserSession()
    object LoggedOut : UserSession()
    data class TeacherSession(val name: String) : UserSession()
    data class StudentSession(val student: Student) : UserSession()
}

class TuitionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TuitionRepository
    private val sharedPrefs = application.getSharedPreferences("tuition_backup_prefs", Context.MODE_PRIVATE)
    
    private val _autoBackupEnabled = MutableStateFlow(sharedPrefs.getBoolean("auto_backup_enabled", true))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _antiPauseStatus = MutableStateFlow("Initializing Keep-Alive...")
    val antiPauseStatus: StateFlow<String> = _antiPauseStatus.asStateFlow()
    
    val allStudents: StateFlow<List<Student>>
    val allTransactions: StateFlow<List<FeeTransaction>>
    val appConfig: StateFlow<AppConfig?>
    val allNotifications: StateFlow<List<AppNotification>>

    private val _currentSession = MutableStateFlow<UserSession>(UserSession.Splash)
    val currentSession: StateFlow<UserSession> = _currentSession.asStateFlow()

    private val _activeHeadsUpNotification = MutableStateFlow<AppNotification?>(null)
    val activeHeadsUpNotification: StateFlow<AppNotification?> = _activeHeadsUpNotification.asStateFlow()

    private val _backupState = MutableStateFlow<SyncState>(SyncState.Idle)
    val backupState: StateFlow<SyncState> = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow<SyncState>(SyncState.Idle)
    val restoreState: StateFlow<SyncState> = _restoreState.asStateFlow()

    private val _emailState = MutableStateFlow<EmailState>(EmailState.Idle)
    val emailState: StateFlow<EmailState> = _emailState.asStateFlow()
    
    private val _selectedDate = MutableStateFlow(getTodayDateStr())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()
    
    private val _currentDate = MutableStateFlow(getTodayDateStr())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()
    
    val attendanceForDate: StateFlow<List<Attendance>>
    
    init {
        val database = TuitionDatabase.getDatabase(application)
        repository = TuitionRepository(database.tuitionDao())
        
        allStudents = repository.allStudents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        allTransactions = repository.allTransactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        appConfig = repository.appConfig.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        allNotifications = repository.allNotifications.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        attendanceForDate = _selectedDate
            .flatMapLatest { date -> repository.getAttendanceForDate(date) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
        
        // Seed default config if empty
        viewModelScope.launch {
            val existing = repository.getAppConfigDirect()
            if (existing == null) {
                repository.insertAppConfig(
                    AppConfig(
                        id = 1,
                        tutorName = "Ashutosh",
                        tuitionName = "Ashutosh Tuition Class",
                        address = "A-24, Sector 15, Dwarka, New Delhi - 110075",
                        phone = "+91 98765 43210",
                        email = "aditi.sharma@excelacademy.com",
                        receiptPrefix = "EXT",
                        nextReceiptNo = 1001
                    )
                )
            }
        }

        // Run Anti-Pause keep-alive on startup
        pingSupabaseDatabase()
    }
    
    // Calculated Dues Flow
    val studentDuesList: StateFlow<List<StudentDues>> = combine(allStudents, allTransactions, currentDate) { students, transactions, curDate ->
        students.map { student ->
            val studentTransactions = transactions.filter { it.studentId == student.id }
            val totalPaidAmount = studentTransactions.sumOf { it.amount }
            
            val monthsSinceJoin = getMonthsSinceJoining(student.joiningDate, curDate)
            val totalDueUpToNow = monthsSinceJoin.size * student.feePerMonth
            val dueAmt = if (student.status == "inactive") 0.0 else maxOf(0.0, totalDueUpToNow - totalPaidAmount)
            
            // Attribute to months paid: each block of feePerMonth represents 1 fully paid month
            val numMonthsPaid = if (student.feePerMonth > 0) (totalPaidAmount / student.feePerMonth).toInt() else 0
            val paidMonths = monthsSinceJoin.take(minOf(numMonthsPaid, monthsSinceJoin.size))
            val unpaid = monthsSinceJoin.drop(minOf(numMonthsPaid, monthsSinceJoin.size))

            StudentDues(
                student = student,
                joiningDate = student.joiningDate,
                monthsSinceJoining = monthsSinceJoin,
                monthsPaid = paidMonths,
                unpaidMonths = unpaid,
                dueAmount = dueAmt
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI actions
    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun addStudent(
        name: String,
        guardianName: String,
        parentEmail: String,
        phone: String,
        whatsapp: String,
        address: String,
        className: String,
        subjects: List<String>,
        feePerMonth: Double,
        joiningDate: String,
        notes: String?
    ) {
        viewModelScope.launch {
            val student = Student(
                name = name,
                guardianName = guardianName,
                parentEmail = parentEmail,
                phone = phone,
                whatsapp = whatsapp,
                address = address,
                className = className,
                subjects = subjects,
                feePerMonth = feePerMonth,
                joiningDate = joiningDate,
                status = "active",
                notes = notes
            )
            repository.insertStudent(student)
            triggerAutoBackup()
        }
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
            triggerAutoBackup()
        }
    }

    fun removeStudent(studentId: Long) {
        viewModelScope.launch {
            val student = repository.getStudentById(studentId)
            if (student != null) {
                repository.updateStudent(student.copy(status = "removed"))
                triggerAutoBackup()
            }
        }
    }

    fun toggleStudentStatus(studentId: Long) {
        viewModelScope.launch {
            val student = repository.getStudentById(studentId)
            if (student != null) {
                val newStatus = if (student.status == "active") "inactive" else "active"
                repository.updateStudent(student.copy(status = newStatus))
                triggerAutoBackup()
            }
        }
    }

    fun markAttendance(studentId: Long, date: String, status: String, notes: String? = null) {
        viewModelScope.launch {
            val attendance = Attendance(
                studentId = studentId,
                date = date,
                status = status,
                notes = notes
            )
            repository.insertAttendance(attendance)
            triggerAutoBackup()
        }
    }

    fun markBulkAttendance(date: String, attendanceList: List<Attendance>) {
        viewModelScope.launch {
            repository.insertAttendanceList(attendanceList)
            triggerAutoBackup()
        }
    }

    suspend fun depositFee(
        studentId: Long,
        amount: Double,
        months: List<String>,
        paymentMode: String,
        ref: String?,
        notes: String?
    ): Long {
        val config = repository.getAppConfigDirect() ?: AppConfig(
            id = 1,
            tutorName = "Ashutosh",
            tuitionName = "Ashutosh Tuition Class",
            address = "A-24, Sector 15, Dwarka, New Delhi - 110075",
            phone = "+91 98765 43210",
            email = "aditi.sharma@excelacademy.com",
            receiptPrefix = "EXT",
            nextReceiptNo = 1001
        )
        
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val receiptNoStr = "${config.receiptPrefix}-$year-${String.format(Locale.US, "%04d", config.nextReceiptNo)}"
        
        val transaction = FeeTransaction(
            studentId = studentId,
            receiptNumber = receiptNoStr,
            amount = amount,
            monthsPaid = months,
            paymentDate = getTodayDateStr(),
            paymentMode = paymentMode,
            transactionRef = ref,
            emailSent = true, // simulated true
            notes = notes
        )
        
        val newTxId = repository.insertTransaction(transaction)
        
        // Update AppConfig next receipt no
        repository.insertAppConfig(
            config.copy(nextReceiptNo = config.nextReceiptNo + 1)
        )
        
        triggerAutoBackup()
        return newTxId
    }

    fun updateConfig(
        tutorName: String,
        tuitionName: String,
        address: String,
        phone: String,
        email: String,
        receiptPrefix: String
    ) {
        viewModelScope.launch {
            val current = repository.getAppConfigDirect()
            val updated = current?.copy(
                tutorName = tutorName,
                tuitionName = tuitionName,
                address = address,
                phone = phone,
                email = email,
                receiptPrefix = receiptPrefix
            ) ?: AppConfig(
                id = 1,
                tutorName = tutorName,
                tuitionName = tuitionName,
                address = address,
                phone = phone,
                email = email,
                receiptPrefix = receiptPrefix,
                nextReceiptNo = 1001
            )
            repository.insertAppConfig(updated)
            triggerAutoBackup()
        }
    }

    fun getAttendanceForStudent(studentId: Long): Flow<List<Attendance>> {
        return repository.getAttendanceForStudent(studentId)
    }

    // Helper functions
    private fun getTodayDateStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Calendar.getInstance().time)
    }

    fun getMonthsSinceJoining(joiningDateStr: String, currentDateStr: String): List<String> {
        val monthsList = mutableListOf<String>()
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        try {
            val joinParts = joiningDateStr.split("-")
            if (joinParts.size < 2) return emptyList()
            val joinYear = joinParts[0].toIntOrNull() ?: return emptyList()
            val joinMonth = joinParts[1].toIntOrNull() ?: return emptyList()

            val curParts = currentDateStr.split("-")
            val curYear = if (curParts.isNotEmpty()) curParts[0].toIntOrNull() ?: 2026 else 2026
            val curMonth = if (curParts.size > 1) curParts[1].toIntOrNull() ?: 6 else 6

            var tempYear = joinYear
            var tempMonth = joinMonth

            while (tempYear < curYear || (tempYear == curYear && tempMonth <= curMonth)) {
                if (tempMonth in 1..12) {
                    monthsList.add("${monthNames[tempMonth - 1]} $tempYear")
                }
                tempMonth++
                if (tempMonth > 12) {
                    tempMonth = 1
                    tempYear++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return monthsList
    }

    fun convertNumberToWords(amount: Double): String {
        val num = amount.toLong()
        if (num == 0L) return "Zero Only"
        
        val units = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf(
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
        )

        fun helper(n: Long): String {
            return when {
                n < 20 -> units[n.toInt()]
                n < 100 -> tens[n.toInt() / 10] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
                n < 1000 -> units[n.toInt() / 100] + " Hundred" + (if (n % 100 != 0L) " and " + helper(n % 100) else "")
                n < 100000 -> helper(n / 1000) + " Thousand" + (if (n % 1000 != 0L) " " + helper(n % 1000) else "")
                else -> helper(n / 100000) + " Lakh" + (if (n % 100000 != 0L) " " + helper(n % 100000) else "")
            }
        }
        
        return "${helper(num)} Rupees Only"
    }

    fun seedSampleData() {
        viewModelScope.launch {
            // Seed Students
            val s1 = Student(
                id = 1,
                name = "Gautam Sharma",
                guardianName = "Suresh Sharma",
                parentEmail = "gautam.parent@gmail.com",
                phone = "+91 94412 34567",
                whatsapp = "+91 94412 34567",
                address = "Flat 102, Shanti Vihar, Dwarka, Delhi",
                className = "Class 9",
                subjects = listOf("Math", "Science"),
                feePerMonth = 1500.0,
                joiningDate = "2026-04-10",
                status = "active",
                notes = "Excellent student, needs occasional focus on chemistry formulas."
            )
            val s2 = Student(
                id = 2,
                name = "Aisha Khan",
                guardianName = "Imran Khan",
                parentEmail = "aisha.khan@hotmail.com",
                phone = "+91 98876 54321",
                whatsapp = "+91 98876 54321",
                address = "H.No 45, Gali 3, Jamia Nagar, Okhla, Delhi",
                className = "Class 10",
                subjects = listOf("English", "History"),
                feePerMonth = 1800.0,
                joiningDate = "2026-03-01",
                status = "active",
                notes = "Very regular, scores well in English literature."
            )
            val s3 = Student(
                id = 3,
                name = "Rohan Verma",
                guardianName = "Rakesh Verma",
                parentEmail = "rohan.verma@yahoo.com",
                phone = "+91 87765 43210",
                whatsapp = "+91 87765 43210",
                address = "C-4, Sector 7, Rohini, Delhi",
                className = "Class 8",
                subjects = listOf("Math"),
                feePerMonth = 1200.0,
                joiningDate = "2026-05-15",
                status = "active",
                notes = "Struggles slightly with geometry, practicing theorems."
            )
            val s4 = Student(
                id = 4,
                name = "Priya Nair",
                guardianName = "K.G. Nair",
                parentEmail = "priya.nair@nairassociates.in",
                phone = "+91 95532 10987",
                whatsapp = "+91 95532 10987",
                address = "Pocket E-3, Sector 11, Dwarka, Delhi",
                className = "Class 12",
                subjects = listOf("Physics", "Chemistry"),
                feePerMonth = 2500.0,
                joiningDate = "2026-02-20",
                status = "active",
                notes = "Highly competitive student, aiming for JEE Main/NEET."
            )
            val s5 = Student(
                id = 5,
                name = "Kabir Singh",
                guardianName = "Jaspreet Singh",
                parentEmail = "kabir.singh@gmail.com",
                phone = "+91 92233 44556",
                whatsapp = "+91 92233 44556",
                address = "Block C, H-12, Vikas Puri, Delhi",
                className = "Class 11",
                subjects = listOf("Math"),
                feePerMonth = 2000.0,
                joiningDate = "2026-05-01",
                status = "inactive",
                notes = "On temporary leave due to sports camp."
            )

            repository.insertStudent(s1)
            repository.insertStudent(s2)
            repository.insertStudent(s3)
            repository.insertStudent(s4)
            repository.insertStudent(s5)

            // Seed Attendance
            val dates = listOf("2026-06-21", "2026-06-22", "2026-06-23", "2026-06-24", "2026-06-25")
            val attendanceRecords = mutableListOf<Attendance>()
            
            // Gautam Sharma: Present 4, Leave 1
            attendanceRecords.add(Attendance(studentId = 1, date = "2026-06-21", status = "present"))
            attendanceRecords.add(Attendance(studentId = 1, date = "2026-06-22", status = "present"))
            attendanceRecords.add(Attendance(studentId = 1, date = "2026-06-23", status = "leave", notes = "Family function"))
            attendanceRecords.add(Attendance(studentId = 1, date = "2026-06-24", status = "present"))
            attendanceRecords.add(Attendance(studentId = 1, date = "2026-06-25", status = "present"))

            // Aisha Khan: Present 4, Absent 1
            attendanceRecords.add(Attendance(studentId = 2, date = "2026-06-21", status = "present"))
            attendanceRecords.add(Attendance(studentId = 2, date = "2026-06-22", status = "absent", notes = "Sick"))
            attendanceRecords.add(Attendance(studentId = 2, date = "2026-06-23", status = "present"))
            attendanceRecords.add(Attendance(studentId = 2, date = "2026-06-24", status = "present"))
            attendanceRecords.add(Attendance(studentId = 2, date = "2026-06-25", status = "present"))

            // Rohan Verma: Present 5
            dates.forEach { d ->
                attendanceRecords.add(Attendance(studentId = 3, date = d, status = "present"))
            }

            // Priya Nair: Present 3, Absent 1, Leave 1
            attendanceRecords.add(Attendance(studentId = 4, date = "2026-06-21", status = "present"))
            attendanceRecords.add(Attendance(studentId = 4, date = "2026-06-22", status = "present"))
            attendanceRecords.add(Attendance(studentId = 4, date = "2026-06-23", status = "absent"))
            attendanceRecords.add(Attendance(studentId = 4, date = "2026-06-24", status = "leave"))
            attendanceRecords.add(Attendance(studentId = 4, date = "2026-06-25", status = "present"))

            repository.insertAttendanceList(attendanceRecords)

            // Seed Fee Transactions
            repository.insertTransaction(FeeTransaction(
                id = 101,
                studentId = 1,
                receiptNumber = "EXT-2026-1001",
                amount = 1500.0,
                monthsPaid = listOf("April 2026"),
                paymentDate = "2026-04-12",
                paymentMode = "cash",
                emailSent = true,
                notes = "First payment"
            ))
            repository.insertTransaction(FeeTransaction(
                id = 102,
                studentId = 1,
                receiptNumber = "EXT-2026-1002",
                amount = 1500.0,
                monthsPaid = listOf("May 2026"),
                paymentDate = "2026-05-10",
                paymentMode = "upi",
                transactionRef = "UPI9872513411",
                emailSent = true
            ))

            repository.insertTransaction(FeeTransaction(
                id = 103,
                studentId = 2,
                receiptNumber = "EXT-2026-1003",
                amount = 1800.0,
                monthsPaid = listOf("March 2026"),
                paymentDate = "2026-03-05",
                paymentMode = "cash",
                emailSent = true
            ))
            repository.insertTransaction(FeeTransaction(
                id = 104,
                studentId = 2,
                receiptNumber = "EXT-2026-1004",
                amount = 1800.0,
                monthsPaid = listOf("April 2026"),
                paymentDate = "2026-04-04",
                paymentMode = "bank_transfer",
                transactionRef = "TXN-998822",
                emailSent = true
            ))

            repository.insertTransaction(FeeTransaction(
                id = 105,
                studentId = 3,
                receiptNumber = "EXT-2026-1005",
                amount = 1200.0,
                monthsPaid = listOf("May 2026"),
                paymentDate = "2026-05-18",
                paymentMode = "cash",
                emailSent = true
            ))

            repository.insertTransaction(FeeTransaction(
                id = 106,
                studentId = 4,
                receiptNumber = "EXT-2026-1006",
                amount = 5000.0,
                monthsPaid = listOf("February 2026", "March 2026"),
                paymentDate = "2026-03-15",
                paymentMode = "upi",
                transactionRef = "UPI8812555",
                emailSent = true
            ))
            repository.insertTransaction(FeeTransaction(
                id = 107,
                studentId = 4,
                receiptNumber = "EXT-2026-1007",
                amount = 5000.0,
                monthsPaid = listOf("April 2026", "May 2026"),
                paymentDate = "2026-05-10",
                paymentMode = "upi",
                transactionRef = "UPI8819999",
                emailSent = true
            ))

            // Update app config next receipt number
            val config = repository.getAppConfigDirect()
            if (config != null) {
                repository.insertAppConfig(config.copy(nextReceiptNo = 1008))
            }
        }
    }

    // Supabase & Resend operations
    fun backupToSupabase(email: String, passcode: String) {
        viewModelScope.launch {
            _backupState.value = SyncState.Loading
            val result = repository.performSupabaseBackup(email, passcode)
            result.fold(
                onSuccess = {
                    _backupState.value = SyncState.Success("Data backed up successfully to Cloud!")
                },
                onFailure = {
                    _backupState.value = SyncState.Error(it.message ?: "Backup failed.")
                }
            )
        }
    }

    fun restoreFromSupabase(email: String, passcode: String) {
        viewModelScope.launch {
            _restoreState.value = SyncState.Loading
            val result = repository.performSupabaseRestore(email, passcode)
            result.fold(
                onSuccess = { found ->
                    if (found) {
                        _restoreState.value = SyncState.Success("Data restored successfully from Cloud!")
                    } else {
                        _restoreState.value = SyncState.Error("No backup found for this email address.")
                    }
                },
                onFailure = {
                    _restoreState.value = SyncState.Error(it.message ?: "Restore failed.")
                }
            )
        }
    }

    fun clearSyncStates() {
        _backupState.value = SyncState.Idle
        _restoreState.value = SyncState.Idle
        _emailState.value = EmailState.Idle
    }

    fun sendEmailReceipt(student: Student, transaction: FeeTransaction) {
        viewModelScope.launch {
            if (student.parentEmail.trim().isEmpty()) {
                _emailState.value = EmailState.Error("Student parent email is empty!")
                return@launch
            }
            _emailState.value = EmailState.Loading
            
            val subject = "Tuition Fee Receipt: ${transaction.receiptNumber}"
            val htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px; padding: 20px; color: #333;">
                    <div style="text-align: center; border-bottom: 2px solid #6750A4; padding-bottom: 15px; margin-bottom: 20px;">
                        <h2 style="color: #6750A4; margin: 0;">FEE PAYMENT RECEIPT</h2>
                        <p style="margin: 5px 0 0 0; font-size: 14px; color: #666;">Generated via Tuition Manager App</p>
                    </div>
                    
                    <div style="margin-bottom: 20px;">
                        <table style="width: 100%; border-collapse: collapse;">
                            <tr>
                                <td style="padding: 5px 0; font-weight: bold; width: 120px;">Receipt No:</td>
                                <td style="padding: 5px 0;">${transaction.receiptNumber}</td>
                            </tr>
                            <tr>
                                <td style="padding: 5px 0; font-weight: bold;">Date:</td>
                                <td style="padding: 5px 0;">${transaction.paymentDate}</td>
                            </tr>
                            <tr>
                                <td style="padding: 5px 0; font-weight: bold;">Student Name:</td>
                                <td style="padding: 5px 0;">${student.name}</td>
                            </tr>
                            <tr>
                                <td style="padding: 5px 0; font-weight: bold;">Class:</td>
                                <td style="padding: 5px 0;">${student.className}</td>
                            </tr>
                        </table>
                    </div>
                    
                    <div style="background-color: #f9f9f9; border-radius: 6px; padding: 15px; margin-bottom: 20px;">
                        <table style="width: 100%; border-collapse: collapse;">
                            <thead>
                                <tr style="border-bottom: 1px solid #ddd;">
                                    <th style="text-align: left; padding-bottom: 8px; font-weight: bold;">Description</th>
                                    <th style="text-align: right; padding-bottom: 8px; font-weight: bold;">Amount</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td style="padding: 10px 0;">Tuition Fees for Months: <strong style="color: #6750A4;">${transaction.monthsPaid.joinToString(", ")}</strong></td>
                                    <td style="text-align: right; padding: 10px 0; font-weight: bold;">₹${transaction.amount}</td>
                                </tr>
                                <tr style="border-top: 2px solid #6750A4;">
                                    <td style="padding: 10px 0; font-weight: bold;">TOTAL PAID:</td>
                                    <td style="text-align: right; padding: 10px 0; font-weight: bold; color: #2E7D32; font-size: 18px;">₹${transaction.amount}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    
                    <div style="margin-bottom: 20px; font-size: 13px; color: #555;">
                        <p><strong>Payment Mode:</strong> ${transaction.paymentMode.uppercase()}</p>
                        ${if (!transaction.transactionRef.isNullOrBlank()) "<p><strong>Reference / UPI ID:</strong> ${transaction.transactionRef}</p>" else ""}
                        ${if (!transaction.notes.isNullOrBlank()) "<p><strong>Notes:</strong> ${transaction.notes}</p>" else ""}
                    </div>
                    
                    <div style="border-top: 1px solid #eee; padding-top: 15px; text-align: center; font-size: 12px; color: #888;">
                        <p>This is an electronically generated receipt. Thank you for the payment!</p>
                        <p>&copy; 2026 Tuition Manager System. All rights reserved.</p>
                    </div>
                </div>
            """.trimIndent()
            
            val result = repository.sendResendEmail(student.parentEmail, subject, htmlContent)
            result.fold(
                onSuccess = {
                    _emailState.value = EmailState.Success("Receipt email sent successfully to ${student.parentEmail}")
                },
                onFailure = {
                    _emailState.value = EmailState.Error("Failed to send receipt email: ${it.message}")
                }
            )
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    fun saveBackupCredentials(email: String, passcode: String) {
        sharedPrefs.edit()
            .putString("backup_email", email.trim().lowercase())
            .putString("backup_passcode", passcode)
            .apply()
    }

    fun getBackupEmail(): String = sharedPrefs.getString("backup_email", "") ?: ""
    fun getBackupPasscode(): String = sharedPrefs.getString("backup_passcode", "") ?: ""

    fun triggerAutoBackup() {
        val autoBackup = sharedPrefs.getBoolean("auto_backup_enabled", true)
        val email = sharedPrefs.getString("backup_email", "") ?: ""
        val passcode = sharedPrefs.getString("backup_passcode", "") ?: ""
        
        if (autoBackup && email.isNotBlank() && passcode.isNotBlank()) {
            viewModelScope.launch {
                repository.performSupabaseBackup(email, passcode)
            }
        }
    }

    fun pingSupabaseDatabase() {
        viewModelScope.launch {
            _antiPauseStatus.value = "Pinging Supabase..."
            val result = repository.pingSupabase()
            result.fold(
                onSuccess = {
                    val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())
                    _antiPauseStatus.value = "Anti-Pause Active (Last Ping: ${getTodayDateStr()} $timeStr)"
                    sharedPrefs.edit().putLong("last_ping_time", System.currentTimeMillis()).apply()
                },
                onFailure = {
                    _antiPauseStatus.value = "Offline or Unconfigured"
                }
            )
        }
    }

    fun checkRememberedSession() {
        val isLoggedIn = sharedPrefs.getBoolean("logged_in", false)
        val role = sharedPrefs.getString("logged_role", "")
        if (isLoggedIn) {
            if (role == "teacher") {
                val name = sharedPrefs.getString("teacher_name", "Tutor") ?: "Tutor"
                _currentSession.value = UserSession.TeacherSession(name)
            } else if (role == "student") {
                val studentId = sharedPrefs.getLong("student_id", -1L)
                if (studentId != -1L) {
                    val student = allStudents.value.find { it.id == studentId }
                    if (student != null) {
                        _currentSession.value = UserSession.StudentSession(student)
                    } else {
                        _currentSession.value = UserSession.LoggedOut
                    }
                } else {
                    _currentSession.value = UserSession.LoggedOut
                }
            } else {
                _currentSession.value = UserSession.LoggedOut
            }
        } else {
            _currentSession.value = UserSession.LoggedOut
        }
    }

    fun onSplashFinished() {
        checkRememberedSession()
    }

    fun loginAsTeacher(name: String, pin: String, rememberMe: Boolean = false): Boolean {
        val trimmedName = name.trim()
        val trimmedPin = pin.trim()
        
        // Validation for tutor/admin with password "ashutosh" (or case-insensitive) or "admin"
        if (trimmedPin == "ashutosh" || trimmedPin.lowercase() == "ashutosh" || trimmedPin == "admin") {
            viewModelScope.launch {
                val current = repository.getAppConfigDirect()
                if (current != null) {
                    repository.insertAppConfig(current.copy(tutorName = trimmedName))
                } else {
                    repository.insertAppConfig(
                        AppConfig(
                            id = 1,
                            tutorName = trimmedName,
                            tuitionName = "Ashutosh Tuition Class",
                            address = "A-24, Sector 15, Dwarka, New Delhi - 110075",
                            phone = "+91 98765 43210",
                            email = "aditi.sharma@excelacademy.com",
                            receiptPrefix = "EXT",
                            nextReceiptNo = 1001
                        )
                    )
                }
            }
            
            _currentSession.value = UserSession.TeacherSession(trimmedName)
            
            if (rememberMe) {
                sharedPrefs.edit()
                    .putBoolean("logged_in", true)
                    .putString("logged_role", "teacher")
                    .putString("teacher_name", trimmedName)
                    .apply()
            }
            return true
        }
        return false
    }

    fun loginAsStudent(emailOrPhone: String, pinOrId: String, rememberMe: Boolean = false): Boolean {
        val normalizedUser = emailOrPhone.trim().lowercase()
        val normalizedPin = pinOrId.trim()
        val student = allStudents.value.find { student ->
            (student.parentEmail.trim().lowercase() == normalizedUser || student.phone.trim() == normalizedUser) &&
            (student.id.toString() == normalizedPin || student.phone.takeLast(4) == normalizedPin)
        }
        if (student != null) {
            _currentSession.value = UserSession.StudentSession(student)
            if (rememberMe) {
                sharedPrefs.edit()
                    .putBoolean("logged_in", true)
                    .putString("logged_role", "student")
                    .putLong("student_id", student.id)
                    .apply()
            }
            return true
        }
        return false
    }

    fun login(emailOrPhone: String, pinOrId: String): Boolean {
        return loginAsStudent(emailOrPhone, pinOrId, false)
    }

    fun logout() {
        _currentSession.value = UserSession.LoggedOut
        sharedPrefs.edit()
            .putBoolean("logged_in", false)
            .putString("logged_role", "")
            .putString("teacher_name", "")
            .putLong("student_id", -1L)
            .apply()
    }

    fun dismissHeadsUpNotification() {
        _activeHeadsUpNotification.value = null
    }

    fun postNotification(title: String, message: String, studentId: Long?) {
        viewModelScope.launch {
            val config = repository.getAppConfigDirect()
            val sender = config?.tutorName ?: "Tutor"
            val notif = AppNotification(
                studentId = studentId,
                title = title,
                message = message,
                timestamp = System.currentTimeMillis(),
                senderName = sender
            )
            repository.insertNotification(notif)
            
            // Trigger heads-up notification (WhatsApp style)
            _activeHeadsUpNotification.value = notif
            
            triggerAutoBackup()
        }
    }

    suspend fun recordCustomFeePayment(
        studentId: Long,
        customAmount: Double,
        paymentMode: String,
        ref: String?,
        notes: String?
    ): Pair<FeeTransaction, Double>? {
        val student = repository.getStudentById(studentId) ?: return null
        val config = repository.getAppConfigDirect() ?: return null

        val receiptNo = "${config.receiptPrefix}-${config.nextReceiptNo}"
        
        val studentTransactions = allTransactions.value.filter { it.studentId == studentId }
        val currentPaidTotal = studentTransactions.sumOf { it.amount }
        val newPaidTotal = currentPaidTotal + customAmount
        
        val monthsSinceJoin = getMonthsSinceJoining(student.joiningDate, getTodayDateStr())
        val totalDueUpToNow = monthsSinceJoin.size * student.feePerMonth
        val remainingDues = maxOf(0.0, totalDueUpToNow - newPaidTotal)
        
        val currentNumMonthsPaid = if (student.feePerMonth > 0) (currentPaidTotal / student.feePerMonth).toInt() else 0
        val newNumMonthsPaid = if (student.feePerMonth > 0) (newPaidTotal / student.feePerMonth).toInt() else 0
        val monthsPaidThisTime = if (newNumMonthsPaid > currentNumMonthsPaid) {
            monthsSinceJoin.subList(currentNumMonthsPaid, minOf(newNumMonthsPaid, monthsSinceJoin.size))
        } else {
            emptyList()
        }

        val transaction = FeeTransaction(
            studentId = studentId,
            receiptNumber = receiptNo,
            amount = customAmount,
            monthsPaid = monthsPaidThisTime,
            paymentDate = getTodayDateStr(),
            paymentMode = paymentMode,
            transactionRef = ref,
            emailSent = true,
            notes = notes
        )

        val txId = repository.insertTransaction(transaction)
        val savedTx = transaction.copy(id = txId)

        repository.insertAppConfig(
            config.copy(nextReceiptNo = config.nextReceiptNo + 1)
        )

        postNotification(
            title = "Fee Payment Successful",
            message = "Rs. $customAmount received from ${student.name}. Remaining due: Rs. $remainingDues.",
            studentId = studentId
        )

        triggerAutoBackup()
        
        return Pair(savedTx, remainingDues)
    }
}
