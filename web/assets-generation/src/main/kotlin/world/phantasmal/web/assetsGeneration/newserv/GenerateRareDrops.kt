package world.phantasmal.web.assetsGeneration.newserv

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Converts newserv's `rare-table-v4.json` (the vanilla Blue Burst rare drop table, MIT -- see
 * data/newserv-tables/) into the mobile game's generated rare chart: Episode 1, Normal
 * difficulty, every section ID, keyed by the client's enemy names and box areas, each cell a
 * (numerator, denominator, item code, item name) list.
 *
 * NOTE: this is the *vanilla* table. The wiki the user treats as authority (Ephinea's) runs
 * customized drops, and the hand-built Forest chart in DropTables.kt transcribes that wiki --
 * so the runtime prefers the hand chart where one exists and falls back to this for everything
 * beyond it (the areas the wiki was never transcribed for).
 *
 * Run with `./gradlew :web:assets-generation:generateNewservRareDrops`.
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: GenerateRareDrops <rare-table-v4.json> <names-v4.json> <out.kt>" }

    fun loadJson(path: String): JsonObject {
        val text = File(path).readText()
            .replace(Regex("//[^\n]*"), "")
            .replace(Regex("0x([0-9A-Fa-f]+)")) { m -> m.groupValues[1].toLong(16).toString() }
            .replace(Regex(",(\\s*[}\\]])"), "$1")
        return Json.parseToJsonElement(text).jsonObject
    }

    val chart = loadJson(args[0])
        .getValue("Normal").jsonObject
        .getValue("Episode1").jsonObject
        .getValue("Normal").jsonObject
    val names = loadJson(args[1]).mapValues { it.value.jsonPrimitive.content }
    val outFile = File(args[2])

    // newserv's one spelling quirk vs our SectionId enum.
    fun sectionName(s: String): String = if (s == "Greennill") "Greenill" else s

    class Cell(val numerator: Int, val denominator: Int, val code: String, val name: String)

    val bySection = sortedMapOf<String, MutableMap<String, MutableList<Cell>>>()
    var unnamed = 0

    for ((rawSection, enemies) in chart) {
        val section = sectionName(rawSection)
        val sectionMap = bySection.getOrPut(section) { sortedMapOf() }
        for ((enemyOrBox, cells) in enemies.jsonObject) {
            for (cell in cells.jsonArray) {
                val pair = cell.jsonArray
                val fraction = pair[0].jsonPrimitive.content.split("/")
                val codeValue = pair[1].jsonPrimitive.long
                val code = codeValue.toString(16).uppercase().padStart(6, '0')
                val itemName = names[code] ?: run { unnamed++; "item $code" }
                sectionMap.getOrPut(enemyOrBox) { mutableListOf() }.add(
                    Cell(fraction[0].toInt(), fraction[1].toInt(), code, itemName)
                )
            }
        }
    }

    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    outFile.writeText(buildString {
        appendLine("// GENERATED FILE -- do not edit. Produced by :web:assets-generation's")
        appendLine("// GenerateRareDrops.kt from data/newserv-tables/rare-table-v4.json and names-v4.json")
        appendLine("// (newserv, MIT): vanilla BB Episode 1 Normal-difficulty rare drops.")
        appendLine("package world.phantasmal.web.mobileGame.player")
        appendLine()
        appendLine("internal class GeneratedRareDrop(")
        appendLine("    val numerator: Int, val denominator: Int, val code: String, val itemName: String,")
        appendLine(")")
        appendLine()
        appendLine("internal object GeneratedRareDrops {")
        appendLine("    /** Section ID name -> client enemy name or Box-Area -> that cell's rares. */")
        appendLine("    val normal: Map<String, Map<String, List<GeneratedRareDrop>>> = mapOf(")
        for ((section, enemies) in bySection) {
            appendLine("        \"$section\" to mapOf(")
            for ((enemy, cells) in enemies) {
                val cellsText = cells.joinToString(", ") {
                    "GeneratedRareDrop(${it.numerator}, ${it.denominator}, \"${it.code}\", \"${esc(it.name)}\")"
                }
                appendLine("            \"$enemy\" to listOf($cellsText),")
            }
            appendLine("        ),")
        }
        appendLine("    )")
        appendLine("}")
    })

    println("Wrote ${outFile.absolutePath} (${outFile.length() / 1024} KB), unnamed codes: $unnamed")
    println("Sanity: Viridia BOOMA = " +
        bySection.getValue("Viridia").getValue("BOOMA").joinToString { "${it.numerator}/${it.denominator} ${it.name}" })
}
