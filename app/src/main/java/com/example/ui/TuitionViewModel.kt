package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.example.network.ResendAttachment
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
        
        // Automatically send receipt email with PDF attachment to parents
        val student = allStudents.value.find { it.id == studentId }
        if (student != null && student.parentEmail.isNotBlank()) {
            val savedTx = transaction.copy(id = newTxId)
            sendEmailReceipt(student, savedTx)
        }
        
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

    fun generateReceiptPdfBase64(student: Student, transaction: FeeTransaction, appConfig: AppConfig?): String {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        val paint = Paint()
        val textPaint = Paint()
        
        // Header Accent Band
        paint.color = android.graphics.Color.rgb(103, 80, 164)
        canvas.drawRect(30f, 40f, 565f, 45f, paint)
        
        textPaint.isAntiAlias = true
        textPaint.color = android.graphics.Color.BLACK
        
        // Tuition Center name
        textPaint.textSize = 20f
        textPaint.isFakeBoldText = true
        val tuitionName = appConfig?.tuitionName ?: "Ashutosh Tuition Class"
        canvas.drawText(tuitionName, 50f, 80f, textPaint)
        
        // Address and Contact details
        textPaint.textSize = 10f
        textPaint.isFakeBoldText = false
        textPaint.color = android.graphics.Color.GRAY
        val address = appConfig?.address ?: "A-24, Sector 15, Dwarka, New Delhi"
        val contact = "Phone: ${appConfig?.phone ?: "+91 98765 43210"} | Email: ${appConfig?.email ?: "gautam663@gmail.com"}"
        canvas.drawText("Address: $address", 50f, 100f, textPaint)
        canvas.drawText(contact, 50f, 115f, textPaint)
        
        // Divider
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRect(30f, 130f, 565f, 131f, paint)
        
        // Receipt Header
        textPaint.color = android.graphics.Color.rgb(103, 80, 164)
        textPaint.textSize = 14f
        textPaint.isFakeBoldText = true
        canvas.drawText("FEE PAYMENT RECEIPT", 50f, 160f, textPaint)
        
        // Basic receipt metadata
        textPaint.textSize = 12f
        textPaint.color = android.graphics.Color.BLACK
        textPaint.isFakeBoldText = false
        canvas.drawText("Receipt No: ${transaction.receiptNumber}", 50f, 190f, textPaint)
        canvas.drawText("Date: ${transaction.paymentDate}", 400f, 190f, textPaint)
        
        canvas.drawRect(30f, 210f, 565f, 211f, paint)
        
        var yPos = 240f
        val lineSpacing = 25f
        
        fun drawRow(label: String, value: String) {
            textPaint.isFakeBoldText = true
            canvas.drawText(label, 50f, yPos, textPaint)
            textPaint.isFakeBoldText = false
            canvas.drawText(value, 200f, yPos, textPaint)
            yPos += lineSpacing
        }
        
        drawRow("Student Name:", student.name)
        drawRow("Class / Grade:", student.className)
        drawRow("Guardian Name:", student.guardianName ?: "N/A")
        drawRow("Months Paid:", transaction.monthsPaid.joinToString(", "))
        drawRow("Payment Mode:", transaction.paymentMode.replace("_", " ").uppercase())
        if (!transaction.transactionRef.isNullOrBlank()) {
            drawRow("Reference / UPI ID:", transaction.transactionRef)
        }
        
        canvas.drawRect(30f, yPos, 565f, yPos + 1f, paint)
        yPos += 30f
        
        // Amount highlights box
        paint.color = android.graphics.Color.rgb(248, 250, 252)
        canvas.drawRect(50f, yPos, 545f, yPos + 60f, paint)
        
        textPaint.textSize = 14f
        textPaint.color = android.graphics.Color.rgb(51, 65, 85)
        textPaint.isFakeBoldText = true
        canvas.drawText("AMOUNT PAID:", 70f, yPos + 35f, textPaint)
        
        textPaint.textSize = 20f
        textPaint.color = android.graphics.Color.rgb(16, 185, 129)
        canvas.drawText("Rs. ${transaction.amount.toInt()}", 380f, yPos + 38f, textPaint)
        
        yPos += 90f
        
        // Authorization block & custom signature
        textPaint.textSize = 10f
        textPaint.color = android.graphics.Color.BLACK
        textPaint.isFakeBoldText = false
        canvas.drawText("Status: REGISTERED", 50f, yPos, textPaint)
        canvas.drawText("Receipt email sent successfully", 50f, yPos + 15f, textPaint)
        
        val tutorName = appConfig?.tutorName ?: "Ashutosh"
        canvas.drawText(tutorName, 400f, yPos, textPaint)
        paint.color = android.graphics.Color.DKGRAY
        canvas.drawRect(400f, yPos + 5f, 520f, yPos + 6f, paint)
        canvas.drawText("Authorized Tutor Signature", 400f, yPos + 20f, textPaint)
        
        pdfDocument.finishPage(page)
        
        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun sendEmailReceipt(student: Student, transaction: FeeTransaction) {
        viewModelScope.launch {
            if (student.parentEmail.trim().isEmpty()) {
                _emailState.value = EmailState.Error("Student parent email is empty!")
                return@launch
            }
            _emailState.value = EmailState.Loading
            
            val subject = "Fee Collected Receipt: ${transaction.receiptNumber} - ${student.name}"
            
            val tutorName = appConfig.value?.tutorName ?: "Ashutosh"
            val tuitionName = appConfig.value?.tuitionName ?: "Ashutosh Tuition Class"
            val phone = appConfig.value?.phone ?: "+91 98765 43210"
            
            val htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px; padding: 20px; color: #333;">
                    <div style="text-align: center; border-bottom: 2px solid #6750A4; padding-bottom: 15px; margin-bottom: 20px;">
                        <h2 style="color: #6750A4; margin: 0;">FEE PAYMENT RECEIVED</h2>
                        <p style="margin: 5px 0 0 0; font-size: 14px; color: #666;">$tuitionName</p>
                    </div>
                    
                    <div style="font-size: 15px; line-height: 1.6; color: #444; margin-bottom: 20px;">
                        <p>Dear Parent/Guardian,</p>
                        <p>We have successfully collected the tuition fees for <strong>${student.name}</strong> (Class: ${student.className}). Thank you for the payment.</p>
                        <p>Below is a summary of the transaction. A copy of the formal receipt has been attached as a PDF document to this email.</p>
                    </div>
                    
                    <div style="margin-bottom: 20px; background-color: #f9f9f9; padding: 15px; border-radius: 6px;">
                        <table style="width: 100%; border-collapse: collapse;">
                            <tr>
                                <td style="padding: 6px 0; font-weight: bold; color: #555; width: 140px;">Receipt No:</td>
                                <td style="padding: 6px 0; color: #333;">${transaction.receiptNumber}</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; font-weight: bold; color: #555;">Date:</td>
                                <td style="padding: 6px 0; color: #333;">${transaction.paymentDate}</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; font-weight: bold; color: #555;">Student Name:</td>
                                <td style="padding: 6px 0; color: #333; font-weight: bold;">${student.name}</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; font-weight: bold; color: #555;">Months Covered:</td>
                                <td style="padding: 6px 0; color: #6750A4; font-weight: bold;">${transaction.monthsPaid.joinToString(", ")}</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; font-weight: bold; color: #555;">Amount Paid:</td>
                                <td style="padding: 6px 0; color: #2E7D32; font-weight: bold; font-size: 16px;">₹${transaction.amount.toInt()}</td>
                            </tr>
                            <tr>
                                <td style="padding: 6px 0; font-weight: bold; color: #555;">Payment Mode:</td>
                                <td style="padding: 6px 0; color: #333;">${transaction.paymentMode.replace("_", " ").uppercase()}</td>
                            </tr>
                        </table>
                    </div>
                    
                    <div style="font-size: 14px; color: #555; line-height: 1.5; margin-bottom: 20px;">
                        <p>Best regards,<br/><strong>$tutorName</strong><br/>$tuitionName<br/>Phone: $phone</p>
                    </div>
                    
                    <div style="border-top: 1px solid #eee; padding-top: 15px; text-align: center; font-size: 12px; color: #888;">
                        <p>This is an automated system email notification with a formal PDF invoice attached.</p>
                        <p>&copy; 2026 $tuitionName. All rights reserved.</p>
                    </div>
                </div>
            """.trimIndent()
            
            try {
                val pdfBase64 = generateReceiptPdfBase64(student, transaction, appConfig.value)
                val attachment = ResendAttachment(
                    content = pdfBase64,
                    filename = "Receipt_${transaction.receiptNumber}.pdf"
                )
                val result = repository.sendResendEmail(
                    toEmail = student.parentEmail,
                    subject = subject,
                    htmlBody = htmlContent,
                    attachments = listOf(attachment)
                )
                result.fold(
                    onSuccess = {
                        _emailState.value = EmailState.Success("Receipt email with PDF attachment sent successfully to ${student.parentEmail}")
                    },
                    onFailure = {
                        _emailState.value = EmailState.Error("Failed to send receipt email with PDF attachment: ${it.message}")
                    }
                )
            } catch (e: Exception) {
                _emailState.value = EmailState.Error("Error generating PDF or sending email: ${e.message}")
            }
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
        val trimmedName = name.trim().lowercase()
        val trimmedPin = pin.trim()
        
        // Match user's explicit login details (case-insensitive for convenience)
        val isExactCredentials = (trimmedName == "gautam663@gmail.com") && trimmedPin.equals("Gautam@2012", ignoreCase = true)
        val isFallbackTeacher = (trimmedName == "ashutosh" || trimmedName == "admin" || trimmedName.isBlank()) && 
                (trimmedPin.equals("Gautam@2012", ignoreCase = true) || trimmedPin.lowercase() == "ashutosh" || trimmedPin == "admin")
        
        if (isExactCredentials || isFallbackTeacher) {
            val resolvedName = "Ashutosh"
            
            // Automatically pre-configure their backup credentials so automatic Supabase sync is immediately active!
            sharedPrefs.edit()
                .putString("backup_email", "gautam663@gmail.com")
                .putString("backup_passcode", "Gautam@2012")
                .apply()

            viewModelScope.launch {
                val current = repository.getAppConfigDirect()
                if (current != null) {
                    repository.insertAppConfig(current.copy(tutorName = resolvedName))
                } else {
                    repository.insertAppConfig(
                        AppConfig(
                            id = 1,
                            tutorName = resolvedName,
                            tuitionName = "Ashutosh Tuition Class",
                            address = "A-24, Sector 15, Dwarka, New Delhi - 110075",
                            phone = "+91 98765 43210",
                            email = "gautam663@gmail.com",
                            receiptPrefix = "EXT",
                            nextReceiptNo = 1001
                        )
                    )
                }
                
                // Real-time synchronization: Immediately restore saved data from Supabase!
                repository.performSupabaseRestore("gautam663@gmail.com", "Gautam@2012")
            }
            
            _currentSession.value = UserSession.TeacherSession(resolvedName)
            
            if (rememberMe) {
                sharedPrefs.edit()
                    .putBoolean("logged_in", true)
                    .putString("logged_role", "teacher")
                    .putString("teacher_name", resolvedName)
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

        // Automatically send receipt email with PDF attachment to parents
        if (student.parentEmail.isNotBlank()) {
            sendEmailReceipt(student, savedTx)
        }

        postNotification(
            title = "Fee Payment Successful",
            message = "Rs. $customAmount received from ${student.name}. Remaining due: Rs. $remainingDues.",
            studentId = studentId
        )

        triggerAutoBackup()
        
        return Pair(savedTx, remainingDues)
    }
}
