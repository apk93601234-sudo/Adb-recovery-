package com.example.core.adb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure-Kotlin implementation of the Android Debug Bridge (ADB) binary wire protocol
 * over both local TCP sockets and USB OTG Host bulk endpoints.
 */
object AdbWire {
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257
    const val A_AUTH = 0x48545541

    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSAPUBLICKEY = 3

    const val VERSION = 0x01000000
    const val MAX_PAYLOAD = 256 * 1024
    const val HEADER_SIZE = 24
}

data class AdbPacket(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = ByteArray(0),
) {
    fun encode(): ByteArray {
        val checksum = data.sumOf { it.toInt() and 0xFF }
        val buf = ByteBuffer.allocate(AdbWire.HEADER_SIZE + data.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(data.size)
        buf.putInt(checksum)
        buf.putInt(command xor -0x1)
        if (data.isNotEmpty()) buf.put(data)
        return buf.array()
    }
}

interface AdbTransport {
    val description: String
    fun writePacket(packet: AdbPacket)
    fun readPacket(): AdbPacket
    fun close()
}

class TcpAdbTransport(
    private val host: String,
    private val port: Int,
    connectTimeoutMs: Int = 4000,
) : AdbTransport {

    private val socket = Socket()
    private val input: InputStream
    private val output: OutputStream

    init {
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket.soTimeout = 15000
        socket.tcpNoDelay = true
        input = socket.getInputStream()
        output = socket.getOutputStream()
    }

    override val description: String = "TCP $host:$port"

    override fun writePacket(packet: AdbPacket) {
        synchronized(output) {
            val headerBuf = ByteBuffer.allocate(AdbWire.HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val checksum = packet.data.sumOf { it.toInt() and 0xFF }
            headerBuf.putInt(packet.command)
            headerBuf.putInt(packet.arg0)
            headerBuf.putInt(packet.arg1)
            headerBuf.putInt(packet.data.size)
            headerBuf.putInt(checksum)
            headerBuf.putInt(packet.command xor -0x1)
            output.write(headerBuf.array())
            if (packet.data.isNotEmpty()) {
                output.write(packet.data)
            }
            output.flush()
        }
    }

    override fun readPacket(): AdbPacket {
        val header = readFully(AdbWire.HEADER_SIZE)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.getInt()
        val arg0 = buf.getInt()
        val arg1 = buf.getInt()
        val len = buf.getInt()
        buf.getInt() // checksum
        buf.getInt() // magic
        if (len < 0 || len > 1024 * 1024) {
            throw IOException("Invalid ADB packet length: $len")
        }
        val data = if (len > 0) readFully(len) else ByteArray(0)
        return AdbPacket(cmd, arg0, arg1, data)
    }

    private fun readFully(length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(out, offset, length - offset)
            if (read < 0) throw EOFException("ADB TCP socket closed")
            offset += read
        }
        return out
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

class UsbAdbTransport(
    private val connection: UsbDeviceConnection,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
    override val description: String,
) : AdbTransport {

    private val writeLock = Any()

    override fun writePacket(packet: AdbPacket) {
        synchronized(writeLock) {
            val header = ByteBuffer.allocate(AdbWire.HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    val checksum = packet.data.sumOf { it.toInt() and 0xFF }
                    putInt(packet.command)
                    putInt(packet.arg0)
                    putInt(packet.arg1)
                    putInt(packet.data.size)
                    putInt(checksum)
                    putInt(packet.command xor -0x1)
                }.array()

            bulkOut(header)
            if (packet.data.isNotEmpty()) {
                bulkOut(packet.data)
            }
        }
    }

    private fun bulkOut(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val chunk = bytes.size - offset
            val written = connection.bulkTransfer(endpointOut, bytes, offset, chunk, 10000)
            if (written <= 0) throw IOException("USB bulk OUT failed ($written)")
            offset += written
        }
        if (bytes.isNotEmpty() && bytes.size % endpointOut.maxPacketSize == 0) {
            connection.bulkTransfer(endpointOut, ByteArray(0), 0, 0, 2000)
        }
    }

    override fun readPacket(): AdbPacket {
        val header = bulkInExact(AdbWire.HEADER_SIZE)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.getInt()
        val arg0 = buf.getInt()
        val arg1 = buf.getInt()
        val len = buf.getInt()
        buf.getInt()
        buf.getInt()
        if (len < 0 || len > 1024 * 1024) {
            throw IOException("Invalid USB ADB length: $len")
        }
        val data = if (len > 0) bulkInExact(len) else ByteArray(0)
        return AdbPacket(cmd, arg0, arg1, data)
    }

    private fun bulkInExact(length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = connection.bulkTransfer(endpointIn, out, offset, length - offset, 15000)
            if (read < 0) throw IOException("USB bulk IN timeout or disconnected ($read)")
            offset += read
        }
        return out
    }

    override fun close() {
        runCatching { connection.close() }
    }
}

data class AdbBannerInfo(
    val rawBanner: String,
    val state: String,
    val productName: String,
    val model: String,
    val device: String,
)

class AdbClient(
    private val transport: AdbTransport,
    private val keyManager: AdbKeyManager,
) {
    private val nextLocalId = AtomicInteger(1)
    private var maxPayload = 4096
    private val ioLock = Any()

    suspend fun connect(onAwaitingAuth: () -> Unit = {}): AdbBannerInfo = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            transport.writePacket(
                AdbPacket(
                    command = AdbWire.A_CNXN,
                    arg0 = AdbWire.VERSION,
                    arg1 = AdbWire.MAX_PAYLOAD,
                    data = "host::recoveryadb\u0000".toByteArray(Charsets.UTF_8),
                )
            )

            var sentSignature = false
            while (true) {
                val pkt = transport.readPacket()
                when (pkt.command) {
                    AdbWire.A_CNXN -> {
                        maxPayload = pkt.arg1.coerceIn(4096, AdbWire.MAX_PAYLOAD)
                        return@withContext parseBanner(String(pkt.data, Charsets.UTF_8))
                    }
                    AdbWire.A_AUTH -> {
                        if (pkt.arg0 == AdbWire.AUTH_TOKEN) {
                            if (!sentSignature) {
                                sentSignature = true
                                val sig = keyManager.signToken(pkt.data)
                                transport.writePacket(
                                    AdbPacket(AdbWire.A_AUTH, AdbWire.AUTH_SIGNATURE, 0, sig)
                                )
                            } else {
                                onAwaitingAuth()
                                val pubPayload = keyManager.getAdbPublicKeyPayload()
                                transport.writePacket(
                                    AdbPacket(AdbWire.A_AUTH, AdbWire.AUTH_RSAPUBLICKEY, 0, pubPayload)
                                )
                            }
                        }
                    }
                    else -> throw IOException("Unexpected ADB handshake packet: 0x${pkt.command.toString(16)}")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IOException("Unreachable")
        }
    }

    suspend fun runShell(command: String): String = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val localId = nextLocalId.getAndIncrement()
            val destination = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            transport.writePacket(AdbPacket(AdbWire.A_OPEN, localId, 0, destination))

            var remoteId = 0
            val result = StringBuilder()

            while (true) {
                val pkt = transport.readPacket()
                when (pkt.command) {
                    AdbWire.A_OKAY -> {
                        if (pkt.arg1 == localId) {
                            remoteId = pkt.arg0
                        }
                    }
                    AdbWire.A_WRTE -> {
                        if (pkt.arg1 == localId) {
                            result.append(String(pkt.data, Charsets.UTF_8))
                            transport.writePacket(AdbPacket(AdbWire.A_OKAY, localId, pkt.arg0))
                        }
                    }
                    AdbWire.A_CLSE -> {
                        if (pkt.arg1 == localId || pkt.arg0 == remoteId) {
                            transport.writePacket(AdbPacket(AdbWire.A_CLSE, localId, pkt.arg0))
                            break
                        }
                    }
                }
            }
            result.toString().trimEnd()
        }
    }

    suspend fun streamShell(
        command: String,
        isStopped: () -> Boolean,
        onTextChunk: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val localId = nextLocalId.getAndIncrement()
            val destination = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            transport.writePacket(AdbPacket(AdbWire.A_OPEN, localId, 0, destination))
            var remoteId = 0

            while (!isStopped()) {
                val pkt = try {
                    transport.readPacket()
                } catch (_: Exception) {
                    break
                }
                when (pkt.command) {
                    AdbWire.A_OKAY -> if (pkt.arg1 == localId) remoteId = pkt.arg0
                    AdbWire.A_WRTE -> {
                        if (pkt.arg1 == localId) {
                            onTextChunk(String(pkt.data, Charsets.UTF_8))
                            transport.writePacket(AdbPacket(AdbWire.A_OKAY, localId, pkt.arg0))
                        }
                    }
                    AdbWire.A_CLSE -> {
                        transport.writePacket(AdbPacket(AdbWire.A_CLSE, localId, pkt.arg0))
                        break
                    }
                }
            }
            if (remoteId != 0) {
                runCatching { transport.writePacket(AdbPacket(AdbWire.A_CLSE, localId, remoteId)) }
            }
        }
    }

    suspend fun reboot(target: String = "") = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val localId = nextLocalId.getAndIncrement()
            val svc = "reboot:$target\u0000".toByteArray(Charsets.UTF_8)
            transport.writePacket(AdbPacket(AdbWire.A_OPEN, localId, 0, svc))
            while (true) {
                val pkt = transport.readPacket()
                if (pkt.command == AdbWire.A_OKAY && pkt.arg1 == localId) {
                    transport.writePacket(AdbPacket(AdbWire.A_CLSE, localId, pkt.arg0))
                    break
                }
                if (pkt.command == AdbWire.A_CLSE) break
            }
        }
    }

    suspend fun pushFile(
        input: InputStream,
        totalBytes: Long,
        remotePath: String,
        onProgress: (Float) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        synchronized(ioLock) {
            val localId = nextLocalId.getAndIncrement()
            transport.writePacket(
                AdbPacket(AdbWire.A_OPEN, localId, 0, "sync:\u0000".toByteArray(Charsets.UTF_8))
            )
            var remoteId = 0
            while (remoteId == 0) {
                val pkt = transport.readPacket()
                if (pkt.command == AdbWire.A_OKAY && pkt.arg1 == localId) remoteId = pkt.arg0
                if (pkt.command == AdbWire.A_CLSE) throw IOException("Sync service refused")
            }

            fun writeSyncPayload(bytes: ByteArray) {
                var off = 0
                while (off < bytes.size) {
                    val sliceLen = minOf(maxPayload, bytes.size - off)
                    val slice = bytes.copyOfRange(off, off + sliceLen)
                    transport.writePacket(AdbPacket(AdbWire.A_WRTE, localId, remoteId, slice))
                    while (true) {
                        val ack = transport.readPacket()
                        if (ack.command == AdbWire.A_OKAY) break
                        if (ack.command == AdbWire.A_CLSE) throw IOException("Sync closed mid-transfer")
                    }
                    off += sliceLen
                }
            }

            val spec = "$remotePath,0644".toByteArray(Charsets.UTF_8)
            val sendHdr = ByteBuffer.allocate(8 + spec.size).order(ByteOrder.LITTLE_ENDIAN)
                .put("SEND".toByteArray(Charsets.US_ASCII))
                .putInt(spec.size)
                .put(spec)
                .array()
            writeSyncPayload(sendHdr)

            val buffer = ByteArray(minOf(65536, maxPayload - 8))
            var sent = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val dataHdr = ByteBuffer.allocate(8 + read).order(ByteOrder.LITTLE_ENDIAN)
                    .put("DATA".toByteArray(Charsets.US_ASCII))
                    .putInt(read)
                    .put(buffer, 0, read)
                    .array()
                writeSyncPayload(dataHdr)
                sent += read
                if (totalBytes > 0) {
                    onProgress((sent.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                }
            }

            val mtime = (System.currentTimeMillis() / 1000L).toInt()
            val doneHdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .put("DONE".toByteArray(Charsets.US_ASCII))
                .putInt(mtime)
                .array()
            writeSyncPayload(doneHdr)

            var statusMsg = "OKAY"
            while (true) {
                val pkt = transport.readPacket()
                if (pkt.command == AdbWire.A_WRTE && pkt.data.size >= 4) {
                    val tag = String(pkt.data, 0, 4, Charsets.US_ASCII)
                    val msg = if (pkt.data.size > 8) String(pkt.data, 8, pkt.data.size - 8) else ""
                    statusMsg = if (msg.isNotBlank()) "$tag: $msg" else tag
                    transport.writePacket(AdbPacket(AdbWire.A_OKAY, localId, pkt.arg0))
                    break
                }
                if (pkt.command == AdbWire.A_CLSE) break
            }
            transport.writePacket(AdbPacket(AdbWire.A_CLSE, localId, remoteId))
            statusMsg
        }
    }

    fun close() = transport.close()

    private fun parseBanner(raw: String): AdbBannerInfo {
        val clean = raw.trimEnd('\u0000', '\n', '\r')
        val state = clean.substringBefore("::", "device")
        val propsPart = clean.substringAfter("::", "")
        val map = propsPart.split(';')
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
            }.toMap()
        return AdbBannerInfo(
            rawBanner = clean,
            state = state.ifBlank { "device" },
            productName = map["ro.product.name"].orEmpty(),
            model = map["ro.product.model"].orEmpty(),
            device = map["ro.product.device"].orEmpty(),
        )
    }
}
