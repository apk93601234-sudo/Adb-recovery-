package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.adb.AdbKeyManager
import com.example.core.adb.AdbPacket
import com.example.core.adb.AdbWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Recovery ADB", appName)
    }

    @Test
    fun `adb packet encodes little-endian header and magic correctly`() {
        val payload = "host::recoveryadb\u0000".toByteArray(Charsets.UTF_8)
        val pkt = AdbPacket(AdbWire.A_CNXN, AdbWire.VERSION, AdbWire.MAX_PAYLOAD, payload)
        val bytes = pkt.encode()
        assertEquals(AdbWire.HEADER_SIZE + payload.size, bytes.size)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbWire.A_CNXN, buf.getInt())
        assertEquals(AdbWire.VERSION, buf.getInt())
        assertEquals(AdbWire.MAX_PAYLOAD, buf.getInt())
        assertEquals(payload.size, buf.getInt())
        buf.getInt() // checksum
        assertEquals(AdbWire.A_CNXN xor -0x1, buf.getInt())
    }

    @Test
    fun `adb rsa key manager generates and signs token offline`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AdbKeyManager(context)
        val pubPayload = manager.getAdbPublicKeyPayload()
        val pubText = String(pubPayload, Charsets.UTF_8)
        assertTrue(pubText.contains("recoveryadb@android"))

        val sig = manager.signToken(ByteArray(20) { it.toByte() })
        assertEquals(256, sig.size) // 2048-bit RSA signature = 256 bytes
    }
}
