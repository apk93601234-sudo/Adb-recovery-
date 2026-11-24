package com.example.core.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Pure-Kotlin local RSA-2048 ADB key manager.
 * Generates, stores, and signs ADB AUTH challenges offline without any external tools.
 */
class AdbKeyManager(private val context: Context) {

    private val privateFile: File get() = File(context.filesDir, "adb_rsa_private.der")
    private val publicFile: File get() = File(context.filesDir, "adb_rsa_public.der")

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    fun getOrCreateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }
        synchronized(this) {
            cachedKeyPair?.let { return it }
            if (privateFile.exists() && publicFile.exists()) {
                runCatching {
                    val factory = KeyFactory.getInstance("RSA")
                    val priv = factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes()))
                    val pub = factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes()))
                    val pair = KeyPair(pub, priv)
                    cachedKeyPair = pair
                    return pair
                }
            }
            return regenerateKeyPair()
        }
    }

    fun regenerateKeyPair(): KeyPair {
        synchronized(this) {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val pair = generator.generateKeyPair()
            privateFile.writeBytes(pair.private.encoded)
            publicFile.writeBytes(pair.public.encoded)
            cachedKeyPair = pair
            return pair
        }
    }

    fun getSha256Fingerprint(): String {
        val pub = getOrCreateKeyPair().public
        val digest = MessageDigest.getInstance("SHA-256").digest(pub.encoded)
        return digest.take(12).joinToString(":") { "%02X".format(it) }
    }

    /**
     * Signs the 20-byte ADB AUTH token using PKCS#1 v1.5 with the SHA-1 DigestInfo ASN.1 prefix
     * as expected by Android's adbd daemon.
     */
    fun signToken(token: ByteArray): ByteArray {
        val privateKey: PrivateKey = getOrCreateKeyPair().private
        val sha1DigestInfoPrefix = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
            0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
        )
        val payload = sha1DigestInfoPrefix + token
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(payload)
    }

    /**
     * Encodes the RSA-2048 public key in AOSP's 524-byte RSAPublicKey binary format + Base64.
     */
    fun getAdbPublicKeyPayload(): ByteArray {
        val rsaPub = getOrCreateKeyPair().public as RSAPublicKey
        val androidStruct = encodeAndroidRsaPublicKey(rsaPub)
        val b64 = Base64.encodeToString(androidStruct, Base64.NO_WRAP)
        return "$b64 recoveryadb@android\u0000".toByteArray(Charsets.UTF_8)
    }

    private fun encodeAndroidRsaPublicKey(pubKey: RSAPublicKey): ByteArray {
        val numWords = 64 // 2048 bits / 32
        val modulus = pubKey.modulus
        val r = BigInteger.valueOf(2).pow(32)
        val n0inv = r.subtract(modulus.modInverse(r)).toInt()

        val rr = BigInteger.valueOf(2).pow(numWords * 32 * 2).mod(modulus)

        val buf = ByteBuffer.allocate(4 + 4 + numWords * 4 + numWords * 4 + 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        buf.putInt(numWords)
        buf.putInt(n0inv)

        val nWords = bigIntToLittleEndianWords(modulus, numWords)
        for (w in nWords) buf.putInt(w)

        val rrWords = bigIntToLittleEndianWords(rr, numWords)
        for (w in rrWords) buf.putInt(w)

        buf.putInt(pubKey.publicExponent.toInt())
        return buf.array()
    }

    private fun bigIntToLittleEndianWords(value: BigInteger, numWords: Int): IntArray {
        val words = IntArray(numWords)
        var temp = value
        val mask = BigInteger.valueOf(0xFFFFFFFFL)
        for (i in 0 until numWords) {
            words[i] = temp.and(mask).toInt()
            temp = temp.shiftRight(32)
        }
        return words
    }
}
