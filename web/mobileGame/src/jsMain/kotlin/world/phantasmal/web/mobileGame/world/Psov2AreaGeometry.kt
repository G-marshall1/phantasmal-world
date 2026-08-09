package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.fileFormats.Vec3
import world.phantasmal.psolib.fileFormats.ninja.NinjaEvaluationFlags
import world.phantasmal.psolib.fileFormats.ninja.NjChunk
import world.phantasmal.psolib.fileFormats.ninja.NjModel
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.psolib.fileFormats.ninja.angleToRad
import world.phantasmal.psolib.fileFormats.ninja.parseNjModel
import world.phantasmal.psolib.fileFormats.vec3Float

/**
 * psov2's area/room ".rel" files don't use the "fmt2" render-geometry wrapper that phantasmal's
 * own `parseAreaRenderGeometry` expects (that check fails and the section table it reads next is
 * garbage). Ported from psov2's NinjaRoom.js `prepare`/`readBone`: it turns out each section's
 * static models are plain Ninja bone/chunk object graphs -- the exact same format used by
 * player/enemy .nj models -- just referenced through a different, simpler outer table with no
 * per-format version tag.
 *
 * Each section's models are wrapped in a node carrying that section's position/rotation. Skipping
 * that step (as this once did) leaves every section stacked on the origin, which for Forest 1
 * collapsed a 960-unit-wide map into a single small island -- the models' own coordinates are
 * section-relative, not world-relative.
 */
fun parseNinjaRoomStaticModels(cursor: Cursor): List<NjObject> {
    cursor.seekEnd(16)
    val ptr = cursor.int()
    cursor.seekStart(ptr)

    val sectionCount = cursor.int()
    cursor.seek(4)
    val sectionsOffset = cursor.int()
    // Texture pack offset follows; unused, textures come from the separate generated .xvm file.

    val roots = mutableListOf<NjObject>()

    // Sections are NOT laid out at a fixed stride -- they're read sequentially, one 60-byte
    // record after another, with the cursor saved before each section's model-table excursions
    // and restored right after so the next section's record picks up exactly where this one's
    // left off (mirrors NinjaRoom.js's own saveOfs/seekSet(saveOfs) dance).
    cursor.seekStart(sectionsOffset)

    for (i in 0 until sectionCount) {
        val sectionRoots = mutableListOf<NjObject>()
        cursor.seek(4) // Section id.
        val sectionPosition = cursor.vec3Float()
        val sectionRotation = Vec3(
            angleToRad(cursor.int()).toFloat(),
            angleToRad(cursor.int()).toFloat(),
            angleToRad(cursor.int()).toFloat(),
        )
        cursor.seek(4) // Radius.
        val staticModelOffset = cursor.int()
        cursor.seek(4) // Attribute table offset.
        val animatedModelOffset = cursor.int()
        val staticModelCount = cursor.int()
        cursor.seek(4) // Attribute count.
        val animatedModelCount = cursor.int()
        cursor.seek(4) // Section end offset.

        val nextSectionPos = cursor.position

        cursor.seekStart(staticModelOffset)

        for (k in 0 until staticModelCount) {
            val meshOffset = cursor.int()
            cursor.seek(0x2c)
            val nextEntryPos = cursor.position

            // Some meshes across the wider map set don't parse cleanly (an area-format quirk
            // that doesn't show up in the outer section table, only once chunk-parsing gets into
            // the individual mesh) -- skip just that one mesh rather than losing the whole area.
            try {
                cursor.seekStart(meshOffset)
                sectionRoots.addAll(parseNjObjectSiblings(cursor))
            } catch (e: Throwable) {
                console.warn("Skipping unparseable static model at offset $meshOffset (section $i, entry $k)", e)
            }

            cursor.seekStart(nextEntryPos)
        }

        // Also fold in "animated model" entries -- besides actually-animated scenery, this turns
        // out to be where a map's main terrain mesh usually lives (see Forest 1). The animation
        // itself isn't applied, just the mesh's rest-pose geometry.
        cursor.seekStart(animatedModelOffset)

        for (k in 0 until animatedModelCount) {
            val meshOffset = cursor.int()
            cursor.seek(4) // Animation offset, unused.
            cursor.seek(0x34)
            val nextEntryPos = cursor.position

            try {
                cursor.seekStart(meshOffset)
                sectionRoots.addAll(parseNjObjectSiblings(cursor))
            } catch (e: Throwable) {
                console.warn("Skipping unparseable animated model at offset $meshOffset (section $i, entry $k)", e)
            }

            cursor.seekStart(nextEntryPos)
        }

        // Wrap rather than mutate: NjObject.position is a val and Vec3 immutable, and a parent
        // node composes the section's rotation with each model's own transform correctly, which
        // adding offsets component-wise would not.
        if (sectionRoots.isNotEmpty()) {
            roots.add(
                NjObject(
                    offset = 0,
                    evaluationFlags = NinjaEvaluationFlags(0),
                    model = null,
                    position = sectionPosition,
                    rotation = sectionRotation,
                    scale = Vec3(1f, 1f, 1f),
                    children = sectionRoots,
                )
            )
        }

        cursor.seekStart(nextSectionPos)
    }

    return roots
}

/**
 * Mirrors psolib's own (private) NJCM sibling-object parser -- see Ninja.kt's parseSiblingObjects.
 * Not private: also used by Psov2StageGeometry.kt and Psov2NpcGeometry.kt, since stage sections'
 * static/animated models and the standalone-.rel city NPCs are the exact same bone/chunk object
 * graph format room sections' are.
 *
 * Creates one fresh polygon-list cache (see [parseNjModel]'s cachedChunks parameter) and threads
 * it through the whole recursive traversal -- matching psolib's own parseNj, which creates exactly
 * one such map per top-level call (Ninja.kt:14) and shares it across every object in the tree.
 * NJCM's CachePolygonList/DrawPolygonList chunk pair lets one bone cache a strip list that a LATER
 * bone elsewhere in the same tree replays without redefining it, so the cache has to survive across
 * sibling/child boundaries, not reset per object -- multi-part skinned character models (city NPCs
 * in particular) lean on this heavily for their arm/leg/torso pieces; a fresh map per object left
 * every DrawPolygonList chunk pointing at a cache index that was never populated, silently dropping
 * most of the body (psolib logs "pointed to nonexistent cache index" and skips, no crash).
 */
fun parseNjObjectSiblings(cursor: Cursor): MutableList<NjObject> =
    parseNjObjectSiblings(cursor, mutableMapOf())

private fun parseNjObjectSiblings(
    cursor: Cursor,
    cachedChunks: MutableMap<Int, List<NjChunk>>,
): MutableList<NjObject> {
    val offset = cursor.position
    val evalFlags = cursor.int()
    val modelOffset = cursor.int()
    val pos = cursor.vec3Float()
    val rotation = Vec3(
        angleToRad(cursor.int()),
        angleToRad(cursor.int()),
        angleToRad(cursor.int()),
    )
    val scale = cursor.vec3Float()
    val childOffset = cursor.int()
    val siblingOffset = cursor.int()

    val model: NjModel? = if (modelOffset == 0) {
        null
    } else {
        cursor.seekStart(modelOffset)
        parseNjModel(cursor, cachedChunks)
    }

    val children = if (childOffset == 0) {
        mutableListOf()
    } else {
        cursor.seekStart(childOffset)
        parseNjObjectSiblings(cursor, cachedChunks)
    }

    val siblings = if (siblingOffset == 0) {
        mutableListOf()
    } else {
        cursor.seekStart(siblingOffset)
        parseNjObjectSiblings(cursor, cachedChunks)
    }

    val obj = NjObject(offset, NinjaEvaluationFlags(evalFlags), model, pos, rotation, scale, children)
    siblings.add(0, obj)
    return siblings
}
