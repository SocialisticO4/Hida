package com.example.hida.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hida.data.PreferencesManager
import com.example.hida.ui.theme.DarkBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showFakePinDialog by remember { mutableStateOf(false) }
    var currentIcon by remember { mutableStateOf(prefs.getIconAlias()) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Security Section
            Text("Security", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            
            SettingsItem(
                title = "Change Real PIN",
                subtitle = "Update your main access code",
                icon = Icons.Default.Lock,
                onClick = { showPinDialog = true }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsItem(
                title = "Set Fake PIN",
                subtitle = "Duress code for empty gallery",
                icon = Icons.Default.Warning,
                onClick = { showFakePinDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Icon Section
            Text(
                "Disguise Icon",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
                columns = GridCells.Adaptive(minSize = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(icons) { option ->
                    IconGridItem(
                        option = option,
                        isSelected = currentIcon == option.alias,
                        onSelect = {
                            setIcon(context, option.alias)
                            prefs.saveIconAlias(option.alias)
                            currentIcon = option.alias
                            Toast.makeText(context, "Icon changed to ${option.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        ChangePinDialog(
            title = "Set Real PIN",
            onDismiss = { showPinDialog = false },
            onConfirm = { newPin ->
                prefs.savePin(newPin)
                showPinDialog = false
                Toast.makeText(context, "Real PIN Updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showFakePinDialog) {
        ChangePinDialog(
            title = "Set Fake PIN",
            onDismiss = { showFakePinDialog = false },
            onConfirm = { newPin ->
                prefs.saveFakePin(newPin)
                showFakePinDialog = false
                Toast.makeText(context, "Fake PIN Updated", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

data class IconOption(val name: String, val alias: String, val icon: ImageVector)

@Composable
fun SettingsItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
fun IconGridItem(option: IconOption, isSelected: Boolean, onSelect: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF3A3A3C) else Color(0xFF1C1C1E))
            .clickable { onSelect() }
            .padding(12.dp)
    ) {
        Icon(
            option.icon, 
            null, 
            tint = if (isSelected) Color(0xFFFF9F0A) else Color.White, 
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            option.name, 
            color = if (isSelected) Color.White else Color.Gray, 
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
fun ChangePinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) text = it },
                label = { Text("Enter 4 digits") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.length == 4) onConfirm(text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun setIcon(context: Context, activeAlias: String) {
    val pm = context.packageManager
    val packageName = context.packageName
    
    val aliases = listOf(
        "MainActivity",
        "com.example.hida.WeatherAlias",
        "com.example.hida.NotesAlias",
        "com.example.hida.ClockAlias",
        "com.example.hida.MusicAlias",
        "com.example.hida.CalendarAlias",
        "com.example.hida.MailAlias",
        "com.example.hida.BrowserAlias",
        "com.example.hida.CameraAlias",
        "com.example.hida.MapsAlias",
        "com.example.hida.PhoneAlias",
        "com.example.hida.ContactsAlias",
        "com.example.hida.MessagesAlias",
        "com.example.hida.SettingsAlias",
        "com.example.hida.PlayStoreAlias",
        "com.example.hida.DriveAlias",
        "com.example.hida.FilesAlias",
        "com.example.hida.RadioAlias"
    )

    aliases.forEach { alias ->
        val state = if (alias == activeAlias) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        
        val componentName = ComponentName(packageName, if (alias == "MainActivity") "$packageName.MainActivity" else alias)
        
        try {
            pm.setComponentEnabledSetting(
                componentName,
                state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
