package world.phantasmal.web.core.rendering.conversion

import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint16Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.set
import world.phantasmal.core.asArray
import world.phantasmal.core.jsArrayOf
import world.phantasmal.core.unsafe.UnsafeMap
import world.phantasmal.psolib.fileFormats.ninja.XvrTexture
import world.phantasmal.web.externals.three.*
import world.phantasmal.webui.obj

class MeshBuilder(
    private val textures: List<XvrTexture?> = emptyList(),
    private val textureCache: UnsafeMap<Int, Texture?> = UnsafeMap(),
    private val anisotropy: Int = 1,
    /**
     * Minification filter. The default is unfiltered-between-mips because most assets here are
     * characters and props seen roughly head-on; a mipmapping filter is what large surfaces seen
     * at a grazing angle need, and is also what makes [anisotropy] do anything at all -- without
     * mipmaps there are no levels for anisotropic sampling to choose between.
     */
    private val minFilter: TextureFilter = LinearFilter,
) {
    private val positions = mutableListOf<Vector3>()
    private val normals = mutableListOf<Vector3>()
    private val uvs = mutableListOf<Vector2>()
    private val boneIndices = mutableListOf<Short>()
    private val boneWeights = mutableListOf<Float>()
    private val bones = mutableListOf<Bone>()

    /**
     * One group per material.
     */
    private val groups = mutableListOf<Group>()
    private var indexCount = 0

    private var defaultMaterial: Material? = null

    /**
     * [flipU]/[flipV]/[clampU]/[clampV] are the material's UV addressing flags where the source
     * format records them (NJCM's texture-id chunk does): clamp beats flip beats the plain-repeat
     * default. Null means the caller's format doesn't say, which keeps the mirrored-repeat
     * behavior this builder always had -- the flags were previously ignored entirely, which
     * mirrored every second tile of any texture authored for plain repeat (obvious on large
     * tiled surfaces like area terrain).
     */
    fun getGroupIndex(
        textureIndex: Int?,
        alpha: Boolean,
        additiveBlending: Boolean,
        flipU: Boolean? = null,
        flipV: Boolean? = null,
        clampU: Boolean? = null,
        clampV: Boolean? = null,
    ): Int {
        val wrapS = wrapMode(clampU, flipU)
        val wrapT = wrapMode(clampV, flipV)

        val groupIndex = groups.indexOfFirst {
            it.textureIndex == textureIndex &&
                    it.alpha == alpha &&
                    it.additiveBlending == additiveBlending &&
                    it.wrapS == wrapS &&
                    it.wrapT == wrapT
        }

        return if (groupIndex != -1) {
            groupIndex
        } else {
            groups.add(Group(textureIndex, alpha, additiveBlending, wrapS, wrapT))
            groups.lastIndex
        }
    }

    val vertexCount: Int
        get() = positions.size

    fun getPosition(index: Int): Vector3 =
        positions[index]

    fun getNormal(index: Int): Vector3 =
        normals[index]

    /**
     * Every triangle's 3 vertex indices, flattened across all material groups in the same order
     * [build] writes them into the final geometry's index buffer (i.e. this is exactly the
     * triangle soup the resulting mesh renders, independent of material/texture grouping).
     */
    fun allIndices(): IntArray {
        val result = IntArray(indexCount)
        var offset = 0

        for (group in groups) {
            for (idx in group.indices.asArray()) {
                result[offset++] = idx
            }
        }

        return result
    }

    fun vertex(
        position: Vector3,
        normal: Vector3,
        uv: Vector2? = null,
        boneIndices: IntArray? = null,
        boneWeights: FloatArray? = null,
    ) {
        positions.add(position)
        normals.add(normal)
        uv?.let { uvs.add(uv) }

        if (boneIndices != null && boneWeights != null) {
            require(boneIndices.size == 4)
            require(boneWeights.size == 4)

            for (index in boneIndices) {
                this.boneIndices.add(index.toShort())
            }

            for (weight in boneWeights) {
                this.boneWeights.add(weight)
            }
        }
    }

    fun index(groupIdx: Int, index: Int) {
        groups[groupIdx].indices.push(index)
        indexCount++
    }

    fun bone(bone: Bone) {
        bones.add(bone)
    }

    fun defaultMaterial(material: Material) {
        defaultMaterial = material
    }

    fun buildMesh(boundingVolumes: Boolean = false): Mesh =
        build(skinning = false, boundingVolumes) { geom, materials, _ ->
            Mesh(geom, materials)
        }

    /**
     * Creates an [InstancedMesh] with 0 instances.
     */
    fun buildInstancedMesh(maxInstances: Int, boundingVolumes: Boolean = false): InstancedMesh =
        build(skinning = false, boundingVolumes) { geom, materials, _ ->
            InstancedMesh(geom, materials, maxInstances).apply {
                // Start with 0 instances.
                count = 0
            }
        }

    /**
     * Creates a [SkinnedMesh] with bones and a skeleton for animation.
     */
    fun buildSkinnedMesh(boundingVolumes: Boolean = false): SkinnedMesh =
        build(skinning = true, boundingVolumes) { geom, materials, bones ->
            SkinnedMesh(geom, materials).apply {
                add(bones[0])
                bind(Skeleton(bones))
            }
        }

    private fun <M : Mesh> build(
        skinning: Boolean,
        boundingVolumes: Boolean,
        createMesh: (BufferGeometry, Array<Material>, Array<Bone>) -> M,
    ): M {
        check(positions.size == normals.size)
        check(uvs.isEmpty() || positions.size == uvs.size)

        val positions = Float32Array(3 * positions.size)
        val normals = Float32Array(3 * normals.size)
        val uvs = if (uvs.isEmpty()) null else Float32Array(2 * uvs.size)

        for (i in this.positions.indices) {
            val pos = this.positions[i]
            positions[3 * i] = pos.x.toFloat()
            positions[3 * i + 1] = pos.y.toFloat()
            positions[3 * i + 2] = pos.z.toFloat()

            val normal = this.normals[i]
            normals[3 * i] = normal.x.toFloat()
            normals[3 * i + 1] = normal.y.toFloat()
            normals[3 * i + 2] = normal.z.toFloat()

            uvs?.let {
                val uv = this.uvs[i]
                uvs[2 * i] = uv.x.toFloat()
                uvs[2 * i + 1] = uv.y.toFloat()
            }
        }

        val geom = BufferGeometry()
        geom.setAttribute("position", Float32BufferAttribute(positions, 3))
        geom.setAttribute("normal", Float32BufferAttribute(normals, 3))
        uvs?.let { geom.setAttribute("uv", Float32BufferAttribute(uvs, 2)) }

        if (skinning) {
            check(this.positions.size == boneIndices.size / 4)
            check(this.positions.size == boneWeights.size / 4)

            boneIndices.maxOrNull()?.let {
                check(it < bones.size)
            }

            geom.setAttribute(
                "skinIndex",
                Uint16BufferAttribute(Uint16Array(boneIndices.toTypedArray()), 4)
            )
            geom.setAttribute(
                "skinWeight",
                Float32BufferAttribute(Float32Array(boneWeights.toTypedArray()), 4)
            )
        }

        // A mesh built from many merged sections (e.g. a whole map's terrain + props) can exceed
        // Uint16's 65535-vertex ceiling, unlike the individual character/enemy models this was
        // originally sized for. Indices used to be truncated to Short unconditionally, which
        // silently wrapped any index past 32767 into a negative value -- Three.js's own indexed
        // buffer renderer picks UNSIGNED_SHORT vs UNSIGNED_INT by checking the array's concrete
        // type (Uint32Array vs anything else), so the buffer's actual type has to match, not just
        // its byte size.
        val useWideIndices = this.positions.size > 65535
        val indices16 = if (useWideIndices) null else Uint16Array(indexCount)
        val indices32 = if (useWideIndices) Uint32Array(indexCount) else null

        var offset = 0

        val materials = mutableListOf<Material>()

        val defaultMaterial = defaultMaterial ?: MeshLambertMaterial(obj {
            this.skinning = skinning
            side = DoubleSide
        })

        for (group in groups) {
            if (useWideIndices) {
                indices32!!.set(group.indices.asArray(), offset)
            } else {
                indices16!!.set(group.indices.asArray().map { it.toShort() }.toTypedArray(), offset)
            }
            geom.addGroup(offset, group.indices.length, materials.size)

            var tex: Texture? = null

            if (group.textureIndex != null) {
                // One cache entry per texture *and* wrap combination -- the same image can be
                // clamped on one material and tiled on another, and a three.js Texture carries
                // its wrap modes with it.
                val cacheKey = group.textureIndex * 9 + wrapCacheBits(group.wrapS) * 3 +
                        wrapCacheBits(group.wrapT)
                tex = textureCache.get(cacheKey)

                if (tex == null) {
                    tex = textures.getOrNull(group.textureIndex)?.let { xvm ->
                        xvrTextureToThree(
                            xvm,
                            minFilter = minFilter,
                            anisotropy = anisotropy,
                            wrapS = group.wrapS,
                            wrapT = group.wrapT,
                        )
                    }
                    textureCache.set(cacheKey, tex)
                }
            }

            val mat = if (tex == null) {
                defaultMaterial
            } else {
                MeshBasicMaterial(obj {
                    this.skinning = skinning
                    map = tex
                    side = DoubleSide

                    if (group.alpha) {
                        transparent = true
                        alphaTest = 0.01
                    }

                    if (group.additiveBlending) {
                        transparent = true
                        alphaTest = 0.01
                        blending = AdditiveBlending
                    }
                })
            }

            materials.add(mat)
            offset += group.indices.length
        }

        geom.setIndex(
            if (useWideIndices) Uint32BufferAttribute(indices32!!, 1)
            else Uint16BufferAttribute(indices16!!, 1)
        )

        if (boundingVolumes) {
            geom.computeBoundingBox()
            geom.computeBoundingSphere()
        }

        return createMesh(geom, materials.toTypedArray(), bones.toTypedArray())
    }

    private class Group(
        val textureIndex: Int?,
        val alpha: Boolean,
        val additiveBlending: Boolean,
        val wrapS: Wrapping,
        val wrapT: Wrapping,
    ) {
        val indices = jsArrayOf<Int>()
    }

    companion object {
        /**
         * Sega Ninja UV addressing to three.js wrapping. Clamp wins over flip; both null (a
         * format that doesn't record the flags) keeps the historical mirrored-repeat default.
         */
        private fun wrapMode(clamp: Boolean?, flip: Boolean?): Wrapping = when {
            clamp == true -> ClampToEdgeWrapping
            flip == null -> MirroredRepeatWrapping
            flip -> MirroredRepeatWrapping
            else -> RepeatWrapping
        }

        /** Stable small int per wrap mode, for the texture cache key. */
        private fun wrapCacheBits(wrapping: Wrapping): Int = when (wrapping) {
            RepeatWrapping -> 0
            ClampToEdgeWrapping -> 1
            else -> 2
        }
    }
}
