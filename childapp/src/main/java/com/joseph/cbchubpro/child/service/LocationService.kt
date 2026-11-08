package com.joseph.cbchubpro.child.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import com.google.firebase.database.FirebaseDatabase
import com.joseph.cbchubpro.child.MainActivity

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationService", "New location: ${location.latitude}, ${location.longitude}")
                    updateLocation(location.latitude, location.longitude)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service onStartCommand")
        
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1001, notification)
        }
        
        startLocationUpdates()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .setMinUpdateIntervalMillis(15000)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                Log.e("LocationService", "Failed to request location updates: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("LocationService", "Exception requesting location updates", e)
        }
    }

    private fun updateLocation(lat: Double, lng: Double) {
        val uid = auth.currentUser?.uid ?: run {
            Log.w("LocationService", "No authenticated user found for location update")
            return
        }

        val timestamp = com.google.firebase.Timestamp.now()
        
        // 1. Update Firestore (Last Known Location)
        val firestoreData = mapOf(
            "location" to GeoPoint(lat, lng),
            "lastLocationUpdate" to timestamp,
            "isOnline" to true
        )
        db.collection("users").document(uid)
            .set(firestoreData, SetOptions.merge())
            .addOnFailureListener { e -> Log.e("LocationService", "Firestore sync failed: ${e.message}") }

        // 2. Update Realtime Database (Live Tracking)
        val rtdbData = mapOf(
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis()
        )
        // Ensure path matches what parent app expects
        rtdb.child("locations").child(uid).setValue(rtdbData)
            .addOnFailureListener { e -> Log.e("LocationService", "RTDB sync failed: ${e.message}") }
    }

    private fun createNotification(): Notification {
        val channelId = "location_service_channel"
        val channelName = "Safety Tracking Service"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CBC Hub Safety Active")
            .setContentText("Keeping you safe and connected.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("LocationService", "Service onDestroy")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}
