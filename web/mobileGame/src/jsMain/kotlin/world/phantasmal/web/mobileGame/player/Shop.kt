package world.phantasmal.web.mobileGame.player

/**
 * Pioneer 2's shops.
 *
 * Every table in this file (and Armor.kt's spec prices) is the FULL price book: tool prices are
 * the wiki's Shopping District table verbatim; weapon and armor prices are this project's own
 * curve, since the wiki's Price guide turned out to value rares in player-traded Photon Drops
 * and never publishes NPC meseta prices. Selling back pays an eighth (tools: half), floored
 * low. Rares famously sell for next to nothing in PSO, hence the flat [TREASURE_SELL_PRICE].
 *
 * While the economy is young the counters run an early-settlement sale: [SHOP_DISCOUNT] scales
 * every purchase, and the sell prices derived from purchases scale with it (so the discount
 * can't be arbitraged). Set it to 1.0 to restore the full book.
 */
const val SHOP_DISCOUNT = 0.1

/** The counter price after the early-settlement discount. */
fun shopPrice(fullPrice: Int): Int = (fullPrice * SHOP_DISCOUNT).toInt().coerceAtLeast(1)

val TOOL_SHOP: List<Pair<ToolType, Int>> = listOf(
    ToolType.MONOMATE to 50,
    ToolType.DIMATE to 300,
    ToolType.TRIMATE to 2_000,
    ToolType.MONOFLUID to 100,
    ToolType.DIFLUID to 500,
    ToolType.TRIFLUID to 3_600,
    ToolType.ANTIDOTE to 60,
    ToolType.ANTIPARALYSIS to 60,
    ToolType.TELEPIPE to 100,
    ToolType.SOL_ATOMIZER to 300,
    ToolType.MOON_ATOMIZER to 500,
    ToolType.STAR_ATOMIZER to 5_000,
)

val WEAPON_PRICES: Map<String, Int> = mapOf(
    "Saber" to 100, "Brand" to 1_500, "Buster" to 5_600, "Pallasch" to 12_000, "Gladius" to 24_000,
    "Handgun" to 150, "Autogun" to 1_400, "Lockgun" to 5_200, "Railgun" to 11_000, "Raygun" to 22_000,
    "Cane" to 120, "Stick" to 1_300, "Mace" to 5_000, "Club" to 10_500,
    "Sword" to 400, "Gigush" to 2_200, "Breaker" to 7_000, "Claymore" to 14_000, "Calibur" to 27_000,
    "Dagger" to 130, "Knife" to 1_400, "Blade" to 5_300, "Edge" to 11_200, "Ripper" to 22_500,
    "Partisan" to 350, "Halbert" to 2_000, "Glaive" to 6_600, "Berdys" to 13_000, "Gungnir" to 25_000,
    "Slicer" to 200, "Spinner" to 1_600, "Cutter" to 5_800, "Sawcer" to 12_000, "Diska" to 23_000,
    "Rifle" to 300, "Sniper" to 1_900, "Blaster" to 6_200, "Beam" to 12_500, "Laser" to 24_500,
    "Mechgun" to 250, "Assault" to 1_700, "Repeater" to 6_000, "Gatling" to 12_200, "Vulcan" to 24_000,
    "Shot" to 450, "Spread" to 2_400, "Cannon" to 7_400, "Launcher" to 14_500, "Arms" to 28_000,
    "Rod" to 280, "Pole" to 1_800, "Pillar" to 6_100, "Striker" to 12_300,
    "Wand" to 110, "Staff" to 1_200, "Baton" to 4_800, "Scepter" to 10_200,
    "Double Saber" to 8_000, "Claw" to 900, "Yamigarasu" to 30_000,
    "Twinkle Star" to 9_000, "Panzer Faust" to 12_000,
)

/**
 * What the arms shop stocks for a character of [playerLevel]: one more star of each line every
 * five levels, which is the wiki's "availability depends on character level" made concrete.
 * Every common line is carried now that any class can actually be drawn (runtime weapon-class
 * switching); the specialty rares stay off the shelf, as rares should.
 */
fun armsShopStock(playerLevel: Int): List<WeaponTier> =
    listOf(
        SABER_LINE, SWORD_LINE, DAGGER_LINE, PARTISAN_LINE, SLICER_LINE,
        HANDGUN_LINE, RIFLE_LINE, MECHGUN_LINE, SHOT_LINE,
        CANE_LINE, ROD_LINE, WAND_LINE,
    ).flatMap { line ->
        // BB's star ratings don't start every line at zero (a Sword is 1 star to a Saber's
        // zero), so the shelf unlocks by position within the line, not absolute stars.
        val baseStars = line.first().stars
        line.filter { it.stars - baseStars <= playerLevel / 5 }
    }

fun weaponBuyPrice(tier: WeaponTier): Int = shopPrice(WEAPON_PRICES[tier.name] ?: 100)

fun weaponSellPrice(item: WeaponItem): Int =
    (weaponBuyPrice(item.tier) / 8).coerceAtLeast(5)

/** What a rare trophy sells for -- PSO's famous insult of a price. */
const val TREASURE_SELL_PRICE = 10

/** Tools sell back at half the counter price; null for anything the shop doesn't deal in. */
fun toolSellPrice(tool: ToolType): Int? =
    TOOL_SHOP.find { it.first == tool }?.second?.let { (shopPrice(it) / 2).coerceAtLeast(1) }
