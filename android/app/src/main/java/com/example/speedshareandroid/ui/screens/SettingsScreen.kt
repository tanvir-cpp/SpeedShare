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
    val customDeviceName by viewModel.customDeviceName.collectAsState()
    var editName by remember(customDeviceName) { mutableStateOf(customDeviceName) }
    var isEditingName by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceSlate950)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Device Profile
        Text(
            text = "DEVICE PROFILE",
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceSlate700.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (isEditingName) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Visible Device Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = SurfaceSlate700,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
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
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateDeviceName(editName)
                                isEditingName = false
                                Toast.makeText(context, "Device name updated", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save", color = TextPureWhite, fontWeight = FontWeight.Bold)
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
                                text = "Visible Name",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = customDeviceName,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Broadcasted to other devices on the same network.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        IconButton(onClick = { isEditingName = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = PrimaryIndigoLight
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Storage & Downloads
        Text(
            text = "STORAGE & PROTOCOL",
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceSlate700.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Folder,
                    title = "Target Download Folder",
                    value = "Internal Storage / Download / SpeedShare",
                    accentColor = AccentAmber
                )
                HorizontalDivider(color = SurfaceSlate800, thickness = 0.5.dp)
                SettingsItem(
                    icon = Icons.Default.Bolt,
                    title = "Streaming Protocol",
                    value = "High-Throughput Raw TCP Streaming (2MB Buffers)",
                    accentColor = AccentMint
                )
            }
        }

        // Section 3: Network Diagnostics
        Text(
            text = "NETWORK DIAGNOSTICS",
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceSlate700.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Wifi,
                    title = "Local Network IP",
                    value = viewModel.localIp,
                    accentColor = AccentSky
                )
                HorizontalDivider(color = SurfaceSlate800, thickness = 0.5.dp)
                SettingsItem(
                    icon = Icons.Default.Radar,
                    title = "Discovery Port",
                    value = "UDP 53317 (Beacon Broadcasts)",
                    accentColor = AccentViolet
                )
                HorizontalDivider(color = SurfaceSlate800, thickness = 0.5.dp)
                SettingsItem(
                    icon = Icons.Default.SwapVert,
                    title = "Streaming Port",
                    value = "TCP 53318 (Direct Socket Streaming)",
                    accentColor = AccentMint
                )
            }
        }

        // Section 4: About
        Text(
            text = "ABOUT SPEEDSHARE",
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SurfaceSlate700.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = PrimaryIndigoLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SpeedShare v${viewModel.currentAppVersion}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Native Jetpack Compose & Kotlin",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()

                    Button(
                        onClick = { viewModel.checkForUpdates(isManual = true) },
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigoContainer),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = PrimaryIndigoLight,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(text = "Check", fontSize = 12.sp, color = PrimaryIndigoLight, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val manualUpdateMessage by viewModel.manualUpdateMessage.collectAsState()
                manualUpdateMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        color = AccentMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ultra-fast, zero-cloud peer-to-peer file sharing designed for direct socket transfers across Windows and Android devices on the local network.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.12f)),
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
                color = TextMuted,
                fontSize = 11.sp
            )
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

