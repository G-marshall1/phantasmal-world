package world.phantasmal.web.mobileGame.player

import kotlin.random.Random
import world.phantasmal.web.mobileGame.world.enemyStats
import world.phantasmal.web.shared.dto.SectionId

/** One thing an enemy left behind. [rare] drops land as PSO's red box. */
sealed class Drop(val rare: Boolean) {
    class WeaponDrop(val item: WeaponItem, rare: Boolean = false) : Drop(rare)
    class ToolDrop(val tool: ToolType, rare: Boolean = false) : Drop(rare)
    class TreasureDrop(val treasure: TreasureType) : Drop(true)
    class MesetaDrop(val amount: Int) : Drop(false)
    class FrameDrop(val item: FrameItem, rare: Boolean = false) : Drop(rare)
    class BarrierDrop(val item: BarrierItem, rare: Boolean = false) : Drop(rare)
    class UnitDrop(val unit: UnitType, rare: Boolean = false) : Drop(rare)
}

/** One cell of the rare chart: what drops and its 1-in-[denominator] chance. */
private class RareSlot(val denominator: Double, val make: () -> Drop)

private fun tool(tool: ToolType, denominator: Double, rare: Boolean = false) =
    RareSlot(denominator) { Drop.ToolDrop(tool, rare) }

private fun treasure(t: TreasureType, denominator: Double) =
    RareSlot(denominator) { Drop.TreasureDrop(t) }

private fun unit(u: UnitType, denominator: Double) =
    RareSlot(denominator) { Drop.UnitDrop(u, rare = true) }

private fun recoveryBarrier(denominator: Double) =
    RareSlot(denominator) { Drop.BarrierDrop(rollBarrier(RECOVERY_BARRIER_SPEC), rare = true) }

private fun allIds(slot: RareSlot): Map<SectionId, RareSlot> =
    SectionId.entries.associateWith { slot }

/**
 * The Episode 1 / Normal / Forest rare chart, per enemy per section ID -- the drop-chart page
 * verbatim, flavour quirks included: a Savage Wolf carries a rare for Pinkal alone, a Gigobooma
 * hands out Sol Atomizers constantly except to Redria (whose slot is a 1-in-5201 Star instead),
 * a Barbarous Wolf's Scape Doll comes easily to Viridia and near-never to Bluefull, and a
 * Hildebear's "rare" slot is a Trimate half the time. The [tool]/[treasure] `rare` flags mark
 * which of these arrive as the red box; the everyday consumable slots don't.
 *
 * The rates are the real, brutal ones -- a Rappy's Wing at 1 in 7282 is the hunt, not a bug.
 */
private val FOREST_RARE_CHART: Map<String, Map<SectionId, RareSlot>> = mapOf(
    "Booma" to allIds(treasure(TreasureType.BOOMAS_RIGHT_ARM, 8_359.2)),
    "GoBooma" to allIds(treasure(TreasureType.GOBOOMAS_RIGHT_ARM, 20_480.0)),
    "GigaBooma" to SectionId.entries.associateWith { id ->
        if (id == SectionId.Redria) tool(ToolType.STAR_ATOMIZER, 5_201.3, rare = true)
        else tool(ToolType.SOL_ATOMIZER, 5.1)
    },
    "Rappy" to allIds(treasure(TreasureType.RAPPYS_WING, 7_281.8)),
    "SavageWolf" to mapOf(
        SectionId.Pinkal to recoveryBarrier(6_068.1),
    ),
    "BarbarousWolf" to mapOf(
        SectionId.Viridia to tool(ToolType.SCAPE_DOLL, 758.5, rare = true),
        SectionId.Greenill to tool(ToolType.STAR_ATOMIZER, 758.5, rare = true),
        SectionId.Skyly to tool(ToolType.SOL_ATOMIZER, 758.5, rare = true),
        SectionId.Bluefull to tool(ToolType.SCAPE_DOLL, 10_402.5, rare = true),
        SectionId.Purplenum to tool(ToolType.SOL_ATOMIZER, 758.5, rare = true),
        SectionId.Pinkal to tool(ToolType.SOL_ATOMIZER, 758.5, rare = true),
        SectionId.Redria to tool(ToolType.SOL_ATOMIZER, 758.5, rare = true),
        SectionId.Oran to tool(ToolType.STAR_ATOMIZER, 758.5, rare = true),
        SectionId.Yellowboze to tool(ToolType.SOL_ATOMIZER, 758.5, rare = true),
        SectionId.Whitill to tool(ToolType.SOL_ATOMIZER, 758.5, rare = true),
    ),
    "Hildebear" to allIds(tool(ToolType.TRIMATE, 2.2)),
    "Hildeblue" to mapOf(
        SectionId.Viridia to unit(UnitType.RESIST_FIRE, 1.1),
        SectionId.Greenill to tool(ToolType.POWER_MATERIAL, 1.1, rare = true),
        SectionId.Skyly to unit(UnitType.RESIST_FIRE, 1.1),
        SectionId.Bluefull to unit(UnitType.GENERAL_MIND, 1.1),
        SectionId.Purplenum to unit(UnitType.GENERAL_LEGS, 1.1),
        SectionId.Pinkal to unit(UnitType.RESIST_FLAME, 1.1),
        SectionId.Redria to unit(UnitType.RESIST_LIGHT, 1.1),
        SectionId.Oran to unit(UnitType.GENERAL_MIND, 1.1),
        SectionId.Yellowboze to unit(UnitType.RESIST_FIRE, 1.1),
        SectionId.Whitill to unit(UnitType.GENERAL_LEGS, 1.1),
    ),
    "AlRappy" to mapOf(
        SectionId.Viridia to tool(ToolType.MIND_MATERIAL, 1.1, rare = true),
        SectionId.Greenill to tool(ToolType.MIND_MATERIAL, 1.1, rare = true),
        SectionId.Skyly to tool(ToolType.MIND_MATERIAL, 1.1, rare = true),
        SectionId.Bluefull to tool(ToolType.HP_MATERIAL, 1.1, rare = true),
        SectionId.Purplenum to tool(ToolType.MIND_MATERIAL, 1.1, rare = true),
        SectionId.Pinkal to tool(ToolType.STAR_ATOMIZER, 1.1, rare = true),
        SectionId.Redria to tool(ToolType.TRIMATE, 1.1, rare = true),
        SectionId.Oran to tool(ToolType.HP_MATERIAL, 1.1, rare = true),
        SectionId.Yellowboze to tool(ToolType.TRIMATE, 1.8, rare = true),
        SectionId.Whitill to tool(ToolType.STAR_ATOMIZER, 1.1, rare = true),
    ),
)

/** The rare chart's item name for a species and ID, for tests and any future drop-info UI. */
fun forestRareDropName(slug: String, sectionId: SectionId): String? =
    FOREST_RARE_CHART[slug]?.get(sectionId)?.let { slot ->
        when (val drop = slot.make()) {
            is Drop.ToolDrop -> drop.tool.uiName
            is Drop.TreasureDrop -> drop.treasure.uiName
            is Drop.WeaponDrop -> drop.item.displayName
            is Drop.MesetaDrop -> "Meseta"
            is Drop.FrameDrop -> drop.item.spec.name
            is Drop.BarrierDrop -> drop.item.spec.name
            is Drop.UnitDrop -> drop.unit.uiName
        }
    }

/**
 * Rolls what a kill leaves behind. The gate is the species' own drop-anything rate from the
 * monster table, then the rare chart gets its real 1-in-N look, then the common fallback.
 *
 * The common split is a documented approximation (the real game's per-enemy category tables
 * aren't transcribed): 35% weapon by the section-ID type table, 35% tool weighted toward the
 * small recovery items, 30% meseta at Forest-Normal amounts -- with Yellowboze's "+ Meseta
 * drops" identity honoured as a bonus, its exact figure being unpublished.
 */
fun rollEnemyDrop(slug: String, sectionId: SectionId, random: Random = Random): Drop? {
    val stats = enemyStats(slug)
    if (random.nextDouble() * 100.0 >= stats.dropRate) return null

    FOREST_RARE_CHART[slug]?.get(sectionId)?.let { slot ->
        if (random.nextDouble() < 1.0 / slot.denominator) return slot.make()
    }

    // Species outside the hand-transcribed Forest chart (everything from the Caves on) roll
    // the vanilla BB chart instead -- see GeneratedRareDrops' header for why both exist.
    if (slug !in FOREST_RARE_CHART) {
        world.phantasmal.web.mobileGame.world.newservEnemyName(slug)?.let { enemyName ->
            GeneratedRareDrops.normal[sectionId.name]?.get(enemyName)?.forEach { cell ->
                if (random.nextDouble() < cell.numerator.toDouble() / cell.denominator) {
                    resolveGeneratedRare(cell)?.let { return it }
                }
            }
        }
    }

    val r = random.nextDouble()
    return when {
        r < 0.30 -> Drop.WeaponDrop(rollForestWeaponDrop(sectionId))
        r < 0.62 -> Drop.ToolDrop(rollCommonTool(random))
        r < 0.70 -> rollCommonArmor(random)
        else -> {
            val base = MESETA_MIN + random.nextInt(MESETA_MAX - MESETA_MIN + 1)
            val amount =
                if (sectionId == SectionId.Yellowboze) (base * YELLOWBOZE_MESETA_BONUS).toInt()
                else base
            Drop.MesetaDrop(amount)
        }
    }
}

/**
 * A vanilla-chart cell as a concrete drop, resolved through the generated item catalogue.
 * Cells naming things this game can't hand out yet (mags, rare-series weapons with no motion
 * set, unmodelled units) resolve to null and the roll falls through to the common table --
 * dropping nothing would punish the player for our gaps.
 */
private fun resolveGeneratedRare(cell: GeneratedRareDrop): Drop? {
    val titled = titleCased(cell.itemName)
    return when {
        cell.code.startsWith("0101") ->
            frameSpecByName(titled)?.let { Drop.FrameDrop(rollFrame(it), rare = true) }

        cell.code.startsWith("0102") ->
            barrierSpecByName(titled)?.let { Drop.BarrierDrop(rollBarrier(it), rare = true) }

        cell.code.startsWith("0103") ->
            UnitType.entries.find { it.uiName.equals(cell.itemName, ignoreCase = true) }
                ?.let { Drop.UnitDrop(it, rare = true) }

        cell.code.startsWith("00") ->
            weaponTierByName(titled)?.let { Drop.WeaponDrop(WeaponItem(it), rare = true) }

        cell.code.startsWith("03") -> {
            ToolType.entries.find { it.uiName.equals(cell.itemName, ignoreCase = true) }
                ?.let { return Drop.ToolDrop(it, rare = true) }
            TreasureType.entries.find { it.uiName.equals(cell.itemName, ignoreCase = true) }
                ?.let { Drop.TreasureDrop(it) }
        }

        else -> null
    }
}

/**
 * Common armor at Forest-Normal grade: the two starter tiers of frame and barrier, and mostly
 * the entry-tier units. The split within armor is this project's own approximation, like the
 * category split above it.
 */
private fun rollCommonArmor(random: Random): Drop {
    val r = random.nextDouble()
    return when {
        r < 0.40 -> Drop.FrameDrop(rollFrame(FRAME_SPECS[random.nextInt(2)], random))
        r < 0.75 -> Drop.BarrierDrop(rollBarrier(BARRIER_SPECS[random.nextInt(2)], random))
        else -> Drop.UnitDrop(
            listOf(
                UnitType.KNIGHT_POWER, UnitType.KNIGHT_POWER, UnitType.MARKSMAN_ARM,
                UnitType.MARKSMAN_ARM, UnitType.GENERAL_POWER, UnitType.GENERAL_BODY,
                UnitType.GENERAL_LEGS, UnitType.GENERAL_HP, UnitType.GENERAL_TP,
            ).random(random),
        )
    }
}

/**
 * What a smashed crate holds. Boxes lean toward consumables and pocket money -- they're the
 * between-fights top-up, not a second drop table -- and unlike an enemy they always hold
 * something, since an empty crate would just read as a broken interaction.
 */
fun rollBoxDrop(sectionId: SectionId, random: Random = Random): Drop {
    val r = random.nextDouble()
    return when {
        r < 0.44 -> Drop.ToolDrop(rollCommonTool(random))
        r < 0.74 -> Drop.MesetaDrop(BOX_MESETA_MIN + random.nextInt(BOX_MESETA_MAX - BOX_MESETA_MIN + 1))
        r < 0.90 -> Drop.WeaponDrop(rollForestWeaponDrop(sectionId))
        else -> rollCommonArmor(random)
    }
}

private const val BOX_MESETA_MIN = 6
private const val BOX_MESETA_MAX = 30

private fun rollCommonTool(random: Random): ToolType {
    val r = random.nextDouble()
    return when {
        r < 0.45 -> ToolType.MONOMATE
        r < 0.67 -> ToolType.MONOFLUID
        r < 0.79 -> ToolType.DIMATE
        r < 0.85 -> ToolType.DIFLUID
        r < 0.925 -> ToolType.ANTIDOTE
        else -> ToolType.ANTIPARALYSIS
    }
}

private const val MESETA_MIN = 4
private const val MESETA_MAX = 24
private const val YELLOWBOZE_MESETA_BONUS = 1.25
