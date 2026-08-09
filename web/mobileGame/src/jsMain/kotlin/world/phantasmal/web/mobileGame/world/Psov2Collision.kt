package world.phantasmal.web.mobileGame.world

import org.khronos.webgl.Float32Array
import world.phantasmal.core.isBitSet
import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.fileFormats.CollisionGeometry
import world.phantasmal.psolib.fileFormats.CollisionMesh
import world.phantasmal.psolib.fileFormats.CollisionTriangle
import world.phantasmal.psolib.fileFormats.vec3Float
import world.phantasmal.web.externals.three.BufferGeometry
import world.phantasmal.web.externals.three.DoubleSide
import world.phantasmal.web.externals.three.Float32BufferAttribute
import world.phantasmal.web.externals.three.Group
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.MeshBasicMaterial
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.webui.obj

/**
 * Parses psov2's map collision files (`map_*c.rel`) -- the real, hand-authored collision the
 * original game plays against, which psolib's own [world.phantasmal.psolib.fileFormats.parseAreaCollisionGeometry]
 * can't read (that parser expects indexed vertex/triangle tables; this format has neither).
 *
 * Layout, worked out from `map_forest01c.rel` directly since psov2's own room code never reads
 * these files:
 *
 * - The standard .rel trailer's data offset (u32 at size-16) points at a u32 holding the block
 *   table's offset.
 * - The block table is 24 bytes per block: `{recordsEndOffset u32, centre f32 x3, radius f32,
 *   flags u32}` -- a bounding sphere per block (Forest 1 has 69 blocks). It ends at the first
 *   entry whose offset is 0 or past the file.
 * - `recordsEndOffset` points at a `{triangleCount u32, 0 u32}` pair sitting immediately *after*
 *   that block's triangle records, which are 68 bytes each: three vertices, the face normal, the
 *   centroid, a bounding radius (centroid and radius verified as exactly the vertex average and
 *   max centroid-to-vertex distance), and a u32 flags word.
 *
 * The flags observed in Forest 1, matching the walkable bits (0/4/6) this repo's Quest Editor
 * already checks for Blue Burst collision, plus two new ones:
 * - `0x101`, `0x110`, `0x111`, `0x140`: floors (face normals near-vertical).
 * - `0x104`: also floor -- bit 2 traces the shallow river bed through the map, walkable in the
 *   real game (the bit presumably switches the footstep/splash effect).
 * - `0x920`: real walls (normals near-horizontal).
 * - `0x40000000`: the invisible map-boundary planes (normals exactly horizontal).
 */
fun parsePsov2Collision(cursor: Cursor): CollisionGeometry {
    cursor.seekEnd(16)
    val dataOffset = cursor.int()
    cursor.seekStart(dataOffset)
    val blockTableOffset = cursor.int()

    val meshes = mutableListOf<CollisionMesh>()
    var blockOffset = blockTableOffset

    // The block table sits directly before the data-offset word; walk it until either boundary.
    while (blockOffset + 24 <= dataOffset) {
        cursor.seekStart(blockOffset)
        val recordsEndOffset = cursor.int()
        blockOffset += 24

        if (recordsEndOffset <= 0 || recordsEndOffset >= cursor.size) break

        cursor.seekStart(recordsEndOffset)
        val triangleCount = cursor.int()
        val recordsStart = recordsEndOffset - triangleCount * TRIANGLE_RECORD_SIZE
        if (triangleCount <= 0 || recordsStart < 0) continue

        cursor.seekStart(recordsStart)

        val vertices = ArrayList<world.phantasmal.psolib.fileFormats.Vec3>(triangleCount * 3)
        val triangles = ArrayList<CollisionTriangle>(triangleCount)

        repeat(triangleCount) {
            val base = vertices.size
            vertices.add(cursor.vec3Float())
            vertices.add(cursor.vec3Float())
            vertices.add(cursor.vec3Float())
            val normal = cursor.vec3Float()
            // Centroid and bounding radius -- derivable from the vertices, so not kept.
            cursor.seek(16)
            val flags = cursor.int()

            triangles.add(CollisionTriangle(base, base + 1, base + 2, flags, normal))
        }

        meshes.add(CollisionMesh(vertices, triangles))
    }

    return CollisionGeometry(meshes)
}

/**
 * Whether this real-collision triangle is ground the player can stand on, judged purely from its
 * authored flags -- unlike the slope heuristic synthesized collision needs (see MapAssetLoader's
 * isWalkable), the original game's own data settles it. Bits 0/4/6 are the Quest Editor's
 * established walkable set; bit 2 is the river bed (see [parsePsov2Collision]).
 */
fun isPsov2Walkable(triangle: CollisionTriangle): Boolean =
    triangle.flags.isBitSet(0) || triangle.flags.isBitSet(2) ||
        triangle.flags.isBitSet(4) || triangle.flags.isBitSet(6)

/**
 * Bit 30 marks the full-height planes that ring each room: Forest 1 has exactly 8 near-identical
 * *pairs* of them in blocks flagged 0xc0000000 (each ring stored twice, once facing each way),
 * and together they partition the entire map -- so the original game cannot be treating them as
 * static player collision, or no room could ever be left. They're the room-containment/wave-gate
 * geometry: enemies are kept inside them (which is why a chased Booma never follows you across
 * the map), and the wave system raises them as the glowing fences that lock a room mid-fight.
 * The player's collider ignores them; the enemies' collider keeps them solid.
 */
fun isPsov2ContainmentPlane(triangle: CollisionTriangle): Boolean =
    triangle.flags.isBitSet(30)

/**
 * The subset of [collisionGeometry] accepted by [predicate], as a bare Object3D for
 * [findGroundHeight]'s raycasts. Unlike the Quest Editor's collisionGeometryToGroup this isn't a
 * visualization: one mesh, one double-sided material (authored collision winding isn't guaranteed
 * consistent, and a front-side material would make down-rays silently miss the triangles wound the
 * other way), and no wireframe copy, which halves what every per-frame ground raycast has to test.
 */
fun collisionRaycastObject(
    collisionGeometry: CollisionGeometry,
    predicate: (CollisionTriangle) -> Boolean,
): Object3D {
    val group = Group()

    for (collisionMesh in collisionGeometry.meshes) {
        val accepted = collisionMesh.triangles.filter(predicate)
        if (accepted.isEmpty()) continue

        val positions = Float32Array(accepted.size * 9)
        var i = 0

        for (triangle in accepted) {
            for (index in intArrayOf(triangle.index1, triangle.index2, triangle.index3)) {
                val v = collisionMesh.vertices[index]
                positions.asDynamic()[i++] = v.x
                positions.asDynamic()[i++] = v.y
                positions.asDynamic()[i++] = v.z
            }
        }

        val geom = BufferGeometry()
        geom.setAttribute("position", Float32BufferAttribute(positions, 3))
        geom.computeBoundingSphere()

        group.add(Mesh(geom, MeshBasicMaterial(obj { side = DoubleSide })))
    }

    return group
}

private const val TRIANGLE_RECORD_SIZE = 68
