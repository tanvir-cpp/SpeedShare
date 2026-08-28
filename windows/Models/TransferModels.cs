using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json.Serialization;

namespace SpeedShareWindows.Models
{
    public class DiscoveredPeer
    {
        public string DeviceId { get; set; } = string.Empty;
        public string DeviceName { get; set; } = string.Empty;
        public string DeviceType { get; set; } = "WINDOWS"; // "WINDOWS" or "ANDROID"
        public string IpAddress { get; set; } = string.Empty;
        public int Port { get; set; } = 53318;
        public DateTime LastSeen { get; set; } = DateTime.UtcNow;

        public bool IsAndroid => string.Equals(DeviceType, "ANDROID", StringComparison.OrdinalIgnoreCase);
        public string DisplayType => IsAndroid ? "Android" : "Windows";
        public string DisplayBadge => IsAndroid ? "Android Device" : "Windows PC";
    }

    public class BeaconMessage
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = "BEACON";

        [JsonPropertyName("deviceId")]
        public string DeviceId { get; set; } = string.Empty;

        [JsonPropertyName("deviceName")]
        public string DeviceName { get; set; } = string.Empty;

        [JsonPropertyName("deviceType")]
        public string DeviceType { get; set; } = "WINDOWS";

        [JsonPropertyName("port")]
        public int Port { get; set; } = 53318;

        [JsonPropertyName("version")]
        public int Version { get; set; } = 1;
    }

    public class FileMetadata
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = string.Empty;

        [JsonPropertyName("name")]
        public string Name { get; set; } = string.Empty;

        [JsonPropertyName("size")]
        public long Size { get; set; }

        [JsonPropertyName("mime")]
        public string Mime { get; set; } = "application/octet-stream";

        [JsonIgnore]
        public string LocalPath { get; set; } = string.Empty;

        [JsonIgnore]
        public string FormattedSize => FormatBytes(Size);

        [JsonIgnore]
        public string FileCategory
        {
            get
            {
                string ext = Path.GetExtension(Name).ToLowerInvariant();
                return ext switch
                {
                    ".mp4" or ".mkv" or ".avi" or ".mov" or ".wmv" or ".webm" => "VIDEO",
                    ".jpg" or ".jpeg" or ".png" or ".gif" or ".webp" or ".bmp" or ".svg" => "IMAGE",
                    ".mp3" or ".flac" or ".wav" or ".aac" or ".ogg" or ".m4a" => "AUDIO",
                    ".zip" or ".rar" or ".7z" or ".tar" or ".gz" or ".iso" => "ARCHIVE",
                    ".pdf" or ".doc" or ".docx" or ".xls" or ".xlsx" or ".ppt" or ".pptx" or ".txt" or ".csv" => "DOCUMENT",
                    ".cs" or ".kt" or ".java" or ".py" or ".cpp" or ".c" or ".js" or ".ts" or ".html" or ".css" or ".json" => "CODE",
                    _ => "FILE"
                };
            }
        }

        [JsonIgnore]
        public string CategoryColor
        {
            get => FileCategory switch
            {
                "VIDEO" => "#EC4899",     // Pink
                "IMAGE" => "#8B5CF6",     // Purple
                "AUDIO" => "#F59E0B",     // Amber
                "ARCHIVE" => "#10B981",   // Emerald
                "DOCUMENT" => "#06B6D4",  // Cyan
                "CODE" => "#3B82F6",      // Blue
                _ => "#64748B"            // Slate
            };
        }

        public static string FormatBytes(long bytes)
        {
            if (bytes < 1024) return $"{bytes} B";
            if (bytes < 1024 * 1024) return $"{bytes / 1024.0:F1} KB";
            if (bytes < 1024 * 1024 * 1024) return $"{bytes / (1024.0 * 1024.0):F2} MB";
            return $"{bytes / (1024.0 * 1024.0 * 1024.0):F2} GB";
        }
    }

    public class ControlMessage
    {
        [JsonPropertyName("action")]
        public string Action { get; set; } = string.Empty;

        [JsonPropertyName("sessionId")]
        public string SessionId { get; set; } = string.Empty;

        [JsonPropertyName("senderDevice")]
        public string SenderDevice { get; set; } = string.Empty;

        [JsonPropertyName("deviceType")]
        public string DeviceType { get; set; } = "WINDOWS";

        [JsonPropertyName("files")]
        public List<FileMetadata>? Files { get; set; }

        [JsonPropertyName("totalSize")]
        public long TotalSize { get; set; }

        [JsonPropertyName("reason")]
        public string? Reason { get; set; }

        [JsonPropertyName("status")]
        public string? Status { get; set; }
    }

    public class TransferProgressReport
    {
        public string SessionId { get; set; } = string.Empty;
        public string CurrentFileName { get; set; } = string.Empty;
        public int CurrentFileIndex { get; set; }
        public int TotalFiles { get; set; }
        public long TotalBytesTransferred { get; set; }
        public long TotalBytes { get; set; }
        public double Percentage { get; set; }
        public double SpeedBytesPerSec { get; set; }
        public TimeSpan EstimatedTimeRemaining { get; set; }

        public string FormattedSpeed
        {
            get
            {
                if (SpeedBytesPerSec < 1024 * 1024)
                    return $"{SpeedBytesPerSec / 1024.0:F1} KB/s";
                return $"{SpeedBytesPerSec / (1024.0 * 1024.0):F1} MB/s";
            }
        }

        public string FormattedBitrate
        {
            get
            {
                double mbps = (SpeedBytesPerSec * 8.0) / (1000.0 * 1000.0);
                if (mbps >= 1000)
                    return $"{mbps / 1000.0:F2} Gbps";
                return $"{mbps:F1} Mbps";
            }
        }

        public string FormattedEta
        {
            get
            {
                if (EstimatedTimeRemaining.TotalSeconds <= 0 || double.IsInfinity(EstimatedTimeRemaining.TotalSeconds))
                    return "Calculating...";
                if (EstimatedTimeRemaining.TotalHours >= 1)
                    return $"{(int)EstimatedTimeRemaining.TotalHours}h {EstimatedTimeRemaining.Minutes}m";
                if (EstimatedTimeRemaining.TotalMinutes >= 1)
                    return $"{EstimatedTimeRemaining.Minutes}m {EstimatedTimeRemaining.Seconds}s";
                return $"{EstimatedTimeRemaining.Seconds}s";
            }
        }
    }
}
