package world.phantasmal.web.mobileGame.input

/**
 * A single ripped PSO v2 (PC) UI sprite sheet (512x812, credit: Torchid09), sliced via CSS
 * background-position. Every coordinate below was measured off the actual PNG (per-scanline colour
 * classification + alpha connected-component analysis), not eyeballed.
 *
 * The bar sprites are the genuine PSO HUD bars -- chevron-pointed caps, cyan border, horizontal
 * scanline striping. They are never stretched to represent a value: a fill bar is drawn at its
 * fixed native width inside an `overflow:hidden` clip whose width carries the percentage, so the
 * artwork is revealed/clipped exactly like the real game drains its bars (see PlayerStatusPanel).
 */
const val HUD_SPRITE_SHEET_URL = "/assets/hud/pso-ui.png"
const val HUD_SHEET_WIDTH = 512
const val HUD_SHEET_HEIGHT = 812

private const val SHEET_WIDTH = HUD_SHEET_WIDTH
private const val SHEET_HEIGHT = HUD_SHEET_HEIGHT

class HudSprite(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * CSS for a sprite region scaled independently on each axis -- needed by the capsule bars' 3-slice
 * middle section, which stretches horizontally while keeping its vertical scale.
 */
fun hudSpriteStyleXY(sprite: HudSprite, srcX: Int, srcW: Int, scaleX: Double, scaleY: Double): String =
    "background-image:url($HUD_SPRITE_SHEET_URL);" +
        "background-position:${-srcX * scaleX}px ${-sprite.y * scaleY}px;" +
        "background-repeat:no-repeat;" +
        "background-size:${SHEET_WIDTH * scaleX}px ${SHEET_HEIGHT * scaleY}px;" +
        "width:${srcW * scaleX}px;height:${sprite.h * scaleY}px;" +
        "image-rendering:pixelated;flex:0 0 auto;"

/**
 * CSS for one sprite, optionally magnified by [scale]. `image-rendering:pixelated` keeps the
 * scanline striping crisp instead of smearing it through bilinear filtering when scaled up.
 */
fun hudSpriteStyle(sprite: HudSprite, scale: Double = 1.0): String =
    "background-image:url($HUD_SPRITE_SHEET_URL);" +
        "background-position:${-sprite.x * scale}px ${-sprite.y * scale}px;" +
        "background-repeat:no-repeat;" +
        "background-size:${SHEET_WIDTH * scale}px ${SHEET_HEIGHT * scale}px;" +
        "width:${sprite.w * scale}px;height:${sprite.h * scale}px;" +
        "image-rendering:pixelated;"

object HudSprites {
    /**
     * The rounded "capsule" status bars -- blue border, rounded caps, vertical dark->bright->dark
     * gradient core. These are the bars the in-game HUD actually uses (matching the reference
     * screenshot), not the angular chevron-capped bars higher up the sheet at y=98..178, which
     * belong to a different UI element.
     *
     * All three are 40x20 and stacked vertically at x=161. They are 3-sliced when drawn (8px
     * rounded cap each side, 24px horizontally-uniform middle that stretches) so a bar of any
     * length keeps round caps instead of stretching them into ellipses -- see
     * PlayerStatusPanel.capsuleBar.
     */
    val CAPSULE_TRACK = HudSprite(161, 663, 40, 20)
    val CAPSULE_ORANGE = HudSprite(161, 687, 40, 20)
    val CAPSULE_GREEN = HudSprite(161, 711, 40, 20)

    /** Source-pixel width of a capsule bar's rounded end cap, for 3-slicing. */
    const val CAPSULE_CAP = 8

    /**
     * The full-width chevron-capped status bars at the top of the sheet -- the strips the actual
     * in-game HP/TP readout draws, stacked with shared cyan borders (row bounds measured by
     * per-row dominant-colour scan: track 98-123, orange 124-150, green 151-177). Dark scanlined
     * track, orange low-HP fill, green fill; no blue variant ships, so TP tints the green one
     * exactly like the capsules did.
     */
    val CHEVRON_TRACK = HudSprite(0, 98, 190, 26)
    val CHEVRON_ORANGE = HudSprite(0, 124, 190, 27)
    val CHEVRON_GREEN = HudSprite(0, 151, 190, 27)

    /** 3-slice caps for the chevron bars: near-straight left edge, pointed right end. */
    const val CHEVRON_CAP_LEFT = 6
    const val CHEVRON_CAP_RIGHT = 16

    /**
     * Small colored hex "gem" icons -- the level badge (orange = self). Each gem is only 24x20,
     * noticeably smaller than the ~34px spacing between them, so an oversized crop box bleeds into
     * the neighbouring gem (this bit me once already: the badge rendered blue instead of orange).
     */
    val GEM_RED = HudSprite(4, 462, 24, 20)
    val GEM_GREEN = HudSprite(38, 462, 24, 20)
    val GEM_ORANGE = HudSprite(72, 462, 24, 20)
    val GEM_BLUE = HudSprite(106, 462, 24, 20)

    /**
     * A handful of the sheet's large hex action-icon frames (a uniform ~50x53 grid starting at
     * x=256,y=0), picked for visual clarity -- exact icon-to-slot semantics don't matter since
     * only [ACTION_ATTACK] is wired to a real action, the rest are decorative chrome matching the
     * reference screenshots' visual density (see ActionPalette.kt).
     */
    val ACTION_ATTACK = HudSprite(457, 327, 50, 53)

    /**
     * The sheet's hex icon-tile grid: five 50px columns from x=256, rows on a 43px stride from
     * y=0 -- measured off the sheet by connected-component detection, not assumed. An earlier
     * 54px row rhythm was wrong for every row below the first, so each tile crop carried a
     * slice of its vertical neighbours: the "other icons inside the icons".
     *
     * The layout is systematic -- columns are families, rows are tiers: Zonde/Foie/Barta down
     * columns 0-2, Recovery in column 3 (Resta/Reverser/Anti), Special in column 4
     * (Grants/Megid/Ryuker), with Deband/Shifta on row 3 and Zalure/Jellen on row 4. Every
     * assignment was pixel-matched against the wiki's own glyph images on this exact grid.
     */
    fun hexTile(col: Int, row: Int): HudSprite =
        HudSprite(256 + col * 50, row * 43, 50, 43)
    val ACTION_DECOR_1 = HudSprite(257, 327, 50, 53)
    val ACTION_DECOR_2 = HudSprite(307, 327, 50, 53)
    val ACTION_DECOR_3 = HudSprite(357, 327, 50, 53)
    val ACTION_DECOR_4 = HudSprite(407, 327, 50, 53)
}

/**
 * The four-hex action cluster, as its own pre-extracted image rather than a crop of the sheet.
 * Two rectangular crops in a row rendered wrong (clipped hexes, stray neighbouring art), and the
 * root cause is that no rectangle can be right: the sheet packs other elements *inside* the
 * cluster's bounding box (a "B" button marker sits between the top hex's shoulders). The asset
 * was therefore lifted by flood-filling the cluster's connected pixels onto transparency --
 * exactly the interlocked art, nothing else. Yellow rims the left hex, green the top, blue the
 * right, red the bottom.
 */
const val ACTION_CLUSTER_URL = "/assets/hud/pso-action-cluster.png"
const val ACTION_CLUSTER_W = 158
const val ACTION_CLUSTER_H = 120

/**
 * The four positions in the action cluster, as fractions of its width and height so hexes place
 * themselves correctly at any scale. Centres measured by rim-colour centroid on the extracted
 * image: columns 33.5/78.5/123.5, rows 41 (top), 66 (sides), 91 (bottom) -- a clean 45x25 grid,
 * which is what says the measurement is right.
 *
 * Named by position rather than by what they do, because what each one does is the player's
 * choice -- see GameAction and ActionPaletteConfig.
 */
enum class ActionHex(val cx: Double, val cy: Double, val ringColor: String) {
    LEFT(33.5 / 158, 66.0 / 120, "#d8c33a"),
    TOP(78.5 / 158, 41.0 / 120, "#3fae5a"),
    RIGHT(123.5 / 158, 66.0 / 120, "#4a7fe0"),

    /** Bottom centre, nearest the thumb. */
    BOTTOM(78.5 / 158, 91.0 / 120, "#e2564e"),
}

/**
 * PSO's item-category glyphs: one per kind of thing, not one per item -- the game never shipped
 * per-item icons (its menus list items by name), so these are the only item art in the files.
 *
 * Cut out of the UI sheet into their own strip because the two families are drawn differently
 * there: the eight item glyphs are dark art on a light plate inside a 1px border, while the chat
 * and emote glyphs are already light outlines on transparency. Normalising them to white-on-clear
 * makes both usable on the dark hexes; trying to do it in CSS washed one family out while leaving
 * the other's plate visible.
 *
 * Eleven 16x16 glyphs in one row, in [ItemIcon] order. The eleventh -- the laboratory flask
 * that marks tools -- was added later from the same grid: its cells sit on an 18px stride from
 * (162, 100), not the 16px one an earlier crop assumed, which is why a first attempt at it came
 * out as a sliver of the neighbouring cell.
 */
const val ITEM_ICON_SHEET_URL = "/assets/hud/pso-item-icons.png"
private const val ITEM_ICON_COUNT = 11

/** CSS for one item glyph, magnified by [scale]. */
fun itemIconStyle(icon: ItemIcon, scale: Double = 1.0): String =
    "background-image:url($ITEM_ICON_SHEET_URL);" +
        "background-position:${-icon.index * 16 * scale}px 0;" +
        "background-repeat:no-repeat;" +
        "background-size:${ITEM_ICON_COUNT * 16 * scale}px ${16 * scale}px;" +
        "width:${16 * scale}px;height:${16 * scale}px;" +
        "image-rendering:pixelated;"

enum class ItemIcon(val index: Int) {
    MELEE(0),
    RANGED(1),
    CANE(2),
    MAG(3),
    UNIT(4),
    MESETA(5),
    SHIELD(6),
    ARMOR(7),

    /** Speech bubble -- the chat action. */
    CHAT(8),

    /** Smiley -- emotes. */
    EMOTE(9),

    /** The laboratory flask: mates, fluids, atomizers, and the monster parts that trade. */
    TOOL(10),
}

/**
 * The per-tool glyphs: a Monofluid looks like a Monofluid, not like "MF" or the generic flask.
 *
 * PSO itself never shipped per-item art (see the note above [ItemIcon]), so these cells are
 * drawn for this game in the sheet glyphs' own 16x16 pixel style -- green medic canisters with
 * tier pips for the mates, blue flasks for the fluids, the crossed cure vials, the three
 * atomizers, per-stat gems for the materials, tiered gears for the grinders -- one cell per
 * ToolType in declaration order, then the technique disk and the trade-trophy wing (see
 * ItemIcons.kt for the index mapping). Generated by web/assets-generation/gen_tool_icons.py.
 */
const val TOOL_ICON_SHEET_URL = "/assets/hud/pso-tool-icons.png"
const val TOOL_ICON_CELLS = 25

/** CSS for one per-tool glyph, magnified by [scale] -- same recipe as [itemIconStyle]. */
fun toolIconStyle(cell: Int, scale: Double = 1.0): String =
    "background-image:url($TOOL_ICON_SHEET_URL);" +
        "background-position:${-cell * 16 * scale}px 0;" +
        "background-repeat:no-repeat;" +
        "background-size:${TOOL_ICON_CELLS * 16 * scale}px ${16 * scale}px;" +
        "width:${16 * scale}px;height:${16 * scale}px;" +
        "image-rendering:pixelated;"

/**
 * The attack glyphs from the UI sheet's action column: a heavy blade, a lighter one, and the same
 * blade wrapped in the concentric aura that marks a weapon's special.
 *
 * Cut into their own strip for the same reason as [ItemIcon] -- on the sheet they're red art
 * sitting on a black scanlined hex plate, and that plate has to go before they can be drawn on
 * hexes of our own. Normalised to white so each one takes its colour from the hex it sits in,
 * which is what lets any action be assigned to any slot.
 *
 * Three 48x48 glyphs in one row, in [ActionIcon] order.
 */
const val ACTION_ICON_SHEET_URL = "/assets/hud/pso-action-icons.png"
private const val ACTION_ICON_COUNT = 3

/**
 * Drawn via CSS mask rather than background-image so the glyph can take a solid colour: the
 * palette's attack glyphs are red in the real game, and the strip's art is normalised to white
 * (see the doc above). [color] defaults to that red.
 */
fun actionIconStyle(icon: ActionIcon, scale: Double = 1.0, color: String = "#e8362d"): String {
    val pos = "${-icon.index * 48 * scale}px 0"
    val size = "${ACTION_ICON_COUNT * 48 * scale}px ${48 * scale}px"

    return "-webkit-mask-image:url($ACTION_ICON_SHEET_URL);" +
        "mask-image:url($ACTION_ICON_SHEET_URL);" +
        "-webkit-mask-position:$pos;mask-position:$pos;" +
        "-webkit-mask-repeat:no-repeat;mask-repeat:no-repeat;" +
        "-webkit-mask-size:$size;mask-size:$size;" +
        "background:$color;" +
        "width:${48 * scale}px;height:${48 * scale}px;" +
        "image-rendering:pixelated;"
}

enum class ActionIcon(val index: Int) {
    /** The larger blade -- a heavy swing. */
    HEAVY(0),

    /** The lighter blade -- a normal swing. */
    NORMAL(1),

    /** The blade with the aura rings coming off it -- a weapon special. */
    SPECIAL(2),
}
