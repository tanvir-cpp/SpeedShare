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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedshareandroid.theme.*

@Composable
fun QuickCategoryDeck(
    onPickCategory: (String) -> Unit,
    onPickFolder: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Photos & Videos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryTile(
                title = "Photos",
                subtitle = "JPG, PNG, RAW",
                icon = Icons.Default.Image,
                accentColor = AccentSky,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("image/*") }
            )
            CategoryTile(
                title = "Videos",
                subtitle = "MP4, MKV, 4K",
                icon = Icons.Default.VideoLibrary,
                accentColor = AccentViolet,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("video/*") }
            )
        }

        // Row 2: Audio & All Files
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryTile(
                title = "Audio",
                subtitle = "MP3, FLAC, WAV",
                icon = Icons.Default.Audiotrack,
                accentColor = AccentMint,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("audio/*") }
            )
            CategoryTile(
                title = "All Files",
                subtitle = "Docs, ZIP, APK",
                icon = Icons.Default.Description,
                accentColor = PrimaryIndigo,
                modifier = Modifier.weight(1f),
                onClick = { onPickCategory("*/*") }
            )
        }

        // Row 3: Entire Folder
        CategoryTile(
            title = "Folder / Directory",
            subtitle = "Preserve subfolders & batch transfer",
            icon = Icons.Default.FolderOpen,
            accentColor = AccentAmber,
            modifier = Modifier.fillMaxWidth(),
            onClick = onPickFolder
        )
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
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSlate900)
            .border(1.dp, SurfaceSlate700.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

