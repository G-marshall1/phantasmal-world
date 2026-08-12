package world.phantasmal.web.mobileGame.player

import kotlin.random.Random
import world.phantasmal.web.shared.dto.SectionId

/**
 * The weapon *item* layer on top of [WeaponType]'s animation/timing classes: a concrete drop with
 * a tier, a per-swing ATP range, grind, and possibly a special.
 *
 * The catalogue itself is generated from the BB client's own ItemPMT (see
 * GeneratedItemCatalog.kt): every tier of the twelve common series plus each series' embedded
 * rares, and the rare families this game has motion sets for -- true ATP ranges, ATA, grind
 * caps, equip requirements and star ratings.
 */
class WeaponTier(
    val name: String,
    val type: WeaponType,
    val atpMin: Int,
    val atpMax: Int,
    val ata: Int,
    val stars: Int,
    /** The client's own item code -- its low byte is the tier within the series. */
    val code: String = "",
    /**
     * The converted model shown in hand. Resolved against the shipped model set by name where a
     * match exists; anything else is held as its class's base model, since the asset set has no
     * per-tier variants.
     */
    val modelSlug: String = name,
    /** The client's grind cap for this tier (+N maximum). */
    val maxGrind: Int = 0,
    /** Equip requirements -- the stat the class must reach before this can be drawn. */
    val atpRequired: Int = 0,
    val ataRequired: Int = 0,
    /** The client's own equip mask -- see [usableBy] in ClassRules.kt. All-classes default. */
    val usability: Int = 0xFF,
)

/** "DB\'S SABER" -> "Db\'s Saber": the source uses caps for rares; the UI reads better titled. */
internal fun titleCased(sourceName: String): String =
    if (sourceName.any { it.isLowerCase() }) sourceName
    else sourceName.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }

/** The base model each class falls back to when a tier ships no model of its own. */
private val BASE_MODEL_FOR_TYPE: Map<WeaponType, String> = mapOf(
    WeaponType.SABER to "Saber", WeaponType.SWORD to "Sword", WeaponType.DAGGER to "Dagger",
    WeaponType.PARTISAN to "Partisan", WeaponType.SLICER to "Slicer",
    WeaponType.HANDGUN to "Handgun", WeaponType.RIFLE to "Rifle", WeaponType.MECHGUN to "Mechgun",
    WeaponType.SHOT to "Shot", WeaponType.CANE to "Cane", WeaponType.ROD to "Rod",
    WeaponType.WAND to "Wand", WeaponType.CLAW to "Claw", WeaponType.DOUBLE_SABER to "DoubleSaber",
    WeaponType.KATANA to "Yamigarasu", WeaponType.TWIN_SWORD to "TwinkleStar",
    WeaponType.LAUNCHER to "PanzerFaust",
    // The talis family (Talis, Mahu, Hitogata) throws a card; the fan is the closest thing
    // the converted set has to one, and it reads correctly in a Force's hand.
    WeaponType.CARD to "FlightFan",
    // Bare hands have no item of their own, but a fallback keeps resolveModelSlug total.
    WeaponType.FIST to "Claw",
)

private fun normalizedForModelMatch(name: String): String =
    name.filter { it.isLetterOrDigit() }.lowercase()

/**
 * A looser key for the same purpose, with every "s" dropped as well. The catalogue and the
 * converted models disagree constantly about possessives -- "BRINGER'S RIFLE" against
 * BringerRifle, "HEAVEN PUNISHER" against HeavensPunisher -- and that single letter was the
 * only thing standing between dozens of rares and the art we already ship for them.
 */
private fun looseModelMatch(name: String): String =
    normalizedForModelMatch(name).filter { it != 's' }

/** Shipped model whose slug matches the item's name, else the class's base model. */
private fun resolveModelSlug(sourceName: String, type: WeaponType): String {
    val wanted = normalizedForModelMatch(sourceName)
    WEAPON_TYPES.keys.find { normalizedForModelMatch(it) == wanted }?.let { return it }
    val loose = looseModelMatch(sourceName)
    WEAPON_TYPES.keys.find { looseModelMatch(it) == loose }?.let { return it }
    return BASE_MODEL_FOR_TYPE.getValue(type)
}

/** The full generated catalogue as runtime tiers, in item-code order. */
val ALL_WEAPON_TIERS: List<WeaponTier> = GeneratedItemCatalog.weapons.map { w ->
    val type = WeaponType.valueOf(w.type)
    WeaponTier(
        name = titleCased(w.name),
        type = type,
        atpMin = w.atpMin,
        atpMax = w.atpMax,
        ata = w.ata,
        stars = w.stars,
        code = w.code,
        modelSlug = resolveModelSlug(w.name, type),
        maxGrind = w.maxGrind,
        atpRequired = w.atpRequired,
        ataRequired = w.ataRequired,
        usability = w.usability,
    )
}

/**
 * A common line: one series' five shop-and-drop tiers, weakest first. BB's star ratings don't
 * start every line at zero (a Sword line runs 1-5 stars to the Saber's 0-4), so the cut is
 * relative to the line's own base tier -- which also excludes the 9-star rares each series
 * embeds after its commons.
 */
private fun lineOf(type: WeaponType): List<WeaponTier> {
    val ofType = ALL_WEAPON_TIERS.filter { it.type == type }
    val baseStars = ofType.first().stars
    return ofType.filter { it.stars - baseStars <= 4 }.sortedBy { it.stars }
}

val SABER_LINE: List<WeaponTier> = lineOf(WeaponType.SABER)
val SWORD_LINE: List<WeaponTier> = lineOf(WeaponType.SWORD)
val DAGGER_LINE: List<WeaponTier> = lineOf(WeaponType.DAGGER)
val PARTISAN_LINE: List<WeaponTier> = lineOf(WeaponType.PARTISAN)
val SLICER_LINE: List<WeaponTier> = lineOf(WeaponType.SLICER)
val HANDGUN_LINE: List<WeaponTier> = lineOf(WeaponType.HANDGUN)
val RIFLE_LINE: List<WeaponTier> = lineOf(WeaponType.RIFLE)
val MECHGUN_LINE: List<WeaponTier> = lineOf(WeaponType.MECHGUN)
val SHOT_LINE: List<WeaponTier> = lineOf(WeaponType.SHOT)
val CANE_LINE: List<WeaponTier> = lineOf(WeaponType.CANE)
val ROD_LINE: List<WeaponTier> = lineOf(WeaponType.ROD)
val WAND_LINE: List<WeaponTier> = lineOf(WeaponType.WAND)

/**
 * One representative of each category PSO has no common line for, now carrying the client's own
 * stats (Photon Claw is the claw family's base item -- the old placeholder called it "Claw").
 */
val SPECIALTY_TIERS: List<WeaponTier> = listOf(
    "Double Saber", "Photon Claw", "Yamigarasu", "Twinkle Star", "Panzer Faust",
).mapNotNull { name -> ALL_WEAPON_TIERS.find { it.name == name } }

/** Saves store tiers by name -- the lookup the restore path uses. */
fun weaponTierByName(name: String): WeaponTier? = ALL_WEAPON_TIERS.find { it.name == name }

/**
 * The five common tiers of a weapon series, weakest first, or null for a rare.
 *
 * The client's item codes carry this outright: a series occupies one code prefix and its low
 * byte counts up through the commons, so 000100-000104 is Saber, Brand, Buster, Pallasch,
 * Gladius, and 000105 onward in the same prefix is where that series' rares live. Reading the
 * tier off the code means every one of the 901 weapons is placed without a name table.
 */
val WeaponTier.photonTier: Int?
    get() {
        val low = code.takeLast(2).toIntOrNull(16) ?: return null
        return if (low in 0..4) low + 1 else null
    }

/** A true rare: a red box, and 9 stars or better. */
val WeaponTier.isRare: Boolean get() = stars >= 9

/**
 * The photon colour a common weapon glows with, which is how PSO states a common's tier on the
 * model itself: green, blue, purple, red, gold. Technique weapons stop at red -- they have four
 * tiers, not five -- so a gold cane is simply never reached.
 */
val WeaponTier.photonColor: Int?
    get() = when (photonTier) {
        1 -> 0x44ff66
        2 -> 0x3d7bff
        3 -> 0xa64dff
        4 -> 0xff3b3b
        5 -> if (type == WeaponType.ROD || type == WeaponType.WAND || type == WeaponType.CANE) {
            0xff3b3b
        } else {
            0xffc832
        }
        else -> null
    }

/**
 * TESTING AID: the `?arms?` chat command's grant -- the base tier of every line plus each
 * specialty, one weapon per animation class.
 */
val TESTING_ARMORY: List<WeaponTier> =
    listOf(
        SABER_LINE, SWORD_LINE, DAGGER_LINE, PARTISAN_LINE, SLICER_LINE,
        HANDGUN_LINE, RIFLE_LINE, MECHGUN_LINE, SHOT_LINE,
        CANE_LINE, ROD_LINE, WAND_LINE,
    ).map { it.first() } + SPECIALTY_TIERS


/**
 * The special-attack families a dropped weapon can carry, with the wiki's activation powers per
 * tier where the family has them. Families whose costs need systems that don't exist yet (Charge
 * needs meseta, Spirit and the TP drains need a spendable TP pool, EXP steal needs levelling)
 * are deliberately absent from [rollSpecial].
 */
enum class SpecialFamily {
    FIRE, LIGHTNING, FREEZE, PARALYSIS, CONFUSION, INSTANT_KILL, HP_DRAIN, HP_CUT, BERSERK,
}

class WeaponSpecial(val family: SpecialFamily, val tier: Int, val name: String) {
    /** Activation power / percentage for the chance- and drain-based families. */
    val power: Int
        get() = when (family) {
            SpecialFamily.FREEZE, SpecialFamily.PARALYSIS -> intArrayOf(32, 48, 64, 80)[tier - 1]
            SpecialFamily.CONFUSION -> intArrayOf(28, 44, 60, 76)[tier - 1]
            SpecialFamily.INSTANT_KILL -> intArrayOf(48, 66, 78, 93)[tier - 1]
            SpecialFamily.HP_DRAIN -> intArrayOf(5, 9, 13, 17)[tier - 1]
            SpecialFamily.HP_CUT -> intArrayOf(0, 0, 50, 75)[tier - 1]
            else -> 0
        }

    /** Flat elemental damage at character level [level] -- fire and lightning share the curve. */
    fun elementalDamage(level: Int): Int = when (tier) {
        1 -> (level - 1) / 4 + 40
        2 -> (level - 1) / 3 + 60
        3 -> (level - 1) / 2 + 80
        else -> (level - 1) + 100
    }
}

fun weaponSpecial(family: SpecialFamily, tier: Int): WeaponSpecial {
    val name = when (family) {
        SpecialFamily.FIRE -> arrayOf("Heat", "Fire", "Flame", "Burning")[tier - 1]
        SpecialFamily.LIGHTNING -> arrayOf("Shock", "Thunder", "Storm", "Tempest")[tier - 1]
        SpecialFamily.FREEZE -> arrayOf("Ice", "Frost", "Freeze", "Blizzard")[tier - 1]
        SpecialFamily.PARALYSIS -> arrayOf("Bind", "Hold", "Seize", "Arrest")[tier - 1]
        SpecialFamily.CONFUSION -> arrayOf("Panic", "Riot", "Havoc", "Chaos")[tier - 1]
        SpecialFamily.INSTANT_KILL -> arrayOf("Dim", "Shadow", "Dark", "Hell")[tier - 1]
        SpecialFamily.HP_DRAIN -> arrayOf("Draw", "Drain", "Fill", "Gush")[tier - 1]
        SpecialFamily.HP_CUT -> arrayOf("", "", "Devil's", "Demon's")[tier - 1]
        SpecialFamily.BERSERK -> "Berserk"
    }
    return WeaponSpecial(family, tier, name)
}

/**
 * One dropped (or equipped) weapon. [atpMin]/[atpMax] follow the wiki's rules: grind adds 2 ATP
 * per point to the *minimum*, and the per-swing roll spans the fixed spread on top of it.
 */
class WeaponItem(
    val tier: WeaponTier,
    val grind: Int = 0,
    val specialAttack: WeaponSpecial? = null,
    /** A rare fresh off the ground: unappraised, unnamed, unequippable until the Tekker. */
    val unidentified: Boolean = false,
) {
    val atpMin: Int get() = tier.atpMin + grind * 2
    val atpSpread: Int get() = tier.atpMax - tier.atpMin

    /** The weapon ATP one particular swing contributes -- min plus the variance roll. */
    fun rollAtp(random: Double): Int = atpMin + (random * (atpSpread + 1)).toInt()

    val displayName: String
        get() =
            if (unidentified) "????"
            else buildString {
                specialAttack?.let { append(it.name); append(' ') }
                append(tier.name)
                if (grind > 0) append(" +").append(grind)
            }

    /** What the Tekker's appraisal reveals -- the same item, named. */
    fun identified(): WeaponItem = WeaponItem(tier, grind, specialAttack, unidentified = false)
}

/**
 * Special-reduction multiplier per the wiki: multi-target/multi-hit types pay for their coverage
 * on every chance/drain/cut special (never on elemental damage or sacrificial). The Forest's
 * droppable lines are all unreduced; the table exists for when other types drop.
 */
fun specialEffectiveness(type: WeaponType): Double = when (type) {
    WeaponType.SWORD, WeaponType.DAGGER, WeaponType.PARTISAN -> 0.5
    WeaponType.SLICER, WeaponType.MECHGUN, WeaponType.SHOT -> 1.0 / 3.0
    else -> 1.0
}

/**
 * The common weapon drop table from the wiki's Section IDs page: when a common weapon drops, its
 * type is chosen by the party's ID with these percentages (every row sums to 100). Sabers,
 * handguns and canes are every ID's 13% staples; the other columns are where each ID's theme
 * lives -- Skyly's swords, Purplenum's mechguns, Whitill's slicers, Bluefull's rods.
 *
 * Columns in [WEAPON_TABLE_COLUMNS] (wiki) order.
 */
private val WEAPON_TABLE_COLUMNS = listOf(
    WeaponType.SABER, WeaponType.SWORD, WeaponType.DAGGER, WeaponType.PARTISAN,
    WeaponType.SLICER, WeaponType.HANDGUN, WeaponType.RIFLE, WeaponType.MECHGUN,
    WeaponType.SHOT, WeaponType.CANE, WeaponType.ROD, WeaponType.WAND,
)

private val SECTION_ID_WEAPON_WEIGHTS: Map<SectionId, IntArray> = mapOf(
    SectionId.Viridia to intArrayOf(13, 6, 7, 10, 1, 13, 6, 6, 11, 13, 7, 7),
    SectionId.Greenill to intArrayOf(13, 1, 10, 6, 6, 13, 13, 7, 4, 13, 7, 7),
    SectionId.Skyly to intArrayOf(13, 13, 7, 6, 6, 13, 10, 1, 4, 13, 7, 7),
    SectionId.Bluefull to intArrayOf(13, 7, 6, 13, 6, 13, 7, 7, 4, 13, 10, 1),
    SectionId.Purplenum to intArrayOf(13, 3, 10, 3, 6, 13, 7, 13, 5, 13, 7, 7),
    SectionId.Pinkal to intArrayOf(13, 6, 7, 10, 6, 13, 1, 7, 4, 13, 7, 13),
    SectionId.Redria to intArrayOf(13, 7, 1, 7, 10, 13, 7, 7, 8, 13, 7, 7),
    SectionId.Oran to intArrayOf(13, 8, 13, 7, 6, 13, 7, 7, 4, 13, 1, 8),
    SectionId.Yellowboze to intArrayOf(13, 7, 7, 7, 7, 13, 7, 7, 5, 13, 7, 7),
    SectionId.Whitill to intArrayOf(13, 6, 6, 6, 13, 13, 6, 10, 1, 13, 7, 6),
)

/**
 * Chooses a dropped weapon's type by the party's section ID, renormalized over [available]: the
 * wiki notes most types simply don't appear in early areas on Normal difficulty, so their
 * probability mass redistributes across the types that do.
 */
fun rollWeaponType(sectionId: SectionId, available: Set<WeaponType>): WeaponType {
    val weights = SECTION_ID_WEAPON_WEIGHTS.getValue(sectionId)

    var total = 0
    for ((i, type) in WEAPON_TABLE_COLUMNS.withIndex()) {
        if (type in available) total += weights[i]
    }

    var roll = Random.nextInt(total)
    for ((i, type) in WEAPON_TABLE_COLUMNS.withIndex()) {
        if (type !in available) continue
        roll -= weights[i]
        if (roll < 0) return type
    }
    return WeaponType.SABER
}

/** The types that drop in a Normal-difficulty Forest -- the three staple lines, per the wiki. */
private val FOREST_NORMAL_TYPES = setOf(WeaponType.SABER, WeaponType.HANDGUN, WeaponType.CANE)

/**
 * Rolls the weapon a Normal-difficulty Forest drop produces. The type comes from the real
 * per-Section-ID table above (uniform across the Forest's three staple lines for every ID --
 * the IDs' themes only bite once later areas introduce the other columns). Still approximate:
 * the special comes from a flat roll rather than the game's per-area pattern table -- the
 * Forest yields tier 0 with a minority of tier 1, and specials appear on a third of drops at
 * 1-2 stars (plus Berserk, the one sacrificial whose cost -- HP -- exists).
 */
fun rollForestWeaponDrop(sectionId: SectionId): WeaponItem {
    val line = when (rollWeaponType(sectionId, FOREST_NORMAL_TYPES)) {
        WeaponType.HANDGUN -> HANDGUN_LINE
        WeaponType.CANE -> CANE_LINE
        else -> SABER_LINE
    }
    val tier = if (Random.nextDouble() < 0.25) line[1] else line[0]

    val specialAttack = if (Random.nextDouble() < 0.33) {
        val family = SpecialFamily.entries.filter { it != SpecialFamily.HP_CUT }.random()
        val specialTier = when (family) {
            // Berserk is only ever 4-star; everything else drops low-tier here.
            SpecialFamily.BERSERK -> 4
            else -> if (Random.nextDouble() < 0.3) 2 else 1
        }
        weaponSpecial(family, specialTier)
    } else null

    return WeaponItem(tier, grind = 0, specialAttack = specialAttack)
}
