package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.viewer.models.CharacterClass
import kotlin.math.min

/**
 * A character's seven core base attributes: what they are with no weapon, armour, shield or Mag
 * equipped. These grow only by levelling up or by permanently consuming Stat Materials, so at
 * Level 1 they're just [BASE_STATS_LEVEL_1].
 *
 * Androids (the Casts and Caseals) have no [tp] or [mst] at all -- both stay at zero rather than
 * being small, which is why they can't cast techniques.
 */
class BaseStats(
    /** Total life pool. At zero the character faints. */
    val hp: Int,
    /** Technique points, spent casting. Zero for androids. */
    val tp: Int,
    /** Attack power: physical damage output, and the requirement for equipping melee weapons. */
    val atp: Int,
    /** Defence power: reduces incoming physical damage, and gates body armour. */
    val dfp: Int,
    /** Mental strength: technique damage and TP pool. Zero for androids. */
    val mst: Int,
    /** Attack accuracy: hit rate, and the requirement for equipping firearms. */
    val ata: Int,
    /** Luck: critical hit rate -- see [criticalChance]. */
    val lck: Int,
    /**
     * Evasion: how often an incoming attack misses entirely. The mirror of what the player's
     * own swings measure against an enemy's evasion, and what makes a barrier worth wearing.
     */
    val evp: Int = 0,
)

/**
 * Every class's naked Level 1 statline, each class's own wiki page verbatim (fractional ATA
 * truncated) -- what the character is the second they step onto Pioneer 2, before any weapon,
 * armour, shield or Mag. Growth to 200 is anchored by the same pages -- see Leveling.kt.
 *
 * Level 1 characters are genuinely frail: health runs from 27 (the Force Newmans) to 44 (the
 * Hunter androids), so a Booma landing 5-8 a swing knocks a Force out in a handful of hits, and
 * one Monomate's flat 80 refills any class in the game from the brink.
 *
 * Luck is 10 across the board and is the one stat that never rises with levelling, which is why
 * it isn't varied here.
 */
val BASE_STATS_LEVEL_1: Map<CharacterClass, BaseStats> = mapOf(
    CharacterClass.HUmar to BaseStats(hp = 40, tp = 29, atp = 45, dfp = 17, mst = 29, ata = 68, lck = 10, evp = 45),
    CharacterClass.HUnewearl to BaseStats(hp = 38, tp = 40, atp = 40, dfp = 22, mst = 40, ata = 63, lck = 10, evp = 60),
    CharacterClass.HUcast to BaseStats(hp = 44, tp = 0, atp = 45, dfp = 18, mst = 0, ata = 64, lck = 10, evp = 35),
    CharacterClass.HUcaseal to BaseStats(hp = 44, tp = 0, atp = 45, dfp = 18, mst = 0, ata = 71, lck = 10, evp = 35),
    CharacterClass.RAmar to BaseStats(hp = 29, tp = 20, atp = 23, dfp = 13, mst = 20, ata = 80, lck = 10, evp = 36),
    CharacterClass.RAmarl to BaseStats(hp = 29, tp = 20, atp = 23, dfp = 13, mst = 20, ata = 72, lck = 10, evp = 36),
    CharacterClass.RAcast to BaseStats(hp = 33, tp = 0, atp = 30, dfp = 18, mst = 0, ata = 75, lck = 10, evp = 31),
    CharacterClass.RAcaseal to BaseStats(hp = 31, tp = 0, atp = 25, dfp = 23, mst = 0, ata = 77, lck = 10, evp = 31),
    CharacterClass.FOmar to BaseStats(hp = 29, tp = 79, atp = 16, dfp = 10, mst = 53, ata = 63, lck = 10, evp = 35),
    CharacterClass.FOmarl to BaseStats(hp = 29, tp = 79, atp = 16, dfp = 10, mst = 53, ata = 63, lck = 10, evp = 35),
    CharacterClass.FOnewm to BaseStats(hp = 27, tp = 90, atp = 16, dfp = 7, mst = 60, ata = 61, lck = 10, evp = 50),
    CharacterClass.FOnewearl to BaseStats(hp = 27, tp = 87, atp = 13, dfp = 13, mst = 58, ata = 61, lck = 10, evp = 53),
)

/**
 * How an attack is thrown. All three can be mixed freely within one combo.
 *
 * Every figure is PSO's own. Note that a special attack is *weaker* than a normal one -- its
 * value is the weapon's special effect, not raw damage -- and that accuracy falls as damage
 * rises, so heavy is a genuine trade rather than a strictly better button.
 */
enum class AttackType(
    /** Damage multiplier in [physicalDamage]. */
    val damageModifier: Double,
    /** Accuracy multiplier applied to the attacker's total ATA. */
    val accuracyModifier: Double,
    /** How much longer the swing takes than a normal one. This project's own pacing, not PSO's. */
    val windUp: Double,
) {
    NORMAL(damageModifier = 1.0, accuracyModifier = 1.0, windUp = 1.0),
    HEAVY(damageModifier = 1.89, accuracyModifier = 0.7, windUp = 1.5),
    SPECIAL(damageModifier = 0.56, accuracyModifier = 0.5, windUp = 1.4),
}

/**
 * Accuracy multiplier for each step of a three-hit combo. Later hits in a combo are markedly more
 * likely to land, which is what makes finishing a combo worthwhile even with heavy or special
 * attacks in it.
 */
val COMBO_STEP_ACCURACY: List<Double> = listOf(1.0, 1.3, 1.69)

/**
 * A class's hidden per-attack ATP roll. PSO displays the *maximum* ATP in the menu, with the real
 * base being that figure less the profession's own maximum, and a fresh roll added on every swing.
 */
enum class Profession(val variance: IntRange) {
    HUNTER(1..6),
    RANGER(1..4),
    FORCE(1..3),
}

/** Which of the three professions a class belongs to, from the first two letters of its name. */
fun professionOf(characterClass: CharacterClass): Profession =
    when (characterClass.name.take(2)) {
        "HU" -> Profession.HUNTER
        "RA" -> Profession.RANGER
        else -> Profession.FORCE
    }

/**
 * The attack power behind one swing: the class's hidden base, plus the equipped weapon, plus a
 * fresh profession roll. [random] is the roll, in [0, 1).
 *
 * The base is the menu figure less the profession's maximum variance, so a HUmar's displayed 45
 * is really 39 plus 1-6 per attack. Adding the roll on top of the displayed value instead would
 * quietly make every character stronger than the game says they are.
 */
fun effectiveAtp(
    stats: BaseStats,
    profession: Profession,
    weaponAtp: Int,
    random: Double,
): Int {
    val base = stats.atp - profession.variance.last
    val spread = profession.variance.last - profession.variance.first + 1
    val roll = profession.variance.first + (random * spread).toInt().coerceAtMost(spread - 1)
    return base + weaponAtp + roll
}

/**
 * Physical damage one hit deals: `[(ATP - DFP) / 5] x 0.9 x attack modifier`.
 *
 * Truncated rather than rounded, as PSO does -- 100.92 damage lands as 100.
 *
 * Floored at one so a defender whose DFP exceeds the attacker's ATP is very hard to hurt rather
 * than literally invulnerable, which would let a fight stall with no way to end it.
 */
fun physicalDamage(attackerAtp: Int, defenderDfp: Int, type: AttackType = AttackType.NORMAL): Int =
    physicalDamageWithModifier(attackerAtp, defenderDfp, type.damageModifier)

/** Same formula with the modifier stated outright -- a sacrificial special's 3.33x, say. */
fun physicalDamageWithModifier(attackerAtp: Int, defenderDfp: Int, modifier: Double): Int =
    ((attackerAtp - defenderDfp) / DAMAGE_DIVISOR * DAMAGE_SCALE * modifier)
        .toInt()
        .coerceAtLeast(1)

/**
 * Chance for one swing to land, as a percentage.
 *
 * Both modifiers stack, so the third hit of a normal combo is far more reliable than the first
 * heavy one. Anything at or above 100 always hits; anything at or below 0 always misses.
 */
fun accuracyPercent(
    totalAta: Int,
    type: AttackType,
    comboStep: Int,
    targetEvp: Int,
): Double {
    val stepModifier = COMBO_STEP_ACCURACY.getOrElse(comboStep) { COMBO_STEP_ACCURACY.last() }
    val effectiveAta = totalAta * type.accuracyModifier * stepModifier
    return (effectiveAta - targetEvp * EVP_WEIGHT).coerceIn(0.0, 100.0)
}

/**
 * Chance of a critical hit, as a fraction of 1. Every 5 points of luck is 1%, and luck caps at
 * 100 for a 20% critical rate -- the cap is applied here rather than trusted to the caller.
 *
 * Monsters use a more generous divisor of 2 for the same luck, hence [monster].
 */
fun criticalChance(lck: Int, monster: Boolean = false): Double =
    min(lck, MAX_LUCK) / ((if (monster) MONSTER_LUCK_PER_PERCENT else LUCK_PER_PERCENT) * 100.0)

/** A hit taking a quarter or more of the target's maximum health knocks it off its feet. */
fun isKnockdown(damage: Int, maxHp: Int): Boolean =
    maxHp > 0 && damage.toDouble() / maxHp >= KNOCKDOWN_FRACTION

private const val DAMAGE_DIVISOR = 5.0
private const val DAMAGE_SCALE = 0.9
private const val EVP_WEIGHT = 0.2
private const val MAX_LUCK = 100
private const val LUCK_PER_PERCENT = 5
private const val MONSTER_LUCK_PER_PERCENT = 2
private const val KNOCKDOWN_FRACTION = 0.25

/**
 * The attack power an equipped Saber adds on top of the wielder's own.
 *
 * Derived rather than supplied, by reconciling the enemy health figures against the damage
 * formula: a Rag Rappy's 35 HP is described as dying to "a single full 3-hit Saber combo", which
 * needs 12 damage a hit. Under `[(ATP - DFP) / 5] x 0.9` that takes 67 ATP, which is 22 more than
 * a naked HUmar's 45.
 *
 * At this value a high profession roll deals 12 and kills a Rag Rappy in three, while a low roll
 * deals 11 and needs a fourth -- which is exactly the "usually" in the original description. A
 * Booma then takes 5 hits and a Gobooma 6, holding the stated "two full combos" and "one extra
 * hit" as well.
 *
 * A single constant because weapons have no stats of their own yet; when they do, this belongs on
 * the weapon.
 */
const val SABER_ATP = 22

/** What a critical hit multiplies damage by. */
const val CRITICAL_MULTIPLIER = 1.5
