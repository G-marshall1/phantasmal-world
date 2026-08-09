package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.models.CharacterClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The progression systems against the wiki figures they were transcribed from: the shared EXP
 * curve, the per-class growth anchors, the tools' Normal-difficulty restoration values, and the
 * Forest rare chart's flavour cells.
 */
class ProgressionTests {
    @Test
    fun theExpCurveMatchesTheWikiAnchors() {
        assertEquals(0, totalExpForLevel(1))
        assertEquals(50, totalExpForLevel(2))
        assertEquals(4_050, totalExpForLevel(10))
        assertEquals(20_539, totalExpForLevel(20))
        assertEquals(69_816, totalExpForLevel(30))
    }

    @Test
    fun levelsFollowTotalExpExactly() {
        assertEquals(1, levelForTotalExp(0))
        assertEquals(1, levelForTotalExp(49))
        assertEquals(2, levelForTotalExp(50))
        assertEquals(9, levelForTotalExp(4_049))
        assertEquals(10, levelForTotalExp(4_050))
    }

    @Test
    fun statGrowthIsAnchoredToTheWikiAtBothEnds() {
        val level1 = statsAtLevel(CharacterClass.HUmar, 1)
        assertEquals(40, level1.hp)
        assertEquals(45, level1.atp)

        val level200 = statsAtLevel(CharacterClass.HUmar, 200)
        assertEquals(1_420, level200.hp)
        assertEquals(943, level200.atp)
    }

    @Test
    fun androidsNeverGrowTechniquePoints() {
        assertEquals(0, statsAtLevel(CharacterClass.HUcast, 120).tp)
        assertEquals(0, statsAtLevel(CharacterClass.RAcaseal, 200).mst)
    }

    @Test
    fun aSavageWolfCarriesARareForPinkalAlone() {
        assertEquals("Recovery Barrier", forestRareDropName("SavageWolf", SectionId.Pinkal))
        assertNull(forestRareDropName("SavageWolf", SectionId.Skyly))
        assertNull(forestRareDropName("SavageWolf", SectionId.Viridia))
    }

    @Test
    fun redriaIsTheOddGigaboomaOut() {
        assertEquals("Star Atomizer", forestRareDropName("GigaBooma", SectionId.Redria))
        assertEquals("Sol Atomizer", forestRareDropName("GigaBooma", SectionId.Viridia))
    }

    @Test
    fun everyBoomaHuntsItsOwnRightArm() {
        for (id in SectionId.entries) {
            assertEquals("Booma's Right Arm", forestRareDropName("Booma", id))
        }
    }

    @Test
    fun aMonomateRestoresTheWikisNormalAmount() {
        assertEquals(80, ToolType.MONOMATE.hpRestored(maxHp = 500))
        assertEquals(200, ToolType.DIMATE.hpRestored(maxHp = 500))
        assertEquals(500, ToolType.TRIMATE.hpRestored(maxHp = 500))
        assertNull(ToolType.ANTIDOTE.hpRestored(maxHp = 500))
    }

    @Test
    fun toolsStackToTheRealCap() {
        assertTrue(ToolType.entries.all { it.maxStack == 10 })
    }

    /** The wiki's formula: (power + MST) x 0.2 x (100 - resist) / 100, truncated. */
    @Test
    fun techniqueDamageFollowsTheWikiFormula() {
        assertEquals(32, techniqueDamage(power = 110, mst = 53, resistancePercent = 0))
        assertEquals(16, techniqueDamage(power = 110, mst = 53, resistancePercent = 50))
        assertEquals(76, restaHeal(power = 50, mst = 53))
    }

    /** The full technique roster, anchored to the wiki's own level-1 numbers. */
    @Test
    fun theWholeTechniqueRosterMatchesTheWiki() {
        assertEquals(19, Technique.entries.size)
        assertEquals(260, Technique.GIFOIE.power(1)); assertEquals(20, Technique.GIFOIE.tpCost(1))
        assertEquals(350, Technique.RAFOIE.power(1)); assertEquals(30, Technique.RAFOIE.tpCost(1))
        assertEquals(230, Technique.GIBARTA.power(1)); assertEquals(25, Technique.GIBARTA.tpCost(1))
        assertEquals(400, Technique.RABARTA.power(1)); assertEquals(35, Technique.RABARTA.tpCost(1))
        assertEquals(200, Technique.GIZONDE.power(1)); assertEquals(25, Technique.GIZONDE.tpCost(1))
        assertEquals(450, Technique.RAZONDE.power(1)); assertEquals(35, Technique.RAZONDE.tpCost(1))
        assertEquals(1180, Technique.GRANTS.power(1)); assertEquals(45, Technique.GRANTS.tpCost(1))
        assertEquals(27, Technique.MEGID.power(1)); assertEquals(30, Technique.MEGID.tpCost(1))
        assertEquals(10, Technique.SHIFTA.tpCost(1))
    }

    /** Shifta at level 1 is +10% for 40 seconds; the curve climbs ~1.3%/level and 10s/level. */
    @Test
    fun theSupportCurveMatchesTheWiki() {
        assertEquals(0.10, supportBoostFraction(1))
        assertEquals(40.0, supportDurationSeconds(1))
        assertEquals(90.0, supportDurationSeconds(6))
    }

    @Test
    fun foieCostsItsPageTpAtEveryLevel() {
        assertEquals(5, Technique.FOIE.tpCost(1))
        assertEquals(10, Technique.FOIE.tpCost(5))
        assertEquals(110, Technique.FOIE.power(1))
        assertEquals(310, Technique.FOIE.power(5))
    }

    /** The arms shop opens with only the starter tiers and grows a star every five levels. */
    @Test
    fun theArmsShopStockGrowsWithLevel() {
        // Every common line's base tier -- one per weapon class the shop deals in. (BB's star
        // ratings don't start every line at zero, so the count is the invariant, not the stars.)
        assertEquals(12, armsShopStock(1).size)
        assertTrue(armsShopStock(20).any { it.name == "Gladius" })
        assertTrue(armsShopStock(20).any { it.name == "Calibur" })
        // The specialty and in-series rares never reach the shelf.
        assertTrue(armsShopStock(200).none { it.name == "Yamigarasu" })
        assertTrue(armsShopStock(200).none { it.stars >= 9 })
    }

    /** The generated catalogue carries the client's own armor stats and level gates. */
    @Test
    fun armorSpecsMatchTheClientData() {
        assertEquals("Frame", FRAME_SPECS.first().name)
        assertEquals(5, FRAME_SPECS.first().dfpMin)
        assertEquals(0, FRAME_SPECS.first().levelReq)

        // The client's own gate is 38 (the wiki's 39 was one off), DFP rolls 55-59.
        val kings = frameSpecByName("King's Frame")!!
        assertEquals(38, kings.levelReq)
        assertEquals(55, kings.dfpMin)
        assertEquals(59, kings.dfpMax)

        assertEquals("Barrier", BARRIER_SPECS.first().name)
        assertEquals(25, BARRIER_SPECS.first().evpMin)
        assertTrue(BARRIER_SPECS.any { it.name == "Plasma Barrier" })

        // The catalogue reaches all the way up: the famous dev-armor endgame rare exists and
        // stays off the shop shelf.
        val sonicteam = frameSpecByName("Sonicteam Armor")!!
        assertEquals(199, sonicteam.levelReq)
        assertTrue(armorShopFrames(200).none { it.name == "Sonicteam Armor" })
    }

    @Test
    fun unitsGrantTheWikiBonuses() {
        assertEquals(10, UnitType.GENERAL_POWER.atp)
        assertEquals(5, UnitType.KNIGHT_POWER.atp)
        assertEquals(5, UnitType.GENERAL_ARM.ata)
        assertEquals(3, UnitType.MARKSMAN_ARM.ata)
        assertEquals(20, UnitType.GENERAL_HP.hp)
        assertEquals(10, UnitType.GENERAL_MIND.mst)
    }

    @Test
    fun rolledArmorStaysInsideItsSpecRanges() {
        val random = kotlin.random.Random(7)
        repeat(50) {
            val frame = rollFrame(FRAME_SPECS[0], random)
            assertTrue(frame.dfp in 5..7)
            assertTrue(frame.evp in 5..7)
            assertTrue(frame.slots in 0..4)
            val barrier = rollBarrier(BARRIER_SPECS[0], random)
            assertTrue(barrier.dfp in 2..7)
            assertTrue(barrier.evp in 25..30)
        }
    }

    @Test
    fun theRecoveryBarrierSurvivedTheTreasureMigration() {
        assertEquals("Recovery Barrier", barrierSpecByName("Recovery Barrier")?.name)
        assertEquals("Recovery Barrier", forestRareDropName("SavageWolf", SectionId.Pinkal))
        assertEquals("Resist/Fire", forestRareDropName("Hildeblue", SectionId.Viridia))
    }

    @Test
    fun savedWeaponsSurviveTheRoundTrip() {
        val tier = weaponTierByName("Ripper")
        assertEquals(WeaponType.DAGGER, tier?.type)
        assertEquals(125, tier?.atpMin)
    }
}
