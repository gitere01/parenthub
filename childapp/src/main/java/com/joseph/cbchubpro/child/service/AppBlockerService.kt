package com.joseph.cbchubpro.child.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.joseph.cbchubpro.child.ScreenTimePolicy
import java.util.Calendar

class AppBlockerService : AccessibilityService() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var blockedPackages = setOf<String>()
    private var blockedTvApps = setOf<String>()
    private var isLocked = false
    private var isTvLocked = false
    private var blockAppInstalls = false
    private var dailyTimeLimitMinutes = ScreenTimePolicy.DEFAULT_DAILY_LIMIT_MINUTES
    private var screenTimeSchedule: Map<*, *>? = null
    private var tvScreenTimeSchedule: Map<*, *>? = null
    private var availablePackages = emptySet<String>()
    private var cachedDailyUsageMillis = 0L
    private var lastDailyUsageReadAt = 0L
    private val homeScreenPackage: String? by lazy {
        try {
            packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
    }
    private var tvTimeLimitMinutes = 0L
    private var tvUsageTodayMinutes = 0L
    
    private var controlListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private val enforcementHandler = Handler(Looper.getMainLooper())
    private val enforcementTask = object : Runnable {
        override fun run() {
            enforceForegroundRestrictions()
            enforcementHandler.postDelayed(this, 30_000L)
        }
    }
    private val tvUsageTask = object : Runnable {
        override fun run() {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                tvUsageTodayMinutes = currentDailyUsageMinutes()
                db.collection("users").document(uid).update("tvUsageMinutesToday", tvUsageTodayMinutes)
                enforceForegroundRestrictions()
            }
            enforcementHandler.postDelayed(this, 60_000L)
        }
    }

    private val installerPackages = setOf(
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.settings", "com.android.vending", "com.google.android.vending",
        "com.samsung.android.packageinstaller", "com.sec.android.app.samsungapps",
        "com.miui.packageinstaller", "com.coloros.packageinstaller"
    )

    private fun isTv(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || 
               packageManager.hasSystemFeature("android.software.leanback")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        availablePackages = ScreenTimePolicy.alwaysAvailablePackages(this)
        authListener = FirebaseAuth.AuthStateListener { it.currentUser?.uid?.let { uid -> listenToControlData(uid) } }
        auth.addAuthStateListener(authListener!!)
        auth.currentUser?.uid?.let { listenToControlData(it) }
        enforcementHandler.post(enforcementTask)
        
        if (isTv()) enforcementHandler.post(tvUsageTask)
    }

    private fun listenToControlData(uid: String) {
        controlListener?.remove()
        controlListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    blockedPackages = (snapshot.get("blockedApps") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    blockedTvApps = (snapshot.get("blockedTvApps") as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    isLocked = snapshot.getBoolean("locked") ?: false
                    isTvLocked = snapshot.getBoolean("tvLocked") ?: false
                    blockAppInstalls = snapshot.getBoolean("blockAppInstalls") ?: false
                    dailyTimeLimitMinutes = snapshot.getLong("dailyTimeLimit")
                        ?: ScreenTimePolicy.DEFAULT_DAILY_LIMIT_MINUTES
                    screenTimeSchedule = snapshot.get("screenTimeSchedule") as? Map<*, *>
                    tvScreenTimeSchedule = snapshot.get("tvScreenTimeSchedule") as? Map<*, *>
                    tvTimeLimitMinutes = snapshot.getLong("tvDailyTimeLimit") ?: 120L
                    
                    // Fetch usage from snapshot if present
                    tvUsageTodayMinutes = snapshot.getLong("tvUsageMinutesToday") ?: 0L

                    enforceForegroundRestrictions()
                    
                    if (snapshot.contains("loudSignalTrigger")) {
                        playLoudSignal()
                        db.collection("users").document(uid).update("loudSignalTrigger", FieldValue.delete())
                    }
                }
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isDeviceTv = isTv()

        val packageName = event.packageName?.toString() ?: return
        if (isAlwaysAvailable(packageName) || isHomeScreen(packageName)) return

        // Apply daily limits, scheduled downtime, and parent-initiated locks on the child device.
        if (isRestrictionActive(isDeviceTv)) {
            blockAccess()
            return
        }

        // Explicitly blocked apps remain blocked even outside scheduled downtime.
        if (blockedPackages.contains(packageName) || (isDeviceTv && blockedTvApps.contains(packageName))) {
            blockAccess()
            return
        }

        // Block Installations
        if (blockAppInstalls && installerPackages.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: event.source
            if (shouldBlockInstallation(rootNode, packageName)) {
                blockAccess()
                return
            }
        }
    }

    private fun isAlwaysAvailable(candidate: String): Boolean =
        candidate in availablePackages

    private fun isHomeScreen(candidate: String): Boolean = candidate == homeScreenPackage

    private fun isRestrictionActive(isDeviceTv: Boolean = isTv()): Boolean {
        if (isLocked) return true
        if (isDeviceTv) {
            val scheduleActive = ScreenTimePolicy.isScheduleActive(tvScreenTimeSchedule)
            val limitReached = tvTimeLimitMinutes > 0 && currentDailyUsageMinutes() >= tvTimeLimitMinutes
            return isTvLocked || scheduleActive || limitReached
        }
        val scheduleActive = ScreenTimePolicy.isScheduleActive(screenTimeSchedule)
        val limitReached = dailyTimeLimitMinutes > 0 && isDailyLimitReached()
        return scheduleActive || limitReached
    }

    private fun isDailyLimitReached(): Boolean {
        return dailyTimeLimitMinutes > 0 && currentDailyUsageMinutes() >= dailyTimeLimitMinutes
    }

    private fun currentDailyUsageMinutes(): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastDailyUsageReadAt >= 30_000L || now < lastDailyUsageReadAt) {
            cachedDailyUsageMillis = ScreenTimePolicy.todayUsageMillis(this)
            lastDailyUsageReadAt = now
        }
        return cachedDailyUsageMillis / 60_000L
    }

    private fun enforceForegroundRestrictions() {
        val foregroundPackage = rootInActiveWindow?.packageName?.toString() ?: return
        if (isAlwaysAvailable(foregroundPackage) || isHomeScreen(foregroundPackage)) return
        if (isRestrictionActive()) blockAccess()
    }

    private fun shouldBlockInstallation(root: AccessibilityNodeInfo?, pkg: String): Boolean {
        if (root == null) return false
        val installIds = listOf("com.android.vending:id/install_button", "com.android.vending:id/buy_button")
        for (id in installIds) if (root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()) return true
        val keywords = listOf("Install", "Download", "Update", "Get")
        for (word in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(word)
            if (!nodes.isNullOrEmpty()) for (node in nodes) if (node.isClickable || node.parent?.isClickable == true) return true
        }
        return false
    }

    private fun blockAccess() {
        startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
    }

    private var mediaPlayer: MediaPlayer? = null
    private fun playLoudSignal() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AppBlockerService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                isLooping = true
                prepare()
                start()
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ mediaPlayer?.release(); mediaPlayer = null }, 10000)
        } catch (e: Exception) { Log.e("AppBlocker", "Error", e) }
    }

    override fun onDestroy() {
        super.onDestroy()
        enforcementHandler.removeCallbacks(enforcementTask)
        enforcementHandler.removeCallbacks(tvUsageTask)
        authListener?.let { auth.removeAuthStateListener(it) }
        controlListener?.remove()
        mediaPlayer?.release()
    }

    override fun onInterrupt() {}
}
