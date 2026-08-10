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

    /** Feeding never changes the form by itself -- evolution is the caller's explicit step. */
    @Test
    fun feedingCarriesTheFormForward() {
        var mag = Mag(form = "Varuna")
        repeat(5) { mag = mag.fed(ToolType.MONOMATE)!! }
        assertEquals("Varuna", mag.form)
        assertEquals("Rudra", mag.withForm("Rudra").form)
        assertEquals("Rudra", mag.withForm("Rudra").afterDeath().form)
    }

    /** The wiki's whole level-35 table: parent form x leading stat, POW winning ties. */
    @Test
    fun theSecondEvolutionFollowsTheWikiTable() {
        fun mag(form: String, pow: Int, dex: Int, mind: Int) = Mag(
            powExp = pow * Mag.EXP_PER_LEVEL,
            dexExp = dex * Mag.EXP_PER_LEVEL,
            mindExp = mind * Mag.EXP_PER_LEVEL,
            form = form,
        )

        assertEquals("Rudra", mag("Varuna", 20, 10, 5).secondEvolutionForm())
        assertEquals("Marutah", mag("Varuna", 10, 20, 5).secondEvolutionForm())
        assertEquals("Vayu", mag("Varuna", 5, 10, 20).secondEvolutionForm())

        assertEquals("Surya", mag("Kalki", 20, 10, 5).secondEvolutionForm())
        assertEquals("Mitra", mag("Kalki", 10, 20, 5).secondEvolutionForm())
        assertEquals("Tapas", mag("Kalki", 5, 10, 20).secondEvolutionForm())

        assertEquals("Sumba", mag("Vritra", 20, 10, 5).secondEvolutionForm())
        assertEquals("Ashvinau", mag("Vritra", 10, 20, 5).secondEvolutionForm())
        assertEquals("Namuci", mag("Vritra", 5, 10, 20).secondEvolutionForm())

        // Ties: POW beats DEX beats MIND.
        assertEquals("Rudra", mag("Varuna", 15, 15, 15).secondEvolutionForm())
        assertEquals("Marutah", mag("Varuna", 10, 15, 15).secondEvolutionForm())

        // The base form and the second forms have no second evolution of their own.
        assertEquals(null, mag(Mag.BASE_FORM, 20, 10, 5).secondEvolutionForm())
        assertEquals(null, mag("Rudra", 20, 10, 5).secondEvolutionForm())
    }

    /** Spot checks across the wiki's level-50 matrix: class x Section-ID group x ordering. */
    @Test
    fun theThirdEvolutionFollowsTheWikiMatrix() {
        fun mag(pow: Int, dex: Int, mind: Int, def: Int = 5) = Mag(
            defExp = def * Mag.EXP_PER_LEVEL,
            powExp = pow * Mag.EXP_PER_LEVEL,
            dexExp = dex * Mag.EXP_PER_LEVEL,
            mindExp = mind * Mag.EXP_PER_LEVEL,
            form = "Rudra",
        )
        val hunter = Profession.HUNTER
        val ranger = Profession.RANGER
        val force = Profession.FORCE

        // Hunter, group B (Greenill side): POW-led with MIND over DEX is Apsaras.
        assertEquals("Apsaras", mag(30, 5, 20).thirdEvolutionForm(hunter, false, sectionGroupA = false))
        // Hunter, group A (Viridia side): the same ordering is Bhirava.
        assertEquals("Bhirava", mag(30, 5, 20).thirdEvolutionForm(hunter, false, sectionGroupA = true))
        // Hunter, group A, pure POW feeding (DEX trickle, no MIND) lands on Varaha.
        assertEquals("Varaha", mag(40, 8, 0).thirdEvolutionForm(hunter, false, sectionGroupA = true))

        // Ranger, group B: MIND > DEX > POW is Durga.
        assertEquals("Durga", mag(3, 10, 40).thirdEvolutionForm(ranger, false, sectionGroupA = false))

        // Force with 45+ DEF: Bana for anyone, Andhaka only for a POW-led female character.
        assertEquals("Bana", mag(10, 20, 20, def = 45).thirdEvolutionForm(force, false, sectionGroupA = true))
        assertEquals("Andhaka", mag(30, 10, 10, def = 45).thirdEvolutionForm(force, true, sectionGroupA = true))
        // The same POW-led Mag on a male Force falls through to the ID table (group A: Ravana).
        assertEquals("Ravana", mag(30, 10, 15, def = 45).thirdEvolutionForm(force, false, sectionGroupA = true))

        // Not a second form: no third evolution.
        assertEquals(null, Mag(form = "Varuna").thirdEvolutionForm(hunter, false, sectionGroupA = true))
    }
}
