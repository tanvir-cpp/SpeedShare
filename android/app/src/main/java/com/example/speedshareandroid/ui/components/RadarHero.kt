package com.example.speedshareandroid.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedshareandroid.theme.*

@Composable
fun RadarHero(
    localDeviceName: String,
    localIp: String,
    deviceCount: Int,
    onRefresh: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_waves")

    val wave1Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1_scale"
    )
    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1_alpha"
    )

    val wave2Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2_scale"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardGlowGradient)
            .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
            .padding(vertical = 20.dp, horizontal = 18.dp)
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(NeonIndigo.copy(alpha = 0.18f), Color.Transparent)))
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Status + Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(NeonMint)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (deviceCount > 0) "$deviceCount Online on LAN" else "Broadcasting & Scanning",
                        color = if (deviceCount > 0) NeonMint else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BgCardHover)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan LAN",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Radar Core with Animated Waves
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Wave 1
                Box(
                    modifier = Modifier
                        .size((90 * wave1Scale).dp)
                        .clip(CircleShape)
                        .border(1.5.dp, NeonCyan.copy(alpha = wave1Alpha), CircleShape)
                )
                // Wave 2
                Box(
                    modifier = Modifier
                        .size((90 * wave2Scale).dp)
                        .clip(CircleShape)
                        .border(1.5.dp, NeonIndigo.copy(alpha = wave2Alpha), CircleShape)
                )

                // Center Hub Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(NeonIndigo, NeonViolet)
                            )
                        )
                        .border(2.dp, NeonCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = TextPureWhite,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Device Name & IP
            Text(
                text = localDeviceName,
                color = TextPureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Wi-Fi IP: $localIp • 10 Gbps Ready",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}
