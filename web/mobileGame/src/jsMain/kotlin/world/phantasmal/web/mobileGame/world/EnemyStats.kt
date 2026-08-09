package world.phantasmal.web.mobileGame.world

/**
 * One species' combat numbers, from Episode 1 / Normal / One Person -- the difficulty and party
 * size this game runs at, per the wiki's Monsters table (the user's chosen reference).
 *
 * An earlier revision of this table accidentally carried the *multiplayer* Normal column while
 * claiming to be One Person -- a Booma at 92 hp / 106 atp against the real solo 60 / 80 -- which
 * inflated both damage taken and fight length by a third across the board. Every figure below is
 * now the One Person column verbatim.
 *
 * [experience] feeds levelling on every kill, [dropRate] gates the drop roll (see
 * DropTables.kt), and [resistances] wait on elemental damage.
 */
class EnemyStats(
    val hp: Int,
    val atp: Int,
    val dfp: Int,
    val ata: Int,
    /** Evasion. The player's accuracy is their ATA less a fifth of this -- see accuracyPercent. */
    val evp: Int,
    /** Luck. Monsters crit at half their luck as a percentage, twice the player's rate. */
    val lck: Int,
    /**
     * Radius of the upright cylinder this species occupies, in PSO units, where the player's own
     * is 1.0. Collisions are checked centre-to-centre, so a wider cylinder is genuinely easier to
     * hit -- a Monest's 3.0 makes it a wall that even a dagger reaches, a Mothmant's 0.4 makes it
     * something narrow weapons slip past.
     */
    val hitboxRadius: Double,
    /**
     * How close, centre to centre, this species gets before it stops and strikes. Measured from
     * the player's cylinder edge, so it is directly comparable with a weapon's reach.
     */
    val attackRange: Double,
    /**
     * Rooted in place: never chases, never strikes. Only the Monest hive, whose threat is the
     * Mothmants it produces rather than the hive itself -- note its ATP of 0.
     */
    val isStationary: Boolean = false,
    /** How far above the ground this species flies, in PSO units. Zero for everything walking. */
    val hoverUnits: Double = 0.0,
    /**
     * Uniform multiplier on the model's own size. The bosses' authored meshes come out of the
     * shared enemy pipeline at roughly field-enemy proportions, but the real Dragon towers over
     * the player -- knee-height at best in the original fight. Applied to the spawned mesh, so
     * clips and skinning scale with it; the gameplay ranges above are stated at the *scaled*
     * size and must be authored to match.
     */
    val modelScale: Double = 1.0,
    val experience: Int = 0,
    /** Percentage chance this species drops anything at all. */
    val dropRate: Int = 0,
    val resistances: Resistances = Resistances(),
)

/** Percentage damage reduction per element. Recorded for when elemental damage exists. */
class Resistances(
    val fire: Int = 0,
    val ice: Int = 0,
    val thunder: Int = 0,
    val dark: Int = 0,
    val light: Int = 0,
    val special: Int = 0,
)

/**
 * The Forest's enemies. Keyed by the slugs the models and spawn tables use, which don't always
 * match the in-game names: "Rappy" is the Rag Rappy, and "Mothmant" is what a Monest sends out.
 *
 * Al Rappy and Hildeblue are the Forest's rare variants -- roughly 1 in 500 spawns in the real
 * game, and far stronger than what they replace. Nothing rolls for them yet; they appear only
 * where the map data places them outright.
 */
val FOREST_ENEMY_STATS: Map<String, EnemyStats> = mapOf(
    "Rappy" to EnemyStats(
        hp = 30, atp = 65, dfp = 10, ata = 70, evp = 80, lck = 10,
        hitboxRadius = 1.0, attackRange = 1.0, experience = 4, dropRate = 100,
        resistances = Resistances(ice = 20, thunder = 50, dark = 10, light = 45),
    ),
    "AlRappy" to EnemyStats(
        hp = 150, atp = 150, dfp = 10, ata = 100, evp = 5, lck = 5,
        hitboxRadius = 1.0, attackRange = 1.0, experience = 100, dropRate = 100,
        resistances = Resistances(ice = 50, thunder = 20, dark = 35, light = 45),
    ),
    "SavageWolf" to EnemyStats(
        hp = 45, atp = 85, dfp = 20, ata = 90, evp = 60, lck = 8,
        hitboxRadius = 1.1, attackRange = 2.0, experience = 5, dropRate = 30,
        resistances = Resistances(ice = 50, thunder = 10, dark = 10, light = 20, special = 5),
    ),
    "BarbarousWolf" to EnemyStats(
        hp = 65, atp = 95, dfp = 25, ata = 90, evp = 65, lck = 10,
        hitboxRadius = 1.1, attackRange = 2.0, experience = 7, dropRate = 45,
        resistances = Resistances(fire = 10, ice = 60, dark = 15, light = 20, special = 5),
    ),
    "Booma" to EnemyStats(
        hp = 60, atp = 80, dfp = 0, ata = 60, evp = 60, lck = 8,
        hitboxRadius = 1.2, attackRange = 1.4, experience = 5, dropRate = 28,
        resistances = Resistances(ice = 25, thunder = 15, dark = 10, light = 20),
    ),
    "GoBooma" to EnemyStats(
        hp = 85, atp = 85, dfp = 5, ata = 65, evp = 68, lck = 8,
        hitboxRadius = 1.2, attackRange = 1.4, experience = 6, dropRate = 32,
        resistances = Resistances(fire = 15, ice = 35, dark = 10, light = 20),
    ),
    "GigaBooma" to EnemyStats(
        hp = 110, atp = 90, dfp = 30, ata = 75, evp = 75, lck = 5,
        hitboxRadius = 1.4, attackRange = 1.6, experience = 7, dropRate = 35,
        resistances = Resistances(fire = 45, thunder = 15, dark = 15, light = 20),
    ),
    "Mothmant" to EnemyStats(
        hp = 8, atp = 53, dfp = 0, ata = 75, evp = 20, lck = 20,
        hitboxRadius = 0.4, attackRange = 0.8, experience = 1, dropRate = 25,
        // Mothmants fly at the height of the thing they're attacking -- head and shoulder, not
        // ankle. They spill out of the hive's crown and come down onto the player from there.
        hoverUnits = 4.6,
        resistances = Resistances(ice = 25, dark = 10, light = 20),
    ),
    // A hive: no attack power, no accuracy, no evasion. It produces Mothmants and soaks damage.
    "Monest" to EnemyStats(
        hp = 300, atp = 0, dfp = 0, ata = 0, evp = 0, lck = 0,
        hitboxRadius = 3.0, attackRange = 0.0, isStationary = true,
        experience = 6, dropRate = 0,
        resistances = Resistances(ice = 30, dark = 10, light = 20),
    ),
    "Hildebear" to EnemyStats(
        hp = 180, atp = 140, dfp = 30, ata = 80, evp = 22, lck = 10,
        hitboxRadius = 1.6, attackRange = 1.8, experience = 10, dropRate = 80,
        resistances = Resistances(fire = 70, thunder = 30, dark = 28, light = 50, special = 20),
    ),
    // The Dragon: combat numbers ride in from the generated battle params (1300 HP / 160 ATP
    // on Normal Solo); this entry carries only what the params don't -- the body. The radius
    // makes it the wall it should be; reach approximates its claw sweep.
    "Dragon" to EnemyStats(
        hp = 1300, atp = 160, dfp = 0, ata = 200, evp = 0, lck = 8,
        // Ranges at the scaled size: the body is a tower the player fights at ankle level.
        hitboxRadius = 9.0, attackRange = 9.0, experience = 350, dropRate = 100,
        modelScale = 2.5,
    ),
    "Hildeblue" to EnemyStats(
        hp = 250, atp = 200, dfp = 0, ata = 100, evp = 115, lck = 10,
        hitboxRadius = 1.6, attackRange = 1.8, experience = 100, dropRate = 100,
        resistances = Resistances(ice = 70, thunder = 30, dark = 38, light = 50, special = 20),
    ),
)

/**
 * Stand-in for a species with no real numbers yet -- everything outside the Forest. Deliberately
 * conspicuous rather than plausible: anything still using it should stand out as unfinished
 * instead of blending in as balanced.
 */
val DEFAULT_ENEMY_STATS = EnemyStats(
    hp = 50, atp = 50, dfp = 0, ata = 50, evp = 0, lck = 0,
    hitboxRadius = 1.0, attackRange = 1.4,
)

/**
 * Model slug -> the client's own enemy name in the generated battle params. Covers every
 * Episode 1 species this game ships a model for; the rare-variant and Ultimate-rename models
 * map to their base species where the base is what Normal mode fights.
 */
private val NEWSERV_NAME_FOR_SLUG: Map<String, String> = mapOf(
    // Forest
    "Booma" to "BOOMA", "GoBooma" to "GOBOOMA", "GigaBooma" to "GIGOBOOMA",
    "Rappy" to "RAG_RAPPY", "AlRappy" to "AL_RAPPY",
    "SavageWolf" to "SAVAGE_WOLF", "BarbarousWolf" to "BARBAROUS_WOLF",
    "Monest" to "MONEST", "Mothmant" to "MOTHMANT",
    "Hildebear" to "HILDEBEAR", "Hildeblue" to "HILDEBLUE",
    // Caves
    "EvilShark" to "EVIL_SHARK", "PalShark" to "PAL_SHARK", "GuilShark" to "GUIL_SHARK",
    "PoisonLily" to "POISON_LILY", "NarLily" to "NAR_LILY",
    "NanoDragoon" to "NANO_DRAGON", "GrassAssasin" to "GRASS_ASSASSIN",
    "PofuillySlimeBlue" to "POFUILLY_SLIME", "PouillySlimeRed" to "POUILLY_SLIME",
    "PanArms" to "PAN_ARMS", "Hidoom" to "HIDOOM", "Migium" to "MIGIUM",
    // Mines
    "Gilchic" to "GILLCHIC", "Dubchic" to "DUBCHIC", "Canadine" to "CANADINE",
    "Canane" to "CANANE", "SinowBeat" to "SINOW_BEAT", "SinowGold" to "SINOW_GOLD",
    "Garanz" to "GARANZ",
    // Ruins
    "Dimenian" to "DIMENIAN", "LaDimenian" to "LA_DIMENIAN", "SoDimenian" to "SO_DIMENIAN",
    "DarkBelra" to "DARK_BELRA", "ChaosSorcerer" to "CHAOS_SORCERER",
    "DarkGunner" to "DARK_GUNNER", "DarkBringer" to "CHAOS_BRINGER",
    "ChaosBringer" to "CHAOS_BRINGER", "Delsaber" to "DELSABER",
    "BulclawClosed" to "BULCLAW", "BulclawOpen" to "BULCLAW", "Claw" to "CLAW",
    // Bosses
    "Dragon" to "DRAGON", "DeRolLe" to "DE_ROL_LE", "VolOpt" to "VOL_OPT_2",
    "DarkFalzForm1Body" to "DARK_FALZ_1", "DarkFalzForm2Body" to "DARK_FALZ_2",
    "DarkFalzForm3Body" to "DARK_FALZ_3",
)

/** The client's name for a species, for joining the generated tables (drops, stats). */
fun newservEnemyName(slug: String): String? = NEWSERV_NAME_FOR_SLUG[slug]

private val mergedStatsCache = mutableMapOf<String, EnemyStats>()

/**
 * This species' numbers: combat stats, EXP and elemental resistances from the client's own
 * battle params (Episode 1, Solo, Normal) where the species is mapped, laid over the hand-tuned
 * geometry and behaviour above -- hitbox, reach, hover, stationariness and drop gating are this
 * project's play-tested values, not things the battle params carry. Species with neither a
 * mapping nor a hand entry get [DEFAULT_ENEMY_STATS], which is deliberately conspicuous.
 */
fun enemyStats(slug: String): EnemyStats = mergedStatsCache.getOrPut(slug) {
    val hand = FOREST_ENEMY_STATS[slug] ?: DEFAULT_ENEMY_STATS
    val row = NEWSERV_NAME_FOR_SLUG[slug]?.let { GeneratedEnemyStats.normal[it] }
        ?: return@getOrPut hand
    EnemyStats(
        hp = row.hp,
        atp = row.atp,
        dfp = row.dfp,
        ata = row.ata,
        evp = row.evp,
        lck = row.lck,
        hitboxRadius = hand.hitboxRadius,
        attackRange = hand.attackRange,
        isStationary = hand.isStationary,
        hoverUnits = hand.hoverUnits,
        modelScale = hand.modelScale,
        experience = row.exp,
        dropRate = hand.dropRate,
        resistances = Resistances(
            fire = row.efr,
            ice = row.eic,
            thunder = row.eth,
            dark = row.edk,
            light = row.elt,
            special = hand.resistances.special,
        ),
    )
}
