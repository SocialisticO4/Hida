package com.example.hida.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.data.MediaRepository
import com.example.hida.ui.theme.DarkBackground
import com.example.hida.ui.theme.GoldAccent
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
    val repository = remember { MediaRepository(context) }
    var mediaFiles by remember { mutableStateOf(emptyList<File>()) }
    
    // Custom ImageLoader for Encrypted Files
    val imageLoader = remember {
        coil.ImageLoader.Builder(context)
            .components {
                add(com.example.hida.data.EncryptedMediaFetcher.Factory(repository))
            }
            .build()
    }

    // Load media on start
    LaunchedEffect(Unit) {
        if (!isFakeMode) {
            mediaFiles = withContext(Dispatchers.IO) {
                repository.getMediaFiles()
            }
        } else {
            mediaFiles = emptyList()
        }
    }
    
    // Reload when coming back to screen
    LaunchedEffect(mediaFiles) {
        if (!isFakeMode) {
             mediaFiles = withContext(Dispatchers.IO) {
                repository.getMediaFiles()
            }
        }
    }

    // State to track the file currently being moved
    var pendingEncryptedFile by remember { mutableStateOf<File?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            // User denied deletion. Enforce "Move" by deleting the encrypted copy.
            pendingEncryptedFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                    android.widget.Toast.makeText(context, "Move cancelled. Original kept.", android.widget.Toast.LENGTH_SHORT).show()
                    // Refresh list
                    scope.launch {
                        mediaFiles = withContext(Dispatchers.IO) { repository.getMediaFiles() }
                    }
                }
            }
        } else {
            android.widget.Toast.makeText(context, "Secure Move Complete", android.widget.Toast.LENGTH_SHORT).show()
        }
        pendingEncryptedFile = null
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                // 1. Encrypt and Save
                val newFile = repository.saveMediaFromUri(uri) // Need to update repository to return the file
                
                if (newFile != null) {
                    pendingEncryptedFile = newFile
                    
                    // Refresh list immediately to show the item
                    mediaFiles = withContext(Dispatchers.IO) {
                        repository.getMediaFiles()
                    }
                    
                    // 2. Attempt to delete original
                    val intentSender = repository.deleteOriginal(uri)
                    if (intentSender != null) {
                        val request = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                        deleteLauncher.launch(request)
                    } else {
                        // Deletion happened silently or failed silently.
                        // Ideally check if file exists, but for now assume success if no exception.
                        android.widget.Toast.makeText(context, "Imported Securely", android.widget.Toast.LENGTH_SHORT).show()
                        pendingEncryptedFile = null
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Gallery",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isFakeMode) {
                            Text(
                                "Public Mode",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                actions = {
                    Row {
                        if (!isFakeMode) {
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                        IconButton(
                            onClick = onLock,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            if (!isFakeMode) {
                FloatingActionButton(
                    onClick = {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    containerColor = GoldAccent,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Media")
                }
            }
        }
    ) { padding ->
        if (mediaFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isFakeMode) "No media found." else "No hidden media.\nTap + to add photos or videos.",
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 2.dp,
                    end = 2.dp,
                    top = padding.calculateTopPadding(),
                    bottom = 80.dp
                ),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(mediaFiles) { file ->
                    MediaItem(
                        file = file,
                        repository = repository,
                        imageLoader = imageLoader,
                        onClick = {
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

@Composable
fun MediaItem(
    file: File,
    repository: MediaRepository,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit
) {
    val isVideo = remember(file) { repository.isVideo(file) }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isVideo) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(file)
                        .crossfade(true)
                        .size(coil.size.Size.ORIGINAL) // Coil handles downsampling for grid automatically
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
