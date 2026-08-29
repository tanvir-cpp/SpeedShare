package com.example.speedshareandroid.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.speedshareandroid.R
import com.example.speedshareandroid.models.DiscoveredPeer
import com.example.speedshareandroid.models.FileItem
import com.example.speedshareandroid.theme.*
import com.example.speedshareandroid.ui.components.QuickCategoryDeck
import com.example.speedshareandroid.ui.components.RadarHero
import com.example.speedshareandroid.ui.components.UpdateDialog
import com.example.speedshareandroid.ui.screens.HistoryScreen
import com.example.speedshareandroid.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedShareScreen(
    viewModel: SpeedShareViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentTab by viewModel.selectedTab.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val selectedPeer by viewModel.selectedPeer.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val incomingRequest by viewModel.incomingRequest.collectAsState()
    val isTransferring by viewModel.isTransferring.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val statusDialog by viewModel.transferStatusDialog.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()

    var activePickerMime by remember { mutableStateOf("*/*") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFilesFromUris(context, uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.speedshare_logo),
                            contentDescription = "SpeedShare Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Speed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = TextPureWhite
                                )
                                Text(
                                    text = "Share",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = NeonCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonIndigo.copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LAN Transfer",
                                        color = NeonMint,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "IP: ${viewModel.discoveryManager.getLocalIp()}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgMidnight
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(BgMidnight)) {
                // Floating Send Action Deck on Share Tab
                if (currentTab == AppTab.SHARE) {
                    AnimatedVisibility(
                        visible = selectedFiles.isNotEmpty(),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                                    )
                                )
                                .border(1.dp, NeonIndigo.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(14.dp, 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val totalBytes = selectedFiles.sumOf { it.size }
                                    Text(
                                        text = "${selectedFiles.size} ${if (selectedFiles.size == 1) "file" else "files"} selected",
                                        color = TextPureWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = FileItem.formatBytes(totalBytes),
                                        color = NeonMint,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = { viewModel.sendFiles() },
                                    enabled = selectedPeer != null && !isTransferring,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedPeer != null) "Send to ${selectedPeer?.deviceName?.take(10)}" else "Choose Device",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Sleek Material 3 Navigation Bar
                NavigationBar(
                    containerColor = BgCard,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(0.5.dp, BorderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.SHARE,
                        onClick = { viewModel.selectTab(AppTab.SHARE) },
                        icon = {
                            Icon(Icons.Default.Share, contentDescription = "Transfer")
                        },
                        label = { Text("Transfer", fontWeight = if (currentTab == AppTab.SHARE) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = NeonCyan
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.HISTORY,
                        onClick = { viewModel.selectTab(AppTab.HISTORY) },
                        icon = {
                            Icon(Icons.Default.List, contentDescription = "History")
                        },
                        label = { Text("History", fontWeight = if (currentTab == AppTab.HISTORY) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = NeonCyan
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                        icon = {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        },
                        label = { Text("Settings", fontWeight = if (currentTab == AppTab.SETTINGS) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = NeonCyan
                        )
                    )
                }
            }
        },
        containerColor = BgMidnight
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.SHARE -> {
                    ShareTabContent(
                        localDeviceName = viewModel.customDeviceName.collectAsState().value,
                        localIp = viewModel.discoveryManager.getLocalIp(),
                        peers = peers,
                        selectedPeer = selectedPeer,
                        selectedFiles = selectedFiles,
                        onRefresh = { viewModel.refreshDiscovery() },
                        onSelectPeer = { p -> if (selectedPeer?.deviceId == p.deviceId) viewModel.clearSelectedPeer() else viewModel.selectPeer(p) },
                        onPickCategory = { mime ->
                            activePickerMime = mime
                            filePickerLauncher.launch(if (mime == "*/*") arrayOf("*/*") else arrayOf(mime))
                        },
                        onRemoveFile = { f -> viewModel.removeFile(f) },
                        onClearFiles = { viewModel.clearFiles() }
                    )
                }
                AppTab.HISTORY -> {
                    HistoryScreen(viewModel = viewModel)
                }
                AppTab.SETTINGS -> {
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Modal 1: Incoming Transfer Request Dialog
    incomingRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.declineIncoming() },
            containerColor = BgCard,
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonIndigo.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Incoming Transfer",
                        color = TextPureWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "${req.senderDevice} (${req.senderIp}) wants to send ${req.files.size} ${if (req.files.size == 1) "file" else "files"}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Total Payload: ${FileItem.formatBytes(req.totalSize)}",
                        color = NeonMint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BgMidnight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            items(req.files) { f ->
                                Text(
                                    text = "• ${f.name} (${f.formattedSize})",
                                    color = TextPureWhite,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptIncoming() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMint),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Accept & Receive", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declineIncoming() }) {
                    Text("Decline", color = NeonRose)
                }
            }
        )
    }

    // Modal 2: Active High-Speed Transfer Dialog
    if (isTransferring) {
        AlertDialog(
            onDismissRequest = { /* prevent dismiss */ },
            containerColor = BgCard,
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transferring Stream",
                        color = TextPureWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    transferProgress?.let { p ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonIndigo.copy(alpha = 0.3f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = p.formattedSpeed,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            text = {
                Column {
                    val progress = transferProgress
                    val percent = (progress?.percentage ?: 0f) / 100f

                    // Large Speed Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = progress?.formattedSpeed ?: "0.0 MB/s",
                            color = NeonCyan,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = progress?.formattedBitrate ?: "0 Mbps",
                            color = TextMuted,
                            fontSize = 13.sp,
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
                        color = NeonCyan,
                        trackColor = BgMidnight,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (progress != null) "${FileItem.formatBytes(progress.transferredBytes)} / ${FileItem.formatBytes(progress.totalBytes)} (${progress.percentage.toInt()}%)" else "Connecting...",
                            color = TextPureWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = progress?.formattedEta ?: "Calculating...",
                            color = NeonMint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progress?.currentFileName ?: "Preparing sockets...",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelTransfer() }) {
                    Text("Cancel Transfer", color = NeonRose)
                }
            }
        )
    }

    // Modal 3: Transfer Status Result Dialog
    statusDialog?.let { (success, message) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissStatusDialog() },
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (success) NeonMint else NeonRose
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (success) "Transfer Completed" else "Transfer Notice",
                        color = TextPureWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = message ?: (if (success) "Files processed successfully and recorded in History." else "Transfer ended."),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissStatusDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("OK", color = TextPureWhite, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal 4: Auto-Update Dialog
    updateInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            currentVersion = viewModel.currentAppVersion,
            downloadProgress = updateDownloadProgress,
            onDismiss = { viewModel.dismissUpdateDialog() },
            onUpdateNow = { viewModel.startUpdateDownload() }
        )
    }
}

@Composable
fun ShareTabContent(
    localDeviceName: String,
    localIp: String,
    peers: List<DiscoveredPeer>,
    selectedPeer: DiscoveredPeer?,
    selectedFiles: List<FileItem>,
    onRefresh: () -> Unit,
    onSelectPeer: (DiscoveredPeer) -> Unit,
    onPickCategory: (String) -> Unit,
    onRemoveFile: (FileItem) -> Unit,
    onClearFiles: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero: Animated Radar Scanner
        item {
            RadarHero(
                localDeviceName = localDeviceName,
                localIp = localIp,
                deviceCount = peers.size,
                onRefresh = onRefresh
            )
        }

        // Section 1: Discovered Devices
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISCOVERED PEERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                if (peers.isNotEmpty()) {
                    Text(
                        text = "Tap to select",
                        fontSize = 11.sp,
                        color = NeonCyan
                    )
                }
            }
        }

        if (peers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Waiting for other devices...",
                            color = TextPureWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Open SpeedShare on your Windows PC or another phone on the same Wi-Fi.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(peers) { peer ->
                val isSelected = selectedPeer?.deviceId == peer.deviceId
                DeviceCard(
                    peer = peer,
                    isSelected = isSelected,
                    onClick = { onSelectPeer(peer) }
                )
            }
        }

        // Section 2: Quick Pick Categories
        item {
            Text(
                text = "SELECT FILES TO SHARE",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = TextMuted,
                letterSpacing = 1.sp
            )
        }

        item {
            QuickCategoryDeck(onPickCategory = onPickCategory)
        }

        // Section 3: Selected Files Queue
        if (selectedFiles.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUEUE (${selectedFiles.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )

                    TextButton(onClick = onClearFiles) {
                        Text(
                            text = "Clear All",
                            color = NeonRose,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(selectedFiles) { file ->
                FileItemRow(
                    file = file,
                    onRemove = { onRemoveFile(file) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun DeviceCard(
    peer: DiscoveredPeer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E1B4B) else BgCard
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                1.5.dp,
                if (isSelected) NeonCyan else BorderGlass,
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) NeonIndigo.copy(alpha = 0.3f) else BgCardHover
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (peer.isWindows) Icons.Default.Home else Icons.Default.Phone,
                    contentDescription = null,
                    tint = if (peer.isWindows) NeonSky else NeonMint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    color = TextPureWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${peer.ipAddress} • ${peer.displayBadge}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) NeonCyan else BgCardHover)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isSelected) "SELECTED" else "READY",
                    color = if (isSelected) Color.Black else NeonSky,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FileItemRow(
    file: FileItem,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonIndigo.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = file.fileCategory,
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = TextPureWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.formattedSize,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
