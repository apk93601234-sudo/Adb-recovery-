package com.example.core.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class LanCandidate(
    val host: String,
    val port: Int,
    val latencyMs: Long,
)

/**
 * 100% offline local LAN / Hotspot port-5555 ADB discovery scanner.
 */
object LocalLanScanner {

    fun getLocalIpv4Addresses(): List<String> {
        val list = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            list += ip
                        }
                    }
                }
            }
        }
        return list.distinct()
    }

    suspend fun scanSubnetForAdb(port: Int = 5555): List<LanCandidate> = withContext(Dispatchers.IO) {
        val localIps = getLocalIpv4Addresses()
        if (localIps.isEmpty()) return@withContext emptyList()

        val prefixes = localIps.map { ip ->
            val parts = ip.split('.')
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else ""
        }.filter { it.isNotBlank() }.distinct()

        val targets = buildList {
            add("127.0.0.1")
            for (prefix in prefixes) {
                for (hostNum in 1..254) {
                    val candidate = "$prefix.$hostNum"
                    if (candidate !in localIps) add(candidate)
                }
            }
        }

        val semaphore = Semaphore(48)
        coroutineScope {
            targets.map { host ->
                async {
                    semaphore.withPermit {
                        probePort(host, port)
                    }
                }
            }.awaitAll().filterNotNull().sortedBy { it.latencyMs }
        }
    }

    private fun probePort(host: String, port: Int): LanCandidate? {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 220)
                LanCandidate(host, port, System.currentTimeMillis() - start)
            }
        } catch (_: Exception) {
            null
        }
    }
}
