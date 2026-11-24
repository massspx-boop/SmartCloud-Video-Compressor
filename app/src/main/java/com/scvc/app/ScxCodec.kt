package com.scvc.app

import java.io.*
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** SCX v1 exact-byte container. It is not a video transcoder. */
object ScxCodec {
    private val MAGIC = byteArrayOf(0x53, 0x43, 0x58, 0x31)
    private const val VERSION = 1
    const val CHUNK_SIZE = 128 * 1024
    private const val MAX_NAME_BYTES = 1024
    private const val MAX_STORED_CHUNK = CHUNK_SIZE * 2 + 1024
    private const val RAW = 0
    private const val DEFLATE = 1
    data class Result(val inputBytes: Long, val outputBytes: Long, val sha256: String)

    fun compress(input: InputStream, output: OutputStream, originalName: String,
                 progress: (Long) -> Unit = {}): Result {
        val name = safeName(originalName).toByteArray(Charsets.UTF_8)
        require(name.isNotEmpty() && name.size <= MAX_NAME_BYTES) { "Invalid source filename" }
        val counter = CountingOutputStream(output)
        val out = DataOutputStream(BufferedOutputStream(counter, CHUNK_SIZE))
        out.write(MAGIC); out.writeShort(VERSION); out.writeInt(CHUNK_SIZE)
        out.writeShort(name.size); out.write(name)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE)
        var total = 0L
        while (true) {
            var count = 0
            while (count < buffer.size) {
                val n = input.read(buffer, count, buffer.size - count)
                if (n < 0) break
                if (n > 0) count += n
            }
            if (count == 0) break
            digest.update(buffer, 0, count)
            val temp = ByteArrayOutputStream(count)
            DeflaterOutputStream(temp, Deflater(Deflater.DEFAULT_COMPRESSION, false), 8192)
                .use { it.write(buffer, 0, count) }
            val packed = temp.toByteArray()
            val useDeflate = packed.size < count
            out.writeInt(count)
            out.writeInt(if (useDeflate) packed.size else count)
            out.writeByte(if (useDeflate) DEFLATE else RAW)
            if (useDeflate) out.write(packed) else out.write(buffer, 0, count)
            total += count
            progress(total)
        }
        out.writeInt(0); out.writeInt(0); out.writeByte(RAW)
        val hash = digest.digest()
        out.write(hash); out.flush()
        return Result(total, counter.count, hash.hex())
    }

    fun originalName(input: InputStream): String {
        val data = DataInputStream(BufferedInputStream(input))
        validateHeader(data)
        val size = data.readUnsignedShort()
        require(size in 1..MAX_NAME_BYTES) { "Invalid SCX filename length" }
        val name = ByteArray(size); data.readFully(name)
        return safeName(name.toString(Charsets.UTF_8)).ifBlank { "restored-file.bin" }
    }

    fun extract(input: InputStream, output: OutputStream, progress: (Long) -> Unit = {}): Result {
        val data = DataInputStream(BufferedInputStream(input, CHUNK_SIZE))
        validateHeader(data)
        val nameSize = data.readUnsignedShort()
        require(nameSize in 1..MAX_NAME_BYTES) { "Invalid SCX filename length" }
        val ignoredName = ByteArray(nameSize); data.readFully(ignoredName)
        val counter = CountingOutputStream(output)
        val out = BufferedOutputStream(counter, CHUNK_SIZE)
        val digest = MessageDigest.getInstance("SHA-256")
        val plain = ByteArray(CHUNK_SIZE)
        var total = 0L
        while (true) {
            val plainLength = data.readInt()
            val storedLength = data.readInt()
            val codec = data.readUnsignedByte()
            if (plainLength == 0 && storedLength == 0) {
                require(codec == RAW) { "Invalid SCX end marker" }
                break
            }
            require(plainLength in 1..CHUNK_SIZE) { "Invalid SCX chunk length" }
            require(storedLength in 1..MAX_STORED_CHUNK) { "Invalid SCX stored length" }
            val stored = ByteArray(storedLength); data.readFully(stored)
            when (codec) {
                RAW -> {
                    require(storedLength == plainLength) { "Invalid RAW chunk length" }
                    out.write(stored); digest.update(stored)
                }
                DEFLATE -> {
                    var produced = 0
                    InflaterInputStream(ByteArrayInputStream(stored)).use { stream ->
                        while (produced < plainLength) {
                            val n = stream.read(plain, 0, minOf(plain.size, plainLength - produced))
                            if (n < 0) break
                            if (n == 0) continue
                            out.write(plain, 0, n); digest.update(plain, 0, n); produced += n
                        }
                        require(produced == plainLength && stream.read() == -1) {
                            "SCX decompression length mismatch"
                        }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported SCX codec: $codec")
            }
            total += plainLength; progress(total)
        }
        val expected = ByteArray(32); data.readFully(expected)
        require(data.read() == -1) { "Unexpected data after SCX footer" }
        val actual = digest.digest()
        require(MessageDigest.isEqual(expected, actual)) { "SHA-256 verification failed: corrupted file" }
        out.flush()
        return Result(total, counter.count, actual.hex())
    }

    private fun validateHeader(data: DataInputStream) {
        val magic = ByteArray(4); data.readFully(magic)
        require(magic.contentEquals(MAGIC)) { "Not an SCX file" }
        val version = data.readUnsignedShort()
        require(version == VERSION) { "Unsupported SCX version: $version" }
        require(data.readInt() == CHUNK_SIZE) { "Unsupported SCX chunk profile" }
    }
    private fun safeName(s: String) = s.substringAfterLast('/').substringAfterLast('\\')
        .filter { it.code >= 32 && it != '\u007f' }.take(180)
    private class CountingOutputStream(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set
        override fun write(b: Int) { target.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { target.write(b, off, len); count += len }
        override fun flush() = target.flush()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
