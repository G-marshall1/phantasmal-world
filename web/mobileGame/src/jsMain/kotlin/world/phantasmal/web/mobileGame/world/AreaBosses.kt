package world.phantasmal.web.mobileGame.world

/** One boss-room guardian: a species and where it waits. */
class BossSpawn(
    val slug: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double = 0.0,
)

/**
 * An area's mini-boss and the way home it guards.
 *
 * PSO's field areas end at a boss room: clear it and the warp back to Pioneer 2 opens. That
 * closes the loop this game was missing -- a run had a beginning but no end, so there was
 * nothing to come back from and nothing to replay. Taking the warp rebuilds the area from
 * scratch (a fresh encounter layout, fresh drops), which is exactly how the real game gets its
 * replayability.
 */
class BossEncounter(
    /** The room the boss waits in -- see AreaSpawnTable's sections. */
    val sectionId: Int,
    val enemies: List<BossSpawn>,
    /** Where the warp leads. */
    val destinationMap: String,
    val arrivalMessage: String,
    val clearedMessage: String,
    /** Progression flag written to the save on the kill -- what the Ragol teleporter gates on. */
    val bossKey: String? = null,
)

/**
 * The Dragon, in its own arena stage behind Forest 2's boss teleporter -- the real Episode 1
 * flow (the Hildebear stand-in that used to close Forest 1 is retired; Forest 1's exit is the
 * warp to Forest 2 now). The arena has no set data (the client hardcodes its boss), so its
 * spawn table is synthesized (see loadAreaSpawnTable) with one section at the origin; the
 * placement below stands the Dragon across the arena from the player's arrival point.
 */
val AREA_BOSSES: Map<String, BossEncounter> = mapOf(
    "bossArea2" to BossEncounter(
        sectionId = 0,
        enemies = listOf(
            // The worm rises off the raft's +x flank; the fight controller owns it from the
            // first frame, so the spawn spot only decides where it surfaces.
            BossSpawn("DeRolLe", 75.0, 0.0, 0.0),
        ),
        destinationMap = "mines01",
        arrivalMessage = "Something enormous moves beneath the water...",
        clearedMessage = "De Rol Le sinks. The way to the Mines is open.",
        bossKey = "deRolLe",
    ),
    "bossArea4" to BossEncounter(
        sectionId = 0,
        enemies = listOf(
            // Both forms stand from the first frame; the humanoid waits out of sight until
            // the mount falls, and the exit stays shut until the second form dies.
            BossSpawn("DarkFalzForm1Body", 0.0, 0.0, 40.0),
            BossSpawn("DarkFalzForm2Body", 0.0, 0.0, 0.0),
        ),
        destinationMap = "pioneer2",
        arrivalMessage = "An ancient darkness stirs...",
        clearedMessage = "Dark Falz is destroyed. Ragol is free.",
        bossKey = "darkFalz",
    ),
    "bossArea3" to BossEncounter(
        sectionId = 0,
        enemies = listOf(
            // Both forms stand from the first frame: the core in the room, the machine parked
            // out of sight beneath the centre hatch until the core falls. Listing both keeps
            // the exit warp shut until the *second* form dies.
            BossSpawn("VolOptForm1", 0.0, 0.0, -80.0),
            BossSpawn("VolOpt", 0.0, 0.0, 0.0),
        ),
        destinationMap = "ruins01",
        arrivalMessage = "The monitors flicker to life...",
        clearedMessage = "Vol Opt shuts down. The way to the Ruins is open.",
        bossKey = "volOpt",
    ),
    "bossArea1" to BossEncounter(
        sectionId = 0,
        enemies = listOf(
            // Centre field, nudged off the origin: the vent's crater dips 11 units at the
            // exact centre and the ground search would stand the boss down inside it. The
            // player arrives at the -Z gate, so +40 also opens the fight across the field.
            BossSpawn("Dragon", 0.0, 0.0, 40.0),
        ),
        destinationMap = "pioneer2",
        arrivalMessage = "The Dragon descends!",
        clearedMessage = "The Dragon falls. The warp home has opened.",
        bossKey = "dragon",
    ),
)

/** Which boss arena each area's boss teleporter leads to. */
val BOSS_ARENA_FOR_MAP: Map<String, String> = mapOf(
    "forest02" to "bossArea1",
    // De Rol Le's and Vol Opt's arenas aren't built yet, so the underground boss warps skip
    // straight to the next zone -- the loop stays walkable end to end, and these rows move to
    // the real arenas when the fights exist. Ruins 3's warp stays unmapped (Dark Falz pending).
    "cave03" to "bossArea2",
    "mines02" to "bossArea3",
    "ruins03" to "bossArea4",
)
