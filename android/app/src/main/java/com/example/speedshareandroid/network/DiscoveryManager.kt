package com.example.speedshareandroid.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.speedshareandroid.models.DiscoveredPeer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DiscoveryManager(private val context: Context) {
    companion object {
        const val DISCOVERY_PORT = 53317
        const val DEFAULT_TRANSFER_PORT = 53318
        private const val TAG = "DiscoveryManager"
        private const val BEACON_INTERVAL_MS = 1500L
        private const val STALE_PEER_TIMEOUT_MS = 6000L
    }

    val deviceId: String = UUID.randomUUID().toString().replace("-", "").take(8)
    var deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android)"

    private val _peers = ConcurrentHashMap<String, DiscoveredPeer>()
    private val _peersFlow = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peersFlow: StateFlow<List<DiscoveredPeer>> = _peersFlow.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var scope: CoroutineScope? = null

    // Separate receive and send sockets to avoid interleaving send/recv on a single
    // shared DatagramSocket (previously the same socket was used for both).
    private var receiveSocket: DatagramSocket? = null
    private var sendSocket: DatagramSocket? = null

    // Monotonically increasing nonce so we can ignore our own broadcasts that some
    // OS configurations deliver back to the loopback interface.
    private val broadcastNonce = AtomicInteger(0)

    fun start() {
        stop()
        val job = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + job)

        acquireMulticastLock()
        bindReceiveSocket()
        bindSendSocket()

        // Start listening
        scope?.launch { listenLoop() }

        // Initial discover + beacon
        scope?.launch {
            broadcastDiscover()
            broadcastBeacon()
        }

        // Periodic beacon
        scope?.launch {
            while (isActive) {
                delay(BEACON_INTERVAL_MS)
                broadcastBeacon()
            }
        }

        // Periodic cleanup of stale peers
        scope?.launch {
            while (isActive) {
                delay(3000)
                cleanupStalePeers()
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        closeSocketQuietly(receiveSocket, "receive")
        closeSocketQuietly(sendSocket, "send")
        receiveSocket = null
        sendSocket = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Multicast lock release error: ${e.message}")
        }
        multicastLock = null
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("SpeedShareMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }
    }

    private fun bindReceiveSocket() {
        try {
            receiveSocket = DatagramSocket(DISCOVERY_PORT).apply {
                broadcast = true
                reuseAddress = true
                soTimeout = 1000  // allow cooperative cancellation
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind receive socket on port $DISCOVERY_PORT: ${e.message}")
        }
    }

    private fun bindSendSocket() {
        try {
            sendSocket = DatagramSocket().apply {
                broadcast = true
                reuseAddress = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind send socket: ${e.message}")
        }
    }

    private fun closeSocketQuietly(s: DatagramSocket?, name: String) {
        try {
            s?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Socket ($name) close error: ${e.message}")
        }
    }

    private suspend fun listenLoop() = withContext(Dispatchers.IO) {
        val buf = ByteArray(2048)
        while (isActive) {
            val s = receiveSocket
            if (s == null) {
                delay(500)
                continue
            }
            try {
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)

                val rawJson = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val json = JSONObject(rawJson)

                val type = json.optString("type")
                val senderDeviceId = json.optString("deviceId")
                val senderNonce = json.optInt("nonce", -1)

                if (senderDeviceId.isEmpty() || senderDeviceId == deviceId) continue
                // Some devices loop our packet back; ignore via nonce
                if (senderNonce == broadcastNonce.get()) continue

                if (type == "DISCOVER") {
                    // Reply once with a beacon
                    broadcastBeacon()
                } else if (type == "BEACON") {
                    val name = json.optString("deviceName", "Unknown Device")
                    val devType = json.optString("deviceType", "UNKNOWN")
                    val port = json.optInt("port", DEFAULT_TRANSFER_PORT)
                    var senderIp = packet.address.hostAddress ?: ""
                    if (senderIp.startsWith("::ffff:")) senderIp = senderIp.substring(7)

                    val peer = DiscoveredPeer(
                        deviceId = senderDeviceId,
                        deviceName = name,
                        deviceType = devType,
                        ipAddress = senderIp,
                        port = port,
                        lastSeen = System.currentTimeMillis()
                    )

                    _peers[peer.deviceId] = peer
                    _peersFlow.value = _peers.values.toList()
                }
            } catch (e: SocketException) {
                if (isActive) {
                    Log.d(TAG, "Socket closed during receive")
                    break
                }
            } catch (e: Exception) {
                if (isActive && scope?.isActive == true) {
                    Log.d(TAG, "Discovery receive warning: ${e.message}")
                }
            }
        }
    }

    fun broadcastDiscover() {
        try {
            val nonce = broadcastNonce.incrementAndGet()
            val json = JSONObject().apply {
                put("type", "DISCOVER")
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("deviceType", "ANDROID")
                put("port", DEFAULT_TRANSFER_PORT)
                put("nonce", nonce)
                put("version", 1)
            }
            sendToAllInterfaces(json.toString().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.d(TAG, "Discover broadcast error: ${e.message}")
        }
    }

    fun broadcastBeacon() {
        try {
            val nonce = broadcastNonce.incrementAndGet()
            val json = JSONObject().apply {
                put("type", "BEACON")
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("deviceType", "ANDROID")
                put("port", DEFAULT_TRANSFER_PORT)
                put("nonce", nonce)
                put("version", 1)
            }
            sendToAllInterfaces(json.toString().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.d(TAG, "Broadcast error: ${e.message}")
        }
    }

    private fun sendToAllInterfaces(data: ByteArray) {
        val sock = sendSocket ?: return
        // 1. Global standard broadcast
        try {
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(data, data.size, broadcastAddr, DISCOVERY_PORT)
            sock.send(packet)
        } catch (e: Exception) {
            // ignore
        }

        // 2. Per-interface directed broadcast for multi-NIC compatibility
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val bcast = interfaceAddress.broadcast
                    if (bcast != null) {
                        try {
                            val ifPacket = DatagramPacket(data, data.size, bcast, DISCOVERY_PORT)
                            sock.send(ifPacket)
                        } catch (_: Exception) {
                            // ignore per-interface failures
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun cleanupStalePeers() {
        val now = System.currentTimeMillis()
        val staleKeys = _peers.filter { (now - it.value.lastSeen) > STALE_PEER_TIMEOUT_MS }.keys
        if (staleKeys.isNotEmpty()) {
            staleKeys.forEach { _peers.remove(it) }
            _peersFlow.value = _peers.values.toList()
        }
    }

    fun getLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        var host = addr.hostAddress ?: continue
                        if (host.startsWith("::ffff:")) host = host.substring(7)
                        return host
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "IP lookup error: ${e.message}")
        }
        return "127.0.0.1"
    }
}
