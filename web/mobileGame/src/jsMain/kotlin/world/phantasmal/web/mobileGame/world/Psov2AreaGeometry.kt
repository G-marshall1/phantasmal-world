package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.fileFormats.Vec3
import world.phantasmal.psolib.fileFormats.ninja.NinjaEvaluationFlags
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
 * per-format version tag. Section position/rotation is intentionally not applied here, matching
 * NinjaRoom.js's own `execute()`, which never uses it either: each static model's own bone tree
 * root is already in final, section-relative-baked coordinates.
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
        cursor.seek(4) // Section id.
        cursor.seek(12) // Position.
        cursor.seek(12) // Rotation.
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
                roots.addAll(parseNjObjectSiblings(cursor))
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
                roots.addAll(parseNjObjectSiblings(cursor))
            } catch (e: Throwable) {
                console.warn("Skipping unparseable animated model at offset $meshOffset (section $i, entry $k)", e)
            }

            cursor.seekStart(nextEntryPos)
        }

        cursor.seekStart(nextSectionPos)
    }

    return roots
}

/**
 * Mirrors psolib's own (private) NJCM sibling-object parser -- see Ninja.kt's parseSiblingObjects.
 * Not private: also used by Psov2StageGeometry.kt, since stage sections' static/animated models
 * are the exact same bone/chunk object graph format room sections' are.
 */
fun parseNjObjectSiblings(cursor: Cursor): MutableList<NjObject> {
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
        parseNjModel(cursor, mutableMapOf())
    }

    val children = if (childOffset == 0) {
        mutableListOf()
    } else {
        cursor.seekStart(childOffset)
        parseNjObjectSiblings(cursor)
    }

    val siblings = if (siblingOffset == 0) {
        mutableListOf()
    } else {
        cursor.seekStart(siblingOffset)
        parseNjObjectSiblings(cursor)
    }

    val obj = NjObject(offset, NinjaEvaluationFlags(evalFlags), model, pos, rotation, scale, children)
    siblings.add(0, obj)
    return siblings
}
