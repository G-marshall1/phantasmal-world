package world.phantasmal.web.assetsGeneration.quests

import java.io.File
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.quest.parseQstToQuest

/**
 * Research tool: dumps one .qst inside out -- identity, areas, placements, events, and the
 * full disassembled script -- so the mobile game's quest runtime can be built against what the
 * real quests actually contain rather than guesses.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: error("Pass a .qst path")
    val bytes = File(path).readBytes()
    val buffer = Buffer.fromByteArray(bytes)
    val questData = parseQstToQuest(buffer.cursor(), lenient = true).unwrap()
    val quest = questData.quest

    println("=== ${quest.name} (id ${quest.id}, episode ${quest.episode}) ===")
    println("--- short ---")
    println(quest.shortDescription)
    println("--- long ---")
    println(quest.longDescription)
    println("--- map designations (area -> variant) ---")
    println(quest.mapDesignations)
    println("--- npcs by area ---")
    for ((area, npcs) in quest.npcs.groupBy { it.areaId }) {
        println("  area $area: ${npcs.size} npcs: " +
            npcs.groupingBy { it.type.name }.eachCount())
    }
    println("--- objects by area ---")
    for ((area, objects) in quest.objects.groupBy { it.areaId }) {
        println("  area $area: ${objects.size} objects: " +
            objects.groupingBy { it.type.name }.eachCount())
    }
    println("--- events ---")
    println("  ${quest.events.size} events")
    println("--- segments ---")
    for (segment in quest.bytecodeIr.segments) {
        val kind = segment::class.simpleName
        val extra = when (segment) {
            is world.phantasmal.psolib.asm.StringSegment -> " value=" + segment.value.take(60).replace("\n", "\\n")
            is world.phantasmal.psolib.asm.DataSegment -> " bytes=" + segment.data.size
            else -> ""
        }
        println("  labels=${segment.labels} $kind$extra")
    }
    println("--- script ---")
    for (line in world.phantasmal.psolib.asm.disassemble(quest.bytecodeIr, inlineStackArgs = true)) {
        println(line)
    }
}
