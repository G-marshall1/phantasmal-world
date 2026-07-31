package world.phantasmal.web.mobileGame.world

import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.Raycaster
import world.phantasmal.web.externals.three.Vector3

private val raycaster = Raycaster()
private val origin = Vector3()
private val down = Vector3(.0, -1.0, .0)

/**
 * Casts a ray straight down from high above [x],[z] onto [walkable] and returns the height of the
 * nearest hit, or null if [x],[z] isn't above any ground.
 */
fun findGroundHeight(walkable: Object3D, x: Double, z: Double): Double? {
    origin.set(x, 10_000.0, z)
    raycaster.set(origin, down)

    return raycaster.intersectObject(walkable, recursive = true)
        .minByOrNull { it.distance }
        ?.point?.y
}

/**
 * Like [findGroundHeight], but if [x],[z] itself doesn't land on any walkable surface, searches
 * an expanding ring of nearby offsets and returns the height (and actual x/z) of the closest hit
 * instead of giving up. Real terrain's walkable (near-flat) triangles are a sparse, scattered
 * subset of the full mesh -- most of a natural, sloped/uneven area doesn't qualify -- so an exact
 * spawn coordinate landing off of one of those patches is normal, not a sign anything's broken.
 * Meant for one-off placement (player/enemy spawn), not continuous per-frame ground snapping,
 * where snapping sideways to "the nearest walkable point" would visibly teleport the character.
 */
fun findNearestGroundHeight(
    walkable: Object3D,
    x: Double,
    z: Double,
    maxRadius: Double = 20.0,
    step: Double = 2.0,
): Triple<Double, Double, Double>? {
    findGroundHeight(walkable, x, z)?.let { return Triple(x, it, z) }

    var radius = step
    while (radius <= maxRadius) {
        val steps = (2 * kotlin.math.PI * radius / step).toInt().coerceAtLeast(8)

        for (i in 0 until steps) {
            val angle = 2 * kotlin.math.PI * i / steps
            val cx = x + radius * kotlin.math.cos(angle)
            val cz = z + radius * kotlin.math.sin(angle)

            findGroundHeight(walkable, cx, cz)?.let { return Triple(cx, it, cz) }
        }

        radius += step
    }

    return null
}
