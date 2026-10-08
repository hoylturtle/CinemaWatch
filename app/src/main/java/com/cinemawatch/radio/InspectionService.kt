package com.cinemawatch.radio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cinemawatch.CinemaApp
import com.cinemawatch.MainActivity
import com.cinemawatch.R
import kotlinx.coroutines.*

object ScanPermissions {
    fun required(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray()
    fun granted(context: android.content.Context) = required().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

class InspectionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scanner = (application as CinemaApp).scanner
        if (intent?.action == "STOP") { scanner.stop(); return START_NOT_STICKY }
        if (intent == null || !ScanPermissions.granted(this)) { stopSelf(); return START_NOT_STICKY }
        val seconds = intent.getIntExtra("seconds", 120)
        if (seconds !in setOf(120, 180)) { stopSelf(); return START_NOT_STICKY }
        if (wakeLock?.isHeld != true) wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:inspection").apply { acquire((seconds + 30) * 1000L) }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("inspection", com.cinemawatch.AppLanguage.context(this).getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, InspectionService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "inspection").setSmallIcon(R.drawable.ic_cinema)
            .setContentTitle(com.cinemawatch.AppLanguage.context(this).getString(R.string.app_name)).setContentText(com.cinemawatch.AppLanguage.context(this).getString(R.string.scanning_notification))
            .setContentIntent(open).setOngoing(true).addAction(0, com.cinemawatch.AppLanguage.context(this).getString(R.string.stop_notification), stop).build()
        ServiceCompat.startForeground(this, 101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        val zone = intent.getStringExtra("zone") ?: run { stopSelf(); return START_NOT_STICKY }
        scope.launch {
            try {
                scanner.start(SampleRequest(zone, intent.getStringExtra("mode") ?: "INSPECTION", intent.getIntExtra("seconds", 120),
                    optionalInt(intent, "planned"), optionalInt(intent, "actual"), optionalInt(intent, "gate"), intent.getStringExtra("point").orEmpty()))
            } catch (_: Exception) { stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun optionalInt(intent: Intent, key: String) = intent.getIntExtra(key, -1).takeIf { it >= 0 }
    override fun onDestroy() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null; (application as CinemaApp).scanner.stop(); scope.cancel(); super.onDestroy() }
}
