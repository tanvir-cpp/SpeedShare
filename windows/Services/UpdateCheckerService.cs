using System;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Net.Http.Headers;
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
    }

    public class UpdateCheckerService
    {
        public static readonly string CurrentVersion = GetCurrentVersion();
        public const string GitHubApiUrl = "https://api.github.com/repos/tanvir-cpp/SpeedShare/releases/latest";
        public const string ReleasesWebUrl = "https://github.com/tanvir-cpp/SpeedShare/releases/latest";

        private static string GetCurrentVersion()
        {
            try
            {
                var v = typeof(UpdateCheckerService).Assembly.GetName().Version;
                if (v != null && (v.Major > 0 || v.Minor > 0 || v.Build > 0))
                {
                    return $"{v.Major}.{v.Minor}.{v.Build}";
                }
            }
            catch { }
            return "0.0.0";
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

        public static async Task<UpdateInfo?> CheckForUpdatesAsync()
        {
            try
            {
                var response = await _httpClient.GetAsync(GitHubApiUrl);
                if (!response.IsSuccessStatusCode)
                {
                    return null;
                }

                var json = await response.Content.ReadAsStringAsync();
                var release = JsonSerializer.Deserialize<GitHubReleaseResponse>(json);
                if (release == null || string.IsNullOrWhiteSpace(release.TagName))
                {
                    return null;
                }

                var cleanTag = release.TagName.TrimStart('v', 'V');
                if (IsNewerVersion(cleanTag, CurrentVersion))
                {
                    string? installerUrl = null;
                    long installerSize = 0;

                    if (release.Assets != null)
                    {
                        // First pass: prefer SpeedShare-Setup.exe
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
                        // Second pass: any .exe
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
                    }

                    return new UpdateInfo
                    {
                        VersionTag = release.TagName,
                        CleanVersion = cleanTag,
                        Title = string.IsNullOrWhiteSpace(release.Name) ? $"SpeedShare {release.TagName}" : release.Name,
                        Changelog = release.Body ?? "A new update is available on GitHub with performance improvements and bug fixes.",
                        ReleaseUrl = release.HtmlUrl ?? ReleasesWebUrl,
                        InstallerDownloadUrl = installerUrl,
                        InstallerSize = installerSize
                    };
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[UpdateChecker] Error checking GitHub: {ex.Message}");
            }

            return null;
        }

        public static bool IsNewerVersion(string remoteVer, string localVer)
        {
            if (Version.TryParse(remoteVer, out var remote) && Version.TryParse(localVer, out var local))
            {
                return remote > local;
            }

            // Fallback string compare
            return string.Compare(remoteVer, localVer, StringComparison.OrdinalIgnoreCase) > 0;
        }

        public static async Task<string> DownloadInstallerAsync(string downloadUrl, IProgress<double>? progress = null, CancellationToken ct = default)
        {
            var tempPath = Path.Combine(Path.GetTempPath(), "SpeedShare-Setup-Update.exe");

            // Follow redirects manually up to 5 hops
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

            var totalBytes = response.Content.Headers.ContentLength ?? -1L;
            await using var contentStream = await response.Content.ReadAsStreamAsync(ct);
            await using var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);

            var buffer = new byte[81920];
            long totalRead = 0;
            int bytesRead;

            while ((bytesRead = await contentStream.ReadAsync(buffer, ct)) > 0)
            {
                await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), ct);
                totalRead += bytesRead;

                if (totalBytes > 0 && progress != null)
                {
                    progress.Report((double)totalRead / totalBytes * 100.0);
                }
            }

            return tempPath;
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

            // Schedule shutdown on the UI thread if we can; otherwise just exit.
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
