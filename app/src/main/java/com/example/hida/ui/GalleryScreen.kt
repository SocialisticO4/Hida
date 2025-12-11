package com.example.hida.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.hida.data.MediaRepository
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val prefs = remember { PreferencesManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var allMediaFiles by remember { mutableStateOf(emptyList<File>()) }
    var hasPermission by remember { mutableStateOf(false) }
    
    // Tab persistence with rememberSaveable
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // Session timeout - lock when returning from background after timeout
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Mark app as going to background with timestamp
                    prefs.markAppPaused()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Check if session expired while in background
                    if (prefs.isSessionExpired()) {
                        prefs.clearSession()
                        onLock()
                    } else {
                        // Session still valid, clear the paused flag
                        prefs.clearPausedFlag()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Image Loader
    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .components {
                add(com.example.hida.data.EncryptedMediaFetcher.Factory(repository))
            }
            .build()
    }

    // Filter media by tab
    val displayedMedia = remember(allMediaFiles, selectedTab) {
        if (selectedTab == 0) {
            allMediaFiles.filter { !repository.isVideo(it) }
        } else {
            allMediaFiles.filter { repository.isVideo(it) }
        }
    }
    
    // Group by month
    val groupedMedia = remember(displayedMedia) {
        displayedMedia
            .groupBy { file ->
                val cal = Calendar.getInstance().apply { timeInMillis = file.lastModified() }
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
            }
            .toSortedMap(compareByDescending { 
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(it)?.time ?: 0
            })
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (hasPermission && !isFakeMode) {
            scope.launch {
                allMediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
            }
        }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        hasPermission = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!hasPermission) {
            permissionLauncher.launch(permissions)
        } else if (!isFakeMode) {
            allMediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
        }
    }

    // Delete original file handler
    var pendingEncryptedFile by remember { mutableStateOf<File?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pendingEncryptedFile = null
        scope.launch {
            allMediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = repository.saveMediaFromUri(uri)
                result.onSuccess { newFile ->
                    pendingEncryptedFile = newFile
                    allMediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
                    
                    val intentSender = repository.deleteOriginal(uri)
                    if (intentSender != null) {
                        val request = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                        deleteLauncher.launch(request)
                    } else {
                        pendingEncryptedFile = null
                    }
                }.onFailure { e ->
                    android.widget.Toast.makeText(context, "Import failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Exit selection mode
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedFiles = emptySet()
    }
    
    // Delete selected files
    fun deleteSelected() {
        scope.launch {
            selectedFiles.forEach { path ->
                val file = File(path)
                if (file.exists()) repository.deleteMedia(file)
            }
            allMediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
            exitSelectionMode()
        }
    }

    // Back handler for selection mode
    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }

    HidaTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (isSelectionMode) {
                    // Selection Mode Top Bar
                    TopAppBar(
                        title = { Text("${selectedFiles.size} selected") },
                        navigationIcon = {
                            IconButton(onClick = { exitSelectionMode() }) {
                                Icon(Icons.Default.Close, "Cancel")
                            }
                        },
                        actions = {
                            // Select All
                            IconButton(onClick = {
                                selectedFiles = if (selectedFiles.size == displayedMedia.size) {
                                    emptySet()
                                } else {
                                    displayedMedia.map { it.absolutePath }.toSet()
                                }
                            }) {
                                Icon(
                                    if (selectedFiles.size == displayedMedia.size) Icons.Default.CheckBox 
                                    else Icons.Default.CheckBoxOutlineBlank,
                                    "Select All"
                                )
                            }
                            // Delete
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                enabled = selectedFiles.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = md3_dark_error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                } else {
                    // Normal Top Bar
                    CenterAlignedTopAppBar(
                        title = { Text("Vault", style = MaterialTheme.typography.titleLarge) },
                        actions = {
                            if (!isFakeMode) {
                                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSettingsClick() }) {
                                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLock() }) {
                                Icon(Icons.Default.Lock, "Lock", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            bottomBar = {
                if (!isSelectionMode) {
                    NavigationBar(
                        containerColor = md3_dark_surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectedTab = 0 },
                            icon = { Icon(if (selectedTab == 0) Icons.Filled.Image else Icons.Outlined.Image, "Photos") },
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
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectedTab = 1 },
                            icon = { Icon(if (selectedTab == 1) Icons.Filled.Movie else Icons.Outlined.Movie, "Videos") },
                            label = { Text("Videos") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectedTab = 2 },
                            icon = { Icon(if (selectedTab == 2) Icons.Filled.MusicNote else Icons.Default.MusicNote, "Audio") },
                            label = { Text("Audio") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); selectedTab = 3 },
                            icon = { Icon(if (selectedTab == 3) Icons.Filled.Description else Icons.Default.Description, "Documents") },
                            label = { Text("Docs") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!isFakeMode && !isSelectionMode && selectedTab < 2) {
                    LargeFloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Add, "Add Media", modifier = Modifier.size(32.dp))
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Coming Soon for Audio/Documents tabs
                if (selectedTab >= 2) {
                    ComingSoonContent(context, selectedTab)
                } else if (displayedMedia.isEmpty()) {
                    // Empty State
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (selectedTab == 0) Icons.Outlined.Image else Icons.Outlined.Movie,
                            null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (isFakeMode) "Nothing here" else if (selectedTab == 0) "No photos yet" else "No videos yet",
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
                    // Date-Grouped Grid
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        groupedMedia.forEach { (month, files) ->
                            // Month Header (Sticky)
                            stickyHeader {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    Text(
                                        text = month,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                            
                            // Grid of items for this month
                            item {
                                // Non-lazy grid inside LazyColumn item
                                val columns = 3
                                val rows = (files.size + columns - 1) / columns
                                
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (row in 0 until rows) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            for (col in 0 until columns) {
                                                val index = row * columns + col
                                                if (index < files.size) {
                                                    val file = files[index]
                                                    val isSelected = selectedFiles.contains(file.absolutePath)
                                                    
                                                    MediaGridItem(
                                                        file = file,
                                                        isVideo = repository.isVideo(file),
                                                        isMotionPhoto = repository.isMotionPhoto(file),
                                                        isSelected = isSelected,
                                                        isSelectionMode = isSelectionMode,
                                                        imageLoader = imageLoader,
                                                        modifier = Modifier.weight(1f),
                                                        onClick = {
                                                            if (isSelectionMode) {
                                                                selectedFiles = if (isSelected) {
                                                                    selectedFiles - file.absolutePath
                                                                } else {
                                                                    selectedFiles + file.absolutePath
                                                                }
                                                                if (selectedFiles.isEmpty()) exitSelectionMode()
                                                            } else {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                if (repository.isVideo(file)) {
                                                                    onPlayVideo(file.absolutePath)
                                                                } else {
                                                                    onViewImage(file.absolutePath)
                                                                }
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (!isSelectionMode) {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                isSelectionMode = true
                                                                selectedFiles = setOf(file.absolutePath)
                                                            }
                                                        }
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
        
        // Delete Confirmation Dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = T30,
                title = { Text("Delete ${selectedFiles.size} items?", color = OnSurfaceHigh) },
                text = { Text("This will permanently delete the selected items from your vault.", color = OnSurfaceMedium) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        deleteSelected()
                    }) {
                        Text("Delete", color = md3_dark_error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = OnSurfaceHigh)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    file: File,
    isVideo: Boolean,
    isMotionPhoto: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    imageLoader: coil.ImageLoader,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "itemScale"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(md3_dark_surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
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
        
        // Video play icon
        if (isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, md3_dark_scrim.copy(alpha = 0.4f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        
        // Motion photo badge
        if (isMotionPhoto && !isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
            }
        }
        
        // Selection checkbox
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f),
                        CircleShape
                    )
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ComingSoonContent(context: android.content.Context, selectedTab: Int) {
    val tabName = if (selectedTab == 2) "Audio" else "Documents"
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (selectedTab == 2) Icons.Default.MusicNote else Icons.Default.Description,
            null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Coming Soon",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "version 2.0",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "On popular demand by",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "𝗛𝘂𝘇𝗮𝗶𝗳𝗮𝗵 and his friends",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "it will be released",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(
            onClick = {
                // Try to open Instagram app first, fallback to web
                val username = "_huzaifah_18"
                val appUri = Uri.parse("instagram://user?username=$username")
                val webUri = Uri.parse("https://www.instagram.com/$username/")
                
                try {
                    val appIntent = Intent(Intent.ACTION_VIEW, appUri)
                    appIntent.setPackage("com.instagram.android")
                    context.startActivity(appIntent)
                } catch (e: Exception) {
                    // Instagram app not installed, open in browser
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                    context.startActivity(webIntent)
                }
            }
        ) {
            Text(
                text = "Click here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}
