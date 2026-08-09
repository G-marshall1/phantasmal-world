package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.viewer.models.CharacterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PSO's own combat maths, pinned exactly: `[(ATP - DFP) / 5] x 0.9 x attack modifier` for damage,
 * `ATA x type x combo step - EVP/5` for accuracy.
 *
 * These are the numbers a balance change could silently invert, so each one is asserted against
 * the figure the specification states rather than against whatever the code happens to produce.
 */
class CombatFormulaTests {
    private val humar = BASE_STATS_LEVEL_1.getValue(CharacterClass.HUmar)

    @Test
    fun damageAppliesTheNinetyPercentScale() {
        // (100 - 0) / 5 = 20, x0.9 = 18.
        assertEquals(18, physicalDamage(attackerAtp = 100, defenderDfp = 0))
    }

    @Test
    fun damageIsTruncatedNotRounded() {
        // (105 - 0) / 5 = 21, x0.9 = 18.9 -> 18, not 19.
        assertEquals(18, physicalDamage(attackerAtp = 105, defenderDfp = 0))
    }

    @Test
    fun heavyHitsHarderAndSpecialHitsSofterThanNormal() {
        val normal = physicalDamage(1000, 0, AttackType.NORMAL)
        val heavy = physicalDamage(1000, 0, AttackType.HEAVY)
        val special = physicalDamage(1000, 0, AttackType.SPECIAL)

        assertEquals(180, normal)
        assertEquals(340, heavy, "heavy is 1.89x")
        assertEquals(100, special, "a special is 0.56x -- weaker than a normal attack, not stronger")
        assertTrue(special < normal, "a special attack must not out-damage a normal one")
    }

    @Test
    fun theAttackModifiersAreTheStatedOnes() {
        assertEquals(1.0, AttackType.NORMAL.damageModifier)
        assertEquals(1.89, AttackType.HEAVY.damageModifier)
        assertEquals(0.56, AttackType.SPECIAL.damageModifier)
    }

    @Test
    fun accuracyFallsAsDamageRises() {
        assertEquals(1.0, AttackType.NORMAL.accuracyModifier)
        assertEquals(0.7, AttackType.HEAVY.accuracyModifier)
        assertEquals(0.5, AttackType.SPECIAL.accuracyModifier)
    }

    @Test
    fun laterComboStepsAreMoreAccurate() {
        assertEquals(listOf(1.0, 1.3, 1.69), COMBO_STEP_ACCURACY)

        val first = accuracyPercent(50, AttackType.NORMAL, comboStep = 0, targetEvp = 0)
        val second = accuracyPercent(50, AttackType.NORMAL, comboStep = 1, targetEvp = 0)
        val third = accuracyPercent(50, AttackType.NORMAL, comboStep = 2, targetEvp = 0)

        assertEquals(50.0, first)
        assertEquals(65.0, second)
        assertEquals(84.5, third)
    }

    @Test
    fun evasionCostsAFifthOfItsValueInAccuracy() {
        assertEquals(40.0, accuracyPercent(50, AttackType.NORMAL, comboStep = 0, targetEvp = 50))
    }

    @Test
    fun accuracyIsClampedToARealPercentage() {
        assertEquals(100.0, accuracyPercent(200, AttackType.NORMAL, comboStep = 2, targetEvp = 0))
        assertEquals(0.0, accuracyPercent(10, AttackType.SPECIAL, comboStep = 0, targetEvp = 500))
    }

    /** "Characters will be knocked down if a single attack does 25% or more of their max HP." */
    @Test
    fun aQuarterOfMaxHealthInOneBlowKnocksDown() {
        assertTrue(isKnockdown(damage = 10, maxHp = 40))
        assertTrue(isKnockdown(damage = 25, maxHp = 40))
        assertTrue(!isKnockdown(damage = 9, maxHp = 40))
    }

    @Test
    fun criticalRateIsLuckOverFiveForPlayersAndOverTwoForMonsters() {
        assertEquals(0.02, criticalChance(10))
        assertEquals(0.05, criticalChance(10, monster = true))
        assertEquals(0.20, criticalChance(100))
        assertEquals(0.20, criticalChance(255), "luck past 100 should not raise the rate")
        assertEquals(0.50, criticalChance(100, monster = true))
    }

    @Test
    fun eachProfessionRollsItsOwnAtpVariance() {
        assertEquals(1..6, Profession.HUNTER.variance)
        assertEquals(1..4, Profession.RANGER.variance)
        assertEquals(1..3, Profession.FORCE.variance)

        assertEquals(Profession.HUNTER, professionOf(CharacterClass.HUmar))
        assertEquals(Profession.HUNTER, professionOf(CharacterClass.HUcast))
        assertEquals(Profession.RANGER, professionOf(CharacterClass.RAmarl))
        assertEquals(Profession.FORCE, professionOf(CharacterClass.FOnewearl))
    }

    /**
     * The menu shows the *maximum*: a HUmar's 45 is really 39 plus a 1-6 roll, so the lowest
     * possible swing is 40 and the highest is exactly the displayed figure.
     */
    @Test
    fun theDisplayedAtpIsTheCeilingNotTheAverage() {
        val lowest = effectiveAtp(humar, Profession.HUNTER, weaponAtp = 0, random = 0.0)
        val highest = effectiveAtp(humar, Profession.HUNTER, weaponAtp = 0, random = 0.999)

        assertEquals(humar.atp - 6 + 1, lowest)
        assertEquals(humar.atp, highest, "a maximum roll should reach exactly the displayed ATP")
    }

    @Test
    fun theEquippedWeaponAddsItsAttackPowerOnTop() {
        val bare = effectiveAtp(humar, Profession.HUNTER, weaponAtp = 0, random = 0.5)
        val armed = effectiveAtp(humar, Profession.HUNTER, weaponAtp = SABER_ATP, random = 0.5)

        assertEquals(bare + SABER_ATP, armed)
    }
}
