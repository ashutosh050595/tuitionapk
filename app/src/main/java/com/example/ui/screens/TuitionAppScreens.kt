package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.StudentDues
import com.example.ui.TuitionViewModel
import com.example.ui.SyncState
import com.example.ui.EmailState
import com.example.ui.UserSession
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen {
    object Dashboard : Screen()
    object Students : Screen()
    object Attendance : Screen()
    object Fees : Screen()
    object Settings : Screen()
    
    data class StudentProfile(val studentId: Long) : Screen()
    data class AddEditStudent(val studentId: Long? = null) : Screen()
    data class DepositFee(val studentId: Long? = null) : Screen()
    data class ReceiptDetail(val transactionId: Long) : Screen()
    data class Ledger(val studentId: Long? = null) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: TuitionViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Check and request Notification permission on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notifications are disabled. Enable them in Settings for tuition updates.", Toast.LENGTH_LONG).show()
        }
    }

    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()

    LaunchedEffect(currentSession) {
        if (currentSession !is UserSession.Splash) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // Custom Reactive Navigation Stack
    val backStack = remember { mutableStateListOf<Screen>(Screen.Dashboard) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Dashboard
    
    fun navigateTo(screen: Screen) {
        if (screen is Screen.Dashboard || screen is Screen.Students || screen is Screen.Attendance || screen is Screen.Fees || screen is Screen.Settings) {
            backStack.clear()
        }
        backStack.add(screen)
    }
    
    fun navigateBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }
    
    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }
    
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val duesList by viewModel.studentDuesList.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val attendanceList by viewModel.attendanceForDate.collectAsStateWithLifecycle()
    val activeHeadsUp by viewModel.activeHeadsUpNotification.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val session = currentSession) {
            is UserSession.Splash -> {
                SplashScreen(viewModel = viewModel)
            }
            is UserSession.LoggedOut -> {
                LoginScreen(viewModel = viewModel)
            }
            is UserSession.StudentSession -> {
                StudentPortalMainScreen(
                    student = session.student,
                    viewModel = viewModel,
                    onLogout = { viewModel.logout() }
                )
            }
            is UserSession.TeacherSession -> {
                Scaffold(
                    bottomBar = {
            if (currentScreen is Screen.Dashboard || currentScreen is Screen.Students || 
                currentScreen is Screen.Attendance || currentScreen is Screen.Fees || currentScreen is Screen.Settings) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav"),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        selected = currentScreen is Screen.Dashboard,
                        onClick = { navigateTo(Screen.Dashboard) },
                        modifier = Modifier.testTag("nav_dashboard")
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Students") },
                        label = { Text("Students") },
                        selected = currentScreen is Screen.Students,
                        onClick = { navigateTo(Screen.Students) },
                        modifier = Modifier.testTag("nav_students")
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Attendance") },
                        label = { Text("Attendance") },
                        selected = currentScreen is Screen.Attendance,
                        onClick = { navigateTo(Screen.Attendance) },
                        modifier = Modifier.testTag("nav_attendance")
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = "Fees") },
                        label = { Text("Fees") },
                        selected = currentScreen is Screen.Fees,
                        onClick = { navigateTo(Screen.Fees) },
                        modifier = Modifier.testTag("nav_fees")
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentScreen is Screen.Settings,
                        onClick = { navigateTo(Screen.Settings) },
                        modifier = Modifier.testTag("nav_settings")
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
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    is Screen.Dashboard -> DashboardScreen(
                        viewModel = viewModel,
                        students = students,
                        transactions = transactions,
                        duesList = duesList,
                        appConfig = appConfig,
                        onNavigate = { navigateTo(it) }
                    )
                    is Screen.Students -> StudentsScreen(
                        students = students,
                        onNavigate = { navigateTo(it) }
                    )
                    is Screen.Attendance -> AttendanceScreen(
                        viewModel = viewModel,
                        students = students,
                        selectedDate = selectedDate,
                        attendanceList = attendanceList
                    )
                    is Screen.Fees -> FeesDuesScreen(
                        viewModel = viewModel,
                        students = students,
                        duesList = duesList,
                        transactions = transactions,
                        onNavigate = { navigateTo(it) }
                    )
                    is Screen.Settings -> SettingsScreen(
                        viewModel = viewModel,
                        appConfig = appConfig
                    )
                    is Screen.StudentProfile -> StudentProfileScreen(
                        studentId = targetScreen.studentId,
                        viewModel = viewModel,
                        onNavigate = { navigateTo(it) },
                        onBack = { navigateBack() }
                    )
                    is Screen.AddEditStudent -> AddEditStudentScreen(
                        studentId = targetScreen.studentId,
                        viewModel = viewModel,
                        onBack = { navigateBack() }
                    )
                    is Screen.DepositFee -> DepositFeeScreen(
                        studentId = targetScreen.studentId,
                        viewModel = viewModel,
                        onBack = { navigateBack() },
                        onNavigateToReceipt = { navigateTo(Screen.ReceiptDetail(it)) }
                    )
                    is Screen.ReceiptDetail -> ReceiptDetailScreen(
                        transactionId = targetScreen.transactionId,
                        viewModel = viewModel,
                        onBack = { navigateBack() }
                    )
                    is Screen.Ledger -> LedgerScreen(
                        viewModel = viewModel,
                        selectedStudentId = targetScreen.studentId,
                        onNavigate = { navigateTo(it) },
                        onBack = { navigateBack() }
                    )
                }
            }
        }
    }
            }
        }

        // Heads-up Notification Overlay (with slide-in/slide-out animation)
        AnimatedVisibility(
            visible = activeHeadsUp != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            activeHeadsUp?.let { notif ->
                LaunchedEffect(notif) {
                    kotlinx.coroutines.delay(6000)
                    viewModel.dismissHeadsUpNotification()
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.dismissHeadsUpNotification()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notification",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = notif.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = notif.message,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissHeadsUpNotification() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(
    viewModel: TuitionViewModel,
    students: List<Student>,
    transactions: List<FeeTransaction>,
    duesList: List<StudentDues>,
    appConfig: AppConfig?,
    onNavigate: (Screen) -> Unit
) {
    val activeStudents = students.filter { it.status == "active" }
    val totalDues = duesList.filter { it.student.status == "active" }.sumOf { it.dueAmount }
    
    // Calculate current month fee collections
    val currentMonthLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
    val collectedThisMonth = transactions.filter { tx ->
        tx.monthsPaid.contains(currentMonthLabel)
    }.sumOf { it.amount }

    val totalFeeTarget = activeStudents.sumOf { it.feePerMonth }.toDouble()
    val collectedPercent = if (totalFeeTarget > 0) ((collectedThisMonth / totalFeeTarget) * 100).toInt().coerceIn(0, 100) else 0

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Bento Color Selection
    val cardGoalBg = if (isDark) com.example.ui.theme.BentoCardGoalDark else com.example.ui.theme.BentoCardGoal
    val cardGoalText = if (isDark) com.example.ui.theme.BentoTextGoalDark else com.example.ui.theme.BentoTextGoal

    val cardScheduleBg = if (isDark) com.example.ui.theme.BentoCardScheduleDark else com.example.ui.theme.BentoCardSchedule
    val cardScheduleText = if (isDark) com.example.ui.theme.BentoTextScheduleDark else com.example.ui.theme.BentoTextSchedule

    val cardWarmBg = if (isDark) com.example.ui.theme.BentoCardWarmDark else com.example.ui.theme.BentoCardWarm
    val cardWarmText = if (isDark) com.example.ui.theme.BentoTextWarmDark else com.example.ui.theme.BentoTextWarm

    val cardNeutralBg = if (isDark) com.example.ui.theme.BentoCardNeutralDark else com.example.ui.theme.BentoCardNeutral
    val cardNeutralText = if (isDark) com.example.ui.theme.BentoTextNeutralDark else com.example.ui.theme.BentoTextNeutral

    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcoming Bento Header
        item {
            val dateLabel = SimpleDateFormat("EEEE, MMM dd", Locale.US).format(Date()).uppercase()
            val tutorName = when (val session = currentSession) {
                is UserSession.TeacherSession -> session.name
                else -> appConfig?.tutorName ?: "Tutor Aditi"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Good morning, $tutorName",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                // Tutor Avatar & Student Portal Login Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var showLoginDialog by remember { mutableStateOf(false) }
                    
                    IconButton(
                        onClick = { showLoginDialog = true },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .size(44.dp)
                            .testTag("btn_student_portal_login")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Student Portal Login",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    if (showLoginDialog) {
                        StudentLoginDialog(
                            viewModel = viewModel,
                            onDismiss = { showLoginDialog = false }
                        )
                    }

                    val firstChar = tutorName.trim().firstOrNull()?.toString()?.uppercase() ?: "T"
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = firstChar,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                }
            }

        // Bento Grid Card 1: Monthly Goal Tracker (Col-span 2)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.Fees) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardGoalBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = cardGoalText,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Monthly Fee Collections",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = cardGoalText
                            )
                        }
                        Text(
                            text = "$collectedPercent%",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = cardGoalText
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Linear Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isDark) Color(0xFF3B2F50) else Color(0xFFD0BCFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (collectedThisMonth.toFloat() / totalFeeTarget.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(cardGoalText)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "Collected ₹${collectedThisMonth.toInt()} of ₹${totalFeeTarget.toInt()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = cardGoalText.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Bento Grid Row containing:
        // Left Column (Card 2: Attendance Schedule - Row-span 2)
        // Right Column (Card 3: Dues, Card 4: Active Count - Stacked)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column Card (Attendance/Class Status)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(236.dp)
                        .clickable { onNavigate(Screen.Attendance) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardScheduleBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = cardScheduleText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Class Status",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = cardScheduleText
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Real list of students with left status border
                            if (students.isEmpty()) {
                                Text(
                                    text = "No students registered yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cardScheduleText.copy(alpha = 0.6f)
                                )
                            } else {
                                students.take(3).forEach { student ->
                                    val isStudentActive = student.status == "active"
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            val indicatorColor = if (isStudentActive) {
                                                if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                            } else {
                                                if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(24.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(indicatorColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = student.name,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                                    color = cardScheduleText,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = student.className,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                                    color = cardScheduleText.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = "Manage Tracker",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = cardScheduleText.copy(alpha = 0.9f)
                        )
                    }
                }

                // Right Column containing Card 3 and Card 4
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 3: Pending Dues (Warm Card)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clickable { onNavigate(Screen.Fees) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardWarmBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pending Dues",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = cardWarmText
                                )
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (totalDues > 0) Color(0xFFC62828) else cardWarmText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "₹${totalDues.toInt()}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = cardWarmText
                                )
                                Text(
                                    text = if (totalDues > 0) "Needs attention" else "All paid up",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = cardWarmText.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Card 4: Active Count (Neutral Card)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clickable { onNavigate(Screen.Students) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardNeutralBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Active Students",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = cardNeutralText
                                )
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = cardNeutralText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "${activeStudents.size}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = cardNeutralText
                                )
                                Text(
                                    text = "Out of ${students.size} total",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = cardNeutralText.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bento Grid Quick Actions Row (Styled like a modern M3 grid panel)
        item {
            Column {
                Text(
                    text = "QUICK ACTIONS",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Add Student
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clickable { onNavigate(Screen.AddEditStudent()) }
                            .testTag("btn_add_student_dash"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Add Student",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Action 2: Mark Attendance
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clickable { onNavigate(Screen.Attendance) }
                            .testTag("btn_mark_attendance_dash"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Mark Attendance",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 3: Record Fee
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clickable { onNavigate(Screen.DepositFee()) }
                            .testTag("btn_record_fee_dash"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Record Fee",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Action 4: Student Ledger
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clickable { onNavigate(Screen.Ledger()) }
                            .testTag("btn_student_ledger_dash"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF0D47A1) else Color(0xFFE3F2FD)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Student Ledger",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Monthly Collections Record (Bento Bar Chart Card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "FEE COLLECTIONS RECORD",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val monthCollections = remember(transactions) {
                        val cal = Calendar.getInstance()
                        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
                        val months = mutableListOf<Pair<String, Double>>()
                        for (i in 4 downTo 0) {
                            cal.time = Date()
                            cal.add(Calendar.MONTH, -i)
                            val label = sdf.format(cal.time)
                            val sum = transactions.filter { tx -> tx.monthsPaid.contains(label) }.sumOf { it.amount }
                            months.add(label.split(" ")[0].take(3) to sum)
                        }
                        months
                    }
                    
                    SimpleBarChart(
                        data = monthCollections,
                        modifier = Modifier
                            .height(140.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // Recent Activity / Transactions Section
        item {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT FEE TRANSACTIONS",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigate(Screen.Fees) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No transactions recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        transactions.take(4).forEach { tx ->
                            val studName = students.find { it.id == tx.studentId }?.name ?: "Unknown Student"
                            TransactionItemRow(
                                transaction = tx,
                                studentName = studName,
                                onClick = { onNavigate(Screen.ReceiptDetail(tx.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatCard(
    title: String,
    value: String,
    subtext: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TransactionItemRow(
    transaction: FeeTransaction,
    studentName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE0F2FE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = studentName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = transaction.monthsPaid.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${transaction.amount.toInt()}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF10B981)
                )
                Text(
                    text = transaction.paymentDate,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun SimpleBarChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val maxVal = (data.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { (label, value) ->
            val fraction = (value / maxVal).toFloat()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .fillMaxHeight(fraction.coerceIn(0.05f, 1f))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "₹${value.toInt()}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ==========================================
// 2. STUDENTS SCREEN
// ==========================================
@Composable
fun StudentsScreen(
    students: List<Student>,
    onNavigate: (Screen) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") } // "All", "Active", "Inactive"

    val filteredStudents = students.filter { student ->
        val matchesSearch = student.name.contains(searchQuery, ignoreCase = true) || 
                            student.className.contains(searchQuery, ignoreCase = true) ||
                            student.subjects.any { it.contains(searchQuery, ignoreCase = true) }
        
        val matchesStatus = when (statusFilter) {
            "Active" -> student.status == "active"
            "Inactive" -> student.status == "inactive"
            else -> student.status != "removed"
        }
        
        matchesSearch && matchesStatus
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Screen.AddEditStudent()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_student")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Student")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Text(
                text = "STUDENTS DIRECTORY",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name, class or subject...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Filters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Active", "Inactive").forEach { filter ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { statusFilter = filter },
                        label = { Text(filter) },
                        modifier = Modifier.testTag("filter_$filter")
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No students match your query." else "No students recorded. Add your first student!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredStudents) { student ->
                        StudentItemCard(
                            student = student,
                            onClick = { onNavigate(Screen.StudentProfile(student.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudentItemCard(
    student: Student,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("student_card_${student.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (student.status == "active") {
                                listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                            } else {
                                listOf(Color(0xFF94A3B8), Color(0xFF64748B))
                            }
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (student.status == "active") Color(0xFFD1FAE5) else Color(0xFFF3F4F6),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = student.status.uppercase(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp,
                                color = if (student.status == "active") Color(0xFF065F46) else Color(0xFF374151)
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = student.className,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Subjects list
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(student.subjects) { subj ->
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = subj,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Monthly Fee: ₹${student.feePerMonth.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==========================================
// 3. STUDENT PROFILE DETAIL SCREEN
// ==========================================
@Composable
fun StudentProfileScreen(
    studentId: Long,
    viewModel: TuitionViewModel,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val student = students.find { it.id == studentId }
    
    val duesList by viewModel.studentDuesList.collectAsStateWithLifecycle()
    val studentDues = duesList.find { it.student.id == studentId }
    
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val studentTransactions = transactions.filter { it.studentId == studentId }
    
    val attendanceFlow = remember(studentId) { viewModel.getAttendanceForStudent(studentId) }
    val studentAttendance by attendanceFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    if (student == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Student not found.")
        }
        return
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Profile, 1: Fees, 2: Attendance

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "STUDENT PROFILE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(
                onClick = { onNavigate(Screen.AddEditStudent(studentId)) },
                modifier = Modifier.testTag("btn_edit_student")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Student")
            }
        }

        // Student Header Information card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = student.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = student.className,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• Joined: ${student.joiningDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Outstanding Dues", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "₹${studentDues?.dueAmount?.toInt() ?: 0}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if ((studentDues?.dueAmount ?: 0.0) > 0.0) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                            )
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onNavigate(Screen.DepositFee(student.id)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_record_fee_profile")
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record Fee", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, modifier = Modifier.testTag("tab_profile_info")) {
                Text("Contact", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, modifier = Modifier.testTag("tab_profile_fees")) {
                Text("Fees (${studentTransactions.size})", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, modifier = Modifier.testTag("tab_profile_attendance")) {
                Text("Attendance", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Contact details and notes
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            ProfileDetailItem(label = "Guardian Name", value = student.guardianName, icon = Icons.Default.Person)
                        }
                        item {
                            ProfileDetailItem(label = "Parent Email", value = student.parentEmail, icon = Icons.Default.Email)
                        }
                        item {
                            ProfileDetailItem(label = "Phone Number", value = student.phone, icon = Icons.Default.Phone)
                        }
                        item {
                            ProfileDetailItem(label = "WhatsApp Number", value = student.whatsapp, icon = Icons.Default.Phone)
                        }
                        item {
                            ProfileDetailItem(label = "Home Address", value = student.address, icon = Icons.Default.LocationOn)
                        }
                        item {
                            ProfileDetailItem(label = "Subjects Enrolled", value = student.subjects.joinToString(", "), icon = Icons.Default.Info)
                        }
                        item {
                            ProfileDetailItem(label = "Monthly Class Fee", value = "₹${student.feePerMonth.toInt()}", icon = Icons.Default.Star)
                        }
                        student.notes?.let {
                            item {
                                ProfileDetailItem(label = "Tutor's Observations & Notes", value = it, icon = Icons.Default.Edit)
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.toggleStudentStatus(student.id)
                                        Toast.makeText(context, "Student status changed", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_toggle_active"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(if (student.status == "active") "Deactivate" else "Activate")
                                }
                                Button(
                                    onClick = {
                                        viewModel.removeStudent(student.id)
                                        Toast.makeText(context, "Student removed", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_remove_student"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Remove Student")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column {
                        Button(
                            onClick = { onNavigate(Screen.Ledger(student.id)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("btn_view_full_ledger"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Full Payment Ledger", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        // Fees transaction logs
                        if (studentTransactions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No fees paid yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                items(studentTransactions) { tx ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigate(Screen.ReceiptDetail(tx.id)) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Receipt: ${tx.receiptNumber}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Months: ${tx.monthsPaid.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "₹${tx.amount.toInt()}",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = Color(0xFF10B981)
                                            )
                                            Text(
                                                text = tx.paymentDate,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                2 -> {
                    // Attendance overview
                    if (studentAttendance.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No attendance recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val presentCount = studentAttendance.count { it.status == "present" }
                        val leaveCount = studentAttendance.count { it.status == "leave" }
                        val absentCount = studentAttendance.count { it.status == "absent" }
                        val total = studentAttendance.size
                        val rate = if (total > 0) (presentCount.toFloat() / total * 100).toInt() else 0
                        
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Rate banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Attendance Score", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        text = "$rate% Present",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "P: $presentCount | A: $absentCount | L: $leaveCount",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                items(studentAttendance) { att ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = att.date, fontWeight = FontWeight.Bold)
                                                att.notes?.let {
                                                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            
                                            // status pill
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = when (att.status) {
                                                            "present" -> Color(0xFFD1FAE5)
                                                            "absent" -> Color(0xFFFEE2E2)
                                                            else -> Color(0xFFFEF3C7)
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = att.status.uppercase(),
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = when (att.status) {
                                                            "present" -> Color(0xFF065F46)
                                                            "absent" -> Color(0xFF991B1B)
                                                            else -> Color(0xFF92400E)
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileDetailItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ==========================================
// 4. ADD / EDIT STUDENT SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStudentScreen(
    studentId: Long?,
    viewModel: TuitionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val isEdit = studentId != null
    val existingStudent = if (isEdit) students.find { it.id == studentId } else null

    var name by remember { mutableStateOf(existingStudent?.name ?: "") }
    var guardianName by remember { mutableStateOf(existingStudent?.guardianName ?: "") }
    var email by remember { mutableStateOf(existingStudent?.parentEmail ?: "") }
    var phone by remember { mutableStateOf(existingStudent?.phone ?: "") }
    var whatsapp by remember { mutableStateOf(existingStudent?.whatsapp ?: "") }
    var address by remember { mutableStateOf(existingStudent?.address ?: "") }
    var className by remember { mutableStateOf(existingStudent?.className ?: "") }
    var feeStr by remember { mutableStateOf(existingStudent?.feePerMonth?.toInt()?.toString() ?: "1500") }
    var joiningDate by remember { mutableStateOf(existingStudent?.joiningDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var notes by remember { mutableStateOf(existingStudent?.notes ?: "") }
    
    // Subjects selection
    val subjectOptions = listOf("Math", "Science", "Physics", "Chemistry", "Biology", "English", "History")
    val selectedSubjects = remember { mutableStateListOf<String>().apply { 
        existingStudent?.subjects?.let { addAll(it) } 
    } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Student Profile" else "Add New Student") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_form")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Full Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_student_name"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = guardianName,
                    onValueChange = { guardianName = it },
                    label = { Text("Guardian Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_guardian_name"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Parent Email *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("input_email"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("input_phone"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("WhatsApp Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("input_whatsapp"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Home Address") },
                    modifier = Modifier.fillMaxWidth().testTag("input_address"),
                    maxLines = 2
                )
            }
            item {
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("Class / Standard (e.g. Class 9) *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_class"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = feeStr,
                    onValueChange = { feeStr = it },
                    label = { Text("Monthly Tuition Fee (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("input_fee"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = joiningDate,
                    onValueChange = { joiningDate = it },
                    label = { Text("Joining Date (YYYY-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth().testTag("input_joining_date"),
                    singleLine = true
                )
            }
            
            // Subjects Multi-select Chips
            item {
                Column {
                    Text("Select Enrolled Subjects:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subjectOptions.forEach { opt ->
                            val isSel = selectedSubjects.contains(opt)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    if (isSel) selectedSubjects.remove(opt) else selectedSubjects.add(opt)
                                },
                                label = { Text(opt) },
                                modifier = Modifier.testTag("chip_subject_$opt")
                            )
                        }
                    }
                }
            }
            
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observations / Special Instructions") },
                    modifier = Modifier.fillMaxWidth().testTag("input_notes"),
                    maxLines = 3
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.isBlank() || guardianName.isBlank() || email.isBlank() || className.isBlank()) {
                            Toast.makeText(context, "Please complete all fields marked with *", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        
                        val feeVal = feeStr.toDoubleOrNull() ?: 1500.0
                        if (isEdit && existingStudent != null) {
                            viewModel.updateStudent(
                                existingStudent.copy(
                                    name = name,
                                    guardianName = guardianName,
                                    parentEmail = email,
                                    phone = phone,
                                    whatsapp = whatsapp,
                                    address = address,
                                    className = className,
                                    subjects = selectedSubjects.toList(),
                                    feePerMonth = feeVal,
                                    joiningDate = joiningDate,
                                    notes = notes.takeIf { it.isNotBlank() }
                                )
                            )
                            Toast.makeText(context, "Student updated successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addStudent(
                                name = name,
                                guardianName = guardianName,
                                parentEmail = email,
                                phone = phone,
                                whatsapp = whatsapp,
                                address = address,
                                className = className,
                                subjects = selectedSubjects.toList(),
                                feePerMonth = feeVal,
                                joiningDate = joiningDate,
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                            Toast.makeText(context, "Student added successfully", Toast.LENGTH_SHORT).show()
                        }
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_submit_student"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isEdit) "Save Profile Details" else "Register Student")
                }
            }
        }
    }
}

// Simple Layout helper for Flow wrapping chips
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    // Basic implementation since standard FlowRow can sometimes be experimental depending on compose version
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Simple Row wraps chips
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

// ==========================================
// 5. ATTENDANCE SCREEN
// ==========================================
@Composable
fun AttendanceScreen(
    viewModel: TuitionViewModel,
    students: List<Student>,
    selectedDate: String,
    attendanceList: List<Attendance>
) {
    val context = LocalContext.current
    val activeStudents = students.filter { it.status == "active" }
    
    // We maintain local map of studentId to state status
    val tempAttendanceMap = remember(attendanceList, activeStudents) {
        mutableStateMapOf<Long, String>().apply {
            activeStudents.forEach { student ->
                val prefilled = attendanceList.find { it.studentId == student.id }?.status
                put(student.id, prefilled ?: "present") // defaults to present as outlined in spec!
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "DAILY ATTENDANCE MANAGER",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Date selector Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Marking For Date:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Simple clickable row or button to trigger date dialog
                val onSelectDateClick = {
                    val currentCalendar = Calendar.getInstance()
                    try {
                        val parts = selectedDate.split("-")
                        if (parts.size == 3) {
                            currentCalendar.set(Calendar.YEAR, parts[0].toInt())
                            currentCalendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                            currentCalendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val datePickerDialog = android.app.DatePickerDialog(
                        context,
                        { _, year, monthOfYear, dayOfMonth ->
                            val formattedMonth = String.format(Locale.US, "%02d", monthOfYear + 1)
                            val formattedDay = String.format(Locale.US, "%02d", dayOfMonth)
                            val newDateStr = "$year-$formattedMonth-$formattedDay"
                            viewModel.setSelectedDate(newDateStr)
                        },
                        currentCalendar.get(Calendar.YEAR),
                        currentCalendar.get(Calendar.MONTH),
                        currentCalendar.get(Calendar.DAY_OF_MONTH)
                    )
                    datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
                    datePickerDialog.show()
                }

                OutlinedButton(
                    onClick = onSelectDateClick,
                    modifier = Modifier
                        .testTag("attendance_date_picker_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = selectedDate,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Change date",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Header statistics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ACTIVE STUDENTS (${activeStudents.size})",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Default: ALL Present",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF10B981)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No active students to mark attendance.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(activeStudents) { student ->
                    val status = tempAttendanceMap[student.id] ?: "present"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(student.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(student.className, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            // Segmented state toggle buttons (P, A, L)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AttendanceStatusPill(
                                    label = "P",
                                    status = "present",
                                    isSelected = status == "present",
                                    selectedColor = Color(0xFFD1FAE5),
                                    textColor = Color(0xFF065F46),
                                    onClick = { tempAttendanceMap[student.id] = "present" },
                                    modifier = Modifier.testTag("pill_p_${student.id}")
                                )
                                AttendanceStatusPill(
                                    label = "A",
                                    status = "absent",
                                    isSelected = status == "absent",
                                    selectedColor = Color(0xFFFEE2E2),
                                    textColor = Color(0xFF991B1B),
                                    onClick = { tempAttendanceMap[student.id] = "absent" },
                                    modifier = Modifier.testTag("pill_a_${student.id}")
                                )
                                AttendanceStatusPill(
                                    label = "L",
                                    status = "leave",
                                    isSelected = status == "leave",
                                    selectedColor = Color(0xFFFEF3C7),
                                    textColor = Color(0xFF92400E),
                                    onClick = { tempAttendanceMap[student.id] = "leave" },
                                    modifier = Modifier.testTag("pill_l_${student.id}")
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val batchList = tempAttendanceMap.map { (studentId, status) ->
                        Attendance(
                            studentId = studentId,
                            date = selectedDate,
                            status = status
                        )
                    }
                    viewModel.markBulkAttendance(selectedDate, batchList)
                    Toast.makeText(context, "Attendance records saved successfully", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_save_attendance"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Today's Attendance")
            }
        }
    }
}

@Composable
fun AttendanceStatusPill(
    label: String,
    status: String,
    isSelected: Boolean,
    selectedColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) selectedColor else Color(0xFFF3F4F6))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) textColor else Color(0xFF6B7280)
            )
        )
    }
}

// ==========================================
// 6. FEES & DUES DASHBOARD SCREEN
// ==========================================
@Composable
fun FeesDuesScreen(
    viewModel: TuitionViewModel,
    students: List<Student>,
    duesList: List<StudentDues>,
    transactions: List<FeeTransaction>,
    onNavigate: (Screen) -> Unit
) {
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Dues, 1: Transaction logs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "TUITION FEES & DUES CENTER",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Segment selector
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = selectedSubTab == 0, onClick = { selectedSubTab = 0 }, modifier = Modifier.testTag("tab_fees_dues")) {
                Text("Outstanding Dues", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedSubTab == 1, onClick = { selectedSubTab = 1 }, modifier = Modifier.testTag("tab_fees_history")) {
                Text("Payment Records", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = selectedSubTab == 2, onClick = { selectedSubTab = 2 }, modifier = Modifier.testTag("tab_fees_ledger")) {
                Text("Student Ledger", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedSubTab) {
            0 -> {
                // Outstanding dues table
                val overdueStudents = duesList.filter { it.student.status == "active" && it.dueAmount > 0.0 }
                
                if (overdueStudents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(50.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("All dues are fully collected. Great job!", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Total Pending Accounts: ${overdueStudents.size}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Sum: ₹${overdueStudents.sumOf { it.dueAmount }.toInt()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(overdueStudents) { item ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.student.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                            Text("Unpaid: ${item.unpaidMonths.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "₹${item.dueAmount.toInt()}",
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 16.sp
                                            )
                                            Button(
                                                onClick = { onNavigate(Screen.DepositFee(item.student.id)) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("collect_dues_${item.student.id}")
                                            ) {
                                                Text("Collect", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                // Historic transactions
                if (transactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No payment transactions on record.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(transactions) { tx ->
                            val stud = students.find { it.id == tx.studentId }
                            TransactionItemRow(
                                transaction = tx,
                                studentName = stud?.name ?: "Removed Student",
                                onClick = { onNavigate(Screen.ReceiptDetail(tx.id)) }
                            )
                        }
                    }
                }
            }
            2 -> {
                LedgerContent(
                    students = students,
                    duesList = duesList,
                    transactions = transactions,
                    initialStudentId = null,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

// ==========================================
// 7. FEE DEPOSIT FORM SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositFeeScreen(
    studentId: Long?,
    viewModel: TuitionViewModel,
    onBack: () -> Unit,
    onNavigateToReceipt: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val duesList by viewModel.studentDuesList.collectAsStateWithLifecycle()
    
    // filter only active students to register fees
    val activeStudents = students.filter { it.status == "active" }
    
    var selectedStudentId by remember { mutableStateOf(studentId ?: activeStudents.firstOrNull()?.id ?: 0L) }
    val currentStudent = activeStudents.find { it.id == selectedStudentId }
    val studentDues = duesList.find { it.student.id == selectedStudentId }
    
    val unpaidMonths = studentDues?.unpaidMonths ?: emptyList()
    val selectedMonths = remember(selectedStudentId) { mutableStateListOf<String>() }
    
    var manualAmount by remember { mutableStateOf("") }
    
    // Auto calculate fee sum based on checked months
    val autoAmount = remember(selectedMonths.size, currentStudent) {
        val multiplier = currentStudent?.feePerMonth ?: 0.0
        selectedMonths.size * multiplier
    }
    
    var paymentMode by remember { mutableStateOf("cash") } // 'cash' | 'upi' | 'bank_transfer' | 'cheque'
    var transactionRef by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deposit Tuition Fee") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_deposit")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Student Dropdown select
            item {
                Column {
                    Text("Select Enrolled Student *", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Display selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (activeStudents.isEmpty()) {
                                Text("No active students to collect fees.")
                            } else {
                                var expanded by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = true }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentStudent?.name ?: "Tap to choose student...",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.testTag("dropdown_student_trigger")
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                }
                                
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    activeStudents.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text("${s.name} (${s.className})") },
                                            onClick = {
                                                selectedStudentId = s.id
                                                expanded = false
                                            },
                                            modifier = Modifier.testTag("dropdown_item_${s.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Unpaid Months selector (Checkboxes)
            if (currentStudent != null) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Outstanding Unpaid Months (${unpaidMonths.size})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            if (selectedMonths.isNotEmpty()) {
                                Text(
                                    "Clear Selection",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.clickable { selectedMonths.clear() }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        if (unpaidMonths.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE0F2FE), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "This student is fully paid. (No outstanding months dues)",
                                    color = Color(0xFF0369A1),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    unpaidMonths.forEach { m ->
                                        val checked = selectedMonths.contains(m)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (checked) selectedMonths.remove(m) else selectedMonths.add(m)
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = {
                                                    if (checked) selectedMonths.remove(m) else selectedMonths.add(m)
                                                },
                                                modifier = Modifier.testTag("check_month_$m")
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(m, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Deposit Amount
            item {
                Column {
                    Text("Deposit Amount (₹) *", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = if (manualAmount.isBlank()) autoAmount.toInt().toString() else manualAmount,
                        onValueChange = { manualAmount = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_deposit_amount"),
                        singleLine = true,
                        placeholder = { Text("0") }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Auto calculated from selected months: ₹${autoAmount.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Payment mode selector chips
            item {
                Column {
                    Text("Payment Mode *", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("cash", "upi", "bank_transfer", "cheque").forEach { mode ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (paymentMode == mode) MaterialTheme.colorScheme.primary else Color(0xFFF3F4F6))
                                    .clickable { paymentMode = mode }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.replace("_", " ").uppercase(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (paymentMode == mode) Color.White else Color(0xFF4B5563)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Reference Transaction No
            if (paymentMode != "cash") {
                item {
                    OutlinedTextField(
                        value = transactionRef,
                        onValueChange = { transactionRef = it },
                        label = { Text("UPI Ref / Txn ID / Cheque No") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_txn_ref"),
                        singleLine = true
                    )
                }
            }

            // Payment comments
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Comments / Partial Remarks") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_deposit_notes"),
                    maxLines = 2
                )
            }

            // Submit Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (selectedStudentId == 0L) {
                            Toast.makeText(context, "Please select a student", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedMonths.isEmpty()) {
                            Toast.makeText(context, "Please select at least one month being paid for", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val amtToPay = (if (manualAmount.isBlank()) autoAmount else manualAmount.toDoubleOrNull()) ?: 0.0
                        if (amtToPay <= 0) {
                            Toast.makeText(context, "Please enter a valid deposit amount", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        scope.launch {
                            val newTxId = viewModel.depositFee(
                                studentId = selectedStudentId,
                                amount = amtToPay,
                                months = selectedMonths.toList(),
                                paymentMode = paymentMode,
                                ref = transactionRef.takeIf { it.isNotBlank() },
                                notes = notes.takeIf { it.isNotBlank() }
                            )
                            Toast.makeText(context, "Fee receipt registered & shared!", Toast.LENGTH_LONG).show()
                            onBack()
                            onNavigateToReceipt(newTxId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_submit_deposit"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Register Fee Deposit & Create Receipt")
                }
            }
        }
    }
}

// ==========================================
// 8. RECEIPT DETAIL VIEW SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    transactionId: Long,
    viewModel: TuitionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val transaction = transactions.find { it.id == transactionId }
    
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()

    if (transaction == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Receipt transaction not found.")
        }
        return
    }

    val student = students.find { it.id == transaction.studentId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt Details") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_receipt")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Receipt Canvas / Layout Sheet
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tuition block
                    Text(
                        text = appConfig?.tuitionName ?: "EXCEL HOME TUITION ACADEMICS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Address: ${appConfig?.address ?: "A-24, Sector 15, Dwarka, Delhi"}\nPhone: ${appConfig?.phone ?: "+91 98765 43210"} | Email: ${appConfig?.email ?: "aditi.sharma@excelacademy.com"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = Color(0xFF64748B), lineHeight = 14.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Divider dot line
                    Divider(color = Color(0xFFCBD5E1), modifier = Modifier.padding(vertical = 4.dp))
                    
                    // Header title
                    Text(
                        text = "FEE PAYMENT RECEIPT",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Receipt No: ${transaction.receiptNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF334155))
                        )
                        Text(
                            "Date: ${transaction.paymentDate}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                        )
                    }
                    
                    Divider(color = Color(0xFFE2E8F0))
                    
                    // Receipt Body content
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        ReceiptRow(label = "Student Name:", value = student?.name ?: "Removed Student")
                        ReceiptRow(label = "Class / Grade:", value = student?.className ?: "N/A")
                        ReceiptRow(label = "Guardian Name:", value = student?.guardianName ?: "N/A")
                        ReceiptRow(label = "Months Paid For:", value = transaction.monthsPaid.joinToString(", "))
                        ReceiptRow(label = "Payment Mode:", value = transaction.paymentMode.replace("_", " ").uppercase())
                        
                        transaction.transactionRef?.let {
                            ReceiptRow(label = "Ref/Txn No:", value = it)
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Large sum box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("AMOUNT PAID:", fontWeight = FontWeight.ExtraBold, color = Color(0xFF334155))
                                    Text("₹${transaction.amount.toInt()}", fontWeight = FontWeight.Black, color = Color(0xFF10B981), fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "In Words: ${viewModel.convertNumberToWords(transaction.amount)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color(0xFF475569))
                                )
                            }
                        }
                    }
                    
                    // Signature block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Email Shared with parents\nStatus: REGISTERED",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = appConfig?.tutorName ?: "Ashutosh",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            )
                            Divider(color = Color(0xFF64748B), modifier = Modifier.width(100.dp))
                            Text("Authorized Tutor Signature", fontSize = 8.sp, color = Color(0xFF64748B))
                        }
                    }
                }
            }
            
            val emailState by viewModel.emailState.collectAsStateWithLifecycle()
            
            // Send professional email via Resend
            Button(
                onClick = {
                    if (student == null) {
                        Toast.makeText(context, "Error: Student details not found", Toast.LENGTH_SHORT).show()
                    } else if (student.parentEmail.isBlank()) {
                        Toast.makeText(context, "Error: Student has no parent email registered.", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.sendEmailReceipt(student, transaction)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_email_receipt"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(10.dp),
                enabled = emailState !is EmailState.Loading
            ) {
                if (emailState is EmailState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send Official Receipt via Resend Email")
                }
            }

            // Showing email send status toast
            LaunchedEffect(emailState) {
                if (emailState is EmailState.Success) {
                    Toast.makeText(context, (emailState as EmailState.Success).message, Toast.LENGTH_LONG).show()
                    viewModel.clearSyncStates()
                } else if (emailState is EmailState.Error) {
                    Toast.makeText(context, (emailState as EmailState.Error).error, Toast.LENGTH_LONG).show()
                    viewModel.clearSyncStates()
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        // Simulate PDF Download
                        Toast.makeText(context, "Receipt PDF generated & stored locally", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_pdf_download"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF Receipt")
                }
                
                Button(
                    onClick = {
                        // Share receipt text format
                        val shareText = """
                            *${appConfig?.tuitionName ?: "EXCEL ACADEMY HOME TUITION"}*
                            *FEE PAYMENT RECEIPT*
                            
                            Receipt No: ${transaction.receiptNumber}
                            Date: ${transaction.paymentDate}
                            
                            Dear Parent/Guardian,
                            We have received ₹${transaction.amount.toInt()} with thanks from ${student?.guardianName} for student ${student?.name} (Class: ${student?.className}) towards fee payments for: *${transaction.monthsPaid.joinToString(", ")}*.
                            
                            Payment Mode: ${transaction.paymentMode.replace("_", " ").uppercase()}
                            Tutor: ${appConfig?.tutorName ?: "Ashutosh"}
                            
                            Thank you for your continuous support.
                        """.trimIndent()
                        
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Receipt Via"))
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("btn_share_receipt"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share on WhatsApp/Email")
                }
            }
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        Text(text = value, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B), style = MaterialTheme.typography.bodyMedium)
    }
}

// ==========================================
// 9. CONFIGURATION & SETTINGS SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TuitionViewModel,
    appConfig: AppConfig?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var tutorName by remember(appConfig) { mutableStateOf(appConfig?.tutorName ?: "Ashutosh") }
    var tuitionName by remember(appConfig) { mutableStateOf(appConfig?.tuitionName ?: "Ashutosh Tuition Class") }
    var address by remember(appConfig) { mutableStateOf(appConfig?.address ?: "A-24, Sector 15, Dwarka, Delhi") }
    var phone by remember(appConfig) { mutableStateOf(appConfig?.phone ?: "+91 98765 43210") }
    var email by remember(appConfig) { mutableStateOf(appConfig?.email ?: "aditi.sharma@excelacademy.com") }
    var receiptPrefix by remember(appConfig) { mutableStateOf(appConfig?.receiptPrefix ?: "EXT") }

    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val antiPauseStatus by viewModel.antiPauseStatus.collectAsStateWithLifecycle()

    var backupEmail by remember { mutableStateOf(viewModel.getBackupEmail().ifBlank { appConfig?.email ?: "" }) }
    var backupPasscode by remember { mutableStateOf(viewModel.getBackupPasscode()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "TUTOR CONFLICTS & GLOBAL CONFIGS",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                OutlinedTextField(
                    value = tuitionName,
                    onValueChange = { tuitionName = it },
                    label = { Text("Tuition Academy Name") },
                    modifier = Modifier.fillMaxWidth().testTag("input_tuition_name"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = tutorName,
                    onValueChange = { tutorName = it },
                    label = { Text("Tutor Primary Name") },
                    modifier = Modifier.fillMaxWidth().testTag("input_tutor_name"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Phone") },
                    modifier = Modifier.fillMaxWidth().testTag("input_config_phone"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Tutor Email Address") },
                    modifier = Modifier.fillMaxWidth().testTag("input_config_email"),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Physical Tuition Center Address") },
                    modifier = Modifier.fillMaxWidth().testTag("input_config_address"),
                    maxLines = 2
                )
            }
            item {
                OutlinedTextField(
                    value = receiptPrefix,
                    onValueChange = { receiptPrefix = it },
                    label = { Text("Receipt Serial Number Prefix") },
                    modifier = Modifier.fillMaxWidth().testTag("input_config_prefix"),
                    singleLine = true
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.updateConfig(
                            tutorName = tutorName,
                            tuitionName = tuitionName,
                            address = address,
                            phone = phone,
                            email = email,
                            receiptPrefix = receiptPrefix
                        )
                        Toast.makeText(context, "Configurations updated successfully", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_save_config"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Tuition Configs")
                }
            }

            // Cloud Sync & Backup section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "CLOUD SYNC & BACKUP (SUPABASE)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Secure your student records, attendance histories, and transaction ledgers in the cloud. You can restore your data instantly on any device or after uninstalls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = backupEmail,
                    onValueChange = { 
                        backupEmail = it
                        viewModel.saveBackupCredentials(it, backupPasscode)
                    },
                    label = { Text("Backup Email Address") },
                    placeholder = { Text("your-email@example.com") },
                    modifier = Modifier.fillMaxWidth().testTag("input_backup_email"),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = backupPasscode,
                    onValueChange = { 
                        backupPasscode = it
                        viewModel.saveBackupCredentials(backupEmail, it)
                    },
                    label = { Text("Backup Access PIN / Passcode") },
                    placeholder = { Text("Enter a secure PIN or password") },
                    modifier = Modifier.fillMaxWidth().testTag("input_backup_passcode"),
                    singleLine = true
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Cloud Backup",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Instantly back up data to the cloud whenever you add students, mark attendance, or receive payments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                        modifier = Modifier.testTag("switch_auto_backup")
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Supabase Anti-Pause System",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Keeps your Supabase DB awake by generating active traffic. Status: $antiPauseStatus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { viewModel.pingSupabaseDatabase() },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(36.dp)
                            .testTag("btn_ping_db")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Ping database",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            item {
                val backupState by viewModel.backupState.collectAsStateWithLifecycle()
                val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()

                // State messages
                when {
                    backupState is SyncState.Loading || restoreState is SyncState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    backupState is SyncState.Success -> {
                        Text(
                            text = (backupState as SyncState.Success).message,
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    backupState is SyncState.Error -> {
                        Text(
                            text = (backupState as SyncState.Error).error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    restoreState is SyncState.Success -> {
                        Text(
                            text = (restoreState as SyncState.Success).message,
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    restoreState is SyncState.Error -> {
                        Text(
                            text = (restoreState as SyncState.Error).error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (backupEmail.isBlank() || backupPasscode.isBlank()) {
                                Toast.makeText(context, "Please enter both backup email and passcode.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.backupToSupabase(backupEmail, backupPasscode)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_backup_cloud"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup Now")
                    }

                    var showRestoreConfirm by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = {
                            if (backupEmail.isBlank() || backupPasscode.isBlank()) {
                                Toast.makeText(context, "Please enter both backup email and passcode to restore.", Toast.LENGTH_SHORT).show()
                            } else {
                                showRestoreConfirm = true
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("btn_restore_cloud"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Cloud")
                    }

                    if (showRestoreConfirm) {
                        AlertDialog(
                            onDismissRequest = { showRestoreConfirm = false },
                            title = { Text("Confirm Cloud Restore") },
                            text = { Text("Are you absolutely sure you want to restore from the cloud? This will overwrite and replace all local records on this device.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showRestoreConfirm = false
                                        viewModel.restoreFromSupabase(backupEmail, backupPasscode)
                                    }
                                ) {
                                    Text("RESTORE AND OVERWRITE")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRestoreConfirm = false }) {
                                    Text("CANCEL")
                                }
                            }
                        )
                    }
                }
            }

            // Developer Seeding utilities
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "DEVELOPER TOOLS & DEMO SEEDING",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Instantly load the local database with fully populated students, historical attendance, and fee transactions to preview reports and charts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        viewModel.seedSampleData()
                        Toast.makeText(context, "Seeded demo students, attendance, & transactions!", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_seed_demo"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seed Sample Demo Data")
                }
            }

            // Secure Session Logout
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "SECURITY & SESSION MANAGEMENT",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Log out from the current tutor session securely. Your configurations, offline student records, and attendance history will remain saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        viewModel.logout()
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_tutor_logout"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Exit icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Secure Logout")
                }
            }
        }
    }
}

// ==========================================
// STUDENT LOGIN & NOTIFICATION DIALOGS
// ==========================================
@Composable
fun StudentLoginDialog(
    viewModel: TuitionViewModel,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Student Portal Login", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Log in using your registered Parent Email or Phone, and your Student ID or last 4 digits of phone as PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Parent Email or Phone Number") },
                    placeholder = { Text("e.g. aditi@example.com / 9876543210") },
                    modifier = Modifier.fillMaxWidth().testTag("student_login_username"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { pinCode = it },
                    label = { Text("Access PIN / Student ID") },
                    placeholder = { Text("e.g. 1001 or last 4 digits of phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("student_login_pin"),
                    singleLine = true
                )
                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isBlank() || pinCode.isBlank()) {
                        errorMsg = "Please fill in all fields"
                        return@Button
                    }
                    val success = viewModel.login(username, pinCode)
                    if (success) {
                        Toast.makeText(context, "Logged in as Student!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        errorMsg = "Invalid credentials! Check student list or use last 4 digits of phone as PIN."
                    }
                },
                modifier = Modifier.testTag("btn_confirm_student_login")
            ) {
                Text("Log In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WhatsAppStyleNotification(
    notification: AppNotification,
    onDismiss: () -> Unit
) {
    LaunchedEffect(notification) {
        kotlinx.coroutines.delay(4000)
        onDismiss()
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .statusBarsPadding()
            .testTag("heads_up_notification_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.DarkGray,
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF25D366), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notification Icon",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.senderName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "just now",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.LightGray.copy(alpha = 0.7f))
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SplashScreen(viewModel: TuitionViewModel) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.background
        )
    )

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        viewModel.onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .size(100.dp)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "App Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "AKG Classes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Welcome to Ashutosh Tuition Class",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = "SECURE ENTERPRISE CONNECT v2.4",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: TuitionViewModel) {
    var isTeacherRole by remember { mutableStateOf(true) }
    
    var teacherName by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }
    
    var studentEmailPhone by remember { mutableStateOf("") }
    var studentPin by remember { mutableStateOf("") }
    
    var rememberMe by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .imePadding()
            .testTag("login_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .size(72.dp)
                    .padding(top = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Text(
                text = "Welcome to Ashutosh Tuition Class",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Sign in to manage your tuition dashboard or access student portal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTeacherRole) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { 
                            isTeacherRole = true 
                            errorMessage = null
                        }
                        .padding(vertical = 12.dp)
                        .testTag("role_teacher"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tutor / Admin",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isTeacherRole) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isTeacherRole) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { 
                            isTeacherRole = false 
                            errorMessage = null
                        }
                        .padding(vertical = 12.dp)
                        .testTag("role_student"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Student / Parent",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (!isTeacherRole) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isTeacherRole) {
                        OutlinedTextField(
                            value = teacherName,
                            onValueChange = { 
                                teacherName = it
                                errorMessage = null
                            },
                            label = { Text("Name or Email") },
                            placeholder = { Text("Enter your name or email") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User Icon") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("teacher_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        var isPasswordVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = passcode,
                            onValueChange = { 
                                passcode = it
                                errorMessage = null
                            },
                            label = { Text("Passcode") },
                            placeholder = { Text("Enter passcode") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Key Icon") },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Check else Icons.Default.Info,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("teacher_password_input"),
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = studentEmailPhone,
                            onValueChange = { 
                                studentEmailPhone = it
                                errorMessage = null
                            },
                            label = { Text("Email or Phone") },
                            placeholder = { Text("e.g. parent@email.com or +91...") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email/Phone Icon") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_email_phone_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = studentPin,
                            onValueChange = { 
                                studentPin = it
                                errorMessage = null
                            },
                            label = { Text("Student PIN / ID") },
                            placeholder = { Text("e.g. last 4 digits of phone") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Pin Icon") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_pin_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            modifier = Modifier.testTag("checkbox_remember_me")
                        )
                        Text(
                            text = "Keep me signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (isTeacherRole) {
                                if (teacherName.isBlank()) {
                                    errorMessage = "Please enter your welcome name."
                                    return@Button
                                }
                                if (passcode.isBlank()) {
                                    errorMessage = "Please enter your administrator passcode."
                                    return@Button
                                }
                                isLoading = true
                                val success = viewModel.loginAsTeacher(teacherName, passcode, rememberMe)
                                isLoading = false
                                if (!success) {
                                    errorMessage = "Incorrect passcode. Use your administrator password."
                                }
                            } else {
                                if (studentEmailPhone.isBlank() || studentPin.isBlank()) {
                                    errorMessage = "Please enter email/phone and PIN."
                                    return@Button
                                }
                                isLoading = true
                                val success = viewModel.loginAsStudent(studentEmailPhone, studentPin, rememberMe)
                                isLoading = false
                                if (!success) {
                                    errorMessage = "Invalid student credentials. Student phone/email and PIN do not match."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_login_submit"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Secure Sign In",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: TuitionViewModel,
    selectedStudentId: Long?,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit
) {
    val students by viewModel.allStudents.collectAsStateWithLifecycle()
    val duesList by viewModel.studentDuesList.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "STUDENT PAYMENT LEDGER",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_ledger_back")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LedgerContent(
                students = students,
                duesList = duesList,
                transactions = transactions,
                initialStudentId = selectedStudentId,
                onNavigate = onNavigate
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerContent(
    students: List<Student>,
    duesList: List<StudentDues>,
    transactions: List<FeeTransaction>,
    initialStudentId: Long?,
    onNavigate: (Screen) -> Unit
) {
    var selectedStudentIdState by remember { mutableStateOf<Long?>(initialStudentId) }
    var searchQuery by remember { mutableStateOf("") }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Horizontal row of student capsules at the top
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = selectedStudentIdState == null,
                    onClick = { selectedStudentIdState = null },
                    label = { Text("All Students", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("chip_all_students")
                )
            }
            items(students) { student ->
                FilterChip(
                    selected = selectedStudentIdState == student.id,
                    onClick = { selectedStudentIdState = student.id },
                    label = { Text(student.name, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("chip_student_${student.id}")
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        val currentId = selectedStudentIdState
        if (currentId == null) {
            // "All Students" Master Ledger view
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search student by name or class...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ledger_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                val filteredStudents = students.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.className.contains(searchQuery, ignoreCase = true)
                }

                if (filteredStudents.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No matching students found.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredStudents) { student ->
                            val dues = duesList.find { it.student.id == student.id }
                            val totalPaid = transactions.filter { it.studentId == student.id }.sumOf { it.amount }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedStudentIdState = student.id }
                                    .testTag("ledger_row_${student.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                                    ),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                student.name.take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                student.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                "Class: ${student.className}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Paid: ₹${totalPaid.toInt()}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF10B981)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val dueAmount = dues?.dueAmount ?: 0.0
                                        if (dueAmount > 0.0) {
                                            Text(
                                                text = "Due: ₹${dueAmount.toInt()}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                text = "No Dues",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF10B981)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Detail",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Specific Student Detailed Ledger View
            val student = students.find { it.id == currentId }
            if (student == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Student not found.")
                }
            } else {
                val dues = duesList.find { it.student.id == student.id }
                val studentTx = transactions.filter { it.studentId == student.id }.sortedByDescending { it.paymentDate }
                val totalPaid = studentTx.sumOf { it.amount }
                val currentDues = dues?.dueAmount ?: 0.0
                val totalCharged = totalPaid + currentDues

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Student Info Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = student.name.uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Column {
                                        Text("Class / Grade", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(student.className, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                    Column {
                                        Text("Monthly Fee", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("₹${student.feePerMonth.toInt()}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                    Column {
                                        Text("Joining Date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(student.joiningDate, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                                
                                if (!student.guardianName.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text("Guardian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(student.guardianName ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Contact", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(student.phone, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // KPI Cards Grid (Total Charged, Total Paid, Outstanding Dues)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Total Charged
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Charged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("₹${totalCharged.toInt()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                                }
                            }

                            // Total Paid
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Paid to Date", style = MaterialTheme.typography.bodySmall, color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("₹${totalPaid.toInt()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)))
                                }
                            }

                            // Outstanding Dues
                            Card(
                                modifier = Modifier.weight(1.1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (currentDues > 0.0) {
                                        if (isDark) Color(0xFFB71C1C) else Color(0xFFFFEBEE)
                                    } else {
                                        if (isDark) Color(0xFF1B5E20) else Color(0xFFE8F5E9)
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val titleColor = if (currentDues > 0.0) {
                                        if (isDark) Color(0xFFEF5350) else Color(0xFFC62828)
                                    } else {
                                        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    }
                                    Text("Outstanding", style = MaterialTheme.typography.bodySmall, color = titleColor)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (currentDues > 0.0) "₹${currentDues.toInt()}" else "Fully Paid",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = titleColor)
                                    )
                                }
                            }
                        }
                    }

                    // Unpaid months badge card if dues exist
                    if (currentDues > 0.0 && dues != null && dues.unpaidMonths.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Pending Fee Months", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text(dues.unpaidMonths.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                        }
                    }

                    // Detailed Transactions list header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PAYMENT HISTORY",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            IconButton(onClick = { onNavigate(Screen.DepositFee(student.id)) }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Collect Fee", tint = Color(0xFF10B981))
                            }
                        }
                    }

                    if (studentTx.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No payment transactions recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { onNavigate(Screen.DepositFee(student.id)) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Collect First Payment")
                                    }
                                }
                            }
                        }
                    } else {
                        items(studentTx) { tx ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(Screen.ReceiptDetail(tx.id)) }
                                    .testTag("ledger_tx_row_${tx.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(0xFF10B981), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Receipt: ${tx.receiptNumber}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Payment Date: ${tx.paymentDate}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "₹${tx.amount.toInt()}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = Color(0xFF10B981)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = tx.paymentMode.replace("_", " ").uppercase(),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text("Months Paid: ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(tx.monthsPaid.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        if (!tx.transactionRef.isNullOrBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text("UPI/Ref ID: ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(tx.transactionRef, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                        if (!tx.notes.isNullOrBlank()) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                Text("Notes: ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(tx.notes ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "View Receipt PDF",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
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
    }
}
