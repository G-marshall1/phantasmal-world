package world.phantasmal.web.assetsGeneration.psov2

import kotlin.math.cos
import kotlin.math.sin
import mu.KotlinLogging
import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.compression.prs.prsDecompress
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.parseAfs
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Converts a subset of psov2 (https://gitlab.com/dashgl/psov2) assets -- a copyright-free
 * recreation of Phantasy Star Online's asset files -- into the exact file names and formats the
 * mobile game's asset loaders expect (see PlayerAssetLoader/EnemyAssetLoader/MapAssetLoader in
 * :web:mobileGame). psov2 ships real Ninja-format geometry (re-usable as-is) but Dreamcast-format
 * PVR textures (twiddled/VQ-compressed), which this also decodes and re-encodes to the DXT3-based
 * XVR format phantasmal's renderer understands.
 *
 * Covers 9 of the 12 playable classes (see PLAYER_CLASS_SPECS in PlayerClassSpecs.kt for the
 * missing 3), 95 enemies -- every boss except Boss1 (psov2 doesn't implement it), including all
 * of Bulclaw/Dark Falz's per-form/per-piece entries and both of Vol Opt's phases, 6 of which are
 * genuinely multi-part (De Rol Le, Dal Ral Lie, Garanz, Baranz, Vol Opt, Vol Opt V2) -- (see
 * ENEMY_SPECS in EnemySpecs.kt), 77 maps (see MAP_SPECS in
 * MapSpecs.kt) -- all of episode 1's field areas, every fixed layout variant psov2 ships (Forest
 * has one; Cave/Mine/Ruins have 5-6 each), in both base and Ultimate-difficulty reskins -- 227
 * weapons/shields/mags/units (see WEAPON_SPECS in WeaponSpecs.kt), a handful of decorative map
 * props (see OBJECT_SPECS in ObjectSpecs.kt), static hub stages like Pioneer 2 (see STAGE_SPECS in
 * StageSpecs.kt), and 45 town NPCs (see NPC_SPECS/NPC_REL_SPECS in NpcSpecs.kt).
 */
fun generatePsov2MobileAssets(sourceDir: File, outputDir: File) {
    logger.info("Generating psov2-derived mobile game assets.")

    for (spec in PLAYER_CLASS_SPECS) {
        generatePlayerClass(sourceDir, outputDir, spec)
    }
    for (spec in ENEMY_SPECS) {
        generateEnemy(sourceDir, outputDir, spec)
    }
    for (spec in MAP_SPECS) {
        generateMap(sourceDir, outputDir, spec)
    }
    for (spec in STAGE_SPECS) {
        generateStage(sourceDir, outputDir, spec)
    }
    for (spec in NPC_SPECS) {
        generateNpc(sourceDir, outputDir, spec)
    }
    for (spec in NPC_REL_SPECS) {
        generateNpcRel(sourceDir, outputDir, spec)
    }
    generatePlayerAnimations(sourceDir, outputDir)
    generateWeapons(sourceDir, outputDir)
    generateObjects(sourceDir, outputDir)
    generateGslObjects(sourceDir, outputDir)
    generateLooseObjects(sourceDir, outputDir)
    generateAreaSpawns(sourceDir, outputDir)

    logger.info("Done generating psov2-derived mobile game assets.")
}

/**
 * Player animations are shared across every class (one skeleton, ~572 unnamed clips) rather than
 * bundled per-class -- see AnimationAssetLoader / PlayerAnimations.kt, which already expect
 * exactly this "/player/animation/animation_NNN.njm" layout. psolib's own parseNjm already has a
 * fallback path documented as "Format used by PSO:BB plymotiondata.rlc", i.e. this is the exact
 * file phantasmal's original animation set was itself extracted from.
 */
private fun generatePlayerAnimations(sourceDir: File, outputDir: File) {
    logger.info("Generating player/animation/*.")

    val entries = readRlc(File(sourceDir, "plymotiondata.rlc").readBytes())

    for ((i, entry) in entries.withIndex()) {
        val name = i.toString().padStart(3, '0')
        write(outputDir, "player/animation/animation_$name.njm", decryptPrc(entry))
    }
}

private fun generatePlayerClass(sourceDir: File, outputDir: File, spec: PlayerClassSpec) {
    logger.info("Generating player/${spec.slug}*.")

    val letterLower = spec.letter.lowercaseChar()
    val bml = readBml(File(sourceDir, "pl${letterLower}nj.bml").readBytes())
    write(outputDir, "player/${spec.slug}Body.nj", bml.getValue("pl${spec.letter}bdy00.nj"))

    // Every head/hair/accessory mesh variant psov2 actually ships for this class (not just index
    // 0) -- see PlayerClassSpec's doc comment for how these counts/indices were derived.
    for (i in 0 until spec.headCount) {
        val name = "pl${spec.letter}hed${i.toString().padStart(2, '0')}.nj"
        write(outputDir, "player/${spec.slug}Head$i.nj", bml.getValue(name))
    }

    for (i in 0 until spec.hairCount) {
        val name = "pl${spec.letter}hai${i.toString().padStart(2, '0')}.nj"
        write(outputDir, "player/${spec.slug}Hair$i.nj", bml.getValue(name))
    }

    for (i in spec.accessoryHairIndices) {
        val name = "pl${spec.letter}cap${i.toString().padStart(2, '0')}.nj"
        write(outputDir, "player/${spec.slug}Accessory$i.nj", bml.getValue(name))
    }

    val afsBytes = File(sourceDir, "pl${letterLower}tex.afs").readBytes()
    val afsCursor = Buffer.fromByteArray(afsBytes, Endianness.Little).cursor()
    val afsEntries = parseAfs(afsCursor).unwrap()

    fun decodeEntry(index: Int): DecodedImage =
        decodeTextures(afsEntries[index].byteArray).first()

    val fallbackXvm = buildXvm(listOf(decodeEntry(0)))
    val maxSlot = spec.slotMap.keys.max()
    val decoded = mutableMapOf<Int, ByteArray>()

    val entries = (0..maxSlot).map { target ->
        val source = spec.slotMap[target]

        if (source == null) {
            fallbackXvm
        } else {
            decoded.getOrPut(source) { buildXvm(listOf(decodeEntry(source))) }
        }
    }

    write(outputDir, "player/${spec.slug}Tex.afs", buildAfs(entries))
}

private fun generateEnemy(sourceDir: File, outputDir: File, spec: EnemySpec) {
    logger.info("Generating npcs/${spec.slug}.*.")

    val archiveBytes = File(sourceDir, spec.archive).readBytes()
    val gsl = if (spec.isGsl) readGsl(archiveBytes) else null
    val bmlBytes = if (spec.isGsl) gsl!!.getValue(spec.bmlEntry!!) else archiveBytes
    val bml = readBml(bmlBytes)

    write(outputDir, "npcs/${spec.slug}.nj", bml.getValue(spec.njName))

    // Animation clips (already just PRS-compressed within the same bml, no PRC encryption -- that
    // only applies to the player's shared plymotiondata.rlc). Keeps psov2's own clip name (e.g.
    // "walk_bm1_s_wala_body.njm") since several enemy variants share the exact same animation set.
    for (animName in spec.animationNames) {
        write(outputDir, "npcs/${spec.slug}/$animName", bml.getValue(animName))
    }

    // Multi-part fragments (see EnemyFragment's doc comment) -- always empty for regular enemies.
    // Written into the same per-enemy subdirectory as animation clips; no name collision since
    // fragment models end in ".nj", not ".njm". Fragments with their own separate texture pack
    // (Garanz/Baranz) get their own generated .xvm alongside their .nj, named after the source
    // .pvm so it can't collide with the main body's own "${slug}.xvm".
    for (fragment in spec.fragments) {
        write(outputDir, "npcs/${spec.slug}/${fragment.njName}", bml.getValue(fragment.njName))

        if (fragment.pvmName != null) {
            val fragmentTextures = decodeTextures(bml.getValue(fragment.pvmName))
            write(outputDir, "npcs/${spec.slug}/${fragment.pvmName}.xvm", buildXvm(fragmentTextures))
        }
    }

    val pvmBytes = when (val pvmSource = spec.pvmSource) {
        is PvmSource.FromBml -> bml.getValue(pvmSource.name)
        is PvmSource.FromGsl -> gsl!!.getValue(pvmSource.name)
        is PvmSource.Standalone -> File(sourceDir, pvmSource.fileName).readBytes()
    }
    val textures = decodeTextures(pvmBytes)
    write(outputDir, "npcs/${spec.slug}.xvm", buildXvm(textures))
}

private fun generateMap(sourceDir: File, outputDir: File, spec: MapSpec) {
    logger.info("Generating areas/map_${spec.slug}.*.")

    write(outputDir, "areas/map_${spec.slug}n.rel", File(sourceDir, spec.nRelName).readBytes())
    write(outputDir, "areas/map_${spec.slug}d.rel", File(sourceDir, spec.dRelName).readBytes())
    write(outputDir, "areas/map_${spec.slug}c.rel", File(sourceDir, spec.cRelName).readBytes())

    val textures = decodeTextures(File(sourceDir, spec.pvmName).readBytes())
    write(outputDir, "areas/map_${spec.slug}.xvm", buildXvm(textures))
}

/**
 * Every item's model and texture live in one pair of shared archives (unlike enemies, which each
 * have their own bml/gsl). itemmodel.afs/itemtexture.afs use the same AFS container psolib's own
 * parseAfs already reads (offset/length table after the "AFS\0" magic), but -- unlike the
 * uncompressed AFS archives that format was written for, e.g. player textures -- psov2's own
 * NinjaFile.js reads these particular two PRS-compressed per entry, so each entry needs an extra
 * decompression pass parseAfs doesn't do itself. Model bytes are already a plain NJTL+NJCM(+NMDM)
 * chunk container -- the exact same top-level format psolib's parseNj reads for every other .nj
 * file, filtering to just the NJCM chunk -- so they're written out as-is, no conversion needed.
 */
private fun generateWeapons(sourceDir: File, outputDir: File) {
    logger.info("Generating weapons/*.")

    val texArchive = parseAfs(
        Buffer.fromByteArray(File(sourceDir, "itemtexture.afs").readBytes(), Endianness.Little)
            .cursor()
    ).unwrap()
    val modelArchive = parseAfs(
        Buffer.fromByteArray(File(sourceDir, "itemmodel.afs").readBytes(), Endianness.Little)
            .cursor()
    ).unwrap()

    val decodedModels = mutableMapOf<Int, ByteArray>()
    val builtTextures = mutableMapOf<Int, ByteArray>()

    for (spec in WEAPON_SPECS) {
        logger.info("Generating weapons/${spec.slug}.*.")

        val modelBytes = decodedModels.getOrPut(spec.modelIndex) {
            prsDecompress(modelArchive[spec.modelIndex].cursor()).unwrap().buffer().byteArray
        }
        write(outputDir, "weapons/${spec.slug}.nj", modelBytes)

        val xvm = builtTextures.getOrPut(spec.texIndex) {
            val texBytes =
                prsDecompress(texArchive[spec.texIndex].cursor()).unwrap().buffer().byteArray
            buildXvm(decodeTextures(texBytes))
        }
        write(outputDir, "weapons/${spec.slug}.xvm", xvm)
    }
}

private fun generateStage(sourceDir: File, outputDir: File, spec: StageSpec) {
    logger.info("Generating stages/map_${spec.slug}.*.")

    write(outputDir, "stages/map_${spec.slug}n.rel", File(sourceDir, spec.nRelName).readBytes())
    write(outputDir, "stages/map_${spec.slug}d.rel", File(sourceDir, spec.dRelName).readBytes())

    val textures = decodeTextures(File(sourceDir, spec.pvmName).readBytes())
    write(outputDir, "stages/map_${spec.slug}.xvm", buildXvm(textures))
}

/**
 * Each NPC's nested bml only has one .nj and one .pvm entry apiece, but under whatever internal
 * filename the model happened to be authored with (psov2's own loader for these scans by
 * extension for exactly this reason rather than hardcoding names) -- mirrored here the same way.
 */
private fun generateNpc(sourceDir: File, outputDir: File, spec: NpcSpec) {
    logger.info("Generating npcCharacters/${spec.slug}.*.")

    val archiveBytes = File(sourceDir, spec.archive).readBytes()
    val gsl = readGsl(archiveBytes)
    val bml = readBml(gsl.getValue(spec.bmlEntry))

    val njEntry = bml.keys.first { it.endsWith(".nj") }
    val pvmEntry = bml.keys.first { it.endsWith(".pvm") }

    write(outputDir, "npcCharacters/${spec.slug}.nj", bml.getValue(njEntry))

    val textures = decodeTextures(bml.getValue(pvmEntry))
    write(outputDir, "npcCharacters/${spec.slug}.xvm", buildXvm(textures))
}

/**
 * The 14 city NPCs sourced as standalone ".rel" entries (see NpcRelSpec's doc comment in
 * NpcSpecs.kt for the footer-pointer object-graph format these require at load time). The raw
 * .rel bytes are written out completely unmodified -- its internal childOffset/siblingOffset/
 * modelOffset values are absolute positions within this exact file, so slicing or re-packing it
 * would break every one of those offsets. Only the texture gets decoded/re-encoded, same as any
 * other psov2 asset.
 */
private fun generateNpcRel(sourceDir: File, outputDir: File, spec: NpcRelSpec) {
    logger.info("Generating npcCharacters/${spec.slug}.*.")

    val gsl = readGsl(File(sourceDir, spec.archive).readBytes())

    write(outputDir, "npcCharacters/${spec.slug}.rel", gsl.getValue(spec.relEntry))

    val textures = decodeTextures(gsl.getValue(spec.pvmEntry))
    write(outputDir, "npcCharacters/${spec.slug}.xvm", buildXvm(textures))
}

/**
 * A handful of decorative map props, all bundled in the single shared item.bml archive (unlike
 * the messier, per-map-GSL-sourced multi-part objects OBJECT_SPECS' doc comment mentions skipping
 * -- see ObjectSpecs.kt). Entries are PRS-compressed within the bml the same as any other bml,
 * so readBml already handles decompression; no separate PRC/AFS step needed here.
 */
private fun generateObjects(sourceDir: File, outputDir: File) {
    logger.info("Generating objects/*.")

    val bml = readBml(File(sourceDir, "item.bml").readBytes())

    for (spec in OBJECT_SPECS) {
        logger.info("Generating objects/${spec.slug}.*.")

        write(outputDir, "objects/${spec.slug}.nj", bml.getValue(spec.njEntry))

        val textures = decodeTextures(bml.getValue(spec.pvmEntry))
        write(outputDir, "objects/${spec.slug}.xvm", buildXvm(textures))
    }
}

/**
 * GSL-sourced props (see [GSL_OBJECT_SPECS]) -- same output layout as [generateObjects], plus an
 * optional ".njm" animation clip written alongside as "objects/{slug}.njm".
 */
private fun generateLooseObjects(sourceDir: File, outputDir: File) {
    logger.info("Generating loose-file objects/*.")

    for (spec in LOOSE_OBJECT_SPECS) {
        logger.info("Generating objects/${spec.slug}.*.")

        val njBytes = when {
            spec.njFile != null -> File(sourceDir, spec.njFile).readBytes()
            else -> readBml(File(sourceDir, spec.bmlFile!!).readBytes()).getValue(spec.njEntry!!)
        }
        write(outputDir, "objects/${spec.slug}.nj", njBytes)

        val textures = decodeTextures(File(sourceDir, spec.pvmFile).readBytes())
        write(outputDir, "objects/${spec.slug}.xvm", buildXvm(textures))
    }
}

private fun generateGslObjects(sourceDir: File, outputDir: File) {
    logger.info("Generating GSL-sourced objects/*.")

    for (spec in GSL_OBJECT_SPECS) {
        logger.info("Generating objects/${spec.slug}.*.")

        val gsl =
            if (spec.topLevelBml) emptyMap()
            else readGsl(File(sourceDir, spec.archive).readBytes())
        val bml =
            if (spec.topLevelBml) readBml(File(sourceDir, spec.archive).readBytes())
            else readBml(gsl.getValue(spec.bmlEntry))

        write(outputDir, "objects/${spec.slug}.nj", bml.getValue(spec.njEntry))

        val pvmBytes =
            if (spec.pvmFromGsl) gsl.getValue(spec.pvmEntry) else bml.getValue(spec.pvmEntry)
        val textures = decodeTextures(pvmBytes)
        write(outputDir, "objects/${spec.slug}.xvm", buildXvm(textures))

        // The locked twin: the same model, with its status light swapped for the red variant.
        // Emitted as its own slug so the runtime can simply show one or the other.
        spec.lockedPvmEntry?.let { redEntry ->
            val red = decodeTextures(gsl.getValue(redEntry))
            val swapped = textures.toMutableList()
            swapped[spec.lockedTextureIndex] = red.first()
            write(outputDir, "objects/${spec.slug}Locked.nj", bml.getValue(spec.njEntry))
            write(outputDir, "objects/${spec.slug}Locked.xvm", buildXvm(swapped))
        }

        spec.njmEntry?.let { write(outputDir, "objects/${spec.slug}.njm", bml.getValue(it)) }
    }
}

/**
 * Emits each area's room/wave encounter tables as JSON -- see [AREA_SPAWN_SPECS] for what the
 * source files are. Nothing here is authored: the rooms, the waves, the exact spawn coordinates
 * and the "what unlocks when this wave dies" script are all read out of psov2's own map data.
 *
 * Two files per layout. The ".dat" is a bare array of PSO NPC records giving each enemy its
 * section, wave and section-local position. The ".evt" is the wave script: one entry per
 * (section, wave) saying what to do once that wave is wiped -- spawn the next wave, or open the
 * doors out of the room.
 *
 * Coordinates are baked to world space here using the map's own section table, which for the
 * Forest is an unrotated grid of terrain tiles (asserted below, since baking would silently
 * misplace every enemy on a map whose sections *are* rotated). That keeps the runtime free of
 * both a section table and a transform step.
 *
 * JSON rather than passing the raw files through, because the runtime would otherwise need a PSO
 * entity parser, an event-bytecode interpreter and the type/skin mapping on the JS side -- all of
 * which already exist here, and none of which have to exist twice.
 */
private fun generateAreaSpawns(sourceDir: File, outputDir: File) {
    logger.info("Generating spawns/*.")

    for (spec in AREA_SPAWN_SPECS) {
        // The forests share one terrain across all their tables; the caves' every layout is
        // its own terrain, whose section table rides inside that layout's JSON instead.
        val sharedSections =
            if (spec.sectionRel.isNotEmpty()) readSectionTable(File(sourceDir, spec.sectionRel))
            else emptyMap()
        val layouts = mutableListOf<String>()

        for (layout in spec.layouts) {
            val enemyFile = File(sourceDir, "${layout.base}e.dat")
            val eventFile = File(sourceDir, "${layout.base}.evt")

            if (!enemyFile.exists() || !eventFile.exists()) {
                logger.warn("Incomplete layout ${layout.base}, skipping.")
                continue
            }

            val sections = layout.sectionRel
                ?.let { readSectionTable(File(sourceDir, it)) }
                ?: sharedSections

            val enemies = readEnemyPlacements(enemyFile.readBytes(), sections)
            val events = readWaveScript(eventFile.readBytes())

            // The interactive objects placed alongside the enemies: doors, laser fences and the
            // switches that drop them, from the "<base>o.dat" that mirrors the enemy "<base>e.dat".
            // Not every layout ships one (the offline variants reuse their online sibling's file
            // in the real game); absent just means no objects, not a broken layout.
            val objectFile = File(sourceDir, "${layout.base}o.dat")
                .takeIf { it.exists() }
                ?: File(sourceDir, "${layout.base.removeSuffix("_off")}o.dat")
            val objects =
                if (objectFile.exists()) readObjectPlacements(objectFile.readBytes(), sections)
                else emptyList()

            val geometryField = layout.geometry?.let { """"geometry":"$it",""" } ?: ""
            val sectionsField =
                if (layout.sectionRel != null) {
                    val rows = sections.entries.sortedBy { it.key }.map { (id, s) ->
                        """{"id":$id,"x":${s.x},"y":${s.y},"z":${s.z}}"""
                    }
                    """"sections":[${rows.joinToString(",")}],"""
                } else ""

            layouts.add(
                """{"name":"${layout.base}","solo":${layout.solo},$geometryField$sectionsField""" +
                    """"enemies":[${enemies.joinToString(",")}],""" +
                    """"events":[${events.joinToString(",")}],""" +
                    """"objects":[${objects.joinToString(",")}]}"""
            )
            logger.info(
                "  ${spec.slug} ${layout.base}: ${enemies.size} enemies, ${events.size} events, " +
                    "${objects.size} objects"
            )
        }

        // The section table goes out alongside the (already world-baked) placements because the
        // runtime still needs to know where each room *is* -- that's what tells it which room the
        // player just walked into, and so which wave to start.
        val sectionJson = sharedSections.entries.sortedBy { it.key }.map { (id, s) ->
            """{"id":$id,"x":${s.x},"y":${s.y},"z":${s.z}}"""
        }

        write(
            outputDir,
            "spawns/${spec.slug}.json",
            ("""{"sections":[${sectionJson.joinToString(",")}],""" +
                """"layouts":[${layouts.joinToString(",")}]}""").toByteArray(),
        )
    }
}

/**
 * Section id -> world origin, from a map's terrain .rel. Same outer table the room geometry itself
 * is read through (see :web:mobileGame's Psov2AreaGeometry, which walks these same 60-byte records
 * to place each section's meshes), read here only for the transform.
 */
internal class SectionTransform(val x: Float, val y: Float, val z: Float, val yaw: Double) {
    /** Section-local (lx, lz) to world, by the same R_y the geometry loader composes. */
    fun worldX(lx: Float, lz: Float): Double = x + lx * cos(yaw) + lz * sin(yaw)
    fun worldZ(lx: Float, lz: Float): Double = z - lx * sin(yaw) + lz * cos(yaw)
}

internal fun readSectionTable(file: File): Map<Int, SectionTransform> {
    val b = littleEndian(file.readBytes())
    val tableOffset = b.getInt(b.capacity() - 16)
    val count = b.getInt(tableOffset)
    val sectionsOffset = b.getInt(tableOffset + 8)

    return buildMap {
        for (i in 0 until count) {
            val base = sectionsOffset + i * SECTION_RECORD_SIZE
            val id = b.getInt(base)
            // A few cave connectors carry id -1; nothing references them, and keying them
            // would have each overwrite the last.
            if (id < 0) continue

            val rotX = b.getInt(base + 16)
            val rotY = b.getInt(base + 20)
            val rotZ = b.getInt(base + 24)

            // Y rotation is real (the caves' rooms turn); X/Z rotation never appears and the
            // baking below doesn't handle it, so keep the tripwire for those.
            require(rotX == 0 && rotZ == 0) {
                "Section $id of ${file.name} has X/Z rotation; positions can't be baked."
            }

            put(
                id,
                SectionTransform(
                    b.getFloat(base + 4), b.getFloat(base + 8), b.getFloat(base + 12),
                    rotY * 2.0 * Math.PI / 65536.0,
                ),
            )
        }
    }
}

private fun readEnemyPlacements(
    bytes: ByteArray,
    sections: Map<Int, SectionTransform>,
): List<String> {
    val b = littleEndian(bytes)

    require(bytes.size % NPC_RECORD_SIZE == 0) { "Not a whole number of NPC records." }

    return buildList {
        for (i in 0 until bytes.size / NPC_RECORD_SIZE) {
            val base = i * NPC_RECORD_SIZE
            val typeId = b.getShort(base).toInt()
            val section = b.getShort(base + 12).toInt()
            val wave = b.getShort(base + 14).toInt()
            val special = b.getFloat(base + 48).toInt() == 1
            val skin = b.getInt(base + 64)

            val slug = forestEnemySlug(typeId, skin, special) ?: continue
            val origin = sections[section] ?: continue

            val lx = b.getFloat(base + 20)
            val lz = b.getFloat(base + 28)
            val x = origin.worldX(lx, lz)
            val y = origin.y + b.getFloat(base + 24)
            val z = origin.worldZ(lx, lz)
            // Only the Y rotation matters -- these all stand upright. Ninja's 16-bit-per-turn
            // angle, converted (and composed with the section's own turn) here so the runtime
            // doesn't have to know the encoding.
            val yaw = b.getInt(base + 36) * 2.0 * Math.PI / 65536.0 + origin.yaw

            add("""{"slug":"$slug","section":$section,"wave":$wave,"x":$x,"y":$y,"z":$z,"yaw":$yaw}""")
        }
    }
}

/**
 * The interactive objects out of a layout's "<base>o.dat": 68-byte PSO object records (the same
 * layout psolib's QuestObject reads -- typeId@0, id@8, groupId@10, sectionId@12, position@16,
 * rotation@28, type params@40+). Coordinates are section-baked to world space exactly like the
 * enemy records above.
 *
 * "doorId" is the low byte of the int at offset 52: for a door it's the number the wave script's
 * door-unlock events name (verified across Forest 1 layout 0 -- the door in section 2 carries 2,
 * section 4's carries 9, matching that section's unlock event exactly); for a switch it's the
 * number the switch opens; for an Energy Barrier or Rising Bridge it's the door number that
 * drops/raises it.
 *
 * "paramsF"/"paramsI" are the record's typed parameter block (three floats at 40/44/48, three
 * ints at 52/56/60), emitted raw; each type's meanings are documented in psolib's ObjectType --
 * a Teleporter's floats carry its destination Area ID, a Warp's its destination point, an Event
 * Collision's its trigger radius (ints: Floor/Event/Door/Switch IDs at 52).
 */
private val EMITTED_OBJECT_TYPES = setOf(
    // Spawn points, the field warps, wave-trigger volumes, room metadata and the boss warp.
    0, 2, 3, 8, 14, 25,
    // Doors, switches and laser fences.
    128, 129, 130, 131, 132,
    // The breakable boxes: Random/Fixed/Empty are item crates; the two "enemy box" types hide
    // a monster instead of a drop.
    136, 145, 146, 147, 149,
    // The rest of the Forest's furniture: probe, weather station, Rico's message pods, energy
    // barriers, the rising bridge, no-door switches and the monuments.
    135, 137, 141, 142, 143, 144, 342,
    // The Caves: the floor panel a switch door reads (192), the four-button door (193), the
    // plain door (194) and the switch door (206). The elemental traps (10, 12) and the heal
    // ring (13) ride along -- all are placed by these layouts in numbers.
    192, 193, 194, 206, 10, 12, 13,
    // The Mines: same door family as the caves under new numbers -- the plain door (256), its
    // floor panel (257), the four-button door (258) and the switch door (268). Type 11 is the
    // Mines' own trap variant.
    256, 257, 258, 268, 11,
    // The Ruins: each area uses its own normal-door type (324/325/326 for Ruins 1/3/2's door
    // models), plus the floor switch (323) and the Ruins' own in-map warp (321), whose record
    // is parameter-for-parameter the standard warp's (dest xyz in the floats, yaw in the ints).
    321, 323, 324, 325, 326,
    // The Ruins' own crates (353 fixed, 354 random), and the true heal ring (19) everywhere --
    // 13, long mistaken for the heal ring, is psolib's LargeElementalTrap.
    353, 354, 19,
    // The Ruins' remaining mechanisms: teleporter (320), standing door switch (322), the
    // 4-button (330) and 2-button (331) doors, fence switch (333), laser fences (334/335),
    // and the poison blob (338).
    320, 322, 330, 331, 333, 334, 335, 338,
    // The ceiling pillar trap and the crystal monument.
    339, 341,
)
private const val OBJECT_RECORD_SIZE = 68

private fun readObjectPlacements(
    bytes: ByteArray,
    sections: Map<Int, SectionTransform>,
): List<String> {
    val b = littleEndian(bytes)

    require(bytes.size % OBJECT_RECORD_SIZE == 0) { "Not a whole number of object records." }

    return buildList {
        for (i in 0 until bytes.size / OBJECT_RECORD_SIZE) {
            val base = i * OBJECT_RECORD_SIZE
            val typeId = b.getShort(base).toInt()

            if (typeId !in EMITTED_OBJECT_TYPES) continue

            val id = b.getShort(base + 8).toInt()
            val section = b.getShort(base + 12).toInt()
            val origin = sections[section] ?: continue

            val lx = b.getFloat(base + 16)
            val lz = b.getFloat(base + 24)
            val x = origin.worldX(lx, lz)
            val y = origin.y + b.getFloat(base + 20)
            val z = origin.worldZ(lx, lz)
            val yaw = b.getInt(base + 32) * 2.0 * Math.PI / 65536.0 + origin.yaw
            val doorId = b.getInt(base + 52) and 0xFF
            val paramsF = (0 until 3).map { b.getFloat(base + 40 + it * 4) }
            val paramsI = (0 until 3).map { b.getInt(base + 52 + it * 4) }

            add(
                """{"typeId":$typeId,"id":$id,"doorId":$doorId,"section":$section,""" +
                    """"x":$x,"y":$y,"z":$z,"yaw":$yaw,""" +
                    """"paramsF":[${paramsF.joinToString(",")}],""" +
                    """"paramsI":[${paramsI.joinToString(",")}]}"""
            )
        }
    }
}

/**
 * The wave script. Each entry fires when its (section, wave) has been wiped out, waits [delay]
 * frames, then runs a little bytecode stream of actions.
 *
 * The whole opcode set across every map file psov2 ships is four instructions, of which this uses
 * three: 0x0c triggers another event by id (that event's wave spawns), 0x0a unlocks a door, 0x01
 * ends the stream. 0x08 appears four times in total across every map and is left unhandled --
 * skipped with a warning rather than guessed at, since mis-sizing its operand would desynchronise
 * the rest of the stream.
 */
private fun readWaveScript(bytes: ByteArray): List<String> {
    val b = littleEndian(bytes)
    val actionStreamOffset = b.getInt(0)
    val entriesOffset = b.getInt(4)
    val entryCount = b.getInt(8)

    return buildList {
        for (i in 0 until entryCount) {
            val base = entriesOffset + i * EVENT_RECORD_SIZE
            val id = b.getInt(base)
            val section = b.getShort(base + 8).toInt()
            val wave = b.getShort(base + 10).toInt()
            val delay = b.getShort(base + 12).toInt()

            val triggers = mutableListOf<Int>()
            val doors = mutableListOf<Int>()
            var p = actionStreamOffset + b.getInt(base + 16)

            loop@ while (p < bytes.size) {
                when (val opcode = b.get(p).toInt() and 0xff) {
                    0x01 -> break@loop
                    0x0a -> { doors.add(b.getShort(p + 1).toInt()); p += 3 }
                    0x0c -> { triggers.add(b.getInt(p + 1)); p += 5 }
                    else -> {
                        logger.warn("Unhandled event opcode ${opcode.toString(16)}, truncating stream.")
                        break@loop
                    }
                }
            }

            add(
                """{"id":$id,"section":$section,"wave":$wave,"delay":$delay,""" +
                    """"triggers":[${triggers.joinToString(",")}],"doors":[${doors.joinToString(",")}]}"""
            )
        }
    }
}

private fun littleEndian(bytes: ByteArray): java.nio.ByteBuffer =
    java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)

/** psolib's NPC_BYTE_SIZE -- the ".dat" files are bare arrays of that record. */
private const val NPC_RECORD_SIZE = 72
private const val EVENT_RECORD_SIZE = 0x14
private const val SECTION_RECORD_SIZE = 60

private fun write(outputDir: File, relativePath: String, bytes: ByteArray) {
    val file = File(outputDir, relativePath)
    file.parentFile.mkdirs()
    file.writeBytes(bytes)
}
