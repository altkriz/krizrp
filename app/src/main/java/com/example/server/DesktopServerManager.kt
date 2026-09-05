package com.example.server

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.repository.CharacterRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChubRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

data class ServerEvent(val type: String, val payload: String = "{}")

object DesktopServerManager {
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl = _serverUrl.asStateFlow()

    private val _ipAddress = MutableStateFlow("127.0.0.1")
    val ipAddress = _ipAddress.asStateFlow()

    private val _port = MutableStateFlow(8080)
    val port = _port.asStateFlow()

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients = _connectedClients.asStateFlow()

    private val _requestCount = MutableStateFlow(0)
    val requestCount = _requestCount.asStateFlow()

    private val _lastLog = MutableStateFlow("Desktop server is idle")
    val lastLog = _lastLog.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val eventFlow = _eventFlow.asSharedFlow()

    private var activeServer: DesktopHttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun updateRunningState(running: Boolean, url: String = "") {
        _isRunning.value = running
        _serverUrl.value = url
        if (running) {
            _lastLog.value = "Server active at $url"
        } else {
            _lastLog.value = "Server stopped"
            _connectedClients.value = 0
        }
    }

    fun setPort(newPort: Int) {
        _port.value = newPort
    }

    fun incrementRequestCount() {
        _requestCount.value += 1
    }

    fun updateClientCount(count: Int) {
        _connectedClients.value = count
    }

    fun log(message: String) {
        _lastLog.value = message
    }

    fun broadcastEvent(type: String, payload: String = "{}") {
        _eventFlow.tryEmit(ServerEvent(type, payload))
    }

    fun getLocalIpAddress(context: Context): String {
        // 1. Try modern ConnectivityManager (Android 10+)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            if (activeNetwork != null) {
                val linkProps = cm.getLinkProperties(activeNetwork)
                val linkAddresses = linkProps?.linkAddresses
                if (linkAddresses != null) {
                    for (linkAddr in linkAddresses) {
                        val addr = linkAddr.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress
                            if (host != null && !host.startsWith("127.")) {
                                _ipAddress.value = host
                                return host
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Try network interfaces prioritizing Wi-Fi, Hotspot, and Ethernet
        try {
            val interfaceList = mutableListOf<NetworkInterface>()
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (nif.isLoopback || !nif.isUp) continue
                interfaceList.add(nif)
            }

            // Sort so wlan, ap, rndis, eth come first
            interfaceList.sortWith { a, b ->
                fun score(name: String): Int = when {
                    name.startsWith("wlan", ignoreCase = true) -> 1
                    name.startsWith("ap", ignoreCase = true) -> 2
                    name.startsWith("rndis", ignoreCase = true) -> 3
                    name.startsWith("eth", ignoreCase = true) -> 4
                    else -> 10
                }
                score(a.name).compareTo(score(b.name))
            }

            for (nif in interfaceList) {
                val addresses = nif.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            _ipAddress.value = host
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback: WifiManager legacy
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0" && !ip.startsWith("127.")) {
                    _ipAddress.value = ip
                    return ip
                }
            }
        } catch (_: Exception) {}

        _ipAddress.value = "127.0.0.1"
        return "127.0.0.1"
    }

    fun startService(context: Context, customPort: Int = _port.value) {
        _port.value = customPort
        val intent = Intent(context, DesktopServerService::class.java).apply {
            action = DesktopServerService.ACTION_START
            putExtra(DesktopServerService.EXTRA_PORT, customPort)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, DesktopServerService::class.java).apply {
            action = DesktopServerService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun startHttpServer(
        context: Context,
        port: Int,
        db: AppDatabase,
        characterRepository: CharacterRepository,
        chatRepository: ChatRepository,
        settingsRepository: SettingsRepository,
        chubRepository: ChubRepository
    ) {
        stopHttpServer()
        val ip = getLocalIpAddress(context)
        val server = DesktopHttpServer(
            context = context,
            port = port,
            characterRepository = characterRepository,
            chatRepository = chatRepository,
            settingsRepository = settingsRepository,
            chubRepository = chubRepository
        )
        activeServer = server
        server.start()
        val url = "http://$ip:$port"
        updateRunningState(true, url)
    }

    fun stopHttpServer() {
        try {
            activeServer?.stop()
        } catch (_: Exception) {}
        activeServer = null
        updateRunningState(false)
    }
}
