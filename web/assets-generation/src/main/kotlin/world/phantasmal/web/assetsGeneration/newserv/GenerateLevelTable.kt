package world.phantasmal.web.assetsGeneration.newserv

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts newserv's `level-table-v4.json` (the Blue Burst client's own PlyLevelTbl, exported to
 * JSON by newserv -- MIT, see data/newserv-tables/) into the mobile game's generated Kotlin level
 * table.
 *
 * What gets emitted is the RAW cumulative per-level table: base stats plus every level's gains,
 * per class, levels 1-200, plus the shared lifetime-EXP-per-level totals (the EXP field in the
 * source is already cumulative -- level 2 reads 50, level 3 reads 200, matching the wiki's
 * published totals verbatim).
 *
 * The runtime (Leveling.kt) anchors these curves to the wiki's published level-1/level-200
 * endpoints rather than using them raw, because the BB client derives displayed HP/TP/ATA from
 * the table through per-profession factors that live in the client binary, not in this data --
 * the table's MST/EVP/DFP match the wiki's naked level-200 figures exactly, while HP and ATA
 * need that anchoring (verified against wiki.pioneer2.net class pages for all twelve classes).
 *
 * Run with `./gradlew :web:assets-generation:generateNewservTables`.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: GenerateLevelTable <level-table-v4.json> <out.kt>" }
    val sourceFile = File(args[0])
    val outFile = File(args[1])

    // The source is JSON with // comments and hex integer literals; normalize before parsing.
    val text = sourceFile.readText()
        .replace(Regex("//[^\n]*"), "")
        .replace(Regex("0x([0-9A-Fa-f]+)")) { m -> m.groupValues[1].toLong(16).toString() }
    val root = Json.parseToJsonElement(text).jsonObject

    // newserv's char class order (StaticGameData.cc name_for_char_class) -- BB appends the three
    // classes it introduced after the nine V2 ones.
    val classOrder = listOf(
        "HUmar", "HUnewearl", "HUcast", "RAmar", "RAcast", "RAcaseal",
        "FOmarl", "FOnewm", "FOnewearl", "HUcaseal", "FOmar", "RAmarl",
    )

    val baseStats = root.getValue("BaseStats").jsonArray
    val levelDeltas = root.getValue("LevelDeltas").jsonArray
    require(baseStats.size == classOrder.size && levelDeltas.size == classOrder.size)

    val statKeys = listOf("ATP", "MST", "EVP", "DFP", "ATA", "HP")

    // exp totals are identical across classes (asserted below); read once from class 0.
    val expTotals = levelDeltas[0].jsonArray.map { it.jsonObject.getValue("EXP").jsonPrimitive.int }
    for (ci in classOrder.indices) {
        val classExp = levelDeltas[ci].jsonArray.map { it.jsonObject.getValue("EXP").jsonPrimitive.int }
        require(classExp == expTotals) { "EXP curve differs for ${classOrder[ci]}" }
    }
    require(expTotals.size == 200 && expTotals[0] == 0 && expTotals[1] == 50)

    // Cumulative per-class curves: base + running sum of deltas.
    val curves: Map<String, Map<String, IntArray>> = classOrder.withIndex().associate { (ci, cls) ->
        val base = baseStats[ci].jsonObject
        val rows = levelDeltas[ci].jsonArray
        require(rows.size == 200)
        cls to statKeys.associateWith { key ->
            var running = base[key]?.jsonPrimitive?.int ?: 0
            IntArray(200) { level ->
                running += rows[level].jsonObject[key]?.jsonPrimitive?.int ?: 0
                running
            }
        }
    }

    fun IntArray.literal(): String = buildString {
        append("intArrayOf(")
        this@literal.forEachIndexed { i, v ->
            if (i > 0) append(", ")
            if (i % 20 == 0 && i > 0) append("\n            ")
            append(v)
        }
        append(")")
    }

    outFile.writeText(buildString {
        appendLine("// GENERATED FILE -- do not edit. Produced by :web:assets-generation's")
        appendLine("// GenerateLevelTable.kt from data/newserv-tables/level-table-v4.json (newserv, MIT).")
        appendLine("// See that file's header comment for what these numbers are and how they're used.")
        appendLine("package world.phantasmal.web.mobileGame.player")
        appendLine()
        appendLine("internal object GeneratedLevelTable {")
        appendLine("    /** Lifetime EXP required to BE level N (index N-1); one shared curve, per the data. */")
        appendLine("    val expTotals: IntArray = ${IntArray(200) { expTotals[it] }.literal()}")
        appendLine()
        for (key in statKeys) {
            appendLine("    val ${key.lowercase()}: Map<String, IntArray> = mapOf(")
            for (cls in classOrder) {
                appendLine("        \"$cls\" to ${curves.getValue(cls).getValue(key).literal()},")
            }
            appendLine("    )")
            appendLine()
        }
        appendLine("}")
    })

    println("Wrote ${outFile.absolutePath} (${outFile.length() / 1024} KB)")
    println("Sanity: HUmar EVP L200 = ${curves.getValue("HUmar").getValue("EVP")[199]} (wiki: 682)")
    println("Sanity: EXP total L200 = ${expTotals[199]}")
}
