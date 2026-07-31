package world.phantasmal.web.assetsGeneration.psov2

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.cursor.cursor

/** A fully decoded texture, RGBA8888, row-major, top-to-bottom. */
class DecodedImage(val width: Int, val height: Int, val rgba: ByteArray)

private const val ARGB_1555 = 0x00
private const val RGB_565 = 0x01
private const val ARGB_4444 = 0x02

private const val TWIDDLED = 0x01
private const val TWIDDLED_MM = 0x02
private const val VQ = 0x03
private const val VQ_MM = 0x04
private const val PALLET4_MM = 0x06
private const val PALLET8_MM = 0x08
private const val RECTANGLE = 0x09
private const val SMALL_VQ = 0x10
private const val SMALL_VQ_MM = 0x11
private const val TWIDDLED_MM_ALT = 0x12

private val MIPMAP_FORMATS =
    setOf(TWIDDLED_MM, VQ_MM, PALLET4_MM, PALLET8_MM, SMALL_VQ_MM, TWIDDLED_MM_ALT)
private val SMALL_FORMATS = setOf(SMALL_VQ, SMALL_VQ_MM)

/**
 * Decodes a psov2/Dreamcast PVR texture file. Handles both a single texture (`GBIX`/`PVRT`
 * magic) and a texture pack (`PVMH` magic, used for e.g. enemy and area texture archives).
 * Ported faithfully from psov2's NinjaTexture.js, which is the tool that produced these assets.
 */
fun decodeTextures(bytes: ByteArray): List<DecodedImage> {
    val cursor = Buffer.fromByteArray(bytes, Endianness.Little).cursor()
    val magic = cursor.stringAscii(4, nullTerminated = false)
    cursor.seekStart(0)

    return when (magic) {
        "PVMH" -> readTexturePack(cursor)
        else -> listOf(readSingleTexture(cursor))
    }
}

/**
 * Scans forward 4 bytes at a time (matching the reference's repeated `readString(4)` calls, which
 * is only correct because leading GBIX chunks are always a multiple of 4 bytes long) until
 * [magic] is found. Leaves the cursor positioned right after the magic bytes.
 */
private fun seekToMagic(cursor: Cursor, magic: String) {
    while (cursor.stringAscii(4, nullTerminated = false) != magic) {
        // Keep scanning.
    }
}

/**
 * Reads one "PVRT"-prefixed chunk. The reference implementation always skips to exactly
 * (position right after the length field) + length afterwards, regardless of how many bytes the
 * body actually consumed -- so we replicate that jump rather than trusting our own field parsing
 * to land in the right place, matching the reference byte-for-byte.
 */
private fun readPvrtChunk(cursor: Cursor): DecodedImage {
    seekToMagic(cursor, "PVRT")
    val chunkLen = cursor.uInt().toInt()
    val afterLenPos = cursor.position
    val image = readTextureBody(cursor)
    cursor.seekStart(afterLenPos + chunkLen)
    return image
}

private fun readSingleTexture(cursor: Cursor): DecodedImage = readPvrtChunk(cursor)

private fun readTexturePack(cursor: Cursor): List<DecodedImage> {
    cursor.seek(4) // "PVMH"
    val chunkLen = cursor.uInt().toInt()
    val afterLenPos = cursor.position

    val nbTex = run {
        cursor.seek(2) // texFlags, unused: entry-table layout isn't needed since we skip via
        // chunkLen below rather than manually tallying per-entry field sizes.
        cursor.uShort().toInt()
    }

    // Skip the rest of the entry table (name/format/etc. per texture) directly via the chunk's
    // declared length, exactly like the reference implementation's setOfs/clearOfs windowing.
    cursor.seekStart(afterLenPos + chunkLen)

    return (0 until nbTex).map { readPvrtChunk(cursor) }
}

private fun readTextureBody(cursor: Cursor): DecodedImage {
    val pixelFormat = cursor.uByte().toInt()
    val dataFormat = cursor.uByte().toInt()
    cursor.seek(2)

    val width = cursor.uShort().toInt()
    val height = cursor.uShort().toInt()

    val isMipmap = dataFormat in MIPMAP_FORMATS
    val isSmall = dataFormat in SMALL_FORMATS

    val pixels: IntArray = when (dataFormat) {
        TWIDDLED_MM, TWIDDLED, TWIDDLED_MM_ALT -> decodeTwiddle(cursor, width, isMipmap)
        VQ, VQ_MM, SMALL_VQ, SMALL_VQ_MM -> decodeVector(cursor, width, height, isMipmap, isSmall)
        RECTANGLE -> decodeRectangle(cursor, width, height)
        else -> error("Unsupported PVR pixel data format $dataFormat.")
    }

    val rgba = ByteArray(width * height * 4)

    for (i in pixels.indices) {
        val pix = pixels[i]
        var r = 0
        var g = 0
        var b = 0
        var a = 255

        when (pixelFormat) {
            ARGB_1555 -> {
                a = if (pix and 0x8000 != 0) 255 else 0
                r = (pix and 0x7C00) shr 7
                g = (pix and 0x03E0) shr 2
                b = (pix and 0x001F) shl 3
            }
            RGB_565 -> {
                r = (pix shr 8) and (0x1f shl 3)
                g = (pix shr 3) and (0x3f shl 2)
                b = (pix shl 3) and (0x1f shl 3)
                a = 255
            }
            ARGB_4444 -> {
                a = (pix shr 8) and 0xf0
                r = (pix shr 4) and 0xf0
                g = pix and 0xf0
                b = (pix shl 4) and 0xf0
            }
        }

        val o = i * 4
        rgba[o] = r.toByte()
        rgba[o + 1] = g.toByte()
        rgba[o + 2] = b.toByte()
        rgba[o + 3] = a.toByte()
    }

    return DecodedImage(width, height, rgba)
}

/** Skips over the mipmap levels smaller than [width] that precede the full-size level. */
private fun decodeTwiddle(cursor: Cursor, width: Int, isMipmap: Boolean): IntArray {
    if (isMipmap) {
        var seekOfs = 0x02
        var i = 0
        while (i <= 10) {
            val mipWidth = 1 shl i
            if (width == mipWidth) break
            seekOfs += mipWidth * mipWidth * 2
            i++
        }
        cursor.seek(seekOfs)
    }

    return readTwiddled(cursor, width, isVq = false)
}

private fun decodeVector(
    cursor: Cursor,
    width: Int,
    height: Int,
    isMipmap: Boolean,
    isSmall: Boolean,
): IntArray {
    var clutSize = 256

    if (isSmall) {
        clutSize = if (isMipmap) {
            when (width) {
                8, 16 -> 16
                32 -> 64
                else -> clutSize
            }
        } else {
            when (width) {
                8, 16 -> 16
                32 -> 32
                64 -> 128
                else -> clutSize
            }
        }
    }

    val clut = IntArray(clutSize * 4) { cursor.uShort().toInt() }

    if (isMipmap) {
        var seekOfs = 0x01
        var i = 0
        while (i <= 10) {
            val mipWidth = 1 shl i
            if (width == mipWidth) break
            seekOfs += (mipWidth * mipWidth) / 4
            i++
        }
        cursor.seek(seekOfs)
    }

    val halfWidth = width / 2
    val dataBody = readTwiddled(cursor, halfWidth, isVq = true)

    val image = IntArray(width * height)
    var x = 0
    var y = 0

    for (i in dataBody.indices) {
        var clutOfs = dataBody[i] * 4

        for (xOfs in 0 until 2) {
            for (yOfs in 0 until 2) {
                val pix = (y * 2 + yOfs) * width + (x * 2 + xOfs)
                image[pix] = clut[clutOfs++]
            }
        }

        x++
        if (x == halfWidth) {
            x = 0
            y++
        }
    }

    return image
}

private fun decodeRectangle(cursor: Cursor, width: Int, height: Int): IntArray {
    // Matches the reference implementation's indexing (y * height + x) exactly.
    val image = IntArray(width * height)

    for (y in 0 until height) {
        for (x in 0 until width) {
            image[y * height + x] = cursor.uShort().toInt()
        }
    }

    return image
}

/**
 * Reads a square, Morton-order ("twiddled") block of texels and de-swizzles it into row-major
 * order. [isVq] selects whether texels are read as bytes (VQ codebook indices) or shorts (raw
 * pixels). Quadrant recursion order (top-left, bottom-left, top-right, bottom-right) matches the
 * reference implementation exactly -- it determines which source bytes land at which final pixel.
 */
private fun readTwiddled(cursor: Cursor, width: Int, isVq: Boolean): IntArray {
    val list = IntArray(width * width)

    fun subdivideAndMove(x: Int, y: Int, mipSize: Int) {
        if (mipSize == 1) {
            list[y * width + x] = if (isVq) cursor.uByte().toInt() else cursor.uShort().toInt()
        } else {
            val ns = mipSize / 2
            subdivideAndMove(x, y, ns)
            subdivideAndMove(x, y + ns, ns)
            subdivideAndMove(x + ns, y, ns)
            subdivideAndMove(x + ns, y + ns, ns)
        }
    }

    subdivideAndMove(0, 0, width)
    return list
}
