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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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

    private val _incomingRequestFlow = MutableSharedFlow<IncomingTransferRequest>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val incomingRequestFlow: SharedFlow<IncomingTransferRequest> = _incomingRequestFlow.asSharedFlow()

    private val _progressFlow = MutableStateFlow<TransferProgress?>(null)
    val progressFlow = _progressFlow

    private val _transferCompletedFlow = MutableSharedFlow<Pair<Boolean, String?>>(
        replay = 0,
        extraBufferCapacity = 8
    )
    val transferCompletedFlow: SharedFlow<Pair<Boolean, String?>> = _transferCompletedFlow.asSharedFlow()

    // Per-session decision and tracking so multiple concurrent incoming
    // transfers are supported (previous code overwrote a single field).
    private val pendingDecisions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val activeSockets = ConcurrentHashMap<String, Socket>()
    private val requestMeta = ConcurrentHashMap<String, Pair<String, String>>() // sessionId -> (senderDevice, senderIp)
    private val sessionIdGen = AtomicLong(System.currentTimeMillis())

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

                    scope?.launch {
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
        // Close all active sockets and cancel pending decisions
        activeSockets.values.forEach { runCatching { it.close() } }
        activeSockets.clear()
        pendingDecisions.values.forEach { it.cancel() }
        pendingDecisions.clear()
        requestMeta.clear()

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Server close error: ${e.message}")
        }
        serverSocket = null
    }

    fun acceptTransfer(sessionId: String? = null) {
        if (sessionId == null) {
            // accept most recent pending
            pendingDecisions.values.lastOrNull()?.complete(true)
        } else {
            pendingDecisions.remove(sessionId)?.complete(true)
        }
    }

    fun declineTransfer(sessionId: String? = null) {
        if (sessionId == null) {
            pendingDecisions.values.lastOrNull()?.complete(false)
        } else {
            pendingDecisions.remove(sessionId)?.complete(false)
        }
    }

    fun cancelTransfer(sessionId: String? = null) {
        if (sessionId == null) {
            activeSockets.values.forEach { runCatching { it.close() } }
            activeSockets.clear()
        } else {
            activeSockets.remove(sessionId)?.let { runCatching { it.close() } }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        var currentSenderDevice = "Unknown Device"
        var currentSenderIp = socket.inetAddress?.hostAddress ?: ""
        if (currentSenderIp.startsWith("::ffff:")) currentSenderIp = currentSenderIp.substring(7)
        var sessionId = "ss-${sessionIdGen.incrementAndGet().toString(16)}"
        val receivedFilesList = mutableListOf<FileItem>()
        var averageSpeed = 0.0

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SpeedShare:TransferServerWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L)
        }

        try {
            val inputStream = DataInputStream(socket.getInputStream())
            val outputStream = DataOutputStream(socket.getOutputStream())
            activeSockets[sessionId] = socket

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

            sessionId = json.optString("sessionId").ifEmpty { sessionId }
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

            // Replace the active socket entry now that we have a real sessionId
            val oldId = activeSockets.entries.firstOrNull { it.value === socket }?.key
            if (oldId != null && oldId != sessionId) {
                activeSockets.remove(oldId)
            }
            activeSockets[sessionId] = socket
            requestMeta[sessionId] = currentSenderDevice to currentSenderIp

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
            pendingDecisions[sessionId] = decisionDeferred
            _incomingRequestFlow.emit(request)

            val isAccepted = try {
                decisionDeferred.await()
            } catch (e: CancellationException) {
                // Server was stopped
                false
            } finally {
                pendingDecisions.remove(sessionId)
            }

            if (!isAccepted) {
                val declineJson = JSONObject().apply {
                    put("action", "TRANSFER_DECLINE")
                    put("sessionId", sessionId)
                    put("reason", "Declined by recipient")
                }.toString().toByteArray(Charsets.UTF_8)

                outputStream.writeInt(declineJson.size)
                outputStream.write(declineJson)
                outputStream.flush()

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
                val fileStartTime = System.currentTimeMillis()

                // Read file index & size
                val fileIndex = inputStream.readInt()
                val fileSize = inputStream.readLong()

                if (fileIndex != i) {
                    Log.w(TAG, "Unexpected file index $fileIndex (expected $i)")
                }

                val safeFileName = fileMeta.name.replace("/", "_").replace("\\", "_")
                val destFile = getUniqueFile(downloadDir, safeFileName)

                if (fileSize == 0L) {
                    // Empty file: still need to create it so MediaScanner picks it up
                    destFile.createNewFile()
                } else {
                    FileOutputStream(destFile).use { fos ->
                        var remaining = fileSize
                        while (remaining > 0) {
                            val toRead = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
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
                                val remainingBytes = maxOf(0L, totalSize - totalBytesTransferred)
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
                }

                val fileDurationSec = (System.currentTimeMillis() - fileStartTime) / 1000.0
                val fileAverageSpeed = when {
                    fileSize == 0L -> averageSpeed
                    fileDurationSec > 0.001 -> fileSize / fileDurationSec
                    else -> averageSpeed
                }

                // Add successful history record with true average speed
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
                        speedBytesPerSec = fileAverageSpeed
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
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
            activeSockets.remove(sessionId)
            requestMeta.remove(sessionId)
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
