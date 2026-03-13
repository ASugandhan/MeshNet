package com.meshnet.app.routing

import android.util.Log
import com.meshnet.app.crypto.CryptoManager
import com.meshnet.app.data.MessageQueueEntity
import com.meshnet.app.data.MeshNetDatabase
import com.meshnet.app.mesh.NearbyConnectionsManager
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.PacketStatus

/**
 * Core routing logic. Implementation of Section 6.
 */
class SprayAndWaitRouter(
    private val nearbyManager: NearbyConnectionsManager,
    private val db: MeshNetDatabase
) {
    private val TAG = "SprayAndWaitRouter"

    suspend fun handleIncomingPacket(packet: MeshPacket, myDeviceId: String) {
        val dao = db.messageQueueDao()

        // 1. Check for duplicates
        if (dao.getPacketById(packet.packetId) != null) {
            Log.d(TAG, "Duplicate packet ${packet.packetId} ignored.")
            return
        }

        // 2. Check for expiry
        if (packet.isExpired()) {
            Log.d(TAG, "Expired packet ${packet.packetId} ignored.")
            return
        }

        // 3. Check if it's for me
        if (CryptoManager.isMyPacket(packet.destinationHash, myDeviceId)) {
            Log.d(TAG, "Packet ${packet.packetId} is for ME! Delivering locally.")
            val deliveredPacket = packet.markDelivered()
            dao.insertPacket(MessageQueueEntity.fromMeshPacket(deliveredPacket, isRelayPacket = false))
            
            // Trigger kill signal broadcast
            broadcastKillSignal(packet.packetId, nearbyManager.connectedPeers.keys.toList())
            return
        }

        // 4. It's a relay packet. Check if we can forward it.
        if (packet.canForward()) {
            Log.d(TAG, "Storing and preparing to relay packet ${packet.packetId}")
            dao.insertPacket(MessageQueueEntity.fromMeshPacket(packet, isRelayPacket = true))
            
            // Try to spray to current connected peers
            val availablePeers = nearbyManager.connectedPeers.map { (id, name) ->
                // In a real app, we'd get real telemetry here. For prototype, use defaults.
                NearbyPeer(id, name, 80, true, 40f, 0, false)
            }
            sprayToCarriers(packet, availablePeers)
        } else {
            Log.d(TAG, "Packet ${packet.packetId} cannot be forwarded (limit reached). Carrying only.")
            dao.insertPacket(MessageQueueEntity.fromMeshPacket(packet, isRelayPacket = true))
        }
    }

    suspend fun sprayToCarriers(packet: MeshPacket, availablePeers: List<NearbyPeer>) {
        if (availablePeers.isEmpty()) return
        
        val selected = CarrierScorer.selectCarriers(availablePeers, packet)
        var count = 0
        
        for (peer in selected) {
            if (packet.canForward()) {
                val relayCopy = packet.createRelayCopy()
                if (nearbyManager.sendPacket(peer.endpointId, relayCopy)) {
                    count++
                }
            }
        }
        Log.d(TAG, "Sprayed $count copies of packet ${packet.packetId}")
    }

    suspend fun broadcastKillSignal(packetId: String, connectedPeers: List<String>) {
        // In this prototype, we send a special MeshPacket with status DELIVERED as a "kill signal"
        // Real implementation might use a smaller specialized JSON.
        val killPacket = MeshPacket(
            packetId = packetId,
            status = PacketStatus.DELIVERED,
            encryptedPayload = "KILL_SIGNAL"
        )
        
        for (peerId in connectedPeers) {
            nearbyManager.sendPacket(peerId, killPacket)
        }
        Log.d(TAG, "Broadcast kill signal for $packetId to ${connectedPeers.size} peers")
    }

    suspend fun processKillSignal(packetId: String) {
        db.messageQueueDao().deletePacket(packetId)
        Log.d(TAG, "Packet $packetId killed — delivery confirmed elsewhere")
    }

    suspend fun cleanExpiredPackets() {
        db.messageQueueDao().deleteExpiredPackets(System.currentTimeMillis())
        Log.d(TAG, "Cleaned up expired packets from DB")
    }
}
