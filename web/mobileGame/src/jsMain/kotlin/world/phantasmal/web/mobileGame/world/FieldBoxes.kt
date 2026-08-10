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
    /**
     * Radius the player's swing has to reach, in world units -- the crate's widest half-extent,
     * not its half-diagonal. The diagonal covered the corners but made every crate read fat:
     * you'd bump into air and break boxes the blade visibly missed.
     */
    val radius: Double,
    /** The crate's own half-extents and yaw, for the exact walk-into-it rectangle test. */
    val halfX: Double = radius,
    val halfZ: Double = radius,
    val yaw: Double = 0.0,
    /** How tall the crate stands, world units -- where its reticle floats. */
    val height: Double = 0.0,
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
