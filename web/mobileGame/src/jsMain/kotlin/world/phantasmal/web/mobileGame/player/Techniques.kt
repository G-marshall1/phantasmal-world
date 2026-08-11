package world.phantasmal.web.mobileGame.player

/**
 * The technique system's Forest-era slice: the three basic attack techniques and Resta, with the
 * wiki's own numbers.
 *
 * Damage is the wiki's formula (quoted on the Foie page):
 * `Damage = (TechPower + MST) x 0.2 x (1 + boosts) x (100 - resistance) / 100`, truncated like
 * all PSO damage; no boosts exist yet so that factor is 1. Resta restores
 * `TechPower + MST / 2`.
 *
 * Power tables are the wiki's levels 1-5 verbatim. Technique disks aren't dropping yet, so every
 * known technique sits at level 1 -- the tables exist so disks have something to raise. TP
 * costs: Foie's page publishes its cost column (5/6/7/8/10) and Barta/Zonde borrow that curve
 * until their pages are transcribed; Resta's flat 15 is this project's own placeholder.
 */
enum class Technique(
    val uiName: String,
    val icon: TechniqueIcon,
    private val powers: IntArray,
    private val tpCosts: IntArray,
) {
    // -- Fire --
    FOIE("Foie", TechniqueIcon.FOIE, intArrayOf(110, 160, 210, 260, 310), intArrayOf(5, 6, 7, 8, 10)),
    GIFOIE("Gifoie", TechniqueIcon.GIFOIE, intArrayOf(260, 286, 312, 338, 364), intArrayOf(20, 20, 21, 22, 22)),
    RAFOIE("Rafoie", TechniqueIcon.RAFOIE, intArrayOf(350, 372, 394, 416, 438), intArrayOf(30, 30, 30, 30, 31)),

    // -- Ice --
    BARTA("Barta", TechniqueIcon.BARTA, intArrayOf(50, 100, 150, 200, 250), intArrayOf(5, 6, 7, 8, 10)),
    GIBARTA("Gibarta", TechniqueIcon.GIBARTA, intArrayOf(230, 254, 278, 302, 326), intArrayOf(25, 25, 26, 26, 27)),
    RABARTA("Rabarta", TechniqueIcon.RABARTA, intArrayOf(400, 419, 438, 457, 476), intArrayOf(35, 35, 35, 35, 35)),

    // -- Lightning --
    ZONDE("Zonde", TechniqueIcon.ZONDE, intArrayOf(80, 130, 180, 230, 280), intArrayOf(5, 6, 7, 8, 10)),
    GIZONDE("Gizonde", TechniqueIcon.GIZONDE, intArrayOf(200, 222, 244, 266, 288), intArrayOf(25, 25, 26, 26, 27)),
    RAZONDE("Razonde", TechniqueIcon.RAZONDE, intArrayOf(450, 466, 482, 498, 514), intArrayOf(35, 35, 35, 35, 36)),

    // -- Light and dark --
    GRANTS("Grants", TechniqueIcon.GRANTS, intArrayOf(1180, 1255, 1330, 1405, 1480), intArrayOf(45, 46, 47, 48, 49)),

    /** Megid's "power" is its percent chance to kill outright, less the target's dark resist. */
    MEGID("Megid", TechniqueIcon.MEGID, intArrayOf(27, 30, 33, 36, 39), intArrayOf(30, 31, 32, 33, 34)),

    // -- Recovery --
    RESTA("Resta", TechniqueIcon.RESTA, intArrayOf(50, 50, 50, 50, 50), intArrayOf(15, 15, 15, 15, 15)),
    ANTI("Anti", TechniqueIcon.ANTI, intArrayOf(0, 0, 0, 0, 0), intArrayOf(10, 10, 11, 11, 12)),
    REVERSER("Reverser", TechniqueIcon.REVERSER, intArrayOf(0, 0, 0, 0, 0), intArrayOf(20, 20, 20, 20, 20)),

    // -- Support: the buffs and debuffs share one 10% + ~1.3%/level curve and 40s + 10s/level. --
    SHIFTA("Shifta", TechniqueIcon.SHIFTA, intArrayOf(0, 0, 0, 0, 0), intArrayOf(10, 11, 12, 13, 14)),
    DEBAND("Deband", TechniqueIcon.DEBAND, intArrayOf(0, 0, 0, 0, 0), intArrayOf(10, 11, 12, 13, 14)),
    JELLEN("Jellen", TechniqueIcon.JELLEN, intArrayOf(0, 0, 0, 0, 0), intArrayOf(10, 11, 12, 13, 14)),
    ZALURE("Zalure", TechniqueIcon.ZALURE, intArrayOf(0, 0, 0, 0, 0), intArrayOf(10, 11, 12, 13, 14)),

    /** Opens a telepipe back to Pioneer 2 where the caster stands. */
    RYUKER("Ryuker", TechniqueIcon.RYUKER, intArrayOf(0, 0, 0, 0, 0), intArrayOf(15, 15, 15, 15, 15)),
    ;

    fun power(level: Int): Int = powers[(level - 1).coerceIn(0, powers.size - 1)]
    fun tpCost(level: Int): Int = tpCosts[(level - 1).coerceIn(0, tpCosts.size - 1)]
}

/**
 * The support curve every buff and debuff shares, from the wiki's Shifta/Deband/Jellen/Zalure
 * pages: 10% at level 1 rising ~1.3% per level, lasting 40 seconds plus 10 per level.
 */
fun supportBoostFraction(level: Int): Double = 0.10 + (level - 1) * 0.013

fun supportDurationSeconds(level: Int): Double = 40.0 + (level - 1) * 10.0

/** Rabarta's published freeze odds; Gibarta shares them until its own figure surfaces. */
const val ICE_FREEZE_CHANCE = 0.20

/** The wiki's attack-technique damage formula, truncated, floored at 1 like physical damage. */
fun techniqueDamage(power: Int, mst: Int, resistancePercent: Int, boost: Double = 0.0): Int =
    ((power + mst) * 0.2 * (1.0 + boost) * (100 - resistancePercent) / 100.0)
        .toInt()
        .coerceAtLeast(1)

/** The wiki's Resta formula: `TechPower + MST / 2`. */
fun restaHeal(power: Int, mst: Int): Int = power + mst / 2
