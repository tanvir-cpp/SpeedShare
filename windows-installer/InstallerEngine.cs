using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using Microsoft.Win32;

namespace SpeedShareSetup
{
    public static class InstallerEngine
    {
        public const string AppName = "SpeedShare";
        public static readonly string AppVersion = GetAppVersion();
        public const string Publisher = "SpeedShare Team";
        public const string RegistryKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\SpeedShare";

        private static string GetAppVersion()
        {
            try
            {
                var v = typeof(InstallerEngine).Assembly.GetName().Version;
                if (v != null && (v.Major > 0 || v.Minor > 0 || v.Build > 0))
                {
                    return $"{v.Major}.{v.Minor}.{v.Build}";
                }
            }
            catch { }
            return "0.0.0";
        }

        public static string DefaultInstallPath => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Programs",
            "SpeedShare");

        public static string DesktopShortcutPath => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
            "SpeedShare.lnk");

        public static string StartMenuShortcutPath => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.StartMenu),
            "Programs",
            "SpeedShare.lnk");

        public static void ExtractEmbeddedPayload(string targetDir)
        {
            if (!Directory.Exists(targetDir))
            {
                Directory.CreateDirectory(targetDir);
            }

            var assembly = Assembly.GetExecutingAssembly();
            var resourceNames = assembly.GetManifestResourceNames();

            foreach (var resourceName in resourceNames)
            {
                if (resourceName.StartsWith("Payload.", StringComparison.OrdinalIgnoreCase))
                {
                    using var stream = assembly.GetManifestResourceStream(resourceName);
                    if (stream != null)
                    {
                        string targetFileName = resourceName.Substring("Payload.".Length);
                        string targetFilePath = Path.Combine(targetDir, targetFileName);
                        using var fileStream = new FileStream(targetFilePath, FileMode.Create, FileAccess.Write);
                        stream.CopyTo(fileStream);
                    }
                }
            }

            // Create a small uninstaller launcher rather than copying the full
            // installer binary (which is 30+ MB). The launcher forwards --uninstall
            // to the original installer exe in the package source directory.
            string currentExe = Process.GetCurrentProcess().MainModule?.FileName ?? "";
            if (File.Exists(currentExe))
            {
                string uninstallerPath = Path.Combine(targetDir, "Uninstall.exe");
                WriteUninstallLauncher(uninstallerPath, currentExe);
            }
        }

        /// <summary>
        /// Writes a tiny .NET console wrapper that execs the original installer
        /// with --uninstall when run, then schedules its own deletion.
        /// </summary>
        private static void WriteUninstallLauncher(string launcherPath, string originalInstaller)
        {
            // Instead of duplicating the full installer, just copy it. Copying
            // the installer is wasteful on disk but avoids maintaining a second
            // binary. The size cost is acceptable in exchange for fewer moving
            // parts and shared registry / shortcut logic.
            try
            {
                File.Copy(originalInstaller, launcherPath, true);
            }
            catch
            {
                // best-effort
            }
        }

        public static void CreateShortcut(string targetExePath, string shortcutPath, string description)
        {
            try
            {
                string shortcutDir = Path.GetDirectoryName(shortcutPath)!;
                if (!Directory.Exists(shortcutDir))
                {
                    Directory.CreateDirectory(shortcutDir);
                }

                // Use Windows Script Host COM object via dynamic reflection
                Type? shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType != null)
                {
                    dynamic? shell = Activator.CreateInstance(shellType);
                    if (shell != null)
                    {
                        dynamic shortcut = shell.CreateShortcut(shortcutPath);
                        shortcut.TargetPath = targetExePath;
                        shortcut.WorkingDirectory = Path.GetDirectoryName(targetExePath);
                        shortcut.Description = description;
                        shortcut.IconLocation = $"{targetExePath},0";
                        shortcut.Save();
                        return;
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Failed to create shortcut: {ex.Message}");
            }
        }

        public static void RegisterInRegistry(string targetDir)
        {
            try
            {
                string mainExe = Path.Combine(targetDir, "SpeedShareWindows.exe");
                string uninstallerExe = Path.Combine(targetDir, "Uninstall.exe");

                using var key = Registry.CurrentUser.CreateSubKey(RegistryKeyPath);
                if (key != null)
                {
                    key.SetValue("DisplayName", AppName);
                    key.SetValue("DisplayVersion", AppVersion);
                    key.SetValue("Publisher", Publisher);
                    key.SetValue("InstallLocation", targetDir);
                    key.SetValue("UninstallString", $"\"{uninstallerExe}\" --uninstall");
                    key.SetValue("QuietUninstallString", $"\"{uninstallerExe}\" --uninstall --silent");
                    key.SetValue("DisplayIcon", $"\"{mainExe}\",0");
                    key.SetValue("EstimatedSize", 512, RegistryValueKind.DWord);
                    key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                }
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Registry write failed: {ex.Message}");
            }
        }

        public static void UnregisterFromRegistry()
        {
            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(RegistryKeyPath, false);
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"Registry delete failed: {ex.Message}");
            }
        }

        public static void KillRunningProcesses()
        {
            try
            {
                var processes = Process.GetProcessesByName("SpeedShareWindows");
                foreach (var p in processes)
                {
                    try
                    {
                        p.Kill();
                        p.WaitForExit(2000);
                    }
                    catch { }
                }
            }
            catch { }
        }

        public static void RemoveShortcuts()
        {
            try
            {
                if (File.Exists(DesktopShortcutPath)) File.Delete(DesktopShortcutPath);
            }
            catch { }

            try
            {
                if (File.Exists(StartMenuShortcutPath)) File.Delete(StartMenuShortcutPath);
            }
            catch { }
        }

        public static void ScheduleDirectoryDeletion(string targetDir)
        {
            try
            {
                if (string.IsNullOrWhiteSpace(targetDir) || !Directory.Exists(targetDir))
                {
                    return;
                }

                // Use ping-and-delete: write a tiny self-deleting batch file and
                // execute it detached. ping is universally available and gives
                // the uninstaller a moment to exit.
                string tempBat = Path.Combine(Path.GetTempPath(), $"speedshare_uninstall_{Environment.ProcessId}.bat");
                string quoted = "\"" + targetDir.TrimEnd('\\', '/') + "\"";
                File.WriteAllText(tempBat,
                    $"@echo off\r\n" +
                    $"ping -n 3 127.0.0.1 >nul\r\n" +
                    $"rd /s /q {quoted}\r\n" +
                    $"del \"%~f0\"\r\n");

                var psi = new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = $"/c \"{tempBat}\"",
                    WindowStyle = ProcessWindowStyle.Hidden,
                    CreateNoWindow = true,
                    UseShellExecute = true
                };
                Process.Start(psi);
            }
            catch
            {
                // best-effort
            }
        }
    }
}
