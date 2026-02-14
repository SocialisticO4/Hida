package com.example.hida.ui

import android.net.Uri

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.hida.R

import com.example.hida.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val repository = LocalMediaRepository.current
    val file = remember { File(filePath) }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // Motion photo
    val isMotionPhoto = remember { repository.isMotionPhoto(file) }
    var playingVideo by remember { mutableStateOf(false) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(false) }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
        }
    }
    
    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .components {
                add(com.example.hida.data.EncryptedMediaFetcher.Factory(repository))
            }
            .build()
    }
    
    // Handle video playback
    LaunchedEffect(playingVideo) {
        if (playingVideo && isMotionPhoto) {
            if (videoFile == null) {
                loading = true
                videoFile = repository.getMotionPhotoVideoTempFile(file)
                loading = false
            }
            videoFile?.let {
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(it)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        } else {
            exoPlayer.pause()
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            videoFile?.delete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // Content
        if (playingVideo && videoFile != null) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f; offsetX = 0f; offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x; offsetY += pan.y
                            } else {
                                offsetX = 0f; offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offsetX, translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )
            }
        }
        
        // Loading indicator
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Top bar
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                
                Row {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            val ok = repository.exportMedia(file)
                            if (ok) onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Export", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                    }
                }
            }
        }

        // Motion photo toggle
        if (isMotionPhoto && showControls) {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playingVideo = !playingVideo
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            ) {
                Icon(
                    if (playingVideo) Icons.Default.Image else Icons.Default.PlayArrow,
                    contentDescription = if (playingVideo) "Show Image" else "Play Video",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Delete dialog
        if (showDeleteDialog) {
            HidaConfirmDialog(
                title = stringResource(R.string.image_delete_title),
                text = stringResource(R.string.image_delete_message),
                onConfirm = {
                    scope.launch {
                        repository.deleteMedia(file)
                        showDeleteDialog = false
                        onBack()
                    }
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }
}
