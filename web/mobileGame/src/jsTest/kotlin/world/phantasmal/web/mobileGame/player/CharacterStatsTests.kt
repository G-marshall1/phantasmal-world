package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.mobileGame.world.enemyStats
import world.phantasmal.web.viewer.models.CharacterClass
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * These pin the combat maths to the stated Level 1 outcomes -- how many saber hits each Forest
 * enemy is supposed to take, and how hard they hit back. The damage formula was derived from
 * exactly these, so if a stat or the divisor is ever edited, this is what catches it.
 */
class CharacterStatsTests {
    private val humar = BASE_STATS_LEVEL_1.getValue(CharacterClass.HUmar)

    /**
     * The stated hit counts are for a character swinging a Saber, not bare-handed, and on a good
     * profession roll -- the displayed ATP is the ceiling, so this is the best a HUmar can swing.
     */
    private val humarWithSaber = humar.atp + SABER_ATP

    private fun hitsToKill(slug: String): Int {
        val stats = enemyStats(slug)
        return ceil(stats.hp.toDouble() / physicalDamage(humarWithSaber, stats.dfp)).toInt()
    }

    private fun hitsToKO(characterClass: CharacterClass, enemySlug: String): Int {
        val stats = BASE_STATS_LEVEL_1.getValue(characterClass)
        val damage = physicalDamage(enemyStats(enemySlug).atp, stats.dfp)
        return ceil(stats.hp.toDouble() / damage).toInt()
    }

    @Test
    fun everyClassHasAStatline() {
        assertEquals(CharacterClass.entries.size, BASE_STATS_LEVEL_1.size)
    }

    @Test
    fun androidsHaveNoTechniquePoints() {
        for (androidClass in listOf(
            CharacterClass.HUcast,
            CharacterClass.HUcaseal,
            CharacterClass.RAcast,
            CharacterClass.RAcaseal,
        )) {
            val stats = BASE_STATS_LEVEL_1.getValue(androidClass)
            assertEquals(0, stats.tp, "$androidClass should have no TP")
            assertEquals(0, stats.mst, "$androidClass should have no MST")
        }
    }

    /**
     * Evasion is what decides how often an incoming attack is turned aside rather than absorbed,
     * so every class needs one -- these are each class page's own figures.
     */
    @Test
    fun everyClassHasEvasion() {
        for ((characterClass, stats) in BASE_STATS_LEVEL_1) {
            assertTrue(stats.evp > 0, "$characterClass should start with evasion")
        }
        assertEquals(45, BASE_STATS_LEVEL_1.getValue(CharacterClass.HUmar).evp)
        assertEquals(60, BASE_STATS_LEVEL_1.getValue(CharacterClass.HUnewearl).evp)
        assertEquals(682, statsAtLevel(CharacterClass.HUmar, 200).evp)
    }

    /** A Booma swinging at a fresh HUmar lands about half the time -- 60 ATA against 45 EVP. */
    @Test
    fun aBoomaMissesAFreshHunterSometimes() {
        val humarEvp = BASE_STATS_LEVEL_1.getValue(CharacterClass.HUmar).evp
        val chance = accuracyPercent(
            totalAta = enemyStats("Booma").ata,
            type = AttackType.NORMAL,
            comboStep = 0,
            targetEvp = humarEvp,
        )
        assertEquals(51.0, chance)
    }

    @Test
    fun luckStartsAtTenForEveryClass() {
        for ((characterClass, stats) in BASE_STATS_LEVEL_1) {
            assertEquals(10, stats.lck, "$characterClass should start at 10 luck")
        }
    }

    @Test
    fun aHumarSwingingASaberDealsTwelveDamageToAnUndefendedForestEnemy() {
        assertEquals(12, physicalDamage(humarWithSaber, defenderDfp = 0))
    }

    /** A low roll deals one less, which is what makes the Rag Rappy combo kill a "usually". */
    @Test
    fun aPoorProfessionRollDealsOneLess() {
        val worst = effectiveAtp(humar, Profession.HUNTER, SABER_ATP, random = 0.0)
        assertEquals(11, physicalDamage(worst, defenderDfp = 0))
    }

    /** Level 1 characters are frail: nothing in the game has a triple-digit health pool. */
    @Test
    fun everyClassStartsBetweenTwentySevenAndFortyFourHealth() {
        for ((characterClass, stats) in BASE_STATS_LEVEL_1) {
            assertTrue(
                stats.hp in 27..44,
                "$characterClass has ${stats.hp} HP, outside the real Level 1 range",
            )
        }
    }

    /** "a single standard Monomate ... will completely fill the entire health pool of any class". */
    @Test
    fun oneMonomateRefillsAnyClassFromTheBrink() {
        for ((characterClass, stats) in BASE_STATS_LEVEL_1) {
            val healed = ToolType.MONOMATE.hpRestored(maxHp = stats.hp)!!
            assertTrue(
                healed >= stats.hp,
                "a Monomate wouldn't top up $characterClass's ${stats.hp} HP",
            )
        }
    }

    /**
     * A Force is the frailest thing in the game: three Booma hits and it is over. Under the
     * wiki-verbatim statlines a RAcast happens to fall in the same number of hits (33 HP behind
     * 18 DFP), so the guarantee is "no one folds faster", strictly faster only than the Hunters.
     */
    @Test
    fun aBoomaKnocksOutAFonewearlFastest() {
        assertEquals(3, hitsToKO(CharacterClass.FOnewearl, "Booma"))

        for (other in CharacterClass.VALUES_LIST) {
            assertTrue(
                hitsToKO(other, "Booma") >= hitsToKO(CharacterClass.FOnewearl, "Booma"),
                "$other should survive at least as long as a FOnewearl",
            )
        }
        for (hunter in listOf(CharacterClass.HUmar, CharacterClass.HUcast)) {
            assertTrue(
                hitsToKO(hunter, "Booma") > hitsToKO(CharacterClass.FOnewearl, "Booma"),
                "$hunter should survive longer than a FOnewearl",
            )
        }
    }

    @Test
    fun aBoomaTakesLongerToKnockOutAHunterThanAForce() {
        assertTrue(hitsToKO(CharacterClass.HUmar, "Booma") > hitsToKO(CharacterClass.FOnewearl, "Booma"))
    }

    /**
     * The earlier prose described a Rag Rappy dying to one three-hit combo. Against its real 56
     * health and 10 defence that's three hits -- exactly the single full combo the original
     * description promised, now that the table really is the One Person column (the earlier,
     * accidentally-multiplayer figures made it six).
     */
    @Test
    fun aRagRappyDiesToOneFullCombo() {
        assertEquals(3, hitsToKill("Rappy"))
    }

    /** The Forest's baseline foot soldier: 60 health, no defence, five saber hits. */
    @Test
    fun aBoomaTakesFiveHits() {
        assertEquals(5, hitsToKill("Booma"))
    }

    /**
     * A Gobooma is tougher than a Booma on both counts -- more health and some defence -- so it
     * takes meaningfully longer, not the single extra hit the earlier prose suggested.
     */
    @Test
    fun aGoboomaTakesLongerThanABooma() {
        assertTrue(hitsToKill("GoBooma") > hitsToKill("Booma"))
    }

    /**
     * Defence matters enormously at this level: a Gigobooma's 30 halves a level 1 saber's damage,
     * so it is far tougher than its health alone suggests.
     */
    @Test
    fun defenceMattersMoreThanHealthAtLevelOne() {
        val gigo = enemyStats("GigaBooma")
        val perHit = physicalDamage(humarWithSaber, gigo.dfp)

        assertEquals(6, perHit, "a Gigobooma's 30 defence should roughly halve a saber hit")
        assertTrue(hitsToKill("GigaBooma") > 2 * hitsToKill("Booma"))
    }

    /**
     * A Booma's real attack power is 106, not the 42 the earlier prose quoted, so it hits a naked
     * HUmar for 16 -- three hits to a knockout, and every one of them a knockdown.
     *
     * That is what the real Normal table says a level 1 character faces bare. In the actual game
     * you would not be naked: a Frame and Barrier add both defence and evasion, neither of which
     * exists here yet.
     */
    @Test
    fun aBoomaHitsANakedHumarForEleven() {
        assertEquals(11, physicalDamage(enemyStats("Booma").atp, humar.dfp))
    }

    @Test
    fun aGigoboomaHitsHarderThanABooma() {
        val gigo = enemyStats("GigaBooma")
        val booma = enemyStats("Booma")
        assertTrue(physicalDamage(gigo.atp!!, humar.dfp) > physicalDamage(booma.atp!!, humar.dfp))
    }

    @Test
    fun aDefenderToughEnoughToShrugOffAHitStillTakesOne() {
        assertEquals(1, physicalDamage(attackerAtp = 10, defenderDfp = 500))
    }

    @Test
    fun luckConvertsToCriticalRateAtOnePercentPerFivePoints() {
        assertEquals(0.02, criticalChance(10))
        assertEquals(0.10, criticalChance(50))
    }

    @Test
    fun criticalRateCapsAtTwentyPercent() {
        assertEquals(0.20, criticalChance(100))
        assertEquals(0.20, criticalChance(255), "luck past 100 should not raise the rate")
    }

    /**
     * The Forest's real health figures, from the Episode 1 / Normal / One Person table. Pinned
     * because an earlier set derived from prose ran low across the board -- a Booma at 60 against
     * the real 92 -- and getting these wrong quietly rebalances every fight in the game.
     */
    @Test
    fun theForestHealthFiguresAreTheRealOnes() {
        assertEquals(30, enemyStats("Rappy").hp)
        assertEquals(60, enemyStats("Booma").hp)
        assertEquals(85, enemyStats("GoBooma").hp)
        assertEquals(110, enemyStats("GigaBooma").hp)
        assertEquals(45, enemyStats("SavageWolf").hp)
        assertEquals(65, enemyStats("BarbarousWolf").hp)
        assertEquals(300, enemyStats("Monest").hp)
        assertEquals(180, enemyStats("Hildebear").hp)
    }

    /** A Mothmant has 8 health -- the frailest thing in the game, and gone to a single hit. */
    @Test
    fun aMothmantDiesToOneHitFromAnything() {
        assertEquals(1, hitsToKill("Mothmant"))
    }

    /** The rare variants are far stronger than what they replace, not reskins. */
    @Test
    fun rareVariantsOutclassTheirBaseMonster() {
        assertTrue(enemyStats("AlRappy").hp > enemyStats("Rappy").hp * 4)
        assertTrue(enemyStats("Hildeblue").hp > enemyStats("Hildebear").hp)
        assertTrue(enemyStats("AlRappy").atp > enemyStats("Rappy").atp)
    }

    /** A hive has no attack power, accuracy or evasion at all -- it only soaks damage. */
    @Test
    fun aMonestIsInertApartFromItsHealth() {
        val monest = enemyStats("Monest")
        assertEquals(0, monest.atp)
        assertEquals(0, monest.ata)
        assertEquals(0, monest.evp)
        assertTrue(monest.isStationary)
    }
}
