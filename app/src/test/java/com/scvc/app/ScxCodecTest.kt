package com.scvc.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class ScxCodecTest {
    @Test fun exactRoundTripForMixedData() {
        val random = Random(42)
        val source = ByteArray(400_123)
        random.nextBytes(source)
        // Add a highly compressible region to exercise both chunk choices.
        for (i in 100_000 until 220_000) source[i] = 0
        val packed = ByteArrayOutputStream()
        ScxCodec.compress(ByteArrayInputStream(source), packed, "sample.mp4")
        val restored = ByteArrayOutputStream()
        val result = ScxCodec.extract(ByteArrayInputStream(packed.toByteArray()), restored)
        assertArrayEquals(source, restored.toByteArray())
        assertEquals(source.size.toLong(), result.inputBytes)
    }

    @Test fun emptyFileRoundTrip() {
        val packed = ByteArrayOutputStream()
        ScxCodec.compress(ByteArrayInputStream(byteArrayOf()), packed, "empty.bin")
        val restored = ByteArrayOutputStream()
        ScxCodec.extract(ByteArrayInputStream(packed.toByteArray()), restored)
        assertArrayEquals(byteArrayOf(), restored.toByteArray())
    }
}
