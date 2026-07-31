package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.fileFormats.Vec3
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.psolib.fileFormats.ninja.angleToRad
import world.phantasmal.psolib.fileFormats.vec3Float

/**
 * One stage section's own static/animated model roots, plus the position + Y-rotation transform
 * that has to be applied on top of them by the caller. This is the one real structural difference
 * from room/dungeon areas (see Psov2AreaGeometry.kt, whose section table this is otherwise
 * byte-for-byte identical to): NinjaRoom.js bakes each model's final world coordinates directly
 * into the model itself and never touches the section's own position/rotation, but NinjaStage.js's
 * `readBone` parents each section's models under a bone carrying that section's transform, i.e.
 * stage sections (city blocks/buildings) really are separately offset/rotated pieces, unlike a
 * dungeon room's already-placed static geometry. X/Z rotation are read (matching the section
 * header layout) but -- going by NinjaStage.js's own `execute()`, which only ever calls
 * `makeRotationY` on a section's bone -- never actually applied to anything, so only Y is kept.
 */
class StageSection(
    val position: Vec3,
    val rotationY: Double,
    val roots: List<NjObject>,
)

fun parseNinjaStageSections(cursor: Cursor): List<StageSection> {
    cursor.seekEnd(16)
    val ptr = cursor.int()
    cursor.seekStart(ptr)

    val sectionCount = cursor.int()
    cursor.seek(4)
    val sectionsOffset = cursor.int()
    // Texture pack offset follows; unused, textures come from the separate generated .xvm file.

    val sections = mutableListOf<StageSection>()

    cursor.seekStart(sectionsOffset)

    for (i in 0 until sectionCount) {
        cursor.seek(4) // Section id -- psov2 buckets negative ids separately for gameplay lookups
        // it does elsewhere, but every section's geometry still needs rendering regardless, so
        // there's no need to replicate that distinction here.
        val position = cursor.vec3Float()
        cursor.seek(4) // Rotation X (unused, see class doc).
        val rotationY = angleToRad(cursor.int()).toDouble()
        cursor.seek(4) // Rotation Z (unused).
        cursor.seek(4) // Radius.
        val staticModelOffset = cursor.int()
        cursor.seek(4) // Attribute table offset.
        val animatedModelOffset = cursor.int()
        val staticModelCount = cursor.int()
        cursor.seek(4) // Attribute count.
        val animatedModelCount = cursor.int()
        cursor.seek(4) // Section end offset.

        val nextSectionPos = cursor.position

        val roots = mutableListOf<NjObject>()

        cursor.seekStart(staticModelOffset)

        for (k in 0 until staticModelCount) {
            val meshOffset = cursor.int()
            cursor.seek(0x2c)
            val nextEntryPos = cursor.position

            try {
                cursor.seekStart(meshOffset)
                roots.addAll(parseNjObjectSiblings(cursor))
            } catch (e: Throwable) {
                console.warn("Skipping unparseable static model at offset $meshOffset (section $i, entry $k)", e)
            }

            cursor.seekStart(nextEntryPos)
        }

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

        sections.add(StageSection(position, rotationY, roots))

        cursor.seekStart(nextSectionPos)
    }

    return sections
}
