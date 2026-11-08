package com.joseph.cbchubpro.child

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import java.util.Calendar

/** Shared screen-time calculations used by the child dashboard and blocker service. */
object ScreenTimePolicy {
    const val DEFAULT_DAILY_LIMIT_MINUTES = 120L

    fun todayUsageMillis(context: Context): Long {
        return try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val statsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = statsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                calendar.timeInMillis,
                System.currentTimeMillis()
            ).orEmpty()
            val alwaysAvailable = alwaysAvailablePackages(context)
            stats.filterNot { it.packageName in alwaysAvailable }
                .sumOf { it.totalTimeInForeground }
        } catch (_: Exception) {
            0L
        }
    }

    /** Keep emergency communication and the child app available during limits and schedules. */
    fun isAlwaysAvailablePackage(context: Context, candidate: String): Boolean {
        return candidate in alwaysAvailablePackages(context)
    }

    fun alwaysAvailablePackages(context: Context): Set<String> {
        val available = mutableSetOf(context.packageName, "com.android.emergency")
        val dialer = try {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
        val sms = try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Exception) {
            null
        }
        if (dialer != null) available.add(dialer)
        if (sms != null) available.add(sms)
        return available
    }

    fun isScheduleActive(schedule: Map<*, *>?, now: Calendar = Calendar.getInstance()): Boolean {
        if (schedule?.get("enabled") != true) return false
        val start = (schedule["startMinute"] as? Number)?.toInt()?.coerceIn(0, 1439) ?: return false
        val end = (schedule["endMinute"] as? Number)?.toInt()?.coerceIn(0, 1439) ?: return false
        val days = (schedule["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet().orEmpty()
        if (days.isEmpty()) return false

        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        val currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (start == end) return currentDay in days
        if (start < end) return currentDay in days && currentMinute in start until end

        // For an overnight window, the after-midnight portion belongs to yesterday's schedule.
        if (currentMinute >= start) return currentDay in days
        if (currentMinute < end) {
            val previousDay = (currentDay + 5) % 7 + 1 // Calendar days are Sunday=1 through Saturday=7.
            return previousDay in days
        }
        return false
    }
}
