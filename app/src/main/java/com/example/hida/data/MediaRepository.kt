package com.example.hida.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import android.content.IntentSender
import android.app.RecoverableSecurityException

class MediaRepository(private val context: Context) {

    private val cryptoManager = CryptoManager(context)
    private val directory = File(context.filesDir, "secure_media")

    init {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    suspend fun saveMediaFromUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val type = context.contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "dat"
            
            inputStream?.let { input ->
                val filename = "${UUID.randomUUID()}.$extension"
                val file = File(directory, filename)
                cryptoManager.encryptToFile(input, file)
            }
        }
    }

    fun getMediaFiles(): List<File> {
        return directory.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun getDecryptedStream(file: File): InputStream {
        return cryptoManager.decryptFromFile(file)
    }

    fun isVideo(file: File): Boolean {
        return file.extension.equals("mp4", ignoreCase = true) || 
               file.extension.equals("mov", ignoreCase = true) ||
               file.extension.equals("mkv", ignoreCase = true)
    }

    fun deleteOriginal(uri: Uri): IntentSender? {
        try {
            context.contentResolver.delete(uri, null, null)
            return null
        } catch (e: RecoverableSecurityException) {
            return e.userAction.actionIntent.intentSender
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun deleteMedia(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
