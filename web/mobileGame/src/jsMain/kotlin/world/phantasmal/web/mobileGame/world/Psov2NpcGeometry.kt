package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.cursor.Cursor
import world.phantasmal.psolib.fileFormats.ninja.NjObject

/**
 * The 14 city NPCs sourced as standalone ".rel" entries (NPC_REL_SPECS in :web:assets-generation's
 * NpcSpecs.kt) locate their root NJCM object graph via a two-pointer walk from the file's own
 * footer, rather than the section table Room/Stage files use: 16 bytes before the end of the file
 * is a table pointer, which points at a second pointer, which points directly at the root object
 * header. Verified byte-for-byte against npc_a00_data.rel before writing this. Once the root is
 * found, the object graph itself is the exact same bone/chunk format `parseNjObjectSiblings`
 * already reads for every other Ninja model.
 */
fun parseNpcRelObject(cursor: Cursor): NjObject {
    cursor.seekEnd(16)
    val tablePtr = cursor.int()
    cursor.seekStart(tablePtr)
    val njPtr = cursor.int()
    cursor.seekStart(njPtr)
    return parseNjObjectSiblings(cursor).first()
}
