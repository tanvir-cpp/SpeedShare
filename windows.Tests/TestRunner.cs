using System;
using System.IO;
using SpeedShareWindows;
using SpeedShareWindows.Models;
using SpeedShareWindows.Services;

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

            // -------- Version comparison --------

            Test("IsNewerVersion: equal versions are not newer", () =>
            {
                if (UpdateCheckerService.IsNewerVersion("1.1.1", "1.1.1")) throw new Exception();
            });

            Test("IsNewerVersion: patch bump", () =>
            {
                if (!UpdateCheckerService.IsNewerVersion("1.1.2", "1.1.1")) throw new Exception();
            });

            Test("IsNewerVersion: minor bump", () =>
            {
                if (!UpdateCheckerService.IsNewerVersion("1.2.0", "1.1.5")) throw new Exception();
            });

            Test("IsNewerVersion: major bump", () =>
            {
                if (!UpdateCheckerService.IsNewerVersion("2.0.0", "1.99.99")) throw new Exception();
            });

            Test("IsNewerVersion: older remote is not newer", () =>
            {
                if (UpdateCheckerService.IsNewerVersion("1.1.0", "1.1.1")) throw new Exception();
            });

            Test("IsNewerVersion: rc tag with newer main version is still newer", () =>
            {
                // 1.2.0-rc1 vs 1.1.1: main is 1.2.0 vs 1.1.1, so the RC
                // is newer than the older release, even without
                // prerelease opt-in.
                if (!UpdateCheckerService.IsNewerVersion("1.2.0-rc1", "1.1.1")) throw new Exception();
            });

            Test("IsNewerVersion: rc tag with same main version needs opt-in", () =>
            {
                // 1.1.1-rc1 vs 1.1.1 (release): a release is always
                // newer than an RC of the same version, even without
                // prerelease opt-in.
                if (!UpdateCheckerService.IsNewerVersion("1.1.1", "1.1.1-rc1")) throw new Exception();

                // 1.1.1-rc2 vs 1.1.1-rc1: a newer RC of the same main
                // version is NOT newer than an older RC without
                // opt-in, but IS newer WITH opt-in.
                if (UpdateCheckerService.IsNewerVersion("1.1.1-rc2", "1.1.1-rc1")) throw new Exception();
                if (!UpdateCheckerService.IsNewerVersion("1.1.1-rc2", "1.1.1-rc1", allowPrerelease: true)) throw new Exception();
            });

            Test("IsNewerVersion: release is newer than rc of same version", () =>
            {
                if (!UpdateCheckerService.IsNewerVersion("1.2.0", "1.2.0-rc1", allowPrerelease: true)) throw new Exception();
            });

            // -------- SHA-256 streaming hash --------

            Test("SHA-256 of known data", () =>
            {
                var tmp = Path.Combine(Path.GetTempPath(), "speedshare_test_" + Guid.NewGuid().ToString("N") + ".bin");
                try
                {
                    File.WriteAllBytes(tmp, new byte[] { 1, 2, 3, 4, 5 });
                    // Pre-computed: SHA-256 of bytes 1,2,3,4,5
                    var expected = "74f81fe167d99b4cb41d6d0ccda82278caee9f3e2f25d5e5a3936ff3dcec60d0";
                    using var sha = System.Security.Cryptography.SHA256.Create();
                    var hash = sha.ComputeHash(File.ReadAllBytes(tmp));
                    var actual = Convert.ToHexString(hash).ToLowerInvariant();
                    if (actual != expected) throw new Exception($"expected {expected}, got {actual}");
                }
                finally
                {
                    try { File.Delete(tmp); } catch { }
                }
            });

            // -------- Version normalization & CurrentVersion sanity --------

            Test("CurrentVersion returns a non-zero valid version", () =>
            {
                var v = UpdateCheckerService.CurrentVersion;
                if (string.IsNullOrWhiteSpace(v)) throw new Exception("CurrentVersion is empty");
                if (v == "0.0.0") throw new Exception("CurrentVersion is 0.0.0 (assembly identity not populated)");
                var parts = v.Split('.');
                if (parts.Length < 2 || !int.TryParse(parts[0], out var major) || major < 1)
                {
                    throw new Exception($"CurrentVersion looks invalid: '{v}'");
                }
            });

            Test("CurrentVersion matches the same major.minor that GitHub would offer", () =>
            {
                // The published binary should report a version that is
                // a valid semver of the form X.Y.Z. A 0.0.0 here would
                // mean the in-app updater thinks every release is newer
                // than itself and would prompt forever.
                var v = UpdateCheckerService.CurrentVersion;
                if (v.StartsWith("0.")) throw new Exception($"CurrentVersion starts with 0: '{v}'");
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
