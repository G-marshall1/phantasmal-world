package world.phantasmal.psolib.fileFormats.ninja

import mu.KotlinLogging
import world.phantasmal.core.isBitSet
import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.fileFormats.Vec2
import world.phantasmal.psolib.fileFormats.Vec3
import world.phantasmal.psolib.fileFormats.vec3Float
import kotlin.math.abs

// TODO:
//  - colors
//  - bump maps

private val logger = KotlinLogging.logger {}

// TODO: Simplify parser by not parsing chunks into vertices and meshes. Do the chunk to vertex/mesh
//       conversion at a higher level.
fun parseNjModel(cursor: Cursor, cachedChunks: MutableMap<Int, List<NjChunk>>): NjModel {
    val vlistOffset = cursor.int() // Vertex list
    val plistOffset = cursor.int() // Triangle strip index list
    val collisionSphereCenter = cursor.vec3Float()
    val collisionSphereRadius = cursor.float()
    val vertices: MutableList<NjVertex?> = mutableListOf()
    val meshes: MutableList<NjTriangleStrip> = mutableListOf()

    if (vlistOffset != 0) {
        cursor.seekStart(vlistOffset)

        for (chunk in parseChunks(cursor)) {
            if (chunk is NjChunk.Vertex) {
                for (vertex in chunk.vertices) {
                    while (vertices.size <= vertex.index) {
                        vertices.add(null)
                    }

                    vertices[vertex.index] = NjVertex(
                        vertex.position,
                        vertex.normal,
                        vertex.boneWeight,
                        vertex.boneWeightStatus,
                        vertex.calcContinue,
                    )
                }
            }
        }
    }

    if (plistOffset > 0) {
        cursor.seekStart(plistOffset)

        PolygonChunkProcessor(cachedChunks, meshes).process(parseChunks(cursor))
    }

    return NjModel(
        vertices,
        meshes,
        collisionSphereCenter,
        collisionSphereRadius,
    )
}

private class PolygonChunkProcessor(
    private val cachedChunks: MutableMap<Int, List<NjChunk>>,
    private val meshes: MutableList<NjTriangleStrip>,
) {
    private var textureId: Int? = null
    private var srcAlpha: Int? = null
    private var dstAlpha: Int? = null
    private var flipU: Boolean? = null
    private var flipV: Boolean? = null
    private var clampU: Boolean? = null
    private var clampV: Boolean? = null

    /**
     * When [cacheList] is non-null we are caching chunks.
     */
    private var cacheList: MutableList<NjChunk>? = null

    fun process(chunks: List<NjChunk>) {
        for (chunk in chunks) {
            if (cacheList == null) {
                when (chunk) {
                    is NjChunk.BlendAlpha -> {
                        srcAlpha = chunk.srcAlpha
                        dstAlpha = chunk.dstAlpha
                    }

                    is NjChunk.CachePolygonList -> {
                        cacheList = mutableListOf()
                        cachedChunks[chunk.cacheIndex] = cacheList!!
                    }

                    is NjChunk.DrawPolygonList -> {
                        val cached = cachedChunks[chunk.cacheIndex]

                        if (cached == null) {
                            logger.debug {
                                "Draw Polygon List chunk pointed to nonexistent cache index ${chunk.cacheIndex}."
                            }
                        } else {
                            process(cached)
                        }
                    }

                    is NjChunk.Tiny -> {
                        textureId = chunk.textureId
                        flipU = chunk.flipU
                        flipV = chunk.flipV
                        clampU = chunk.clampU
                        clampV = chunk.clampV
                    }

                    is NjChunk.Material -> {
                        srcAlpha = chunk.srcAlpha
                        dstAlpha = chunk.dstAlpha
                    }

                    is NjChunk.Strip -> {
                        for (strip in chunk.triangleStrips) {
                            strip.textureId = textureId
                            strip.srcAlpha = srcAlpha
                            strip.dstAlpha = dstAlpha
                            strip.flipU = flipU
                            strip.flipV = flipV
                            strip.clampU = clampU
                            strip.clampV = clampV
                        }

                        meshes.addAll(chunk.triangleStrips)
                    }

                    else -> {
                        // Ignore
                    }
                }
            } else {
                cacheList!!.add(chunk)
            }
        }
    }
}

private fun parseChunks(cursor: Cursor): List<NjChunk> {
    val chunks: MutableList<NjChunk> = mutableListOf()

    do {
        val chunkStartPosition = cursor.position
        val typeId = cursor.uByte().toInt()
        val flags = cursor.uByte().toInt()
        val chunkDataPosition = cursor.position
        var size = 0
        val chunk: NjChunk

        when (typeId) {
            0 -> {
                chunk = NjChunk.Null
            }
            1 -> {
                chunk = NjChunk.BlendAlpha(
                    srcAlpha = (flags ushr 3) and 0b111,
                    dstAlpha = flags and 0b111,
                )
            }
            2 -> {
                chunk = NjChunk.MipmapDAdjust(
                    adjust = flags and 0b1111,
                )
            }
            3 -> {
                chunk = NjChunk.SpecularExponent(
                    specular = flags and 0b11111,
                )
            }
            4 -> {
                chunk = NjChunk.CachePolygonList(
                    cacheIndex = flags,
                )
            }
            5 -> {
                chunk = NjChunk.DrawPolygonList(
                    cacheIndex = flags,
                )
            }
            in 8..9 -> {
                size = 2
                val textureBitsAndId = cursor.uShort().toInt()

                chunk = NjChunk.Tiny(
                    typeId,
                    flipU = flags.isBitSet(7),
                    flipV = flags.isBitSet(6),
                    clampU = flags.isBitSet(5),
                    clampV = flags.isBitSet(4),
                    mipmapDAdjust = flags.toUInt() and 0b1111u,
                    filterMode = textureBitsAndId ushr 14,
                    superSample = (textureBitsAndId and 0x40) != 0,
                    textureId = textureBitsAndId and 0x1FFF,
                )
            }
            in 17..31 -> {
                val bodySize = 2 * cursor.short()
                size = 2 + bodySize

                var diffuse: NjArgb? = null
                var ambient: NjArgb? = null
                var specular: NjErgb? = null

                if (typeId == 24) {
                    // Skip bump map data.
                    cursor.seek(bodySize)
                } else {
                    if (typeId.isBitSet(0)) {
                        diffuse = NjArgb(
                            b = cursor.uByte().toFloat() / 255f,
                            g = cursor.uByte().toFloat() / 255f,
                            r = cursor.uByte().toFloat() / 255f,
                            a = cursor.uByte().toFloat() / 255f,
                        )
                    }

                    if (typeId.isBitSet(1)) {
                        ambient = NjArgb(
                            b = cursor.uByte().toFloat() / 255f,
                            g = cursor.uByte().toFloat() / 255f,
                            r = cursor.uByte().toFloat() / 255f,
                            a = cursor.uByte().toFloat() / 255f,
                        )
                    }

                    if (typeId.isBitSet(2)) {
                        specular = NjErgb(
                            b = cursor.uByte(),
                            g = cursor.uByte(),
                            r = cursor.uByte(),
                            e = cursor.uByte(),
                        )
                    }
                }

                chunk = NjChunk.Material(
                    typeId,
                    srcAlpha = (flags ushr 3) and 0b111,
                    dstAlpha = flags and 0b111,
                    diffuse,
                    ambient,
                    specular,
                )
            }
            in 32..50 -> {
                size = 2 + 4 * cursor.short()
                chunk = NjChunk.Vertex(
                    typeId,
                    vertices = parseVertexChunk(cursor, typeId, flags),
                )
            }
            in 56..58 -> {
                size = 2 + 2 * cursor.short()
                chunk = NjChunk.Volume(
                    typeId,
                )

                // Volume data itself isn't parsed, just skipped -- the chunk loop below always
                // reseeks to chunkDataPosition + size afterwards regardless, which already skips
                // over it. (A stray second read of the length here, from the wrong position, used
                // to also seek forward with it: harmless if in-bounds since it gets overridden by
                // that reseek anyway, but if it read past the end of the buffer entirely -- e.g. a
                // volume chunk near the end of a mesh's data -- it threw and broke the whole
                // parse.)
            }
            in 64..75 -> {
                size = 2 + 2 * cursor.short()
                chunk = NjChunk.Strip(
                    typeId,
                    triangleStrips = parseTriangleStripChunk(cursor, typeId, flags),
                )
            }
            255 -> {
                chunk = NjChunk.End
            }
            else -> {
                val bodySize = 2 * cursor.short()
                size = 2 + bodySize
                chunk = NjChunk.Unknown(
                    typeId,
                )
                // Skip unknown data.
                cursor.seek(bodySize)
                logger.warn { "Unknown chunk type $typeId at offset ${chunkStartPosition}." }
            }
        }

        chunks.add(chunk)

        val bytesRead = cursor.position - chunkDataPosition

        check(bytesRead <= size) {
            "Expected to read at most $size bytes, actually read $bytesRead."
        }

        cursor.seekStart(chunkDataPosition + size)
    } while (chunk != NjChunk.End)

    return chunks
}

private fun parseVertexChunk(
    cursor: Cursor,
    chunkTypeId: Int,
    flags: Int,
): List<NjChunkVertex> {
    val boneWeightStatus = flags and 0b11
    val calcContinue = (flags and 0x80) != 0

    val index = cursor.uShort()
    val vertexCount = cursor.uShort()

    val vertices: MutableList<NjChunkVertex> = mutableListOf()

    for (i in (0u).toUShort() until vertexCount) {
        var vertexIndex = index + i
        val position = cursor.vec3Float()
        var normal: Vec3? = null
        var boneWeight: Float? = null

        when (chunkTypeId) {
            32 -> {
                // NJD_CV_SH
                cursor.seek(4) // Always 1.0
            }
            33 -> {
                // NJD_CV_VN_SH
                cursor.seek(4) // Always 1.0
                normal = cursor.vec3Float()
                cursor.seek(4) // Always 0.0
            }
            34 -> {
                // NJD_CV
                // Nothing to do.
            }
            in 35..40 -> {
                if (chunkTypeId == 37) {
                    // NJD_CV_NF
                    // NinjaFlags32
                    vertexIndex = index + cursor.uShort()
                    boneWeight = cursor.uShort().toFloat() / 255f
                } else {
                    // NJD_CV_D8
                    // NJD_CV_UF
                    // NJD_CV_S5
                    // NJD_CV_S4
                    // NJD_CV_IN
                    // Skip user flags and material information.
                    cursor.seek(4)
                }
            }
            41 -> {
                // NJD_CV_VN
                normal = cursor.vec3Float()
            }
            in 42..47 -> {
                normal = cursor.vec3Float()

                if (chunkTypeId == 44) {
                    // NJD_CV_VN_NF
                    // NinjaFlags32
                    vertexIndex = index + cursor.uShort()
                    boneWeight = cursor.uShort().toFloat() / 255f
                } else {
                    // NJD_CV_VN_D8
                    // NJD_CV_VN_UF
                    // NJD_CV_VN_S5
                    // NJD_CV_VN_S4
                    // NJD_CV_VN_IN
                    // Skip user flags and material information.
                    cursor.seek(4)
                }
            }
            in 48..50 -> {
                // NJD_CV_VNX
                // 32-Bit vertex normal in format: reserved(2)|x(10)|y(10)|z(10)
                val n = cursor.uInt()
                normal = Vec3(
                    ((n shr 20) and 0x3FFu).toFloat() / 0x3FF,
                    ((n shr 10) and 0x3FFu).toFloat() / 0x3FF,
                    (n and 0x3FFu).toFloat() / 0x3FF,
                )

                if (chunkTypeId >= 49) {
                    // NJD_CV_VNX_D8
                    // NJD_CV_VNX_UF
                    // Skip user flags and material information.
                    cursor.seek(4)
                }
            }
            else -> error("Unexpected chunk type ID ${chunkTypeId}.")
        }

        vertices.add(NjChunkVertex(
            vertexIndex.toInt(),
            position,
            normal,
            boneWeight,
            boneWeightStatus,
            calcContinue,
        ))
    }

    return vertices
}

private fun parseTriangleStripChunk(
    cursor: Cursor,
    chunkTypeId: Int,
    flags: Int,
): List<NjTriangleStrip> {
    val ignoreLight = flags.isBitSet(0)
    val ignoreSpecular = flags.isBitSet(1)
    val ignoreAmbient = flags.isBitSet(2)
    val useAlpha = flags.isBitSet(3)
    val doubleSide = flags.isBitSet(4)
    val flatShading = flags.isBitSet(5)
    val environmentMapping = flags.isBitSet(6)

    val userOffsetAndStripCount = cursor.short().toInt()
    val userFlagsSize = 2 * (userOffsetAndStripCount ushr 14)
    val stripCount = userOffsetAndStripCount and 0x3FFF

    var hasTexCoords = false
    var hasColor = false
    var hasNormal = false
    var hasDoubleTexCoords = false

    // UV fixed-point scale. Ninja has two textured-strip encodings per layout: UVN types store
    // UVs in 0-255, UVH types (66/69/72) in 0-1023. Decoding UVH as if it were UVN -- as this
    // parser always did -- multiplies every UV by ~4, so a texture tiles four times too densely
    // and large surfaces (area terrain especially) smear into fine parallel stripes.
    var uvDivisor = 255f

    when (chunkTypeId) {
        64 -> {
        }
        65 -> {
            hasTexCoords = true
        }
        66 -> {
            hasTexCoords = true
            uvDivisor = 1023f
        }
        67 -> {
            hasNormal = true
        }
        68 -> {
            hasTexCoords = true
            hasNormal = true
        }
        69 -> {
            hasTexCoords = true
            hasNormal = true
            uvDivisor = 1023f
        }
        70 -> {
            hasColor = true
        }
        71 -> {
            hasTexCoords = true
            hasColor = true
        }
        72 -> {
            hasTexCoords = true
            hasColor = true
            uvDivisor = 1023f
        }
        73 -> {
        }
        74, 75 -> {
            hasDoubleTexCoords = true
        }
        else -> error("Unexpected chunk type ID: ${chunkTypeId}.")
    }

    val strips: MutableList<NjTriangleStrip> = mutableListOf()

    repeat(stripCount) {
        val windingFlagAndIndexCount = cursor.short().toInt()
        val clockwiseWinding = windingFlagAndIndexCount < 0
        val indexCount = abs(windingFlagAndIndexCount)

        val vertices: MutableList<NjMeshVertex> = mutableListOf()

        for (j in 0 until indexCount) {
            val index = cursor.uShort().toInt()

            // UVs are *signed* 16-bit fixed point: a strip whose mapping crosses the texture
            // origin stores small negative values (perfectly normal -- wrapping handles them).
            // Reading them unsigned turned e.g. -422 into 65114, i.e. ~64 texture repeats across
            // one triangle, which smeared any such surface into dense parallel stripes.
            val texCoords = if (hasTexCoords) {
                Vec2(
                    cursor.short().toFloat() / uvDivisor,
                    cursor.short().toFloat() / uvDivisor,
                )
            } else null

            // Ignore ARGB8888 color.
            if (hasColor) {
                cursor.seek(4)
            }

            val normal = if (hasNormal) {
                Vec3(
                    cursor.uShort().toFloat() / 255f,
                    cursor.uShort().toFloat() / 255f,
                    cursor.uShort().toFloat() / 255f,
                )
            } else null

            // Ignore double texture coordinates (Ua, Vb, Ua, Vb).
            if (hasDoubleTexCoords) {
                cursor.seek(8)
            }

            // User flags start at the third vertex because they are per-triangle.
            if (j >= 2) {
                cursor.seek(userFlagsSize)
            }

            vertices.add(NjMeshVertex(
                index,
                normal,
                texCoords,
            ))
        }

        strips.add(NjTriangleStrip(
            ignoreLight,
            ignoreSpecular,
            ignoreAmbient,
            useAlpha,
            doubleSide,
            flatShading,
            environmentMapping,
            clockwiseWinding,
            hasTexCoords,
            hasNormal,
            textureId = null,
            srcAlpha = null,
            dstAlpha = null,
            vertices,
        ))
    }

    return strips
}
