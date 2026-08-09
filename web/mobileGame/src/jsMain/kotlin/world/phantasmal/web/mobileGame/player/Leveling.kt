package world.phantasmal.web.mobileGame.player

import kotlin.math.roundToInt
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * Levelling, from the game's own Blue Burst level table (see GeneratedLevelTable.kt -- newserv's
 * export of the client's PlyLevelTbl, converted by :web:assets-generation).
 *
 * The EXP curve is the table's own, all 200 levels, shared by every class -- its first thirty
 * entries match the wiki's Experience page verbatim, which is how the two sources validate each
 * other.
 *
 * Stats come from the same table, anchored to the wiki's published level-1 and level-200
 * endpoints ([BASE_STATS_LEVEL_1] / [MAX_STATS_LEVEL_200]): the raw table matches those
 * endpoints exactly for MST/EVP/DFP -- for those the anchoring changes nothing -- but the BB
 * client derives displayed HP and ATA (and small ATP constants) through per-profession factors
 * that live in the client binary, not in the data. Anchoring keeps every endpoint exactly as
 * published while the table supplies the true shape of the growth in between, replacing the
 * straight-line interpolation this file used to do.
 *
 * TP is the one stat the table doesn't carry at all (its column is zeros; the client computes TP
 * entirely from its own factors), so TP alone still interpolates between the wiki anchors.
 */

const val MAX_LEVEL = 200

/** Total lifetime EXP needed to *be* [level]. */
fun totalExpForLevel(level: Int): Int =
    GeneratedLevelTable.expTotals[(level.coerceIn(1, MAX_LEVEL)) - 1]

/** The level a character with [totalExp] lifetime EXP has reached. */
fun levelForTotalExp(totalExp: Int): Int {
    var level = 1
    while (level < MAX_LEVEL && totalExp >= totalExpForLevel(level + 1)) level++
    return level
}

/**
 * Level 200 statlines, from each class's own wiki page (fractional ATA truncated). Together with
 * [BASE_STATS_LEVEL_1] these anchor the generated table's curves at both ends. The zeros are
 * real: androids have no TP or MST at any level.
 */
private val MAX_STATS_LEVEL_200: Map<CharacterClass, BaseStats> = mapOf(
    CharacterClass.HUmar to BaseStats(hp = 1420, tp = 793, atp = 943, dfp = 422, mst = 594, ata = 174, lck = 10, evp = 682),
    CharacterClass.HUnewearl to BaseStats(hp = 1308, tp = 1084, atp = 835, dfp = 538, mst = 885, ata = 147, lck = 10, evp = 666),
    CharacterClass.HUcast to BaseStats(hp = 1762, tp = 0, atp = 1146, dfp = 501, mst = 0, ata = 158, lck = 10, evp = 585),
    CharacterClass.HUcaseal to BaseStats(hp = 1380, tp = 0, atp = 901, dfp = 399, mst = 0, ata = 184, lck = 10, evp = 777),
    CharacterClass.RAmar to BaseStats(hp = 1520, tp = 704, atp = 806, dfp = 359, mst = 505, ata = 230, lck = 10, evp = 639),
    CharacterClass.RAmarl to BaseStats(hp = 1315, tp = 931, atp = 743, dfp = 426, mst = 732, ata = 216, lck = 10, evp = 798),
    CharacterClass.RAcast to BaseStats(hp = 1964, tp = 0, atp = 859, dfp = 505, mst = 0, ata = 199, lck = 10, evp = 626),
    CharacterClass.RAcaseal to BaseStats(hp = 1890, tp = 0, atp = 775, dfp = 562, mst = 0, ata = 208, lck = 10, evp = 713),
    CharacterClass.FOmar to BaseStats(hp = 1175, tp = 1783, atp = 753, dfp = 321, mst = 990, ata = 138, lck = 10, evp = 551),
    CharacterClass.FOmarl to BaseStats(hp = 1273, tp = 1699, atp = 721, dfp = 351, mst = 934, ata = 144, lck = 10, evp = 513),
    CharacterClass.FOnewm to BaseStats(hp = 1232, tp = 1945, atp = 613, dfp = 408, mst = 1098, ata = 128, lck = 10, evp = 531),
    CharacterClass.FOnewearl to BaseStats(hp = 1148, tp = 2098, atp = 483, dfp = 334, mst = 1200, ata = 133, lck = 10, evp = 735),
)

/**
 * The class's naked statline at [level]: the generated table's curve, anchored so level 1 and
 * level 200 land exactly on the published endpoints. When the raw curve already hits both
 * endpoints (MST/EVP/DFP do), the anchoring is the identity and the game's own numbers pass
 * through untouched.
 */
fun statsAtLevel(characterClass: CharacterClass, level: Int): BaseStats {
    val base = BASE_STATS_LEVEL_1.getValue(characterClass)
    val max = MAX_STATS_LEVEL_200.getValue(characterClass)
    val index = level.coerceIn(1, MAX_LEVEL) - 1
    val slug = characterClass.slug

    fun anchored(curveByClass: Map<String, IntArray>, anchor1: Int, anchor200: Int): Int {
        val curve = curveByClass.getValue(slug)
        val raw1 = curve[0]
        val raw200 = curve[MAX_LEVEL - 1]
        if (raw200 == raw1) return anchor1
        val scale = (anchor200 - anchor1).toDouble() / (raw200 - raw1)
        return (anchor1 + (curve[index] - raw1) * scale).roundToInt()
    }

    // TP: absent from the table; straight line between the anchors, exact at both ends.
    val tp = base.tp + ((max.tp - base.tp) * (index / (MAX_LEVEL - 1.0))).toInt()

    return BaseStats(
        hp = anchored(GeneratedLevelTable.hp, base.hp, max.hp),
        tp = tp,
        atp = anchored(GeneratedLevelTable.atp, base.atp, max.atp),
        dfp = anchored(GeneratedLevelTable.dfp, base.dfp, max.dfp),
        mst = anchored(GeneratedLevelTable.mst, base.mst, max.mst),
        ata = anchored(GeneratedLevelTable.ata, base.ata, max.ata),
        lck = base.lck,
        evp = anchored(GeneratedLevelTable.evp, base.evp, max.evp),
    )
}
