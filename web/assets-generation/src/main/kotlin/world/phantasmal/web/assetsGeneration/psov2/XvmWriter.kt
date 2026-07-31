package world.phantasmal.web.assetsGeneration.psov2

import java.io.ByteArrayOutputStream

private const val DXT3_FORMAT2 = 7

/**
 * Builds a single XVRT IFF chunk (see psolib's `parseXvr`/`Iff.kt`) wrapping DXT3-compressed
 * image data. Chunk layout: 4-byte "XVRT" type, 4-byte chunk size, then the chunk body: format1
 * (unused by the renderer, written as 0), format2 (7 = DXT3), id, width, height, data size, 36
 * reserved bytes, then the DXT3 data itself.
 */
private fun buildXvrtChunk(image: DecodedImage, id: Int): ByteArray {
    val dxtData = encodeDxt3(image)
    val out = ByteArrayOutputStream()

    out.write("XVRT".toByteArray(Charsets.US_ASCII))
    writeIntLE(out, 20 + 36 + dxtData.size) // Chunk size.
    writeIntLE(out, 0) // format1, unused.
    writeIntLE(out, DXT3_FORMAT2) // format2.
    writeIntLE(out, id)
    writeShortLE(out, image.width)
    writeShortLE(out, image.height)
    writeIntLE(out, dxtData.size)
    out.write(ByteArray(36))
    out.write(dxtData)

    return out.toByteArray()
}

/**
 * Builds an XVM file (see psolib's `parseXvm`) containing one XVRT chunk per image, in order. No
 * XVMH header chunk is written since `parseXvm` only requires it when there are zero textures.
 */
fun buildXvm(images: List<DecodedImage>): ByteArray {
    val out = ByteArrayOutputStream()

    for ((id, image) in images.withIndex()) {
        out.write(buildXvrtChunk(image, id))
    }

    return out.toByteArray()
}

/**
 * Builds an AFS archive (see psolib's `Afs.kt`) whose entries are the given already-encoded
 * blobs, packed back-to-back with no padding between them.
 */
fun buildAfs(entries: List<ByteArray>): ByteArray {
    val headerSize = 8 + entries.size * 8
    val offsets = IntArray(entries.size)
    var pos = headerSize

    for (i in entries.indices) {
        offsets[i] = pos
        pos += entries[i].size
    }

    val out = ByteArrayOutputStream()
    out.write("AFS".toByteArray(Charsets.US_ASCII))
    out.write(0)
    writeIntLE(out, entries.size)

    for (i in entries.indices) {
        writeIntLE(out, offsets[i])
        writeIntLE(out, entries[i].size)
    }

    for (entry in entries) {
        out.write(entry)
    }

    return out.toByteArray()
}

private fun writeShortLE(out: ByteArrayOutputStream, v: Int) {
    out.write(v and 0xFF)
    out.write((v shr 8) and 0xFF)
}

private fun writeIntLE(out: ByteArrayOutputStream, v: Int) {
    out.write(v and 0xFF)
    out.write((v shr 8) and 0xFF)
    out.write((v shr 16) and 0xFF)
    out.write((v shr 24) and 0xFF)
}
