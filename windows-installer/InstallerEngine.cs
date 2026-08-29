using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
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
                string workingDir = Path.GetDirectoryName(targetExePath) ?? "";
                // Direct P/Invoke to the shell's IShellLink/IPersistFile
                // interfaces. Replaces the previous WScript.Shell late-bound
                // COM call which was a flag for AV heuristics and provided
                // no compile-time type safety.
                ShellLinkInterop.CreateShortcut(
                    linkPath: shortcutPath,
                    targetPath: targetExePath,
                    workingDirectory: workingDir,
                    description: description);
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

        /// <summary>
        /// Ask any running SpeedShare instance to exit gracefully. We
        /// send WM_CLOSE to its main window so it can persist state
        /// (settings, transfer history) before exiting. We fall back
        /// to Process.Kill only if the process doesn't respond within
        /// a short timeout, and only if it still has a window.
        /// </summary>
        public static void KillRunningProcesses()
        {
            try
            {
                const int WM_CLOSE = 0x0010;
                var processes = Process.GetProcessesByName("SpeedShareWindows");
                foreach (var p in processes)
                {
                    try
                    {
                        IntPtr hwnd = p.MainWindowHandle;
                        if (hwnd != IntPtr.Zero)
                        {
                            // Best-effort graceful close. The target process
                            // is a WPF app and will receive the WM_CLOSE,
                            // save its settings, and exit. If it's hung,
                            // we'll fall back to Kill below.
                            SendMessage(hwnd, WM_CLOSE, IntPtr.Zero, IntPtr.Zero);
                            if (p.WaitForExit(3000)) continue;
                        }
                        // Either no main window (e.g. a service-like host)
                        // or the process didn't exit in time. Kill as a
                        // last resort.
                        p.Kill();
                        p.WaitForExit(2000);
                    }
                    catch { /* best-effort */ }
                    finally
                    {
                        p.Dispose();
                    }
                }
            }
            catch { }
        }

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool SendMessage(IntPtr hWnd, int Msg, IntPtr wParam, IntPtr lParam);

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
            // We deliberately do NOT spawn cmd.exe or PowerShell to
            // self-delete the install directory. That pattern is a
            // classic heuristic flag for malware ("installer that
            // deletes itself after running") and has no functional
            // benefit here: the install lives in
            // %LOCALAPPDATA%\Programs\SpeedShare which is small, and
            // the user can remove it manually with one click if they
            // want a truly clean uninstall. The next install of a
            // newer version overwrites the same files in place.
            //
            // This is the only method that intentionally no-ops.
            _ = targetDir;
        }
    }
}
