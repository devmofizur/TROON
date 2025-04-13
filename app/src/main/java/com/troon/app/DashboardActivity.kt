package com.troon.app

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.util.*
import com.google.firebase.auth.FirebaseAuth



class DashboardActivity : ComponentActivity() {
    private lateinit var displayName: String
    private lateinit var photoUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        // Retrieve the passed Google Account details
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

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showPermissionDialog(onProceed: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To display app usage statistics, TROON needs permission to access usage data. Please allow this in the next screen.")
            .setPositiveButton("Allow") { _, _ -> onProceed() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun promptForUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }

    private fun showUsageStats() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        ).filter { it.totalTimeInForeground > 0 }

        if (usageStatsList.isNotEmpty()) {
            val sortedList = usageStatsList.sortedByDescending { it.totalTimeInForeground }
            setContent {
                StatisticsDashboard(displayName, photoUrl, sortedList)
            }
        } else {
            Toast.makeText(this, "No usage stats available", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsDashboard(displayName: String, photoUrl: String, usageStatsList: List<UsageStats>) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val totalUsage = usageStatsList.sumOf { it.totalTimeInForeground }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("TROON", fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            fontFamily = FontFamily(Font(R.font.bungee_spice)),//custom font
                            color = Color.Black
                        )
                    )

                },
                actions = {
                    ProfileMenu(name = displayName, photoUrl = photoUrl)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "App Usage (24h)",
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp),
                style = TextStyle(
                    fontFamily = FontFamily(Font(R.font.google_sans)), // Use Google Sans font
                    color = Color.Black // Text color set to black
                )
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(Color(0xFFFAFAFA))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp)
                ) {
                    items(usageStatsList) { usageStats ->
                        val appName = try {
                            val appInfo: ApplicationInfo =
                                packageManager.getApplicationInfo(usageStats.packageName, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }

                        val icon: Drawable? = try {
                            packageManager.getApplicationIcon(usageStats.packageName)
                        } catch (e: Exception) {
                            null
                        }

                        val usageTime = usageStats.totalTimeInForeground
                        val progress = usageTime.toFloat() / maxOf(totalUsage, 1)

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
    }
}

@Composable
fun ProfileMenu(name: String, photoUrl: String) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = name,
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
                    onClick = {
                        expanded = false
                        // TODO: Navigate to settings
                    }
                )
                DropdownMenuItem(
                    text = { Text("Membership") },
                    onClick = {
                        expanded = false
                        // TODO: Navigate to membership
                    }
                )
                DropdownMenuItem(
                    text = { Text("Logout") },
                    onClick = {
                        expanded = false
                        // Sign out from Firebase
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

private fun ColumnScope.finish() {
    TODO("Not yet implemented")
}

@Composable
fun StatCard(appName: String, usageTime: String, progress: Float, icon: Drawable?) {
    val animatedProgress by animateFloatAsState(targetValue = progress)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp) // Reduced vertical padding
        , shape = RoundedCornerShape(8.dp),
        elevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp) // Reduced padding
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = appName,
                    modifier = Modifier
                        .size(20.dp)  // Smaller icon size
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(8.dp)) // Reduced horizontal spacing
            Column(modifier = Modifier.weight(1f)) {
                Text(text = appName, fontSize = 14.sp, fontWeight = FontWeight.Bold)  // Smaller font size
                Text(text = usageTime, fontSize = 12.sp)  // Smaller font size
            }
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp) // Reduced width of the progress bar
                    .padding(end = 4.dp)  // Reduced right padding
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
