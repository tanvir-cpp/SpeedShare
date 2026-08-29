package com.example.speedshareandroid.network

import android.content.Context
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
import java.io.FileInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class TransferClient(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {
    companion object {
        private const val CHUNK_SIZE = 1024 * 1024 // 1 MB buffer
        private const val TAG = "TransferClient"
    }

    private var activeSocket: Socket? = null

    private val _progressFlow = MutableStateFlow<TransferProgress?>(null)
    val progressFlow = _progressFlow

    private val _transferResultFlow = MutableSharedFlow<Pair<Boolean, String?>>()
    val transferResultFlow: SharedFlow<Pair<Boolean, String?>> = _transferResultFlow.asSharedFlow()

    fun cancel() {
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Cancel error: ${e.message}")
        }
        activeSocket = null
    }

    suspend fun sendFiles(
        targetPeer: DiscoveredPeer,
        localDeviceName: String,
        files: List<FileItem>
    ) = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val socket = Socket()
        activeSocket = socket
        var averageSpeed = 0.0

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SpeedShare:TransferClientWakeLock")?.apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L)
        }

        try {
            socket.tcpNoDelay = true
            socket.sendBufferSize = 2 * 1024 * 1024
            socket.receiveBufferSize = 2 * 1024 * 1024
            socket.connect(InetSocketAddress(targetPeer.ipAddress, targetPeer.port), 5000)

            val inputStream = DataInputStream(socket.getInputStream())
            val outputStream = DataOutputStream(socket.getOutputStream())

            val totalSize = files.sumOf { it.size }

            // 1. Prepare and send Transfer Request
            val filesArray = JSONArray()
            files.forEachIndexed { idx, f ->
                val fObj = JSONObject().apply {
                    put("id", idx.toString())
                    put("name", f.name)
                    put("size", f.size)
                    put("mime", f.mimeType)
                }
                filesArray.put(fObj)
            }

            val requestJson = JSONObject().apply {
                put("action", "TRANSFER_REQUEST")
                put("sessionId", sessionId)
                put("senderDevice", localDeviceName)
                put("deviceType", "ANDROID")
                put("files", filesArray)
                put("totalSize", totalSize)
            }.toString().toByteArray(Charsets.UTF_8)

            outputStream.writeInt(requestJson.size)
            outputStream.write(requestJson)
            outputStream.flush()

            // 2. Wait for Accept / Decline
            val responseLength = inputStream.readInt()
            if (responseLength <= 0 || responseLength > 10 * 1024 * 1024) {
                throw IllegalArgumentException("Invalid response size")
            }

            val responseBytes = ByteArray(responseLength)
            inputStream.readFully(responseBytes)
            val responseJson = JSONObject(String(responseBytes, Charsets.UTF_8))

            val action = responseJson.optString("action")
            if (action != "TRANSFER_ACCEPT") {
                val reason = responseJson.optString("reason", "Transfer was declined by recipient")

                files.forEach { f ->
                    historyRepository.addRecord(
                        TransferRecord(
                            fileName = f.name,
                            fileSize = f.size,
                            mimeType = f.mimeType,
                            direction = TransferDirection.SENT,
                            status = TransferStatus.CANCELLED,
                            peerName = targetPeer.deviceName,
                            peerIp = targetPeer.ipAddress
                        )
                    )
                }

                _transferResultFlow.emit(Pair(false, reason))
                return@withContext
            }

            // 3. High-Speed File Streaming
            var totalBytesSent = 0L
            val buffer = ByteArray(CHUNK_SIZE)
            val startTime = System.currentTimeMillis()
            var lastReportTime = startTime
            var lastReportBytes = 0L

            for (i in files.indices) {
                val fileItem = files[i]
                val fileStartTime = System.currentTimeMillis()

                // Write file index (4 bytes) & file size (8 bytes)
                outputStream.writeInt(i)
                outputStream.writeLong(fileItem.size)
                outputStream.flush()

                var inStream: InputStream? = null
                try {
                    inStream = if (fileItem.uri != null) {
                        context.contentResolver.openInputStream(fileItem.uri)
                    } else if (fileItem.localPath != null) {
                        FileInputStream(fileItem.localPath)
                    } else {
                        throw IllegalArgumentException("No URI or path for ${fileItem.name}")
                    }

                    if (inStream == null) throw IllegalArgumentException("Could not open stream for ${fileItem.name}")

                    var remaining = fileItem.size
                    while (remaining > 0) {
                        val toRead = Math.min(CHUNK_SIZE.toLong(), remaining).toInt()
                        val bytesRead = inStream.read(buffer, 0, toRead)
                        if (bytesRead == -1) break

                        outputStream.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                        totalBytesSent += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 200 || totalBytesSent == totalSize) {
                            val deltaSec = (now - lastReportTime) / 1000.0
                            val speed = if (deltaSec > 0) (totalBytesSent - lastReportBytes) / deltaSec else 0.0
                            averageSpeed = speed
                            val remainingBytes = Math.max(0L, totalSize - totalBytesSent)
                            val eta = if (speed > 0) (remainingBytes / speed).toLong() else 0L

                            _progressFlow.value = TransferProgress(
                                sessionId = sessionId,
                                currentFileName = fileItem.name,
                                currentFileIndex = i + 1,
                                totalFiles = files.size,
                                transferredBytes = totalBytesSent,
                                totalBytes = totalSize,
                                percentage = if (totalSize > 0) (totalBytesSent.toFloat() / totalSize.toFloat() * 100f) else 100f,
                                speedBytesPerSec = speed,
                                etaSeconds = eta
                            )

                            lastReportBytes = totalBytesSent
                            lastReportTime = now
                        }
                    }
                    outputStream.flush()
                } finally {
                    try { inStream?.close() } catch (e: Exception) {}
                }

                val fileDurationSec = (System.currentTimeMillis() - fileStartTime) / 1000.0
                val fileAverageSpeed = if (fileDurationSec > 0) fileItem.size / fileDurationSec else averageSpeed

                // Add record to history with true average speed
                historyRepository.addRecord(
                    TransferRecord(
                        fileName = fileItem.name,
                        fileSize = fileItem.size,
                        mimeType = fileItem.mimeType,
                        direction = TransferDirection.SENT,
                        status = TransferStatus.COMPLETED,
                        peerName = targetPeer.deviceName,
                        peerIp = targetPeer.ipAddress,
                        speedBytesPerSec = fileAverageSpeed
                    )
                )
            }

            // 4. Read final completion confirmation
            val compLen = inputStream.readInt()
            val compBytes = ByteArray(compLen)
            inputStream.readFully(compBytes)

            _transferResultFlow.emit(Pair(true, null))
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")

            files.forEach { f ->
                historyRepository.addRecord(
                    TransferRecord(
                        fileName = f.name,
                        fileSize = f.size,
                        mimeType = f.mimeType,
                        direction = TransferDirection.SENT,
                        status = TransferStatus.FAILED,
                        peerName = targetPeer.deviceName,
                        peerIp = targetPeer.ipAddress
                    )
                )
            }

            _transferResultFlow.emit(Pair(false, e.message ?: "Transfer error"))
        } finally {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (e: Exception) {}
            try { socket.close() } catch (e: Exception) {}
            activeSocket = null
        }
    }
}
