package com.example.speedshareandroid.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedshareandroid.models.DiscoveredPeer
import com.example.speedshareandroid.models.FileItem
import com.example.speedshareandroid.models.IncomingTransferRequest
import com.example.speedshareandroid.models.TransferProgress
import com.example.speedshareandroid.network.DiscoveryManager
import com.example.speedshareandroid.network.TransferClient
import com.example.speedshareandroid.network.TransferServer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SpeedShareViewModel(application: Application) : AndroidViewModel(application) {

    val discoveryManager = DiscoveryManager(application)
    val transferServer = TransferServer(application)
    val transferClient = TransferClient(application)

    val peers = discoveryManager.peersFlow

    private val _selectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    val selectedPeer = _selectedPeer.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<FileItem>>(emptyList())
    val selectedFiles = _selectedFiles.asStateFlow()

    private val _incomingRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingRequest = _incomingRequest.asStateFlow()

    private val _isTransferring = MutableStateFlow(false)
    val isTransferring = _isTransferring.asStateFlow()

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress = _transferProgress.asStateFlow()

    private val _transferStatusDialog = MutableStateFlow<Pair<Boolean, String?>?>(null) // (success, message)
    val transferStatusDialog = _transferStatusDialog.asStateFlow()

    init {
        discoveryManager.start()
        transferServer.start()

        // Collect incoming requests
        viewModelScope.launch {
            transferServer.incomingRequestFlow.collect { req ->
                _incomingRequest.value = req
            }
        }

        // Collect server progress
        viewModelScope.launch {
            transferServer.progressFlow.collect { p ->
                if (_isTransferring.value) {
                    _transferProgress.value = p
                }
            }
        }

        // Collect client progress
        viewModelScope.launch {
            transferClient.progressFlow.collect { p ->
                if (_isTransferring.value) {
                    _transferProgress.value = p
                }
            }
        }

        // Server transfer completion
        viewModelScope.launch {
            transferServer.transferCompletedFlow.collect { (success, err) ->
                _isTransferring.value = false
                _transferProgress.value = null
                _transferStatusDialog.value = Pair(success, if (success) "Files saved to Downloads/SpeedShare" else err)
            }
        }

        // Client transfer completion
        viewModelScope.launch {
            transferClient.transferResultFlow.collect { (success, err) ->
                _isTransferring.value = false
                _transferProgress.value = null
                _transferStatusDialog.value = Pair(success, if (success) "All files sent successfully" else err)
            }
        }
    }

    fun selectPeer(peer: DiscoveredPeer) {
        _selectedPeer.value = peer
    }

    fun clearSelectedPeer() {
        _selectedPeer.value = null
    }

    fun addFilesFromUris(context: Context, uris: List<Uri>) {
        val currentList = _selectedFiles.value.toMutableList()
        val resolver = context.contentResolver

        for (uri in uris) {
            var fileName = "unknown_file"
            var fileSize = 0L
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            if (!currentList.any { it.uri == uri }) {
                currentList.add(
                    FileItem(
                        id = UUID.randomUUID().toString(),
                        name = fileName,
                        size = fileSize,
                        mimeType = mimeType,
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
        _transferProgress.value = TransferProgress(
            sessionId = "",
            currentFileName = "Connecting to ${peer.deviceName}...",
            currentFileIndex = 0,
            totalFiles = files.size,
            transferredBytes = 0L,
            totalBytes = files.sumOf { it.size },
            percentage = 0f,
            speedBytesPerSec = 0.0,
            etaSeconds = 0
        )

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
        _transferProgress.value = null
    }

    fun dismissStatusDialog() {
        _transferStatusDialog.value = null
    }

    fun refreshDiscovery() {
        discoveryManager.broadcastBeacon()
    }

    override fun onCleared() {
        super.onCleared()
        discoveryManager.stop()
        transferServer.stop()
    }
}
