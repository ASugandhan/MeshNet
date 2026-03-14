package com.meshnet.app.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.meshnet.app.models.MeshPacket
import java.nio.charset.StandardCharsets

/**
 * Handles Google Nearby Connections API for peer-to-peer mesh networking.
 */
class NearbyConnectionsManager(private val context: Context) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val gson = Gson()
    
    val connectedPeers = mutableMapOf<String, String>() // endpointId -> Name|Hash
    val pendingConnections = mutableSetOf<String>()
    
    private var localDiscoveryName: String = "MeshNetUser"
    
    var onPacketReceivedCallback: ((String) -> Unit)? = null
    var onPeerFoundCallback: ((String, String) -> Unit)? = null
    var onConnectedCallback: ((String) -> Unit)? = null
    var onDisconnectedCallback: ((String) -> Unit)? = null

    companion object {
        private const val SERVICE_ID = "com.meshnet.app.mesh"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                val json = String(bytes, StandardCharsets.UTF_8)
                onPacketReceivedCallback?.invoke(json)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            connectedPeers[endpointId] = info.endpointName
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pendingConnections.remove(endpointId)
            if (result.status.isSuccess) {
                onConnectedCallback?.invoke(endpointId)
            } else {
                connectedPeers.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedPeers.remove(endpointId)
            pendingConnections.remove(endpointId)
            onDisconnectedCallback?.invoke(endpointId)
        }
    }

    fun startAdvertising(discoveryName: String, onPacketReceived: (String) -> Unit) {
        this.localDiscoveryName = discoveryName
        this.onPacketReceivedCallback = onPacketReceived
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            discoveryName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        )
    }

    fun startDiscovery(onPeerFound: (endpointId: String, name: String) -> Unit) {
        this.onPeerFoundCallback = onPeerFound
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    onPeerFoundCallback?.invoke(endpointId, info.endpointName)
                }
                override fun onEndpointLost(endpointId: String) {}
            },
            options
        )
    }

    fun connectToPeer(endpointId: String) {
        if (connectedPeers.containsKey(endpointId) || pendingConnections.contains(endpointId)) return
        
        pendingConnections.add(endpointId)
        connectionsClient.requestConnection(
            localDiscoveryName, // Send our Name|Hash to the peer
            endpointId,
            connectionLifecycleCallback
        )
    }

    fun sendPacket(endpointId: String, packet: MeshPacket): Boolean {
        return try {
            val json = gson.toJson(packet)
            val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
            connectionsClient.sendPayload(endpointId, payload)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedPeers.clear()
        pendingConnections.clear()
    }
}
