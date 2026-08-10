package world.phantasmal.web.assetsGeneration.quests

import java.io.File
import world.phantasmal.psolib.asm.FloatArg
import world.phantasmal.psolib.asm.InstructionSegment
import world.phantasmal.psolib.asm.IntArg
import world.phantasmal.psolib.asm.StringArg
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.quest.Quest
import world.phantasmal.psolib.fileFormats.quest.parseQstToQuest

/**
 * Converts real .qst quest files (the Tethealla BB set in psolib's test resources -- the
 * authentic quests, scripts and all) into the mobile game's JSON: quest identity, per-area NPC
 * and object placements in the spawn-table shape the game already reads, the .dat wave events,
 * and the complete script as structured instructions for the in-game VM.
 *
 * Everything is verbatim from the quest data: the placements are the quest's own, the dialogue
 * strings are the script's own, and the reward logic is whatever the script does.
 */
fun main(args: Array<String>) {
    val repoRoot = File(args.getOrNull(0) ?: ".")
    val questDir = File(
        repoRoot,
        "psolib/src/commonTest/resources/tethealla_v0.143_quests",
    )
    val outDir = File(repoRoot, "web/mobileGame/src/jsMain/resources/assets/quests")
    outDir.mkdirs()

    // The government arc: Episode 1's story retold through the Principal's jobs.
    val quests = listOf(
        "gov1_1" to "princ/ep1/1-1.qst",
        "gov1_2" to "princ/ep1/1-2.qst",
        "gov1_3" to "princ/ep1/1-3.qst",
        "gov2_1" to "princ/ep1/2-1.qst",
        "gov2_2" to "princ/ep1/2-2.qst",
        "gov2_3" to "princ/ep1/2-3.qst",
        "gov2_4" to "princ/ep1/2-4.qst",
        "gov3_1" to "princ/ep1/3-1.qst",
        "gov3_2" to "princ/ep1/3-2.qst",
        "gov3_3" to "princ/ep1/3-3.qst",
        "gov4_1" to "princ/ep1/4-1.qst",
        "gov4_2" to "princ/ep1/4-2.qst",
        "gov4_3" to "princ/ep1/4-3.qst",
        "gov4_4" to "princ/ep1/4-4.qst",
        "gov4_5" to "princ/ep1/4-5.qst",
    )

    val index = StringBuilder("[\n")
    for ((slug, relative) in quests) {
        val file = File(questDir, relative)
        val questData = parseQstToQuest(
            Buffer.fromByteArray(file.readBytes()).cursor(), lenient = true,
        ).unwrap()
        val quest = questData.quest
        File(outDir, "$slug.json").writeText(questJson(quest))
        index.append("""  {"slug":${json(slug)},"name":${json(quest.name)},"short":${json(quest.shortDescription)}},""").append('\n')
        println("Converted $relative -> $slug.json (${quest.name})")
    }
    index.append("]\n")
    File(outDir, "index.json").writeText(index.toString().replace(",\n]", "\n]"))
}

private fun questJson(quest: Quest): String = buildString {
    append("{\n")
    append("\"name\":${json(quest.name)},\n")
    append("\"short\":${json(quest.shortDescription)},\n")
    append("\"long\":${json(quest.longDescription)},\n")

    append("\"npcs\":[\n")
    appendList(quest.npcs.map { npc ->
        """{"type":${json(npc.type.name)},"area":${npc.areaId},"section":${npc.sectionId},""" +
            """"x":${npc.position.x},"y":${npc.position.y},"z":${npc.position.z},""" +
            """"yaw":${npc.rotation.y},"wave":${npc.wave},"script":${npc.scriptLabel}}"""
    })
    append("],\n")

    append("\"objects\":[\n")
    appendList(quest.objects.map { obj ->
        // The record's typed parameter block, same shape the free-roam spawn tables emit:
        // three floats at 40 and three ints at 52 (door/switch ids ride the first int).
        val paramsF = listOf(40, 44, 48).map { obj.data.getFloat(it) }
        val paramsI = listOf(52, 56, 60).map { obj.data.getInt(it) }
        """{"type":${json(obj.type.name)},"typeId":${obj.typeId},"area":${obj.areaId},""" +
            """"section":${obj.sectionId},"x":${obj.position.x},"y":${obj.position.y},""" +
            """"z":${obj.position.z},"yaw":${obj.rotation.y},""" +
            """"paramsF":[${paramsF.joinToString(",")}],""" +
            """"paramsI":[${paramsI.joinToString(",")}]}"""
    })
    append("],\n")

    append("\"events\":[\n")
    appendList(quest.events.map { event ->
        """{"id":${event.id},"section":${event.sectionId},"area":${event.areaId},""" +
            """"wave":${event.wave},"delay":${event.delay},""" +
            """"actions":${eventActions(event)}}"""
    })
    append("],\n")

    append("\"script\":{\n")
    appendList(
        quest.bytecodeIr.segments.filterIsInstance<InstructionSegment>().map { segment ->
            segment.labels.joinToString(",") { label ->
                json(label.toString()) + ":" + segmentJson(segment)
            }
        },
    )
    append("}\n")
    append("}\n")
}

private fun eventActions(event: world.phantasmal.psolib.fileFormats.quest.DatEvent): String =
    event.actions.joinToString(",", "[", "]") { action ->
        when (action) {
            is world.phantasmal.psolib.fileFormats.quest.DatEventAction.SpawnNpcs ->
                """{"kind":"spawn","section":${action.sectionId},"appearFlag":${action.appearFlag}}"""
            is world.phantasmal.psolib.fileFormats.quest.DatEventAction.Unlock ->
                """{"kind":"unlock","door":${action.doorId}}"""
            is world.phantasmal.psolib.fileFormats.quest.DatEventAction.Lock ->
                """{"kind":"lock","door":${action.doorId}}"""
            is world.phantasmal.psolib.fileFormats.quest.DatEventAction.TriggerEvent ->
                """{"kind":"trigger","event":${action.eventId}}"""
        }
    }

private fun segmentJson(segment: InstructionSegment): String =
    segment.instructions.joinToString(",", "[", "]") { instruction ->
        val argsJson = instruction.args.joinToString(",", "[", "]") { arg ->
            when (arg) {
                is IntArg -> arg.value.toString()
                is FloatArg -> arg.value.toString()
                is StringArg -> json(arg.value)
                else -> json(arg.value.toString())
            }
        }
        """{"op":${json(instruction.opcode.mnemonic)},"args":$argsJson}"""
    }

private fun StringBuilder.appendList(items: List<String>) {
    items.forEachIndexed { index, item ->
        append(item)
        if (index < items.size - 1) append(',')
        append('\n')
    }
}

private fun json(value: String): String = buildString {
    append('"')
    for (ch in value) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
    append('"')
}
