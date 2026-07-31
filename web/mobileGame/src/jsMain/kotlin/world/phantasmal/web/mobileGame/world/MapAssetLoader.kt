package world.phantasmal.web.mobileGame.world

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
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
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.Vector3

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

    companion object {
        private val UP = Vector3(.0, 1.0, .0)
        private val COS_75_DEG = cos(PI / 180 * 75)
        private val tmpVec = Vector3()

        private fun buildCollisionGeometry(builder: MeshBuilder): CollisionGeometry {
            val vertices = (0 until builder.vertexCount).map { i ->
                val p = builder.getPosition(i)
                Vec3(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
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

            return CollisionGeometry(listOf(CollisionMesh(vertices, triangles)))
        }

        /** Same ground-triangle heuristic used by the Quest Editor's AreaAssetLoader. */
        private fun isWalkable(triangle: CollisionTriangle): Boolean =
            if (triangle.flags.isBitSet(0) || triangle.flags.isBitSet(4) ||
                triangle.flags.isBitSet(6)
            ) {
                tmpVec.setFromVec3(triangle.normal)
                tmpVec dot UP >= COS_75_DEG
            } else {
                false
            }
    }
}
