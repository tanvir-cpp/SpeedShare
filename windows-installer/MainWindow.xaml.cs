using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using Microsoft.Win32;

namespace SpeedShareSetup
{
    public partial class MainWindow : Window
    {
        private readonly bool _isUninstallMode;
        private readonly bool _isSilent;
        private string _targetDir;
        private bool _isFinished = false;

        public MainWindow()
        {
            InitializeComponent();

            string[] args = Environment.GetCommandLineArgs();
            _isUninstallMode = args.Any(a => a.Equals("--uninstall", StringComparison.OrdinalIgnoreCase) || a.Equals("/uninstall", StringComparison.OrdinalIgnoreCase));
            _isSilent = args.Any(a => a.Equals("--silent", StringComparison.OrdinalIgnoreCase) || a.Equals("/silent", StringComparison.OrdinalIgnoreCase));

            _targetDir = InstallerEngine.DefaultInstallPath;
            TxtInstallPath.Text = _targetDir;

            if (_isUninstallMode)
            {
                Title = "Uninstall SpeedShare";
                TxtHeaderTitle.Text = "Uninstall SpeedShare";
                TxtHeaderSubtitle.Text = "Remove SpeedShare from this computer";
                BtnMainAction.Content = "Uninstall";

                InstallConfigPanel.Visibility = Visibility.Collapsed;
                UninstallPanel.Visibility = Visibility.Visible;
            }

            Loaded += MainWindow_Loaded;
        }

        private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            if (_isSilent)
            {
                Hide();
                if (_isUninstallMode)
                {
                    await PerformUninstallAsync();
                }
                else
                {
                    await PerformInstallAsync();
                }
                Application.Current.Shutdown();
            }
        }

        private void BtnBrowse_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new OpenFolderDialog
            {
                Title = "Select Destination Folder"
            };

            if (dialog.ShowDialog() == true)
            {
                _targetDir = Path.Combine(dialog.FolderName, "SpeedShare");
                TxtInstallPath.Text = _targetDir;
            }
        }

        private async void BtnMainAction_Click(object sender, RoutedEventArgs e)
        {
            if (_isFinished)
            {
                if (!_isUninstallMode && ChkLaunchOnFinish.IsChecked == true)
                {
                    string mainExe = Path.Combine(_targetDir, "SpeedShareWindows.exe");
                    if (File.Exists(mainExe))
                    {
                        Process.Start(new ProcessStartInfo
                        {
                            FileName = mainExe,
                            UseShellExecute = true
                        });
                    }
                }
                Application.Current.Shutdown();
                return;
            }

            if (_isUninstallMode)
            {
                await PerformUninstallAsync();
            }
            else
            {
                await PerformInstallAsync();
            }
        }

        private async Task PerformInstallAsync()
        {
            InstallConfigPanel.Visibility = Visibility.Collapsed;
            ProgressPanel.Visibility = Visibility.Visible;
            BtnMainAction.IsEnabled = false;

            TxtProgressStatus.Text = "Closing existing instances...";
            await Task.Run(() => InstallerEngine.KillRunningProcesses());
            await Task.Delay(300);

            TxtProgressStatus.Text = "Extracting application files...";
            await Task.Run(() => InstallerEngine.ExtractEmbeddedPayload(_targetDir));
            await Task.Delay(300);

            TxtProgressStatus.Text = "Creating shortcuts...";
            string mainExe = Path.Combine(_targetDir, "SpeedShareWindows.exe");
            await Task.Run(() =>
            {
                if (ChkDesktopShortcut.IsChecked == true)
                {
                    InstallerEngine.CreateShortcut(mainExe, InstallerEngine.DesktopShortcutPath, "SpeedShare - Ultra Fast Local Transfer");
                }
                if (ChkStartMenuShortcut.IsChecked == true)
                {
                    InstallerEngine.CreateShortcut(mainExe, InstallerEngine.StartMenuShortcutPath, "SpeedShare - Ultra Fast Local Transfer");
                }
            });
            await Task.Delay(200);

            TxtProgressStatus.Text = "Registering in Windows...";
            await Task.Run(() => InstallerEngine.RegisterInRegistry(_targetDir));
            await Task.Delay(200);

            ProgressPanel.Visibility = Visibility.Collapsed;
            FinishedPanel.Visibility = Visibility.Visible;
            _isFinished = true;
            BtnMainAction.IsEnabled = true;
            BtnMainAction.Content = ChkLaunchOnFinish.IsChecked == true ? "Launch SpeedShare" : "Finish";
        }

        private async Task PerformUninstallAsync()
        {
            UninstallPanel.Visibility = Visibility.Collapsed;
            ProgressPanel.Visibility = Visibility.Visible;
            BtnMainAction.IsEnabled = false;

            TxtProgressStatus.Text = "Closing SpeedShare processes...";
            await Task.Run(() => InstallerEngine.KillRunningProcesses());
            await Task.Delay(400);

            TxtProgressStatus.Text = "Removing shortcuts...";
            await Task.Run(() => InstallerEngine.RemoveShortcuts());
            await Task.Delay(300);

            TxtProgressStatus.Text = "Unregistering application from Windows...";
            await Task.Run(() => InstallerEngine.UnregisterFromRegistry());
            await Task.Delay(300);

            TxtProgressStatus.Text = "Cleaning up installation files...";
            string currentExeDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            await Task.Run(() => InstallerEngine.ScheduleDirectoryDeletion(currentExeDir));
            await Task.Delay(300);

            ProgressPanel.Visibility = Visibility.Collapsed;
            FinishedPanel.Visibility = Visibility.Visible;
            TxtFinishedTitle.Text = "SpeedShare Uninstalled";
            TxtFinishedSubtitle.Text = "SpeedShare has been completely removed from your PC.";
            _isFinished = true;
            BtnMainAction.IsEnabled = true;
            BtnMainAction.Content = "Close";
        }
    }
}