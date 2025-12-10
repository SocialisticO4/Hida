package com.example.hida.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
    
    var allMediaFiles by remember { mutableStateOf(emptyList<File>()) }
    var hasPermission by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showFabMenu by remember { mutableStateOf(false) }
    
    // Custom ImageLoader for Encrypted Files
    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .components {
                add(com.example.hida.data.EncryptedMediaFetcher.Factory(repository))
            }
            .build()
    }

    // Filter media based on selected tab
    val displayedMedia = remember(allMediaFiles, selectedTab) {
        if (selectedTab == 0) {
            allMediaFiles.filter { !repository.isVideo(it) } // Photos
        } else {
            allMediaFiles.filter { repository.isVideo(it) } // Videos
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission && !isFakeMode) {
            scope.launch {
                allMediaFiles = withContext(Dispatchers.IO) {
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
            allMediaFiles = withContext(Dispatchers.IO) {
                repository.getMediaFiles()
            }
        }
    }

    // Pending file for delete confirmation
    var pendingEncryptedFile by remember { mutableStateOf<File?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            pendingEncryptedFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                    Toast.makeText(context, "Move cancelled", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        allMediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
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
                    allMediaFiles = withContext(Dispatchers.IO) {
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
        showFabMenu = false
    }

    HidaTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Vault",
                            style = MaterialTheme.typography.titleLarge
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLock()
                            }
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = md3_dark_surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTab = 0
                        },
                        icon = {
                            Icon(
                                if (selectedTab == 0) Icons.Filled.Image else Icons.Outlined.Image,
                                contentDescription = "Photos"
                            )
                        },
                        label = { Text("Photos") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTab = 1
                        },
                        icon = {
                            Icon(
                                if (selectedTab == 1) Icons.Filled.Movie else Icons.Outlined.Movie,
                                contentDescription = "Videos"
                            )
                        },
                        label = { Text("Videos") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            },
            floatingActionButton = {
                if (!isFakeMode) {
                    Box {
                        // FAB Menu Items
                        AnimatedVisibility(
                            visible = showFabMenu,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 72.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SmallFloatingActionButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pickMedia.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Icon(Icons.Default.Image, "Import Photo")
                                }
                                SmallFloatingActionButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pickMedia.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.VideoOnly
                                            )
                                        )
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Icon(Icons.Default.Movie, "Import Video")
                                }
                            }
                        }
                        
                        // Main FAB
                        LargeFloatingActionButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showFabMenu = !showFabMenu
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape
                        ) {
                            Icon(
                                if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "Add Media",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (displayedMedia.isEmpty()) {
                    // Empty State
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (selectedTab == 0) Icons.Outlined.Image else Icons.Outlined.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (isFakeMode) "Nothing here" 
                                   else if (selectedTab == 0) "No photos yet"
                                   else "No videos yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isFakeMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to add ${if (selectedTab == 0) "photos" else "videos"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    // Staggered Masonry Grid
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        items(displayedMedia) { file ->
                            MediaThumbnailCard(
                                file = file,
                                isVideo = repository.isVideo(file),
                                imageLoader = imageLoader,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
fun MediaThumbnailCard(
    file: File,
    isVideo: Boolean,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    // Random height for masonry effect
    val aspectRatio = remember { 
        listOf(0.75f, 1f, 1.25f, 1.5f).random()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = MaterialTheme.colorScheme.primary),
                onClick = onClick
            ),
        shape = HidaShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = md3_dark_surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
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
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    md3_dark_scrim.copy(alpha = 0.6f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
