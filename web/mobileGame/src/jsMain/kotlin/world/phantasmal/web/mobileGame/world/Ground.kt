package world.phantasmal.web.mobileGame.world

import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.Raycaster
import world.phantasmal.web.externals.three.Vector3

private val raycaster = Raycaster()
private val origin = Vector3()
private val down = Vector3(.0, -1.0, .0)

/**
 * Casts a ray straight down from high above [x],[z] onto [walkable] and returns the height of a
 * hit, or null if [x],[z] isn't above any ground. Picks the *topmost* hit by default -- correct
 * for continuous ground snapping, where a character standing on an upper walkway should stay on
 * it, not fall through to whatever's underneath. Pass [lowest] = true instead for one-off spawn
 * placement on maps with overlapping multi-story walkable surfaces (elevated decks/walkways
 * directly above a real floor) -- see [findNearestGroundHeight]'s doc comment.
 */
fun findGroundHeight(walkable: Object3D, x: Double, z: Double, lowest: Boolean = false): Double? {
    origin.set(x, 10_000.0, z)
    raycaster.set(origin, down)

    val hits = raycaster.intersectObject(walkable, recursive = true)
    return (if (lowest) hits.maxByOrNull { it.distance } else hits.minByOrNull { it.distance })
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
 *
 * [lowest] should be true when spawning on a Stage-format hub (Pioneer 2 and similar): those maps
 * can have an elevated walkway/deck directly overlapping the real ground floor at the exact same
 * (x,z) -- verified by grid-scanning Pioneer 2's actual collision geometry, where a candidate
 * spawn point sat under a walkway covering nearly the whole map, with the real floor only a couple
 * of units away but *underneath* it, not beside it, so no amount of ring-searching sideways (with
 * the default topmost-hit behavior) would ever find it. Field/dungeon Room-format maps haven't
 * shown this problem, so they keep the default (false).
 */
fun findNearestGroundHeight(
    walkable: Object3D,
    x: Double,
    z: Double,
    maxRadius: Double = 20.0,
    step: Double = 2.0,
    lowest: Boolean = false,
): Triple<Double, Double, Double>? {
    findGroundHeight(walkable, x, z, lowest)?.let { return Triple(x, it, z) }

    var radius = step
    while (radius <= maxRadius) {
        val steps = (2 * kotlin.math.PI * radius / step).toInt().coerceAtLeast(8)

        for (i in 0 until steps) {
            val angle = 2 * kotlin.math.PI * i / steps
            val cx = x + radius * kotlin.math.cos(angle)
            val cz = z + radius * kotlin.math.sin(angle)

            findGroundHeight(walkable, cx, cz, lowest)?.let { return Triple(cx, it, cz) }
        }

        radius += step
    }

    return null
}

/**
 * Like [findGroundHeight] with `lowest = true`, but instead of trusting one exact raycast, samples
 * a small local grid around [x],[z] and returns the height shared by the largest cluster of nearby
 * samples (points within [tolerance] of each other), not just whatever a single point happened to
 * hit. Thin decorative details -- counter trim, railings, signage -- are usually only a unit or two
 * wide, so a single-point raycast can land squarely on one and report it as solid ground even
 * though nothing beside it is actually standable; requiring several nearby points to agree rejects
 * those in favor of whatever surface is broad enough to be real floor. Verified necessary on
 * Pioneer 2, where a single-point search kept finding a counter's decorative edge molding instead
 * of the open plaza right next to it.
 */
fun findStableGroundHeight(
    walkable: Object3D,
    x: Double,
    z: Double,
    sampleRadius: Double = 2.0,
    sampleStep: Double = 1.0,
    tolerance: Double = 1.0,
): Double? {
    val samples = mutableListOf<Double>()
    var dx = -sampleRadius
    while (dx <= sampleRadius) {
        var dz = -sampleRadius
        while (dz <= sampleRadius) {
            findGroundHeight(walkable, x + dx, z + dz, lowest = true)?.let { samples.add(it) }
            dz += sampleStep
        }
        dx += sampleStep
    }

    if (samples.isEmpty()) return null

    val used = BooleanArray(samples.size)
    var bestGroup: MutableList<Double>? = null

    for (i in samples.indices) {
        if (used[i]) continue

        val group = mutableListOf(samples[i])
        used[i] = true

        for (j in i + 1 until samples.size) {
            if (!used[j] && kotlin.math.abs(samples[j] - samples[i]) <= tolerance) {
                group.add(samples[j])
                used[j] = true
            }
        }

        if (bestGroup == null || group.size > bestGroup!!.size) {
            bestGroup = group
        }
    }

    return bestGroup?.average()
}

/**
 * Like [findNearestGroundHeight] with `lowest = true`, but uses [findStableGroundHeight] at each
 * candidate instead of a single raycast -- see its doc comment for why. Meant specifically for
 * Stage-format hub spawn placement (Pioneer 2 and similar).
 */
fun findNearestStableGroundHeight(
    walkable: Object3D,
    x: Double,
    z: Double,
    maxRadius: Double = 20.0,
    step: Double = 2.0,
): Triple<Double, Double, Double>? {
    findStableGroundHeight(walkable, x, z)?.let { return Triple(x, it, z) }

    var radius = step
    while (radius <= maxRadius) {
        val steps = (2 * kotlin.math.PI * radius / step).toInt().coerceAtLeast(8)

        for (i in 0 until steps) {
            val angle = 2 * kotlin.math.PI * i / steps
            val cx = x + radius * kotlin.math.cos(angle)
            val cz = z + radius * kotlin.math.sin(angle)

            findStableGroundHeight(walkable, cx, cz)?.let { return Triple(cx, it, cz) }
        }

        radius += step
    }

    return null
}
