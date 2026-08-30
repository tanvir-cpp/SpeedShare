package com.example.speedshareandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    val isSent = record.direction == TransferDirection.SENT
    val directionColor = if (isSent) AccentSky else AccentMint
    val (categoryIcon, categoryColor) = getCategoryMeta(record.fileCategory)

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceSlate900),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (record.status == TransferStatus.COMPLETED && record.filePath != null) {
                    onOpen()
                }
            }
            .border(1.dp, SurfaceSlate700.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Information
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Direction Tag
                    Text(
                        text = if (isSent) "To ${record.peerName}" else "From ${record.peerName}",
                        color = directionColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "•",
                        color = TextMuted,
                        fontSize = 10.sp
                    )

                    Text(
                        text = record.formattedSize,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    if (record.formattedSpeed.isNotEmpty()) {
                        Text(
                            text = "•",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = record.formattedSpeed,
                            color = StatusSuccess,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Date & Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = record.formattedDateTime,
                        color = TextMuted,
                        fontSize = 10.sp
                    )

                    if (record.status != TransferStatus.COMPLETED) {
                        val statusColor = if (record.status == TransferStatus.FAILED) StatusError else StatusWarning
                        val statusText = if (record.status == TransferStatus.FAILED) "Failed" else "Cancelled"
                        Text(
                            text = "•",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.status == TransferStatus.COMPLETED && record.filePath != null) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun getCategoryMeta(category: String): Pair<ImageVector, Color> {
    return when (category) {
        "VIDEO" -> Pair(Icons.Default.VideoLibrary, AccentViolet)
        "IMAGE" -> Pair(Icons.Default.Image, AccentSky)
        "AUDIO" -> Pair(Icons.Default.Audiotrack, AccentMint)
        "ARCHIVE" -> Pair(Icons.Default.FolderZip, AccentAmber)
        "DOCUMENT" -> Pair(Icons.Default.Description, AccentSky)
        "APP" -> Pair(Icons.Default.Android, PrimaryIndigo)
        "CODE" -> Pair(Icons.Default.Code, AccentMint)
        else -> Pair(Icons.Default.Description, TextSecondary)
    }
}



