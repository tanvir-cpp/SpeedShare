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
        public const string AppVersion = "1.0.0";
        public const string Publisher = "SpeedShare Team";
        public const string RegistryKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\SpeedShare";

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
            string[] embeddedFiles = new[]
            {
                "Payload.SpeedShareWindows.exe",
                "Payload.SpeedShareWindows.dll",
                "Payload.SpeedShareWindows.runtimeconfig.json",
                "Payload.SpeedShareWindows.deps.json"
            };

            foreach (var resourceName in embeddedFiles)
            {
                using var stream = assembly.GetManifestResourceStream(resourceName);
                if (stream != null)
                {
                    string targetFileName = resourceName.Replace("Payload.", "");
                    string targetFilePath = Path.Combine(targetDir, targetFileName);
                    using var fileStream = new FileStream(targetFilePath, FileMode.Create, FileAccess.Write);
                    stream.CopyTo(fileStream);
                }
            }

            // Copy self as Uninstall.exe into the target directory
            string currentExe = Process.GetCurrentProcess().MainModule?.FileName ?? "";
            if (File.Exists(currentExe))
            {
                string uninstallerPath = Path.Combine(targetDir, "Uninstall.exe");
                File.Copy(currentExe, uninstallerPath, true);
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
                // Run a detached background cmd to delete directory after this uninstaller exits
                string cmd = $"/c timeout /t 1 /nobreak > nul & rd /s /q \"{targetDir}\"";
                var psi = new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = cmd,
                    WindowStyle = ProcessWindowStyle.Hidden,
                    CreateNoWindow = true,
                    UseShellExecute = false
                };
                Process.Start(psi);
            }
            catch { }
        }
    }
}
