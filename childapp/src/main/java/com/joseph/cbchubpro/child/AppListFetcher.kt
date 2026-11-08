package com.joseph.cbchubpro.child

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val usageTimeMillis: Long = 0
)

class AppListFetcher(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun isTv(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || 
               context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    fun fetchAndUploadAppList() {
        val uid = auth.currentUser?.uid ?: return
        val isTvDevice = isTv()
        
        Log.d("AppListFetcher", "Starting app sync for ${if(isTvDevice) "TV" else "Phone"} Device (UID: $uid)")
        val pm = context.packageManager
        
        val usageStatsMap = try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (e: Exception) {
            emptyMap()
        }

        val allApps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList<ApplicationInfo>()
        }
        
        val appList = mutableListOf<AppInfo>()

        for (appInfo in allApps) {
            val packageName = appInfo.packageName
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLauncher = pm.getLaunchIntentForPackage(packageName) != null
            
            if (!isSystem || isUpdatedSystem || hasLauncher) {
                try {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val usageTime = usageStatsMap[packageName]?.totalTimeInForeground ?: 0L
                    appList.add(AppInfo(label, packageName, isSystem, usageTime))
                } catch (e: Exception) {}
            }
        }

        if (appList.isNotEmpty()) {
            val appData = appList.distinctBy { it.packageName }.map {
                mapOf(
                    "name" to it.name,
                    "packageName" to it.packageName,
                    "isSystemApp" to it.isSystemApp,
                    "usageTime" to it.usageTimeMillis
                )
            }

            // Sync to standard list AND tvApps if it's a TV
            val updates = mutableMapOf<String, Any>("installedApps" to appData)
            if (isTvDevice) {
                updates["tvApps"] = appData
            }

            db.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener { Log.i("AppListFetcher", "Synced ${appData.size} apps.") }
        }
    }
}
