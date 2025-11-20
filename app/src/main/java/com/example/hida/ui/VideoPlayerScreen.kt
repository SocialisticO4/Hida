package com.example.hida.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hida.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VideoPlayerScreen(
    filePath: String,
    repository: MediaRepository
) {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }

    // Decrypt to temp file
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            val encryptedFile = File(filePath)
            if (encryptedFile.exists()) {
                val tempFile = File.createTempFile("temp_video", ".mp4", context.cacheDir)
                tempFile.deleteOnExit() // Ensure cleanup
                
                // Decrypt stream to temp file
                repository.getDecryptedStream(encryptedFile).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                videoUri = Uri.fromFile(tempFile)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (videoUri != null) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setMediaController(MediaController(ctx))
                        setVideoURI(videoUri)
                        setOnPreparedListener { mp ->
                            mp.start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
