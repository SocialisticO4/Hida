package com.example.hida.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * MediaRepository - Secure Media Storage with Chunked Encryption
 * 
 * Uses Chunked ChaCha20-Poly1305 for all files.
 * File format: [12-byte nonce][64KB chunk + 16B tag][64KB chunk + 16B tag]...
 * 
 * This enables instant streaming playback for videos while maintaining
 * full authenticated encryption (AEAD).
 */
class MediaRepository(private val context: Context) {

    private val directory = File(context.filesDir, "secure_media")
    private val prefs = PreferencesManager(context)
    val cryptoManager = CryptoManager(context)
    private val motionPhotoParser = MotionPhotoParser()

    init {
        if (!directory.exists()) {
            directory.mkdirs()
        } else {
            if (prefs.getWrappedKey() == null && (directory.list()?.isNotEmpty() == true)) {
                clearAllMedia()
            }
        }
    }
    
    fun clearAllMedia() {
        directory.listFiles()?.forEach { 
            if (it.exists()) it.delete() 
        }
    }

    suspend fun getMediaFiles(): List<File> = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".meta") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Import and encrypt media using chunked ChaCha20-Poly1305.
     * Uses streaming to handle large files without loading into memory.
     */
    suspend fun saveMediaFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            
            var ext = "jpg"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val originalName = cursor.getString(nameIndex)
                        val lastDot = originalName.lastIndexOf(".")
                        if (lastDot != -1) {
                            ext = originalName.substring(lastDot + 1).lowercase()
                        }
                    }
                }
            }

            val encryptedFile = File(directory, "${UUID.randomUUID()}.$ext")
            val isVideoFile = ext in listOf("mp4", "3gp", "webm", "mkv", "avi", "mov", "m4v")
            
            // For photos, we need to read bytes for motion photo detection
            // For videos, use streaming to avoid memory issues
            if (isVideoFile) {
                // Stream encryption for large video files
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    encryptFromStream(inputStream, encryptedFile)
                } ?: throw Exception("Failed to open video stream")
            } else {
                // Photos - read bytes for motion photo detection
                val originalBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Failed to read file")
                
                encryptToFile(originalBytes, encryptedFile)
                
                // Check for motion photo
                val motionInfo = motionPhotoParser.getMotionPhotoInfo(originalBytes)
                if (motionInfo != null) {
                    saveMetadata(encryptedFile, motionInfo)
                }
            }
            
            Result.success(encryptedFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Encrypt from InputStream using streaming (for large files like videos).
     * Memory usage is constant regardless of file size.
     */
    private fun encryptFromStream(inputStream: InputStream, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            // Write master nonce
            val nonce = cryptoManager.generateNonce()
            fos.write(nonce)
            
            // Read and encrypt in chunks
            val buffer = ByteArray(CryptoManager.CHUNK_SIZE)
            var chunkIndex = 0L
            
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                
                val chunkData = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                val encryptedChunk = cryptoManager.encryptChunk(chunkData, nonce, chunkIndex)
                fos.write(encryptedChunk)
                
                chunkIndex++
            }
        }
    }

    /**
     * Encrypt byte array to file using chunked ChaCha20-Poly1305.
     */
    private fun encryptToFile(plaintext: ByteArray, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            // Write master nonce
            val nonce = cryptoManager.generateNonce()
            fos.write(nonce)
            
            // Encrypt in chunks
            var offset = 0
            var chunkIndex = 0L
            while (offset < plaintext.size) {
                val chunkEnd = minOf(offset + CryptoManager.CHUNK_SIZE, plaintext.size)
                val chunkData = plaintext.copyOfRange(offset, chunkEnd)
                
                val encryptedChunk = cryptoManager.encryptChunk(chunkData, nonce, chunkIndex)
                fos.write(encryptedChunk)
                
                offset = chunkEnd
                chunkIndex++
            }
        }
    }

    /**
     * Decrypt entire file to bytes (for photos and small files).
     */
    suspend fun getDecryptedBytes(file: File): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val raf = RandomAccessFile(file, "r")
            
            // Read nonce
            val nonce = ByteArray(CryptoManager.NONCE_SIZE)
            raf.read(nonce)
            
            // Decrypt all chunks
            val result = mutableListOf<Byte>()
            var chunkIndex = 0L
            
            while (raf.filePointer < raf.length()) {
                val remaining = raf.length() - raf.filePointer
                val encryptedChunkSize = minOf(CryptoManager.ENCRYPTED_CHUNK_SIZE.toLong(), remaining).toInt()
                
                if (encryptedChunkSize <= CryptoManager.AUTH_TAG_SIZE) break
                
                val encryptedChunk = ByteArray(encryptedChunkSize)
                raf.read(encryptedChunk)
                
                val decryptedChunk = cryptoManager.decryptChunk(encryptedChunk, nonce, chunkIndex)
                result.addAll(decryptedChunk.toList())
                
                chunkIndex++
            }
            
            raf.close()
            result.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get decryption stream for photos (wraps chunked decryption).
     */
    fun getDecryptedStream(file: File): InputStream {
        return ChunkedDecryptionInputStream(file, cryptoManager)
    }
    
    /**
     * Delete original file from MediaStore.
     */
    suspend fun deleteOriginal(uri: Uri): IntentSender? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(uri, null, null)
            null
        } catch (e: SecurityException) {
            val recoverableSecurityException = e as? RecoverableSecurityException
            recoverableSecurityException?.userAction?.actionIntent?.intentSender
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveMetadata(mediaFile: File, motionInfo: MotionPhotoParser.MotionPhotoInfo) {
        val metaFile = File(mediaFile.parent, "${mediaFile.name}.meta")
        val content = "v=1;offset=${motionInfo.videoOffset}".toByteArray()
        try {
            encryptToFile(content, metaFile)
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun loadMetadata(mediaFile: File): MotionPhotoParser.MotionPhotoInfo? {
        val metaFile = File(mediaFile.parent, "${mediaFile.name}.meta")
        if (!metaFile.exists()) return null
        
        return try {
            val content = getDecryptedBytes(metaFile)?.toString(Charsets.UTF_8)
            val offset = content?.substringAfter("offset=")?.toLongOrNull() ?: 0L
            MotionPhotoParser.MotionPhotoInfo(offset, 0, 0)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Export media to public gallery.
     */
    suspend fun exportMedia(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val isVideo = isVideo(file)
            
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val details = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "restored_${file.name.substringBeforeLast('.')}")
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Restored" else "Pictures/Restored")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            
            val uri = resolver.insert(collection, details) ?: return@withContext false
            
            resolver.openOutputStream(uri)?.use { output ->
                getDecryptedStream(file).use { input ->
                    input.copyTo(output)
                }
            }
            
            details.clear()
            details.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, details, null, null)
            
            deleteMedia(file)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun deleteMedia(file: File) {
        withContext(Dispatchers.IO) {
            if (file.exists()) file.delete()
            val meta = File(file.parent, "${file.name}.meta")
            if (meta.exists()) meta.delete()
        }
    }

    fun isVideo(file: File): Boolean {
        return file.extension.lowercase() in listOf("mp4", "3gp", "webm", "mkv", "avi", "mov")
    }
    
    fun isMotionPhoto(file: File): Boolean {
        return File(file.parent, "${file.name}.meta").exists()
    }

    suspend fun getMotionPhotoImageStream(file: File): InputStream {
        return getDecryptedStream(file)
    }
    
    suspend fun getMotionPhotoVideoTempFile(file: File): File? {
        val info = loadMetadata(file) ?: return null
        val bytes = getDecryptedBytes(file) ?: return null
        
        return withContext(Dispatchers.IO) {
            try {
                val videoBytes = bytes.copyOfRange(info.videoOffset.toInt(), bytes.size)
                val tempFile = File.createTempFile("motion_video", ".mp4", context.cacheDir)
                tempFile.deleteOnExit()
                tempFile.writeBytes(videoBytes)
                tempFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

/**
 * InputStream wrapper for chunked decrypted data.
 */
class ChunkedDecryptionInputStream(
    private val file: File,
    private val cryptoManager: CryptoManager
) : InputStream() {

    private val raf = RandomAccessFile(file, "r")
    private val nonce: ByteArray
    private var currentChunkIndex = 0L
    private var currentChunkData: ByteArray? = null
    private var currentChunkOffset = 0
    private var isEof = false

    init {
        nonce = ByteArray(CryptoManager.NONCE_SIZE)
        raf.read(nonce)
    }

    override fun read(): Int {
        if (isEof) return -1
        
        if (currentChunkData == null || currentChunkOffset >= currentChunkData!!.size) {
            if (!loadNextChunk()) {
                isEof = true
                return -1
            }
        }
        
        return currentChunkData!![currentChunkOffset++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isEof) return -1
        if (len == 0) return 0

        var totalRead = 0
        var offset = off
        var remaining = len

        while (remaining > 0) {
            if (currentChunkData == null || currentChunkOffset >= currentChunkData!!.size) {
                if (!loadNextChunk()) {
                    isEof = true
                    break
                }
            }

            val chunk = currentChunkData!!
            val available = chunk.size - currentChunkOffset
            val toCopy = minOf(remaining, available)

            System.arraycopy(chunk, currentChunkOffset, b, offset, toCopy)
            currentChunkOffset += toCopy
            offset += toCopy
            remaining -= toCopy
            totalRead += toCopy
        }

        return if (totalRead == 0 && isEof) -1 else totalRead
    }

    private fun loadNextChunk(): Boolean {
        if (raf.filePointer >= raf.length()) return false

        val remaining = raf.length() - raf.filePointer
        val encryptedChunkSize = minOf(CryptoManager.ENCRYPTED_CHUNK_SIZE.toLong(), remaining).toInt()

        if (encryptedChunkSize <= CryptoManager.AUTH_TAG_SIZE) return false

        val encryptedChunk = ByteArray(encryptedChunkSize)
        raf.read(encryptedChunk)

        currentChunkData = cryptoManager.decryptChunk(encryptedChunk, nonce, currentChunkIndex)
        currentChunkIndex++
        currentChunkOffset = 0
        return true
    }

    override fun close() {
        raf.close()
    }
}
