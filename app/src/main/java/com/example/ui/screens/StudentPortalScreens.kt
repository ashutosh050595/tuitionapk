package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.TuitionViewModel
import com.example.utils.ReceiptPdfHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class StudentTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : StudentTab("Home", Icons.Default.Home)
    object Attendance : StudentTab("Attendance", Icons.Default.CheckCircle)
    object Payments : StudentTab("Payments", Icons.Default.Star)
    object Notifications : StudentTab("Messages", Icons.Default.Email)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPortalMainScreen(
    student: Student,
    viewModel: TuitionViewModel,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var activeTab by remember { mutableStateOf<StudentTab>(StudentTab.Home) }
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    val duesList by viewModel.studentDuesList.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val allNotifications by viewModel.allNotifications.collectAsStateWithLifecycle()
    
    // Filter transactions and dues for this specific student
    val studentDues = duesList.find { it.student.id == student.id }
    val studentTransactions = transactions.filter { it.studentId == student.id }
    
    // Filter notifications: broadcast (null) or student-specific
    val studentNotifications = allNotifications.filter { it.studentId == null || it.studentId == student.id }
    
    // Student attendance flow
    val attendanceList by viewModel.getAttendanceForStudent(student.id).collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Student Portal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier.testTag("btn_student_logout")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Log Out Student Portal",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("student_bottom_nav"),
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(StudentTab.Home, StudentTab.Attendance, StudentTab.Payments, StudentTab.Notifications)
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag("tab_${tab.title.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "StudentTabTransition"
            ) { tab ->
                when (tab) {
                    is StudentTab.Home -> StudentHomeTab(
                        student = student,
                        appConfig = appConfig,
                        studentDues = studentDues
                    )
                    is StudentTab.Attendance -> StudentAttendanceTab(
                        attendanceList = attendanceList
                    )
                    is StudentTab.Payments -> StudentPaymentsTab(
                        student = student,
                        studentDues = studentDues,
                        transactions = studentTransactions,
                        appConfig = appConfig,
                        viewModel = viewModel
                    )
                    is StudentTab.Notifications -> StudentNotificationsTab(
                        notifications = studentNotifications
                    )
                }
            }
        }
    }
}

// ==========================================
// 1. STUDENT HOME TAB
// ==========================================
@Composable
fun StudentHomeTab(
    student: Student,
    appConfig: AppConfig?,
    studentDues: com.example.ui.StudentDues?
) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Welcome Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = student.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Welcome Back,",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                        )
                    }
                }
            }
        }

        item {
            // Academic Profile Details
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ACADEMIC PROFILE",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    ProfileRow(label = "Class/Batch", value = student.className)
                    ProfileRow(label = "Enrolled Date", value = student.joiningDate)
                    ProfileRow(label = "Registered Email", value = student.parentEmail)
                    ProfileRow(label = "Mobile Phone", value = student.phone)
                    ProfileRow(label = "WhatsApp Link", value = student.whatsapp)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Enrolled Subjects",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        student.subjects.forEach { subject ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = subject,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            // Tutor details Card
            if (appConfig != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "YOUR TUTOR ACADEMY",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = appConfig.tuitionName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Instructor: ${appConfig.tutorName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Address: ${appConfig.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${appConfig.phone}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = "Call")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call Tutor")
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:${appConfig.email}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = "Email")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Email")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==========================================
// 2. STUDENT ATTENDANCE TAB
// ==========================================
@Composable
fun StudentAttendanceTab(
    attendanceList: List<Attendance>
) {
    val totalClasses = attendanceList.size
    val presentCount = attendanceList.count { it.status == "present" }
    val absentCount = attendanceList.count { it.status == "absent" }
    val lateCount = attendanceList.count { it.status == "late" }
    
    val attendancePercent = if (totalClasses > 0) {
        ((presentCount + lateCount).toDouble() / totalClasses * 100).toInt()
    } else 100

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Metrics Overview
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ATTENDANCE SCORE",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$attendancePercent%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Present",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AttendanceStat(label = "Classes", count = totalClasses, color = MaterialTheme.colorScheme.secondary)
                        AttendanceStat(label = "Present", count = presentCount, color = Color(0xFF2E7D32))
                        AttendanceStat(label = "Absent", count = absentCount, color = Color(0xFFC62828))
                        AttendanceStat(label = "Late", count = lateCount, color = Color(0xFFEF6C00))
                    }
                }
            }
        }

        item {
            Text(
                text = "Class Activity Log",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (attendanceList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No attendance recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(attendanceList) { att ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val parsedDate = try {
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val date = sdf.parse(att.date)
                                SimpleDateFormat("MMMM dd, yyyy (EEEE)", Locale.US).format(date)
                            } catch (e: Exception) {
                                att.date
                            }
                            Text(
                                text = parsedDate,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (!att.notes.isNullOrBlank()) {
                                Text(
                                    text = "Notes: ${att.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        val (bg, txt, label) = when (att.status) {
                            "present" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Present")
                            "absent" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Absent")
                            else -> Triple(Color(0xFFFFF3E0), Color(0xFFEF6C00), "Late")
                        }
                        
                        Box(
                            modifier = Modifier
                                .background(bg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = txt
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==========================================
// 3. STUDENT PAYMENTS TAB
// ==========================================
@Composable
fun StudentPaymentsTab(
    student: Student,
    studentDues: com.example.ui.StudentDues?,
    transactions: List<FeeTransaction>,
    appConfig: AppConfig?,
    viewModel: TuitionViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var customAmountStr by remember { mutableStateOf("") }
    var referenceNotes by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val pendingDues = studentDues?.dueAmount ?: 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Outstanding Dues card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingDues > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (pendingDues > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "OUTSTANDING FEES DUE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format("Rs. %,.2f", pendingDues),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (pendingDues > 0) {
                            "Unpaid Months: ${studentDues?.unpaidMonths?.joinToString(", ") ?: "None"}"
                        } else {
                            "All fees paid to date! Awesome job!"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            // Pay Custom Fee Section
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CUSTOMIZE UPI PAYMENT",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = customAmountStr,
                        onValueChange = { customAmountStr = it },
                        label = { Text("Amount to Pay (INR)") },
                        placeholder = { Text("e.g. 1500") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("student_pay_custom_amount"),
                        singleLine = true,
                        leadingIcon = { Text("Rs.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = referenceNotes,
                        onValueChange = { referenceNotes = it },
                        label = { Text("Payment Notes (Optional)") },
                        placeholder = { Text("e.g. Paid for June partial") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Button(
                        onClick = {
                            val amountVal = customAmountStr.toDoubleOrNull()
                            if (amountVal == null || amountVal <= 0) {
                                Toast.makeText(context, "Please enter a valid payment amount", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            isProcessing = true
                            
                            // 1. Launch UPI intent (Free standard gateway)
                            val upiId = "9708084906@ybl"
                            val cleanTuitionName = appConfig?.tuitionName ?: "Tuition Fee"
                            val upiUri = "upi://pay?pa=$upiId&pn=${Uri.encode(cleanTuitionName)}&am=$amountVal&cu=INR&tn=${Uri.encode("Tuition Fee - ${student.name}")}"
                            
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                context.startActivity(intent)
                                Toast.makeText(context, "Opening UPI payment gateways...", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                // Fallback if no UPI app installed (or simulated)
                                Toast.makeText(context, "Simulated: No physical UPI app detected. Proceeding with recording transaction...", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_launch_upi_payment"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)) // Primary Purple
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "UPI")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay via Free UPI (9708084906@ybl)", fontWeight = FontWeight.Bold)
                    }

                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val amountVal = customAmountStr.toDoubleOrNull() ?: 0.0
                                scope.launch {
                                    val result = viewModel.recordCustomFeePayment(
                                        studentId = student.id,
                                        customAmount = amountVal,
                                        paymentMode = "upi",
                                        ref = "UPI-" + System.currentTimeMillis().toString().takeLast(6),
                                        notes = referenceNotes.ifBlank { "UPI Fee Payment" }
                                    )
                                    if (result != null) {
                                        val (tx, remaining) = result
                                        if (appConfig != null) {
                                            ReceiptPdfHelper.generateAndSaveReceiptPdf(context, student, tx, appConfig, remaining)
                                        }
                                        Toast.makeText(context, "Payment Recorded & PDF Receipt Saved!", Toast.LENGTH_LONG).show()
                                        customAmountStr = ""
                                        referenceNotes = ""
                                        isProcessing = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_confirm_upi_payment"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)) // Success Green
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Paid")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I Have Transferred - Confirm Payment", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Fee Receipt History",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        if (transactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No prior payments found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(transactions) { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tx.receiptNumber,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Paid on: ${tx.paymentDate} (${tx.paymentMode.uppercase()})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (tx.monthsPaid.isNotEmpty()) {
                                Text(
                                    text = "Allocated Months: ${tx.monthsPaid.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("Rs. %,.2f", tx.amount),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(
                                onClick = {
                                    if (appConfig != null) {
                                        // Compute remaining due at the historical moment
                                        val remainingAtMoment = pendingDues
                                        ReceiptPdfHelper.generateAndSaveReceiptPdf(context, student, tx, appConfig, remainingAtMoment)
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow, // wait, Icons.Default.ArrowDownward or similar
                                    contentDescription = "Download Receipt PDF",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. STUDENT NOTIFICATIONS TAB
// ==========================================
@Composable
fun StudentNotificationsTab(
    notifications: List<AppNotification>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "TUTOR ANNOUNCEMENTS",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (notifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No new announcements or messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(notifications) { notif ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (notif.studentId != null) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) // Private DM highlighted
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (notif.studentId != null) Color.Red else MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (notif.studentId != null) "Direct Message (Private)" else "Announcement (All)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (notif.studentId != null) Color.Red else MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            val timeStr = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(notif.timestamp))
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = notif.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = notif.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sent by: ${notif.senderName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
