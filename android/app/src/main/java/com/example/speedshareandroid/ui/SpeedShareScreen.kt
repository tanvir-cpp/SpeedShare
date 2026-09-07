package com.example.speedshareandroid.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import com.example.speedshareandroid.ui.components.IncomingTransferSheet
import com.example.speedshareandroid.ui.components.QuickCategoryDeck
import com.example.speedshareandroid.ui.components.RadarHero
import com.example.speedshareandroid.ui.components.TransferSheet
import com.example.speedshareandroid.ui.components.UpdateDialog
import com.example.speedshareandroid.ui.screens.HistoryScreen
import com.example.speedshareandroid.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedShareScreen(
    viewModel: SpeedShareViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = LocalSpeedShareColors.current
    val currentTab by viewModel.selectedTab.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val selectedPeer by viewModel.selectedPeer.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val incomingRequest by viewModel.incomingRequest.collectAsState()
    val isTransferring by viewModel.isTransferring.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val statusDialog by viewModel.transferStatusDialog.collectAsState()
    val isReceiving by viewModel.isReceiving.collectAsState()
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
                                .clip(RoundedCornerShape(9.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SpeedShare",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val peerCount = peers.size
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (peerCount > 0) colors.successContainer else MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = if (peerCount > 0) "$peerCount online" else "Scanning",
                                        color = if (peerCount > 0) colors.onSuccessContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "IP: ${viewModel.localIp}",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                // Gradient Send deck on the Share tab when files are queued
                if (currentTab == AppTab.SHARE) {
                    AnimatedVisibility(
                        visible = selectedFiles.isNotEmpty(),
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val totalBytes = selectedFiles.sumOf { it.size }
                                    Text(
                                        text = "${selectedFiles.size} ${if (selectedFiles.size == 1) "file" else "files"} selected",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = FileItem.formatBytes(totalBytes),
                                        color = colors.success,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Button(
                                    onClick = { viewModel.sendFiles() },
                                    enabled = selectedPeer != null && !isTransferring,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = colors.surfaceRaised,
                                        disabledContentColor = colors.textDisabled
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                                    modifier = Modifier.background(
                                        brush = colors.brandGradients.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
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

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                    )
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.SHARE,
                        onClick = { viewModel.selectTab(AppTab.SHARE) },
                        icon = { Icon(Icons.Default.Share, contentDescription = "Transfer") },
                        label = { Text("Transfer") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.HISTORY,
                        onClick = { viewModel.selectTab(AppTab.HISTORY) },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = colors.textSecondary,
                            unselectedTextColor = colors.textSecondary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                when (tab) {
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
    }

    // Flow 1: Incoming transfer request — modal bottom sheet
    incomingRequest?.let { req ->
        IncomingTransferSheet(
            request = req,
            onAccept = { viewModel.acceptIncoming() },
            onDecline = { viewModel.declineIncoming() }
        )
    }

    // Flow 2: Active transfer + inline result — modal bottom sheet
    if (isTransferring || statusDialog != null) {
        TransferSheet(
            progress = transferProgress,
            inProgress = isTransferring,
            resultSuccess = statusDialog?.first,
            resultMessage = statusDialog?.second,
            isReceiving = isReceiving,
            onCancel = { viewModel.cancelTransfer() },
            onDismissResult = { viewModel.dismissStatusDialog() }
        )
    }

    // Flow 4: Auto-update dialog
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
    val colors = LocalSpeedShareColors.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            RadarHero(
                localDeviceName = localDeviceName,
                localIp = localIp,
                deviceCount = peers.size,
                onRefresh = onRefresh
            )
        }

        // Section 1: Discovered devices
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby devices",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
                if (peers.isNotEmpty()) {
                    Text(
                        text = "Tap to select",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (peers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceRaised)
                                .border(1.dp, colors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No devices nearby yet",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Open SpeedShare on your Windows PC or another phone on the same Wi-Fi.",
                            color = colors.textSecondary,
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

        // Section 2: Quick-pick categories
        item {
            Text(
                text = "Choose files to share",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = colors.textPrimary
            )
        }

        item {
            QuickCategoryDeck(
                onPickCategory = onPickCategory,
                onPickFolder = onPickFolder
            )
        }

        // Section 3: Queued files
        if (selectedFiles.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Queued files (${selectedFiles.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                    TextButton(onClick = onClearFiles) {
                        Text(
                            text = "Clear all",
                            color = colors.error,
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
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
fun DeviceCard(
    peer: DiscoveredPeer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSpeedShareColors.current
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) scheme.primaryContainer else scheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                1.5.dp,
                if (isSelected) scheme.primary else colors.border,
                RoundedCornerShape(16.dp)
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) scheme.primary.copy(alpha = 0.18f) else colors.surfaceRaised
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (peer.isWindows) Icons.Default.Laptop else Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = if (isSelected) scheme.primary else colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.deviceName,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${peer.ipAddress} • ${peer.displayBadge}",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) scheme.primary else colors.surfaceRaised,
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isSelected) "Selected" else "Ready",
                        color = if (isSelected) scheme.onPrimary else colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FileItemRow(
    file: FileItem,
    onRemove: () -> Unit
) {
    val colors = LocalSpeedShareColors.current
    val categoryColor = CategoryColors.forCategory(file.fileCategory)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(categoryColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileCategoryIcon(file.fileCategory),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = file.formattedSize,
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = colors.textMuted,
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
