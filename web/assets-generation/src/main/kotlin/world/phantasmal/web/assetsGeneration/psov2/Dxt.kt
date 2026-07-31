package world.phantasmal.web.assetsGeneration.psov2

import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Encodes an RGBA image to DXT2/DXT3 (BC2) block-compressed data, matching exactly the bit
 * layout phantasmal's renderer expects (see `decodeDxt3` in XvrTextureConversion.kt): per 4x4
 * block, 8 bytes of explicit 4-bit alpha followed by an 8-byte DXT1-style color block that is
 * always decoded in 4-color (non-punch-through) mode.
 *
 * Quality is a simple min/max-luminance endpoint heuristic (not a real PCA/cluster-fit encoder)
 * -- good enough for re-encoded game textures, not a general-purpose compressor.
 */
fun encodeDxt3(image: DecodedImage): ByteArray {
    val out = ByteArrayOutputStream()
    val blocksX = (image.width + 3) / 4
    val blocksY = (image.height + 3) / 4

    for (by in 0 until blocksY) {
        for (bx in 0 until blocksX) {
            encodeBlock(image, bx * 4, by * 4, out)
        }
    }

    return out.toByteArray()
}

private fun DecodedImage.pixelAt(x: Int, y: Int): IntArray {
    val cx = x.coerceIn(0, width - 1)
    val cy = y.coerceIn(0, height - 1)
    val o = (cy * width + cx) * 4
    return intArrayOf(
        rgba[o].toInt() and 0xFF,
        rgba[o + 1].toInt() and 0xFF,
        rgba[o + 2].toInt() and 0xFF,
        rgba[o + 3].toInt() and 0xFF,
    )
}

/** Block-local pixel index j, matching the decoder's (blockX, blockY) -> j mapping inverted. */
private fun blockIndex(bx: Int, by: Int): Int = (3 - by) * 4 + (3 - bx)

private fun to565(r: Int, g: Int, b: Int): Int =
    ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)

/** Expands a 565 color back to 0..255 per channel, matching the decoder's own reconstruction. */
private fun expand565(c: Int): IntArray {
    val r = ((c ushr 11) and 0x1F) * 255 / 31
    val g = ((c ushr 5) and 0x3F) * 255 / 63
    val b = (c and 0x1F) * 255 / 31
    return intArrayOf(r, g, b)
}

private fun dist2(a: IntArray, b: IntArray): Int {
    val dr = a[0] - b[0]
    val dg = a[1] - b[1]
    val db = a[2] - b[2]
    return dr * dr + dg * dg + db * db
}

private fun encodeBlock(image: DecodedImage, blockX: Int, blockY: Int, out: ByteArrayOutputStream) {
    val pixels = Array(16) { j ->
        // Invert blockIndex(bx, by) = (3-by)*4 + (3-bx) to find (bx, by) for pixel index j.
        val bx = 3 - j % 4
        val by = 3 - j / 4
        image.pixelAt(blockX + bx, blockY + by)
    }

    // Alpha.
    var alphaHigh = 0L
    var alphaLow = 0L
    for (j in 0 until 8) {
        val nibble = (pixels[j][3] * 15 + 127) / 255
        alphaHigh = alphaHigh or (nibble.toLong() shl (4 * (7 - j)))
    }
    for (j in 8 until 16) {
        val nibble = (pixels[j][3] * 15 + 127) / 255
        alphaLow = alphaLow or (nibble.toLong() shl (4 * (7 - (j - 8))))
    }
    writeIntLE(out, alphaLow.toInt())
    writeIntLE(out, alphaHigh.toInt())

    // Color: min/max luminance endpoints.
    var minLum = Int.MAX_VALUE
    var maxLum = Int.MIN_VALUE
    var minColor = pixels[0]
    var maxColor = pixels[0]

    for (p in pixels) {
        val lum = p[0] * 299 + p[1] * 587 + p[2] * 114

        if (lum < minLum) {
            minLum = lum
            minColor = p
        }

        if (lum > maxLum) {
            maxLum = lum
            maxColor = p
        }
    }

    val c0 = to565(maxColor[0], maxColor[1], maxColor[2])
    val c1 = to565(minColor[0], minColor[1], minColor[2])

    val e0 = expand565(c0)
    val e1 = expand565(c1)
    val e2 = intArrayOf(
        (2 * e0[0] + e1[0]) / 3,
        (2 * e0[1] + e1[1]) / 3,
        (2 * e0[2] + e1[2]) / 3,
    )
    val e3 = intArrayOf(
        (e0[0] + 2 * e1[0]) / 3,
        (e0[1] + 2 * e1[1]) / 3,
        (e0[2] + 2 * e1[2]) / 3,
    )

    var codes = 0L

    for (j in 0 until 16) {
        val p = pixels[j]
        val d0 = dist2(p, e0)
        val d1 = dist2(p, e1)
        val d2 = dist2(p, e2)
        val d3 = dist2(p, e3)
        val minD = minOf(d0, d1, d2, d3)

        val code = when (minD) {
            d0 -> 0
            d1 -> 1
            d2 -> 2
            else -> 3
        }

        codes = codes or (code.toLong() shl (2 * (15 - j)))
    }

    writeShortLE(out, c0)
    writeShortLE(out, c1)
    writeIntLE(out, codes.toInt())
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
