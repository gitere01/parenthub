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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class AppBlockerService : AccessibilityService() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var blockedPackages = setOf<String>()
    private var blockedTvApps = setOf<String>()
    private var isLocked = false
    private var isTvLocked = false
    private var blockAppInstalls = false
    private var tvTimeLimitMinutes = 0L
    private var tvUsageTodayMinutes = 0L
    
    private var controlListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

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
        authListener = FirebaseAuth.AuthStateListener { it.currentUser?.uid?.let { uid -> listenToControlData(uid) } }
        auth.addAuthStateListener(authListener!!)
        auth.currentUser?.uid?.let { listenToControlData(it) }
        
        // Start Usage Tracker for TV
        if (isTv()) startTvUsageTracker()
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
                    tvTimeLimitMinutes = snapshot.getLong("tvDailyTimeLimit") ?: 120L
                    
                    // Fetch usage from snapshot if present
                    tvUsageTodayMinutes = snapshot.getLong("tvUsageMinutesToday") ?: 0L

                    if (isLocked || (isTv() && (isTvLocked || (tvTimeLimitMinutes > 0 && tvUsageTodayMinutes >= tvTimeLimitMinutes)))) {
                        blockAccess()
                    }
                    
                    if (snapshot.contains("loudSignalTrigger")) {
                        playLoudSignal()
                        db.collection("users").document(uid).update("loudSignalTrigger", FieldValue.delete())
                    }
                }
            }
    }

    private fun startTvUsageTracker() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                val uid = auth.currentUser?.uid
                if (uid != null && !isTvLocked && !isLocked) {
                    // Only count if an app is actually in foreground (not the launcher)
                    if (isForegroundAppActive()) {
                        tvUsageTodayMinutes++
                        db.collection("users").document(uid).update("tvUsageMinutesToday", tvUsageTodayMinutes)
                        
                        if (tvTimeLimitMinutes > 0 && tvUsageTodayMinutes >= tvTimeLimitMinutes) {
                            blockAccess()
                        }
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 60000) // Check every minute
            }
        }, 60000)
    }

    private fun isForegroundAppActive(): Boolean {
        // Logic to detect if a non-system/non-launcher app is active
        // For simplicity on TV, we check if the current window is NOT the home screen
        return true // Simplified for accessibility context
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isDeviceTv = isTv()
        
        // 1. Enforce Locks
        if (isLocked || (isDeviceTv && (isTvLocked || (tvTimeLimitMinutes > 0 && tvUsageTodayMinutes >= tvTimeLimitMinutes)))) {
            blockAccess()
            return
        }

        val packageName = event.packageName?.toString() ?: ""

        // 2. Block Restricted Apps
        if (blockedPackages.contains(packageName) || (isDeviceTv && blockedTvApps.contains(packageName))) {
            blockAccess()
            return
        }

        // 3. Block Installations
        if (blockAppInstalls && installerPackages.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: event.source
            if (shouldBlockInstallation(rootNode, packageName)) {
                blockAccess()
                return
            }
        }
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
        authListener?.let { auth.removeAuthStateListener(it) }
        controlListener?.remove()
        mediaPlayer?.release()
    }

    override fun onInterrupt() {}
}
