package com.meshnet.app.mesh

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meshnet.app.MainActivity
import com.meshnet.app.R
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.data.MeshNetDatabase
import com.meshnet.app.data.MessageQueueEntity
import com.meshnet.app.fallback.SmsFallbackManager
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.routing.SprayAndWaitRouter
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android Foreground Service to keep mesh networking alive in the background.
 */
class MeshService : Service() {

    private val TAG = "MeshService"
    private val CHANNEL_ID = "meshnet_service_channel"
    private val SOS_CHANNEL_ID = "meshnet_sos_channel"
    private val NOTIFICATION_ID = 1

    private lateinit var nearbyManager: NearbyConnectionsManager
    private lateinit var router: SprayAndWaitRouter
    private lateinit var smsFallback: SmsFallbackManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val _connectedPeers = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedPeers: StateFlow<Map<String, String>> = _connectedPeers

    private val _technicalEvents = MutableSharedFlow<String>(replay = 10)
    val technicalEvents: SharedFlow<String> = _technicalEvents

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService
    }

    private val binder = MeshBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        nearbyManager = NearbyConnectionsManager(this)
        
        nearbyManager.onConnectedCallback = { endpointId ->
            val name = nearbyManager.connectedPeers[endpointId] ?: "Unknown"
            _connectedPeers.value = nearbyManager.connectedPeers.toMap()
            logTechnical("CONNECTED: $name")
        }
        
        nearbyManager.onDisconnectedCallback = { endpointId ->
            _connectedPeers.value = nearbyManager.connectedPeers.toMap()
            logTechnical("DISCONNECTED: $endpointId")
        }

        smsFallback = SmsFallbackManager(this)
        val db = MeshNetDatabase.getDatabase(this)
        router = SprayAndWaitRouter(nearbyManager, db)
        
        createNotificationChannels()
    }

    private fun logTechnical(msg: String) {
        serviceScope.launch {
            _technicalEvents.emit(msg)
            Log.d(TAG, msg)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("MeshNet Active", "Relay mode on — earning credits")
        startForeground(NOTIFICATION_ID, notification)
        startMesh()
        return START_STICKY
    }

    private fun getMyId(): String {
        return CryptoManager.getMeshNetId(this) ?: "unknown"
    }

    private fun startMesh() {
        val myId = getMyId()
        val displayName = getSharedPreferences("meshnet_prefs", Context.MODE_PRIVATE)
            .getString("display_name", Build.MODEL) ?: Build.MODEL
        
        val discoveryName = "$displayName|$myId"
        logTechnical("Starting Discovery as: $displayName")

        nearbyManager.startAdvertising(discoveryName) { json ->
            serviceScope.launch {
                try {
                    val packet = gson.fromJson(json, MeshPacket::class.java)
                    val currentMyId = getMyId()

                    if (packet.encryptedPayload == "KILL_SIGNAL") {
                        logTechnical("KILL SIG received for ${packet.packetId.take(8)}")
                        router.processKillSignal(packet.packetId)
                        smsFallback.cancelFallback(packet.packetId)
                    } else {
                        // 1. Filter out self-sent packets
                        if (packet.originalSenderId == currentMyId) {
                            logTechnical("Ignoring self-packet: ${packet.packetId.take(8)}")
                            return@launch
                        }

                        val isForMe = CryptoManager.isMyPacket(packet.destinationHash, currentMyId)
                        logTechnical("PACKET received: ${packet.packetId.take(8)} (For Me: $isForMe)")
                        
                        // 2. Process via router and only notify if it's NEW
                        val isNew = router.handleIncomingPacket(packet, currentMyId)
                        
                        if (isNew) {
                            val isSOS = packet.priority == MessagePriority.SOS
                            if (isSOS) {
                                showSOSNotification(packet)
                            } else if (isForMe) {
                                showDeliveryNotification("New message received", "Tap to read")
                            } else {
                                showRelayNotification()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing incoming packet", e)
                }
            }
        }

        nearbyManager.startDiscovery { endpointId, name ->
            logTechnical("PEER FOUND: $name")
            nearbyManager.connectToPeer(endpointId)
        }
    }

    fun sprayPacket(packet: MeshPacket) {
        serviceScope.launch {
            val peers = nearbyManager.connectedPeers.map { (id, name) ->
                val parts = name.split("|")
                val peerName = parts.getOrNull(0) ?: name
                val peerHash = parts.getOrNull(1) ?: "unknown_hash"
                com.meshnet.app.routing.NearbyPeer(id, peerHash, peerName, 80, true, 40f, 0, false)
            }
            logTechnical("SPRAYING: ${packet.packetId.take(8)} to ${peers.size} peers")
            router.sprayToCarriers(packet, peers)
        }
    }

    private fun showRelayNotification() {
        val notification = createNotification("MeshNet Active", "Relayed 1 packet. +0.8MB credits")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showSOSNotification(packet: MeshPacket) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(this, SOS_CHANNEL_ID)
            .setContentTitle("🚨 EMERGENCY SOS RECEIVED")
            .setContentText("A nearby user needs help! Tap to view location.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(3, notification)
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
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "MeshNet Service", NotificationManager.IMPORTANCE_LOW
            ))
            val sosChannel = NotificationChannel(
                SOS_CHANNEL_ID, "Emergency SOS Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical life-safety alerts from nearby mesh users"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            manager.createNotificationChannel(sosChannel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        nearbyManager.stopAll()
        smsFallback.destroy()
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
