package com.troon.app

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.util.*
import kotlin.math.min

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isUsageStatsPermissionGranted()) {
            showUsageStats()
        } else {
            promptForUsageStatsPermission()
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
                StatisticsDashboard(sortedList)
            }
        } else {
            Toast.makeText(this, "No usage stats available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptForUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun StatisticsDashboard(usageStatsList: List<UsageStats>) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val totalUsage = usageStatsList.sumOf { it.totalTimeInForeground }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "App Usage (24h)",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Scrollable GroupBox-style container (half-screen height)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(16.dp)),
            elevation = 8.dp,
            backgroundColor = Color(0xFFFAFAFA)
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
                        usageStats.packageName
                    }

                    val icon: Drawable? = try {
                        packageManager.getApplicationIcon(usageStats.packageName)
                    } catch (e: Exception) {
                        null
                    }

                    val usageTime = usageStats.totalTimeInForeground
                    val progress = usageTime.toFloat() / maxOf(totalUsage, 1)

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
fun StatCard(appName: String, usageTime: String, progress: Float, icon: Drawable?) {
    val animatedProgress by animateFloatAsState(targetValue = min(progress, 1f))

    Card(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        elevation = 4.dp,
        backgroundColor = Color(0xFFF5F5F5)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Image(
                            bitmap = it.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = appName,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                Text(
                    text = usageTime,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun formatTime(timeInMillis: Long): String {
    val hours = (timeInMillis / (1000 * 60 * 60)) % 24
    val minutes = (timeInMillis / (1000 * 60)) % 60
    return "%02dh %02dm".format(hours, minutes)
}
