package com.meshnet.app.models

import java.util.UUID

/**
 * Message priority classes.
 * Controls spray count, hop limit, expiry, and cost.
 */
enum class MessagePriority {
    NORMAL,   // 5 copies, 1 hop,       24h expiry, 1x cost
    URGENT,   // 15 copies, 3 hops,     6h expiry,  3x cost
    SOS       // 25 copies, unlimited,  72h expiry, FREE
}

/**
 * Delivery status of a packet.
 */
enum class PacketStatus {
    QUEUED,       // waiting for a relay carrier
    IN_TRANSIT,   // handed to at least one carrier
    DELIVERED,    // confirmed delivered to recipient
    EXPIRED,      // TTL ran out before delivery
    FAILED        // all paths exhausted
}

/**
 * The core data unit of MeshNet.
 * Every message — Normal, Urgent, or SOS — travels as a MeshPacket.
 * Relay nodes carry this packet without being able to read its content.
 */
data class MeshPacket(

    // ── Identity ──────────────────────────────────────────
    val packetId: String = UUID.randomUUID().toString(),
    // Unique ID for this packet. Used for kill signals and duplicate rejection.

    val originalSenderId: String = "",
    // Device ID of the person who originally sent this message.

    val destinationHash: String = "",
    // SHA-256 hash of recipient's device ID.
    // Relay nodes see only this hash — NOT the actual phone number.

    // ── Routing ───────────────────────────────────────────
    val priority: MessagePriority = MessagePriority.NORMAL,

    val sprayLimit: Int = 5,
    // How many copies were originally sprayed.
    // NORMAL=5, URGENT=15, SOS=25

    val sprayRemaining: Int = 5,
    // How many more times this copy can be forwarded.
    // Decrements on each relay. When 0 — carry only, no more forwarding.

    val hopCount: Int = 0,
    // How many hops this copy has already traveled.

    val maxHops: Int = 1,
    // Maximum allowed hops before copy stops forwarding.
    // NORMAL=1, URGENT=3, SOS=Int.MAX_VALUE (unlimited)

    // ── Timing ────────────────────────────────────────────
    val createdAt: Long = System.currentTimeMillis(),
    // Timestamp when Ravi originally sent the message.

    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
    // When this packet auto-deletes if not delivered.
    // NORMAL=24h, URGENT=6h, SOS=72h

    // ── Content ───────────────────────────────────────────
    val encryptedPayload: String = "",
    // AES-256-GCM encrypted message content.
    // Only recipient can decrypt. Relay nodes see only random bytes.

    val isSOSMessage: Boolean = false,
    // If true — this is an emergency. No credit deduction. Max spray.

    // ── Status ────────────────────────────────────────────
    val status: PacketStatus = PacketStatus.QUEUED,

    val deliveredAt: Long? = null,
    // Timestamp when delivery was confirmed. Null if not yet delivered.

    // ── GPS (for SOS) ────────────────────────────────────
    val senderLatitude: Double? = null,
    val senderLongitude: Double? = null,
    // Attached for SOS packets so recipient can see location immediately.

) {
    /**
     * Check if this packet has expired.
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }

    /**
     * Check if this copy can be forwarded further.
     */
    fun canForward(): Boolean {
        return sprayRemaining > 0 && hopCount < maxHops
    }

    /**
     * Create a copy of this packet for spraying to a new carrier.
     * Decrements sprayRemaining and increments hopCount.
     */
    fun createRelayCopy(): MeshPacket {
        return this.copy(
            sprayRemaining = sprayRemaining - 1,
            hopCount = hopCount + 1,
            status = PacketStatus.IN_TRANSIT
        )
    }

    /**
     * Mark this packet as delivered.
     */
    fun markDelivered(): MeshPacket {
        return this.copy(
            status = PacketStatus.DELIVERED,
            deliveredAt = System.currentTimeMillis()
        )
    }

    companion object {
        /**
         * Factory — create a NORMAL message packet.
         */
        fun createNormal(
            senderId: String,
            destinationHash: String,
            encryptedPayload: String
        ): MeshPacket = MeshPacket(
            originalSenderId = senderId,
            destinationHash = destinationHash,
            priority = MessagePriority.NORMAL,
            sprayLimit = 5,
            sprayRemaining = 5,
            maxHops = 1,
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000L),
            encryptedPayload = encryptedPayload,
            isSOSMessage = false
        )

        /**
         * Factory — create an URGENT message packet.
         */
        fun createUrgent(
            senderId: String,
            destinationHash: String,
            encryptedPayload: String
        ): MeshPacket = MeshPacket(
            originalSenderId = senderId,
            destinationHash = destinationHash,
            priority = MessagePriority.URGENT,
            sprayLimit = 15,
            sprayRemaining = 15,
            maxHops = 3,
            expiresAt = System.currentTimeMillis() + (6 * 60 * 60 * 1000L),
            encryptedPayload = encryptedPayload,
            isSOSMessage = false
        )

        /**
         * Factory — create an SOS packet.
         * Free. 25 copies. Unlimited hops. 72h expiry. GPS attached.
         */
        fun createSOS(
            senderId: String,
            destinationHash: String,
            encryptedPayload: String,
            latitude: Double,
            longitude: Double
        ): MeshPacket = MeshPacket(
            originalSenderId = senderId,
            destinationHash = destinationHash,
            priority = MessagePriority.SOS,
            sprayLimit = 25,
            sprayRemaining = 25,
            maxHops = Int.MAX_VALUE,
            expiresAt = System.currentTimeMillis() + (72 * 60 * 60 * 1000L),
            encryptedPayload = encryptedPayload,
            isSOSMessage = true,
            senderLatitude = latitude,
            senderLongitude = longitude
        )
    }
}