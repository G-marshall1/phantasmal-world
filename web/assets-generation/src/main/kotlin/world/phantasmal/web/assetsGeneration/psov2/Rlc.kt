package world.phantasmal.web.assetsGeneration.psov2

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.compression.prs.prsDecompress
import world.phantasmal.psolib.cursor.cursor

/**
 * Reads psov2's player motion archive (plymotiondata.rlc): a big-endian container of PRC-encoded
 * entries (see [decryptPrc]). Format (ported from psov2's NinjaFile.js `api_rlc`): 16-byte name +
 * u32 count + u32 table offset + 8 bytes (end offset, always-zero field), then at the table
 * offset, `count` big-endian (offset, length) pairs. Entry byte ranges are zero-padded up to a
 * multiple of 4 bytes if needed, matching the reference's own padding before decryption.
 */
fun readRlc(bytes: ByteArray): List<ByteArray> {
    val cursor = Buffer.fromByteArray(bytes, Endianness.Big).cursor()
    cursor.seek(0x10)
    val count = cursor.int()
    val tableOffset = cursor.int()

    cursor.seekStart(tableOffset)
    val ranges = (0 until count).map { Pair(cursor.int(), cursor.int()) }

    return ranges.map { (offset, length) ->
        cursor.seekStart(offset)
        val raw = cursor.buffer(length).byteArray

        if (length % 4 == 0) raw else raw + ByteArray(4 - length % 4)
    }
}

/**
 * Decrypts and decompresses one PRC-encoded blob (SEGA's "PRC" format: a classic lagged-Fibonacci
 * stream cipher over PRS-compressed data, also used for PSO quest.bin/quest.dat). Ported from
 * psov2's NinjaFile.js `api_prc`. The first 8 bytes are an (uncompressed length, key) header (both
 * little-endian, unrelated to the big-endian RLC container around it); the rest is XORed 4 bytes
 * at a time against a keystream, then PRS-decompressed.
 */
fun decryptPrc(bytes: ByteArray): ByteArray {
    val key = readIntLE(bytes, 4)
    val body = bytes.copyOfRange(8, bytes.size)

    val stream = IntArray(56)
    stream[55] = key
    var streamKey = key
    var tmp = 1
    var i = 0x15

    while (i <= 0x46E) {
        val idx = i % 55
        streamKey -= tmp
        stream[idx] = tmp
        tmp = streamKey
        streamKey = stream[idx]
        i += 0x15
    }

    repeat(4) { mixStream(stream) }

    var pos = 56
    var offset = 0

    while (offset < body.size) {
        if (pos == 56) {
            mixStream(stream)
            pos = 1
        }

        val decrypted = readIntLE(body, offset) xor stream[pos]
        pos++
        writeIntLE(body, offset, decrypted)
        offset += 4
    }

    val cursor = Buffer.fromByteArray(body, Endianness.Little).cursor()
    return prsDecompress(cursor).unwrap().buffer().byteArray
}

private fun mixStream(stream: IntArray) {
    var ptr = 1

    for (i in 24 downTo 1) {
        stream[ptr] -= stream[ptr + 31]
        ptr++
    }

    ptr = 25

    for (i in 31 downTo 1) {
        stream[ptr] -= stream[ptr - 24]
        ptr++
    }
}

private fun readIntLE(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)

private fun writeIntLE(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value and 0xFF).toByte()
    bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
    bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
}
