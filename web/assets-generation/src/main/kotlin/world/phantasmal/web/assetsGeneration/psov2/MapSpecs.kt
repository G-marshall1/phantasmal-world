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

/**
 * In the real game, Cave/Mine/Ruins areas are randomly assembled from a handful of fixed layout
 * variants each time they're generated, while Forest never varies (one fixed layout) -- psov2's
 * own data mirrors this exactly: Forest's .rel files have no numeric suffix at all, while Cave/
 * Mine/Ruins ship 5-6 complete "_00".."_05" geometry variants per area, all sharing one texture
 * pack per area (no per-variant .pvm). [variantCount] is that count (checked directly against the
 * source directory's file listing); slug 0 keeps the un-suffixed area name (so nothing that
 * already references e.g. "cave01" as the default/first variant breaks), the rest get "Layout2"
 * upward.
 */
private fun areaVariants(
    slug: String,
    pvmName: String,
    relPrefix: String,
    variantCount: Int,
): List<MapSpec> =
    (0 until variantCount).map { i ->
        val variantSlug = if (i == 0) slug else "${slug}Layout${i + 1}"
        val suffix = i.toString().padStart(2, '0')
        MapSpec(
            variantSlug,
            pvmName,
            "${relPrefix}_${suffix}n.rel",
            "${relPrefix}_${suffix}d.rel",
            "${relPrefix}_${suffix}c.rel",
        )
    }

val MAP_SPECS: List<MapSpec> = buildList {
    add(MapSpec("forest01", "map_forest01.pvm", "map_forest01n.rel", "map_forest01d.rel", "map_forest01c.rel"))
    add(MapSpec("forest02", "map_forest02.pvm", "map_forest02n.rel", "map_forest02d.rel", "map_forest02c.rel"))
    addAll(areaVariants("cave01", "map_cave01.pvm", "map_cave01", 6))
    addAll(areaVariants("cave02", "map_cave02.pvm", "map_cave02", 5))
    addAll(areaVariants("cave03", "map_cave03.pvm", "map_cave03", 6))
    addAll(areaVariants("mines01", "map_machine01.pvm", "map_machine01", 6))
    addAll(areaVariants("mines02", "map_machine02.pvm", "map_machine02", 6))
    addAll(areaVariants("ruins01", "map_ancient01.pvm", "map_ancient01", 5))
    addAll(areaVariants("ruins02", "map_ancient02.pvm", "map_ancient02", 5))
    addAll(areaVariants("ruins03", "map_ancient03.pvm", "map_ancient03", 5))

    // Ultimate difficulty's reskinned Forest/Cave/Mines (the "a" prefix psov2 uses for these --
    // Ultimate difficulty in the original game never reskins Ruins, so there's no equivalent
    // entry for it). Forest still has no layout variants even under this reskin, matching the
    // base game above.
    add(MapSpec("ultimateForest01", "map_aforest01.pvm", "map_aforest01n.rel", "map_aforest01d.rel", "map_aforest01c.rel"))
    add(MapSpec("ultimateForest02", "map_aforest02.pvm", "map_aforest02n.rel", "map_aforest02d.rel", "map_aforest02c.rel"))
    addAll(areaVariants("ultimateCave01", "map_acave01.pvm", "map_acave01", 6))
    addAll(areaVariants("ultimateCave02", "map_acave02.pvm", "map_acave02", 5))
    addAll(areaVariants("ultimateCave03", "map_acave03.pvm", "map_acave03", 6))
    addAll(areaVariants("ultimateMines01", "map_amachine01.pvm", "map_amachine01", 6))
    addAll(areaVariants("ultimateMines02", "map_amachine02.pvm", "map_amachine02", 6))
}
