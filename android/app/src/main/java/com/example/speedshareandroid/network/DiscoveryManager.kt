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
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DiscoveryManager(private val context: Context) {
    companion object {
        const val DISCOVERY_PORT = 53317
        const val DEFAULT_TRANSFER_PORT = 53318
        private const val TAG = "DiscoveryManager"
    }

    val deviceId: String = UUID.randomUUID().toString().replace("-", "").take(8)
    var deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android)"

    private val _peers = ConcurrentHashMap<String, DiscoveredPeer>()
    private val _peersFlow = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peersFlow: StateFlow<List<DiscoveredPeer>> = _peersFlow.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var scope: CoroutineScope? = null
    private var socket: DatagramSocket? = null

    fun start() {
        stop()
        val job = SupervisorJob()
        scope = CoroutineScope(Dispatchers.IO + job)

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("SpeedShareMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock: ${e.message}")
        }

        try {
            socket = DatagramSocket(DISCOVERY_PORT).apply {
                broadcast = true
                reuseAddress = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind discovery socket on port $DISCOVERY_PORT: ${e.message}")
            try {
                socket = DatagramSocket().apply { broadcast = true }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to create fallback socket: ${ex.message}")
            }
        }

        // Start listening
        scope?.launch { listenLoop() }

        // Send initial discover ping & beacon
        scope?.launch {
            broadcastDiscover()
            broadcastBeacon()
        }

        // Start periodic beacon
        scope?.launch {
            while (isActive) {
                delay(1500)
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
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Socket close error: ${e.message}")
        }
        socket = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Multicast lock release error: ${e.message}")
        }
        multicastLock = null
    }

    private suspend fun listenLoop() = withContext(Dispatchers.IO) {
        val buf = ByteArray(2048)
        while (isActive) {
            try {
                val s = socket ?: break
                val packet = DatagramPacket(buf, buf.size)
                s.receive(packet)

                val rawJson = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val json = JSONObject(rawJson)

                val type = json.optString("type")
                val senderDeviceId = json.optString("deviceId")

                if (senderDeviceId.isNotEmpty() && senderDeviceId != deviceId) {
                    if (type == "DISCOVER") {
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
                }
            } catch (e: Exception) {
                if (scope?.isActive == true) {
                    Log.d(TAG, "Discovery receive warning: ${e.message}")
                }
            }
        }
    }

    fun broadcastDiscover() {
        try {
            val json = JSONObject().apply {
                put("type", "DISCOVER")
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("deviceType", "ANDROID")
                put("port", DEFAULT_TRANSFER_PORT)
                put("version", 1)
            }
            sendToAllInterfaces(json.toString().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.d(TAG, "Discover broadcast error: ${e.message}")
        }
    }

    fun broadcastBeacon() {
        try {
            val json = JSONObject().apply {
                put("type", "BEACON")
                put("deviceId", deviceId)
                put("deviceName", deviceName)
                put("deviceType", "ANDROID")
                put("port", DEFAULT_TRANSFER_PORT)
                put("version", 1)
            }
            sendToAllInterfaces(json.toString().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.d(TAG, "Broadcast error: ${e.message}")
        }
    }

    private fun sendToAllInterfaces(data: ByteArray) {
        // Send to global broadcast 255.255.255.255
        try {
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(data, data.size, broadcastAddr, DISCOVERY_PORT)
            socket?.send(packet)
        } catch (e: Exception) { }

        // Also broadcast on each interface broadcast address for best Wi-Fi compatibility
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val bcast = interfaceAddress.broadcast
                    if (bcast != null) {
                        val ifPacket = DatagramPacket(data, data.size, bcast, DISCOVERY_PORT)
                        socket?.send(ifPacket)
                    }
                }
            }
        } catch (e: Exception) { }
    }

    private fun cleanupStalePeers() {
        val now = System.currentTimeMillis()
        val staleKeys = _peers.filter { (now - it.value.lastSeen) > 6000 }.keys
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
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "IP lookup error: ${e.message}")
        }
        return "127.0.0.1"
    }
}
