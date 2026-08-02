package world.phantasmal.web.mobileGame.world

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import world.phantasmal.core.isBitSet
import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.CollisionGeometry
import world.phantasmal.psolib.fileFormats.CollisionMesh
import world.phantasmal.psolib.fileFormats.CollisionTriangle
import world.phantasmal.psolib.fileFormats.Vec3
import world.phantasmal.psolib.fileFormats.ninja.parseXvm
import world.phantasmal.web.core.dot
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.conversion.MeshBuilder
import world.phantasmal.web.core.rendering.conversion.collisionGeometryToGroup
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToMeshBuilder
import world.phantasmal.web.core.rendering.conversion.setFromVec3
import world.phantasmal.web.externals.three.Group
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.Vector3

/**
 * Cave/Mine/Ruins each ship several complete, fixed room layouts in psov2 (Forest has just one --
 * see MAP_SPECS's doc comment in :web:assets-generation's MapSpecs.kt for how these were counted
 * and named: variant 0 keeps the bare area slug, e.g. "cave01", and variants 1+ are
 * "cave01Layout2".."cave01Layout6"). Mirrors the real game picking a random layout each time an
 * area is entered, rather than always showing the same one.
 */
private val AREA_LAYOUT_COUNTS: Map<String, Int> = mapOf(
    "cave01" to 6, "cave02" to 5, "cave03" to 6,
    "mines01" to 6, "mines02" to 6,
    "ruins01" to 5, "ruins02" to 5, "ruins03" to 5,
    "ultimateCave01" to 6, "ultimateCave02" to 5, "ultimateCave03" to 6,
    "ultimateMines01" to 6, "ultimateMines02" to 6,
)

/**
 * Resolves a base area slug (e.g. "cave01") to a random one of its layout variants (e.g.
 * "cave01Layout4"). Slugs not in [AREA_LAYOUT_COUNTS] -- Forest, static stages, or an already-
 * specific "...LayoutN" slug passed in for direct testing -- pass through unchanged.
 */
fun randomAreaLayoutSlug(baseSlug: String): String {
    val count = AREA_LAYOUT_COUNTS[baseSlug] ?: return baseSlug
    val i = Random.nextInt(count)
    return if (i == 0) baseSlug else "${baseSlug}Layout${i + 1}"
}

class GameMap(
    val renderObject: Object3D,
    /** Raw parsed triangle data -- the character controller needs the triangle data directly. */
    val collisionGeometry: CollisionGeometry,
    /** Ground-only subset of the collision geometry, for spawn/ground-height raycasts. */
    val walkableCollisionObject: Object3D,
)

/**
 * Loads one hardcoded map (Forest 1, episode I area 1). Trimmed down from the Quest Editor's
 * AreaAssetLoader -- drops section/portal bookkeeping the game doesn't need.
 */
class MapAssetLoader(private val assetLoader: AssetLoader) {
    /**
     * psov2's area geometry isn't in phantasmal's usual "fmt2" wrapper -- each section's static
     * models turn out to be plain Ninja bone/chunk object graphs instead, see
     * Psov2AreaGeometry.kt. psov2's own room loader also reads two render files per map (n =
     * decorative props, d = the actual terrain) into the same per-section data, so both need
     * parsing here too. [slug] matches the psov2 conversion's output naming, e.g. "forest01",
     * "cave01", "mines01", "ruins01".
     */
    suspend fun loadArea(slug: String): GameMap {
        val roots = parseNinjaRoomStaticModels(
            assetLoader.loadArrayBuffer("/areas/map_${slug}n.rel").cursor(Endianness.Little)
        ) + parseNinjaRoomStaticModels(
            assetLoader.loadArrayBuffer("/areas/map_${slug}d.rel").cursor(Endianness.Little)
        )
        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/areas/map_$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val builder = MeshBuilder(xvm.textures)
        for (root in roots) {
            ninjaObjectToMeshBuilder(root, builder)
        }
        val renderObject = builder.buildMesh()

        // psov2's collision .rel doesn't match phantasmal's collision format (unlike render
        // geometry, psov2's own NinjaRoom.js never parses collision at all, so there's no
        // reference implementation to port from here). Sidesteps the whole problem instead:
        // build synthetic collision data directly from the render mesh's own already-computed,
        // world-space triangle soup (real per-triangle surface-type flags don't exist here
        // either way, so every triangle is collision-eligible and isWalkable/isWall fall back
        // entirely on the geometric slope test).
        val collisionGeometry = buildCollisionGeometry(builder)

        return GameMap(
            renderObject = renderObject,
            collisionGeometry = collisionGeometry,
            walkableCollisionObject = collisionGeometryToGroup(collisionGeometry, ::isWalkable),
        )
    }

    /**
     * Loads a static hub stage like Pioneer 2 (see STAGE_SPECS in :web:assets-generation's
     * StageSpecs.kt). Structurally different from [loadArea]: each *section's own* position/
     * rotation.y has to be applied on top of its models rather than being pre-baked in (see
     * Psov2StageGeometry.kt), so this builds one mesh per section instead of one mesh for the
     * whole map, positioning/rotating each individually, and applies the same transform to that
     * section's collision data by hand (there's no scene-graph equivalent for the raw
     * triangle/vertex collision representation the way there is for the render mesh).
     */
    suspend fun loadStage(slug: String): GameMap {
        val sections = parseNinjaStageSections(
            assetLoader.loadArrayBuffer("/stages/map_${slug}n.rel").cursor(Endianness.Little)
        ) + parseNinjaStageSections(
            assetLoader.loadArrayBuffer("/stages/map_${slug}d.rel").cursor(Endianness.Little)
        )
        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/stages/map_$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val renderObject = Group()
        val collisionMeshes = mutableListOf<CollisionMesh>()

        for (section in sections) {
            if (section.roots.isEmpty()) continue

            val builder = MeshBuilder(xvm.textures)
            for (root in section.roots) {
                ninjaObjectToMeshBuilder(root, builder)
            }

            val sectionMesh = builder.buildMesh()
            sectionMesh.position.set(
                section.position.x.toDouble(),
                section.position.y.toDouble(),
                section.position.z.toDouble(),
            )
            sectionMesh.rotation.y = section.rotationY
            renderObject.add(sectionMesh)

            collisionMeshes.add(buildCollisionMesh(builder, sectionTransform(section)))
        }

        var collisionGeometry = CollisionGeometry(collisionMeshes)

        // Pioneer 2 has a pair of genuine, intentional 100+-unit-tall walls mirrored on its east
        // and west sides (x ~90 and x ~-90 -- confirmed solid and unbroken end to end, not a data
        // gap) separating the main plaza from rooms beyond them. There's presumably a real doorway
        // into each room somewhere else along the building's perimeter, but neither was found by
        // exploring, and both rooms are otherwise fully reachable (confirmed via noclip flight,
        // same floor height, same wall styling) -- so by request, both wall planes are stripped
        // out entirely rather than continuing to search for their real entrances.
        if (slug == "pioneer2") {
            collisionGeometry = collisionGeometry.withoutWallAtX(EAST_WALL_X).withoutWallAtX(WEST_WALL_X)
        }

        return GameMap(
            renderObject = renderObject,
            collisionGeometry = collisionGeometry,
            walkableCollisionObject = collisionGeometryToGroup(collisionGeometry, ::isWalkable),
        )
    }

    companion object {
        private val UP = Vector3(.0, 1.0, .0)
        private val COS_75_DEG = cos(PI / 180 * 75)
        private val tmpVec = Vector3()

        private fun buildCollisionGeometry(builder: MeshBuilder): CollisionGeometry =
            CollisionGeometry(listOf(buildCollisionMesh(builder)))

        /**
         * [transform] bakes a section's position/rotation.y into the collision data the same way
         * setting it on the render mesh's own Object3D.position/rotation does visually -- see
         * [loadStage]. Room maps (see [loadArea]) don't need this (identity by default) since
         * their models are already in final world-space coordinates.
         */
        private fun buildCollisionMesh(
            builder: MeshBuilder,
            transform: (Vec3) -> Vec3 = { it },
        ): CollisionMesh {
            val vertices = (0 until builder.vertexCount).map { i ->
                val p = builder.getPosition(i)
                transform(Vec3(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))
            }

            val indices = builder.allIndices()
            val triangles = ArrayList<CollisionTriangle>(indices.size / 3)

            var i = 0
            while (i + 2 < indices.size) {
                val i1 = indices[i]
                val i2 = indices[i + 1]
                val i3 = indices[i + 2]
                i += 3

                val a = vertices[i1]
                val b = vertices[i2]
                val c = vertices[i3]

                val abx = b.x - a.x
                val aby = b.y - a.y
                val abz = b.z - a.z
                val acx = c.x - a.x
                val acy = c.y - a.y
                val acz = c.z - a.z

                // ac x ab, not the more conventional ab x ac -- MeshBuilder's triangle winding
                // puts the "outward" (upward, for a floor) normal on this side; verified against
                // isWalkable actually accepting floor triangles (empty otherwise).
                var nx = acy * abz - acz * aby
                var ny = acz * abx - acx * abz
                var nz = acx * aby - acy * abx
                val len = sqrt(nx * nx + ny * ny + nz * nz)

                if (len > 0f) {
                    nx /= len
                    ny /= len
                    nz /= len
                }

                // Bit 0 always set: there's no real per-triangle surface-type data to check here,
                // so isWalkable's flag check always passes and the slope test does all the work.
                triangles.add(CollisionTriangle(i1, i2, i3, flags = 1, normal = Vec3(nx, ny, nz)))
            }

            return CollisionMesh(vertices, triangles)
        }

        /**
         * Pure Y-axis rotation + translation, matching what setting Object3D.position/rotation.y
         * does to the render mesh -- derived directly from three.js's own Matrix4.makeRotationY so
         * the two stay consistent (render and collision geometry lining up is the whole point).
         */
        private fun sectionTransform(section: StageSection): (Vec3) -> Vec3 {
            val cosY = cos(section.rotationY)
            val sinY = sin(section.rotationY)
            val pos = section.position

            return { v ->
                Vec3(
                    (v.x * cosY + v.z * sinY).toFloat() + pos.x,
                    v.y + pos.y,
                    (-v.x * sinY + v.z * cosY).toFloat() + pos.z,
                )
            }
        }

        /**
         * Same ground-triangle heuristic used by the Quest Editor's AreaAssetLoader, plus one
         * addition for Stage-format hubs: [abs] on the slope test, not a plain signed comparison.
         * Verified via a direct comparison against Three.js's own raycast-computed face normal
         * (which only depends on final vertex winding, independent of any per-triangle normal we
         * store) that Pioneer 2's sections don't share one consistent winding -- most sections'
         * triangles need [buildCollisionMesh]'s `ac x ab` order to come out pointing up, but at
         * least one section (its ground floor near world origin) needs the opposite, `ab x ac`,
         * to point up instead. No single fixed cross-product order can get every section right, so
         * the slope test has to accept a near-flat triangle regardless of which way it happens to
         * be wound. Safe to do broadly (not just for Stage maps): every ground-collision candidate
         * this codebase generates is a thin single-sided surface, not a solid slab with a genuine
         * separate ceiling-facing-down triangle, so there's no real ceiling this would wrongly
         * start accepting -- see buildCollisionMesh's own normal-computation comment for the winding
         * background this fixes.
         */
        private fun isWalkable(triangle: CollisionTriangle): Boolean =
            if (triangle.flags.isBitSet(0) || triangle.flags.isBitSet(4) ||
                triangle.flags.isBitSet(6)
            ) {
                tmpVec.setFromVec3(triangle.normal)
                abs(tmpVec dot UP) >= COS_75_DEG
            } else {
                false
            }

        /** See [loadStage]'s boundary-wall comment -- Pioneer 2's plaza-side walls, x ~90/~-90. */
        private const val EAST_WALL_X = 90.0
        private const val WEST_WALL_X = -90.0
        private const val WALL_X_TOLERANCE = 1.0

        /**
         * Drops every collision triangle whose vertices all sit within [WALL_X_TOLERANCE] of [x]
         * -- each of these walls is a flat vertical plane at a constant world X (confirmed via
         * direct inspection: every vertex on one reads ~90.0, ~-90.0 on the other), so a tight
         * per-vertex X tolerance removes exactly that one plane's triangles without touching any
         * other wall or floor geometry elsewhere on the map, including other sections that merely
         * pass near the same X in an unrelated part of the stage.
         */
        private fun CollisionGeometry.withoutWallAtX(x: Double): CollisionGeometry =
            CollisionGeometry(
                meshes.map { mesh ->
                    CollisionMesh(
                        mesh.vertices,
                        mesh.triangles.filterNot { triangle ->
                            val a = mesh.vertices[triangle.index1]
                            val b = mesh.vertices[triangle.index2]
                            val c = mesh.vertices[triangle.index3]
                            abs(a.x - x) < WALL_X_TOLERANCE &&
                                abs(b.x - x) < WALL_X_TOLERANCE &&
                                abs(c.x - x) < WALL_X_TOLERANCE
                        },
                    )
                }
            )
    }
}
