package world.phantasmal.web.mobileGame.player

/**
 * Indices into the player's 572 unnamed `animation_NNN.njm` clips. psov2's own asset browser
 * (https://dashgl.gitlab.io/psov2/, Player > a class > the animation dropdown) doesn't have real
 * names for these either -- it's the same plain "plymotiondata_NNN" numbering -- so these were
 * identified visually, by index, the same way [world.phantasmal.web.mobileGame.debug.AnimationDebugOverlay]
 * does it locally. PSO has a separate idle/walk/etc set per weapon type (saber, gun, polearm,
 * ...), so these specific numbers only apply to the saber/melee set the current weapon system
 * (see Weapon.kt) always equips. Revisit once other weapon types actually change what's held.
 */
object PlayerAnimations {
    const val IDLE = 6
    const val WALK = 104
    const val DEAD = 86

    /** Saber combo: downward slash, lunging thrust, upward slash -- cycled in order per swing. */
    val ATTACKS = listOf(99, 100, 101)
}
