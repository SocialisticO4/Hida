package com.example.hida.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.hida.data.MediaRepository
import com.example.hida.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    isFakeMode: Boolean = false,
    onLock: () -> Unit,
    onSettingsClick: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onViewImage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val repository = remember { MediaRepository(context) }
    var mediaFiles by remember { mutableStateOf(emptyList<File>()) }
    var hasPermission by remember { mutableStateOf(false) }
    
    // Custom ImageLoader for Encrypted Files
    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .components {
                add(com.example.hida.data.EncryptedMediaFetcher.Factory(repository))
            }
            .build()
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission && !isFakeMode) {
            scope.launch {
                mediaFiles = withContext(Dispatchers.IO) {
                    repository.getMediaFiles()
                }
            }
        }
    }

    // Check and request permissions
    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        hasPermission = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermission) {
            permissionLauncher.launch(permissions)
        } else if (!isFakeMode) {
            mediaFiles = withContext(Dispatchers.IO) {
                repository.getMediaFiles()
            }
        }
    }

    // State to track the file currently being moved
    var pendingEncryptedFile by remember { mutableStateOf<File?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            pendingEncryptedFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                    Toast.makeText(context, "Move cancelled", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        mediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
                    }
                }
            }
        } else {
            Toast.makeText(context, "Moved to vault", Toast.LENGTH_SHORT).show()
        }
        pendingEncryptedFile = null
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val newFile = repository.saveMediaFromUri(uri)
                
                if (newFile != null) {
                    pendingEncryptedFile = newFile
                    mediaFiles = withContext(Dispatchers.IO) {
                        repository.getMediaFiles()
                    }
                    
                    val intentSender = repository.deleteOriginal(uri)
                    if (intentSender != null) {
                        val request = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                        deleteLauncher.launch(request)
                    } else {
                        Toast.makeText(context, "Added to vault", Toast.LENGTH_SHORT).show()
                        pendingEncryptedFile = null
                    }
                }
            }
        }
    }

    HidaTheme {
        Scaffold(
            containerColor = PureBlack,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = "Vault",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.W300
                            ),
                            color = TextPrimary
                        )
                    },
                    actions = {
                        if (!isFakeMode) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSettingsClick()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = TextSecondary
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLock()
                            }
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = BlackCherry)
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = PureBlack,
                        scrolledContainerColor = SurfaceBlack
                    )
                )
            },
            floatingActionButton = {
                if (!isFakeMode) {
                    ExpressiveFAB(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pickMedia.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                )
                            )
                        }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(PureBlack)
            ) {
                if (mediaFiles.isEmpty()) {
                    // Expressive Empty State
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = SurfaceContainer
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (isFakeMode) "Nothing here" else "Your vault is empty",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextTertiary
                        )
                        if (!isFakeMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to add photos & videos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    // Media Grid with Expressive Cards
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(mediaFiles) { file ->
                            ExpressiveMediaCard(
                                file = file,
                                isVideo = repository.isVideo(file),
                                imageLoader = imageLoader,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val encodedPath = URLEncoder.encode(
                                        file.absolutePath,
                                        StandardCharsets.UTF_8.toString()
                                    )
                                    if (repository.isVideo(file)) {
                                        onPlayVideo(file.absolutePath)
                                    } else {
                                        onViewImage(file.absolutePath)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveFAB(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fabScale"
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.scale(scale),
        containerColor = BlackCherry,
        contentColor = TextPrimary,
        shape = CircleShape,
        interactionSource = interactionSource
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add Media",
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun ExpressiveMediaCard(
    file: File,
    isVideo: Boolean,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(file)
                    .crossfade(true)
                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Video overlay
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    PureBlack.copy(alpha = 0.6f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = TextPrimary.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}
