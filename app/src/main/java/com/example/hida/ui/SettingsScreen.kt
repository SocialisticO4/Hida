package com.example.hida.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.ripple.rememberRipple
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
                        IconOption("Calculator", "MainActivity", Icons.Default.Calculate),
                        IconOption("Weather", "com.example.hida.WeatherAlias", Icons.Default.WbSunny),
                        IconOption("Notes", "com.example.hida.NotesAlias", Icons.Default.Edit),
                        IconOption("Clock", "com.example.hida.ClockAlias", Icons.Default.AccessTime),
                        IconOption("Music", "com.example.hida.MusicAlias", Icons.Default.MusicNote),
                        IconOption("Calendar", "com.example.hida.CalendarAlias", Icons.Default.CalendarToday),
                        IconOption("Mail", "com.example.hida.MailAlias", Icons.Default.Mail),
                        IconOption("Browser", "com.example.hida.BrowserAlias", Icons.Default.Public),
                        IconOption("Camera", "com.example.hida.CameraAlias", Icons.Default.CameraAlt),
                        IconOption("Maps", "com.example.hida.MapsAlias", Icons.Default.Map),
                        IconOption("Phone", "com.example.hida.PhoneAlias", Icons.Default.Phone),
                        IconOption("Contacts", "com.example.hida.ContactsAlias", Icons.Default.Contacts),
                        IconOption("Messages", "com.example.hida.MessagesAlias", Icons.AutoMirrored.Filled.Message),
                        IconOption("Settings", "com.example.hida.SettingsAlias", Icons.Default.Settings),
                        IconOption("Play Store", "com.example.hida.PlayStoreAlias", Icons.Default.Shop),
                        IconOption("Drive", "com.example.hida.DriveAlias", Icons.Default.Cloud),
                        IconOption("Files", "com.example.hida.FilesAlias", Icons.Default.Folder),
                        IconOption("Radio", "com.example.hida.RadioAlias", Icons.Default.Radio)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(400.dp),
                        userScrollEnabled = false
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
                                    Toast.makeText(context, "Icon: ${iconOption.name}", Toast.LENGTH_SHORT).show()
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
                currentPin = prefs.getPin(),
                onDismiss = { showPinDialog = false },
                onConfirm = { newPin ->
                    prefs.savePin(newPin)
                    showPinDialog = false
                    Toast.makeText(context, "PIN updated", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "Decoy PIN set", Toast.LENGTH_SHORT).show()
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
                indication = rememberRipple(color = MaterialTheme.colorScheme.primary),
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
                Icon(
                    iconOption.icon,
                    contentDescription = iconOption.name,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = iconOption.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
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
    var pin by remember { mutableStateOf(currentPin) }

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
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) pin = it },
                label = { Text("Enter PIN") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (pin.isNotEmpty()) onConfirm(pin) }
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
    val icon: ImageVector
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
