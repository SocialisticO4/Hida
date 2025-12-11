package com.example.hida.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.hida.R
import androidx.compose.foundation.Image

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { PreferencesManager(context) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showFakePinDialog by remember { mutableStateOf(false) }
    var currentIcon by remember { mutableStateOf(prefs.getIconAlias()) }

    HidaTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.displaySmall
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = md3_dark_surfaceContainer
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Security Section
                item {
                    Text(
                        "Security",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                    )
                }
                
                item {
                    MD3SettingsCard(
                        title = "Access PIN",
                        subtitle = "Change your vault access code",
                        icon = Icons.Default.Lock,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showPinDialog = true
                        }
                    )
                }
                
                item {
                    MD3SettingsCard(
                        title = "Decoy PIN",
                        subtitle = "Set a fake PIN that shows empty vault",
                        icon = Icons.Default.VisibilityOff,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showFakePinDialog = true
                        }
                    )
                }

                // Auto-Lock Timeout
                item {
                    var expanded by remember { mutableStateOf(false) }
                    var currentTimeout by remember { mutableStateOf(prefs.getSessionTimeout()) }
                    val timeoutOptions = PreferencesManager.AutoLockTimeout.entries
                    val currentLabel = timeoutOptions.find { it.ms == currentTimeout }?.label ?: "1 minute"
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = HidaShapes.large,
                        colors = CardDefaults.cardColors(containerColor = md3_dark_surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Lock",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Lock vault when app is in background",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Box {
                                TextButton(onClick = { expanded = true }) {
                                    Text(currentLabel, color = MaterialTheme.colorScheme.primary)
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    containerColor = md3_dark_surfaceContainerHigh
                                ) {
                                    timeoutOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    option.label,
                                                    color = if (option.ms == currentTimeout) 
                                                        MaterialTheme.colorScheme.primary 
                                                    else MaterialTheme.colorScheme.onSurface
                                                ) 
                                            },
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                prefs.setSessionTimeout(option.ms)
                                                currentTimeout = option.ms
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Appearance Section
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                    )
                }

                item {
                    Text(
                        "App Icon",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }

                // Icon Grid
                item {
                    val icons = listOf(
                        IconOption("Hida", "MainActivity", R.drawable.lock_6065983),
                        IconOption("Calculator", "com.example.hida.CalculatorAlias", R.drawable.ic_calculator_launcher),
                        IconOption("Weather", "com.example.hida.WeatherAlias", R.drawable.ic_weather_launcher),
                        IconOption("Keep Notes", "com.example.hida.NotesAlias", R.drawable.ic_notes_launcher),
                        IconOption("Clock", "com.example.hida.ClockAlias", R.drawable.ic_clock_launcher),
                        IconOption("YT Music", "com.example.hida.MusicAlias", R.drawable.ic_music_launcher),
                        IconOption("Calendar", "com.example.hida.CalendarAlias", R.drawable.ic_calendar_launcher),
                        IconOption("Gmail", "com.example.hida.MailAlias", R.drawable.ic_mail_launcher),
                        IconOption("Chrome", "com.example.hida.BrowserAlias", R.drawable.ic_browser_launcher),
                        IconOption("Camera", "com.example.hida.CameraAlias", R.drawable.ic_camera_launcher),
                        IconOption("Maps", "com.example.hida.MapsAlias", R.drawable.ic_maps_launcher),
                        IconOption("Phone", "com.example.hida.PhoneAlias", R.drawable.ic_phone_launcher),
                        IconOption("Contacts", "com.example.hida.ContactsAlias", R.drawable.ic_contacts_launcher),
                        IconOption("Messages", "com.example.hida.MessagesAlias", R.drawable.ic_messages_launcher),
                        IconOption("Play Store", "com.example.hida.PlayStoreAlias", R.drawable.ic_playstore_launcher),
                        IconOption("Drive", "com.example.hida.DriveAlias", R.drawable.ic_drive_launcher),
                        IconOption("Files", "com.example.hida.FilesAlias", R.drawable.ic_files_launcher)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.height(520.dp),
                        userScrollEnabled = true
                    ) {
                        items(icons) { iconOption ->
                            MD3IconSelector(
                                iconOption = iconOption,
                                isSelected = currentIcon == iconOption.componentName ||
                                        (currentIcon == "MainActivity" && iconOption.componentName == "MainActivity"),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    switchIcon(context, currentIcon, iconOption.componentName)
                                    prefs.saveIconAlias(iconOption.componentName)
                                    currentIcon = iconOption.componentName
                                    // Icon changed silently
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // PIN Dialog
        if (showPinDialog) {
            MD3PinDialog(
                title = "Change Access PIN",
                currentPin = prefs.getPin() ?: "",
                onDismiss = { showPinDialog = false },
                onConfirm = { newPin ->
                    prefs.savePin(newPin)
                    showPinDialog = false
                    // PIN updated silently
                }
            )
        }

        // Fake PIN Dialog
        if (showFakePinDialog) {
            MD3PinDialog(
                title = "Set Decoy PIN",
                currentPin = prefs.getFakePin(),
                onDismiss = { showFakePinDialog = false },
                onConfirm = { newPin ->
                    prefs.saveFakePin(newPin)
                    showFakePinDialog = false
                    // Decoy PIN set silently
                }
            )
        }
    }
}

@Composable
fun MD3SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = HidaShapes.large,
        colors = CardDefaults.cardColors(
            containerColor = md3_dark_surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MD3IconSelector(
    iconOption: IconOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = HidaShapes.medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else md3_dark_surfaceContainerHigh,
            tonalElevation = if (isSelected) 6.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(id = iconOption.iconRes),
                    contentDescription = iconOption.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(HidaShapes.small),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = iconOption.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MD3PinDialog(
    title: String,
    currentPin: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }  // Start empty, don't prefill
    var showPin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = md3_dark_surfaceContainerHigh,
        shape = HidaShapes.extraLarge,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { 
                        if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                            pin = it
                            error = null
                        }
                    },
                    label = { Text("New PIN") },
                    placeholder = { Text("Enter 4-10 digits") },
                    visualTransformation = if (showPin) androidx.compose.ui.text.input.VisualTransformation.None 
                                          else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(
                                if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPin) "Hide PIN" else "Show PIN",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (pin.length < 4) {
                        error = "PIN must be at least 4 digits"
                    } else {
                        onConfirm(pin) 
                    }
                }
            ) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class IconOption(
    val name: String,
    val componentName: String,
    val iconRes: Int  // Drawable resource ID (R.drawable.*)
)

private fun switchIcon(context: Context, currentAlias: String, newAlias: String) {
    val pm = context.packageManager
    
    // Disable current
    val currentComponent = if (currentAlias == "MainActivity") {
        ComponentName(context, "com.example.hida.MainActivity")
    } else {
        ComponentName(context, currentAlias)
    }
    
    pm.setComponentEnabledSetting(
        currentComponent,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
    )
    
    // Enable new
    val newComponent = if (newAlias == "MainActivity") {
        ComponentName(context, "com.example.hida.MainActivity")
    } else {
        ComponentName(context, newAlias)
    }
    
    pm.setComponentEnabledSetting(
        newComponent,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
}
