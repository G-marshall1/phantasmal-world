package world.phantasmal.web.mobileGame.world

import kotlin.math.abs
import kotlin.math.sqrt
import world.phantasmal.psolib.fileFormats.CollisionGeometry
import world.phantasmal.web.externals.three.Vector3

/**
 * Precomputes the near-vertical ("wall") subset of a map's collision triangles and pushes a
 * position out of them horizontally, so the character can't walk through walls. Brute-forces over
 * every wall triangle each call -- fine at forest01's triangle counts; revisit with a spatial grid
 * (e.g. a uniform grid keyed by triangle centroid) only if profiling shows it's actually slow.
 */
class WallCollider(collisionGeometry: CollisionGeometry) {
    private val walls: List<Triangle> = buildList {
        for (mesh in collisionGeometry.meshes) {
            for (triangle in mesh.triangles) {
                if (isWall(triangle.normal.y.toDouble())) {
                    val a = mesh.vertices[triangle.index1]
                    val b = mesh.vertices[triangle.index2]
                    val c = mesh.vertices[triangle.index3]
                    add(
                        Triangle(
                            a.x.toDouble(), a.y.toDouble(), a.z.toDouble(),
                            b.x.toDouble(), b.y.toDouble(), b.z.toDouble(),
                            c.x.toDouble(), c.y.toDouble(), c.z.toDouble(),
                        )
                    )
                }
            }
        }
    }

    /**
     * Pushes [position] out of any wall triangles it's inside [radius] of, ignoring walls whose
     * closest point is more than [verticalTolerance] above/below [position] (so a wall on a floor
     * above/below doesn't affect movement on this floor). Mutates [position]'s x/z in place.
     */
    fun resolve(position: Vector3, radius: Double, verticalTolerance: Double) {
        repeat(ITERATIONS) {
            for (wall in walls) {
                closestPointOnTriangle(position.x, position.y, position.z, wall, closest)

                val dy = position.y - closest.y
                if (abs(dy) > verticalTolerance) continue

                val dx = position.x - closest.x
                val dz = position.z - closest.z
                val distSq = dx * dx + dz * dz

                if (distSq < radius * radius && distSq > MIN_DIST_SQ) {
                    val dist = sqrt(distSq)
                    val push = radius - dist
                    position.x += dx / dist * push
                    position.z += dz / dist * push
                }
            }
        }
    }

    private class Triangle(
        val ax: Double, val ay: Double, val az: Double,
        val bx: Double, val by: Double, val bz: Double,
        val cx: Double, val cy: Double, val cz: Double,
    )

    /** Reused across calls to avoid allocating a Vector3 per triangle per frame. */
    private class Closest {
        var x = .0
        var y = .0
        var z = .0
    }

    private val closest = Closest()

    companion object {
        private const val ITERATIONS = 3
        private const val MIN_DIST_SQ = 1e-9

        // Roughly "within 15 degrees of perfectly vertical" -- steep enough to be a real wall,
        // rather than a slope, floor, or ceiling.
        private fun isWall(normalY: Double): Boolean = abs(normalY) < 0.26

        /**
         * Closest point on triangle [t] to point ([px], [py], [pz]), written into [out].
         * Standard region-based algorithm (Ericson, "Real-Time Collision Detection", section
         * 5.1.5) operating on raw doubles rather than three.js Vector3s to avoid allocating one
         * per triangle per frame in this hot path.
         */
        private fun closestPointOnTriangle(
            px: Double, py: Double, pz: Double,
            t: Triangle,
            out: Closest,
        ) {
            val abx = t.bx - t.ax; val aby = t.by - t.ay; val abz = t.bz - t.az
            val acx = t.cx - t.ax; val acy = t.cy - t.ay; val acz = t.cz - t.az
            val apx = px - t.ax; val apy = py - t.ay; val apz = pz - t.az

            val d1 = abx * apx + aby * apy + abz * apz
            val d2 = acx * apx + acy * apy + acz * apz

            if (d1 <= 0 && d2 <= 0) {
                out.x = t.ax; out.y = t.ay; out.z = t.az
                return
            }

            val bpx = px - t.bx; val bpy = py - t.by; val bpz = pz - t.bz
            val d3 = abx * bpx + aby * bpy + abz * bpz
            val d4 = acx * bpx + acy * bpy + acz * bpz

            if (d3 >= 0 && d4 <= d3) {
                out.x = t.bx; out.y = t.by; out.z = t.bz
                return
            }

            val vc = d1 * d4 - d3 * d2

            if (vc <= 0 && d1 >= 0 && d3 <= 0) {
                val v = d1 / (d1 - d3)
                out.x = t.ax + v * abx; out.y = t.ay + v * aby; out.z = t.az + v * abz
                return
            }

            val cpx = px - t.cx; val cpy = py - t.cy; val cpz = pz - t.cz
            val d5 = abx * cpx + aby * cpy + abz * cpz
            val d6 = acx * cpx + acy * cpy + acz * cpz

            if (d6 >= 0 && d5 <= d6) {
                out.x = t.cx; out.y = t.cy; out.z = t.cz
                return
            }

            val vb = d5 * d2 - d1 * d6

            if (vb <= 0 && d2 >= 0 && d6 <= 0) {
                val w = d2 / (d2 - d6)
                out.x = t.ax + w * acx; out.y = t.ay + w * acy; out.z = t.az + w * acz
                return
            }

            val va = d3 * d6 - d5 * d4

            if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
                val w = (d4 - d3) / ((d4 - d3) + (d5 - d6))
                out.x = t.bx + w * (t.cx - t.bx)
                out.y = t.by + w * (t.cy - t.by)
                out.z = t.bz + w * (t.cz - t.bz)
                return
            }

            val denom = 1.0 / (va + vb + vc)
            val v = vb * denom
            val w = vc * denom
            out.x = t.ax + abx * v + acx * w
            out.y = t.ay + aby * v + acy * w
            out.z = t.az + abz * v + acz * w
        }
    }
}
