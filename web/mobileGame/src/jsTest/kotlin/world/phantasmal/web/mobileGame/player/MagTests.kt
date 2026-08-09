package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.viewer.models.CharacterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Mag's own stats and how they become the character's. These conversion rates are the reason a
 * raised Mag matters more than levelling in PSO, so they're worth pinning exactly.
 */
class MagTests {
    @Test
    fun aStarterMagIsLevelFiveAndAllDefence() {
        val mag = Mag()

        assertEquals(5, mag.level)
        assertEquals(5, mag.def)
        assertEquals(5, mag.bonusDfp, "the starter Mag is where a character's first 5 DFP comes from")
        assertEquals(0, mag.bonusAtp)
    }

    @Test
    fun theLevelIsTheSumOfTheFourStats() {
        assertEquals(100, Mag(defExp = 500, powExp = 5_000, dexExp = 2_000, mindExp = 2_500).level)
    }

    @Test
    fun eachStatConvertsAtItsOwnRate() {
        val mag = Mag(defExp = 1_000, powExp = 2_000, dexExp = 3_000, mindExp = 4_000)

        assertEquals(10, mag.bonusDfp, "1 DFP per DEF")
        assertEquals(40, mag.bonusAtp, "2 ATP per POW")
        assertEquals(15, mag.bonusAta, "half an ATA per DEX")
        assertEquals(80, mag.bonusMst, "2 MST per MIND")
    }

    /** Half a point per DEX means an odd DEX truncates, as everything else in PSO does. */
    @Test
    fun accuracyFromDexTruncates() {
        assertEquals(3, Mag(dexExp = 700).bonusAta)
    }

    /** A fully raised Mag dwarfs a level 1 statline -- 400 ATP against a HUmar's 45. */
    @Test
    fun aFullyRaisedMagOutweighsTheCharacter() {
        val humar = BASE_STATS_LEVEL_1.getValue(CharacterClass.HUmar)
        val mag = Mag(defExp = 0, powExp = Mag.MAX_STAT * Mag.EXP_PER_LEVEL, dexExp = 0, mindExp = 0)

        assertEquals(400, mag.bonusAtp)
        assertTrue(mag.bonusAtp > humar.atp * 8)
    }

    @Test
    fun dyingCostsFiveSynchro() {
        assertEquals(15, Mag(synchro = 20).afterDeath().synchro)
    }

    @Test
    fun synchroNeverFallsBelowZero() {
        assertEquals(0, Mag(synchro = 3).afterDeath().synchro)
    }

    /** The boost bands are flat steps, not a smooth curve. */
    @Test
    fun synchroBoostsTriggerRatesInBands() {
        assertEquals(0, Mag(synchro = 30).synchroTriggerBoost)
        assertEquals(15, Mag(synchro = 31).synchroTriggerBoost)
        assertEquals(15, Mag(synchro = 60).synchroTriggerBoost)
        assertEquals(25, Mag(synchro = 61).synchroTriggerBoost)
        assertEquals(30, Mag(synchro = 81).synchroTriggerBoost)
        assertEquals(35, Mag(synchro = 120).synchroTriggerBoost)
    }

    @Test
    fun supportTechniqueLevelComesFromIqAndCapsAtSix() {
        assertEquals(1, Mag(iq = 0).supportTechniqueLevel)
        assertEquals(2, Mag(iq = 40).supportTechniqueLevel)
        assertEquals(6, Mag(iq = 200).supportTechniqueLevel)
        assertEquals(6, Mag(iq = 999).supportTechniqueLevel, "the level is capped at 6")
    }

    /** "Damage taken * 20 / (level * 15)" against "damage dealt / (level * 15)". */
    @Test
    fun takingDamageFillsTheBlastGaugeTwentyTimesFaster() {
        val dealing = PhotonBlastGauge().apply { onDamageDealt(15, characterLevel = 1) }
        val taking = PhotonBlastGauge().apply { onDamageTaken(15, characterLevel = 1) }

        assertEquals(1.0, dealing.value)
        assertEquals(20.0, taking.value)
    }

    @Test
    fun theBlastGaugeFillsToOneHundredAndNoFurther() {
        val gauge = PhotonBlastGauge()
        repeat(20) { gauge.onDamageTaken(15, characterLevel = 1) }

        assertEquals(100.0, gauge.value)
        assertTrue(gauge.isFull)

        gauge.spend()
        assertEquals(0.0, gauge.value)
        assertTrue(!gauge.isFull)
    }

    /** Higher-level characters build the gauge more slowly for the same damage. */
    @Test
    fun theBlastGaugeScalesWithCharacterLevel() {
        val low = PhotonBlastGauge().apply { onDamageDealt(150, characterLevel = 1) }
        val high = PhotonBlastGauge().apply { onDamageDealt(150, characterLevel = 10) }

        assertTrue(low.value > high.value)
    }

    /** Table 0: a Monomate is mostly POW food -- 2.5 of them make one visible POW point. */
    @Test
    fun feedingMatesGrowsPow() {
        var mag = Mag()
        repeat(3) { mag = mag.fed(ToolType.MONOMATE)!! }

        assertEquals(1, mag.pow, "3 Monomates x 40 exp = 120 exp = 1 POW point")
        assertEquals(0, mag.mind)
        assertEquals(29, mag.synchro, "3 feeds x 3 synchro on top of the starter 20")
    }

    @Test
    fun weaponsAreNotMagFood() {
        assertEquals(null, Mag().fed(ToolType.SCAPE_DOLL))
        assertTrue(!isMagFood(ToolType.SCAPE_DOLL))
        assertTrue(isMagFood(ToolType.MONOFLUID))
    }

    /** 500 fed POW exp on the starter's 500 DEF exp crosses the level-10 evolution line. */
    @Test
    fun sustainedFeedingReachesTheEvolutionLevel() {
        var mag = Mag()
        repeat(13) { mag = mag.fed(ToolType.MONOMATE)!! }

        assertTrue(mag.level >= Mag.FIRST_EVOLUTION_LEVEL)
    }

    /** The first evolution depends only on the class that raises the Mag to level 10. */
    @Test
    fun theFirstEvolutionFollowsTheClassThatRaisedIt() {
        assertEquals("Varuna", firstEvolutionOf(CharacterClass.HUmar))
        assertEquals("Varuna", firstEvolutionOf(CharacterClass.HUcaseal))
        assertEquals("Kalki", firstEvolutionOf(CharacterClass.RAmar))
        assertEquals("Vritra", firstEvolutionOf(CharacterClass.FOnewearl))
    }
}
