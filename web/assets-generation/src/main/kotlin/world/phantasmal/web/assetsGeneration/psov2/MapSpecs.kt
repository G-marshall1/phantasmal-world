package world.phantasmal.web.assetsGeneration.psov2

/**
 * One map's psov2 source data. Derived from psov2's AssetRooms.js, which loads three files per
 * map: a texture pack (.pvm), decorative-props render geometry (n.rel), and terrain render
 * geometry (d.rel) -- see MapAssetLoader.kt's loadForest1 (renamed loadArea below) for why both
 * render files are needed. The matching collision file (c.rel) isn't loaded by psov2's own room
 * code at all, but exists alongside the others under the same base name.
 */
class MapSpec(
    val slug: String,
    val pvmName: String,
    val nRelName: String,
    val dRelName: String,
    val cRelName: String,
)

val MAP_SPECS: List<MapSpec> = listOf(
    MapSpec("forest01", "map_forest01.pvm", "map_forest01n.rel", "map_forest01d.rel", "map_forest01c.rel"),
    MapSpec("forest02", "map_forest02.pvm", "map_forest02n.rel", "map_forest02d.rel", "map_forest02c.rel"),
    MapSpec("cave01", "map_cave01.pvm", "map_cave01_00n.rel", "map_cave01_00d.rel", "map_cave01_00c.rel"),
    MapSpec("cave02", "map_cave02.pvm", "map_cave02_00n.rel", "map_cave02_00d.rel", "map_cave02_00c.rel"),
    MapSpec("cave03", "map_cave03.pvm", "map_cave03_00n.rel", "map_cave03_00d.rel", "map_cave03_00c.rel"),
    MapSpec("mines01", "map_machine01.pvm", "map_machine01_00n.rel", "map_machine01_00d.rel", "map_machine01_00c.rel"),
    MapSpec("mines02", "map_machine02.pvm", "map_machine02_00n.rel", "map_machine02_00d.rel", "map_machine02_00c.rel"),
    MapSpec("ruins01", "map_ancient01.pvm", "map_ancient01_00n.rel", "map_ancient01_00d.rel", "map_ancient01_00c.rel"),
    MapSpec("ruins02", "map_ancient02.pvm", "map_ancient02_00n.rel", "map_ancient02_00d.rel", "map_ancient02_00c.rel"),
    MapSpec("ruins03", "map_ancient03.pvm", "map_ancient03_00n.rel", "map_ancient03_00d.rel", "map_ancient03_00c.rel"),
)
