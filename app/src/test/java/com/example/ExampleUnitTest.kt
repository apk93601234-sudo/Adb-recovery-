package com.example

import com.example.core.adb.AdbPacket
import com.example.core.adb.AdbWire
import com.example.core.patcher.CpioArchive
import com.example.core.patcher.Lz4Codec
import com.example.data.catalog.CommandCatalog
import com.example.data.catalog.RecoveryGuidesCatalog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun adbPacket_encodesLittleEndianHeaderAndChecksum() {
        val payload = "host::recoveryadb\u0000".toByteArray(Charsets.UTF_8)
        val pkt = AdbPacket(AdbWire.A_CNXN, AdbWire.VERSION, AdbWire.MAX_PAYLOAD, payload)
        val bytes = pkt.encode()
        assertEquals(AdbWire.HEADER_SIZE + payload.size, bytes.size)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbWire.A_CNXN, buf.getInt())
        assertEquals(AdbWire.VERSION, buf.getInt())
        assertEquals(AdbWire.MAX_PAYLOAD, buf.getInt())
        assertEquals(payload.size, buf.getInt())
        val expectedChecksum = payload.sumOf { it.toInt() and 0xFF }
        assertEquals(expectedChecksum, buf.getInt())
        assertEquals(AdbWire.A_CNXN xor -0x1, buf.getInt())
    }

    @Test
    fun offlineCatalogs_arePopulated() {
        assertTrue(CommandCatalog.allCommands.size >= 30)
        assertTrue(RecoveryGuidesCatalog.oemGuides.any { it.id == "mediatek" })
        assertTrue(RecoveryGuidesCatalog.troubleshooting.isNotEmpty())
    }

    @Test
    fun cpioArchive_roundTripsAndPatchesEntries() {
        val archive = CpioArchive(emptyList())
        archive.upsertFile("default.prop", "ro.adb.secure=1\n".toByteArray())
        archive.ensureDirectory("sbin")
        archive.upsertFile("sbin/enable_adb_debug.sh", "#!/sbin/sh\nstart adbd\n".toByteArray(), mode = 0x81EDL)

        val raw = archive.serialize()
        assertTrue(CpioArchive.isCpioNewc(raw))

        val reparsed = CpioArchive.parse(raw)
        val prop = reparsed.find("default.prop")
        assertNotNull(prop)
        assertEquals("ro.adb.secure=1\n", String(prop!!.data))
        assertNotNull(reparsed.find("sbin/enable_adb_debug.sh"))
    }

    @Test
    fun lz4Codec_legacyAndFrameRoundTrip() {
        val sample = ("ro.adb.secure=0\nro.debuggable=1\nsys.usb.config=adb\n".repeat(40)).toByteArray()

        val legacyCompressed = Lz4Codec.compressLegacy(sample)
        assertTrue(Lz4Codec.isLz4Legacy(legacyCompressed))
        assertArrayEquals(sample, Lz4Codec.decompressLegacy(legacyCompressed))

        val frameCompressed = Lz4Codec.compressFrame(sample)
        assertTrue(Lz4Codec.isLz4Frame(frameCompressed))
        assertArrayEquals(sample, Lz4Codec.decompressFrame(frameCompressed))
    }
}
