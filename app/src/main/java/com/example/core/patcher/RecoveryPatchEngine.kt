package com.example.core.patcher

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Pure-Kotlin 100% offline Android Boot / Recovery / VendorBoot Image Parser & Ramdisk Patcher
 * with native support for MediaTek (MTK) headers, USB UDC gadgets, LZ4 Legacy/Frame, GZIP, and CPIO newc.
 */
object RecoveryPatchEngine {

    private const val MTK_HEADER_MAGIC = 0x58881688
    private const val MTK_HEADER_SIZE = 512

    private data class UnpackedPayload(
        val rawBytes: ByteArray,
        val headerMagic: String,
        val headerVersion: Int,
        val pageSize: Int,
        val headerSize: Int,
        val kernelSize: Int,
        val ramdiskOffset: Int,
        val ramdiskSize: Int,
        val hasMtkRamdiskHeader: Boolean,
        val mtkRamdiskHeader: ByteArray?,
        val ramdiskCompression: RamdiskCompression,
        val cpioBytes: ByteArray?,
        val cmdline: String,
        val osVersionText: String,
        val isMediaTek: Boolean,
        val mediaTekReasons: List<String>,
        val hasAvbFooter: Boolean,
        val sha256Before: String,
    )

    suspend fun analyzeImage(context: Context, uri: Uri): ParsedBootImageInfo = withContext(Dispatchers.IO) {
        val (fileName, bytes) = readUriBytes(context, uri)
        val unpacked = parseAndUnpack(bytes)
        val archive = unpacked.cpioBytes?.let { CpioArchive.parse(it) }
        val entryNames = archive?.entries?.map { it.name }.orEmpty()

        val (layout, indicators) = classifyRecoveryLayout(
            fileName = fileName,
            cmdline = unpacked.cmdline,
            headerMagic = unpacked.headerMagic,
            entryNames = entryNames,
        )

        val mtkReasons = unpacked.mediaTekReasons.toMutableList()
        entryNames.filter {
            val l = it.lowercase()
            l.contains("mt6") || l.contains("mt8") || l.contains("mediatek") || l.contains("mtk")
        }.take(4).forEach { mtkReasons += "Ramdisk entry: $it" }

        val isMtk = unpacked.isMediaTek || mtkReasons.isNotEmpty()
        val mtkDetails = if (isMtk) {
            mtkReasons.distinct().joinToString(" • ").ifBlank { "MediaTek signature detected" }
        } else {
            "Standard AOSP / Non-MTK header"
        }

        ParsedBootImageInfo(
            sourceUri = uri.toString(),
            fileName = fileName,
            fileSizeBytes = bytes.size.toLong(),
            headerMagic = unpacked.headerMagic,
            headerVersion = unpacked.headerVersion,
            pageSize = unpacked.pageSize,
            kernelSize = unpacked.kernelSize,
            ramdiskSize = unpacked.ramdiskSize,
            cmdline = unpacked.cmdline,
            osVersionText = unpacked.osVersionText,
            isMediaTekImage = isMtk,
            mediaTekDetails = mtkDetails,
            ramdiskCompression = unpacked.ramdiskCompression,
            cpioEntryCount = entryNames.size,
            recoveryLayout = layout,
            recoveryIndicators = indicators,
            hasAvbFooter = unpacked.hasAvbFooter,
            sha256Before = unpacked.sha256Before,
        )
    }

    suspend fun patchAndSaveImage(
        context: Context,
        sourceUri: Uri,
        destUri: Uri,
        options: PatchOptions,
        outputFileName: String,
    ): PatchResultReport = withContext(Dispatchers.IO) {
        val (_, originalBytes) = readUriBytes(context, sourceUri)
        val unpacked = parseAndUnpack(originalBytes)
        val cpioRaw = unpacked.cpioBytes
            ?: error("Ramdisk is empty or uses an unsupported compression format (${unpacked.ramdiskCompression}).")

        val archive = CpioArchive.parse(cpioRaw)
        val changes = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (unpacked.hasAvbFooter) {
            warnings += "Original image contains an AVB 2.0 footer (AVBf). Flashing requires an unlocked bootloader (or vbmeta --disable-verity --disable-verification)."
        }

        // 1. Patch prop files inside ramdisk
        val propUpdates = linkedMapOf<String, String>()
        if (options.autoAuthorizeAdb) {
            propUpdates["ro.adb.secure"] = "0"
            propUpdates["ro.debuggable"] = "1"
            propUpdates["persist.sys.usb.config"] = "adb"
            propUpdates["sys.usb.config"] = "adb"
            propUpdates["persist.service.adb.enable"] = "1"
            propUpdates["persist.service.debuggable"] = "1"
        }
        if (options.enableRootShellInRecovery) {
            propUpdates["ro.secure"] = "0"
            propUpdates["service.adb.root"] = "1"
        }

        if (propUpdates.isNotEmpty()) {
            val propTargets = listOf(
                "default.prop",
                "prop.default",
                "system/etc/prop.default",
                "first_stage_ramdisk/default.prop",
            )
            var patchedPropFiles = 0
            for (target in propTargets) {
                val existing = archive.find(target)
                if (existing != null) {
                    val updated = mergeProperties(String(existing.data, Charsets.UTF_8), propUpdates)
                    archive.upsertFile(target, updated.toByteArray(Charsets.UTF_8), mode = 0x81A4L /* 0100644 */)
                    patchedPropFiles++
                    changes += "Patched $target (${propUpdates.keys.joinToString(", ")})"
                }
            }
            if (patchedPropFiles == 0) {
                val created = mergeProperties("# Generated by Recovery ADB Offline Patcher\n", propUpdates)
                archive.upsertFile("default.prop", created.toByteArray(Charsets.UTF_8), mode = 0x81A4L)
                changes += "Created default.prop with auto-auth ADB & debug properties"
            }
        }

        // 2. Inject Recovery ADB Debug menu trigger script + init.recovery.adb_debug.rc
        if (options.addRecoveryAdbMenuTrigger) {
            archive.ensureDirectory("sbin")
            val scriptContent = buildRecoveryAdbScript(
                includeMtkFix = options.enableMediaTekUsbGadgetFix,
                includeRoot = options.enableRootShellInRecovery,
            )
            archive.upsertFile(
                "sbin/enable_adb_debug.sh",
                scriptContent.toByteArray(Charsets.UTF_8),
                mode = 0x81EDL, // 0100755 executable
            )
            changes += "Injected /sbin/enable_adb_debug.sh (0755)"

            val rcContent = buildRecoveryAdbRc(
                includeMtkFix = options.enableMediaTekUsbGadgetFix,
                autoAuthorize = options.autoAuthorizeAdb,
                rootShell = options.enableRootShellInRecovery,
            )
            archive.upsertFile(
                "init.recovery.adb_debug.rc",
                rcContent.toByteArray(Charsets.UTF_8),
                mode = 0x81E8L, // 0100750
            )
            changes += "Injected /init.recovery.adb_debug.rc service & USB ADB triggers"

            // Hook import into existing init.rc / init.recovery.*.rc if present
            val rcCandidates = archive.entries.map { it.name }.filter {
                val l = it.lowercase()
                l == "init.rc" || (l.startsWith("init.recovery.") && l.endsWith(".rc") && l != "init.recovery.adb_debug.rc")
            }
            for (rcPath in rcCandidates) {
                val entry = archive.find(rcPath) ?: continue
                val text = String(entry.data, Charsets.UTF_8)
                if (!text.contains("init.recovery.adb_debug.rc")) {
                    val patched = "import /init.recovery.adb_debug.rc\n$text"
                    archive.upsertFile(rcPath, patched.toByteArray(Charsets.UTF_8), mode = entry.mode)
                    changes += "Hooked import /init.recovery.adb_debug.rc into $rcPath"
                }
            }

            // Add recovery menu config descriptor for custom recovery UIs
            archive.ensureDirectory("res")
            val menuCfg = buildString {
                appendLine("# Recovery ADB Menu Action Entry")
                appendLine("TITLE=Enable ADB Debug (Auto-Auth)")
                appendLine("PROPERTY=sys.recovery.adb_debug=1")
                appendLine("SCRIPT=/sbin/enable_adb_debug.sh")
                appendLine("AUTO_AUTH=1")
            }
            archive.upsertFile("res/recovery_adb_menu.conf", menuCfg.toByteArray(Charsets.UTF_8), mode = 0x81A4L)
            changes += "Added /res/recovery_adb_menu.conf menu descriptor"
        }

        // 3. Serialize CPIO and recompress matching original format
        val newCpio = archive.serialize()
        val recompressedPayload = when (unpacked.ramdiskCompression) {
            RamdiskCompression.GZIP -> gzipCompress(newCpio)
            RamdiskCompression.LZ4_LEGACY -> Lz4Codec.compressLegacy(newCpio)
            RamdiskCompression.LZ4_FRAME -> Lz4Codec.compressFrame(newCpio)
            RamdiskCompression.RAW_CPIO -> newCpio
            else -> gzipCompress(newCpio)
        }

        val finalRamdisk = if (unpacked.hasMtkRamdiskHeader && unpacked.mtkRamdiskHeader != null) {
            val mtkHdr = unpacked.mtkRamdiskHeader.copyOf()
            ByteBuffer.wrap(mtkHdr).order(ByteOrder.LITTLE_ENDIAN).putInt(4, recompressedPayload.size)
            changes += "Updated MediaTek 512-byte MTK ramdisk header size -> ${recompressedPayload.size} bytes"
            mtkHdr + recompressedPayload
        } else {
            recompressedPayload
        }

        val repackedImage = rebuildBootImage(originalBytes, unpacked, finalRamdisk)
        changes += "Repacked ${unpacked.headerMagic} v${unpacked.headerVersion} (${repackedImage.size} bytes)"

        context.contentResolver.openOutputStream(destUri)?.use { out ->
            out.write(repackedImage)
            out.flush()
        } ?: error("Could not open destination output stream")

        val shaAfter = sha256Hex(repackedImage)
        PatchResultReport(
            outputFileName = outputFileName,
            outputSizeBytes = repackedImage.size.toLong(),
            sha256After = shaAfter,
            appliedChanges = changes,
            warnings = warnings,
        )
    }

    private fun parseAndUnpack(bytes: ByteArray): UnpackedPayload {
        val shaBefore = sha256Hex(bytes)
        val hasAvb = bytes.size >= 64 && String(bytes, bytes.size - 64, 4, Charsets.US_ASCII) == "AVBf"

        if (bytes.size < 256) error("File too small to be an Android boot/recovery image")
        val magic8 = String(bytes, 0, 8, Charsets.US_ASCII)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (magic8 == "ANDROID!") {
            val headerVersion = bb.getInt(40)
            if (headerVersion >= 3) {
                val kernelSize = bb.getInt(8)
                val ramdiskSize = bb.getInt(12)
                val osVerRaw = bb.getInt(16)
                val headerSize = bb.getInt(20)
                val pageSize = 4096
                val cmdline = readCString(bytes, 44, 1536)

                val kernelOff = alignUp(headerSize.coerceAtLeast(1580), pageSize)
                val ramdiskOff = kernelOff + alignUp(kernelSize, pageSize)
                return unpackRamdiskSection(
                    rawBytes = bytes,
                    headerMagic = "ANDROID!",
                    headerVersion = headerVersion,
                    pageSize = pageSize,
                    headerSize = headerSize,
                    kernelSize = kernelSize,
                    ramdiskOffset = ramdiskOff,
                    ramdiskSize = ramdiskSize,
                    cmdline = cmdline,
                    osVersionText = decodeOsVersion(osVerRaw),
                    hasAvbFooter = hasAvb,
                    shaBefore = shaBefore,
                )
            } else {
                val kernelSize = bb.getInt(8)
                val ramdiskSize = bb.getInt(16)
                val pageSize = bb.getInt(36).coerceAtLeast(2048)
                val osVerRaw = bb.getInt(44)
                val cmdline = readCString(bytes, 64, 512)
                val kernelOff = pageSize
                val ramdiskOff = kernelOff + alignUp(kernelSize, pageSize)
                return unpackRamdiskSection(
                    rawBytes = bytes,
                    headerMagic = "ANDROID!",
                    headerVersion = headerVersion,
                    pageSize = pageSize,
                    headerSize = pageSize,
                    kernelSize = kernelSize,
                    ramdiskOffset = ramdiskOff,
                    ramdiskSize = ramdiskSize,
                    cmdline = cmdline,
                    osVersionText = decodeOsVersion(osVerRaw),
                    hasAvbFooter = hasAvb,
                    shaBefore = shaBefore,
                )
            }
        } else if (magic8 == "VNDRBOOT") {
            val headerVersion = bb.getInt(8)
            val pageSize = bb.getInt(12).coerceAtLeast(2048)
            val kernelCmdline = readCString(bytes, 28, 2048)
            val ramdiskSize = bb.getInt(24)
            val headerSize = if (headerVersion >= 4) 2128 else 2112
            val ramdiskOff = alignUp(headerSize, pageSize)
            return unpackRamdiskSection(
                rawBytes = bytes,
                headerMagic = "VNDRBOOT",
                headerVersion = headerVersion,
                pageSize = pageSize,
                headerSize = headerSize,
                kernelSize = 0,
                ramdiskOffset = ramdiskOff,
                ramdiskSize = ramdiskSize,
                cmdline = kernelCmdline,
                osVersionText = "Vendor Boot v$headerVersion",
                hasAvbFooter = hasAvb,
                shaBefore = shaBefore,
            )
        } else {
            error("Unsupported image magic: '$magic8'. Expected ANDROID! or VNDRBOOT.")
        }
    }

    private fun unpackRamdiskSection(
        rawBytes: ByteArray,
        headerMagic: String,
        headerVersion: Int,
        pageSize: Int,
        headerSize: Int,
        kernelSize: Int,
        ramdiskOffset: Int,
        ramdiskSize: Int,
        cmdline: String,
        osVersionText: String,
        hasAvbFooter: Boolean,
        shaBefore: String,
    ): UnpackedPayload {
        val mtkReasons = mutableListOf<String>()
        val lowerCmd = cmdline.lowercase()
        if (lowerCmd.contains("mtk") || lowerCmd.contains("mediatek") || lowerCmd.contains("bootopt=")) {
            mtkReasons += "Kernel cmdline MTK parameter"
        }

        if (ramdiskSize <= 0 || ramdiskOffset + ramdiskSize > rawBytes.size) {
            return UnpackedPayload(
                rawBytes = rawBytes,
                headerMagic = headerMagic,
                headerVersion = headerVersion,
                pageSize = pageSize,
                headerSize = headerSize,
                kernelSize = kernelSize,
                ramdiskOffset = ramdiskOffset,
                ramdiskSize = ramdiskSize,
                hasMtkRamdiskHeader = false,
                mtkRamdiskHeader = null,
                ramdiskCompression = RamdiskCompression.NONE,
                cpioBytes = null,
                cmdline = cmdline,
                osVersionText = osVersionText,
                isMediaTek = mtkReasons.isNotEmpty(),
                mediaTekReasons = mtkReasons,
                hasAvbFooter = hasAvbFooter,
                sha256Before = shaBefore,
            )
        }

        val rawRamdisk = rawBytes.copyOfRange(ramdiskOffset, ramdiskOffset + ramdiskSize)
        var hasMtkHeader = false
        var mtkHeader: ByteArray? = null
        val payloadToDecompress = if (
            rawRamdisk.size > MTK_HEADER_SIZE &&
            ByteBuffer.wrap(rawRamdisk, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int == MTK_HEADER_MAGIC
        ) {
            hasMtkHeader = true
            mtkHeader = rawRamdisk.copyOfRange(0, MTK_HEADER_SIZE)
            val mtkTag = readCString(mtkHeader, 8, 32)
            mtkReasons += "MediaTek 512B header ($mtkTag)"
            rawRamdisk.copyOfRange(MTK_HEADER_SIZE, rawRamdisk.size)
        } else {
            rawRamdisk
        }

        // Also check kernel for MTK 512-byte header
        val kernelOff = if (headerVersion >= 3) alignUp(headerSize, pageSize) else pageSize
        if (kernelSize >= MTK_HEADER_SIZE && kernelOff + 4 <= rawBytes.size) {
            val kMagic = ByteBuffer.wrap(rawBytes, kernelOff, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (kMagic == MTK_HEADER_MAGIC) {
                mtkReasons += "MediaTek 512B kernel header"
            }
        }

        val (compression, cpio) = decompressRamdisk(payloadToDecompress)
        return UnpackedPayload(
            rawBytes = rawBytes,
            headerMagic = headerMagic,
            headerVersion = headerVersion,
            pageSize = pageSize,
            headerSize = headerSize,
            kernelSize = kernelSize,
            ramdiskOffset = ramdiskOffset,
            ramdiskSize = ramdiskSize,
            hasMtkRamdiskHeader = hasMtkHeader,
            mtkRamdiskHeader = mtkHeader,
            ramdiskCompression = compression,
            cpioBytes = cpio,
            cmdline = cmdline,
            osVersionText = osVersionText,
            isMediaTek = mtkReasons.isNotEmpty(),
            mediaTekReasons = mtkReasons,
            hasAvbFooter = hasAvbFooter,
            sha256Before = shaBefore,
        )
    }

    private fun decompressRamdisk(data: ByteArray): Pair<RamdiskCompression, ByteArray?> {
        if (data.isEmpty()) return RamdiskCompression.NONE to null
        return when {
            data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte() -> {
                val out = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
                RamdiskCompression.GZIP to out
            }
            Lz4Codec.isLz4Legacy(data) -> {
                RamdiskCompression.LZ4_LEGACY to Lz4Codec.decompressLegacy(data)
            }
            Lz4Codec.isLz4Frame(data) -> {
                RamdiskCompression.LZ4_FRAME to Lz4Codec.decompressFrame(data)
            }
            CpioArchive.isCpioNewc(data) -> {
                RamdiskCompression.RAW_CPIO to data
            }
            else -> RamdiskCompression.UNSUPPORTED to null
        }
    }

    private fun rebuildBootImage(
        original: ByteArray,
        unpacked: UnpackedPayload,
        newRamdisk: ByteArray,
    ): ByteArray {
        val page = unpacked.pageSize
        val oldAlignedRamdisk = alignUp(unpacked.ramdiskSize, page)
        val tailOffset = (unpacked.ramdiskOffset + oldAlignedRamdisk).coerceAtMost(original.size)
        val tailBytes = original.copyOfRange(tailOffset, original.size)

        val newRamdiskPad = (page - (newRamdisk.size % page)) % page
        val out = ByteArrayOutputStream(unpacked.ramdiskOffset + newRamdisk.size + newRamdiskPad + tailBytes.size)
        out.write(original, 0, unpacked.ramdiskOffset)
        out.write(newRamdisk)
        if (newRamdiskPad > 0) out.write(ByteArray(newRamdiskPad))
        if (tailBytes.isNotEmpty()) out.write(tailBytes)

        val result = out.toByteArray()
        val bb = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        when (unpacked.headerMagic) {
            "ANDROID!" -> {
                if (unpacked.headerVersion >= 3) {
                    bb.putInt(12, newRamdisk.size)
                } else {
                    bb.putInt(16, newRamdisk.size)
                    // Update SHA-1 id[8] at offset 576
                    val sha1 = MessageDigest.getInstance("SHA-1")
                    val kernelOff = page
                    sha1.update(result, kernelOff, unpacked.kernelSize)
                    sha1.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(unpacked.kernelSize).array())
                    sha1.update(newRamdisk)
                    sha1.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(newRamdisk.size).array())
                    val digest = sha1.digest()
                    System.arraycopy(digest, 0, result, 576, minOf(digest.size, 20))
                }
            }
            "VNDRBOOT" -> {
                bb.putInt(24, newRamdisk.size)
            }
        }
        return result
    }

    private fun classifyRecoveryLayout(
        fileName: String,
        cmdline: String,
        headerMagic: String,
        entryNames: List<String>,
    ): Pair<RecoveryLayoutKind, List<String>> {
        val indicators = mutableListOf<String>()
        val hasInitRecovery = entryNames.any { it.startsWith("init.recovery.") }
        val hasRecoveryBin = entryNames.any {
            it == "sbin/recovery" || it == "system/bin/recovery" || it == "bin/recovery"
        }
        val hasRecoveryFstab = entryNames.any { it.contains("recovery.fstab") || it.contains("twrp") }
        val hasFirstStage = entryNames.any { it.startsWith("first_stage_ramdisk") }
        val hasSkipInitramfs = cmdline.contains("skip_initramfs") || cmdline.contains("force_normal_boot")

        if (hasInitRecovery) indicators += "init.recovery.*.rc found"
        if (hasRecoveryBin) indicators += "recovery binary found in ramdisk"
        if (hasRecoveryFstab) indicators += "recovery.fstab / TWRP resources found"
        if (hasFirstStage) indicators += "first_stage_ramdisk directory found"
        if (hasSkipInitramfs) indicators += "cmdline contains skip_initramfs / force_normal_boot"

        val lowerName = fileName.lowercase()
        val kind = when {
            headerMagic == "VNDRBOOT" -> RecoveryLayoutKind.VENDOR_BOOT_RECOVERY
            lowerName.contains("recovery") && (hasInitRecovery || hasRecoveryBin || hasRecoveryFstab) ->
                RecoveryLayoutKind.DEDICATED_RECOVERY
            (hasInitRecovery || hasRecoveryBin || hasRecoveryFstab) && !lowerName.contains("recovery") ->
                RecoveryLayoutKind.RECOVERY_IN_BOOT
            hasInitRecovery || hasRecoveryBin ->
                RecoveryLayoutKind.RECOVERY_IN_BOOT
            else -> RecoveryLayoutKind.BOOT_WITHOUT_RECOVERY
        }
        return kind to indicators
    }

    private fun mergeProperties(existing: String, updates: Map<String, String>): String {
        val lines = existing.lines().toMutableList()
        val applied = mutableSetOf<String>()
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val newVal = updates[key]
                if (newVal != null) {
                    lines[i] = "$key=$newVal"
                    applied += key
                }
            }
        }
        for ((k, v) in updates) {
            if (k !in applied) {
                lines += "$k=$v"
            }
        }
        return lines.joinToString("\n") + "\n"
    }

    private fun buildRecoveryAdbScript(includeMtkFix: Boolean, includeRoot: Boolean): String = buildString {
        appendLine("#!/sbin/sh")
        appendLine("# Generated offline by Recovery ADB Patcher")
        appendLine("setprop ro.adb.secure 0")
        appendLine("setprop ro.debuggable 1")
        if (includeRoot) {
            appendLine("setprop ro.secure 0")
            appendLine("setprop service.adb.root 1")
        }
        appendLine("setprop persist.sys.usb.config adb")
        appendLine("setprop sys.usb.config none")
        if (includeMtkFix) {
            appendLine("for udc in /sys/class/udc/*; do")
            appendLine("  if [ -e \"\$udc\" ]; then")
            appendLine("    setprop sys.usb.controller \$(basename \"\$udc\")")
            appendLine("    break")
            appendLine("  fi")
            appendLine("done")
            appendLine("setprop sys.usb.ffs.ready 1")
        }
        appendLine("setprop sys.usb.config adb")
        appendLine("start adbd")
    }

    private fun buildRecoveryAdbRc(
        includeMtkFix: Boolean,
        autoAuthorize: Boolean,
        rootShell: Boolean,
    ): String = buildString {
        appendLine("# Recovery ADB Debug Menu & Auto-Auth Service")
        appendLine("service recovery_adb_debug /sbin/sh /sbin/enable_adb_debug.sh")
        appendLine("    user root")
        appendLine("    group root")
        appendLine("    disabled")
        appendLine("    oneshot")
        appendLine("    seclabel u:r:recovery:s0")
        appendLine()
        appendLine("on property:sys.recovery.adb_debug=1")
        if (autoAuthorize) {
            appendLine("    setprop ro.adb.secure 0")
            appendLine("    setprop ro.debuggable 1")
        }
        if (rootShell) {
            appendLine("    setprop ro.secure 0")
            appendLine("    setprop service.adb.root 1")
        }
        if (includeMtkFix) {
            appendLine("    setprop sys.usb.ffs.ready 1")
        }
        appendLine("    setprop sys.usb.config adb")
        appendLine("    start adbd")
        appendLine("    start recovery_adb_debug")
        appendLine()
        appendLine("on boot")
        if (autoAuthorize) {
            appendLine("    setprop ro.adb.secure 0")
            appendLine("    setprop sys.usb.config adb")
        }
        if (rootShell) {
            appendLine("    setprop service.adb.root 1")
        }
        appendLine("    start adbd")
    }

    private fun readUriBytes(context: Context, uri: Uri): Pair<String, ByteArray> {
        var name = uri.lastPathSegment ?: "image.img"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx) ?: name
            }
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected image file")
        return name to bytes
    }

    private fun gzipCompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun alignUp(value: Int, align: Int): Int {
        if (align <= 0) return value
        val rem = value % align
        return if (rem == 0) value else value + (align - rem)
    }

    private fun readCString(bytes: ByteArray, offset: Int, maxLen: Int): String {
        val end = minOf(bytes.size, offset + maxLen)
        var i = offset
        while (i < end && bytes[i] != 0.toByte()) i++
        return String(bytes, offset, maxOf(0, i - offset), Charsets.UTF_8)
    }

    private fun decodeOsVersion(raw: Int): String {
        if (raw == 0) return "Unspecified"
        val osVer = raw ushr 11
        val major = (osVer ushr 14) and 0x7F
        val minor = (osVer ushr 7) and 0x7F
        val patch = osVer and 0x7F
        val year = 2000 + ((raw ushr 4) and 0x7F)
        val month = raw and 0x0F
        return "Android $major.$minor.$patch (Patch $year-${"%02d".format(month)})"
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
