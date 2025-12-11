package com.example.hida.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * ExoPlayer DataSource for Chunked ChaCha20-Poly1305 encrypted files.
 * 
 * Optimized for high-bitrate video (4K HDR) with:
 * - 256KB chunks (reduced crypto overhead)
 * - LRU chunk cache (4 chunks = 1MB) for moov atom re-reads
 * - Efficient sequential read support
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EncryptedDataSource(private val cryptoManager: CryptoManager) : BaseDataSource(true) {

    private var randomAccessFile: RandomAccessFile? = null
    private var masterNonce: ByteArray? = null
    private var plaintextLength: Long = 0
    private var currentPosition: Long = 0
    private var bytesRemaining: Long = 0
    private var opened = false
    private var targetUri: Uri? = null

    // LRU Chunk Cache (4 chunks = 1MB for 256KB chunks)
    private val chunkCache = object : LinkedHashMap<Long, ByteArray>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean {
            return size > 4
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        targetUri = dataSpec.uri
        transferInitializing(dataSpec)

        try {
            val file = File(dataSpec.uri.path ?: throw IOException("Invalid URI"))
            val raf = RandomAccessFile(file, "r")
            randomAccessFile = raf

            // Read 12-byte master nonce
            val nonce = ByteArray(CryptoManager.NONCE_SIZE)
            if (raf.read(nonce) != CryptoManager.NONCE_SIZE) {
                throw IOException("Invalid file: missing nonce")
            }
            masterNonce = nonce

            // Calculate plaintext length
            val fileLength = file.length()
            val encryptedDataLength = fileLength - CryptoManager.NONCE_SIZE
            
            val fullChunks = encryptedDataLength / CryptoManager.ENCRYPTED_CHUNK_SIZE
            val lastChunkEncryptedSize = encryptedDataLength % CryptoManager.ENCRYPTED_CHUNK_SIZE
            
            plaintextLength = if (lastChunkEncryptedSize > CryptoManager.AUTH_TAG_SIZE) {
                fullChunks * CryptoManager.CHUNK_SIZE + (lastChunkEncryptedSize - CryptoManager.AUTH_TAG_SIZE)
            } else if (lastChunkEncryptedSize == 0L && fullChunks > 0) {
                fullChunks * CryptoManager.CHUNK_SIZE
            } else if (encryptedDataLength > CryptoManager.AUTH_TAG_SIZE) {
                // Single chunk file
                encryptedDataLength - CryptoManager.AUTH_TAG_SIZE
            } else {
                throw IOException("Invalid encrypted file format")
            }

            // Handle seek position
            currentPosition = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                plaintextLength - currentPosition
            }

            opened = true
            transferStarted(dataSpec)
            return bytesRemaining

        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        try {
            var totalBytesRead = 0
            var bufferOffset = offset
            var remainingToRead = minOf(length.toLong(), bytesRemaining).toInt()

            while (remainingToRead > 0) {
                val chunkIndex = currentPosition / CryptoManager.CHUNK_SIZE
                val offsetInChunk = (currentPosition % CryptoManager.CHUNK_SIZE).toInt()

                // Get chunk from cache or load
                val chunkData = getChunkCached(chunkIndex)
                    ?: return if (totalBytesRead > 0) totalBytesRead else C.RESULT_END_OF_INPUT
                
                val availableInChunk = chunkData.size - offsetInChunk
                val bytesToCopy = minOf(remainingToRead, availableInChunk)

                System.arraycopy(chunkData, offsetInChunk, buffer, bufferOffset, bytesToCopy)

                currentPosition += bytesToCopy
                bufferOffset += bytesToCopy
                totalBytesRead += bytesToCopy
                remainingToRead -= bytesToCopy
                bytesRemaining -= bytesToCopy
            }

            bytesTransferred(totalBytesRead)
            return totalBytesRead

        } catch (e: Exception) {
            throw IOException("Decryption error: ${e.message}", e)
        }
    }

    private fun getChunkCached(chunkIndex: Long): ByteArray? {
        // Check cache first
        chunkCache[chunkIndex]?.let { return it }
        
        // Load and decrypt
        val chunk = loadAndDecryptChunk(chunkIndex)
        if (chunk != null) {
            chunkCache[chunkIndex] = chunk
        }
        return chunk
    }

    private fun loadAndDecryptChunk(chunkIndex: Long): ByteArray? {
        val raf = randomAccessFile ?: return null
        val nonce = masterNonce ?: return null

        val chunkFileOffset = CryptoManager.NONCE_SIZE + (chunkIndex * CryptoManager.ENCRYPTED_CHUNK_SIZE)
        
        if (chunkFileOffset >= raf.length()) return null
        
        raf.seek(chunkFileOffset)

        val remainingEncrypted = raf.length() - chunkFileOffset
        val encryptedChunkSize = minOf(CryptoManager.ENCRYPTED_CHUNK_SIZE.toLong(), remainingEncrypted).toInt()

        if (encryptedChunkSize <= CryptoManager.AUTH_TAG_SIZE) return null

        val encryptedData = ByteArray(encryptedChunkSize)
        val bytesRead = raf.read(encryptedData)
        if (bytesRead != encryptedChunkSize) return null

        return try {
            cryptoManager.decryptChunk(encryptedData, nonce, chunkIndex)
        } catch (e: Exception) {
            null
        }
    }

    override fun getUri(): Uri? = targetUri

    override fun close() {
        if (opened) {
            opened = false
            try {
                randomAccessFile?.close()
            } catch (e: IOException) {
                // Ignore
            }
            randomAccessFile = null
            masterNonce = null
            chunkCache.clear()
            transferEnded()
        }
    }

    class Factory(private val cryptoManager: CryptoManager) : androidx.media3.datasource.DataSource.Factory {
        override fun createDataSource(): androidx.media3.datasource.DataSource {
            return EncryptedDataSource(cryptoManager)
        }
    }
}
