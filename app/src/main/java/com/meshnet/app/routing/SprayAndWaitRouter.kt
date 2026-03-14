package com.meshnet.app.routing

import android.util.Log
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.data.MessageQueueEntity
import com.meshnet.app.data.MeshNetDatabase
import com.meshnet.app.mesh.NearbyConnectionsManager
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority
import com.meshnet.app.models.PacketStatus

/**
 * Core routing logic. Implementation of Section 6.
 */
class SprayAndWaitRouter(
    private val nearbyManager: NearbyConnectionsManager,
    private val db: MeshNetDatabase
) {
    private val TAG = "SprayAndWaitRouter"

    /**
     * Processes an incoming packet.
     * Returns true if the packet is new and was processed, false if it's a duplicate or expired.
     */
    suspend fun handleIncomingPacket(packet: MeshPacket, myDeviceId: String): Boolean {
        val dao = db.messageQueueDao()
        
        // 1. Absolute Duplicate Check
        if (dao.getPacketById(packet.packetId) != null) {
            return false
        }

        // 2. Expiry Check
        if (packet.isExpired()) return false

        // 3. Ignore packets sent by self (if they somehow loop back)
        if (packet.originalSenderId == myDeviceId) return false

        val isUniversal = packet.priority == MessagePriority.SOS || 
                         packet.destinationHash == "BROADCAST" ||
                         packet.destinationHash == "MESH_CHAT"

        val isForMe = CryptoManager.isMyPacket(packet.destinationHash, myDeviceId)

        // 4. Handle Local Delivery
        if (isForMe || isUniversal) {
            val deliveredPacket = packet.markDelivered()
            dao.insertPacket(MessageQueueEntity.fromMeshPacket(deliveredPacket, isRelayPacket = false))
            
            if (!isUniversal && isForMe) {
                broadcastKillSignal(packet.packetId, nearbyManager.connectedPeers.keys.toList())
                return true
            }
        }

        // 5. Handle Relaying (Store and Forward)
        if (packet.canForward() || isUniversal) {
            if (!isUniversal) {
                dao.insertPacket(MessageQueueEntity.fromMeshPacket(packet, isRelayPacket = true))
            }
            
            val availablePeers = nearbyManager.connectedPeers.map { (id, name) ->
                val parts = name.split("|")
                val peerName = parts.getOrNull(0) ?: name
                val peerHash = parts.getOrNull(1) ?: "unknown_hash"
                NearbyPeer(id, peerHash, peerName, 80, true, 40f, 0, false)
            }
            sprayToCarriers(packet, availablePeers)
        }
        
        return true
    }

    suspend fun sprayToCarriers(packet: MeshPacket, availablePeers: List<NearbyPeer>) {
        if (availablePeers.isEmpty()) return
        
        val selected = if (packet.priority == MessagePriority.SOS || packet.destinationHash == "MESH_CHAT") {
            availablePeers // Broadcast to everyone
        } else {
            CarrierScorer.selectCarriers(availablePeers, packet)
        }
        
        for (peer in selected) {
            val relayCopy = packet.createRelayCopy()
            nearbyManager.sendPacket(peer.endpointId, relayCopy)
        }
    }

    suspend fun broadcastKillSignal(packetId: String, connectedPeers: List<String>) {
        val killPacket = MeshPacket(
            packetId = packetId,
            status = PacketStatus.DELIVERED,
            encryptedPayload = "KILL_SIGNAL"
        )
        for (peerId in connectedPeers) {
            nearbyManager.sendPacket(peerId, killPacket)
        }
    }

    suspend fun processKillSignal(packetId: String) {
        val dao = db.messageQueueDao()
        val existing = dao.getPacketById(packetId)
        if (existing != null && (existing.isRelayPacket || existing.status != "DELIVERED")) {
            dao.deletePacket(packetId)
        }
    }

    suspend fun cleanExpiredPackets() {
        db.messageQueueDao().deleteExpiredPackets(System.currentTimeMillis())
    }
}
