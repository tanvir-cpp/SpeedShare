package com.example.speedshareandroid.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedshareandroid.data.HistoryRepository
import com.example.speedshareandroid.models.*
import com.example.speedshareandroid.network.DiscoveryManager
import com.example.speedshareandroid.network.TransferClient
import com.example.speedshareandroid.network.TransferServer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class AppTab {
    SHARE, HISTORY, SETTINGS
}

class SpeedShareViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val prefs = context.getSharedPreferences("speedshare_prefs", Context.MODE_PRIVATE)

    val historyRepository = HistoryRepository(context)
    val discoveryManager: DiscoveryManager
    val transferServer: TransferServer
    val transferClient: TransferClient

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

    // History Tab Filter & Search
    val allHistoryRecords = historyRepository.records
    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
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
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
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

            if (currentList.none { it.uri == uri }) {
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
        _incomingRequest.value = null
        _isTransferring.value = true
        transferServer.acceptTransfer()
    }

    fun declineIncoming() {
        _incomingRequest.value = null
        transferServer.declineTransfer()
    }

    fun cancelTransfer() {
        transferClient.cancel()
        transferServer.cancelTransfer()
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

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stop()
        transferServer.stop()
    }
}
