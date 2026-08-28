package com.example.speedshareandroid.network

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.example.speedshareandroid.data.HistoryRepository
import com.example.speedshareandroid.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class TransferServer(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {
    companion object {
        const val DEFAULT_PORT = 53318
        private const val CHUNK_SIZE = 1024 * 1024 // 1 MB buffer
        private const val TAG = "TransferServer"
    }

    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null

    private val _incomingRequestFlow = MutableSharedFlow<IncomingTransferRequest>()
    val incomingRequestFlow: SharedFlow<IncomingTransferRequest> = _incomingRequestFlow.asSharedFlow()

    private val _progressFlow = MutableStateFlow<TransferProgress?>(null)
    val progressFlow = _progressFlow

    private val _transferCompletedFlow = MutableSharedFlow<Pair<Boolean, String?>>()
    val transferCompletedFlow: SharedFlow<Pair<Boolean, String?>> = _transferCompletedFlow.asSharedFlow()

    private var activeDecisionDeferred: CompletableDeferred<Boolean>? = null
    private var activeSocket: Socket? = null

    fun start(port: Int = DEFAULT_PORT) {
        stop()
        val job = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + job)

        scope?.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    receiveBufferSize = 2 * 1024 * 1024
                    bind(InetSocketAddress(port))
                }
                Log.i(TAG, "TransferServer started on port $port")

                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    client.tcpNoDelay = true
                    client.receiveBufferSize = 2 * 1024 * 1024
                    client.sendBufferSize = 2 * 1024 * 1024
                    activeSocket = client

                    launch {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server loop error: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        try {
            activeSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Server close error: ${e.message}")
        }
        activeSocket = null
        serverSocket = null
    }

    fun acceptTransfer() {
        activeDecisionDeferred?.complete(true)
    }

    fun declineTransfer() {
        activeDecisionDeferred?.complete(false)
    }

    fun cancelTransfer() {
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cancel error: ${e.message}")
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        var currentSenderDevice = "Unknown Device"
        var currentSenderIp = socket.inetAddress.hostAddress ?: ""
        val receivedFilesList = mutableListOf<FileItem>()
        var averageSpeed = 0.0

        try {
            val inputStream = DataInputStream(socket.getInputStream())
            val outputStream = DataOutputStream(socket.getOutputStream())

            // 1. Read header length
            val headerLength = inputStream.readInt()
            if (headerLength <= 0 || headerLength > 10 * 1024 * 1024) {
                throw IllegalArgumentException("Invalid header length")
            }

            // 2. Read JSON request
            val headerBytes = ByteArray(headerLength)
            inputStream.readFully(headerBytes)
            val json = JSONObject(String(headerBytes, Charsets.UTF_8))

            val action = json.optString("action")
            if (action != "TRANSFER_REQUEST") {
                throw IllegalArgumentException("Unexpected action: $action")
            }

            val sessionId = json.optString("sessionId")
            currentSenderDevice = json.optString("senderDevice", "Remote Device")
            val devType = json.optString("deviceType", "WINDOWS")
            val totalSize = json.optLong("totalSize")
            val filesJson = json.optJSONArray("files") ?: JSONArray()

            for (i in 0 until filesJson.length()) {
                val fObj = filesJson.getJSONObject(i)
                receivedFilesList.add(
                    FileItem(
                        id = fObj.optString("id", i.toString()),
                        name = fObj.optString("name", "file_$i"),
                        size = fObj.optLong("size", 0L),
                        mimeType = fObj.optString("mime", "application/octet-stream")
                    )
                )
            }

            val request = IncomingTransferRequest(
                sessionId = sessionId,
                senderDevice = currentSenderDevice,
                deviceType = devType,
                files = receivedFilesList,
                totalSize = totalSize,
                senderIp = currentSenderIp
            )

            // Prompt user
            val decisionDeferred = CompletableDeferred<Boolean>()
            activeDecisionDeferred = decisionDeferred
            _incomingRequestFlow.emit(request)

            val isAccepted = decisionDeferred.await()
            if (!isAccepted) {
                val declineJson = JSONObject().apply {
                    put("action", "TRANSFER_DECLINE")
                    put("sessionId", sessionId)
                    put("reason", "Declined by recipient")
                }.toString().toByteArray(Charsets.UTF_8)

                outputStream.writeInt(declineJson.size)
                outputStream.write(declineJson)
                outputStream.flush()

                // Record declined / cancelled entries in history
                receivedFilesList.forEach { f ->
                    historyRepository.addRecord(
                        TransferRecord(
                            fileName = f.name,
                            fileSize = f.size,
                            mimeType = f.mimeType,
                            direction = TransferDirection.RECEIVED,
                            status = TransferStatus.CANCELLED,
                            peerName = currentSenderDevice,
                            peerIp = currentSenderIp
                        )
                    )
                }

                _transferCompletedFlow.emit(Pair(false, "Declined by user"))
                return@withContext
            }

            // Send Accept
            val acceptJson = JSONObject().apply {
                put("action", "TRANSFER_ACCEPT")
                put("sessionId", sessionId)
            }.toString().toByteArray(Charsets.UTF_8)
            outputStream.writeInt(acceptJson.size)
            outputStream.write(acceptJson)
            outputStream.flush()

            // Prepare download directory
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "SpeedShare"
            ).apply { if (!exists()) mkdirs() }

            // High-speed stream receiver
            var totalBytesTransferred = 0L
            val buffer = ByteArray(CHUNK_SIZE)
            val startTime = System.currentTimeMillis()
            var lastReportTime = startTime
            var lastReportBytes = 0L

            for (i in receivedFilesList.indices) {
                val fileMeta = receivedFilesList[i]

                // Read file index & size
                val fileIndex = inputStream.readInt()
                val fileSize = inputStream.readLong()

                val safeFileName = fileMeta.name.replace("/", "_").replace("\\", "_")
                val destFile = getUniqueFile(downloadDir, safeFileName)

                FileOutputStream(destFile).use { fos ->
                    var remaining = fileSize
                    while (remaining > 0) {
                        val toRead = Math.min(CHUNK_SIZE.toLong(), remaining).toInt()
                        val bytesRead = inputStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) throw java.io.EOFException("Premature end of stream")

                        fos.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                        totalBytesTransferred += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 200 || totalBytesTransferred == totalSize) {
                            val deltaSec = (now - lastReportTime) / 1000.0
                            val speed = if (deltaSec > 0) (totalBytesTransferred - lastReportBytes) / deltaSec else 0.0
                            averageSpeed = speed
                            val remainingBytes = Math.max(0L, totalSize - totalBytesTransferred)
                            val eta = if (speed > 0) (remainingBytes / speed).toLong() else 0L

                            _progressFlow.value = TransferProgress(
                                sessionId = sessionId,
                                currentFileName = safeFileName,
                                currentFileIndex = i + 1,
                                totalFiles = receivedFilesList.size,
                                transferredBytes = totalBytesTransferred,
                                totalBytes = totalSize,
                                percentage = if (totalSize > 0) (totalBytesTransferred.toFloat() / totalSize.toFloat() * 100f) else 100f,
                                speedBytesPerSec = speed,
                                etaSeconds = eta
                            )

                            lastReportBytes = totalBytesTransferred
                            lastReportTime = now
                        }
                    }
                    fos.flush()
                }

                // Add successful history record
                historyRepository.addRecord(
                    TransferRecord(
                        fileName = destFile.name,
                        filePath = destFile.absolutePath,
                        fileSize = fileSize,
                        mimeType = fileMeta.mimeType,
                        direction = TransferDirection.RECEIVED,
                        status = TransferStatus.COMPLETED,
                        peerName = currentSenderDevice,
                        peerIp = currentSenderIp,
                        speedBytesPerSec = averageSpeed
                    )
                )

                // Notify media scanner
                try {
                    MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                } catch (e: Exception) {
                    Log.d(TAG, "Media scan warning: ${e.message}")
                }
            }

            // Send Transfer Complete confirmation
            val completeJson = JSONObject().apply {
                put("action", "TRANSFER_COMPLETE")
                put("sessionId", sessionId)
                put("status", "SUCCESS")
            }.toString().toByteArray(Charsets.UTF_8)
            outputStream.writeInt(completeJson.size)
            outputStream.write(completeJson)
            outputStream.flush()

            _transferCompletedFlow.emit(Pair(true, null))
        } catch (e: Exception) {
            Log.e(TAG, "Transfer receive error: ${e.message}")

            // Log failed transfer to history
            if (receivedFilesList.isNotEmpty()) {
                receivedFilesList.forEach { f ->
                    historyRepository.addRecord(
                        TransferRecord(
                            fileName = f.name,
                            fileSize = f.size,
                            mimeType = f.mimeType,
                            direction = TransferDirection.RECEIVED,
                            status = TransferStatus.FAILED,
                            peerName = currentSenderDevice,
                            peerIp = currentSenderIp
                        )
                    )
                }
            }

            _transferCompletedFlow.emit(Pair(false, e.message ?: "Transfer interrupted"))
        } finally {
            try { socket.close() } catch (e: Exception) {}
            activeSocket = null
        }
    }

    private fun getUniqueFile(folder: File, fileName: String): File {
        var file = File(folder, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var count = 1

        while (file.exists()) {
            file = File(folder, "$nameWithoutExt ($count)$ext")
            count++
        }
        return file
    }
}
