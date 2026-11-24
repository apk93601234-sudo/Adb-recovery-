package com.example.core.adb

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

data class HostDiagnostics(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abis: String,
    val usbHostSupported: Boolean,
    val batteryPercent: Int,
    val freeStorageGb: Double,
    val totalStorageGb: Double,
    val localIps: List<String>,
)

object LocalDiagnostics {
    fun readHostInfo(context: Context): HostDiagnostics {
        val usbHost = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBytes
        val totalBytes = stat.totalBytes

        return HostDiagnostics(
            model = Build.MODEL ?: "Android",
            manufacturer = Build.MANUFACTURER ?: "",
            androidVersion = Build.VERSION.RELEASE ?: "",
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.joinToString(", "),
            usbHostSupported = usbHost,
            batteryPercent = pct,
            freeStorageGb = freeBytes / (1024.0 * 1024.0 * 1024.0),
            totalStorageGb = totalBytes / (1024.0 * 1024.0 * 1024.0),
            localIps = LocalLanScanner.getLocalIpv4Addresses(),
        )
    }
}
