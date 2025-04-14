package com.troon.app

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.provider.CallLog
import android.provider.Settings
import android.provider.Telephony
import android.app.NotificationManager
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.util.*


class DashboardActivity : ComponentActivity() {

    private lateinit var displayName: String
    private lateinit var photoUrl: String
    private var contentShown = false

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        displayName = intent.getStringExtra("displayName") ?: "User"
        photoUrl = intent.getStringExtra("photoUrl") ?: ""
        if (isUsageStatsPermissionGranted()) {
            showUsageStats()
        } else {
            showPermissionDialog {
                promptForUsageStatsPermission()
            }
        }
    }
    override fun onResume() {
        super.onResume()

        if (isUsageStatsPermissionGranted() && !contentShown) {
            showUsageStats()
        }
    }


    private fun isUsageStatsPermissionGranted(): Boolean {

        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            ) == AppOpsManager.MODE_ALLOWED
        }

    }

    private fun showPermissionDialog(onProceed: () -> Unit) {
        try {
            AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat)
                .setTitle("Permission Required")
                .setMessage("To display app usage statistics, TROON needs permission to access usage data. Please allow this in the next screen.")
                .setPositiveButton("Allow") { _, _ ->
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    startActivity(intent)
                    onProceed()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Dialog failed to show: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun promptForUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    }

    private fun showUsageStats() {

        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()

        ).filter { it.totalTimeInForeground > 0 }

        if (usageStatsList.isNotEmpty()) {
            val sortedList = usageStatsList.sortedByDescending { it.totalTimeInForeground }
            if (!contentShown) {
                contentShown = true
                setContent {

                    StatisticsDashboard(displayName, photoUrl, sortedList)
                }
            }

        } else {
            Toast.makeText(this, "No usage stats available", Toast.LENGTH_SHORT).show()
        }
    }


}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsDashboard(displayName: String, photoUrl: String, usageStatsList: List<UsageStats>) {
    val requiredPermissions = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
    )

    val context = LocalContext.current
    val packageManager = context.packageManager
    val totalUsage = usageStatsList.sumOf { it.totalTimeInForeground }

    // States to hold call, sms, notification counts
    var callCount by remember { mutableStateOf(0) }
    var smsCount by remember { mutableStateOf(0) }
    var notificationCount by remember { mutableStateOf(0) }

    // Launcher for requesting permissions
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // When user responds to permission request
        if (permissions.values.all { it }) {
            // All permissions granted
            callCount = getCallCount(context)
            smsCount = getSmsCount(context)
            notificationCount = getNotificationCount(context)
        } else {
            Toast.makeText(context, "Please grant all permissions.", Toast.LENGTH_SHORT).show()
        }
    }

    // Request permissions on screen launch
    LaunchedEffect(Unit) {
        launcher.launch(requiredPermissions)
    }

    Scaffold(
        topBar = { TopBarWithProfile(displayName = displayName, photoUrl = photoUrl) },
        bottomBar = {
            FooterButtonRow(
                onDashboardClick = {
                    Toast.makeText(context, "Dashboard clicked", Toast.LENGTH_SHORT).show()
                },
                onFeaturesClick = {
                    Toast.makeText(context, "Features clicked", Toast.LENGTH_SHORT).show()
                },
                onNoticeClick = {
                    Toast.makeText(context, "Notice clicked", Toast.LENGTH_SHORT).show()
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFBFC9B2))
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            ScreenTimeCard(totalMinutes = 360, usedMinutes = 240)
            AppUsageCard(usageStatsList, packageManager, totalUsage)
            CallsMessagesBox(callCount, smsCount, notificationCount)
        }
    }
}

fun getCallCount(context: Context): Int {
    val callUri = CallLog.Calls.CONTENT_URI
    val cursor = context.contentResolver.query(
        callUri,
        arrayOf(CallLog.Calls._ID),
        null,
        null,
        CallLog.Calls.DATE + " DESC"
    )
    return cursor?.count ?: 0
}

fun getSmsCount(context: Context): Int {
    val smsUri = Telephony.Sms.CONTENT_URI
    val cursor = context.contentResolver.query(
        smsUri,
        arrayOf(Telephony.Sms._ID),
        null,
        null,
        Telephony.Sms.DATE + " DESC"
    )
    return cursor?.count ?: 0
}
fun getNotificationCount(context: Context): Int {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // NotificationManager returns a list of active notifications
    val activeNotifications = notificationManager.activeNotifications
    return activeNotifications.size
}

@Composable
fun TopBarWithProfile(displayName: String, photoUrl: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(Color(0xFF5F8B4C)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TROON",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    fontFamily = FontFamily(Font(R.font.bungee_spice)),
                    color = Color.Black
                )
            )
            ProfileMenu(name = displayName, photoUrl = photoUrl)
        }
    }
}
@Composable
fun ProfileMenu(name: String, photoUrl: String) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 8.dp),
            style = TextStyle(
                fontFamily = FontFamily(Font(R.font.google_sans)),
                color = Color.Black
            )
        )

        Box {
            IconButton(onClick = { expanded = true }) {
                if (photoUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(photoUrl),
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = { expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Membership") },
                    onClick = { expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        expanded = false
                        logout(context)
                    }
                )
            }
        }




    }
}

fun logout(context: Context) {
    val auth = FirebaseAuth.getInstance()
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("624747205743-ak70beh3mcljkioomll2rkffvncgvn3u.apps.googleusercontent.com")
        .requestEmail()
        .build()

    val googleSignInClient = GoogleSignIn.getClient(context, gso)
    auth.signOut()
    googleSignInClient.signOut().addOnCompleteListener {
        val activity = context as? ComponentActivity
        val intent = Intent(context, MainActivity::class.java)
        context.startActivity(intent)
        activity?.finish()
    }
}

@Composable
fun StatCard(appName: String, usageTime: String, progress: Float, icon: Drawable?) {
    val animatedProgress by animateFloatAsState(targetValue = progress)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)

        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = appName,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = usageTime, fontSize = 12.sp)
            }
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .padding(end = 4.dp),
                color = Color(0xFF2B652C),
            )
        }
    }
}

fun formatTime(milliseconds: Long): String {
    val hours = milliseconds / 3600000
    val minutes = (milliseconds % 3600000) / 60000
    val seconds = (milliseconds % 60000) / 1000
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

@Composable
fun ScreenTimeCard(totalMinutes: Int, usedMinutes: Int) {
    val remainingMinutes = (totalMinutes - usedMinutes).coerceAtLeast(0)
    val progress = usedMinutes.toFloat() / totalMinutes.coerceAtLeast(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Screen Time Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Total", color = Color.Gray, fontSize = 12.sp)
                    Text("${totalMinutes / 60}h ${totalMinutes % 60}m", fontWeight = FontWeight.Medium)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Remaining", color = Color.Gray, fontSize = 12.sp)
                    Text("${remainingMinutes / 60}h ${remainingMinutes % 60}m", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
@Composable
fun AppUsageCard(usageStatsList: List<UsageStats>, packageManager: PackageManager, totalUsage: Long) {
    // Text header for App Usage
    Text(
        text = "App Usage (24h)",
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        modifier = Modifier.padding(8.dp),
        style = TextStyle(
            fontFamily = FontFamily(Font(R.font.bungee_tint)),
            color = Color.Black
        )
    )

    // Card containing LazyColumn of app usage stats
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(Color(0xFFFAFAFA))
    ) {
        LazyColumn(modifier = Modifier.padding(10.dp)) {
            items(usageStatsList) { usageStats ->
                // Fetching app name
                val appName = try {
                    val appInfo: ApplicationInfo =
                        packageManager.getApplicationInfo(usageStats.packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }

                // Fetching app icon
                val icon: Drawable? = try {
                    packageManager.getApplicationIcon(usageStats.packageName)
                } catch (e: Exception) {
                    null
                }

                // Calculating usage time and progress
                val usageTime = usageStats.totalTimeInForeground
                val progress = usageTime.toFloat() / maxOf(totalUsage, 1)

                // Displaying the StatCard if app name is found
                if (appName != null) {
                    StatCard(
                        appName = appName,
                        usageTime = formatTime(usageTime),
                        progress = progress,
                        icon = icon
                    )
                }
            }
        }
    }
}
@Composable
fun CallsMessagesBox(callCount: Int, smsCount: Int, notificationCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Activity",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                style = TextStyle(
                    fontFamily = FontFamily(Font(R.font.bungee_tint)),
                    color = Color.Black
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Call Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .background(Color(0xFFE0F7FA), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Calls",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Calls", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$callCount", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // SMS Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .background(Color(0xFFFFF9C4), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "Messages",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Messages", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$smsCount", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Notification Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp)
                        .background(Color(0xFFF1F8E9), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Notifications", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$notificationCount", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
fun FooterButtonRow(
    onDashboardClick: () -> Unit,
    onFeaturesClick: () -> Unit,
    onNoticeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFF6920EF)), // Customize the background color as needed
        horizontalArrangement = Arrangement.Center,  // Center the buttons horizontally
        verticalAlignment = Alignment.CenterVertically  // Align the buttons vertically in the center
    ) {
        // Dashboard Button
        FooterButton(
            imageRes = R.drawable.ic_dashboard, // Use your dashboard icon here
            contentDescription = "Dashboard"
        ) {
            onDashboardClick() // Handle Dashboard click
        }

        Spacer(modifier = Modifier.width(30.dp)) // Reduce the space between buttons if necessary

        // Features Button
        FooterButton(
            imageRes = R.drawable.ic_features, // Use your features icon here
            contentDescription = "Features"
        ) {
            onFeaturesClick() // Handle Features click
        }

        Spacer(modifier = Modifier.width(30.dp)) // Reduce the space between buttons if necessary

        // Notice Button
        FooterButton(
            imageRes = R.drawable.ic_notice, // Use your notice icon here
            contentDescription = "Notice"
        ) {
            onNoticeClick() // Handle Notice click
        }
    }
}

@Composable
fun FooterButton(imageRes: Int, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(45.dp)  // Set the size for the button
            .clip(RoundedCornerShape(12.dp))  // Rounded corners for the button
            .background(Color(0xFFFFFFF))  // Button background color
            .padding(8.dp)  // Padding inside the button to prevent the icon from touching edges
            .indication(interactionSource, LocalIndication.current) // Use indication to handle ripple
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()  // Ensure the image fills the entire button
            // Don't clip the image, keep it in its original shape
        )

    }
}