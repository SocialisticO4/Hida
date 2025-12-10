package com.example.hida.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

class MediaRepository(private val context: Context) {
    private val cryptoManager = CryptoManager()
    private val directory = File(context.filesDir, "secure_media").apply {
        if (!exists()) mkdirs()
    }

    suspend fun saveMediaFromUri(uri: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val type = context.contentResolver.getType(uri)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "dat"
                
                inputStream?.use { input ->
                    val filename = "${UUID.randomUUID()}.$extension"
                    val file = File(directory, filename)
                    
                    // Get encryption cipher (IV is auto-generated)
                    val cipher = cryptoManager.getEncryptCipher()
                    val iv = cipher.iv
                    
                    FileOutputStream(file).use { fos ->
                        // CRITICAL: Write IV first (12 bytes)
                        fos.write(iv)
                        
                        // Write encrypted data
                        CipherOutputStream(fos, cipher).use { cos ->
                            input.copyTo(cos)
                        }
                    }
                    
                    file
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun getDecryptedStream(file: File): InputStream {
        val fis = FileInputStream(file)
        
        // Read the IV (first 12 bytes)
        val iv = ByteArray(CryptoManager.IV_SIZE)
        val bytesRead = fis.read(iv)
        
        if (bytesRead != CryptoManager.IV_SIZE) {
            fis.close()
            throw Exception("File corrupted: Missing or incomplete IV header")
        }
        
        // Decrypt the rest of the stream
        val cipher = cryptoManager.getDecryptCipher(iv)
        return CipherInputStream(fis, cipher)
    }

    suspend fun getDecryptedTempFile(encryptedFile: File): File? {
        return withContext(Dispatchers.IO) {
            try {
                // Get the original extension from the encrypted file name
                val originalExtension = encryptedFile.extension.ifEmpty { "mp4" }
                val tempFile = File.createTempFile("temp_video_", ".$originalExtension", context.cacheDir)
                tempFile.deleteOnExit()
                
                getDecryptedStream(encryptedFile).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                tempFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun getMediaFiles(): List<File> {
        return directory.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun isVideo(file: File): Boolean {
        val videoExtensions = listOf("mp4", "mov", "avi", "mkv", "webm", "3gp", "m4v")
        return videoExtensions.any { file.name.lowercase().endsWith(".$it") }
    }

    fun deleteMedia(file: File): Boolean {
        return file.delete()
    }

    suspend fun deleteOriginal(uri: Uri): IntentSender? {
        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ - Use createDeleteRequest which always shows system dialog
                    return@withContext MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(uri)
                    ).intentSender
                } else {
                    // Android 10 and below - Try direct delete
                    try {
                        context.contentResolver.delete(uri, null, null)
                        null
                    } catch (securityEx: SecurityException) {
                        val recoverable = securityEx as? RecoverableSecurityException
                        recoverable?.userAction?.actionIntent?.intentSender
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun exportMedia(encryptedFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val originalExtension = encryptedFile.extension.ifEmpty { "jpg" }
                val isVideo = isVideo(encryptedFile)
                val mimeType = if (isVideo) "video/$originalExtension" else "image/$originalExtension"
                
                // Create MediaStore entry
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "Hida_Export_${System.currentTimeMillis()}.$originalExtension")
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/HidaExport" else "Pictures/HidaExport")
                    }
                }

                val collection = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val uri = context.contentResolver.insert(collection, values) ?: return@withContext false

                // Decrypt and write to MediaStore
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    getDecryptedStream(encryptedFile).use { input ->
                        input.copyTo(output)
                    }
                }

                // Mark as finished (Android Q+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }

                // Delete encrypted original after successful export
                encryptedFile.delete()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
