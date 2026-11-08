package com.joseph.cbchubpro.child

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.joseph.cbchubpro.child.receiver.AppDeviceAdminReceiver
import com.joseph.cbchubpro.child.service.AppBlockerService
import kotlinx.coroutines.launch

enum class PermissionType {
    DEVICE_ADMIN, USAGE_STATS, ACCESSIBILITY, LOCATION, BATTERY, OVERLAY
}

data class PermissionInfo(
    val type: PermissionType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val checkGranted: (Context) -> Boolean
)

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PermissionsHubScreen(onAllPermissionsGranted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    
    val generalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> 
        refreshTrigger++
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> 
        refreshTrigger++
    }

    val permissions = remember {
        listOf(
            PermissionInfo(
                PermissionType.DEVICE_ADMIN,
                "Anti-Uninstall Protection",
                "Prevents the app from being uninstalled and allows your parent to remotely lock the device for safety.",
                Icons.Rounded.Lock,
                { ctx ->
                    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val adminComponent = ComponentName(ctx, AppDeviceAdminReceiver::class.java)
                    dpm.isAdminActive(adminComponent)
                }
            ),
            PermissionInfo(
                PermissionType.USAGE_STATS,
                "App Usage Tracking",
                "Allows the parent to monitor which apps are used and for how long to help manage screen time habits.",
                Icons.Rounded.BarChart,
                { ctx ->
                    val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
                    }
                    mode == AppOpsManager.MODE_ALLOWED
                }
            ),
            PermissionInfo(
                PermissionType.ACCESSIBILITY,
                "Smart App Blocking",
                "Required to automatically block restricted apps and web content when screen time limits are reached.",
                Icons.Rounded.Block,
                { ctx ->
                    val expectedComponentName = ComponentName(ctx, AppBlockerService::class.java).flattenToString()
                    val enabledServices = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    enabledServices?.contains(expectedComponentName) == true
                }
            ),
            PermissionInfo(
                PermissionType.LOCATION,
                "Live Safety Tracking",
                "Provides your parent with your device's current location so they know you are safe when you are out.",
                Icons.Rounded.LocationOn,
                { ctx ->
                    val fineLocation = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                    } else true
                    
                    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val isLocationEnabled = LocationManagerCompat.isLocationEnabled(lm)
                    
                    fineLocation && backgroundLocation && isLocationEnabled
                }
            ),
            PermissionInfo(
                PermissionType.OVERLAY,
                "Screen Blocking Overlay",
                "Allows the app to show a blocking screen over other apps when your daily usage limit has been reached.",
                Icons.Rounded.Layers,
                { ctx -> Settings.canDrawOverlays(ctx) }
            ),
            PermissionInfo(
                PermissionType.BATTERY,
                "Always-Active Protection",
                "Ensures that CBC Hub's protection services remain active and are not closed by the system to save battery.",
                Icons.Rounded.BatteryFull,
                { ctx ->
                    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                    pm.isIgnoringBatteryOptimizations(ctx.packageName)
                }
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { permissions.size })
    
    val currentPermission = permissions[pagerState.currentPage]
    val isCurrentGranted = remember(pagerState.currentPage, refreshTrigger) { 
        currentPermission.checkGranted(context) 
    }

    DisposableEffect(context) {
        val activity = context.findActivity()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        (activity as? ComponentActivity)?.lifecycle?.addObserver(observer)
        onDispose { (activity as? ComponentActivity)?.lifecycle?.removeObserver(observer) }
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.height(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(permissions.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.LightGray
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(10.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (pagerState.currentPage < permissions.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onAllPermissionsGranted()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Later")
                    }
                    
                    Button(
                        onClick = {
                            if (isCurrentGranted) {
                                if (pagerState.currentPage < permissions.size - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                } else {
                                    onAllPermissionsGranted()
                                }
                            } else {
                                handlePermissionClick(context, currentPermission.type, locationPermissionLauncher, generalLauncher)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = if (isCurrentGranted) ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (isCurrentGranted) "Next" else "Allow")
                    }
                }
                
                if (permissions.all { it.checkGranted(context) }) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onAllPermissionsGranted) {
                        Text("Setup Complete! Continue", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Child Security Setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(24.dp)
            )
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                PermissionCard(permissions[page])
            }
        }
    }
}

@Composable
fun PermissionCard(permission: PermissionInfo) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(140.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ) {
            Icon(
                imageVector = permission.icon,
                contentDescription = null,
                modifier = Modifier.padding(35.dp).fillMaxSize(),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = permission.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = permission.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

private fun handlePermissionClick(
    context: Context, 
    type: PermissionType, 
    locationLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    generalLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    when (type) {
        PermissionType.LOCATION -> {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!LocationManagerCompat.isLocationEnabled(lm)) {
                generalLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                return
            }

            val fineLocationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fineLocationGranted) {
                locationLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val backgroundLocationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!backgroundLocationGranted) {
                    // For Android 10+ (Q), we request background location. 
                    // On Android 11+ (R), this will take the user to the system settings page for Location permissions.
                    locationLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    Toast.makeText(context, "Please select 'Allow all the time' in Location permissions", Toast.LENGTH_LONG).show()
                }
            }
        }
        PermissionType.DEVICE_ADMIN -> {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                val componentName = ComponentName(context, AppDeviceAdminReceiver::class.java)
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enabling this prevents the app from being uninstalled and allows remote locking for safety.")
            }
            try {
                generalLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("PermissionsHub", "Direct Admin activation failed, falling back to list", e)
                // Fallback to the direct Device Admin Settings list
                val fallback = Intent("android.settings.DEVICE_ADMIN_SETTINGS")
                try {
                    generalLauncher.launch(fallback)
                } catch (e2: Exception) {
                    val fallback2 = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    generalLauncher.launch(fallback2)
                }
                Toast.makeText(context, "Please find CBC Hub Pro Child in the list and enable it", Toast.LENGTH_LONG).show()
            }
        }
        else -> {
            val intent = when (type) {
                PermissionType.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                PermissionType.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                PermissionType.BATTERY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                } else null
                PermissionType.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                else -> null
            }
            intent?.let { 
                try {
                    generalLauncher.launch(it)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open settings. Please do it manually.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
