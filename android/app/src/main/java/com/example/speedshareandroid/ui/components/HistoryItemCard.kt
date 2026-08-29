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
import androidx.compose.ui.graphics.Brush
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
    val isSent = record.direction == TransferDirection.SENT
    val directionColor = if (isSent) NeonSky else NeonMint
    val (categoryIcon, categoryColor) = getCategoryMeta(record.fileCategory)

    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (record.status == TransferStatus.COMPLETED && record.filePath != null) {
                    onOpen()
                }
            }
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Category Icon + File Details + Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category Icon with glow
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(categoryColor.copy(alpha = 0.2f), BgCardElevated)
                            )
                        )
                        .border(1.dp, categoryColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & Size
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        color = TextPureWhite,
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (record.formattedSpeed.isNotEmpty()) {
                            Text(
                                text = " • ${record.formattedSpeed}",
                                color = NeonMint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick Action Icons: Share & Delete
                if (record.status == TransferStatus.COMPLETED && record.filePath != null) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = NeonSky,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = BorderGlass, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Direction Pill, Peer Name, Timestamp, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction & Peer Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(directionColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSent) "SENT" else "RCVD",
                            color = directionColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
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
                        TransferStatus.COMPLETED -> Triple(NeonEmerald.copy(alpha = 0.15f), NeonEmerald, "Success")
                        TransferStatus.FAILED -> Triple(NeonRose.copy(alpha = 0.15f), NeonRose, "Failed")
                        TransferStatus.CANCELLED -> Triple(NeonAmber.copy(alpha = 0.15f), NeonAmber, "Cancelled")
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

private fun getCategoryMeta(category: String): Pair<ImageVector, Color> {
    return when (category) {
        "VIDEO" -> Pair(Icons.Default.PlayArrow, NeonViolet)
        "IMAGE" -> Pair(Icons.Default.AccountBox, NeonCyan)
        "AUDIO" -> Pair(Icons.Default.Star, NeonMint)
        "ARCHIVE" -> Pair(Icons.Default.Build, NeonAmber)
        "DOCUMENT" -> Pair(Icons.Default.Edit, NeonSky)
        "APP" -> Pair(Icons.Default.Phone, NeonIndigo)
        "CODE" -> Pair(Icons.Default.Build, NeonEmerald)
        else -> Pair(Icons.Default.List, TextSecondary)
    }
}
