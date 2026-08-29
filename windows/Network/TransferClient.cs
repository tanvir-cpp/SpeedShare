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
    public class TransferClient
    {
        private const int ChunkSize = 1024 * 1024; // 1 MB buffer for maximum speed
        private CancellationTokenSource? _cts;

        public event Action<TransferProgressReport>? ProgressChanged;
        public event Action<string, bool, string?>? TransferCompleted;

        public void Cancel()
        {
            _cts?.Cancel();
        }

        public async Task SendFilesAsync(
            DiscoveredPeer targetPeer, 
            string localDeviceName, 
            List<FileMetadata> filesToTransfer)
        {
            _cts = new CancellationTokenSource();
            var token = _cts.Token;
            string sessionId = Guid.NewGuid().ToString("N");

            long totalSize = 0;
            foreach (var f in filesToTransfer)
            {
                if (File.Exists(f.LocalPath))
                {
                    f.Size = new FileInfo(f.LocalPath).Length;
                    totalSize += f.Size;
                }
                else
                {
                    f.Size = 0;
                }
            }

            TcpClient? client = null;
            try
            {
                client = new TcpClient();
                client.NoDelay = true;

                // 1. Connect with 5s timeout
                var connectTask = client.ConnectAsync(targetPeer.IpAddress, targetPeer.Port);
                var completedTask = await Task.WhenAny(connectTask, Task.Delay(5000, token));
                if (completedTask != connectTask)
                {
                    throw new TimeoutException($"Could not connect to {targetPeer.DeviceName} ({targetPeer.IpAddress}:{targetPeer.Port})");
                }
                await connectTask; // Propagate any connect exceptions

                // Set socket buffer sizes on the underlying socket AFTER connect.
                // Setting them on TcpClient before connect is a hint that the OS
                // can ignore; setting them on the Socket actually applies.
                try
                {
                    client.Client.SendBufferSize = 2 * 1024 * 1024;
                    client.Client.ReceiveBufferSize = 2 * 1024 * 1024;
                }
                catch { /* best-effort */ }

                var stream = client.GetStream();

                // 2. Send Transfer Request
                var request = new ControlMessage
                {
                    Action = "TRANSFER_REQUEST",
                    SessionId = sessionId,
                    SenderDevice = localDeviceName,
                    DeviceType = "WINDOWS",
                    Files = filesToTransfer,
                    TotalSize = totalSize
                };

                await SendControlMessageAsync(stream, request, token);

                // 3. Await Response (Accept/Decline)
                var lengthBuffer = new byte[4];
                await ReadExactAsync(stream, lengthBuffer, 0, 4, token);
                int responseLength = IPAddress.NetworkToHostOrder(BitConverter.ToInt32(lengthBuffer, 0));

                var responseBytes = new byte[responseLength];
                await ReadExactAsync(stream, responseBytes, 0, responseLength, token);
                var response = JsonSerializer.Deserialize<ControlMessage>(Encoding.UTF8.GetString(responseBytes));

                if (response == null || response.Action != "TRANSFER_ACCEPT")
                {
                    string reason = response?.Reason ?? "Transfer was rejected by recipient.";
                    TransferCompleted?.Invoke(sessionId, false, reason);
                    return;
                }

                // 4. Send Files with High Speed Stream
                long totalBytesSent = 0;
                var buffer = new byte[ChunkSize];
                var stopwatch = Stopwatch.StartNew();
                long lastReportBytes = 0;
                var lastReportTime = stopwatch.ElapsedMilliseconds;

                for (int i = 0; i < filesToTransfer.Count; i++)
                {
                    var file = filesToTransfer[i];
                    if (!File.Exists(file.LocalPath))
                    {
                        throw new FileNotFoundException($"File not found: {file.LocalPath}");
                    }

                    // Refresh size in case the file changed since the UI picked it
                    long actualSize = new FileInfo(file.LocalPath).Length;
                    if (actualSize != file.Size)
                    {
                        file.Size = actualSize;
                    }

                    // Write 4-byte file index and 8-byte file size
                    var metaBuf = new byte[12];
                    Array.Copy(BitConverter.GetBytes(IPAddress.HostToNetworkOrder(i)), 0, metaBuf, 0, 4);
                    Array.Copy(BitConverter.GetBytes(IPAddress.HostToNetworkOrder(file.Size)), 0, metaBuf, 4, 8);
                    await stream.WriteAsync(metaBuf, 0, 12, token);

                    if (file.Size == 0)
                    {
                        // Empty file: send nothing, but still report progress and complete
                        ProgressChanged?.Invoke(new TransferProgressReport
                        {
                            SessionId = sessionId,
                            CurrentFileName = file.Name,
                            CurrentFileIndex = i + 1,
                            TotalFiles = filesToTransfer.Count,
                            TotalBytesTransferred = totalBytesSent,
                            TotalBytes = totalSize,
                            Percentage = totalSize > 0 ? (double)totalBytesSent / totalSize * 100.0 : 100.0,
                            SpeedBytesPerSec = 0,
                            EstimatedTimeRemaining = TimeSpan.Zero
                        });
                        continue;
                    }

                    using (var fileStream = new FileStream(
                        file.LocalPath,
                        FileMode.Open,
                        FileAccess.Read,
                        FileShare.Read,
                        bufferSize: ChunkSize,
                        useAsync: true))
                    {
                        long remaining = file.Size;
                        while (remaining > 0)
                        {
                            int toRead = (int)Math.Min(ChunkSize, remaining);
                            int bytesRead = await fileStream.ReadAsync(buffer.AsMemory(0, toRead), token);
                            if (bytesRead == 0)
                            {
                                // Premature EOF on local file — protocol violation
                                throw new EndOfStreamException(
                                    $"Local file '{file.LocalPath}' ended before its declared size of {file.Size} bytes.");
                            }

                            await stream.WriteAsync(buffer.AsMemory(0, bytesRead), token);
                            remaining -= bytesRead;
                            totalBytesSent += bytesRead;

                            // Speed & Progress report every 200ms
                            long now = stopwatch.ElapsedMilliseconds;
                            if (now - lastReportTime >= 200 || totalBytesSent == totalSize)
                            {
                                double deltaSec = (now - lastReportTime) / 1000.0;
                                double speed = deltaSec > 0 ? (totalBytesSent - lastReportBytes) / deltaSec : 0;
                                long remainingBytes = Math.Max(0, totalSize - totalBytesSent);
                                double etaSeconds = speed > 0 ? remainingBytes / speed : 0;

                                ProgressChanged?.Invoke(new TransferProgressReport
                                {
                                    SessionId = sessionId,
                                    CurrentFileName = file.Name,
                                    CurrentFileIndex = i + 1,
                                    TotalFiles = filesToTransfer.Count,
                                    TotalBytesTransferred = totalBytesSent,
                                    TotalBytes = totalSize,
                                    Percentage = totalSize > 0 ? (double)totalBytesSent / totalSize * 100.0 : 100.0,
                                    SpeedBytesPerSec = speed,
                                    EstimatedTimeRemaining = TimeSpan.FromSeconds(etaSeconds)
                                });

                                lastReportBytes = totalBytesSent;
                                lastReportTime = now;
                            }
                        }
                    }
                    await stream.FlushAsync(token);
                }

                // 5. Read Final Completion Message
                await ReadExactAsync(stream, lengthBuffer, 0, 4, token);
                int completionLength = IPAddress.NetworkToHostOrder(BitConverter.ToInt32(lengthBuffer, 0));
                var completionBytes = new byte[completionLength];
                await ReadExactAsync(stream, completionBytes, 0, completionLength, token);

                TransferCompleted?.Invoke(sessionId, true, null);
            }
            catch (OperationCanceledException)
            {
                TransferCompleted?.Invoke(sessionId, false, "Transfer was cancelled.");
            }
            catch (Exception ex)
            {
                Debug.WriteLine($"[TransferClient] Error: {ex.Message}");
                TransferCompleted?.Invoke(sessionId, false, ex.Message);
            }
            finally
            {
                client?.Dispose();
            }
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
    }
}
