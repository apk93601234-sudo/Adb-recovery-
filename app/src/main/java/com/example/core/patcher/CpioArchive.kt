package com.example.core.patcher

import java.io.ByteArrayOutputStream

data class CpioEntry(
    val ino: Long,
    val mode: Long,
    val uid: Long,
    val gid: Long,
    val nlink: Long,
    val mtime: Long,
    val devMajor: Long,
    val devMinor: Long,
    val rdevMajor: Long,
    val rdevMinor: Long,
    val name: String,
    val data: ByteArray,
)

/**
 * Pure-Kotlin SVR4 'newc' (070701) CPIO archive parser, patcher, and serializer.
 */
class CpioArchive(entries: List<CpioEntry>) {

    private val mutableEntries = entries.toMutableList()
    val entries: List<CpioEntry> get() = mutableEntries

    fun find(path: String): CpioEntry? {
        val clean = path.trimStart('/')
        return mutableEntries.firstOrNull { it.name.trimStart('/') == clean }
    }

    fun upsertFile(path: String, content: ByteArray, mode: Long = 0b100_000_001_111_010_0L /* 0100644 */) {
        val clean = path.trimStart('/')
        val idx = mutableEntries.indexOfFirst { it.name.trimStart('/') == clean }
        val baseIno = if (idx >= 0) mutableEntries[idx].ino else (mutableEntries.maxOfOrNull { it.ino } ?: 100L) + 1L
        val newEntry = CpioEntry(
            ino = baseIno,
            mode = mode,
            uid = 0L,
            gid = 0L,
            nlink = 1L,
            mtime = System.currentTimeMillis() / 1000L,
            devMajor = 0L,
            devMinor = 0L,
            rdevMajor = 0L,
            rdevMinor = 0L,
            name = clean,
            data = content,
        )
        if (idx >= 0) {
            mutableEntries[idx] = newEntry
        } else {
            mutableEntries += newEntry
        }
    }

    fun ensureDirectory(path: String, mode: Long = 0x41EDL /* 0040755 */) {
        val clean = path.trimStart('/').trimEnd('/')
        if (clean.isBlank() || find(clean) != null) return
        val nextIno = (mutableEntries.maxOfOrNull { it.ino } ?: 100L) + 1L
        mutableEntries += CpioEntry(
            ino = nextIno,
            mode = mode,
            uid = 0L,
            gid = 0L,
            nlink = 2L,
            mtime = System.currentTimeMillis() / 1000L,
            devMajor = 0L,
            devMinor = 0L,
            rdevMajor = 0L,
            rdevMinor = 0L,
            name = clean,
            data = ByteArray(0),
        )
    }

    fun serialize(): ByteArray {
        val out = ByteArrayOutputStream()
        for (entry in mutableEntries) {
            if (entry.name == "TRAILER!!!") continue
            writeEntry(out, entry)
        }
        writeEntry(
            out,
            CpioEntry(
                ino = 0, mode = 0, uid = 0, gid = 0, nlink = 1, mtime = 0,
                devMajor = 0, devMinor = 0, rdevMajor = 0, rdevMinor = 0,
                name = "TRAILER!!!",
                data = ByteArray(0),
            )
        )
        // Pad total archive to 512-byte boundary as standard in initramfs
        val rem = out.size() % 512
        if (rem != 0) {
            out.write(ByteArray(512 - rem))
        }
        return out.toByteArray()
    }

    private fun writeEntry(out: ByteArrayOutputStream, e: CpioEntry) {
        val nameBytes = (e.name + "\u0000").toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("070701")
            append("%08x".format(e.ino and 0xFFFFFFFFL))
            append("%08x".format(e.mode and 0xFFFFFFFFL))
            append("%08x".format(e.uid and 0xFFFFFFFFL))
            append("%08x".format(e.gid and 0xFFFFFFFFL))
            append("%08x".format(e.nlink and 0xFFFFFFFFL))
            append("%08x".format(e.mtime and 0xFFFFFFFFL))
            append("%08x".format(e.data.size.toLong() and 0xFFFFFFFFL))
            append("%08x".format(e.devMajor and 0xFFFFFFFFL))
            append("%08x".format(e.devMinor and 0xFFFFFFFFL))
            append("%08x".format(e.rdevMajor and 0xFFFFFFFFL))
            append("%08x".format(e.rdevMinor and 0xFFFFFFFFL))
            append("%08x".format(nameBytes.size.toLong() and 0xFFFFFFFFL))
            append("00000000")
        }.toByteArray(Charsets.US_ASCII)

        out.write(header)
        out.write(nameBytes)
        val headerAndNameLen = 110 + nameBytes.size
        val namePad = (4 - (headerAndNameLen % 4)) % 4
        if (namePad > 0) out.write(ByteArray(namePad))

        if (e.data.isNotEmpty()) {
            out.write(e.data)
            val dataPad = (4 - (e.data.size % 4)) % 4
            if (dataPad > 0) out.write(ByteArray(dataPad))
        }
    }

    companion object {
        fun isCpioNewc(bytes: ByteArray): Boolean {
            if (bytes.size < 6) return false
            val magic = String(bytes, 0, 6, Charsets.US_ASCII)
            return magic == "070701" || magic == "070702"
        }

        fun parse(bytes: ByteArray): CpioArchive {
            val entries = mutableListOf<CpioEntry>()
            var pos = 0
            while (pos + 110 <= bytes.size) {
                val magic = String(bytes, pos, 6, Charsets.US_ASCII)
                if (magic != "070701" && magic != "070702") {
                    break
                }
                fun hex(fieldIndex: Int): Long {
                    val start = pos + 6 + fieldIndex * 8
                    return String(bytes, start, 8, Charsets.US_ASCII).toLong(16)
                }

                val ino = hex(0)
                val mode = hex(1)
                val uid = hex(2)
                val gid = hex(3)
                val nlink = hex(4)
                val mtime = hex(5)
                val fileSize = hex(6).toInt()
                val devMaj = hex(7)
                val devMin = hex(8)
                val rdevMaj = hex(9)
                val rdevMin = hex(10)
                val nameSize = hex(11).toInt()

                val nameStart = pos + 110
                if (nameStart + nameSize > bytes.size) break
                val rawName = String(bytes, nameStart, maxOf(0, nameSize - 1), Charsets.UTF_8)

                val afterName = nameStart + nameSize
                val nameAligned = afterName + ((4 - (afterName % 4)) % 4)
                if (rawName == "TRAILER!!!") {
                    break
                }
                if (nameAligned + fileSize > bytes.size) break
                val data = bytes.copyOfRange(nameAligned, nameAligned + fileSize)
                val afterData = nameAligned + fileSize
                pos = afterData + ((4 - (afterData % 4)) % 4)

                entries += CpioEntry(
                    ino = ino,
                    mode = mode,
                    uid = uid,
                    gid = gid,
                    nlink = nlink,
                    mtime = mtime,
                    devMajor = devMaj,
                    devMinor = devMin,
                    rdevMajor = rdevMaj,
                    rdevMinor = rdevMin,
                    name = rawName,
                    data = data,
                )
            }
            return CpioArchive(entries)
        }
    }
}
