package com.example.core.inspector

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class InspectedFileReport(
    val uriString: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val md5: String,
    val detectedFormat: String,
    val details: List<Pair<String, String>>,
    val zipEntriesPreview: List<String>,
)

/**
 * 100% offline inspector for recovery .img, OTA .zip, payload.bin, and .apk files
 * using zero-permission Android Storage Access Framework (SAF).
 */
object PackageImageInspector {

    suspend fun inspectUri(context: Context, uri: Uri): InspectedFileReport = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName = uri.lastPathSegment ?: "unknown"
        var reportedSize = -1L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                if (sizeIdx >= 0) reportedSize = cursor.getLong(sizeIdx)
            }
        }

        val shaDigest = MessageDigest.getInstance("SHA-256")
        val md5Digest = MessageDigest.getInstance("MD5")
        val header = ByteArray(2048)
        var headerLen = 0
        var actualSize = 0L

        resolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                if (headerLen < header.size) {
                    val copyLen = minOf(read, header.size - headerLen)
                    System.arraycopy(buf, 0, header, headerLen, copyLen)
                    headerLen += copyLen
                }
                shaDigest.update(buf, 0, read)
                md5Digest.update(buf, 0, read)
                actualSize += read
            }
        } ?: error("Cannot open selected file stream")

        val shaHex = shaDigest.digest().joinToString("") { "%02x".format(it) }
        val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
        val finalSize = if (actualSize > 0) actualSize else reportedSize

        val details = mutableListOf<Pair<String, String>>()
        val zipPreview = mutableListOf<String>()
        var detectedFormat = "Binary / Unknown"

        val magicAscii8 = if (headerLen >= 8) String(header, 0, 8, Charsets.US_ASCII) else ""
        val magicAscii4 = if (headerLen >= 4) String(header, 0, 4, Charsets.US_ASCII) else ""

        when {
            magicAscii8 == "ANDROID!" && headerLen >= 48 -> {
                detectedFormat = "Android Boot / Recovery Image (ANDROID!)"
                parseBootImageHeader(header, details)
            }
            headerLen >= 4 &&
                (header[0].toInt() and 0xFF) == 0x3A &&
                (header[1].toInt() and 0xFF) == 0xFF &&
                (header[2].toInt() and 0xFF) == 0x26 &&
                (header[3].toInt() and 0xFF) == 0xED -> {
                detectedFormat = "Android Sparse Image (simg)"
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val major = bb.getShort(4).toInt() and 0xFFFF
                val minor = bb.getShort(6).toInt() and 0xFFFF
                val blkSize = bb.getInt(12).toLong() and 0xFFFFFFFFL
                val totalBlks = bb.getInt(16).toLong() and 0xFFFFFFFFL
                val totalChunks = bb.getInt(20).toLong() and 0xFFFFFFFFL
                details += "Sparse Version" to "$major.$minor"
                details += "Block Size" to "$blkSize bytes"
                details += "Total Blocks" to "$totalBlks (~${(blkSize * totalBlks) / (1024 * 1024)} MB raw)"
                details += "Total Chunks" to "$totalChunks"
            }
            magicAscii4 == "CrAU" -> {
                detectedFormat = "Android A/B OTA Payload (CrAU)"
                if (headerLen >= 12) {
                    val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                    val version = bb.getLong(4)
                    details += "Payload Format Version" to version.toString()
                }
            }
            headerLen >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() -> {
                val lowerName = fileName.lowercase()
                var hasPayload = false
                var hasUpdater = false
                var hasManifest = false
                var entryCount = 0

                resolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(stream).use { zis ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            entryCount++
                            val eName = entry.name
                            if (zipPreview.size < 30) {
                                val kb = if (entry.size > 0) " (${entry.size / 1024} KB)" else ""
                                zipPreview += "$eName$kb"
                            }
                            if (eName == "payload.bin") hasPayload = true
                            if (eName.contains("updater-script") || eName.contains("update-binary")) hasUpdater = true
                            if (eName == "AndroidManifest.xml") hasManifest = true
                            zis.closeEntry()
                            if (entryCount > 600) break
                        }
                    }
                }

                detectedFormat = when {
                    hasPayload -> "A/B OTA Update Package (ZIP + payload.bin)"
                    hasUpdater -> "Recovery Flashable ZIP (update-binary)"
                    hasManifest || lowerName.endsWith(".apk") -> "Android Package Archive (APK)"
                    else -> "ZIP Archive"
                }
                details += "Archive Entries Scanned" to if (entryCount > 600) "600+" else "$entryCount"
                details += "Has payload.bin (A/B)" to if (hasPayload) "Yes" else "No"
                details += "Has Recovery Script" to if (hasUpdater) "Yes" else "No"
                details += "Has AndroidManifest" to if (hasManifest) "Yes" else "No"
            }
        }

        InspectedFileReport(
            uriString = uri.toString(),
            fileName = fileName,
            sizeBytes = finalSize,
            sha256 = shaHex,
            md5 = md5Hex,
            detectedFormat = detectedFormat,
            details = details,
            zipEntriesPreview = zipPreview,
        )
    }

    private fun parseBootImageHeader(header: ByteArray, details: MutableList<Pair<String, String>>) {
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val kernelSize = bb.getInt(8).toLong() and 0xFFFFFFFFL
        val kernelAddr = bb.getInt(12).toLong() and 0xFFFFFFFFL
        val ramdiskSize = bb.getInt(16).toLong() and 0xFFFFFFFFL
        val pageSize = bb.getInt(36).toLong() and 0xFFFFFFFFL
        val headerVersion = bb.getInt(40)
        val osVersionRaw = bb.getInt(44)

        details += "Header Version" to "v$headerVersion"
        details += "Kernel Size" to "${kernelSize / 1024} KB (0x${kernelAddr.toString(16)})"
        details += "Ramdisk Size" to "${ramdiskSize / 1024} KB"
        details += "Page Size" to "$pageSize bytes"

        if (osVersionRaw != 0) {
            val osVer = osVersionRaw ushr 11
            val major = (osVer ushr 14) and 0x7F
            val minor = (osVer ushr 7) and 0x7F
            val patch = osVer and 0x7F
            val year = 2000 + ((osVersionRaw ushr 4) and 0x7F)
            val month = osVersionRaw and 0x0F
            details += "Declared Android OS" to "$major.$minor.$patch (Patch $year-${"%02d".format(month)})"
        }
    }
}
