package com.meshnet.app.routing

import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority

/**
 * Represents a nearby peer device that could carry our packet.
 * The app scores each peer before deciding who gets a copy.
 */
data class NearbyPeer(
    val endpointId: String,        // Nearby Connections endpoint ID
    val deviceName: String,        // Human readable name
    val batteryLevel: Int,         // 0-100
    val isMovingTowardSignal: Boolean, // true = heading toward city/signal
    val speedKmh: Float,           // estimated speed
    val existingRelayCount: Int,   // how many packets already carrying
    val isScheduledBus: Boolean,   // KSRTC/bus on known route = huge bonus
)

/**
 * Carrier Scoring Algorithm.
 *
 * Every nearby peer gets a score before receiving a packet copy.
 * Higher score = better carrier = gets priority.
 *
 * Based on Section 2.4 of the MeshNet master document.
 */
object CarrierScorer {

    /**
     * Score a single peer as a potential relay carrier.
     */
    fun score(peer: NearbyPeer): Int {
        var score = 0

        // ── Direction ─────────────────────────────────────
        if (peer.isMovingTowardSignal) {
            score += 50   // moving toward city/signal = best trait
        } else {
            score -= 50   // moving away = bad carrier
        }

        // ── Speed ─────────────────────────────────────────
        when {
            peer.speedKmh > 60 -> score += 20  // highway speed
            peer.speedKmh > 30 -> score += 10  // town speed
            peer.speedKmh > 0  -> score += 5   // slow but moving
            else               -> score -= 10  // stationary = bad
        }

        // ── Battery ───────────────────────────────────────
        when {
            peer.batteryLevel > 70 -> score += 15
            peer.batteryLevel > 40 -> score += 8
            peer.batteryLevel > 20 -> score += 2
            else                   -> score -= 40  // might die before delivering
        }

        // ── Existing load ─────────────────────────────────
        when {
            peer.existingRelayCount == 0  -> score += 10  // fresh carrier
            peer.existingRelayCount < 5   -> score += 5
            peer.existingRelayCount < 10  -> score += 0
            peer.existingRelayCount < 20  -> score -= 10
            else                          -> score -= 30  // overloaded
        }

        // ── Bus bonus ─────────────────────────────────────
        if (peer.isScheduledBus) {
            score += 80  // guaranteed to reach city — massive bonus
        }

        return score
    }

    /**
     * Filter and rank peers based on message priority.
     * Returns only peers who meet the minimum score threshold.
     */
    fun selectCarriers(
        peers: List<NearbyPeer>,
        packet: MeshPacket
    ): List<NearbyPeer> {

        val minScore = when (packet.priority) {
            MessagePriority.NORMAL -> 70   // strict — only good carriers
            MessagePriority.URGENT -> 40   // lenient — okay carriers
            MessagePriority.SOS    -> Int.MIN_VALUE  // no filter — everyone
        }

        val maxCarriers = when (packet.priority) {
            MessagePriority.NORMAL -> 3    // top 3
            MessagePriority.URGENT -> 10   // top 10
            MessagePriority.SOS    -> Int.MAX_VALUE  // everyone
        }

        return peers
            .map { peer -> Pair(peer, score(peer)) }
            .filter { (_, score) -> score >= minScore }
            .sortedByDescending { (_, score) -> score }
            .take(maxCarriers)
            .map { (peer, _) -> peer }
    }
}