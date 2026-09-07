package com.example.speedshareandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedshareandroid.models.FileItem
import com.example.speedshareandroid.models.TransferProgress
import com.example.speedshareandroid.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    progress: TransferProgress?,
    inProgress: Boolean,
    resultSuccess: Boolean?,
    resultMessage: String?,
    isReceiving: Boolean,
    onCancel: () -> Unit,
    onDismissResult: () -> Unit
) {
    val colors = LocalSpeedShareColors.current
    val scheme = MaterialTheme.colorScheme
    val percent = ((progress?.percentage ?: 0f) / 100f).coerceIn(0f, 1f)

    // Determine the finished state for inline result rendering
    val finished = !inProgress && resultSuccess != null

    ModalBottomSheet(
        onDismissRequest = {
            if (!inProgress && resultSuccess != null) onDismissResult()
        },
        containerColor = scheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Title + live speed pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        finished && resultSuccess == true -> "Transfer complete"
                        finished && resultSuccess == false && (resultMessage?.contains("cancelled", true) == true) -> "Transfer cancelled"
                        finished -> "Transfer failed"
                        else -> "Transferring…"
                    },
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (inProgress && progress != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.infoContainer
                    ) {
                        Text(
                            text = progress.formattedSpeed,
                            color = colors.onInfoContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (inProgress) {
                // --- In-progress HUD ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = progress?.formattedSpeed ?: "Connecting…",
                        color = colors.info,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = progress?.formattedBitrate ?: "",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = progress?.formattedEta ?: "Estimating…",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = colors.info,
                    trackColor = colors.surfaceRaised
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (progress != null)
                            "${FileItem.formatBytes(progress.transferredBytes)} / ${FileItem.formatBytes(progress.totalBytes)} (${progress.percentage.toInt()}%)"
                        else "Waiting for peer…",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = progress?.currentFileName ?: if (isReceiving) "Preparing to receive…" else "Waiting for recipient to accept…",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong)
                ) {
                    Text("Cancel transfer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            } else {
                // --- Inline result ---
                val success = resultSuccess == true
                val accent = if (success) colors.success else if (resultMessage?.contains("cancelled", true) == true) colors.warning else colors.error
                val icon = if (success) Icons.Default.CheckCircle else Icons.Default.Warning
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when {
                            success && isReceiving -> "Files saved"
                            success -> "All files sent"
                            resultMessage?.contains("cancelled", true) == true -> "Transfer cancelled"
                            else -> "Transfer failed"
                        },
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = resultMessage
                            ?: (if (success) (if (isReceiving) "Files are ready in Downloads/SpeedShare." else "The recipient received all files.") else "The transfer could not be completed."),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = onDismissResult,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = colors.brandGradients.primary,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = scheme.onPrimary,
                            disabledContainerColor = colors.surfaceRaised,
                            disabledContentColor = colors.textDisabled
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = true
                    ) {
                        if (success) {
                            Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Text("Close", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
