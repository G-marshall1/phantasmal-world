package world.phantasmal.web.mobileGame.world

import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.Object3D

/**
 * One breakable crate standing in the field, from the map's own object placements.
 *
 * The placements were in the map data all along -- Forest 1 carries about fifty per layout --
 * they simply weren't being emitted by the asset generator, so there was nowhere to put boxes.
 * [SpawnObject.BREAKABLE_BOX_TYPES] is the set that stands.
 */
class FieldBox(
    val mesh: Mesh,
    val typeId: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    /** Radius the player's swing has to reach, in world units. */
    val radius: Double,
) {
    var broken = false

    /** True for the crates that hide a monster rather than an item. */
    val hidesEnemy: Boolean get() = typeId in SpawnObject.ENEMY_BOX_TYPES
}

/** A fragment thrown clear of a smashed crate, tumbling and shrinking as it goes. */
class BoxShard(
    val mesh: Object3D,
    val velocityX: Double,
    val velocityY: Double,
    val velocityZ: Double,
    val spin: Double,
    var remaining: Double,
)
