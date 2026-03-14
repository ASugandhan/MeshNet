package com.meshnet.app.fallback

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.meshnet.app.models.MeshPacket
import com.meshnet.app.models.MessagePriority
import kotlinx.coroutines.*
import java.util.Locale

/**
 * SMS Fallback Manager — India-specific last resort delivery.
 *
 * If a packet (especially SOS) finds no peer for 5 minutes,
 * compress it into ≤140 bytes and fire it via 2G SMS.
 *
 * Format: MNET|PRIORITY|PKTID_SHORT|DEST_SHORT|LAT|LNG|PAYLOAD_TRUNCATED
 * Example: MNET|SOS|A3F2|B7C1|10.324|76.995|HELP ANKLE INJURY
 */
class SmsFallbackManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingFallbacks = mutableMapOf<String, Job>()

    companion object {
        const val FALLBACK_DELAY_MS = 5 * 60 * 1000L  // 5 minutes
        const val SOS_FALLBACK_DELAY_MS = 60 * 1000L  // 1 minute for SOS
        const val MAX_SMS_BYTES = 140
        const val TAG = "SmsFallback"
        const val PREFIX = "MNET"
    }

    /**
     * Register a packet for SMS fallback.
     * If no peer delivers it within the timeout, SMS fires automatically.
     */
    fun registerForFallback(packet: MeshPacket, recipientPhone: String) {
        val delay = if (packet.priority == MessagePriority.SOS)
            SOS_FALLBACK_DELAY_MS else FALLBACK_DELAY_MS

        val job = scope.launch {
            delay(delay)
            Log.w(TAG, "No peer found in ${delay/1000}s — triggering SMS fallback for ${packet.packetId.take(8)}")
            sendViaSms(packet, recipientPhone)
        }
        pendingFallbacks[packet.packetId] = job
        Log.d(TAG, "Fallback registered for ${packet.packetId.take(8)} — fires in ${delay/1000}s")
    }

    /**
     * Cancel fallback if a peer delivered the packet normally.
     * Call this when you receive a kill signal.
     */
    fun cancelFallback(packetId: String) {
        pendingFallbacks[packetId]?.cancel()
        pendingFallbacks.remove(packetId)
        Log.d(TAG, "Fallback cancelled — packet delivered via mesh: ${packetId.take(8)}")
    }

    /**
     * Compress and send the packet via SMS.
     * Fits everything into ≤140 bytes using short format.
     */
    private fun sendViaSms(packet: MeshPacket, recipientPhone: String) {
        val compressed = compress(packet)
        if (compressed.length > MAX_SMS_BYTES) {
            Log.e(TAG, "Compressed SMS too long: ${compressed.length} bytes — truncating")
        }
        val smsText = compressed.take(MAX_SMS_BYTES)

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(
                recipientPhone,
                null,
                smsText,
                null,
                null
            )
            Log.i(TAG, "SMS sent to $recipientPhone: $smsText")
        } catch (e: Exception) {
            Log.e(TAG, "SMS send failed: ${e.message}")
        }
    }

    /**
     * Compress packet into short SMS-safe string.
     *
     * Format: MNET|P|ID8|DEST8|LAT|LNG|MSG
     * Where P = N(ormal)/U(rgent)/S(OS)
     */
    private fun compress(packet: MeshPacket): String {
        val priority = when (packet.priority) {
            MessagePriority.NORMAL -> "N"
            MessagePriority.URGENT -> "U"
            MessagePriority.SOS    -> "S"
        }
        val idShort   = packet.packetId.take(8)
        val destShort = packet.destinationHash.take(8)
        val lat = packet.senderLatitude?.let { String.format(Locale.US, "%.4f", it) } ?: "0"
        val lng = packet.senderLongitude?.let { String.format(Locale.US, "%.4f", it) } ?: "0"

        // Truncate payload to fit within 140 bytes total
        val header = "$PREFIX|$priority|$idShort|$destShort|$lat|$lng|"
        val remaining = MAX_SMS_BYTES - header.length
        val payload = packet.encryptedPayload
            .take(remaining.coerceAtLeast(0))

        return "$header$payload"
    }

    /**
     * Parse an incoming SMS to check if it's a MeshNet fallback packet.
     * Returns null if it's not a MeshNet SMS.
     */
    fun parseIncomingSms(smsBody: String): ParsedSms? {
        if (!smsBody.startsWith(PREFIX)) return null
        return try {
            val parts = smsBody.split("|")
            ParsedSms(
                priority  = parts[1],
                packetId  = parts[2],
                destHash  = parts[3],
                latitude  = parts[4].toDoubleOrNull(),
                longitude = parts[5].toDoubleOrNull(),
                payload   = parts.getOrElse(6) { "" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MeshNet SMS: ${e.message}")
            null
        }
    }

    fun destroy() {
        scope.cancel()
        pendingFallbacks.clear()
    }
}

data class ParsedSms(
    val priority: String,
    val packetId: String,
    val destHash: String,
    val latitude: Double?,
    val longitude: Double?,
    val payload: String
)
