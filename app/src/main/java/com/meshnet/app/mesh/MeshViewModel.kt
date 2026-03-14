package com.meshnet.app.mesh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.data.MeshNetDatabase
import com.meshnet.app.data.MessageQueueEntity
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.routing.CarrierScorer
import com.meshnet.app.routing.NearbyPeer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Central ViewModel — connects Nearby Connections discovery
 * to the Spray-and-Wait routing logic.
 */
class MeshViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MeshNetDatabase.getDatabase(application)
    private val dao = db.messageQueueDao()

    private var meshService: MeshService? = null

    // ── State exposed to UI ──────────────────────────────────
    private val _connectedPeers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val connectedPeers: StateFlow<List<NearbyPeer>> = _connectedPeers

    // Observe pending packets
    val pendingPackets: StateFlow<List<MeshPacket>> = dao.getAllPendingPackets()
        .map { list -> list.map { it.toMeshPacket() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Observe all messages (including those sent by this device)
    // We filter in the UI level if we want to show only incoming messages.
    val receivedMessages: StateFlow<List<MeshPacket>> = dao.getInboxMessages()
        .map { list -> list.map { it.toMeshPacket() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _relayLog = MutableStateFlow<List<String>>(emptyList())
    val relayLog: StateFlow<List<String>> = _relayLog

    private fun getMyId(): String {
        return CryptoManager.getMeshNetId(getApplication()) ?: "unknown"
    }

    fun setMeshService(service: MeshService?) {
        this.meshService = service
        if (service != null) {
            viewModelScope.launch {
                service.technicalEvents.collect { event ->
                    log(event)
                }
            }
            viewModelScope.launch {
                service.connectedPeers.collect { peerMap ->
                    val peers = peerMap.map { (id, name) ->
                        val parts = name.split("|")
                        val peerName = parts.getOrNull(0) ?: name
                        val peerHash = parts.getOrNull(1) ?: "unknown_hash"
                        NearbyPeer(id, peerHash, peerName, 80, true, 40f, 0, false)
                    }
                    _connectedPeers.value = peers
                    if (peers.isNotEmpty()) {
                        attemptRelayAll()
                    }
                }
            }
        }
    }

    /**
     * Send a message to a specific peer or broadcast to the mesh chat.
     */
    fun sendMessage(
        destinationDeviceId: String,
        messageText: String,
        priority: MessagePriority = MessagePriority.NORMAL,
        lat: Double? = null,
        lng: Double? = null
    ) {
        viewModelScope.launch {
            val myMeshNetId = getMyId()
            val destHash = when {
                priority == MessagePriority.SOS -> "BROADCAST"
                destinationDeviceId == "MESH_CHAT" -> "MESH_CHAT"
                else -> destinationDeviceId.trim()
            }
            
            val encrypted = when (destHash) {
                "BROADCAST" -> CryptoManager.encryptMessage(messageText, "BROADCAST")
                "MESH_CHAT" -> CryptoManager.encryptMessage(messageText, "MESH_CHAT")
                else -> CryptoManager.encryptMessage(messageText, destHash)
            }
            
            val packet = when (priority) {
                MessagePriority.NORMAL -> MeshPacket.createNormal(myMeshNetId, destHash, encrypted)
                MessagePriority.URGENT -> MeshPacket.createUrgent(myMeshNetId, destHash, encrypted)
                MessagePriority.SOS    -> MeshPacket.createSOS(
                    myMeshNetId, destHash, encrypted,
                    lat ?: 0.0, lng ?: 0.0
                )
            }
            
            dao.insertPacket(MessageQueueEntity.fromMeshPacket(packet, isRelayPacket = false))
            meshService?.sprayPacket(packet)
        }
    }

    private fun attemptRelayAll() {
        val service = meshService ?: return
        viewModelScope.launch {
            val packets = pendingPackets.value
            packets.forEach { packet ->
                if (packet.canForward() || packet.priority == MessagePriority.SOS || packet.destinationHash == "MESH_CHAT") {
                    service.sprayPacket(packet)
                }
            }
        }
    }

    private fun log(msg: String) {
        val current = _relayLog.value.toMutableList()
        current.add(0, "[${System.currentTimeMillis() % 100000}] $msg")
        if (current.size > 50) current.removeAt(current.size - 1)
        _relayLog.value = current
    }
}
