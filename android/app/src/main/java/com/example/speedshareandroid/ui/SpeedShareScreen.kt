package com.example.speedshareandroid.ui

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore if not allowed
            }
            viewModel.addFilesFromTreeUri(context, treeUri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.speedshare_logo),
                            contentDescription = "SpeedShare Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SpeedShare",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = PrimaryIndigo.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "LAN P2P",
                                        color = PrimaryIndigoLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "IP: ${viewModel.localIp}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceSlate950
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(SurfaceSlate950)) {
                // Floating Send Action Deck on Share Tab
                if (currentTab == AppTab.SHARE) {
                    AnimatedVisibility(
                        visible = selectedFiles.isNotEmpty(),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceSlate900,
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(PrimaryIndigo.copy(alpha = 0.5f))
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp, 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val totalBytes = selectedFiles.sumOf { it.size }
                                    Text(
                                        text = "${selectedFiles.size} ${if (selectedFiles.size == 1) "file" else "files"} selected",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = FileItem.formatBytes(totalBytes),
                                        color = AccentMint,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Button(
                                    onClick = { viewModel.sendFiles() },
                                    enabled = selectedPeer != null && !isTransferring,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryIndigo,
                                        contentColor = TextPureWhite,
                                        disabledContainerColor = SurfaceSlate800,
                                        disabledContentColor = TextDisabled
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedPeer != null) "Send to ${selectedPeer?.deviceName?.take(12)}" else "Select Peer",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Material 3 Navigation Bar
                NavigationBar(
                    containerColor = SurfaceSlate900,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(0.5.dp, SurfaceSlate700.copy(alpha = 0.5f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.SHARE,
                        onClick = { viewModel.selectTab(AppTab.SHARE) },
                        icon = {
                            Icon(Icons.Default.Share, contentDescription = "Transfer")
                        },
                        label = { Text("Transfer") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryIndigoLight,
                            selectedTextColor = PrimaryIndigoLight,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = PrimaryIndigoContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.HISTORY,
                        onClick = { viewModel.selectTab(AppTab.HISTORY) },
                        icon = {
                            Icon(Icons.Default.History, contentDescription = "History")
                        },
                        label = { Text("History") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryIndigoLight,
                            selectedTextColor = PrimaryIndigoLight,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = PrimaryIndigoContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                        icon = {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryIndigoLight,
                            selectedTextColor = PrimaryIndigoLight,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = PrimaryIndigoContainer
                        )
                    )
                }
            }
        },
        containerColor = SurfaceSlate950
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
                        localIp = viewModel.localIp,
                        peers = peers,
                        selectedPeer = selectedPeer,
                        selectedFiles = selectedFiles,
                        onRefresh = { viewModel.refreshDiscovery() },
                        onSelectPeer = { p -> if (selectedPeer?.deviceId == p.deviceId) viewModel.clearSelectedPeer() else viewModel.selectPeer(p) },
                        onPickCategory = { mime ->
                            activePickerMime = mime
                            filePickerLauncher.launch(if (mime == "*/*") arrayOf("*/*") else arrayOf(mime))
                        },
                        onPickFolder = { folderPickerLauncher.launch(null) },
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
            containerColor = SurfaceSlate900,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PrimaryIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = PrimaryIndigoLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Incoming Transfer",
                        color = TextPrimary,
                        fontSize = 17.sp,
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total Payload: ${FileItem.formatBytes(req.totalSize)}",
                        color = AccentMint,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceSlate950),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            items(req.files) { f ->
                                Text(
                                    text = "• ${f.name} (${f.formattedSize})",
                                    color = TextPrimary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Accept & Receive", color = TextPureWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declineIncoming() }) {
                    Text("Decline", color = StatusError)
                }
            }
        )
    }

    // Modal 2: Active High-Speed Transfer Dialog
    if (isTransferring) {
        AlertDialog(
            onDismissRequest = { /* prevent dismiss */ },
            containerColor = SurfaceSlate900,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transfer in Progress",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    transferProgress?.let { p ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StatusSuccess.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = p.formattedSpeed,
                                color = StatusSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                            color = StatusSuccess,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = progress?.formattedBitrate ?: "0 Mbps",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { percent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PrimaryIndigo,
                        trackColor = SurfaceSlate950
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (progress != null) "${FileItem.formatBytes(progress.transferredBytes)} / ${FileItem.formatBytes(progress.totalBytes)} (${progress.percentage.toInt()}%)" else "Connecting…",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Text(
                            text = progress?.formattedEta ?: "Estimating…",
                            color = AccentSky,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = progress?.currentFileName ?: "Preparing sockets…",
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelTransfer() }) {
                    Text("Cancel Transfer", color = StatusError)
                }
            }
        )
    }

    // Modal 3: Transfer Status Result Dialog
    statusDialog?.let { (success, message) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissStatusDialog() },
            containerColor = SurfaceSlate900,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (success) StatusSuccess else StatusError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (success) "Transfer Completed" else "Transfer Notice",
                        color = TextPrimary,
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(8.dp)
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
    onPickFolder: () -> Unit,
    onRemoveFile: (FileItem) -> Unit,
    onClearFiles: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )

                if (peers.isNotEmpty()) {
                    Text(
                        text = "Tap to select",
                        fontSize = 11.sp,
                        color = PrimaryIndigoLight
                    )
                }
            }
        }

        if (peers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlate900),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SurfaceSlate700.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Searching for nearby devices…",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Ensure SpeedShare is open on your Windows PC or other phones on the same Wi-Fi.",
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
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
        }

        item {
            QuickCategoryDeck(
                onPickCategory = onPickCategory,
                onPickFolder = onPickFolder
            )
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
                        text = "QUEUED FILES (${selectedFiles.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    TextButton(onClick = onClearFiles) {
                        Text(
                            text = "Clear All",
                            color = StatusError,
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
            Spacer(modifier = Modifier.height(90.dp))
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
            containerColor = if (isSelected) PrimaryIndigoContainer else SurfaceSlate900
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                1.5.dp,
                if (isSelected) PrimaryIndigo else SurfaceSlate700.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) PrimaryIndigo.copy(alpha = 0.25f) else SurfaceSlate800
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (peer.isWindows) Icons.Default.Laptop else Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = if (isSelected) PrimaryIndigoLight else TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
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

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) PrimaryIndigo else SurfaceSlate800,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Text(
                    text = if (isSelected) "SELECTED" else "READY",
                    color = if (isSelected) TextPureWhite else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
        colors = CardDefaults.cardColors(containerColor = SurfaceSlate900),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceSlate700.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PrimaryIndigo.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileCategoryIcon(file.fileCategory),
                    contentDescription = null,
                    tint = PrimaryIndigoLight,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = TextPrimary,
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

private fun getFileCategoryIcon(category: String): ImageVector {
    return when (category) {
        "VIDEO" -> Icons.Default.VideoLibrary
        "IMAGE" -> Icons.Default.Image
        "AUDIO" -> Icons.Default.Audiotrack
        "ARCHIVE" -> Icons.Default.FolderZip
        "DOCUMENT" -> Icons.Default.Description
        "APP" -> Icons.Default.Android
        "CODE" -> Icons.Default.Code
        else -> Icons.Default.Description
    }
}



