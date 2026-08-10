package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.mobileGame.input.ItemIcon

/**
 * Which of PSO's item-category glyphs marks each kind of thing (see [ItemIcon] and the sprite
 * strip it indexes). The game never shipped per-item art -- a Saber and a Gladius share the
 * sword glyph -- so the icon says what a thing *is*, which is exactly what a bare name like
 * "Pallasch" or "Soul Barrier" doesn't.
 *
 * Monster parts ride the tool glyph because that's the tab they live in: PSO files Booma's
 * Right Arm and Rappy's Wing under Tools, not as their own category.
 */
val WeaponType.itemIcon: ItemIcon
    get() = when (this) {
        WeaponType.HANDGUN, WeaponType.RIFLE, WeaponType.MECHGUN,
        WeaponType.SHOT, WeaponType.LAUNCHER -> ItemIcon.RANGED

        WeaponType.CANE, WeaponType.ROD, WeaponType.WAND -> ItemIcon.CANE

        else -> ItemIcon.MELEE
    }

val WeaponItem.itemIcon: ItemIcon get() = tier.type.itemIcon

val ToolType.itemIcon: ItemIcon get() = ItemIcon.TOOL

val TreasureType.itemIcon: ItemIcon get() = ItemIcon.TOOL

/**
 * Cell in the per-tool strip (see HudSprites' TOOL_ICON_SHEET_URL): the strip is drawn in
 * ToolType declaration order, so the ordinal *is* the cell -- gen_tool_icons.py and this file
 * must agree, and the two trailing cells below are the ones past the enum.
 */
val ToolType.iconCell: Int get() = ordinal

/** The violet technique disk, first cell past the ToolType run. */
const val TECH_DISK_ICON_CELL = 23

/** The golden wing marking the trade trophies, last cell of the strip. */
val TreasureType.iconCell: Int get() = 24

val FrameItem.itemIcon: ItemIcon get() = ItemIcon.ARMOR

val BarrierItem.itemIcon: ItemIcon get() = ItemIcon.SHIELD

val UnitType.itemIcon: ItemIcon get() = ItemIcon.UNIT
