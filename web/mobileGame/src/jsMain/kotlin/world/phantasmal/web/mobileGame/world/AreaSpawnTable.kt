package world.phantasmal.web.mobileGame.world

import kotlinx.serialization.Serializable
import world.phantasmal.web.core.loading.AssetLoader

/**
 * An area's room/wave encounter table, generated from PSO's own map data -- see
 * generateAreaSpawns in :web:assets-generation's GeneratePsov2MobileAssets.kt for where each
 * field comes from and how positions were baked to world space.
 *
 * [sections] are the map's rooms: every enemy and every event names the section it belongs to,
 * and a section's entry is what says where that room actually is.
 */
@Serializable
class AreaSpawnTable(
    val sections: List<SpawnSection>,
    val layouts: List<SpawnLayout>,
)

@Serializable
class SpawnSection(val id: Int, val x: Double, val y: Double, val z: Double)

/**
 * One of the layout variations the game picks between on entering the area. [solo] marks the
 * offline table, which is the one a single-player game wants -- see AreaLayoutSpec.
 */
@Serializable
class SpawnLayout(
    val name: String,
    val solo: Boolean,
    /**
     * The runtime geometry slug this table belongs to ("cave01Layout3"): the caves' every
     * layout variant is its own terrain, so table and terrain must be picked together. Null
     * (the forests) fits any.
     */
    val geometry: String? = null,
    /** Per-layout section table, for areas whose variants carry their own terrain. */
    val sections: List<SpawnSection> = emptyList(),
    val enemies: List<SpawnEnemy>,
    val events: List<SpawnEvent>,
    /**
     * The layout's interactive objects -- doors, laser fences, and the switches that drop them.
     * Defaulted so tables generated before objects existed still parse.
     */
    val objects: List<SpawnObject> = emptyList(),
)

/**
 * One interactive object placement -- see readObjectPlacements in :web:assets-generation's
 * GeneratePsov2MobileAssets.kt for the record source and the [doorId] semantics: for a door it's
 * the number the wave script's unlock events name, for a switch it's the number it opens.
 */
@Serializable
class SpawnObject(
    val typeId: Int,
    val id: Int,
    val doorId: Int,
    val section: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double,
    /**
     * The record's raw typed parameter block: three floats at offsets 40/44/48 and three ints
     * at 52/56/60, whose meanings psolib's ObjectType documents per type -- a Teleporter's
     * first float is its destination Area ID, a Warp's floats are its destination point, an
     * Event Collision's first float is its trigger radius; the first int carries the
     * Floor/Event/Door/Switch ID depending on type. Defaulted so older tables still parse.
     */
    val paramsF: List<Double> = emptyList(),
    val paramsI: List<Int> = emptyList(),
) {
    companion object {
        /** Authored player spawn points (Slot ID in paramsF[0]). */
        const val TYPE_PLAYER_SET = 0

        /** Area-transition pad: paramsF[0] = destination Area ID. */
        const val TYPE_TELEPORTER = 2

        /** In-area hop: paramsF = destination x/y/z, paramsI[0] = arrival yaw (Ninja angle). */
        const val TYPE_WARP = 3

        /** Invisible wave-trigger volume: paramsF[0] = radius, paramsI[0] = event ID. */
        const val TYPE_EVENT_COLLISION = 8

        /** Room metadata marker. */
        const val TYPE_OBJ_ROOM_ID = 14

        /** The warp to the area's boss arena. */
        const val TYPE_BOSS_TELEPORTER = 25

        const val TYPE_FOREST_DOOR = 128
        const val TYPE_FOREST_SWITCH = 129
        const val TYPE_LASER_FENCE = 130
        const val TYPE_SQUARE_LASER_FENCE = 131
        const val TYPE_LASER_FENCE_SWITCH = 132

        /**
         * The breakable crates. Random/Fixed/Empty hold an item (or nothing); the two "enemy
         * box" types hide a monster that bursts out when the crate is smashed -- which is why
         * they're listed separately from [ITEM_BOX_TYPES].
         */
        const val TYPE_RANDOM_BOX = 136
        const val TYPE_ENEMY_BOX_GREY = 145
        const val TYPE_FIXED_BOX = 146
        const val TYPE_ENEMY_BOX_BROWN = 147
        const val TYPE_EMPTY_BOX = 149

        val ITEM_BOX_TYPES = setOf(TYPE_RANDOM_BOX, TYPE_FIXED_BOX, TYPE_EMPTY_BOX)
        val ENEMY_BOX_TYPES = setOf(TYPE_ENEMY_BOX_GREY, TYPE_ENEMY_BOX_BROWN)
        val BREAKABLE_BOX_TYPES =
            ITEM_BOX_TYPES + ENEMY_BOX_TYPES + TYPE_RUINS_FIXED_BOX + TYPE_RUINS_RANDOM_BOX

        /** The Forest's remaining furniture -- see the prop model specs in ObjectSpecs.kt. */
        const val TYPE_PROBE = 135
        const val TYPE_WEATHER_STATION = 137
        const val TYPE_RICO_MESSAGE_POD = 141

        /** Door-driven like a fence: drops when the door number in [doorId] opens. */
        const val TYPE_ENERGY_BARRIER = 142

        /** Door-driven: rises when the door number in [doorId] opens. */
        const val TYPE_RISING_BRIDGE = 143

        /** A switch that trips a switch ID (paramsI[0]) rather than a door directly. */
        const val TYPE_SWITCH_NONE_DOOR = 144

        const val TYPE_MONUMENT = 342

        // The Caves. A floor panel is the switch a four-button door counts; the switch door
        // and the plain door are ordinary door-ID gates. See psolib's ObjectType.
        const val TYPE_CAVE_FLOOR_PANEL = 192
        const val TYPE_CAVE_4_BUTTON_DOOR = 193
        const val TYPE_CAVE_DOOR = 194
        const val TYPE_CAVE_SWITCH_DOOR = 206
        const val TYPE_ELEMENTAL_TRAP = 10
        const val TYPE_LARGE_ELEMENTAL_TRAP = 12
        // 19 is psolib's HealRing. 13 -- long mistaken for it -- is a second large trap.
        const val TYPE_HEAL_RING = 19
        const val TYPE_LARGE_ELEMENTAL_TRAP_B = 13

        // The Ruins' own crates.
        const val TYPE_RUINS_FIXED_BOX = 353
        const val TYPE_RUINS_RANDOM_BOX = 354

        // The Mines: the same door family as the caves under its own numbers, plus the Mines'
        // trap variant.
        const val TYPE_MINE_DOOR = 256
        const val TYPE_MINE_FLOOR_PANEL = 257
        const val TYPE_MINE_4_BUTTON_DOOR = 258
        const val TYPE_MINE_SWITCH_DOOR = 268
        const val TYPE_MINE_TRAP = 11

        // The Ruins: an in-map warp whose record matches the standard warp's, the floor switch,
        // and one normal-door type per area (324/325/326 belong to Ruins 1/3/2's own models).
        const val TYPE_RUINS_WARP = 321
        const val TYPE_RUINS_SWITCH = 323

        // The Ruins' remaining mechanisms.
        const val TYPE_RUINS_TELEPORTER = 320
        const val TYPE_RUINS_DOOR_SWITCH = 322
        const val TYPE_RUINS_4_BUTTON_DOOR = 330
        const val TYPE_RUINS_2_BUTTON_DOOR = 331
        const val TYPE_RUINS_FENCE_SWITCH = 333
        const val TYPE_RUINS_FENCE_4X2 = 334
        const val TYPE_RUINS_FENCE_6X2 = 335
        const val TYPE_RUINS_POISON_BLOB = 338
        const val TYPE_RUINS_PILLAR_TRAP = 339
        const val TYPE_RUINS_CRYSTAL = 341
        const val TYPE_RUINS_DOOR_A1 = 324
        const val TYPE_RUINS_DOOR_A3 = 325
        const val TYPE_RUINS_DOOR_A2 = 326
    }
}

@Serializable
class SpawnEnemy(
    val slug: String,
    val section: Int,
    val wave: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double,
)

/**
 * What happens once the wave this event names has been wiped out: wait [delay] frames, then spawn
 * each wave in [triggers] and unlock each door in [doors].
 *
 * An event nothing else triggers is a room's opening wave -- that's what the player walking in
 * starts. A room can have more than one, which is how a couple of Forest 2's rooms run two
 * interleaved wave chains at once.
 */
@Serializable
class SpawnEvent(
    val id: Int,
    val section: Int,
    val wave: Int,
    val delay: Int,
    val triggers: List<Int>,
    val doors: List<Int>,
)

/**
 * Loads an area's encounter table, or null for an area that has none generated yet (most of them
 * -- only the Forest is covered so far). A missing table means the area simply spawns nothing,
 * which is the honest outcome: better an empty area than the old behaviour of scattering one of
 * every converted species around regardless of biome.
 *
 * Areas with no species roster ([AREA_ENEMIES]) are known up front not to have a table, and are
 * skipped rather than fetched and 404'd -- every enemy needs a roster entry for its clips anyway,
 * so a table without one couldn't put anything on the map.
 */
suspend fun loadAreaSpawnTable(assetLoader: AssetLoader, mapSlug: String): AreaSpawnTable? {
    if (mapSlug !in AREA_ENEMIES) return null

    // Boss arenas have no set data -- the client hardcodes its bosses -- so their tables are
    // synthesized: one section at the origin, one empty layout. The boss itself arrives via
    // AREA_BOSSES the moment the player sets foot in that section.
    if (mapSlug in AREA_BOSSES) {
        return AreaSpawnTable(
            sections = listOf(SpawnSection(0, 0.0, 0.0, 0.0)),
            layouts = listOf(
                SpawnLayout(name = "boss", solo = true, enemies = emptyList(), events = emptyList()),
            ),
        )
    }

    return assetLoader.load<AreaSpawnTable>("/spawns/$mapSlug.json")
}
