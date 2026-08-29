using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace SpeedShareWindows.Services
{
    public class UpdateInfo
    {
        public string VersionTag { get; set; } = string.Empty;
        public string CleanVersion { get; set; } = string.Empty;
        public string Title { get; set; } = string.Empty;
        public string Changelog { get; set; } = string.Empty;
        public string ReleaseUrl { get; set; } = string.Empty;
        public string? InstallerDownloadUrl { get; set; }
        public long InstallerSize { get; set; }
        public string? Sha256 { get; set; }
        public bool IsPrerelease { get; set; }
    }

    public enum UpdateCheckResult
    {
        UpdateAvailable,
        NoUpdate,
        Failed
    }

    public sealed class UpdateCheckOutcome
    {
        public UpdateCheckResult Result { get; init; }
        public UpdateInfo? Update { get; init; }
        public string? Error { get; init; }
    }

    public static class UpdateCheckerService
    {
        public static readonly string CurrentVersion = GetCurrentVersion();
        public const string GitHubApiUrl = "https://api.github.com/repos/tanvir-cpp/SpeedShare/releases/latest";
        public const string ReleasesWebUrl = "https://github.com/tanvir-cpp/SpeedShare/releases/latest";

        /// <summary>
        /// Determine the current app version at runtime. Tries four
        /// sources in order of reliability:
        ///   1. InformationalVersion (set from &lt;Version&gt; in Directory.Build.props)
        ///   2. FileVersion from the Win32 VERSIONINFO resource
        ///   3. AssemblyVersion (set from &lt;AssemblyVersion&gt;)
        ///   4. Entry-process executable path's FileVersion
        /// Single-file published .NET apps can occasionally report
        /// 0.0.0.0 via Assembly.GetName() because the bundle is loaded
        /// from a memory stream rather than a file. Falling through to
        /// the file system version avoids the "always thinks there's an
        /// update" bug.
        /// </summary>
        private static string GetCurrentVersion()
        {
            var assembly = typeof(UpdateCheckerService).Assembly;
            try
            {
                var info = assembly.GetCustomAttribute<System.Reflection.AssemblyInformationalVersionAttribute>();
                if (info != null && !string.IsNullOrWhiteSpace(info.InformationalVersion))
                {
                    var v = info.InformationalVersion.Split('+')[0].Trim(); // strip commit hash if present
                    if (IsValidVersion(v)) return NormalizeVersion(v);
                }
            }
            catch { }
            try
            {
                // For single-file apps, Assembly.Location is empty.
                // We try it anyway because in framework-dependent builds
                // it does return the dll path, which has the version.
#pragma warning disable IL3000
                var location = assembly.Location;
#pragma warning restore IL3000
                if (!string.IsNullOrEmpty(location) && System.IO.File.Exists(location))
                {
                    var fvi = System.Diagnostics.FileVersionInfo.GetVersionInfo(location);
                    if (IsValidVersion(fvi.FileVersion) && fvi.FileVersion != null)
                        return NormalizeVersion(fvi.FileVersion);
                }
            }
            catch { }
            try
            {
                var v = assembly.GetName().Version;
                if (v != null && (v.Major > 0 || v.Minor > 0 || v.Build > 0))
                {
                    return $"{v.Major}.{v.Minor}.{v.Build}";
                }
            }
            catch { }
            // Last resort: read the entry process's exe version
            try
            {
                var entry = System.Diagnostics.Process.GetCurrentProcess().MainModule?.FileName;
                if (!string.IsNullOrEmpty(entry) && System.IO.File.Exists(entry))
                {
                    var fvi = System.Diagnostics.FileVersionInfo.GetVersionInfo(entry);
                    if (IsValidVersion(fvi.FileVersion) && fvi.FileVersion != null)
                        return NormalizeVersion(fvi.FileVersion);
                }
            }
            catch { }
            return "0.0.0";
        }

        private static bool IsValidVersion(string? v)
        {
            if (string.IsNullOrWhiteSpace(v)) return false;
            var trimmed = v.Trim();
            if (trimmed == "0.0.0.0" || trimmed == "0.0.0") return false;
            // Must contain at least one digit and a dot
            return trimmed.Contains('.') && trimmed.Any(char.IsDigit);
        }

        private static string NormalizeVersion(string v)
        {
            if (string.IsNullOrEmpty(v)) return "0.0.0";
            // Strip any suffix like "-rc1" or "+abc123" — the in-app
            // version comparison only cares about the numeric part.
            var t = v.Trim();
            var plus = t.IndexOf('+');
            if (plus > 0) t = t.Substring(0, plus);
            var dash = t.IndexOf('-');
            if (dash > 0) t = t.Substring(0, dash);
            // Take only the first three numeric parts (Major.Minor.Build)
            var parts = t.Split('.');
            if (parts.Length >= 3) return $"{parts[0]}.{parts[1]}.{parts[2]}";
            return t;
        }

        private static readonly HttpClient _httpClient = new()
        {
            Timeout = TimeSpan.FromSeconds(15)
        };

        static UpdateCheckerService()
        {
            _httpClient.DefaultRequestHeaders.UserAgent.Add(
                new ProductInfoHeaderValue("SpeedShare-Windows", CurrentVersion));
            _httpClient.DefaultRequestHeaders.Accept.Add(
                new MediaTypeWithQualityHeaderValue("application/vnd.github.v3+json"));
        }

        /// <summary>
        /// Compare two semver-ish version strings. Prerelease tags
        /// ("1.2.0-rc1") are NOT considered newer than the same release
        /// version unless <paramref name="allowPrerelease"/> is true.
        /// </summary>
        public static bool IsNewerVersion(string remoteVer, string localVer, bool allowPrerelease = false)
        {
            if (string.Equals(remoteVer, localVer, StringComparison.OrdinalIgnoreCase)) return false;
            var (rMain, rPre) = SplitPrerelease(remoteVer);
            var (lMain, lPre) = SplitPrerelease(localVer);
            var cmp = CompareMain(rMain, lMain);
            if (cmp != 0) return cmp > 0;
            if (rPre == null && lPre == null) return false;
            if (rPre == null) return true;
            if (lPre == null) return false;
            if (!allowPrerelease) return false;
            return string.Compare(rPre, lPre, StringComparison.OrdinalIgnoreCase) > 0;
        }

        private static (string Main, string? Pre) SplitPrerelease(string v)
        {
            var dash = v.IndexOf('-');
            return dash < 0 ? (v, null) : (v.Substring(0, dash), v.Substring(dash + 1));
        }

        private static int CompareMain(string a, string b)
        {
            var ap = a.Split('.');
            var bp = b.Split('.');
            int len = Math.Max(ap.Length, bp.Length);
            for (int i = 0; i < len; i++)
            {
                int x = i < ap.Length ? (int.TryParse(ap[i], out var xi) ? xi : 0) : 0;
                int y = i < bp.Length ? (int.TryParse(bp[i], out var yi) ? yi : 0) : 0;
                if (x != y) return x - y;
            }
            return 0;
        }

        /// <summary>
        /// Check the latest GitHub release. Never throws; returns an
        /// UpdateCheckOutcome instead so the UI can render a useful
        /// message instead of swallowing exceptions.
        /// </summary>
        public static async Task<UpdateCheckOutcome> CheckForUpdatesAsync()
        {
            try
            {
                var response = await _httpClient.GetAsync(GitHubApiUrl);
                if (!response.IsSuccessStatusCode)
                {
                    return new UpdateCheckOutcome
                    {
                        Result = UpdateCheckResult.Failed,
                        Error = $"GitHub returned HTTP {(int)response.StatusCode}"
                    };
                }
                var json = await response.Content.ReadAsStringAsync();
                var release = JsonSerializer.Deserialize<GitHubReleaseResponse>(json);
                if (release == null || string.IsNullOrWhiteSpace(release.TagName))
                {
                    return new UpdateCheckOutcome { Result = UpdateCheckResult.NoUpdate };
                }

                var isPrerelease = release.Prerelease;
                var cleanTag = release.TagName.TrimStart('v', 'V');
                if (!IsNewerVersion(cleanTag, CurrentVersion, allowPrerelease: false) ||
                    (isPrerelease && !IsNewerVersion(cleanTag, CurrentVersion, allowPrerelease: true)))
                {
                    return new UpdateCheckOutcome { Result = UpdateCheckResult.NoUpdate };
                }

                string? installerUrl = null;
                long installerSize = 0;
                string? sha256Url = null;

                if (release.Assets != null)
                {
                    // First pass: pick the .exe (preferring *-Setup.exe).
                    foreach (var asset in release.Assets)
                    {
                        if (asset.Name != null &&
                            asset.Name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) &&
                            asset.Name.Contains("Setup", StringComparison.OrdinalIgnoreCase))
                        {
                            installerUrl = asset.BrowserDownloadUrl;
                            installerSize = asset.Size;
                            break;
                        }
                    }
                    if (installerUrl == null)
                    {
                        foreach (var asset in release.Assets)
                        {
                            if (asset.Name != null && asset.Name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                            {
                                installerUrl = asset.BrowserDownloadUrl;
                                installerSize = asset.Size;
                                break;
                            }
                        }
                    }
                    // Second pass: pick a sidecar .sha256 for the chosen asset.
                    if (installerUrl != null)
                    {
                        var installerName = installerUrl.Substring(installerUrl.LastIndexOf('/') + 1);
                        foreach (var asset in release.Assets)
                        {
                            if (asset.Name != null &&
                                asset.Name.Equals(installerName + ".sha256", StringComparison.OrdinalIgnoreCase))
                            {
                                sha256Url = asset.BrowserDownloadUrl;
                                break;
                            }
                        }
                    }
                }

                string? expectedSha256 = null;
                if (sha256Url != null)
                {
                    try
                    {
                        var sha = await _httpClient.GetStringAsync(sha256Url);
                        expectedSha256 = sha.Trim().Split(new[] { ' ', '\t', '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries).FirstOrDefault();
                    }
                    catch { /* hash verification is best-effort */ }
                }

                return new UpdateCheckOutcome
                {
                    Result = UpdateCheckResult.UpdateAvailable,
                    Update = new UpdateInfo
                    {
                        VersionTag = release.TagName,
                        CleanVersion = cleanTag,
                        Title = string.IsNullOrWhiteSpace(release.Name) ? $"SpeedShare {release.TagName}" : release.Name,
                        Changelog = release.Body ?? "A new update is available on GitHub with performance improvements and bug fixes.",
                        ReleaseUrl = release.HtmlUrl ?? ReleasesWebUrl,
                        InstallerDownloadUrl = installerUrl,
                        InstallerSize = installerSize,
                        Sha256 = expectedSha256,
                        IsPrerelease = isPrerelease
                    }
                };
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[UpdateChecker] Error checking GitHub: {ex.Message}");
                return new UpdateCheckOutcome
                {
                    Result = UpdateCheckResult.Failed,
                    Error = ex.Message
                };
            }
        }

        /// <summary>
        /// Download the installer, verify its size and SHA-256, and
        /// return the path. Throws on any verification failure so
        /// the UI can show an explicit error.
        /// </summary>
        public static async Task<string> DownloadInstallerAsync(
            string downloadUrl,
            long expectedSize,
            string? expectedSha256 = null,
            IProgress<double>? progress = null,
            CancellationToken ct = default)
        {
            var tempPath = Path.Combine(Path.GetTempPath(), "SpeedShare-Setup-Update.exe");

            // Follow up to 5 redirects manually
            string url = downloadUrl;
            HttpResponseMessage? response = null;
            for (int redirect = 0; redirect < 5; redirect++)
            {
                var req = new HttpRequestMessage(HttpMethod.Get, url);
                req.Headers.UserAgent.ParseAdd($"SpeedShare-Windows/{CurrentVersion}");
                response = await _httpClient.SendAsync(req, HttpCompletionOption.ResponseHeadersRead, ct);
                if ((int)response.StatusCode is 301 or 302 or 303 or 307 or 308)
                {
                    var loc = response.Headers.Location?.ToString();
                    response.Dispose();
                    if (string.IsNullOrEmpty(loc)) throw new HttpRequestException("Redirect without Location header");
                    url = loc;
                    continue;
                }
                break;
            }
            if (response == null) throw new HttpRequestException("No response received.");
            response.EnsureSuccessStatusCode();

            // Write to a temp file first, then rename, so a partially-downloaded
            // file from a previous run doesn't pollute the verification step.
            var tempPathInProgress = tempPath + ".part";

            var totalBytes = response.Content.Headers.ContentLength ?? -1L;
            long totalRead = 0;
            using (var sha = SHA256.Create())
            {
                await using (var contentStream = await response.Content.ReadAsStreamAsync(ct))
                await using (var fileStream = new FileStream(tempPathInProgress, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true))
                {
                    var buffer = new byte[81920];
                    int bytesRead;
                    while ((bytesRead = await contentStream.ReadAsync(buffer, ct)) > 0)
                    {
                        sha.TransformBlock(buffer, 0, bytesRead, null, 0);
                        await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), ct);
                        totalRead += bytesRead;
                        if (totalBytes > 0 && progress != null)
                        {
                            progress.Report((double)totalRead / totalBytes * 100.0);
                        }
                    }
                }
                sha.TransformFinalBlock(Array.Empty<byte>(), 0, 0);
                var actualHash = Convert.ToHexString(sha.Hash ?? Array.Empty<byte>()).ToLowerInvariant();

                // Size check
                if (expectedSize > 0 && totalRead != expectedSize)
                {
                    try { File.Delete(tempPathInProgress); } catch { }
                    throw new InvalidDataException(
                        $"Installer size mismatch (expected {expectedSize}, got {totalRead}).");
                }

                // Hash check
                if (!string.IsNullOrEmpty(expectedSha256) &&
                    !string.Equals(actualHash, expectedSha256, StringComparison.OrdinalIgnoreCase))
                {
                    try { File.Delete(tempPathInProgress); } catch { }
                    throw new InvalidDataException(
                        $"Installer SHA-256 mismatch (expected {expectedSha256}, got {actualHash}).");
                }

                if (File.Exists(tempPath)) File.Delete(tempPath);
                File.Move(tempPathInProgress, tempPath);
                return tempPath;
            }
        }

        public static void LaunchInstallerAndExit(string installerPath)
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = installerPath,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[UpdateChecker] Failed to launch installer: {ex.Message}");
                return;
            }
            try
            {
                var app = System.Windows.Application.Current;
                if (app != null)
                {
                    app.Dispatcher.BeginInvoke(new Action(() => app.Shutdown()));
                }
            }
            catch
            {
                // best-effort
            }
        }

        private class GitHubReleaseResponse
        {
            [JsonPropertyName("tag_name")]
            public string? TagName { get; set; }

            [JsonPropertyName("name")]
            public string? Name { get; set; }

            [JsonPropertyName("body")]
            public string? Body { get; set; }

            [JsonPropertyName("html_url")]
            public string? HtmlUrl { get; set; }

            [JsonPropertyName("prerelease")]
            public bool Prerelease { get; set; }

            [JsonPropertyName("assets")]
            public GitHubAsset[]? Assets { get; set; }
        }

        private class GitHubAsset
        {
            [JsonPropertyName("name")]
            public string? Name { get; set; }

            [JsonPropertyName("browser_download_url")]
            public string? BrowserDownloadUrl { get; set; }

            [JsonPropertyName("size")]
            public long Size { get; set; }
        }
    }
}
