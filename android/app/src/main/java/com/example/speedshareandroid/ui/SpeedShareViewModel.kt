package com.example.speedshareandroid.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedshareandroid.BuildConfig
import com.example.speedshareandroid.data.HistoryRepository
import com.example.speedshareandroid.models.*
import com.example.speedshareandroid.network.DiscoveryManager
import com.example.speedshareandroid.network.TransferClient
import com.example.speedshareandroid.network.TransferServer
import com.example.speedshareandroid.network.UpdateChecker
import com.example.speedshareandroid.network.UpdateError
import com.example.speedshareandroid.network.UpdateInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class AppTab {
    SHARE, HISTORY, SETTINGS
}

class SpeedShareViewModel(application: Application) : AndroidViewModel(application) {

    val currentAppVersion: String = BuildConfig.VERSION_NAME

    private val context: Context get() = getApplication<Application>().applicationContext
    private val prefs = context.getSharedPreferences("speedshare_prefs", Context.MODE_PRIVATE)

    val historyRepository = HistoryRepository(context)
    val discoveryManager: DiscoveryManager
    val transferServer: TransferServer
    val transferClient: TransferClient

    // Cached local IP — recomputing per recomposition hit the network stack
    val localIp: String get() = discoveryManager.getLocalIp()

    // Tab state
    private val _selectedTab = MutableStateFlow(AppTab.SHARE)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    // Device Discovery State
    val peers: StateFlow<List<DiscoveredPeer>>
    private val _selectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    val selectedPeer: StateFlow<DiscoveredPeer?> = _selectedPeer.asStateFlow()

    // Queued Files State
    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val selectedFiles: StateFlow<List<FileItem>> = _selectedFiles.asStateFlow()

    // Active Transfer / Modals State
    private val _incomingRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingRequest: StateFlow<IncomingTransferRequest?> = _incomingRequest.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring: StateFlow<Boolean> = _isTransferring.asStateFlow()

    private val _transferStatusDialog = MutableStateFlow<Pair<Boolean, String?>?>(null)
    val transferStatusDialog: StateFlow<Pair<Boolean, String?>?> = _transferStatusDialog.asStateFlow()

    // Auto-Update State
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    private val _manualUpdateMessage = MutableStateFlow<String?>(null)
    val manualUpdateMessage: StateFlow<String?> = _manualUpdateMessage.asStateFlow()

    // History Tab Filter & Search
    val allHistoryRecords = historyRepository.records
    private val _historySearchQuery = MutableStateFlow(prefs.getString("history_search", "") ?: "")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow(
        HistoryFilter.valueOf(prefs.getString("history_filter", HistoryFilter.ALL.name) ?: HistoryFilter.ALL.name)
    )
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    val filteredHistoryRecords: StateFlow<List<TransferRecord>> = combine(
        allHistoryRecords,
        _historySearchQuery,
        _historyFilter
    ) { records, query, filter ->
        historyRepository.filterRecords(records, query, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Device Settings State
    private val _customDeviceName = MutableStateFlow(
        prefs.getString("device_name", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}") ?: "Android Device"
    )
    val customDeviceName: StateFlow<String> = _customDeviceName.asStateFlow()

    init {
        discoveryManager = DiscoveryManager(context).apply {
            deviceName = _customDeviceName.value
        }
        peers = discoveryManager.peersFlow

        transferServer = TransferServer(context, historyRepository)
        transferClient = TransferClient(context, historyRepository)

        transferServer.start()
        discoveryManager.start()

        // Listen for incoming transfer requests
        viewModelScope.launch {
            transferServer.incomingRequestFlow.collect { req ->
                _incomingRequest.value = req
            }
        }

        // Listen for server completion
        viewModelScope.launch {
            transferServer.transferCompletedFlow.collect { result ->
                _isTransferring.value = false
                _transferStatusDialog.value = result
            }
        }

        // Listen for client completion
        viewModelScope.launch {
            transferClient.transferResultFlow.collect { result ->
                _isTransferring.value = false
                _transferStatusDialog.value = result
            }
        }

        // Check for updates on startup
        checkForUpdates(isManual = false)
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
        prefs.edit().putString("history_search", query).apply()
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
        prefs.edit().putString("history_filter", filter.name).apply()
    }

    fun selectPeer(peer: DiscoveredPeer) {
        _selectedPeer.value = peer
    }

    fun clearSelectedPeer() {
        _selectedPeer.value = null
    }

    fun refreshDiscovery() {
        discoveryManager.broadcastBeacon()
    }

    fun addFilesFromUris(ctx: Context, uris: List<Uri>) {
        val currentList = _selectedFiles.value.toMutableList()

        for (uri in uris) {
            var fileName = "unknown_file"
            var fileSize = 0L

            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: "file"
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            // Dedupe by URI, but also avoid name+size duplicates from "Open with" pickers
            val already = currentList.any { it.uri == uri }
            if (!already) {
                currentList.add(
                    FileItem(
                        id = uri.toString(),
                        name = fileName,
                        size = fileSize,
                        mimeType = ctx.contentResolver.getType(uri) ?: "application/octet-stream",
                        uri = uri
                    )
                )
            }
        }
        _selectedFiles.value = currentList
    }

    /**
     * Walk a document tree URI (granted via Storage Access Framework) and enqueue
     * every file as an individual FileItem. Subfolder structure is preserved in
     * the display name.
     */
    fun addFilesFromTreeUri(ctx: Context, treeUri: Uri) {
        val currentList = _selectedFiles.value.toMutableList()
        val rootDoc = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            android.provider.DocumentsContract.getTreeDocumentId(treeUri)
        )

        fun walk(parentUri: Uri, prefix: String) {
            val children = ctx.contentResolver.query(
                parentUri,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    android.provider.DocumentsContract.Document.COLUMN_SIZE
                ),
                null, null, null
            ) ?: return
            children.use { c ->
                val idIdx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_SIZE)
                while (c.moveToNext()) {
                    val id = c.getString(idIdx) ?: continue
                    val name = c.getString(nameIdx) ?: continue
                    val mime = c.getString(mimeIdx) ?: "application/octet-stream"
                    val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                    val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    val display = if (prefix.isEmpty()) name else "$prefix/$name"

                    if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(childUri, display)
                    } else {
                        if (currentList.none { it.uri == childUri }) {
                            currentList.add(
                                FileItem(
                                    id = childUri.toString(),
                                    name = display,
                                    size = size,
                                    mimeType = mime,
                                    uri = childUri
                                )
                            )
                        }
                    }
                }
            }
        }
        walk(rootDoc, prefix = "")
        _selectedFiles.value = currentList
    }

    fun removeFile(file: FileItem) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != file.id }
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    fun sendFiles() {
        val peer = _selectedPeer.value ?: return
        val files = _selectedFiles.value
        if (files.isEmpty() || _isTransferring.value) return

        _isTransferring.value = true
        viewModelScope.launch {
            transferClient.sendFiles(peer, discoveryManager.deviceName, files)
        }
    }

    fun acceptIncoming() {
        val req = _incomingRequest.value ?: return
        _incomingRequest.value = null
        _isTransferring.value = true
        transferServer.acceptTransfer(req.sessionId)
    }

    fun declineIncoming() {
        val req = _incomingRequest.value
        _incomingRequest.value = null
        if (req != null) {
            transferServer.declineTransfer(req.sessionId)
        } else {
            transferServer.declineTransfer()
        }
    }

    fun cancelTransfer() {
        transferClient.cancel()
        transferServer.cancelTransfer(_incomingRequest.value?.sessionId)
        _isTransferring.value = false
        _transferStatusDialog.value = Pair(false, "Transfer cancelled.")
    }

    fun dismissStatusDialog() {
        _transferStatusDialog.value = null
    }

    // Active progress combining server or client
    val transferProgress = combine(
        transferServer.progressFlow,
        transferClient.progressFlow
    ) { serverProg, clientProg ->
        clientProg ?: serverProg
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // History Actions
    fun deleteHistoryRecord(recordId: String) {
        viewModelScope.launch {
            historyRepository.deleteRecord(recordId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    fun openFile(ctx: Context, record: TransferRecord) {
        try {
            if (record.filePath != null) {
                val file = File(record.filePath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        ctx,
                        "${ctx.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, record.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                    return
                }
            }
            Toast.makeText(ctx, "File not found on local storage", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(ctx: Context, record: TransferRecord) {
        try {
            if (record.filePath != null) {
                val file = File(record.filePath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        ctx,
                        "${ctx.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = record.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(intent, "Share ${record.fileName}").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(chooser)
                    return
                }
            }
            Toast.makeText(ctx, "File not found on local storage", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateDeviceName(newName: String) {
        if (newName.isNotBlank()) {
            val trimmed = newName.trim()
            _customDeviceName.value = trimmed
            prefs.edit().putString("device_name", trimmed).apply()
            discoveryManager.deviceName = trimmed
            discoveryManager.broadcastBeacon()
        }
    }

    fun checkForUpdates(isManual: Boolean = false) {
        // Rate-limit auto-checks to once per 6 hours to stay well below
        // GitHub's anonymous rate limit (60/hr per IP). Manual checks
        // always run.
        if (!isManual) {
            val lastCheck = prefs.getLong("last_update_check", 0L)
            val now = System.currentTimeMillis()
            if (now - lastCheck < 6L * 60L * 60L * 1000L) {
                return
            }
        }
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _manualUpdateMessage.value = null
            prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
            val result = UpdateChecker.checkForUpdates(currentAppVersion)
            _isCheckingUpdate.value = false
            result.fold(
                onSuccess = { update ->
                    // Don't re-show a dialog the user just dismissed
                    if (_updateInfo.value == null) {
                        _updateInfo.value = update
                    }
                },
                onFailure = { err ->
                    if (isManual) {
                        _manualUpdateMessage.value = when (err) {
                            is UpdateError.NoUpdate -> "SpeedShare v$currentAppVersion is up to date!"
                            else -> "Update check failed: ${err.message ?: err::class.simpleName}"
                        }
                    } else if (err !is UpdateError.NoUpdate) {
                        Log.w("UpdateChecker", "Background update check failed: ${err.message}")
                    }
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
        _updateDownloadProgress.value = null
    }

    fun clearManualUpdateMessage() {
        _manualUpdateMessage.value = null
    }

    fun startUpdateDownload() {
        val update = _updateInfo.value ?: return
        val url = update.apkDownloadUrl
        if (url.isNullOrEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.htmlUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }

        viewModelScope.launch {
            _updateDownloadProgress.value = 0f
            val result = UpdateChecker.downloadApk(
                context = context,
                downloadUrl = url,
                expectedSize = update.apkSize,
                sha256Url = update.sha256
            ) { progress -> _updateDownloadProgress.value = progress }

            result.fold(
                onSuccess = { file ->
                    val installResult = UpdateChecker.launchApkInstaller(context, file)
                    if (installResult.isSuccess) {
                        _updateDownloadProgress.value = null
                        _updateInfo.value = null
                    } else {
                        _updateDownloadProgress.value = null
                        val err = installResult.exceptionOrNull()
                        Toast.makeText(context,
                            "Couldn't open installer: ${err?.message ?: "unknown"}. Opening browser…",
                            Toast.LENGTH_LONG).show()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.htmlUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                },
                onFailure = { err ->
                    _updateDownloadProgress.value = null
                    Toast.makeText(context,
                        "Update failed: ${err.message ?: err::class.simpleName}",
                        Toast.LENGTH_LONG).show()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.htmlUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stop()
        transferServer.stop()
    }
}
