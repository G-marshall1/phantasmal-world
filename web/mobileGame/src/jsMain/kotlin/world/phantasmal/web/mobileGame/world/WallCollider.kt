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
 *
 * [stepHeight] is how far above the character's feet a wall's top can be and still be walked up
 * onto rather than blocking. Applied per-frame against the character's own height, not as a
 * construction-time filter --
 * e.g. Pioneer 2's raised walkway borders (a curb/lip only a fraction of a unit tall) register as
 * a "wall" exactly like a real building wall does; geometrically nothing distinguishes them except
 * how tall the vertical face actually is. Without this, a character can walk right up to a curb
 * like that and then simply can't cross it -- confirmed getting physically stuck at one, unable to
 * reach a walkable floor beyond it despite that floor being perfectly solid once reached another
 * way (noclip flight, see FlyToggleButton). Pass the same step-height scale used for ground
 * snapping (see CharacterController.maxStepHeight) so "short enough to step onto" and "short
 * enough to not count as a wall" stay the same threshold, rather than picking a second, unrelated
 * number that could disagree with it.
 */
/**
 * A doorway carved through the authored collision: walls whose centre falls inside the circle
 * simply don't block. Pioneer 2's shop and guild doorways are sealed in the collision data --
 * the real game *warps* you at those doors instead of letting you walk through -- so the walk-in
 * version opens them here.
 */
class PassageZone(val x: Double, val z: Double, val radius: Double)

class WallCollider(
    collisionGeometry: CollisionGeometry,
    private val stepHeight: Double = 0.0,
    /**
     * True when the geometry's per-triangle flags are the game's own authored data (see
     * GameMap.hasAuthoredCollision). A pushing wall is then a triangle that is (a) not flagged
     * walkable, (b) actually steep -- a *flat* non-walkable triangle (the "lid" hovering over a
     * shrub cluster) only means "you can't stand here", and letting it push sideways made shrubs
     * into solid obstacles you couldn't walk through -- and (c) not a room-containment plane
     * unless [blockContainmentPlanes] asks for them. False keeps the pure slope heuristic for
     * synthesized collision, whose flags are all a meaningless bit 0.
     */
    authoredFlags: Boolean = false,
    /**
     * Whether the room-containment rings block this collider's owner -- see
     * [isPsov2ContainmentPlane]. True for enemies (they stay in their room, like the real game);
     * false for the player, who walks freely between rooms. Only meaningful with [authoredFlags].
     */
    blockContainmentPlanes: Boolean = false,
    passageZones: List<PassageZone> = emptyList(),
) {
    private val walls: List<Triangle> = buildList {
        for (mesh in collisionGeometry.meshes) {
            for (triangle in mesh.triangles) {
                val blocking =
                    if (authoredFlags) {
                        !isPsov2Walkable(triangle) &&
                                isWall(triangle.normal.y.toDouble()) &&
                                (blockContainmentPlanes || !isPsov2ContainmentPlane(triangle))
                    } else {
                        isWall(triangle.normal.y.toDouble())
                    }

                if (blocking) {
                    val a = mesh.vertices[triangle.index1]
                    val b = mesh.vertices[triangle.index2]
                    val c = mesh.vertices[triangle.index3]

                    val centroidX = (a.x + b.x + c.x) / 3.0
                    val centroidZ = (a.z + b.z + c.z) / 3.0
                    val inPassage = passageZones.any { zone ->
                        val dx = centroidX - zone.x
                        val dz = centroidZ - zone.z
                        dx * dx + dz * dz <= zone.radius * zone.radius
                    }
                    if (inPassage) continue

                    add(
                        Triangle(
                            a.x.toDouble(), a.y.toDouble(), a.z.toDouble(),
                            b.x.toDouble(), b.y.toDouble(), b.z.toDouble(),
                            c.x.toDouble(), c.y.toDouble(), c.z.toDouble(),
                            topY = maxOf(a.y, b.y, c.y).toDouble(),
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
     *
     * A wall low enough to step onto is skipped -- but that's judged from how far its top sits
     * above the character's feet, not from how tall the individual triangle is. Filtering by
     * triangle height at construction (as this used to) threw away most of the map: terrain is
     * tessellated finely, so a cliff is built from a stack of short triangles, each of which
     * looks steppable on its own. Forest 1 has 3980 near-vertical triangles and that filter kept
     * 1464 of them, leaving nearly two thirds of the walls with no collision at all.
     */
    fun resolve(position: Vector3, radius: Double, verticalTolerance: Double) {
        repeat(ITERATIONS) {
            for (wall in walls) {
                // Low enough to walk up onto rather than be stopped by.
                if (wall.topY - position.y <= stepHeight) continue

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
        /** Highest point of the triangle, for the step-over test in [resolve]. */
        val topY: Double,
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
        /**
         * Anything too steep to stand on is a wall. Deliberately the exact complement of
         * MapAssetLoader's own walkable test, so every triangle is one or the other -- when these
         * two disagreed (wall past 74.9 degrees, floor up to 75) almost every slope in the map fell
         * in the gap and was climbable, which is how the player walked up cliffs and off the map.
         */
        private fun isWall(normalY: Double): Boolean =
            abs(normalY) < MapAssetLoader.COS_MAX_WALKABLE_SLOPE

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
