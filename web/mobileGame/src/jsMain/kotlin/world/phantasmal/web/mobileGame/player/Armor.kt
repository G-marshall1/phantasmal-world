package world.phantasmal.web.mobileGame.player

import kotlin.random.Random

/**
 * The armor system. Specs come from the BB client's own ItemPMT (GeneratedItemCatalog.kt):
 * every frame and barrier in the game with its true stat ranges, level requirements and star
 * ratings.
 *
 * Frames are body armor with zero to four unit slots; barriers are shields; units slot into a
 * frame and grant flat stat bonuses. DFP and EVP are *ranges* in the real game -- every dropped
 * or bought piece rolls its own values -- and that roll is reproduced here at acquisition.
 * Prices are this project's own curve (the game prices armor through sale divisors this shop
 * doesn't model), scaled like the arms shop's.
 */
class FrameSpec(
    val name: String,
    val dfpMin: Int,
    val dfpMax: Int,
    val evpMin: Int,
    val evpMax: Int,
    val levelReq: Int,
    val price: Int,
    val stars: Int = 0,
)

/**
 * This project's own price anchors for the pieces that had hand prices before the generated
 * catalogue arrived; everything else prices off its level requirement on the same curve.
 */
private val ARMOR_PRICE_ANCHORS: Map<String, Int> = mapOf(
    "Frame" to 50, "Armor" to 150, "Psy Armor" to 400, "Giga Frame" to 900,
    "Soul Frame" to 1_800, "Cross Armor" to 3_200, "Solid Frame" to 5_400,
    "Brave Armor" to 8_500, "Hyper Frame" to 12_500, "Grand Armor" to 17_500,
    "Shock Frame" to 24_000, "King's Frame" to 32_000,
    "Barrier" to 40, "Shield" to 120, "Core Shield" to 350, "Giga Shield" to 800,
    "Soul Barrier" to 1_600, "Hard Shield" to 2_900, "Brave Barrier" to 5_000,
    "Solid Shield" to 7_800, "Flame Barrier" to 11_500, "Plasma Barrier" to 16_000,
)

private fun armorPriceFor(name: String, levelReq: Int): Int =
    ARMOR_PRICE_ANCHORS[name] ?: (40 + levelReq * levelReq * 21)

/** Every frame in the game, weakest first -- stats and level gates from the client's data. */
val FRAME_SPECS: List<FrameSpec> = GeneratedItemCatalog.frames
    .map { a ->
        FrameSpec(
            titleCased(a.name),
            dfpMin = a.dfp, dfpMax = a.dfp + a.dfpRange,
            evpMin = a.evp, evpMax = a.evp + a.evpRange,
            levelReq = a.levelReq,
            price = armorPriceFor(a.name, a.levelReq),
            stars = a.stars,
        )
    }
    .sortedWith(compareBy({ it.levelReq }, { it.stars }, { it.dfpMin }))

class BarrierSpec(
    val name: String,
    val dfpMin: Int,
    val dfpMax: Int,
    val evpMin: Int,
    val evpMax: Int,
    val levelReq: Int,
    val price: Int,
    val stars: Int = 0,
)

/** Every barrier in the game, weakest first. [RECOVERY_BARRIER_SPEC] is the Forest's rare drop. */
val BARRIER_SPECS: List<BarrierSpec> = GeneratedItemCatalog.barriers
    .map { a ->
        BarrierSpec(
            titleCased(a.name),
            dfpMin = a.dfp, dfpMax = a.dfp + a.dfpRange,
            evpMin = a.evp, evpMax = a.evp + a.evpRange,
            levelReq = a.levelReq,
            price = armorPriceFor(a.name, a.levelReq),
            stars = a.stars,
        )
    }
    .sortedWith(compareBy({ it.levelReq }, { it.stars }, { it.dfpMin }))

/**
 * The 9-star rare from the Pinkal Savage Wolf chart: a technique barrier (the base of Recovery
 * merges in BB). Its merge system doesn't exist here yet, so it wears as the trophy shield it
 * is -- ordinary Barrier-class stats, extraordinary rarity.
 */
val RECOVERY_BARRIER_SPEC = BarrierSpec("Recovery Barrier", 2, 7, 25, 30, 0, 40)

/** One concrete frame: its rolled stats and how many unit slots it came with. */
class FrameItem(val spec: FrameSpec, val dfp: Int, val evp: Int, val slots: Int) {
    val displayName: String get() = "${spec.name}  [${slots}S]"
    val detail: String get() = "DFP $dfp  EVP $evp  ·  Lv${spec.levelReq}+"
}

class BarrierItem(val spec: BarrierSpec, val dfp: Int, val evp: Int) {
    val displayName: String get() = spec.name
    val detail: String get() = "DFP $dfp  EVP $evp  ·  Lv${spec.levelReq}+"
}

/** Slot odds for a fresh frame: most carry one or none, four is a prize. */
fun rollFrame(spec: FrameSpec, random: Random = Random): FrameItem {
    val slotRoll = random.nextDouble()
    val slots = when {
        slotRoll < 0.30 -> 0
        slotRoll < 0.65 -> 1
        slotRoll < 0.90 -> 2
        slotRoll < 0.98 -> 3
        else -> 4
    }
    return FrameItem(
        spec,
        dfp = spec.dfpMin + random.nextInt(spec.dfpMax - spec.dfpMin + 1),
        evp = spec.evpMin + random.nextInt(spec.evpMax - spec.evpMin + 1),
        slots = slots,
    )
}

fun rollBarrier(spec: BarrierSpec, random: Random = Random): BarrierItem = BarrierItem(
    spec,
    dfp = spec.dfpMin + random.nextInt(spec.dfpMax - spec.dfpMin + 1),
    evp = spec.evpMin + random.nextInt(spec.evpMax - spec.evpMin + 1),
)

/**
 * Units, with the wiki's exact bonuses (General/Power +10 ATP, General/Arm +5 ATA, the 20-point
 * Legs/Body/HP/TP family, Knight and Marksman as the entry tiers). The Resist family's exact
 * percentages aren't published; the 3/6 pattern here is this project's own and marked so --
 * they're also inert until enemies deal elemental damage to the player.
 */
enum class UnitType(
    val uiName: String,
    val atp: Int = 0,
    val ata: Int = 0,
    val dfp: Int = 0,
    val evp: Int = 0,
    val mst: Int = 0,
    val hp: Int = 0,
    val tp: Int = 0,
    val resistPercent: Int = 0,
    val price: Int,
) {
    KNIGHT_POWER("Knight/Power", atp = 5, price = 300),
    GENERAL_POWER("General/Power", atp = 10, price = 800),
    MARKSMAN_ARM("Marksman/Arm", ata = 3, price = 300),
    GENERAL_ARM("General/Arm", ata = 5, price = 800),
    GENERAL_LEGS("General/Legs", evp = 20, price = 800),
    GENERAL_BODY("General/Body", dfp = 20, price = 800),
    GENERAL_HP("General/HP", hp = 20, price = 1_000),
    GENERAL_TP("General/TP", tp = 20, price = 1_000),
    GENERAL_MIND("General/Mind", mst = 10, price = 800),
    RESIST_FIRE("Resist/Fire", resistPercent = 3, price = 400),
    RESIST_FLAME("Resist/Flame", resistPercent = 6, price = 700),
    RESIST_LIGHT("Resist/Light", resistPercent = 3, price = 400),
    ;

    val detail: String
        get() = buildString {
            if (atp > 0) append("+$atp ATP  ")
            if (ata > 0) append("+$ata ATA  ")
            if (dfp > 0) append("+$dfp DFP  ")
            if (evp > 0) append("+$evp EVP  ")
            if (mst > 0) append("+$mst MST  ")
            if (hp > 0) append("+$hp HP  ")
            if (tp > 0) append("+$tp TP  ")
            if (resistPercent > 0) append("+$resistPercent% resist")
        }.trim()
}

fun frameSpecByName(name: String): FrameSpec? = FRAME_SPECS.find { it.name == name }

fun barrierSpecByName(name: String): BarrierSpec? =
    if (name == RECOVERY_BARRIER_SPEC.name) RECOVERY_BARRIER_SPEC
    else BARRIER_SPECS.find { it.name == name }

fun unitByName(name: String): UnitType? = UnitType.entries.find { it.name == name }

/**
 * The armor counter's stock: everything wearable now or within a few levels' reach. Rares (9
 * stars and up) never reach the shelf, exactly like the arms shop.
 */
fun armorShopFrames(playerLevel: Int): List<FrameSpec> =
    FRAME_SPECS.filter { it.stars < 9 && it.levelReq <= playerLevel + 4 }

fun armorShopBarriers(playerLevel: Int): List<BarrierSpec> =
    BARRIER_SPECS.filter { it.stars < 9 && it.levelReq <= playerLevel + 4 }

/** The units the counter deals in -- the entry tiers; the General family is drop-only. */
val UNIT_SHOP: List<UnitType> = listOf(UnitType.KNIGHT_POWER, UnitType.MARKSMAN_ARM)

fun frameSellPrice(item: FrameItem): Int = (shopPrice(item.spec.price) / 8).coerceAtLeast(1)
fun barrierSellPrice(item: BarrierItem): Int = (shopPrice(item.spec.price) / 8).coerceAtLeast(1)
fun unitSellPrice(unit: UnitType): Int = (shopPrice(unit.price) / 8).coerceAtLeast(1)
