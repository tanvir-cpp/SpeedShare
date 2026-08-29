package com.example.speedshareandroid.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.speedshareandroid.network.UpdateInfo
import com.example.speedshareandroid.theme.*

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    downloadProgress: Float?,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = {
        if (downloadProgress == null) onDismiss()
    }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCard),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Update Available!",
                            color = TextPureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = updateInfo.versionTag,
                                color = NeonMint,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = " • Current: v$currentVersion",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "RELEASE NOTES",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Changelog box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgMidnight)
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = updateInfo.changelog,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Download Progress
                if (downloadProgress != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading APK...",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                color = TextPureWhite,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = NeonCyan,
                            trackColor = BgMidnight
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                } else {
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary
                            )
                        ) {
                            Text(text = "Later", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPureWhite
                            )
                        ) {
                            Text(text = "GitHub", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onUpdateNow,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                        ) {
                            Text(text = "Update", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPureWhite)
                        }
                    }
                }
            }
        }
    }
}
