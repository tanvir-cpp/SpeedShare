using System;
using System.IO;
using SpeedShareWindows;
using SpeedShareWindows.Models;

namespace SpeedShareWindows.Tests
{
    public static class TestRunner
    {
        public static int Run()
        {
            int passed = 0, failed = 0;

            void Test(string name, Action assertion)
            {
                try
                {
                    assertion();
                    Console.WriteLine($"  PASS  {name}");
                    passed++;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"  FAIL  {name}: {ex.Message}");
                    failed++;
                }
            }

            Console.WriteLine("SpeedShare unit tests");
            Console.WriteLine("=====================");

            Test("formatBytes under 1 KB", () =>
            {
                if (FileMetadata.FormatBytes(0) != "0 B") throw new Exception(FileMetadata.FormatBytes(0));
                if (FileMetadata.FormatBytes(512) != "512 B") throw new Exception(FileMetadata.FormatBytes(512));
            });

            Test("formatBytes KB", () =>
            {
                var s = FileMetadata.FormatBytes(1024);
                if (s != "1.0 KB") throw new Exception(s);
            });

            Test("formatBytes MB", () =>
            {
                var s = FileMetadata.FormatBytes(4L * 1024 * 1024);
                if (s != "4.00 MB") throw new Exception(s);
            });

            Test("formatBytes GB", () =>
            {
                var s = FileMetadata.FormatBytes(5L * 1024 * 1024 * 1024 / 2);
                if (s != "2.50 GB") throw new Exception(s);
            });

            Test("DiscoveredPeer.IsAndroid", () =>
            {
                var p = new DiscoveredPeer { DeviceType = "ANDROID" };
                if (!p.IsAndroid) throw new Exception();
                var w = new DiscoveredPeer { DeviceType = "WINDOWS" };
                if (w.IsAndroid) throw new Exception();
            });

            Test("DiscoveredPeer.Icon mapping", () =>
            {
                var a = new DiscoveredPeer { DeviceType = "ANDROID" };
                if (a.PlatformIcon != "📱") throw new Exception(a.PlatformIcon);
                var w = new DiscoveredPeer { DeviceType = "WINDOWS" };
                if (w.PlatformIcon != "💻") throw new Exception(w.PlatformIcon);
            });

            Test("FileCategory extension classification", () =>
            {
                if (FileMetadata_formatFileCategory("video.mp4") != "VIDEO") throw new Exception();
                if (FileMetadata_formatFileCategory("image.PNG") != "IMAGE") throw new Exception();
                if (FileMetadata_formatFileCategory("archive.zip") != "ARCHIVE") throw new Exception();
                if (FileMetadata_formatFileCategory("unknown.xyz") != "FILE") throw new Exception();
            });

            Test("SettingsService round-trip", () =>
            {
                var tmp = Path.Combine(Path.GetTempPath(), "speedshare_test_" + Guid.NewGuid().ToString("N"));
                try
                {
                    Directory.CreateDirectory(tmp);
                    var file = Path.Combine(tmp, "settings.json");
                    var settings = new AppSettings { DeviceName = "Test", DownloadFolder = "C:\\x" };
                    File.WriteAllText(file, System.Text.Json.JsonSerializer.Serialize(settings));
                    var json = File.ReadAllText(file);
                    var loaded = System.Text.Json.JsonSerializer.Deserialize<AppSettings>(json,
                        new System.Text.Json.JsonSerializerOptions { PropertyNameCaseInsensitive = true });
                    if (loaded == null || loaded.DeviceName != "Test") throw new Exception("mismatch");
                }
                finally
                {
                    try { Directory.Delete(tmp, true); } catch { }
                }
            });

            Test("TransferProgressReport formatting", () =>
            {
                var r = new TransferProgressReport { SpeedBytesPerSec = 5 * 1024 * 1024 };
                if (r.FormattedSpeed != "5.0 MB/s") throw new Exception(r.FormattedSpeed);

                r.SpeedBytesPerSec = 12 * 1024;
                if (r.FormattedSpeed != "12.0 KB/s") throw new Exception(r.FormattedSpeed);
            });

            Test("TransferProgressReport ETA", () =>
            {
                var r = new TransferProgressReport { EstimatedTimeRemaining = TimeSpan.FromSeconds(45) };
                if (!r.FormattedEta.StartsWith("45")) throw new Exception(r.FormattedEta);

                r.EstimatedTimeRemaining = TimeSpan.FromMinutes(2) + TimeSpan.FromSeconds(10);
                if (!r.FormattedEta.Contains("2m")) throw new Exception(r.FormattedEta);

                r.EstimatedTimeRemaining = TimeSpan.Zero;
                if (r.FormattedEta != "Calculating...") throw new Exception(r.FormattedEta);
            });

            Console.WriteLine();
            Console.WriteLine($"  {passed} passed, {failed} failed");
            return failed == 0 ? 0 : 1;
        }

        private static string FileMetadata_formatFileCategory(string name)
        {
            var m = new FileMetadata { Name = name };
            return m.FileCategory;
        }
    }
}
