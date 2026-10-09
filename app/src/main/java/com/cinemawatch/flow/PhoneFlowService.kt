package com.cinemawatch.flow

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.cinemawatch.*
import com.cinemawatch.radio.ScanPermissions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** User-started, bounded foreground BLE experiment. Never restart an old session. */
class PhoneFlowService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var lock:PowerManager.WakeLock?=null
    private var starting=false
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val app=application as CinemaApp
        if(intent?.action=="STOP") { app.phoneFlow.stop();stopSelf();return START_NOT_STICKY }
        if(starting || app.phoneFlow.state.value.active)return START_NOT_STICKY
        val scan=app.scanner.state.value
        if(intent==null || !ScanPermissions.granted(this) || scan.running || scan.saving || scan.saveFailed) {
            app.phoneFlow.failStart();stopSelf();return START_NOT_STICKY
        }
        starting=true
        try {
            val language=AppLanguage.context(this)
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("phone-flow",language.getString(R.string.flow_title),NotificationManager.IMPORTANCE_LOW))
            val open=PendingIntent.getActivity(this,20,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            val stop=PendingIntent.getService(this,21,Intent(this,PhoneFlowService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(this,"phone-flow").setSmallIcon(R.drawable.ic_cinema)
                .setContentTitle(language.getString(R.string.flow_title)).setContentText(language.getString(R.string.flow_notification))
                .setContentIntent(open).setOngoing(true).addAction(0,language.getString(R.string.stop_notification),stop).build()
            ServiceCompat.startForeground(this,201,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            lock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"$packageName:phone-flow").apply { acquire(930000) }
            scope.launch { app.phoneFlow.begin();app.phoneFlow.state.collect { if(!it.active)stopSelf() } }
        } catch (_:Exception) { app.phoneFlow.stop();app.phoneFlow.failStart();stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        (application as CinemaApp).phoneFlow.stop()
        lock?.let { if(it.isHeld)it.release() };lock=null;scope.cancel();super.onDestroy()
    }
}
