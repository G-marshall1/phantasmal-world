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
class AreaLayoutSpec(
    val base: String,
    val solo: Boolean,
    /**
     * Per-layout terrain .rel, for areas whose layout variants each carry their own geometry
     * (the caves). Null means the spec-level [AreaSpawnSpec.sectionRel] serves every layout
     * (the forests, one terrain shared by all tables).
     */
    val sectionRel: String? = null,
    /**
     * The runtime geometry slug this table belongs to ("cave01Layout3"), so the game can match
     * the encounter table to the terrain variant it actually loaded. Null for the forests,
     * where any table fits the one terrain.
     */
    val geometry: String? = null,
)

private fun forestLayouts(area: String, variants: List<Int>, soloVariants: Set<Int>) =
    buildList {
        for (v in variants) {
            val n = v.toString().padStart(2, '0')
            add(AreaLayoutSpec("map_${area}_$n", solo = false))
            if (v in soloVariants) add(AreaLayoutSpec("map_${area}_${n}_off", solo = true))
        }
    }

/**
 * Cave layouts: each variant is its own terrain (map_caveNN_VVd.rel) with its own tables. The
 * "_00" encounter set is the base game's; each variant's "_00_off" is the offline/solo density.
 * Geometry slugs follow the converted area naming: variant 0 is the bare slug, variants 1+ are
 * "Layout2".."LayoutN" (see AREA_LAYOUT_COUNTS in :web:mobileGame's MapAssetLoader).
 */
private fun caveLayouts(area: String, variants: Int) = buildList {
    for (v in 0 until variants) {
        val vv = v.toString().padStart(2, '0')
        val geometry = if (v == 0) area else "${area}Layout${v + 1}"
        val rel = "map_${area}_${vv}d.rel"
        add(AreaLayoutSpec("map_${area}_${vv}_00", solo = false, sectionRel = rel, geometry = geometry))
        add(AreaLayoutSpec("map_${area}_${vv}_00_off", solo = true, sectionRel = rel, geometry = geometry))
    }
}

/**
 * Mines/Ruins layouts: like the caves, each variant is its own terrain -- but where a cave
 * variant has one encounter table (plus an offline twin), these ship two minor tables per
 * variant ("_00"/"_01") and no offline density twin at all. Both minors are registered as solo
 * layouts under the same geometry, so the runtime's per-visit pick chooses between them the way
 * the real game's layout roll does.
 *
 * [area] is this project's runtime slug ("mines01"), [srcArea] psov2's own file naming
 * ("machine01"/"ancient01"). Only the first [variants] variants have encounter tables; the
 * later geometry variants are quest-only, exactly like cave03's.
 */
private fun variantMinorLayouts(area: String, srcArea: String, variants: Int) = buildList {
    for (v in 0 until variants) {
        val vv = v.toString().padStart(2, '0')
        val geometry = if (v == 0) area else "${area}Layout${v + 1}"
        val rel = "map_${srcArea}_${vv}d.rel"
        for (minor in 0..1) {
            val mm = minor.toString().padStart(2, '0')
            add(
                AreaLayoutSpec(
                    "map_${srcArea}_${vv}_$mm",
                    solo = true,
                    sectionRel = rel,
                    geometry = geometry,
                )
            )
        }
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
    AreaSpawnSpec(slug = "cave01", sectionRel = "", layouts = caveLayouts("cave01", 6)),
    AreaSpawnSpec(slug = "cave02", sectionRel = "", layouts = caveLayouts("cave02", 5)),
    AreaSpawnSpec(slug = "cave03", sectionRel = "", layouts = caveLayouts("cave03", 6)),
    AreaSpawnSpec(slug = "mines01", sectionRel = "", layouts = variantMinorLayouts("mines01", "machine01", 3)),
    AreaSpawnSpec(slug = "mines02", sectionRel = "", layouts = variantMinorLayouts("mines02", "machine02", 3)),
    AreaSpawnSpec(slug = "ruins01", sectionRel = "", layouts = variantMinorLayouts("ruins01", "ancient01", 3)),
    AreaSpawnSpec(slug = "ruins02", sectionRel = "", layouts = variantMinorLayouts("ruins02", "ancient02", 3)),
    AreaSpawnSpec(slug = "ruins03", sectionRel = "", layouts = variantMinorLayouts("ruins03", "ancient03", 3)),
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
    // The Caves, per psolib's NpcTypeFromData Episode I rows. Slugs are this project's model
    // directory names (note GrassAssasin's single s and NanoDragoon's double o -- the psov2
    // conversion's own spellings).
    0x060 -> "GrassAssasin"
    0x061 -> if (special) "NarLily" else "PoisonLily"
    0x062 -> "NanoDragoon"
    0x063 -> when (skin % 3) {
        0 -> "EvilShark"
        1 -> "PalShark"
        else -> "GuilShark"
    }
    0x064 -> if (special) "PouillySlimeRed" else "PofuillySlimeBlue"
    0x065 -> "PanArms"
    // The Mines, per psolib's Episode I rows. Slugs are the converted model directory names
    // (Gilchic with one l -- psov2's own spelling).
    0x080 -> if (skin % 2 == 0) "Dubchic" else "Gilchic"
    0x081 -> "Garanz"
    0x082 -> if (special) "SinowGold" else "SinowBeat"
    0x083 -> "Canadine"
    0x084 -> "Canane"
    // The Dubswitch pod that revives downed Dubchics: psov2 has no model for it at all (checked
    // AssetEnemies.js), so it and the revival mechanic it powers are skipped.
    0x085 -> null
    // The Ruins.
    0x0A0 -> "Delsaber"
    0x0A1 -> "ChaosSorcerer"
    // 0x0A3 is the Death Gunner, a Dark Gunner variant psolib itself doesn't map; folded into
    // the Dark Gunner body since psov2's only recolor is a single texture.
    0x0A2, 0x0A3 -> "DarkGunner"
    0x0A4 -> "ChaosBringer"
    0x0A5 -> "DarkBelra"
    0x0A6 -> when (skin % 3) {
        0 -> "Dimenian"
        1 -> "LaDimenian"
        else -> "SoDimenian"
    }
    // The converted assets split Bulclaw into its closed and open bodies; the open one is the
    // fighting form, so that's what spawns.
    0x0A7 -> "BulclawOpen"
    0x0A8 -> "Claw"
    else -> null
}
