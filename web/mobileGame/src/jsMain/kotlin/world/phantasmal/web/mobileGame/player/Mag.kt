package world.phantasmal.web.mobileGame.player

import kotlin.math.min

/**
 * The equipped Mag: a robotic companion that floats beside the character and is, in PSO, the main
 * source of stats a character has. Its own four stats convert into the character's at fixed rates,
 * so a fully raised Mag is worth far more than levelling.
 *
 * Every character starts with a level 5 Mag holding all five points in DEF, which is what the +5
 * defence at character creation comes from.
 *
 * [synchro] and [iq] are secondary: they don't count toward the level or grant stats, but drive
 * how often triggers fire and how strong the Mag's support techniques are.
 */
class Mag(
    val defExp: Int = STARTER_DEF * EXP_PER_LEVEL,
    val powExp: Int = 0,
    val dexExp: Int = 0,
    val mindExp: Int = 0,
    /** 0-120. Boosts trigger rates and Photon Blast damage; 5% is lost on death. */
    val synchro: Int = STARTER_SYNCHRO,
    /** 0-200. Sets the level of the Resta/Shifta/Deband a trigger casts. */
    val iq: Int = 0,
) {
    /** Every 100 feed experience in one stat is one visible point of it (wiki). */
    val def: Int get() = defExp / EXP_PER_LEVEL
    val pow: Int get() = powExp / EXP_PER_LEVEL
    val dex: Int get() = dexExp / EXP_PER_LEVEL
    val mind: Int get() = mindExp / EXP_PER_LEVEL

    /** A Mag's level is simply the sum of its four stats. */
    val level: Int get() = def + pow + dex + mind

    /** Defence granted to the character: 1 per DEF. */
    val bonusDfp: Int get() = def

    /** Attack power granted: 2 per POW. */
    val bonusAtp: Int get() = pow * 2

    /** Accuracy granted: half a point per DEX, truncated as everything else in PSO is. */
    val bonusAta: Int get() = dex / 2

    /** Mental strength granted: 2 per MIND. */
    val bonusMst: Int get() = mind * 2

    /**
     * Level of the Resta, Shifta or Deband a trigger casts, capped at 6. Nothing casts these yet
     * -- there's no technique system -- but the figure is what decides how much they'd be worth.
     */
    val supportTechniqueLevel: Int get() = min(iq / 40 + 1, MAX_SUPPORT_LEVEL)

    /**
     * Extra trigger chance from synchro, in percentage points. Mags whose triggers are
     * synchro-based either add this to their base rate or use it alone.
     */
    val synchroTriggerBoost: Int
        get() = when {
            synchro <= 30 -> 0
            synchro <= 60 -> 15
            synchro <= 80 -> 25
            synchro <= 100 -> 30
            else -> 35
        }

    /** A death costs 5 synchro, never dropping below zero. */
    fun afterDeath(): Mag =
        Mag(defExp, powExp, dexExp, mindExp, (synchro - DEATH_SYNCHRO_PENALTY).coerceAtLeast(0), iq)

    /**
     * One feeding, per Table 0 of the wiki's Mag feeding tables -- the basic Mag's own chart:
     * mates feed POW, fluids MIND, the cure items DEX, atomizers a little of everything. Null if
     * the item isn't Mag food. Evolved forms keep eating from this table until their own tables
     * are transcribed.
     */
    fun fed(tool: ToolType): Mag? {
        val food = MAG_FEED_TABLE[tool] ?: return null
        return Mag(
            defExp = (defExp + food.def).coerceAtMost(MAX_STAT * EXP_PER_LEVEL),
            powExp = (powExp + food.pow).coerceAtMost(MAX_STAT * EXP_PER_LEVEL),
            dexExp = (dexExp + food.dex).coerceAtMost(MAX_STAT * EXP_PER_LEVEL),
            mindExp = (mindExp + food.mind).coerceAtMost(MAX_STAT * EXP_PER_LEVEL),
            synchro = (synchro + food.synchro).coerceAtMost(MAX_SYNCHRO),
            iq = (iq + food.iq).coerceAtMost(MAX_IQ),
        )
    }

    companion object {
        const val EXP_PER_LEVEL = 100

        /** Three items per 3:30 window, per the wiki; unspent feeds don't carry over. */
        const val FEEDS_PER_WINDOW = 3
        const val FEED_WINDOW_SECONDS = 210

        const val STARTER_DEF = 5

        /**
         * Starting synchro. The supplied data gives the range (0-120) and the death penalty but
         * not what a new Mag begins at; 20 is what PSO hands out, and it's one constant to change.
         */
        const val STARTER_SYNCHRO = 20

        const val MAX_LEVEL = 200
        const val MAX_STAT = 200
        const val MAX_SYNCHRO = 120
        const val MAX_IQ = 200
        const val MAX_SUPPORT_LEVEL = 6
        const val DEATH_SYNCHRO_PENALTY = 5

        /** A Mag can't act at all until it has evolved once, which happens at level 10. */
        const val FIRST_EVOLUTION_LEVEL = 10
    }
}

/**
 * The Photon Blast gauge, filled by dealing and taking damage and spent on the Mag's blast.
 *
 * Taking damage fills it twenty times faster than dealing the same amount, which is why a
 * character being worn down builds a blast far quicker than one winning comfortably. Both rates
 * are divided by the character's level, so the gauge doesn't trivialise as the character grows.
 */
class PhotonBlastGauge {
    var value: Double = 0.0
        private set

    val isFull: Boolean get() = value >= FULL

    fun onDamageDealt(damage: Int, characterLevel: Int) {
        add(damage / (characterLevel * RATE_DIVISOR))
    }

    fun onDamageTaken(damage: Int, characterLevel: Int) {
        add(damage * TAKEN_MULTIPLIER / (characterLevel * RATE_DIVISOR))
    }

    /** Spends the whole gauge. */
    fun spend() {
        value = 0.0
    }

    private fun add(amount: Double) {
        value = (value + amount).coerceIn(0.0, FULL)
    }

    private companion object {
        const val FULL = 100.0
        const val RATE_DIVISOR = 15.0
        const val TAKEN_MULTIPLIER = 20.0
    }
}

/**
 * What a Mag's trigger does when it fires. Only [INVULNERABILITY] has anything behind it -- the
 * other three cast techniques, and there is no technique system yet.
 */
enum class MagTrigger {
    INVULNERABILITY,
    RESTA,
    SHIFTA_AND_DEBAND,
    REVERSER,
}

/** How long a Mag's invulnerability trigger protects the character. */
const val MAG_INVULNERABILITY_SECONDS = 30.0

/** What one food item feeds into each stat (in exp hundredths), plus synchro and IQ. */
private class MagFood(
    val def: Int,
    val pow: Int,
    val dex: Int,
    val mind: Int,
    val synchro: Int,
    val iq: Int,
)

/** Table 0 of the wiki's Mag feeding tables, verbatim. */
private val MAG_FEED_TABLE: Map<ToolType, MagFood> = mapOf(
    ToolType.MONOMATE to MagFood(5, 40, 5, 0, 3, 3),
    ToolType.DIMATE to MagFood(10, 45, 5, 0, 3, 3),
    ToolType.TRIMATE to MagFood(15, 50, 10, 0, 4, 4),
    ToolType.MONOFLUID to MagFood(5, 0, 5, 40, 3, 3),
    ToolType.DIFLUID to MagFood(10, 0, 5, 45, 3, 3),
    ToolType.TRIFLUID to MagFood(15, 0, 10, 50, 4, 4),
    ToolType.ANTIDOTE to MagFood(5, 10, 40, 0, 3, 3),
    ToolType.ANTIPARALYSIS to MagFood(5, 0, 44, 10, 3, 3),
    ToolType.SOL_ATOMIZER to MagFood(15, 30, 15, 25, 4, 1),
    ToolType.STAR_ATOMIZER to MagFood(25, 25, 25, 25, 6, 5),
)

fun isMagFood(tool: ToolType): Boolean = tool in MAG_FEED_TABLE

/** "POW +40  DEF +5" style summary of a food's gains, for the menu's feed rows. */
fun magFoodHint(tool: ToolType): String {
    val food = MAG_FEED_TABLE[tool] ?: return ""
    return buildString {
        if (food.pow > 0) append("POW +").append(food.pow).append("  ")
        if (food.mind > 0) append("MIND +").append(food.mind).append("  ")
        if (food.dex > 0) append("DEX +").append(food.dex).append("  ")
        if (food.def > 0) append("DEF +").append(food.def)
    }.trim()
}

/**
 * The Mag a character's starter evolves into at level 10, decided purely by the class that raises
 * it there. These are real models this project already ships.
 */
fun firstEvolutionOf(characterClass: world.phantasmal.web.viewer.models.CharacterClass): String =
    when (professionOf(characterClass)) {
        Profession.HUNTER -> "Varuna"
        Profession.RANGER -> "Kalki"
        Profession.FORCE -> "Vritra"
    }
