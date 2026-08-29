using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using Microsoft.Win32;
using SpeedShareWindows.Models;
using SpeedShareWindows.Network;
using SpeedShareWindows.Services;

namespace SpeedShareWindows
{
    public partial class MainWindow : Window
    {
        private readonly DiscoveryService _discoveryService;
        private readonly TransferServer _transferServer;
        private readonly TransferClient _transferClient;

        private readonly ObservableCollection<DiscoveredPeer> _discoveredPeers = new();
        private readonly ObservableCollection<FileMetadata> _selectedFiles = new();
        private DiscoveredPeer? _selectedPeer;

        private IncomingTransferRequestArgs? _currentIncomingRequest;
        private bool _isTransferActive = false;

        public MainWindow()
        {
            InitializeComponent();

            PeersListBox.ItemsSource = _discoveredPeers;
            FilesListBox.ItemsSource = _selectedFiles;

            _discoveryService = new DiscoveryService();
            _transferServer = new TransferServer();
            _transferClient = new TransferClient();

            // Wire discovery events
            _discoveryService.PeerListChanged += OnPeerListChanged;

            // Wire server (receiving) events
            _transferServer.TransferRequestReceived += OnIncomingTransferRequest;
            _transferServer.ProgressChanged += OnTransferProgress;
            _transferServer.TransferCompleted += OnServerTransferCompleted;

            // Wire client (sending) events
            _transferClient.ProgressChanged += OnTransferProgress;
            _transferClient.TransferCompleted += OnClientTransferCompleted;

            Loaded += MainWindow_Loaded;
            Closing += MainWindow_Closing;
        }

        private UpdateInfo? _availableUpdate;

        private void MainWindow_Loaded(object sender, RoutedEventArgs e)
        {
            string localIp = GetLocalIpAddress();
            LocalInfoText.Text = $"{_discoveryService.DeviceName} • {localIp}";
            TxtCustomDeviceName.Text = _discoveryService.DeviceName;
            TxtDownloadFolder.Text = _transferServer.DownloadFolder;

            _transferServer.Start();
            _discoveryService.Start();

            // Background check for updates from GitHub
            _ = TriggerAutoUpdateCheckAsync();
        }

        private async Task TriggerAutoUpdateCheckAsync()
        {
            try
            {
                await Task.Delay(2000); // Brief delay so window renders first
                var update = await UpdateCheckerService.CheckForUpdatesAsync();
                if (update != null)
                {
                    Dispatcher.Invoke(() => ShowUpdateModal(update));
                }
            }
            catch { }
        }

        private void ShowUpdateModal(UpdateInfo update)
        {
            _availableUpdate = update;
            TxtUpdateNewVersion.Text = update.VersionTag;
            TxtUpdateChangelog.Text = update.Changelog;
            UpdateProgressPanel.Visibility = Visibility.Collapsed;
            UpdateActionButtons.Visibility = Visibility.Visible;
            BtnUpdateNow.IsEnabled = true;
            UpdateModal.Visibility = Visibility.Visible;
        }

        private void MainWindow_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
        {
            _discoveryService.Dispose();
            _transferServer.Dispose();
        }

        private void MinimizeButton_Click(object sender, RoutedEventArgs e)
        {
            WindowState = WindowState.Minimized;
        }

        private void CloseButton_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private static string GetLocalIpAddress()
        {
            try
            {
                using var socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0);
                socket.Connect("8.8.8.8", 65530);
                var endPoint = socket.LocalEndPoint as IPEndPoint;
                return endPoint?.Address.ToString() ?? "127.0.0.1";
            }
            catch
            {
                return "127.0.0.1";
            }
        }

        #region Discovery & Peer Selection

        private void OnPeerListChanged(IReadOnlyList<DiscoveredPeer> peers)
        {
            Dispatcher.Invoke(() =>
            {
                _discoveredPeers.Clear();
                foreach (var p in peers)
                {
                    _discoveredPeers.Add(p);
                }

                TxtDeviceCount.Text = _discoveredPeers.Count.ToString();
                NoDevicesPanel.Visibility = _discoveredPeers.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

                if (_selectedPeer != null)
                {
                    _selectedPeer = _discoveredPeers.FirstOrDefault(p => p.DeviceId == _selectedPeer.DeviceId);
                    if (_selectedPeer == null)
                    {
                        SelectedPeerCard.Visibility = Visibility.Collapsed;
                    }
                }
                UpdateSendButtonState();
            });
        }

        private void PeersListBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            if (PeersListBox.SelectedItem is DiscoveredPeer peer)
            {
                _selectedPeer = peer;
                TxtSelectedPeerName.Text = $"{peer.DeviceName} ({peer.IpAddress})";
                SelectedPeerCard.Visibility = Visibility.Visible;
            }
            UpdateSendButtonState();
        }

        private void ClearSelectedPeer_Click(object sender, RoutedEventArgs e)
        {
            _selectedPeer = null;
            PeersListBox.SelectedItem = null;
            SelectedPeerCard.Visibility = Visibility.Collapsed;
            UpdateSendButtonState();
        }

        private async void BtnRefresh_Click(object sender, RoutedEventArgs e)
        {
            await _discoveryService.BroadcastBeaconAsync();
        }

        #endregion

        #region File Selection & Drag Drop

        private void BtnAddFiles_Click(object sender, RoutedEventArgs e)
        {
            OpenSelectFilesDialog();
        }

        private void DropZone_Click(object sender, MouseButtonEventArgs e)
        {
            OpenSelectFilesDialog();
        }

        private void OpenSelectFilesDialog()
        {
            var dialog = new OpenFileDialog
            {
                Multiselect = true,
                Title = "Select files to share"
            };

            if (dialog.ShowDialog() == true)
            {
                AddFiles(dialog.FileNames);
            }
        }

        private void DropZone_DragOver(object sender, DragEventArgs e)
        {
            if (e.Data.GetDataPresent(DataFormats.FileDrop))
            {
                e.Effects = DragDropEffects.Copy;
                DropZone.BorderBrush = (Brush)FindResource("AccentCyan");
            }
            else
            {
                e.Effects = DragDropEffects.None;
            }
            e.Handled = true;
        }

        private void DropZone_DragLeave(object sender, DragEventArgs e)
        {
            DropZone.BorderBrush = (Brush)FindResource("BorderDark");
        }

        private void DropZone_Drop(object sender, DragEventArgs e)
        {
            DropZone.BorderBrush = (Brush)FindResource("BorderDark");
            if (e.Data.GetDataPresent(DataFormats.FileDrop))
            {
                var files = (string[])e.Data.GetData(DataFormats.FileDrop);
                if (files != null && files.Length > 0)
                {
                    AddFiles(files);
                }
            }
        }

        private void AddFiles(IEnumerable<string> paths)
        {
            foreach (var path in paths)
            {
                if (File.Exists(path))
                {
                    if (!_selectedFiles.Any(f => f.LocalPath == path))
                    {
                        var info = new FileInfo(path);
                        _selectedFiles.Add(new FileMetadata
                        {
                            Id = Guid.NewGuid().ToString("N"),
                            Name = info.Name,
                            Size = info.Length,
                            LocalPath = path
                        });
                    }
                }
                else if (Directory.Exists(path))
                {
                    var dirFiles = Directory.GetFiles(path, "*.*", SearchOption.AllDirectories);
                    foreach (var df in dirFiles)
                    {
                        if (!_selectedFiles.Any(f => f.LocalPath == df))
                        {
                            var info = new FileInfo(df);
                            _selectedFiles.Add(new FileMetadata
                            {
                                Id = Guid.NewGuid().ToString("N"),
                                Name = Path.GetRelativePath(path, df),
                                Size = info.Length,
                                LocalPath = df
                            });
                        }
                    }
                }
            }

            UpdateFilesSummary();
            UpdateSendButtonState();
        }

        private void RemoveFileButton_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is FileMetadata meta)
            {
                _selectedFiles.Remove(meta);
                UpdateFilesSummary();
                UpdateSendButtonState();
            }
        }

        private void BtnClearFiles_Click(object sender, RoutedEventArgs e)
        {
            _selectedFiles.Clear();
            UpdateFilesSummary();
            UpdateSendButtonState();
        }

        private void UpdateFilesSummary()
        {
            long totalBytes = _selectedFiles.Sum(f => f.Size);
            TxtFilesSummary.Text = $"({_selectedFiles.Count} {(_selectedFiles.Count == 1 ? "file" : "files")}, {FileMetadata.FormatBytes(totalBytes)})";
            NoFilesPanel.Visibility = _selectedFiles.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
            BtnClearFiles.Visibility = _selectedFiles.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        }

        private void UpdateSendButtonState()
        {
            bool canSend = _selectedPeer != null && _selectedFiles.Count > 0 && !_isTransferActive;
            BtnSend.IsEnabled = canSend;

            if (_selectedPeer == null && _selectedFiles.Count == 0)
            {
                TxtSendHint.Text = "Select a target device on the left and choose files to send.";
            }
            else if (_selectedPeer == null)
            {
                TxtSendHint.Text = "Select a recipient device from the left panel.";
            }
            else if (_selectedFiles.Count == 0)
            {
                TxtSendHint.Text = $"Target device is {_selectedPeer.DeviceName}. Choose or drop files above.";
            }
            else
            {
                long totalBytes = _selectedFiles.Sum(f => f.Size);
                TxtSendHint.Text = $"Ready to send {_selectedFiles.Count} files ({FileMetadata.FormatBytes(totalBytes)}) to {_selectedPeer.DeviceName}.";
            }
        }

        #endregion

        #region Sending Files Flow

        private async void BtnSend_Click(object sender, RoutedEventArgs e)
        {
            if (_selectedPeer == null || _selectedFiles.Count == 0 || _isTransferActive) return;

            _isTransferActive = true;
            UpdateSendButtonState();

            TxtTransferTitle.Text = $"Sending to {_selectedPeer.DeviceName}";
            TxtTransferSubTitle.Text = "Connecting & waiting for recipient approval...";
            TxtLiveSpeed.Text = "0.0 MB/s";
            TxtLiveBitrate.Text = "0 Mbps";
            TransferProgressBar.Value = 0;
            TxtTransferStats.Text = "Connecting...";
            TxtTransferEta.Text = "Waiting...";
            TxtCurrentFile.Text = "Waiting for peer to accept...";
            TransferModal.Visibility = Visibility.Visible;

            var files = _selectedFiles.ToList();
            await Task.Run(async () =>
            {
                await _transferClient.SendFilesAsync(_selectedPeer, _discoveryService.DeviceName, files);
            });
        }

        private void OnClientTransferCompleted(string sessionId, bool success, string? error)
        {
            Dispatcher.Invoke(() =>
            {
                _isTransferActive = false;
                UpdateSendButtonState();

                if (success)
                {
                    TxtTransferTitle.Text = "Transfer Complete!";
                    TxtTransferSubTitle.Text = "All files sent successfully.";
                    TransferProgressBar.Value = 100;
                    TxtLiveSpeed.Text = "100%";
                    TxtLiveBitrate.Text = "Completed";
                    TxtTransferEta.Text = "Finished";
                    TxtCurrentFile.Text = "All files transferred.";
                    BtnCancelTransfer.Content = "Close";
                }
                else
                {
                    TxtTransferTitle.Text = "Transfer Failed / Declined";
                    TxtTransferSubTitle.Text = error ?? "Transfer could not be completed.";
                    TxtLiveSpeed.Text = "Stopped";
                    TxtLiveBitrate.Text = "";
                    TxtTransferEta.Text = "Aborted";
                    BtnCancelTransfer.Content = "Close";
                }
            });
        }

        #endregion

        #region Receiving Files Flow

        private void OnIncomingTransferRequest(object? sender, IncomingTransferRequestArgs e)
        {
            Dispatcher.Invoke(() =>
            {
                _currentIncomingRequest = e;
                TxtIncomingSender.Text = $"{e.SenderDevice} ({e.SenderIp}) wants to send you {e.Files.Count} {(e.Files.Count == 1 ? "file" : "files")}";
                TxtIncomingTotalSize.Text = $"Total Size: {FileMetadata.FormatBytes(e.TotalSize)}";

                IncomingFilesListPanel.Children.Clear();
                foreach (var f in e.Files)
                {
                    var tb = new TextBlock
                    {
                        Text = $"• {f.Name} ({FileMetadata.FormatBytes(f.Size)})",
                        Foreground = (Brush)FindResource("TextPrimary"),
                        FontSize = 12,
                        Margin = new Thickness(0, 2, 0, 2),
                        TextTrimming = TextTrimming.CharacterEllipsis
                    };
                    IncomingFilesListPanel.Children.Add(tb);
                }

                IncomingModal.Visibility = Visibility.Visible;
            });
        }

        private void BtnAcceptTransfer_Click(object sender, RoutedEventArgs e)
        {
            if (_currentIncomingRequest == null) return;

            IncomingModal.Visibility = Visibility.Collapsed;

            _isTransferActive = true;
            UpdateSendButtonState();

            TxtTransferTitle.Text = $"Receiving from {_currentIncomingRequest.SenderDevice}";
            TxtTransferSubTitle.Text = "High-speed streaming in progress...";
            TxtLiveSpeed.Text = "0.0 MB/s";
            TxtLiveBitrate.Text = "0 Mbps";
            TransferProgressBar.Value = 0;
            TxtTransferStats.Text = "Starting download...";
            TxtTransferEta.Text = "Calculating...";
            TxtCurrentFile.Text = "Preparing disk buffer...";
            BtnCancelTransfer.Content = "Cancel Transfer";
            TransferModal.Visibility = Visibility.Visible;

            _currentIncomingRequest.DecisionTcs.TrySetResult(true);
        }

        private void BtnDeclineTransfer_Click(object sender, RoutedEventArgs e)
        {
            if (_currentIncomingRequest == null) return;
            IncomingModal.Visibility = Visibility.Collapsed;
            _currentIncomingRequest.DecisionTcs.TrySetResult(false);
            _currentIncomingRequest = null;
        }

        private void OnServerTransferCompleted(string sessionId, bool success, string? error)
        {
            Dispatcher.Invoke(() =>
            {
                _isTransferActive = false;
                UpdateSendButtonState();

                if (success)
                {
                    TxtTransferTitle.Text = "Transfer Complete!";
                    TxtTransferSubTitle.Text = $"Files saved to {_transferServer.DownloadFolder}";
                    TransferProgressBar.Value = 100;
                    TxtLiveSpeed.Text = "100%";
                    TxtLiveBitrate.Text = "Saved";
                    TxtTransferEta.Text = "Finished";
                    TxtCurrentFile.Text = "Ready in Downloads/SpeedShare";
                    BtnCancelTransfer.Content = "Open Folder & Close";
                }
                else
                {
                    TxtTransferTitle.Text = "Transfer Ended";
                    TxtTransferSubTitle.Text = error ?? "Transfer was interrupted.";
                    TxtLiveSpeed.Text = "Stopped";
                    TxtLiveBitrate.Text = "";
                    TxtTransferEta.Text = "Aborted";
                    BtnCancelTransfer.Content = "Close";
                }
            });
        }

        #endregion

        #region Live Progress & Cancellation

        private void OnTransferProgress(TransferProgressReport report)
        {
            Dispatcher.Invoke(() =>
            {
                TransferProgressBar.Value = report.Percentage;
                TxtLiveSpeed.Text = report.FormattedSpeed;
                TxtLiveBitrate.Text = report.FormattedBitrate;
                TxtTransferStats.Text = $"{FileMetadata.FormatBytes(report.TotalBytesTransferred)} / {FileMetadata.FormatBytes(report.TotalBytes)} ({report.Percentage:F1}%)";
                TxtTransferEta.Text = $"{report.FormattedEta} remaining";
                TxtCurrentFile.Text = $"File [{report.CurrentFileIndex}/{report.TotalFiles}]: {report.CurrentFileName}";
            });
        }

        private void BtnCancelTransfer_Click(object sender, RoutedEventArgs e)
        {
            if (BtnCancelTransfer.Content.ToString() == "Open Folder & Close")
            {
                BtnOpenFolder_Click(sender, e);
                TransferModal.Visibility = Visibility.Collapsed;
                return;
            }

            if (BtnCancelTransfer.Content.ToString() == "Close")
            {
                TransferModal.Visibility = Visibility.Collapsed;
                return;
            }

            _transferClient.Cancel();
            _transferServer.Stop();
            _transferServer.Start();
            TransferModal.Visibility = Visibility.Collapsed;
            _isTransferActive = false;
            UpdateSendButtonState();
        }

        #endregion

        #region Settings & Downloads Navigation

        private void BtnOpenFolder_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                if (!Directory.Exists(_transferServer.DownloadFolder))
                {
                    Directory.CreateDirectory(_transferServer.DownloadFolder);
                }
                Process.Start(new ProcessStartInfo
                {
                    FileName = _transferServer.DownloadFolder,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Could not open folder: {ex.Message}", "Error", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnSettings_Click(object sender, RoutedEventArgs e)
        {
            SettingsModal.Visibility = Visibility.Visible;
        }

        private void BtnCloseSettings_Click(object sender, RoutedEventArgs e)
        {
            SettingsModal.Visibility = Visibility.Collapsed;
        }

        private void BtnBrowseFolder_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new OpenFolderDialog
            {
                Title = "Select Download Folder"
            };

            if (dialog.ShowDialog() == true)
            {
                TxtDownloadFolder.Text = dialog.FolderName;
            }
        }

        private void BtnSaveSettings_Click(object sender, RoutedEventArgs e)
        {
            if (!string.IsNullOrWhiteSpace(TxtCustomDeviceName.Text))
            {
                _discoveryService.DeviceName = TxtCustomDeviceName.Text.Trim();
                LocalInfoText.Text = $"{_discoveryService.DeviceName} • {GetLocalIpAddress()}";
            }

            if (!string.IsNullOrWhiteSpace(TxtDownloadFolder.Text) && Directory.Exists(TxtDownloadFolder.Text))
            {
                _transferServer.DownloadFolder = TxtDownloadFolder.Text;
            }

            SettingsModal.Visibility = Visibility.Collapsed;
            _ = _discoveryService.BroadcastBeaconAsync();
        }

        private async void BtnCheckUpdateManual_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                BtnCheckUpdateManual.IsEnabled = false;
                TxtSettingsUpdateStatus.Text = "Checking GitHub Releases...";
                TxtSettingsUpdateStatus.Foreground = (Brush)FindResource("NeonCyan");

                var update = await UpdateCheckerService.CheckForUpdatesAsync();
                if (update != null)
                {
                    TxtSettingsUpdateStatus.Text = $"Update {update.VersionTag} is available!";
                    TxtSettingsUpdateStatus.Foreground = (Brush)FindResource("NeonMint");
                    ShowUpdateModal(update);
                }
                else
                {
                    TxtSettingsUpdateStatus.Text = $"SpeedShare v{UpdateCheckerService.CurrentVersion} is up to date!";
                    TxtSettingsUpdateStatus.Foreground = (Brush)FindResource("NeonMint");
                }
            }
            catch (Exception ex)
            {
                TxtSettingsUpdateStatus.Text = $"Check failed: {ex.Message}";
                TxtSettingsUpdateStatus.Foreground = (Brush)FindResource("NeonRose");
            }
            finally
            {
                BtnCheckUpdateManual.IsEnabled = true;
            }
        }

        private void BtnDismissUpdate_Click(object sender, RoutedEventArgs e)
        {
            UpdateModal.Visibility = Visibility.Collapsed;
        }

        private void BtnViewRelease_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                var url = _availableUpdate?.ReleaseUrl ?? UpdateCheckerService.ReleasesWebUrl;
                Process.Start(new ProcessStartInfo
                {
                    FileName = url,
                    UseShellExecute = true
                });
            }
            catch { }
        }

        private async void BtnUpdateNow_Click(object sender, RoutedEventArgs e)
        {
            if (_availableUpdate == null) return;

            if (string.IsNullOrEmpty(_availableUpdate.InstallerDownloadUrl))
            {
                // Fallback to browser
                BtnViewRelease_Click(sender, e);
                return;
            }

            try
            {
                UpdateActionButtons.Visibility = Visibility.Collapsed;
                UpdateProgressPanel.Visibility = Visibility.Visible;
                UpdateProgressBar.Value = 0;
                TxtUpdateProgressPercent.Text = "0%";

                var progress = new Progress<double>(percent =>
                {
                    UpdateProgressBar.Value = percent;
                    TxtUpdateProgressPercent.Text = $"{percent:F0}%";
                });

                var installerPath = await UpdateCheckerService.DownloadInstallerAsync(_availableUpdate.InstallerDownloadUrl, progress);

                TxtUpdateProgressPercent.Text = "Launching installer...";
                await Task.Delay(500);

                UpdateCheckerService.LaunchInstallerAndExit(installerPath);
            }
            catch (Exception ex)
            {
                UpdateProgressPanel.Visibility = Visibility.Collapsed;
                UpdateActionButtons.Visibility = Visibility.Visible;
                MessageBox.Show($"Failed to download update installer: {ex.Message}\nOpening release page in browser instead.", "Update Error", MessageBoxButton.OK, MessageBoxImage.Warning);
                BtnViewRelease_Click(sender, e);
            }
        }

        #endregion
    }
}