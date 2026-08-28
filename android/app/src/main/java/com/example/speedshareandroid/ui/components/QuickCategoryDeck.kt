package com.example.speedshareandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedshareandroid.theme.*

@Composable
fun QuickCategoryDeck(
    onPickCategory: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryTile(
                title = "Photos",
                subtitle = "JPG, PNG, RAW",
                icon = Icons.Default.Face,
                accentColor = NeonCyan,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("image/*") }
            )
            CategoryTile(
                title = "Videos",
                subtitle = "MP4, MKV, 4K",
                icon = Icons.Default.PlayArrow,
                accentColor = NeonViolet,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("video/*") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryTile(
                title = "Music / Audio",
                subtitle = "MP3, FLAC, WAV",
                icon = Icons.Default.Notifications,
                accentColor = NeonMint,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("audio/*") }
            )
            CategoryTile(
                title = "All Files",
                subtitle = "Docs, ZIP, APK",
                icon = Icons.Default.Add,
                accentColor = NeonIndigo,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("*/*") }
            )
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.25f), BgCardHover)
                        )
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = TextPureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
