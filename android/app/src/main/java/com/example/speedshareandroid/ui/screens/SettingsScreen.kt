package com.example.speedshareandroid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.speedshareandroid.theme.*
import com.example.speedshareandroid.ui.SpeedShareViewModel

@Composable
fun SettingsScreen(
    viewModel: SpeedShareViewModel
) {
    val context = LocalContext.current
    val colors = LocalSpeedShareColors.current
    val scheme = MaterialTheme.colorScheme
    val customDeviceName by viewModel.customDeviceName.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    var editName by remember(customDeviceName) { mutableStateOf(customDeviceName) }
    var isEditingName by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Appearance
        SectionHeader("Appearance")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.BrightnessMedium,
                    title = "Theme",
                    value = "Applies across SpeedShare",
                    accentColor = scheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Segmented control: System / Light / Dark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = colors.surfaceRaised,
                                selectedContainerColor = scheme.primaryContainer,
                                labelColor = colors.textSecondary,
                                selectedLabelColor = scheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = colors.border,
                                selectedBorderColor = scheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Section: Device profile
        SectionHeader("Device")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (isEditingName) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Visible device name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = scheme.primary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            editName = customDeviceName
                            isEditingName = false
                        }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateDeviceName(editName)
                                isEditingName = false
                                Toast.makeText(context, "Device name updated", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save", color = scheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Visible name",
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = customDeviceName,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Broadcast to other devices on the same network.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        IconButton(onClick = { isEditingName = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit name",
                                tint = scheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Section: Storage & protocol
        SectionHeader("Storage & protocol")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Folder,
                    title = "Downloads folder",
                    value = "Internal storage / Download / SpeedShare",
                    accentColor = colors.warning
                )
                HorizontalDivider(color = colors.border, thickness = 0.5.dp)
                SettingsItem(
                    icon = Icons.Default.Bolt,
                    title = "Streaming protocol",
                    value = "Direct raw TCP, no cloud relay",
                    accentColor = colors.success
                )
            }
        }

        // Section: Network
        SectionHeader("Network")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Wifi,
                    title = "Local IP",
                    value = viewModel.localIp,
                    accentColor = colors.info
                )
                HorizontalDivider(color = colors.border, thickness = 0.5.dp)
                SettingsItem(
                    icon = Icons.Default.Radar,
                    title = "Discovery",
                    value = "UDP 53317 — beacon broadcast",
                    accentColor = colors.warning
                )
                HorizontalDivider(color = colors.border, thickness = 0.5.dp)
                SettingsItem(
                    icon = Icons.Default.SwapVert,
                    title = "Streaming",
                    value = "TCP 53318 — direct socket",
                    accentColor = colors.success
                )
            }
        }

        // Section: Updates
        SectionHeader("Updates")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SpeedShare v${viewModel.currentAppVersion}",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Signed and hash-verified updates",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.checkForUpdates(isManual = true) },
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primaryContainer,
                            contentColor = scheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = scheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "Check", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val manualUpdateMessage by viewModel.manualUpdateMessage.collectAsState()
                manualUpdateMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = if (msg.contains("up to date", ignoreCase = true)) colors.success else colors.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Section: About
        SectionHeader("About")
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(scheme.primary, colors.info)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Native Jetpack Compose",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "SpeedShare for Android • v${viewModel.currentAppVersion}",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ultra-fast, zero-cloud, peer-to-peer file sharing over your local network. Files never leave your Wi-Fi.",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalSpeedShareColors.current
    Text(
        text = text,
        color = colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = LocalSpeedShareColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: Color
) {
    val colors = LocalSpeedShareColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = colors.textMuted,
                fontSize = 11.sp
            )
            Text(
                text = value,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
