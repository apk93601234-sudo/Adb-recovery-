package com.example.core.patcher

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-Kotlin LZ4 Legacy (0x184C2102) and LZ4 Frame (0x184D2204) codec
 * for Android/MediaTek boot and recovery ramdisks without native libraries.
 */
object Lz4Codec {

    const val MAGIC_LEGACY = 0x184C2102
    const val MAGIC_FRAME = 0x184D2204
    private const val LEGACY_BLOCK_MAX = 8 * 1024 * 1024

    fun isLz4Legacy(data: ByteArray): Boolean {
        if (data.size < 4) return false
        val magic = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return magic == MAGIC_LEGACY
    }

    fun isLz4Frame(data: ByteArray): Boolean {
        if (data.size < 4) return false
        val magic = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return magic == MAGIC_FRAME
    }

    fun decompress(data: ByteArray): ByteArray {
        return when {
            isLz4Legacy(data) -> decompressLegacy(data)
            isLz4Frame(data) -> decompressFrame(data)
            else -> throw IllegalArgumentException("Not a recognized LZ4 stream")
        }
    }

    fun decompressLegacy(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 4
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        while (pos + 4 <= data.size) {
            val blockSize = bb.getInt(pos)
            pos += 4
            if (blockSize <= 0 || blockSize > data.size - pos) {
                // Some MTK images have trailing zero padding after the legacy stream
                break
            }
            val decompressed = decompressBlock(data, pos, blockSize, LEGACY_BLOCK_MAX)
            out.write(decompressed)
            pos += blockSize
        }
        return out.toByteArray()
    }

    fun decompressFrame(data: ByteArray): ByteArray {
        if (data.size < 7) throw IllegalArgumentException("Truncated LZ4 frame")
        var pos = 4
        val flg = data[pos++].toInt() and 0xFF
        val bd = data[pos++].toInt() and 0xFF
        val hasBlockChecksum = (flg and 0x10) != 0
        val hasContentSize = (flg and 0x08) != 0
        val hasContentChecksum = (flg and 0x04) != 0
        val hasDictId = (flg and 0x01) != 0

        if (hasContentSize) pos += 8
        if (hasDictId) pos += 4
        pos++ // header checksum

        val maxBlockSize = when ((bd ushr 4) and 0x07) {
            4 -> 64 * 1024
            5 -> 256 * 1024
            6 -> 1024 * 1024
            7 -> 4 * 1024 * 1024
            else -> 4 * 1024 * 1024
        }

        val out = ByteArrayOutputStream()
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        while (pos + 4 <= data.size) {
            val rawSize = bb.getInt(pos)
            pos += 4
            if (rawSize == 0) {
                if (hasContentChecksum && pos + 4 <= data.size) pos += 4
                break
            }
            val isUncompressed = (rawSize and 0x80000000.toInt()) != 0
            val blockSize = rawSize and 0x7FFFFFFF
            if (pos + blockSize > data.size) break

            if (isUncompressed) {
                out.write(data, pos, blockSize)
            } else {
                val decoded = decompressBlock(data, pos, blockSize, maxBlockSize)
                out.write(decoded)
            }
            pos += blockSize
            if (hasBlockChecksum) pos += 4
        }
        return out.toByteArray()
    }

    /**
     * Compresses data into valid LZ4 Legacy format (supported by Android/MediaTek kernels).
     */
    fun compressLegacy(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val magicBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(MAGIC_LEGACY).array()
        out.write(magicBuf)

        val chunkLimit = 4 * 1024 * 1024
        var offset = 0
        while (offset < input.size) {
            val len = minOf(chunkLimit, input.size - offset)
            val compressed = compressBlock(input, offset, len)
            val sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(compressed.size).array()
            out.write(sizeBuf)
            out.write(compressed)
            offset += len
        }
        return out.toByteArray()
    }

    /**
     * Compresses data into valid LZ4 Frame format (with Content Checksum for modern GKI/MTK kernels).
     */
    fun compressFrame(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        // Magic
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(MAGIC_FRAME).array())
        // FLG: Version=01 (0x40), B.Indep=1 (0x20), C.Checksum=1 (0x04) -> 0x64
        // BD: MaxBlockSize=4MB (7 << 4 = 0x70)
        val flg = 0x64.toByte()
        val bd = 0x70.toByte()
        val headerDesc = byteArrayOf(flg, bd)
        val hc = ((xxHash32(headerDesc, 0, 2, 0) ushr 8) and 0xFF).toByte()
        out.write(flg.toInt())
        out.write(bd.toInt())
        out.write(hc.toInt())

        val blockLimit = 4 * 1024 * 1024
        var offset = 0
        while (offset < input.size) {
            val len = minOf(blockLimit, input.size - offset)
            val compressed = compressBlock(input, offset, len)
            if (compressed.size < len) {
                out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(compressed.size).array())
                out.write(compressed)
            } else {
                // Store uncompressed block with bit 31 set
                val rawHeader = len or 0x80000000.toInt()
                out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(rawHeader).array())
                out.write(input, offset, len)
            }
            offset += len
        }
        // EndMark (0x00000000)
        out.write(byteArrayOf(0, 0, 0, 0))
        // Content checksum (xxHash32)
        val contentHash = xxHash32(input, 0, input.size, 0)
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(contentHash).array())
        return out.toByteArray()
    }

    private fun decompressBlock(src: ByteArray, srcOff: Int, srcLen: Int, maxOutput: Int): ByteArray {
        val end = srcOff + srcLen
        var ip = srcOff
        val out = ByteArrayOutputStream(minOf(srcLen * 3, maxOutput))
        // Keep an indexed buffer for fast match back-references
        var buf = ByteArray(minOf(maxOf(srcLen * 3, 4096), maxOutput))
        var op = 0

        fun ensureCapacity(needed: Int) {
            if (op + needed > buf.size) {
                val newSize = maxOf(buf.size * 2, op + needed)
                buf = buf.copyOf(newSize)
            }
        }

        while (ip < end) {
            val token = src[ip++].toInt() and 0xFF
            var literalLen = token ushr 4
            if (literalLen == 15) {
                while (ip < end) {
                    val b = src[ip++].toInt() and 0xFF
                    literalLen += b
                    if (b != 255) break
                }
            }
            if (literalLen > 0) {
                if (ip + literalLen > end) break
                ensureCapacity(literalLen)
                System.arraycopy(src, ip, buf, op, literalLen)
                ip += literalLen
                op += literalLen
            }
            if (ip >= end) break

            if (ip + 2 > end) break
            val matchOffset = (src[ip].toInt() and 0xFF) or ((src[ip + 1].toInt() and 0xFF) shl 8)
            ip += 2
            if (matchOffset == 0 || matchOffset > op) {
                throw IllegalStateException("Corrupt LZ4 match offset: $matchOffset at $op")
            }

            var matchLen = (token and 0x0F) + 4
            if ((token and 0x0F) == 15) {
                while (ip < end) {
                    val b = src[ip++].toInt() and 0xFF
                    matchLen += b
                    if (b != 255) break
                }
            }
            ensureCapacity(matchLen)
            var matchPos = op - matchOffset
            for (i in 0 until matchLen) {
                buf[op++] = buf[matchPos++]
            }
        }
        out.write(buf, 0, op)
        return out.toByteArray()
    }

    /**
     * Fast pure-Kotlin LZ4 block compressor using a 4-byte hash table.
     */
    private fun compressBlock(src: ByteArray, srcOff: Int, srcLen: Int): ByteArray {
        val out = ByteArrayOutputStream(srcLen)
        if (srcLen < 13) {
            writeLiteralsOnly(out, src, srcOff, srcLen)
            return out.toByteArray()
        }

        val hashTable = IntArray(1 shl 16) { -1 }
        val end = srcOff + srcLen
        val mfLimit = end - 12
        var anchor = srcOff
        var ip = srcOff + 1

        fun hash4(pos: Int): Int {
            val v = (src[pos].toInt() and 0xFF) or
                ((src[pos + 1].toInt() and 0xFF) shl 8) or
                ((src[pos + 2].toInt() and 0xFF) shl 16) or
                ((src[pos + 3].toInt() and 0xFF) shl 24)
            return ((v * -1640531535) ushr 16) and 0xFFFF
        }

        fun equals4(a: Int, b: Int): Boolean {
            return src[a] == src[b] && src[a + 1] == src[b + 1] &&
                src[a + 2] == src[b + 2] && src[a + 3] == src[b + 3]
        }

        while (ip < mfLimit) {
            val h = hash4(ip)
            val ref = hashTable[h]
            hashTable[h] = ip

            if (ref < srcOff || (ip - ref) > 65535 || !equals4(ref, ip)) {
                ip++
                continue
            }

            // Found match
            val litLen = ip - anchor
            val offset = ip - ref

            var matchEnd = ip + 4
            var refEnd = ref + 4
            val matchLimit = end - 5
            while (matchEnd < matchLimit && src[matchEnd] == src[refEnd]) {
                matchEnd++
                refEnd++
            }
            val matchLen = (matchEnd - ip) - 4

            val litToken = minOf(litLen, 15)
            val matchToken = minOf(matchLen, 15)
            out.write((litToken shl 4) or matchToken)

            if (litLen >= 15) {
                var rem = litLen - 15
                while (rem >= 255) {
                    out.write(255)
                    rem -= 255
                }
                out.write(rem)
            }
            if (litLen > 0) out.write(src, anchor, litLen)

            out.write(offset and 0xFF)
            out.write((offset ushr 8) and 0xFF)

            if (matchLen >= 15) {
                var rem = matchLen - 15
                while (rem >= 255) {
                    out.write(255)
                    rem -= 255
                }
                out.write(rem)
            }

            ip = matchEnd
            anchor = ip
        }

        val lastLiterals = end - anchor
        writeLiteralsOnly(out, src, anchor, lastLiterals)
        return out.toByteArray()
    }

    private fun writeLiteralsOnly(out: ByteArrayOutputStream, src: ByteArray, offset: Int, len: Int) {
        val litToken = minOf(len, 15)
        out.write(litToken shl 4)
        if (len >= 15) {
            var rem = len - 15
            while (rem >= 255) {
                out.write(255)
                rem -= 255
            }
            out.write(rem)
        }
        if (len > 0) out.write(src, offset, len)
    }

    private fun xxHash32(data: ByteArray, offset: Int, len: Int, seed: Int): Int {
        val p1 = -1640531535
        val p2 = -2048144777
        val p3 = -1028477379
        val p4 = 668265263
        val p5 = 374761393

        var idx = offset
        val end = offset + len
        var h32: Int

        if (len >= 16) {
            var v1 = seed + p1 + p2
            var v2 = seed + p2
            var v3 = seed
            var v4 = seed - p1
            val limit = end - 16
            while (idx <= limit) {
                v1 = Integer.rotateLeft(v1 + getIntLE(data, idx) * p2, 13) * p1
                v2 = Integer.rotateLeft(v2 + getIntLE(data, idx + 4) * p2, 13) * p1
                v3 = Integer.rotateLeft(v3 + getIntLE(data, idx + 8) * p2, 13) * p1
                v4 = Integer.rotateLeft(v4 + getIntLE(data, idx + 12) * p2, 13) * p1
                idx += 16
            }
            h32 = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7) +
                Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18)
        } else {
            h32 = seed + p5
        }

        h32 += len
        while (idx <= end - 4) {
            h32 = Integer.rotateLeft(h32 + getIntLE(data, idx) * p3, 17) * p4
            idx += 4
        }
        while (idx < end) {
            h32 = Integer.rotateLeft(h32 + (data[idx].toInt() and 0xFF) * p5, 11) * p1
            idx++
        }

        h32 = (h32 xor (h32 ushr 15)) * p2
        h32 = (h32 xor (h32 ushr 13)) * p3
        h32 = h32 xor (h32 ushr 16)
        return h32
    }

    private fun getIntLE(data: ByteArray, idx: Int): Int {
        return (data[idx].toInt() and 0xFF) or
            ((data[idx + 1].toInt() and 0xFF) shl 8) or
            ((data[idx + 2].toInt() and 0xFF) shl 16) or
            ((data[idx + 3].toInt() and 0xFF) shl 24)
    }
}
