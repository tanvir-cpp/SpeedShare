package com.example.speedshareandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedshareandroid.models.TransferDirection
import com.example.speedshareandroid.models.TransferRecord
import com.example.speedshareandroid.models.TransferStatus
import com.example.speedshareandroid.theme.*

@Composable
fun HistoryItemCard(
    record: TransferRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val isSent = record.direction == TransferDirection.SENT
    val directionColor = if (isSent) AccentSky else AccentGreen
    val categoryIcon = getCategoryIcon(record.fileCategory)

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (record.status == TransferStatus.COMPLETED && record.filePath != null) {
                    onOpen()
                }
            }
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: File icon + File Name + More Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardDarkHover),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & Size
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.formattedSize,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        if (record.formattedSpeed.isNotEmpty()) {
                            Text(
                                text = " • ${record.formattedSpeed}",
                                color = AccentSky,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Action Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(CardDark)
                    ) {
                        if (record.status == TransferStatus.COMPLETED && record.filePath != null) {
                            DropdownMenuItem(
                                text = { Text("Open File", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = AccentCyan
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpen()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null,
                                        tint = AccentSky
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove from History", color = AccentRed) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = AccentRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Direction, Peer Name, Timestamp, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction & Peer Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(directionColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Default.ArrowForward else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = directionColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSent) "To ${record.peerName}" else "From ${record.peerName}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Badge & Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.formattedDateTime,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    val (statusBg, statusFg, statusText) = when (record.status) {
                        TransferStatus.COMPLETED -> Triple(AccentGreen.copy(alpha = 0.15f), AccentGreen, "Completed")
                        TransferStatus.FAILED -> Triple(AccentRed.copy(alpha = 0.15f), AccentRed, "Failed")
                        TransferStatus.CANCELLED -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "Cancelled")
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusFg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "VIDEO" -> Icons.Default.PlayArrow
        "IMAGE" -> Icons.Default.Face
        "AUDIO" -> Icons.Default.Notifications
        "ARCHIVE" -> Icons.Default.ShoppingCart
        "DOCUMENT" -> Icons.Default.Edit
        "APP" -> Icons.Default.Phone
        "CODE" -> Icons.Default.Info
        else -> Icons.Default.Menu
    }
}
