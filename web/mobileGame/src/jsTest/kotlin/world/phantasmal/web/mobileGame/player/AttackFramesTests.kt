package world.phantasmal.web.mobileGame.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The combo timing model, checked against the worked examples in the frame data:
 *
 * ```
 * Handgun, N:   N1 (Full)                          = 27      = 27f
 * Handgun, NNN: N1 (Combo) + N2 (Combo) + N3 (Full) = 14+11+19 = 44f
 * Handgun, NH:  N1 (Combo) + H2 (Full)              = 14+30    = 44f
 * ```
 *
 * An attack runs its combo length when another follows and its full length when it's the last, so
 * a chained combo is dramatically faster than the same attacks thrown separately.
 */
class AttackFramesTests {
    private fun sequence(vararg steps: Pair<AttackType, Boolean>): Int =
        steps.withIndex().sumOf { (i, step) ->
            val (type, chaining) = step
            val f = attackFrames(WeaponType.HANDGUN, type, i)
            if (chaining) f.combo else f.full
        }

    @Test
    fun aSingleHandgunShotTakesItsFullLength() {
        assertEquals(27, sequence(AttackType.NORMAL to false))
    }

    @Test
    fun aThreeShotHandgunComboTakesFortyFourFrames() {
        assertEquals(
            44,
            sequence(
                AttackType.NORMAL to true,
                AttackType.NORMAL to true,
                AttackType.NORMAL to false,
            ),
        )
    }

    @Test
    fun aHandgunNormalThenHeavyAlsoTakesFortyFourFrames() {
        assertEquals(
            44,
            sequence(AttackType.NORMAL to true, AttackType.HEAVY to false),
        )
    }

    /** Chaining is the whole point: three shots cost less than three separate first shots. */
    @Test
    fun chainingIsFasterThanRepeatingTheOpener() {
        val chained = sequence(
            AttackType.NORMAL to true,
            AttackType.NORMAL to true,
            AttackType.NORMAL to false,
        )
        val separate = 3 * attackFrames(WeaponType.HANDGUN, AttackType.NORMAL, 0).full

        assertTrue(chained < separate, "a combo ($chained f) should beat three openers ($separate f)")
    }

    @Test
    fun everyWeaponClassHasTimingForEveryAttackType() {
        for (weapon in WeaponType.entries) {
            for (type in AttackType.entries) {
                for (step in 0..2) {
                    val f = attackFrames(weapon, type, step)
                    assertTrue(f.full > 0, "$weapon $type step $step has no full length")
                    assertTrue(f.combo > 0, "$weapon $type step $step has no combo length")
                    assertTrue(
                        f.combo <= f.full,
                        "$weapon $type step $step: chaining should never be slower than finishing",
                    )
                }
            }
        }
    }

    /** The third step always ends the sequence, so there's no shortened version of it. */
    @Test
    fun theFinalStepHasNoChainedLength() {
        for (weapon in WeaponType.entries) {
            val third = attackFrames(weapon, AttackType.NORMAL, 2)
            assertEquals(third.full, third.combo, "$weapon step 3 should have one length only")
        }
    }

    /**
     * On the opening two steps a heavy swing is always slower -- that's the cost of its damage.
     * The finisher is the exception: a dagger, double saber or twin sword ends a heavy combo
     * *faster* than a normal one (49 frames against 50 or 51), because the long normal finisher
     * for those weapons is a multi-hit flourish. Real data, not an anomaly.
     */
    @Test
    fun heavyOpenersAreSlowerButSomeHeavyFinishersAreNot() {
        for (weapon in WeaponType.entries) {
            for (step in 0..1) {
                val normal = attackFrames(weapon, AttackType.NORMAL, step)
                val heavy = attackFrames(weapon, AttackType.HEAVY, step)
                assertTrue(
                    heavy.full >= normal.full,
                    "$weapon step $step: a heavy swing should not be quicker than a normal one",
                )
            }
        }

        for (weapon in listOf(WeaponType.DAGGER, WeaponType.DOUBLE_SABER, WeaponType.TWIN_SWORD)) {
            assertTrue(
                attackFrames(weapon, AttackType.HEAVY, 2).full <
                    attackFrames(weapon, AttackType.NORMAL, 2).full,
                "$weapon should finish a heavy combo faster than a normal one",
            )
        }
    }

    /** A mechgun's opener is nearly twice a saber's, which is what the frame data says. */
    @Test
    fun weaponClassesDifferSharplyInSpeed() {
        val saber = attackFrames(WeaponType.SABER, AttackType.NORMAL, 0).full
        val mechgun = attackFrames(WeaponType.MECHGUN, AttackType.NORMAL, 0).full

        assertEquals(29, saber)
        assertEquals(49, mechgun)
    }

    /**
     * Contact ("attack comes out on") frames for the classes the frame data details, and sane
     * bounds for the estimated ones: a blow always connects before the swing ends, and for the
     * chained steps before the cancel point (which is what lets the next swing follow the hit).
     */
    @Test
    fun contactFramesMatchThePublishedDataAndStayInBounds() {
        assertEquals(11, attackFrames(WeaponType.SABER, AttackType.NORMAL, 0).contact)
        assertEquals(8, attackFrames(WeaponType.SABER, AttackType.NORMAL, 2).contact)
        assertEquals(20, attackFrames(WeaponType.SABER, AttackType.HEAVY, 0).contact)
        assertEquals(5, attackFrames(WeaponType.HANDGUN, AttackType.NORMAL, 0).contact)
        assertEquals(14, attackFrames(WeaponType.PARTISAN, AttackType.NORMAL, 0).contact)

        for (weapon in WeaponType.entries) {
            for (type in AttackType.entries) {
                for (step in 0..2) {
                    val f = attackFrames(weapon, type, step)
                    assertTrue(f.contact >= 1, "$weapon $type step $step contact too early")
                    assertTrue(
                        f.contact <= f.combo,
                        "$weapon $type step $step: contact (${f.contact}) after cancel (${f.combo})",
                    )
                }
            }
        }
    }

    @Test
    fun framesConvertToSecondsAtThirtyPerSecond() {
        // A saber's opener is 29 frames full, 13 chained.
        val f = attackFrames(WeaponType.SABER, AttackType.NORMAL, 0)
        assertEquals(29 / 30.0, f.seconds(chaining = false))
        assertEquals(13 / 30.0, f.seconds(chaining = true))
    }
}
