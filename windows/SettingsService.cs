using System;
using System.IO;
using System.Text.Json;
using SpeedShareWindows.Services;

namespace SpeedShareWindows
{
    public sealed class AppSettings
    {
        public string? DeviceName { get; set; }
        public string? DownloadFolder { get; set; }
        public string? Theme { get; set; }
    }

    public static class SettingsService
    {
        private static readonly string SettingsPath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "SpeedShare",
            "settings.json");

        public static (string? deviceName, string? downloadFolder, AppTheme theme) Load()
        {
            try
            {
                if (!File.Exists(SettingsPath)) return (null, null, AppTheme.System);
                var json = File.ReadAllText(SettingsPath);
                if (string.IsNullOrWhiteSpace(json)) return (null, null, AppTheme.System);
                var settings = JsonSerializer.Deserialize<AppSettings>(json,
                    new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                if (settings == null) return (null, null, AppTheme.System);

                var theme = AppTheme.System;
                if (Enum.TryParse<AppTheme>(settings.Theme, ignoreCase: true, out var parsed))
                {
                    theme = parsed;
                }
                return (settings.DeviceName, settings.DownloadFolder, theme);
            }
            catch
            {
                return (null, null, AppTheme.System);
            }
        }

        public static void Save(string? deviceName, string? downloadFolder, AppTheme? theme = null)
        {
            try
            {
                var dir = Path.GetDirectoryName(SettingsPath);
                if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                var existing = Load();
                var settings = new AppSettings
                {
                    DeviceName = deviceName ?? existing.deviceName,
                    DownloadFolder = downloadFolder ?? existing.downloadFolder,
                    Theme = theme?.ToString() ?? existing.theme.ToString()
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
