package com.example.speedshareandroid.data

import android.content.Context
import android.util.Log
import com.example.speedshareandroid.models.HistoryFilter
import com.example.speedshareandroid.models.TransferDirection
import com.example.speedshareandroid.models.TransferRecord
import com.example.speedshareandroid.models.TransferStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryRepository(private val context: Context) {
    companion object {
        private const val FILE_NAME = "transfer_history.json"
        private const val TAG = "HistoryRepository"
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _records = MutableStateFlow<List<TransferRecord>>(emptyList())
    val records: StateFlow<List<TransferRecord>> = _records.asStateFlow()

    init {
        scope.launch {
            loadRecords()
        }
    }

    private suspend fun loadRecords() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (!file.exists()) {
                    _records.value = emptyList()
                    return@withContext
                }

                val jsonStr = file.readText(Charsets.UTF_8)
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<TransferRecord>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        TransferRecord(
                            id = obj.optString("id"),
                            fileName = obj.optString("fileName"),
                            filePath = obj.optString("filePath").takeIf { it.isNotEmpty() },
                            fileSize = obj.optLong("fileSize"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            direction = TransferDirection.valueOf(obj.optString("direction", TransferDirection.RECEIVED.name)),
                            status = TransferStatus.valueOf(obj.optString("status", TransferStatus.COMPLETED.name)),
                            peerName = obj.optString("peerName", "Unknown Device"),
                            peerIp = obj.optString("peerIp", ""),
                            speedBytesPerSec = obj.optDouble("speedBytesPerSec", 0.0),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                // Sort newest first
                _records.value = list.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load history: ${e.message}")
            }
        }
    }

    private suspend fun saveRecords() = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()
            _records.value.forEach { r ->
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("fileName", r.fileName)
                    put("filePath", r.filePath ?: "")
                    put("fileSize", r.fileSize)
                    put("mimeType", r.mimeType)
                    put("direction", r.direction.name)
                    put("status", r.status.name)
                    put("peerName", r.peerName)
                    put("peerIp", r.peerIp)
                    put("speedBytesPerSec", r.speedBytesPerSec)
                    put("timestamp", r.timestamp)
                }
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history: ${e.message}")
        }
    }

    suspend fun addRecord(record: TransferRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = _records.value.toMutableList()
            current.add(0, record)
            _records.value = current
            saveRecords()
        }
    }

    suspend fun deleteRecord(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _records.value = _records.value.filter { it.id != id }
            saveRecords()
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _records.value = emptyList()
            saveRecords()
        }
    }

    fun filterRecords(
        list: List<TransferRecord>,
        query: String,
        filter: HistoryFilter
    ): List<TransferRecord> {
        return list.filter { r ->
            val matchesQuery = query.isEmpty() ||
                    r.fileName.contains(query, ignoreCase = true) ||
                    r.peerName.contains(query, ignoreCase = true) ||
                    r.fileCategory.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.RECEIVED -> r.direction == TransferDirection.RECEIVED
                HistoryFilter.SENT -> r.direction == TransferDirection.SENT
                HistoryFilter.FAILED -> r.status == TransferStatus.FAILED || r.status == TransferStatus.CANCELLED
            }
            matchesQuery && matchesFilter
        }
    }
}
