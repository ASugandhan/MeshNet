package com.meshnet.app.crypto

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles encryption, hashing, and identity for MeshNet.
 * Implementation of Section 4 in the Implementation Guide.
 */
object CryptoManager {

    private const val PREFS_NAME = "meshnet_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    /**
     * Get or create a persistent unique ID for this device.
     */
    fun generateOrLoadDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * SHA-256 hash of device ID. Used to identify recipients without exposing IDs.
     */
    fun hashDestination(deviceId: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceId.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Encrypts a message using AES-256-GCM.
     * Prototype implementation uses a key derived from recipient hash.
     */
    fun encryptMessage(plaintext: String, recipientHash: String): String {
        return try {
            val key = deriveKeyFromHash(recipientHash)
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val iv = ByteArray(IV_LENGTH).apply { 
                // In a real app, use SecureRandom. For prototype, we use fixed IV or derived.
                // Using a derived IV for simplicity in prototype matching.
                System.arraycopy(key, 0, this, 0, IV_LENGTH) 
            }
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            Base64.encodeToString(ciphertext, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Decrypts an AES-256-GCM encrypted message.
     */
    fun decryptMessage(encryptedBase64: String, myDeviceId: String): String {
        return try {
            val myHash = hashDestination(myDeviceId)
            val key = deriveKeyFromHash(myHash)
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val iv = ByteArray(IV_LENGTH).apply { 
                System.arraycopy(key, 0, this, 0, IV_LENGTH)
            }
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            val encryptedData = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val plaintext = cipher.doFinal(encryptedData)
            
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Check if this packet is meant for us.
     */
    fun isMyPacket(destinationHash: String, myDeviceId: String): Boolean {
        return hashDestination(myDeviceId) == destinationHash
    }

    /**
     * Derives a 256-bit key from a string hash for prototype encryption.
     */
    private fun deriveKeyFromHash(hash: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(hash.toByteArray(Charsets.UTF_8))
    }
}
