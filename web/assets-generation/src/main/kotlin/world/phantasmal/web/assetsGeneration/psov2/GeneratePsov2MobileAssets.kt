package world.phantasmal.web.assetsGeneration.psov2

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
 * missing 3), 69 enemies (see ENEMY_SPECS in EnemySpecs.kt), 10 maps (see MAP_SPECS in
 * MapSpecs.kt) -- all of episode 1's field areas' first layout variant -- 227 weapons/shields/
 * mags/units (see WEAPON_SPECS in WeaponSpecs.kt), and a handful of decorative map props (see
 * OBJECT_SPECS in ObjectSpecs.kt).
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
    generatePlayerAnimations(sourceDir, outputDir)
    generateWeapons(sourceDir, outputDir)
    generateObjects(sourceDir, outputDir)

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
    write(outputDir, "player/${spec.slug}Head0.nj", bml.getValue("pl${spec.letter}hed00.nj"))

    if (spec.hasHair) {
        write(outputDir, "player/${spec.slug}Hair0.nj", bml.getValue("pl${spec.letter}hai00.nj"))
    }

    if (spec.hasAccessory) {
        write(
            outputDir,
            "player/${spec.slug}Accessory0.nj",
            bml.getValue("pl${spec.letter}cap00.nj"),
        )
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

private fun write(outputDir: File, relativePath: String, bytes: ByteArray) {
    val file = File(outputDir, relativePath)
    file.parentFile.mkdirs()
    file.writeBytes(bytes)
}
