package com.example.hida.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.io.*
import java.nio.ByteBuffer

/**
 * CryptoManager - Chunked ChaCha20-Poly1305 Encryption
 * 
 * Uses chunked encryption for fast streaming playback WITH full authentication.
 * Each 64KB chunk is independently encrypted with its own auth tag.
 * This allows instant video playback like Files by Google.
 * 
 * File Format:
 * [12-byte master nonce][chunk0][chunk1]...[chunkN]
 * Each chunk: [encrypted data][16-byte auth tag]
 */
class CryptoManager(context: Context) {

    private val prefs = PreferencesManager(context)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    // ==================== KEY MANAGEMENT ====================

    private fun getMasterKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS_MASTER, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createMasterKey()
    }

    private fun createMasterKey(): SecretKey {
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS_MASTER,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }.generateKey()
    }

    private fun getChaChaKey(): SecretKey {
        val wrappedKeyStr = prefs.getWrappedKey()
        return if (wrappedKeyStr != null) {
            unwrapKey(wrappedKeyStr)
        } else {
            val newKey = generateRandomKey()
            val wrapped = wrapKey(newKey)
            prefs.saveWrappedKey(wrapped)
            newKey
        }
    }

    private fun generateRandomKey(): SecretKey {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "ChaCha20")
    }

    private fun wrapKey(key: SecretKey): String {
        val masterKey = getMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION_MASTER)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(key.encoded)
        
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun unwrapKey(wrappedStr: String): SecretKey {
        val combined = Base64.decode(wrappedStr, Base64.NO_WRAP)
        val masterKey = getMasterKey()
        
        val iv = ByteArray(MASTER_IV_SIZE)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        
        val encryptedSize = combined.size - iv.size
        val encryptedBytes = ByteArray(encryptedSize)
        System.arraycopy(combined, iv.size, encryptedBytes, 0, encryptedSize)
        
        val spec = GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION_MASTER)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
        
        val keyBytes = cipher.doFinal(encryptedBytes)
        return SecretKeySpec(keyBytes, "ChaCha20")
    }

    // ==================== CHUNKED ENCRYPTION ====================

    /**
     * Derive a unique nonce for each chunk.
     * Uses master nonce XOR chunk index.
     */
    private fun deriveChunkNonce(masterNonce: ByteArray, chunkIndex: Long): ByteArray {
        val derived = masterNonce.copyOf()
        val indexBytes = ByteBuffer.allocate(8).putLong(chunkIndex).array()
        // XOR the last 8 bytes of nonce with chunk index
        for (i in 0 until 8) {
            derived[derived.size - 8 + i] = (derived[derived.size - 8 + i].toInt() xor indexBytes[i].toInt()).toByte()
        }
        return derived
    }

    /**
     * Encrypt a single chunk with ChaCha20-Poly1305.
     * Returns encrypted data with 16-byte auth tag appended.
     */
    fun encryptChunk(plaintext: ByteArray, masterNonce: ByteArray, chunkIndex: Long): ByteArray {
        val chunkNonce = deriveChunkNonce(masterNonce, chunkIndex)
        val cipher = Cipher.getInstance(TRANSFORMATION_CHACHA_POLY)
        cipher.init(Cipher.ENCRYPT_MODE, getChaChaKey(), IvParameterSpec(chunkNonce))
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypt a single chunk with ChaCha20-Poly1305.
     * Input includes the 16-byte auth tag.
     */
    fun decryptChunk(ciphertext: ByteArray, masterNonce: ByteArray, chunkIndex: Long): ByteArray {
        val chunkNonce = deriveChunkNonce(masterNonce, chunkIndex)
        val cipher = Cipher.getInstance(TRANSFORMATION_CHACHA_POLY)
        cipher.init(Cipher.DECRYPT_MODE, getChaChaKey(), IvParameterSpec(chunkNonce))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Generate a new random master nonce for a file.
     */
    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    companion object {
        private const val KEY_ALIAS_MASTER = "HidaMasterKey"
        
        private const val TRANSFORMATION_MASTER = "AES/GCM/NoPadding"
        private const val TRANSFORMATION_CHACHA_POLY = "ChaCha20-Poly1305"
        
        const val MASTER_IV_SIZE = 12
        const val NONCE_SIZE = 12
        const val CHUNK_SIZE = 256 * 1024 // 256KB chunks for better high-bitrate video performance
        const val AUTH_TAG_SIZE = 16
        const val ENCRYPTED_CHUNK_SIZE = CHUNK_SIZE + AUTH_TAG_SIZE
    }
}
