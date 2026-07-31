package world.phantasmal.web.mobileGame.player

/**
 * Indices into the player's 572 unnamed `animation_NNN.njm` clips, identified visually via
 * [world.phantasmal.web.mobileGame.debug.AnimationDebugOverlay]. PSO has a separate idle/walk/etc
 * set per weapon type (saber, gun, polearm, ...), so these specific numbers only apply to the
 * currently-equipped state -- there's no weapon system yet, so this is effectively the unarmed/
 * default set. Revisit once more weapon types are added.
 */
object PlayerAnimations {
    const val IDLE = 6
    const val WALK = 104

    /** Saber combo: downward slash, lunging thrust, upward slash -- cycled in order per swing. */
    val ATTACKS = listOf(99, 100, 101)
}
