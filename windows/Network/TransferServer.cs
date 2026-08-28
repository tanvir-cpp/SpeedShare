using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using SpeedShareWindows.Models;

namespace SpeedShareWindows.Network
{
    public class IncomingTransferRequestArgs : EventArgs
    {
        public string SessionId { get; set; } = string.Empty;
        public string SenderDevice { get; set; } = string.Empty;
        public string DeviceType { get; set; } = string.Empty;
        public List<FileMetadata> Files { get; set; } = new();
        public long TotalSize { get; set; }
        public string SenderIp { get; set; } = string.Empty;
        public TaskCompletionSource<bool> DecisionTcs { get; set; } = new();
    }

    public class TransferServer : IDisposable
    {
        public const int DefaultPort = 53318;
        private const int ChunkSize = 1024 * 1024; // 1 MB buffer for maximum speed

        private TcpListener? _listener;
        private CancellationTokenSource? _cts;
        private string _downloadFolder;

        public event EventHandler<IncomingTransferRequestArgs>? TransferRequestReceived;
        public event Action<TransferProgressReport>? ProgressChanged;
        public event Action<string, bool, string?>? TransferCompleted; // sessionId, success, error

        public string DownloadFolder
        {
            get => _downloadFolder;
            set
            {
                _downloadFolder = value;
                if (!Directory.Exists(_downloadFolder))
                {
                    Directory.CreateDirectory(_downloadFolder);
                }
            }
        }

        public TransferServer(string? downloadFolder = null)
        {
            _downloadFolder = downloadFolder ?? Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
                "Downloads",
                "SpeedShare");

            if (!Directory.Exists(_downloadFolder))
            {
                Directory.CreateDirectory(_downloadFolder);
            }
        }

        public void Start(int port = DefaultPort)
        {
            _cts = new CancellationTokenSource();
            _listener = new TcpListener(IPAddress.Any, port);
            _listener.Server.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
            _listener.Server.ReceiveBufferSize = 2 * 1024 * 1024; // 2MB receive buffer
            _listener.Server.NoDelay = true;
            _listener.Start();

            _ = Task.Run(() => AcceptLoopAsync(_cts.Token));
        }

        public void Stop()
        {
            _cts?.Cancel();
            try { _listener?.Stop(); } catch { }
        }

        private async Task AcceptLoopAsync(CancellationToken token)
        {
            if (_listener == null) return;

            while (!token.IsCancellationRequested)
            {
                try
                {
                    var client = await _listener.AcceptTcpClientAsync(token);
                    client.NoDelay = true;
                    client.ReceiveBufferSize = 2 * 1024 * 1024;
                    _ = Task.Run(() => HandleClientAsync(client, token), token);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    Debug.WriteLine($"[TransferServer] Accept error: {ex.Message}");
                }
            }
        }

        private async Task HandleClientAsync(TcpClient client, CancellationToken token)
        {
            using (client)
            using (var stream = client.GetStream())
            {
                string sessionId = string.Empty;
                try
                {
                    // 1. Read Request Header Length
                    var lengthBuffer = new byte[4];
                    await ReadExactAsync(stream, lengthBuffer, 0, 4, token);
                    int headerLength = IPAddress.NetworkToHostOrder(BitConverter.ToInt32(lengthBuffer, 0));

                    if (headerLength <= 0 || headerLength > 10 * 1024 * 1024)
                    {
                        throw new InvalidDataException("Invalid header size");
                    }

                    // 2. Read Request JSON
                    var headerBytes = new byte[headerLength];
                    await ReadExactAsync(stream, headerBytes, 0, headerLength, token);
                    var json = Encoding.UTF8.GetString(headerBytes);
                    var request = JsonSerializer.Deserialize<ControlMessage>(json);

                    if (request == null || request.Action != "TRANSFER_REQUEST" || request.Files == null || request.Files.Count == 0)
                    {
                        throw new InvalidDataException("Invalid transfer request");
                    }

                    sessionId = request.SessionId;
                    var senderIp = ((IPEndPoint?)client.Client.RemoteEndPoint)?.Address.ToString() ?? "";

                    // 3. Prompt user for approval
                    var requestArgs = new IncomingTransferRequestArgs
                    {
                        SessionId = request.SessionId,
                        SenderDevice = request.SenderDevice,
                        DeviceType = request.DeviceType,
                        Files = request.Files,
                        TotalSize = request.TotalSize,
                        SenderIp = senderIp
                    };

                    TransferRequestReceived?.Invoke(this, requestArgs);

                    // Wait for user decision
                    bool accepted = await requestArgs.DecisionTcs.Task;

                    if (!accepted)
                    {
                        // Send Decline
                        var declineMsg = new ControlMessage
                        {
                            Action = "TRANSFER_DECLINE",
                            SessionId = request.SessionId,
                            Reason = "Transfer was declined by receiver"
                        };
                        await SendControlMessageAsync(stream, declineMsg, token);
                        TransferCompleted?.Invoke(sessionId, false, "Declined by user");
                        return;
                    }

                    // Send Accept
                    var acceptMsg = new ControlMessage
                    {
                        Action = "TRANSFER_ACCEPT",
                        SessionId = request.SessionId
                    };
                    await SendControlMessageAsync(stream, acceptMsg, token);

                    // 4. Receive Files with High Speed streaming
                    long totalBytesTransferred = 0;
                    long totalBytes = request.TotalSize;
                    var buffer = new byte[ChunkSize];
                    var stopwatch = Stopwatch.StartNew();
                    long lastReportBytes = 0;
                    var lastReportTime = stopwatch.ElapsedMilliseconds;

                    for (int i = 0; i < request.Files.Count; i++)
                    {
                        var fileMeta = request.Files[i];

                        // Read file index (4 bytes) & file size (8 bytes)
                        var metaBuf = new byte[12];
                        await ReadExactAsync(stream, metaBuf, 0, 12, token);
                        int fileIndex = IPAddress.NetworkToHostOrder(BitConverter.ToInt32(metaBuf, 0));
                        long fileSize = IPAddress.NetworkToHostOrder(BitConverter.ToInt64(metaBuf, 4));

                        // Generate unique target path if file already exists
                        string safeFileName = Path.GetFileName(fileMeta.Name);
                        string destinationPath = GetUniqueFilePath(_downloadFolder, safeFileName);

                        using (var fileStream = new FileStream(
                            destinationPath, 
                            FileMode.Create, 
                            FileAccess.Write, 
                            FileShare.None, 
                            bufferSize: ChunkSize, 
                            useAsync: true))
                        {
                            long remainingForFile = fileSize;
                            while (remainingForFile > 0)
                            {
                                int toRead = (int)Math.Min(ChunkSize, remainingForFile);
                                int bytesRead = await stream.ReadAsync(buffer.AsMemory(0, toRead), token);
                                if (bytesRead == 0)
                                {
                                    throw new EndOfStreamException("Connection terminated before transfer completed.");
                                }

                                await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead), token);
                                remainingForFile -= bytesRead;
                                totalBytesTransferred += bytesRead;

                                // Progress calculation every 200ms
                                long now = stopwatch.ElapsedMilliseconds;
                                if (now - lastReportTime >= 200 || totalBytesTransferred == totalBytes)
                                {
                                    double deltaSec = (now - lastReportTime) / 1000.0;
                                    double speed = deltaSec > 0 ? (totalBytesTransferred - lastReportBytes) / deltaSec : 0;
                                    double overallSpeed = stopwatch.Elapsed.TotalSeconds > 0 ? totalBytesTransferred / stopwatch.Elapsed.TotalSeconds : 0;
                                    
                                    long remainingBytes = Math.Max(0, totalBytes - totalBytesTransferred);
                                    double etaSeconds = speed > 0 ? remainingBytes / speed : 0;

                                    ProgressChanged?.Invoke(new TransferProgressReport
                                    {
                                        SessionId = sessionId,
                                        CurrentFileName = safeFileName,
                                        CurrentFileIndex = i + 1,
                                        TotalFiles = request.Files.Count,
                                        TotalBytesTransferred = totalBytesTransferred,
                                        TotalBytes = totalBytes,
                                        Percentage = totalBytes > 0 ? (double)totalBytesTransferred / totalBytes * 100.0 : 100.0,
                                        SpeedBytesPerSec = speed,
                                        EstimatedTimeRemaining = TimeSpan.FromSeconds(etaSeconds)
                                    });

                                    lastReportBytes = totalBytesTransferred;
                                    lastReportTime = now;
                                }
                            }
                            await fileStream.FlushAsync(token);
                        }
                    }

                    // 5. Send Completion confirmation
                    var completeMsg = new ControlMessage
                    {
                        Action = "TRANSFER_COMPLETE",
                        SessionId = request.SessionId,
                        Status = "SUCCESS"
                    };
                    await SendControlMessageAsync(stream, completeMsg, token);
                    TransferCompleted?.Invoke(sessionId, true, null);
                }
                catch (OperationCanceledException)
                {
                    TransferCompleted?.Invoke(sessionId, false, "Cancelled");
                }
                catch (Exception ex)
                {
                    Debug.WriteLine($"[TransferServer] Transfer error: {ex.Message}");
                    TransferCompleted?.Invoke(sessionId, false, ex.Message);
                }
            }
        }

        private static string GetUniqueFilePath(string folder, string fileName)
        {
            string path = Path.Combine(folder, fileName);
            if (!File.Exists(path)) return path;

            string nameWithoutExt = Path.GetFileNameWithoutExtension(fileName);
            string ext = Path.GetExtension(fileName);
            int counter = 1;

            while (File.Exists(path))
            {
                path = Path.Combine(folder, $"{nameWithoutExt} ({counter}){ext}");
                counter++;
            }
            return path;
        }

        private static async Task SendControlMessageAsync(NetworkStream stream, ControlMessage message, CancellationToken token)
        {
            var json = JsonSerializer.Serialize(message);
            var bytes = Encoding.UTF8.GetBytes(json);
            var lenBytes = BitConverter.GetBytes(IPAddress.HostToNetworkOrder(bytes.Length));

            await stream.WriteAsync(lenBytes, 0, 4, token);
            await stream.WriteAsync(bytes, 0, bytes.Length, token);
            await stream.FlushAsync(token);
        }

        private static async Task ReadExactAsync(Stream stream, byte[] buffer, int offset, int count, CancellationToken token)
        {
            int totalRead = 0;
            while (totalRead < count)
            {
                int read = await stream.ReadAsync(buffer.AsMemory(offset + totalRead, count - totalRead), token);
                if (read == 0)
                {
                    throw new EndOfStreamException("End of stream reached before reading required bytes.");
                }
                totalRead += read;
            }
        }

        public void Dispose()
        {
            Stop();
            _cts?.Dispose();
        }
    }
}
