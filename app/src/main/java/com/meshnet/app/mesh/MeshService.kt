package com.meshnet.app.mesh

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meshnet.app.MainActivity
import com.meshnet.app.R
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.data.MeshNetDatabase
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.routing.SprayAndWaitRouter
import com.google.gson.Gson
import kotlinx.coroutines.*

/**
 * Android Foreground Service to keep mesh networking alive in the background.
 * Implementation of Section 7.
 */
class MeshService : Service() {

    private val TAG = "MeshService"
    private val CHANNEL_ID = "meshnet_service_channel"
    private val NOTIFICATION_ID = 1

    private lateinit var nearbyManager: NearbyConnectionsManager
    private lateinit var router: SprayAndWaitRouter
    private lateinit var deviceId: String
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        deviceId = CryptoManager.generateOrLoadDeviceId(this)
        nearbyManager = NearbyConnectionsManager(this)
        val db = MeshNetDatabase.getDatabase(this)
        router = SprayAndWaitRouter(nearbyManager, db)
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("MeshNet Active", "Relay mode on — earning credits")
        startForeground(NOTIFICATION_ID, notification)

        startMesh()

        return START_STICKY
    }

    private fun startMesh() {
        nearbyManager.startAdvertising(Build.MODEL) { json ->
            serviceScope.launch {
                try {
                    val packet = gson.fromJson(json, MeshPacket::class.java)
                    if (packet.encryptedPayload == "KILL_SIGNAL") {
                        router.processKillSignal(packet.packetId)
                    } else {
                        router.handleIncomingPacket(packet, deviceId)
                        
                        if (CryptoManager.isMyPacket(packet.destinationHash, deviceId)) {
                            showDeliveryNotification("New message received", "Tap to read")
                        } else {
                            showRelayNotification()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing incoming packet", e)
                }
            }
        }

        nearbyManager.startDiscovery { endpointId, name ->
            Log.d(TAG, "Peer found: $name ($endpointId). Connecting...")
            nearbyManager.connectToPeer(endpointId, 
                onConnected = { Log.d(TAG, "Connected to $name") },
                onDisconnected = { Log.d(TAG, "Disconnected from $name") }
            )
        }
    }

    private fun showRelayNotification() {
        // Simple subtle update to foreground notification or separate brief notification
        val notification = createNotification("MeshNet Active", "Relayed 1 packet. +0.8MB credits")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showDeliveryNotification(title: String, text: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Use default icon
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MeshNet Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        nearbyManager.stopAll()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshService::class.java)
            context.stopService(intent)
        }
    }
}
