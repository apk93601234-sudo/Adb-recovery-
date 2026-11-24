package com.example.core.adb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Pure-Kotlin Fastboot USB OTG client supporting bootloader variable inspection and reboots.
 */
class FastbootClient(
    private val connection: UsbDeviceConnection,
    private val endpointIn: UsbEndpoint,
    private val endpointOut: UsbEndpoint,
) {
    data class FastbootResult(
        val success: Boolean,
        val infoLines: List<String>,
        val message: String,
    )

    suspend fun runCommand(command: String): FastbootResult = withContext(Dispatchers.IO) {
        val cmdBytes = command.toByteArray(Charsets.US_ASCII)
        val written = connection.bulkTransfer(endpointOut, cmdBytes, cmdBytes.size, 5000)
        if (written <= 0) {
            throw IOException("Fastboot USB bulk write failed ($written)")
        }

        val infoLines = mutableListOf<String>()
        val buf = ByteArray(256)
        while (true) {
            val read = connection.bulkTransfer(endpointIn, buf, buf.size, 8000)
            if (read < 4) {
                throw IOException("Fastboot USB response timeout ($read)")
            }
            val resp = String(buf, 0, read, Charsets.US_ASCII)
            val prefix = resp.take(4)
            val body = resp.drop(4).trimEnd('\u0000', '\n', '\r')
            when (prefix) {
                "INFO" -> infoLines += body
                "OKAY" -> return@withContext FastbootResult(true, infoLines, body)
                "FAIL" -> return@withContext FastbootResult(false, infoLines, body)
                "DATA" -> return@withContext FastbootResult(true, infoLines, "DATA:$body")
                else -> return@withContext FastbootResult(false, infoLines, resp)
            }
        }
        @Suppress("UNREACHABLE_CODE")
        FastbootResult(false, infoLines, "")
    }

    fun close() {
        runCatching { connection.close() }
    }
}
