package com.example.speedshareandroid.models

import android.net.Uri

data class DiscoveredPeer(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String = "ANDROID", // "WINDOWS" or "ANDROID"
    val ipAddress: String,
    val port: Int = 53318,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val isWindows: Boolean get() = deviceType.equals("WINDOWS", ignoreCase = true)
    val displayType: String get() = if (isWindows) "Windows PC" else "Android Device"
    val displayBadge: String get() = if (isWindows) "Windows" else "Android"
}

data class FileItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String = "application/octet-stream",
    val uri: Uri? = null,
    val localPath: String? = null
) {
    val formattedSize: String get() = formatBytes(size)

    val fileCategory: String
        get() {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".webm") -> "VIDEO"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif") -> "IMAGE"
                lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".aac") -> "AUDIO"
                lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".iso") -> "ARCHIVE"
                lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".ppt") || lower.endsWith(".txt") -> "DOCUMENT"
                lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".cs") || lower.endsWith(".py") || lower.endsWith(".cpp") || lower.endsWith(".js") || lower.endsWith(".json") -> "CODE"
                else -> "FILE"
            }
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
            if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

data class TransferProgress(
    val sessionId: String,
    val currentFileName: String,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val transferredBytes: Long,
    val totalBytes: Long,
    val percentage: Float,
    val speedBytesPerSec: Double,
    val etaSeconds: Long
) {
    val formattedSpeed: String
        get() {
            if (speedBytesPerSec < 1024 * 1024) {
                return String.format("%.1f KB/s", speedBytesPerSec / 1024.0)
            }
            return String.format("%.1f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
        }

    val formattedBitrate: String
        get() {
            val mbps = (speedBytesPerSec * 8.0) / (1000.0 * 1000.0)
            if (mbps >= 1000) {
                return String.format("%.2f Gbps", mbps / 1000.0)
            }
            return String.format("%.1f Mbps", mbps)
        }

    val formattedEta: String
        get() {
            if (etaSeconds <= 0) return "Calculating..."
            if (etaSeconds >= 3600) return "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
            if (etaSeconds >= 60) return "${etaSeconds / 60}m ${etaSeconds % 60}s"
            return "${etaSeconds}s"
        }
}

data class IncomingTransferRequest(
    val sessionId: String,
    val senderDevice: String,
    val deviceType: String,
    val files: List<FileItem>,
    val totalSize: Long,
    val senderIp: String
)
