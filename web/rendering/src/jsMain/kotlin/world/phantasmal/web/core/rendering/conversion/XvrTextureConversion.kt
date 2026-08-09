package world.phantasmal.web.core.rendering.conversion

import org.khronos.webgl.Uint16Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.XvrTexture
import world.phantasmal.web.externals.three.DataTexture
import world.phantasmal.web.externals.three.LinearFilter
import world.phantasmal.web.externals.three.NearestFilter
import world.phantasmal.web.externals.three.MirroredRepeatWrapping
import world.phantasmal.web.externals.three.PixelFormat
import world.phantasmal.web.externals.three.RGBAFormat
import world.phantasmal.web.externals.three.RGBFormat
import world.phantasmal.web.externals.three.Texture
import world.phantasmal.web.externals.three.TextureDataType
import world.phantasmal.web.externals.three.TextureFilter
import world.phantasmal.web.externals.three.UnsignedByteType
import world.phantasmal.web.externals.three.UnsignedShort5551Type
import world.phantasmal.web.externals.three.UnsignedShort565Type
import world.phantasmal.web.externals.three.Wrapping
import kotlin.math.roundToInt

fun xvrTextureToThree(
    xvr: XvrTexture,
    magFilter: TextureFilter = LinearFilter,
    // TODO: Use LinearMipmapLinearFilter once we figure out mipmapping.
    minFilter: TextureFilter = LinearFilter,
    anisotropy: Int = 1,
    // Mirrored repeat was the historical hardcoded behavior; callers that know the material's
    // real UV addressing (see MeshBuilder) pass the accurate mode instead.
    wrapS: Wrapping = MirroredRepeatWrapping,
    wrapT: Wrapping = MirroredRepeatWrapping,
): Texture =
    when (xvr.format.second) {
        // D3DFMT_R5G6B5
        2 -> createDataTexture(
            Uint16Array(xvr.data.arrayBuffer),
            xvr.width,
            xvr.height,
            RGBFormat,
            UnsignedShort565Type,
            magFilter,
            minFilter,
            anisotropy,
            wrapS,
            wrapT,
        )
        // D3DFMT_A1R5G5B5
        3 -> {
            val originalData = Uint16Array(xvr.data.arrayBuffer)
            val data = Uint16Array(originalData.length)

            // Change bit order from ARGB 1555 to RGBA 5551.
            for (i in 0 until originalData.length) {
                val x = originalData[i].toInt()
                data[i] = ((x shl 1) or (x ushr 15)).toShort()
            }

            createDataTexture(
                data,
                xvr.width,
                xvr.height,
                RGBAFormat,
                UnsignedShort5551Type,
                magFilter,
                minFilter,
                anisotropy,
                wrapS,
                wrapT,
            )
        }
        // D3DFMT_DXT1 -- decoded to plain RGBA on the CPU rather than uploaded as a
        // WEBGL_compressed_texture_s3tc texture, since iOS GPUs (and thus WKWebView, which the
        // mobile game runs in) never supported S3TC/DXT at all -- only desktop GPUs do.
        6 -> createDataTexture(
            decodeDxt1(xvr.data.cursor(size = (xvr.width * xvr.height) / 2), xvr.width, xvr.height),
            xvr.width,
            xvr.height,
            RGBAFormat,
            UnsignedByteType,
            magFilter,
            minFilter,
            anisotropy,
            wrapS,
            wrapT,
        )
        // D3DFMT_DXT2 (DXT3 with premultiplied alpha, treated the same as DXT3 here since we
        // decode straight to RGBA and don't distinguish premultiplied vs. straight alpha).
        7 -> createDataTexture(
            decodeDxt3(xvr.data.cursor(size = xvr.width * xvr.height), xvr.width, xvr.height),
            xvr.width,
            xvr.height,
            RGBAFormat,
            UnsignedByteType,
            magFilter,
            minFilter,
            anisotropy,
            wrapS,
            wrapT,
        )
        // 1 -> D3DFMT_A8R8G8B8
        // 4 -> D3DFMT_A4R4G4B4
        // 5 -> D3DFMT_P8
        // 6 -> D3DFMT_R5G6B5
        // 8 -> D3DFMT_DXT3
        // 9 -> D3DFMT_DXT4
        // 10 -> D3DFMT_DXT5
        // 11 -> D3DFMT_A8R8G8B8
        // 12 -> D3DFMT_R5G6B5
        // 13 -> D3DFMT_A1R5G5B5
        // 14 -> D3DFMT_A4R4G4B4
        // 15 -> D3DFMT_YUY2
        // 16 -> D3DFMT_V8U8
        // 17 -> D3DFMT_A8
        // 18 -> D3DFMT_X1R5G5B5
        // 19 -> D3DFMT_X8R8G8B8
        else -> error("Format ${xvr.format.first}, ${xvr.format.second} not supported.")
    }

private fun createDataTexture(
    data: Any,
    width: Int,
    height: Int,
    format: PixelFormat,
    type: TextureDataType,
    magFilter: TextureFilter,
    minFilter: TextureFilter,
    anisotropy: Int,
    wrapS: Wrapping,
    wrapT: Wrapping,
): DataTexture =
    DataTexture(
        data,
        width,
        height,
        format,
        type,
        wrapS = wrapS,
        wrapT = wrapT,
        magFilter = magFilter,
        minFilter = minFilter,
        anisotropy = anisotropy,
    ).also {
        // DataTexture leaves mipmap generation off, so a mipmapping minFilter would find no
        // levels and draw the surface black. Turn it on exactly when such a filter is in use.
        it.generateMipmaps = minFilter != LinearFilter && minFilter != NearestFilter
    }

/**
 * Decodes a DXT1-compressed (BC1) image to a plain RGBA byte array. Each 4x4 pixel block is
 * compressed to 8 bytes: two RGB565 colors followed by a 2-bit interpolation code per pixel.
 */
private fun decodeDxt1(cursor: Cursor, width: Int, height: Int): Uint8Array {
    val image = Uint8Array(width * height * 4)
    val stride = 4 * width
    var i = 0

    while (cursor.bytesLeft >= 8) {
        val c0 = cursor.uShort().toInt()
        val c1 = cursor.uShort().toInt()
        val codes = cursor.int()

        val c0r = (c0 ushr 11) / 31.0
        val c0g = ((c0 ushr 5) and 0x3F) / 63.0
        val c0b = (c0 and 0x1F) / 31.0
        val c1r = (c1 ushr 11) / 31.0
        val c1g = ((c1 ushr 5) and 0x3F) / 63.0
        val c1b = (c1 and 0x1F) / 31.0

        for (j in 0 until 16) {
            val shift = 2 * (16 - j - 1)
            var r = .0
            var g = .0
            var b = .0
            var a = 1.0

            when ((codes ushr shift) and 0b11) {
                0 -> {
                    r = c0r; g = c0g; b = c0b
                }
                1 -> {
                    r = c1r; g = c1g; b = c1b
                }
                2 -> if (c0 > c1) {
                    r = (2 * c0r + c1r) / 3
                    g = (2 * c0g + c1g) / 3
                    b = (2 * c0b + c1b) / 3
                } else {
                    r = (c0r + c1r) / 2
                    g = (c0g + c1g) / 2
                    b = (c0b + c1b) / 2
                }
                3 -> if (c0 > c1) {
                    r = (c0r + 2 * c1r) / 3
                    g = (c0g + 2 * c1g) / 3
                    b = (c0b + 2 * c1b) / 3
                } else {
                    // Punch-through transparency, DXT1-only.
                    a = 0.0
                }
            }

            writePixel(image, i, stride, j, r, g, b, a)
        }

        i += 16
        if (i % stride == 0) i += 3 * stride
    }

    return image
}

/**
 * Decodes a DXT2/DXT3-compressed (BC2) image to a plain RGBA byte array. Each 4x4 pixel block is
 * 16 bytes: an explicit 4-bit alpha value per pixel, followed by a DXT1-style color block (always
 * in its 4-color, non-punch-through form, since alpha is stored separately here).
 */
private fun decodeDxt3(cursor: Cursor, width: Int, height: Int): Uint8Array {
    val image = Uint8Array(width * height * 4)
    val stride = 4 * width
    var i = 0

    while (cursor.bytesLeft >= 16) {
        val alphaLow = cursor.int()
        val alphaHigh = cursor.int()
        val c0 = cursor.uShort().toInt()
        val c1 = cursor.uShort().toInt()
        val codes = cursor.int()

        val c0r = (c0 ushr 11) / 31.0
        val c0g = ((c0 ushr 5) and 0x3F) / 63.0
        val c0b = (c0 and 0x1F) / 31.0
        val c1r = (c1 ushr 11) / 31.0
        val c1g = ((c1 ushr 5) and 0x3F) / 63.0
        val c1b = (c1 and 0x1F) / 31.0

        for (j in 0 until 16) {
            val shift = 2 * (16 - j - 1)
            val r: Double
            val g: Double
            val b: Double

            when ((codes ushr shift) and 0b11) {
                0 -> {
                    r = c0r; g = c0g; b = c0b
                }
                1 -> {
                    r = c1r; g = c1g; b = c1b
                }
                2 -> {
                    r = (2 * c0r + c1r) / 3
                    g = (2 * c0g + c1g) / 3
                    b = (2 * c0b + c1b) / 3
                }
                else -> {
                    r = (c0r + 2 * c1r) / 3
                    g = (c0g + 2 * c1g) / 3
                    b = (c0b + 2 * c1b) / 3
                }
            }

            val alphaShift = 4 * (16 - j - 1)
            val nibble = if (alphaShift >= 32) {
                (alphaHigh ushr (alphaShift - 32)) and 0xF
            } else {
                (alphaLow ushr alphaShift) and 0xF
            }
            val a = nibble / 15.0

            writePixel(image, i, stride, j, r, g, b, a)
        }

        i += 16
        if (i % stride == 0) i += 3 * stride
    }

    return image
}

/** Writes pixel [j] (0..15) of the current 4x4 block, starting at block offset [i], to [image]. */
private fun writePixel(
    image: Uint8Array,
    i: Int,
    stride: Int,
    j: Int,
    r: Double,
    g: Double,
    b: Double,
    a: Double,
) {
    val blockX = 3 - j % 4
    val blockY = 3 - j / 4
    val offset = i + (4 * blockX + blockY * stride)
    image[offset] = (r * 255).roundToInt().toByte()
    image[offset + 1] = (g * 255).roundToInt().toByte()
    image[offset + 2] = (b * 255).roundToInt().toByte()
    image[offset + 3] = (a * 255).roundToInt().toByte()
}
