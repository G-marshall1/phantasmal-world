package world.phantasmal.web.mobileGame.world

import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.player.Enemy

/**
 * One targetable region of a boss, anchored to a bone of its skeleton so the region rides the
 * animation instead of floating at a fixed spot.
 *
 * [armorHp] is the plating a region carries in front of the boss's own health, as De Rol Le's
 * shell segments do (wiki.pioneer2.net/w/De_Rol_Le: "Each non-head body segment ... is also
 * protected by a layer of armor with the listed HP amount that absorbs damage for that segment
 * until it breaks. After an armored part is broken, further damage to that body part is
 * conferred towards the primary health pool"). Zero means the region is bare and everything
 * that lands on it goes straight to the boss.
 *
 * [offsetX] shifts a region sideways off its bone, which is how one skull carries the three
 * separate hurtboxes the wiki credits it with on Normal through Very Hard -- the reason
 * multi-target weapons are so effective on De Rol Le's head.
 */
class BossPart(
    val name: String,
    val boneIndex: Int,
    val radiusUnits: Double,
    val armorHp: Int = 0,
    val offsetX: Double = 0.0,
)

/**
 * A boss built out of separately targetable regions. The renderer aims the reticle, flies its
 * rounds and spends an area technique's hits through this, so every fight that implements it
 * gets per-region targeting for free (see GameRenderer's multiPartBossOf/areaHitPoints).
 */
interface PartedBoss {
    val enemy: Enemy
    val parts: List<BossPart>

    /** Writes [parts]`[index]`'s current world position. */
    fun partPosition(index: Int, out: Vector3)

    /**
     * Routes [damage] landing on [partIndex] and returns how much of it reaches the boss's own
     * health pool. Plated regions swallow it until their armor gives way; bare ones pass it
     * through untouched.
     */
    fun damageThroughPart(partIndex: Int, damage: Int): Int = damage

    /** Armor still standing on [partIndex], for the caller's own feedback. Zero when bare. */
    fun armorRemaining(partIndex: Int): Int = 0
}
