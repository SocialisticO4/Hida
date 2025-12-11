package com.example.hida.ui

import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.hida.data.EncryptedDataSource
import com.example.hida.data.MediaRepository
import com.example.hida.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    val repository = remember { MediaRepository(context) }
    val file = remember { File(filePath) }
    
    // Playback state
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    
    // Dialogs
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Double-tap seek feedback
    var showSeekLeft by remember { mutableStateOf(false) }
    var showSeekRight by remember { mutableStateOf(false) }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Initialize player with encrypted streaming source - INSTANT PLAYBACK!
    LaunchedEffect(Unit) {
        val dataSourceFactory = EncryptedDataSource.Factory(repository.cryptoManager)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
        
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
    }

    // Player listener
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isBuffering = player.playbackState == Player.STATE_BUFFERING
                isPlaying = player.isPlaying
                isEnded = player.playbackState == Player.STATE_ENDED
                duration = player.duration.coerceAtLeast(0L)
                currentTime = player.currentPosition
            }
        }
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    // Update progress
    LaunchedEffect(isPlaying) {
        while(isPlaying) {
            currentTime = exoPlayer.currentPosition
            delay(100)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Seek functions
    fun seekBy(deltaMs: Long) {
        val newPos = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, exoPlayer.duration)
        exoPlayer.seekTo(newPos)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    fun replay() {
        exoPlayer.seekTo(0)
        exoPlayer.play()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            seekBy(-10_000)
                            showSeekLeft = true
                            scope.launch { delay(300); showSeekLeft = false }
                        } else {
                            seekBy(10_000)
                            showSeekRight = true
                            scope.launch { delay(300); showSeekRight = false }
                        }
                    }
                )
            }
    ) {
        // Video Surface
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Buffering Indicator (minimal - just a spinner, no text)
        if (isBuffering && !isEnded) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Primary50
            )
        }
        
        // Double-tap seek feedback
        AnimatedVisibility(
            visible = showSeekLeft,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        
        AnimatedVisibility(
            visible = showSeekRight,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        
        // Controls Overlay (HUD)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                
                // === TOP AREA ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    IconButton(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onBack() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    
                    // Right side: Export + Delete
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    repository.exportMedia(file)
                                    onBack()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, "Export", tint = Color.White)
                        }
                        
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                        }
                    }
                }
                
                // === BOTTOM AREA - Control Island ===
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Time Display Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${formatTime(currentTime)} / ${formatTime(duration)}",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Thick Seekbar
                    Slider(
                        value = if (duration > 0) currentTime.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { progress ->
                            currentTime = (progress * duration).toLong()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(currentTime)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Primary50,
                            activeTrackColor = Primary50,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Transport Controls Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip Back 10s
                        FilledTonalIconButton(
                            onClick = { seekBy(-10_000) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Icon(Icons.Default.Replay10, "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        
                        // Hero Button (Play/Pause/Replay)
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Primary50)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when {
                                        isEnded -> replay()
                                        isPlaying -> exoPlayer.pause()
                                        else -> exoPlayer.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Crossfade(targetState = when {
                                isEnded -> "replay"
                                isPlaying -> "pause"
                                else -> "play"
                            }, label = "hero_icon") { state ->
                                Icon(
                                    imageVector = when (state) {
                                        "replay" -> Icons.Default.Replay
                                        "pause" -> Icons.Default.Pause
                                        else -> Icons.Default.PlayArrow
                                    },
                                    contentDescription = state,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        
                        // Skip Forward 10s
                        FilledTonalIconButton(
                            onClick = { seekBy(10_000) },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.15f)
                            )
                        ) {
                            Icon(Icons.Default.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
        
        // Delete Confirmation Dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = T30,
                title = { Text("Delete Video?", color = OnSurfaceHigh) },
                text = { Text("This will permanently delete this video from the vault.", color = OnSurfaceMedium) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            repository.deleteMedia(file)
                            showDeleteDialog = false
                            onBack()
                        }
                    }) {
                        Text("Delete", color = md3_dark_error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = OnSurfaceHigh)
                    }
                }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
