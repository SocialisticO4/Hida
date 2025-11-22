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
                val tempFile = File.createTempFile("temp_video_", ".mp4", context.cacheDir)
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
        return directory.listFiles()?.toList() ?: emptyList()
    }

    fun isVideo(file: File): Boolean {
        return file.name.contains(".mp4") || file.name.contains(".mov") || file.name.contains(".avi")
    }

    fun deleteMedia(file: File): Boolean {
        return file.delete()
    }

    suspend fun deleteOriginal(uri: Uri): IntentSender? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(uri, null, null)
                null // Success, no permission needed
            } catch (securityEx: SecurityException) {
                // Android 10/11+ Scoped Storage
                val recoverable = securityEx as? RecoverableSecurityException
                if (recoverable != null) {
                    return@withContext recoverable.userAction.actionIntent.intentSender
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ specific trash request
                    return@withContext MediaStore.createTrashRequest(
                        context.contentResolver, 
                        listOf(uri), 
                        true
                    ).intentSender
                }
                throw securityEx
            }
        }
    }
}
