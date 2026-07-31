package world.phantasmal.web.assetsGeneration.psov2

/**
 * One "stage" (as opposed to a dungeon "room"/area) from psov2's AssetStage.js -- static hub/lobby
 * maps like Pioneer 2, as distinct from the randomly-assembled field areas MAP_SPECS covers. Same
 * underlying pvm/d.rel/n.rel file trio as rooms, but NinjaStage.js's section table (unlike
 * NinjaRoom.js's) actually applies each section's own position/rotation as a transform on top of
 * that section's models -- rooms bake final coordinates directly into each model instead. See
 * Psov2StageGeometry.kt for the runtime parser this distinction requires.
 */
class StageSpec(
    val slug: String,
    val pvmName: String,
    val nRelName: String,
    val dRelName: String,
)

val STAGE_SPECS: List<StageSpec> = listOf(
    StageSpec("pioneer2", "map_city00.pvm", "map_city00_00n.rel", "map_city00_00d.rel"),

    // The 9 lobby color variants -- purely cosmetic re-skins of the same lobby layout, matching
    // psov2's own AssetStage.js naming (map_lobby_<color>00.*).
    StageSpec("lobbyBlack", "map_lobby_black00.pvm", "map_lobby_black00n.rel", "map_lobby_black00d.rel"),
    StageSpec("lobbyBlue", "map_lobby_blue00.pvm", "map_lobby_blue00n.rel", "map_lobby_blue00d.rel"),
    StageSpec("lobbyBluegreen", "map_lobby_bluegreen00.pvm", "map_lobby_bluegreen00n.rel", "map_lobby_bluegreen00d.rel"),
    StageSpec("lobbyGreen", "map_lobby_green00.pvm", "map_lobby_green00n.rel", "map_lobby_green00d.rel"),
    StageSpec("lobbyOrange", "map_lobby_orange00.pvm", "map_lobby_orange00n.rel", "map_lobby_orange00d.rel"),
    StageSpec("lobbyPurple", "map_lobby_purple00.pvm", "map_lobby_purple00n.rel", "map_lobby_purple00d.rel"),
    StageSpec("lobbyRed", "map_lobby_red00.pvm", "map_lobby_red00n.rel", "map_lobby_red00d.rel"),
    StageSpec("lobbyWhite", "map_lobby_white00.pvm", "map_lobby_white00n.rel", "map_lobby_white00d.rel"),
    StageSpec("lobbyYellow", "map_lobby_yellow00.pvm", "map_lobby_yellow00n.rel", "map_lobby_yellow00d.rel"),
    StageSpec("lobbyYellowGreen", "map_lobby_y_green00.pvm", "map_lobby_y_green00n.rel", "map_lobby_y_green00d.rel"),

    // Boss arenas -- just the arena geometry itself (no actual boss enemy; multi-part bosses are
    // out of scope, see EnemySpecs.kt's doc comment).
    StageSpec("bossArea1", "map_boss01.pvm", "map_boss01n.rel", "map_boss01d.rel"),
    StageSpec("bossArea2", "map_boss02.pvm", "map_boss02n.rel", "map_boss02d.rel"),
    StageSpec("bossArea3", "map_boss03.pvm", "map_boss03n.rel", "map_boss03d.rel"),
    StageSpec("bossArea4", "map_darkfalz00.pvm", "map_darkfalz00n.rel", "map_darkfalz00d.rel"),

    // Ultimate difficulty's reskinned boss arenas (bosses 1-3 only, matching the original game --
    // Dark Falz's arena is never reskinned for Ultimate).
    StageSpec("ultimateBossArea1", "map_aboss01.pvm", "map_aboss01n.rel", "map_aboss01d.rel"),
    StageSpec("ultimateBossArea2", "map_aboss02.pvm", "map_aboss02n.rel", "map_aboss02d.rel"),
    StageSpec("ultimateBossArea3", "map_aboss03.pvm", "map_aboss03n.rel", "map_aboss03d.rel"),

    // "VS" battle-mode arenas -- psov2 labels these "Spaceship"/"Temple" in its own nav, but the
    // file naming (map_vs01/map_vs02) points to PSO's actual PvP battle mode maps, each with 3
    // layout variants.
    StageSpec("spaceship00", "map_vs01.pvm", "map_vs01_00n.rel", "map_vs01_00d.rel"),
    StageSpec("spaceship01", "map_vs01.pvm", "map_vs01_01n.rel", "map_vs01_01d.rel"),
    StageSpec("spaceship02", "map_vs01.pvm", "map_vs01_02n.rel", "map_vs01_02d.rel"),
    StageSpec("temple00", "map_vs02.pvm", "map_vs02_00n.rel", "map_vs02_00d.rel"),
    StageSpec("temple01", "map_vs02.pvm", "map_vs02_01n.rel", "map_vs02_01d.rel"),
    StageSpec("temple02", "map_vs02.pvm", "map_vs02_02n.rel", "map_vs02_02d.rel"),
)
