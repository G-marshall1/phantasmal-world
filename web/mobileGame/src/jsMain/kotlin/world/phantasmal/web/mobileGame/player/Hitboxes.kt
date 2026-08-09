package world.phantasmal.web.mobileGame.player

/**
 * PSO models every fighter as an upright cylinder, and checks hits by projecting the attacker's
 * strike zone forward and testing it against that cylinder -- no mesh involved. Every range in
 * this project is stated in the same "PSO units" that system uses, where the player's own cylinder
 * has a radius of 1.0 and every one of the twelve classes shares it (a HUcast and a FOnewearl have
 * identical footprints, however different they look).
 *
 * Those units aren't this project's world coordinates, which come from the map files and run to
 * hundreds across a room. [psoUnit] converts, anchored on the one thing both systems agree on: the
 * player's cylinder is 1.0 units wide in PSO and [CharacterController.HITBOX_RADIUS_FACTOR] of the
 * character's bounding sphere here. Everything else scales off that, so the whole table stays in
 * the game's own numbers and only this one line knows about the difference.
 */
fun psoUnit(playerBoundingSphereRadius: Double): Double =
    playerBoundingSphereRadius * CharacterController.HITBOX_RADIUS_FACTOR
