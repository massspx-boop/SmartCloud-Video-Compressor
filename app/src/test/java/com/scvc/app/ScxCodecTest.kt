package com.scvc.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class ScxCodecTest {
    @Test fun exactRoundTripAndByteCounts() {
        val random = Random(42)
        val source = ByteArray(400_123)
        random.nextBytes(source)
        for (i in 100_000 until 220_000) source[i] = 0
        val packed = ByteArrayOutputStream()
        val pack = ScxCodec.compress(ByteArrayInputStream(source), packed, "sample.mp4")
        val restored = ByteArrayOutputStream()
        val unpack = ScxCodec.extract(ByteArrayInputStream(packed.toByteArray()), restored)
        assertArrayEquals(source, restored.toByteArray())
        assertEquals(source.size.toLong(), unpack.inputBytes)
        assertEquals(packed.size().toLong(), pack.outputBytes)
        assertEquals(source.size.toLong(), unpack.outputBytes)
        assertTrue(pack.sha256.matches(Regex("[0-9a-f]{64}")))
    }
    @Test fun emptyFileRoundTrip() {
        val packed = ByteArrayOutputStream()
        ScxCodec.compress(ByteArrayInputStream(byteArrayOf()), packed, "empty.bin")
        val restored = ByteArrayOutputStream()
        ScxCodec.extract(ByteArrayInputStream(packed.toByteArray()), restored)
        assertArrayEquals(byteArrayOf(), restored.toByteArray())
    }
    @Test fun corruptFooterIsRejected() {
        val packed = ByteArrayOutputStream()
        ScxCodec.compress(ByteArrayInputStream(ByteArray(200_000) { (it % 251).toByte() }), packed, "bad.bin")
        val bytes = packed.toByteArray()
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        try {
            ScxCodec.extract(ByteArrayInputStream(bytes), ByteArrayOutputStream())
            fail("Corrupt footer must fail")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun filenameCannotEscapePath() {
        val packed = ByteArrayOutputStream()
        ScxCodec.compress(ByteArrayInputStream(byteArrayOf(1)), packed, "../../evil.mp4")
        assertEquals("evil.mp4", ScxCodec.originalName(ByteArrayInputStream(packed.toByteArray())))
    }
}
