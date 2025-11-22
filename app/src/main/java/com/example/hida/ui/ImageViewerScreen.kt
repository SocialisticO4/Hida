package com.example.hida.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.hida.data.MediaRepository
import com.example.hida.ui.theme.DarkBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }
    val file = File(filePath)
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Load image asynchronously with aggressive downsampling
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Decode bounds only
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                repository.getDecryptedStream(file).use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }

                // 2. Calculate inSampleSize (Target 1080p to be safe)
                val reqWidth = 1080
                val reqHeight = 1920
                
                var inSampleSize = 1
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight: Int = options.outHeight / 2
                    val halfWidth: Int = options.outWidth / 2

                    // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                    // height and width larger than the requested height and width.
                    while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                        inSampleSize *= 2
                    }
                    // Ensure we downsample at least once if it's huge but close to the limit
                    if (inSampleSize == 1 && (options.outHeight > 3000 || options.outWidth > 3000)) {
                        inSampleSize = 2
                    }
                }

                // 3. Decode with inSampleSize
                val finalOptions = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    this.inSampleSize = inSampleSize
                    this.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 // Reduce memory by 50% (no alpha)
                }
                
                repository.getDecryptedStream(file).use { stream ->
                    val decoded = android.graphics.BitmapFactory.decodeStream(stream, null, finalOptions)
                    if (decoded != null) {
                        bitmap = decoded
                    } else {
                        error = "Failed to decode image"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                error = "Error: ${e.message}"
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                error = "Out of Memory"
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Full Screen Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (error != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                    Text(text = error!!, color = Color.Red)
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Image?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteMedia(file)
                        showDeleteDialog = false
                        onBack() // Go back to gallery after delete
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
