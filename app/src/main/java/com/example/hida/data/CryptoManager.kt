package com.example.hida.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class CryptoManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    fun encryptToFile(inputStream: InputStream, file: File) {
        val encryptedFile = getEncryptedFile(file)
        val outputStream = encryptedFile.openFileOutput()
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }

    fun decryptFromFile(file: File): InputStream {
        val encryptedFile = getEncryptedFile(file)
        return encryptedFile.openFileInput()
    }

    private fun getEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            file,
            context,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }
}
