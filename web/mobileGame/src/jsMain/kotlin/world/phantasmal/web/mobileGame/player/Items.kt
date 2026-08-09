package world.phantasmal.web.mobileGame.player

/**
 * PSO's consumable tools, with the wiki's exact Normal-difficulty restoration values (the mates
 * and fluids scale with difficulty in the real game; this game runs at Normal). Every tool
 * stacks to 10 in the pack, per the wiki's Max Stack column.
 */
enum class ToolType(val uiName: String, val maxStack: Int = 10) {
    MONOMATE("Monomate"),
    DIMATE("Dimate"),
    TRIMATE("Trimate"),
    MONOFLUID("Monofluid"),
    DIFLUID("Difluid"),
    TRIFLUID("Trifluid"),
    ANTIDOTE("Antidote"),
    ANTIPARALYSIS("Antiparalysis"),
    SOL_ATOMIZER("Sol Atomizer"),
    MOON_ATOMIZER("Moon Atomizer"),
    STAR_ATOMIZER("Star Atomizer"),

    /** Opens a pipe back to Pioneer 2, and a matching pipe in town leading back here. */
    TELEPIPE("Telepipe"),

    /** Never used by hand: it fires by itself the moment its carrier falls. */
    SCAPE_DOLL("Scape Doll"),

    /** Permanently +2 ATP when consumed. */
    POWER_MATERIAL("Power Material"),

    /** Permanently +2 MST when consumed. */
    MIND_MATERIAL("Mind Material"),

    /** Permanently +2 max HP when consumed. */
    HP_MATERIAL("HP Material"),
    ;

    /**
     * HP one use restores at Normal difficulty, null if this isn't an HP item. Full heals
     * resolve against [maxHp] rather than carrying a magic number.
     */
    fun hpRestored(maxHp: Int): Int? = when (this) {
        MONOMATE -> 80
        DIMATE -> 200
        TRIMATE, STAR_ATOMIZER -> maxHp
        else -> null
    }

    /** TP one use restores at Normal difficulty, null if this isn't a TP item. */
    fun tpRestored(maxTp: Int): Int? = when (this) {
        MONOFLUID -> 70
        DIFLUID -> 200
        TRIFLUID -> maxTp
        else -> null
    }
}

/**
 * Rare trade trophies from the Forest drop charts -- the pieces with no use but their story.
 * The shields and units that used to sit here became real equipment when the armor system
 * arrived (see Armor.kt); saves that still hold them convert on load.
 */
enum class TreasureType(val uiName: String) {
    RAPPYS_WING("Rappy's Wing"),
    BOOMAS_RIGHT_ARM("Booma's Right Arm"),
    GOBOOMAS_RIGHT_ARM("Gobooma's Right Arm"),
}

/** Meseta carried caps at the real game's 999,999. */
const val MAX_MESETA = 999_999
