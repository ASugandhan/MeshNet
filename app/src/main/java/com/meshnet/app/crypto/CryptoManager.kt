package com.meshnet.app.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles encryption, hashing, and identity for MeshNet.
 */
object CryptoManager {

    private const val PREFS_NAME = "meshnet_prefs"
    private const val KEY_MESHNET_ID = "meshnet_id"
    private const val KEY_ALIAS = "meshnet_key_pair"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    fun generateIdentity(context: Context): String {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        
        keyPairGenerator.initialize(spec)
        val keyPair = keyPairGenerator.generateKeyPair()
        
        val publicKeyBytes = keyPair.public.encoded
        val meshNetId = hashDestination(Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP))
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MESHNET_ID, meshNetId).apply()
        
        return meshNetId
    }

    fun getMeshNetId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MESHNET_ID, null)
    }

    fun hasIdentity(context: Context): Boolean {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.containsAlias(KEY_ALIAS) && getMeshNetId(context) != null
    }

    fun hashDestination(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun encryptMessage(plaintext: String, recipientHash: String): String {
        return try {
            val key = deriveKeyFromHash(recipientHash)
            val cipher = Cipher.getInstance(AES_ALGORITHM)
            val iv = ByteArray(IV_LENGTH).apply { 
                System.arraycopy(key, 0, this, 0, IV_LENGTH) 
            }
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            Base64.encodeToString(ciphertext, Base64.DEFAULT)
        } catch (e: Exception) {
            ""
        }
    }

    fun decryptMessage(encryptedBase64: String, myMeshNetId: String): String {
        // 1. Try to decrypt as a BROADCAST (emergency) message
        tryDecrypt(encryptedBase64, "BROADCAST")?.let { return it }

        // 2. Try to decrypt as a MESH_CHAT (group) message
        tryDecrypt(encryptedBase64, "MESH_CHAT")?.let { return it }

        // 3. Try decrypting for this specific user (private)
        return tryDecrypt(encryptedBase64, myMeshNetId) ?: "[Encrypted Message]"
    }

    private fun tryDecrypt(encryptedBase64: String, keyHash: String): String? {
        return try {
            val key = deriveKeyFromHash(keyHash)
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
            null
        }
    }

    fun isMyPacket(destinationHash: String, myMeshNetId: String): Boolean {
        return myMeshNetId == destinationHash || 
               destinationHash == "BROADCAST" || 
               destinationHash == "MESH_CHAT"
    }

    private fun deriveKeyFromHash(hash: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(hash.toByteArray(Charsets.UTF_8))
    }

    fun generateOrLoadDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }
}
