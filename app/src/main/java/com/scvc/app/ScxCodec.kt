package com.scvc.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * SCX v1: streaming, chunked container. This stores exact original bytes.
 * It is not a video re-encoder and does not promise to shrink already-compressed media.
 */
object ScxCodec {
    private val MAGIC = byteArrayOf(0x53, 0x43, 0x58, 0x31) // SCX1
    private const val VERSION = 1
    const val CHUNK_SIZE = 128 * 1024
    private const val MAX_NAME_BYTES = 1024
    private const val MAX_STORED_CHUNK = CHUNK_SIZE * 2 + 1024
    private const val CODEC_RAW = 0
    private const val CODEC_DEFLATE = 1

    data class Result(val inputBytes: Long, val outputBytes: Long, val sha256: String)

    fun compress(input: InputStream, output: OutputStream, originalName: String,
                 progress: (Long) -> Unit = {}): Result {
        val nameBytes = originalName.substringAfterLast('/').toByteArray(Charsets.UTF_8)
        require(nameBytes.isNotEmpty() && nameBytes.size <= MAX_NAME_BYTES) { "Invalid source file name" }
        val out = DataOutputStream(BufferedOutputStream(output, CHUNK_SIZE))
        out.write(MAGIC)
        out.writeShort(VERSION)
        out.writeInt(CHUNK_SIZE)
        out.writeShort(nameBytes.size)
        out.write(nameBytes)

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE)
        var total = 0L
        while (true) {
            var count = 0
            while (count < buffer.size) {
                val n = input.read(buffer, count, buffer.size - count)
                if (n < 0) break
                if (n == 0) continue
                count += n
            }
            if (count == 0) break
            digest.update(buffer, 0, count)
            val compressed = ByteArrayOutputStream(count)
            DeflaterOutputStream(compressed, Deflater(Deflater.DEFAULT_COMPRESSION, false), 8192).use { it.write(buffer, 0, count) }
            val packed = compressed.toByteArray()
            val useCompressed = packed.size < count
            out.writeInt(count)
            out.writeInt(if (useCompressed) packed.size else count)
            out.writeByte(if (useCompressed) CODEC_DEFLATE else CODEC_RAW)
            out.write(if (useCompressed) packed else buffer.copyOf(count))
            total += count
            progress(total)
        }
        out.writeInt(0)
        out.writeInt(0)
        out.writeByte(CODEC_RAW)
        val hash = digest.digest()
        out.write(hash)
        out.flush()
        return Result(total, -1L, hash.toHex())
    }

    fun originalName(input: InputStream): String {
        val data = DataInputStream(BufferedInputStream(input))
        readAndValidateHeader(data)
        val size = data.readUnsignedShort()
        val bytes = ByteArray(size)
        data.readFully(bytes)
        return bytes.toString(Charsets.UTF_8).ifBlank { "restored-file.bin" }
    }

    fun extract(input: InputStream, output: OutputStream,
                progress: (Long) -> Unit = {}): Result {
        val data = DataInputStream(BufferedInputStream(input, CHUNK_SIZE))
        readAndValidateHeader(data)
        val nameSize = data.readUnsignedShort()
        if (nameSize !in 1..MAX_NAME_BYTES) throw IllegalArgumentException("Invalid SCX file name length")
        val nameBytes = ByteArray(nameSize)
        data.readFully(nameBytes)

        val out = BufferedOutputStream(output, CHUNK_SIZE)
        val digest = MessageDigest.getInstance("SHA-256")
        val packedBuffer = ByteArray(CHUNK_SIZE)
        val plainBuffer = ByteArray(CHUNK_SIZE)
        var total = 0L
        while (true) {
            val plainLength = data.readInt()
            val storedLength = data.readInt()
            val codec = data.readUnsignedByte()
            if (plainLength == 0 && storedLength == 0) {
                if (codec != CODEC_RAW) throw IllegalArgumentException("Invalid SCX end marker")
                break
            }
            if (plainLength !in 1..CHUNK_SIZE) throw IllegalArgumentException("Invalid SCX chunk size")
            if (storedLength !in 1..MAX_STORED_CHUNK) throw IllegalArgumentException("Invalid SCX stored length")
            val stored = ByteArray(storedLength)
            data.readFully(stored)
            when (codec) {
                CODEC_RAW -> {
                    if (storedLength != plainLength) throw IllegalArgumentException("Invalid RAW chunk length")
                    out.write(stored)
                    digest.update(stored)
                }
                CODEC_DEFLATE -> {
                    val inflater = InflaterInputStream(ByteArrayInputStream(stored))
                    var produced = 0
                    inflater.use { stream ->
                        while (produced < plainLength) {
                            val n = stream.read(plainBuffer, 0, minOf(plainBuffer.size, plainLength - produced))
                            if (n < 0) break
                            if (n == 0) continue
                            out.write(plainBuffer, 0, n)
                            digest.update(plainBuffer, 0, n)
                            produced += n
                        }
                        if (produced != plainLength || stream.read() != -1) throw IllegalArgumentException("SCX chunk decompression length mismatch")
                    }
                }
                else -> throw IllegalArgumentException("Unsupported SCX codec: $codec")
            }
            total += plainLength
            progress(total)
        }
        val expected = ByteArray(32)
        data.readFully(expected)
        if (data.read() != -1) throw IllegalArgumentException("Unexpected data after SCX integrity footer")
        val actual = digest.digest()
        if (!MessageDigest.isEqual(expected, actual)) throw IllegalArgumentException("SHA-256 verification failed: file is corrupted")
        out.flush()
        return Result(total, -1L, actual.toHex())
    }

    private fun readAndValidateHeader(data: DataInputStream) {
        val magic = ByteArray(4)
        data.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw IllegalArgumentException("Not an SCX file")
        val version = data.readUnsignedShort()
        if (version != VERSION) throw IllegalArgumentException("Unsupported SCX version: $version")
        val chunkSize = data.readInt()
        if (chunkSize != CHUNK_SIZE) throw IllegalArgumentException("Unsupported SCX chunk profile")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
