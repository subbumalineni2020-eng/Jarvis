package com.jarvis.app

import ai.picovoice.porcupine.*
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

class JarvisService : Service() {
    private var wake: PorcupineManager? = null
    private var client: LiveClient? = null
    private val prefs by lazy { getSharedPreferences("j", MODE_PRIVATE) }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("j", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "j").setContentTitle("Jarvis").setContentText("Listening for “Jarvis”").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (i?.action == "talk") talk() else listen()
        return START_STICKY
    }

    @Synchronized private fun listen() {
        val pk = prefs.getString("pk", "") ?: ""
        if (wake != null || client != null || pk.isBlank()) return
        try {
            wake = PorcupineManager.Builder().setAccessKey(pk).setKeyword(Porcupine.BuiltInKeyword.JARVIS).build(this) { talk() }
            wake?.start()
        } catch (e: Exception) { Jarvis.state.value = "error: wake word - ${e.message}" }
    }

    @Synchronized private fun talk() {
        val gk = prefs.getString("gk", "") ?: ""
        if (client != null || gk.isBlank()) return
        wake?.let { runCatching { it.stop(); it.delete() } }; wake = null
        client = LiveClient(gk) { endSession() }.also { it.start() }
    }

    @Synchronized private fun endSession() { client = null; listen() }

    override fun onDestroy() { wake?.let { runCatching { it.stop(); it.delete() } }; client?.stop(); super.onDestroy() }
}
