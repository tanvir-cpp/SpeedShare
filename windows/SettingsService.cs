using System;
using System.IO;
using System.Text.Json;

namespace SpeedShareWindows
{
    public sealed class AppSettings
    {
        public string? DeviceName { get; set; }
        public string? DownloadFolder { get; set; }
    }

    public static class SettingsService
    {
        private static readonly string SettingsPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "SpeedShare",
            "settings.json");

        public static (string? deviceName, string? downloadFolder) Load()
        {
            try
            {
                if (!File.Exists(SettingsPath)) return (null, null);
                var json = File.ReadAllText(SettingsPath);
                if (string.IsNullOrWhiteSpace(json)) return (null, null);
                var settings = JsonSerializer.Deserialize<AppSettings>(json,
                    new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                if (settings == null) return (null, null);
                return (settings.DeviceName, settings.DownloadFolder);
            }
            catch
            {
                return (null, null);
            }
        }

        public static void Save(string? deviceName, string? downloadFolder)
        {
            try
            {
                var dir = Path.GetDirectoryName(SettingsPath);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                var settings = new AppSettings
                {
                    DeviceName = deviceName,
                    DownloadFolder = downloadFolder
                };

                var json = JsonSerializer.Serialize(settings, new JsonSerializerOptions
                {
                    WriteIndented = true
                });
                File.WriteAllText(SettingsPath, json);
            }
            catch
            {
                // best-effort; settings are non-critical
            }
        }
    }
}
