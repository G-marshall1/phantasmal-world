package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.mobileGame.world.enemyStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The spacing rules, in PSO units. These are the tactical facts the range table describes -- which
 * weapons out-reach which enemy, and which lose the exchange -- pinned so a tweak to any single
 * number can't quietly invert a matchup.
 *
 * Reach is measured to the edge of the target's cylinder, so the distance between centres a weapon
 * can cover is its own reach plus the target's hitbox radius.
 */
class HitboxTests {
    private fun centreReach(weapon: WeaponType, enemySlug: String): Double =
        weapon.reach + enemyStats(enemySlug).hitboxRadius

    /** An enemy strikes from its own range plus the player's 1.0-unit cylinder. */
    private fun enemyCentreReach(enemySlug: String): Double =
        enemyStats(enemySlug).attackRange + PLAYER_HITBOX_UNITS

    private companion object {
        const val PLAYER_HITBOX_UNITS = 1.0
    }

    /**
     * The older tactical notes gave the Saber a 1.5 range and a slim 0.1-unit edge over a Booma;
     * the published weapon table (horizontal distance 14, i.e. 1.4 units) puts them at an exact
     * dead heat instead -- the duel is decided by hitboxes and timing, not raw range. The Booma's
     * 1.4 strike range remains this project's own figure, so the tie is asserted as "no
     * meaningful edge either way" rather than an exact equality that would break the moment that
     * estimate is refined.
     */
    @Test
    fun aSaberAndABoomaAreAtRangeParity() {
        val edge = WeaponType.SABER.reach - enemyStats("Booma").attackRange
        assertTrue(edge >= -0.15, "a Booma should not meaningfully out-range a Saber, edge $edge")
        assertTrue(edge <= 0.15, "a Saber should not meaningfully out-range a Booma, edge $edge")
    }

    /**
     * "it actively out-ranges a standard player Saber (1.5 range)."
     *
     * True of the raw ranges -- 1.6 against 1.5 -- but note it does *not* survive adding each
     * side's hitbox, because a Gigobooma's own 1.4-unit body is what a Saber swings at: 1.5 + 1.4
     * reaches further than 1.6 + 1.0. The two rules in the supplied data disagree here, and the
     * engine follows the stated Reach Calculation (see [centreReach]); this test records the raw
     * relationship the tactical note is about.
     */
    @Test
    fun aGigoboomaOutRangesASaber() {
        assertTrue(enemyStats("GigaBooma").attackRange > WeaponType.SABER.reach)
    }

    /** The heaviest of the Booma family reaches furthest. */
    @Test
    fun theBoomaFamilyReachesFurtherAsItGetsBigger() {
        assertTrue(enemyStats("GigaBooma").attackRange > enemyStats("Booma").attackRange)
        assertTrue(enemyStats("GigaBooma").hitboxRadius > enemyStats("Booma").hitboxRadius)
    }

    /** "using a Sword or Partisan lets you hit them from a completely safe distance." */
    @Test
    fun longWeaponsStayOutOfABoomasReach() {
        for (weapon in listOf(WeaponType.SWORD, WeaponType.PARTISAN)) {
            assertTrue(
                weapon.reach > enemyStats("Booma").attackRange + 0.5,
                "$weapon should out-space a Booma by a clear margin",
            )
        }
    }

    /** "Standard Sabers, Daggers, and Claws cannot out-space this." */
    @Test
    fun aWolfsLeapBeatsEveryShortWeapon() {
        val leap = enemyStats("SavageWolf").attackRange

        for (weapon in listOf(WeaponType.SABER, WeaponType.DAGGER, WeaponType.CLAW)) {
            assertTrue(weapon.reach < leap, "$weapon shouldn't out-space a wolf's leap")
        }
        assertTrue(WeaponType.PARTISAN.reach > leap, "a Partisan should intercept a wolf mid-leap")
    }

    /** "Even short-range Daggers can strike it easily because its boundary extends so far out." */
    @Test
    fun aMonestIsHittableEvenWithADagger() {
        assertTrue(centreReach(WeaponType.DAGGER, "Monest") >= 4.0)
    }

    /** A hive doesn't strike back. */
    @Test
    fun aMonestNeverAttacks() {
        assertEquals(0.0, enemyStats("Monest").attackRange)
    }

    /** "The smallest target in the Forest" -- narrow enough that tight weapons slip past. */
    @Test
    fun aMothmantIsTheNarrowestTarget() {
        val mothmant = enemyStats("Mothmant").hitboxRadius

        for (slug in listOf("Rappy", "Booma", "GoBooma", "GigaBooma", "SavageWolf", "Monest")) {
            assertTrue(mothmant < enemyStats(slug).hitboxRadius, "$slug should be wider")
        }
    }

    /** Wide sweeping weapons clear groups; narrow ones commit to one target. */
    @Test
    fun onlyWideWeaponsHitSeveralTargets() {
        assertEquals(1, WeaponType.SABER.maxTargets)
        assertEquals(1, WeaponType.DAGGER.maxTargets)
        assertTrue(WeaponType.PARTISAN.maxTargets > 1)
        assertTrue(WeaponType.SWORD.maxTargets > 1)
        assertTrue(WeaponType.PARTISAN.angleDegrees > WeaponType.DAGGER.angleDegrees)
    }

    @Test
    fun gunsOutRangeEveryMeleeWeapon() {
        val melee = listOf(WeaponType.SABER, WeaponType.SWORD, WeaponType.PARTISAN).maxOf { it.reach }

        for (gun in listOf(WeaponType.HANDGUN, WeaponType.RIFLE, WeaponType.MECHGUN, WeaponType.SHOT)) {
            assertTrue(gun.reach > melee, "$gun should out-range every melee weapon")
        }
        assertTrue(WeaponType.RIFLE.reach > WeaponType.HANDGUN.reach, "a rifle should out-range a handgun")
    }

    /**
     * The conversion from PSO units to this project's world coordinates is a single anchor: the
     * player's cylinder. If that ever stops matching the movement code's own radius, every range
     * in the game silently shifts.
     */
    @Test
    fun psoUnitsAnchorOnThePlayersOwnHitbox() {
        val bSphere = 10.0
        assertEquals(bSphere * CharacterController.HITBOX_RADIUS_FACTOR, psoUnit(bSphere))
    }
}
