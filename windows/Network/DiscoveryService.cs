using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using SpeedShareWindows.Models;

namespace SpeedShareWindows.Network
{
    public class DiscoveryService : IDisposable
    {
        public const int DiscoveryPort = 53317;
        public const int DefaultTransferPort = 53318;

        private readonly string _deviceId;
        private string _deviceName;
        private readonly int _transferPort;
        private UdpClient? _udpListener;
        private UdpClient? _udpSender;
        private CancellationTokenSource? _cts;
        private readonly ConcurrentDictionary<string, DiscoveredPeer> _peers = new();
        private readonly System.Timers.Timer _beaconTimer;
        private readonly System.Timers.Timer _cleanupTimer;

        public event Action<DiscoveredPeer>? PeerFound;
        public event Action<DiscoveredPeer>? PeerUpdated;
        public event Action<string>? PeerLost;
        public event Action<IReadOnlyList<DiscoveredPeer>>? PeerListChanged;

        public string DeviceId => _deviceId;
        public string DeviceName
        {
            get => _deviceName;
            set => _deviceName = value;
        }

        public IReadOnlyList<DiscoveredPeer> ActivePeers => _peers.Values.ToList();

        public DiscoveryService(string? customDeviceName = null, int transferPort = DefaultTransferPort)
        {
            _deviceId = Guid.NewGuid().ToString("N")[..8];
            _deviceName = !string.IsNullOrWhiteSpace(customDeviceName) 
                ? customDeviceName 
                : $"{Environment.MachineName} (Windows)";
            _transferPort = transferPort;

            _beaconTimer = new System.Timers.Timer(1500);
            _beaconTimer.Elapsed += async (s, e) => await BroadcastBeaconAsync();

            _cleanupTimer = new System.Timers.Timer(3000);
            _cleanupTimer.Elapsed += (s, e) => CleanupStalePeers();
        }

        public void Start()
        {
            _cts = new CancellationTokenSource();

            try
            {
                _udpListener = new UdpClient();
                _udpListener.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _udpListener.Client.Bind(new IPEndPoint(IPAddress.Any, DiscoveryPort));
                _udpListener.EnableBroadcast = true;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Discovery] Failed to bind listener: {ex.Message}");
            }

            try
            {
                _udpSender = new UdpClient();
                _udpSender.EnableBroadcast = true;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Discovery] Failed to create sender: {ex.Message}");
            }

            _beaconTimer.Start();
            _cleanupTimer.Start();

            _ = Task.Run(() => ListenLoopAsync(_cts.Token));
            _ = BroadcastDiscoverAsync(); // Send immediate initial discover ping
            _ = BroadcastBeaconAsync();   // Send immediate initial beacon
        }

        public void Stop()
        {
            _cts?.Cancel();
            _beaconTimer.Stop();
            _cleanupTimer.Stop();

            try { _udpListener?.Close(); } catch { }
            try { _udpSender?.Close(); } catch { }
        }

        private async Task ListenLoopAsync(CancellationToken token)
        {
            if (_udpListener == null) return;

            while (!token.IsCancellationRequested)
            {
                try
                {
                    var result = await _udpListener.ReceiveAsync(token);
                    var rawJson = Encoding.UTF8.GetString(result.Buffer);

                    var beacon = JsonSerializer.Deserialize<BeaconMessage>(rawJson);
                    if (beacon != null && beacon.DeviceId != _deviceId)
                    {
                        if (beacon.Type == "DISCOVER")
                        {
                            // Respond immediately with beacon so peer discovers us instantly
                            _ = BroadcastBeaconAsync();
                            continue;
                        }

                        if (beacon.Type == "BEACON" && !string.IsNullOrEmpty(beacon.DeviceId))
                        {
                            var senderIp = result.RemoteEndPoint.Address.ToString();
                            // Normalize localhost / IPv6 mapping if any
                            if (senderIp.StartsWith("::ffff:")) senderIp = senderIp[7..];

                            var peer = new DiscoveredPeer
                            {
                                DeviceId = beacon.DeviceId,
                                DeviceName = beacon.DeviceName,
                                DeviceType = beacon.DeviceType,
                                IpAddress = senderIp,
                                Port = beacon.Port > 0 ? beacon.Port : DefaultTransferPort,
                                LastSeen = DateTime.UtcNow
                            };

                            bool isNew = !_peers.ContainsKey(peer.DeviceId);
                            _peers[peer.DeviceId] = peer;

                            if (isNew)
                            {
                                PeerFound?.Invoke(peer);
                            }
                            else
                            {
                                PeerUpdated?.Invoke(peer);
                            }

                            // Always notify on add/update so the UI learns about
                            // peers without waiting for the next cleanup tick.
                            PeerListChanged?.Invoke(_peers.Values.ToList());
                        }
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[Discovery] Listen error: {ex.Message}");
                }
            }
        }

        public async Task BroadcastDiscoverAsync()
        {
            if (_udpSender == null) return;

            try
            {
                var discover = new BeaconMessage
                {
                    Type = "DISCOVER",
                    DeviceId = _deviceId,
                    DeviceName = _deviceName,
                    DeviceType = "WINDOWS",
                    Port = _transferPort,
                    Version = 1
                };

                var json = JsonSerializer.Serialize(discover);
                var bytes = Encoding.UTF8.GetBytes(json);

                await SendToAllBroadcastAddressesAsync(bytes);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Discovery] Discover broadcast error: {ex.Message}");
            }
        }

        public async Task BroadcastBeaconAsync()
        {
            if (_udpSender == null) return;

            try
            {
                var beacon = new BeaconMessage
                {
                    Type = "BEACON",
                    DeviceId = _deviceId,
                    DeviceName = _deviceName,
                    DeviceType = "WINDOWS",
                    Port = _transferPort,
                    Version = 1
                };

                var json = JsonSerializer.Serialize(beacon);
                var bytes = Encoding.UTF8.GetBytes(json);

                await SendToAllBroadcastAddressesAsync(bytes);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Discovery] Broadcast error: {ex.Message}");
            }
        }

        private async Task SendToAllBroadcastAddressesAsync(byte[] bytes)
        {
            if (_udpSender == null) return;

            // 1. Global standard broadcast
            try
            {
                var broadcastEndpoint = new IPEndPoint(IPAddress.Broadcast, DiscoveryPort);
                await _udpSender.SendAsync(bytes, bytes.Length, broadcastEndpoint);
            }
            catch { }

            // 2. Broadcast to each physical network adapter subnet (Wi-Fi, Ethernet)
            foreach (var bcastIp in GetSubnetBroadcastAddresses())
            {
                try
                {
                    await _udpSender.SendAsync(bytes, bytes.Length, new IPEndPoint(bcastIp, DiscoveryPort));
                }
                catch { }
            }
        }

        private static List<IPAddress> GetSubnetBroadcastAddresses()
        {
            var broadcastList = new List<IPAddress>();
            try
            {
                foreach (var netInterface in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (netInterface.OperationalStatus != OperationalStatus.Up || 
                        netInterface.NetworkInterfaceType == NetworkInterfaceType.Loopback)
                        continue;

                    var ipProps = netInterface.GetIPProperties();
                    foreach (var u in ipProps.UnicastAddresses)
                    {
                        if (u.Address.AddressFamily == AddressFamily.InterNetwork && u.IPv4Mask != null)
                        {
                            var ipBytes = u.Address.GetAddressBytes();
                            var maskBytes = u.IPv4Mask.GetAddressBytes();
                            if (ipBytes.Length == 4 && maskBytes.Length == 4)
                            {
                                var bcastBytes = new byte[4];
                                for (int i = 0; i < 4; i++)
                                {
                                    bcastBytes[i] = (byte)(ipBytes[i] | (maskBytes[i] ^ 255));
                                }
                                broadcastList.Add(new IPAddress(bcastBytes));
                            }
                        }
                    }
                }
            }
            catch { }
            return broadcastList;
        }

        private void CleanupStalePeers()
        {
            var threshold = DateTime.UtcNow.AddSeconds(-6);
            var staleKeys = _peers
                .Where(p => p.Value.LastSeen < threshold)
                .Select(p => p.Key)
                .ToList();

            if (staleKeys.Count > 0)
            {
                foreach (var key in staleKeys)
                {
                    if (_peers.TryRemove(key, out _))
                    {
                        PeerLost?.Invoke(key);
                    }
                }
                PeerListChanged?.Invoke(_peers.Values.ToList());
            }
        }

        public void Dispose()
        {
            Stop();
            _beaconTimer.Dispose();
            _cleanupTimer.Dispose();
            _udpListener?.Dispose();
            _udpSender?.Dispose();
            _cts?.Dispose();
        }
    }
}
