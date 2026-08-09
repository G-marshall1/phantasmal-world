package world.phantasmal.web.assetsGeneration.psov2

/**
 * One area's room/wave tables. Together these are PSO's whole field-encounter system, and psov2
 * ships them verbatim, so none of it has to be hand-authored.
 *
 * Each layout is a pair of files sharing a base name:
 *  - "<base>e.dat" -- the enemy placements: a bare array of 72-byte NPC records (psolib's
 *    NPC_BYTE_SIZE, which is why the file size is always an exact multiple of 72), each tagged
 *    with the section it belongs to and the wave within that section it spawns as.
 *  - "<base>.evt" -- the wave script: what happens when a given (section, wave) is wiped out.
 *
 * [sectionRel] names the map's terrain .rel, whose section table is what the records' coordinates
 * are relative to. Forest's sections are a plain 320-unit grid of terrain tiles with no rotation,
 * which is what lets the generator bake world coordinates outright instead of shipping the table
 * and transforming at runtime.
 */
class AreaSpawnSpec(
    val slug: String,
    val sectionRel: String,
    val layouts: List<AreaLayoutSpec>,
)

/**
 * PSO ships two densities of every layout: the plain file is the online table and the "_off" one
 * is offline/solo, which is roughly half as many enemies per wave (Forest 1 variant 0: 67 vs 32).
 * Both are generated; the runtime only picks among [solo] ones, this being a single-player game.
 *
 * Only some variants have an offline table at all -- Forest 1 has three (0, 2, 4) against six
 * online. That's the real offline variant set, not a gap in psov2.
 */
class AreaLayoutSpec(val base: String, val solo: Boolean)

private fun forestLayouts(area: String, variants: List<Int>, soloVariants: Set<Int>) =
    buildList {
        for (v in variants) {
            val n = v.toString().padStart(2, '0')
            add(AreaLayoutSpec("map_${area}_$n", solo = false))
            if (v in soloVariants) add(AreaLayoutSpec("map_${area}_${n}_off", solo = true))
        }
    }

val AREA_SPAWN_SPECS: List<AreaSpawnSpec> = listOf(
    AreaSpawnSpec(
        slug = "forest01",
        sectionRel = "map_forest01d.rel",
        layouts = forestLayouts("forest01", (0..5).toList(), setOf(0, 2, 4)),
    ),
    AreaSpawnSpec(
        slug = "forest02",
        sectionRel = "map_forest02d.rel",
        layouts = forestLayouts("forest02", (0..4).toList(), setOf(0, 3, 4)),
    ),
)

/**
 * Resolves a raw entity record's type id and skin to one of this project's enemy slugs, following
 * psolib's own NpcTypeFromData mapping for Episode I. Only the Forest species are covered; anything
 * else returns null and is dropped, since no other area has a spawn table generated yet.
 *
 * The "special" flag (a float at offset 48 that reads as 1) is what separates Savage from Barbarous
 * Wolf, exactly as psolib does it -- skin is unused for that one type.
 */
fun forestEnemySlug(typeId: Int, skin: Int, special: Boolean): String? = when (typeId) {
    0x040 -> if (skin % 2 == 0) "Hildebear" else "Hildeblue"
    0x041 -> if (skin % 2 == 0) "Rappy" else "AlRappy"
    0x042 -> "Monest"
    0x043 -> if (special) "BarbarousWolf" else "SavageWolf"
    0x044 -> when (skin % 3) {
        0 -> "Booma"
        1 -> "GoBooma"
        else -> "GigaBooma"
    }
    else -> null
}
