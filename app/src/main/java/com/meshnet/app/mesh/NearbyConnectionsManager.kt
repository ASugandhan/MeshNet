package com.meshnet.app.mesh

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.meshnet.app.models.MeshPacket
import java.nio.charset.StandardCharsets

/**
 * Handles Google Nearby Connections API for peer-to-peer mesh networking.
 * Implementation of Section 5 in the Implementation Guide.
 */
class NearbyConnectionsManager(private val context: Context) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val gson = Gson()
    
    val connectedPeers = mutableMapOf<String, String>() // endpointId -> deviceName
    
    private var onPacketReceivedCallback: ((String) -> Unit)? = null
    private var onPeerFoundCallback: ((String, String) -> Unit)? = null
    private var onConnectedCallback: ((String) -> Unit)? = null
    private var onDisconnectedCallback: ((String) -> Unit)? = null

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

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Can be used to track progress of large packets
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Automatically accept the connection in mesh mode
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            connectedPeers[endpointId] = info.endpointName
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                onConnectedCallback?.invoke(endpointId)
            } else {
                connectedPeers.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedPeers.remove(endpointId)
            onDisconnectedCallback?.invoke(endpointId)
        }
    }

    fun startAdvertising(deviceName: String, onPacketReceived: (String) -> Unit) {
        this.onPacketReceivedCallback = onPacketReceived
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            deviceName,
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

                override fun onEndpointLost(endpointId: String) {
                    // Handle lost peer if needed
                }
            },
            options
        )
    }

    fun connectToPeer(
        endpointId: String,
        onConnected: (String) -> Unit,
        onDisconnected: (String) -> Unit
    ) {
        this.onConnectedCallback = onConnected
        this.onDisconnectedCallback = onDisconnected
        connectionsClient.requestConnection(
            "MeshNetUser", // Local name for request
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

    fun disconnectFromPeer(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
        connectedPeers.remove(endpointId)
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedPeers.clear()
    }
}
