package world.phantasmal.web.assetsGeneration.newserv

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts newserv's `battle-params.json` (the client's own BattleParamEntry sets, MIT -- see
 * data/newserv-tables/) into the mobile game's generated enemy stat table: every Episode 1
 * enemy's HP, ATP, DFP, ATA, EVP, LCK, EXP, meseta and elemental resistances, per difficulty,
 * from the **Solo** set -- the party size this game runs at.
 *
 * The source keys each sub-table (Stats / ResistData / AttackData) by its own per-entry enemy
 * list, because the client indexes each table separately; one enemy's full record is the join
 * of the entries that name it.
 *
 * Run with `./gradlew :web:assets-generation:generateNewservEnemyStats`.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: GenerateEnemyStats <battle-params.json> <out.kt>" }

    val text = File(args[0]).readText()
        .replace(Regex("//[^\n]*"), "")
        .replace(Regex("0x([0-9A-Fa-f]+)")) { m -> m.groupValues[1].toLong(16).toString() }
    val root = Json.parseToJsonElement(text).jsonObject

    val episode = root.getValue("Episode1-Solo").jsonObject
    val difficulties = listOf("Normal", "Hard", "Very Hard", "Ultimate")

    class Row(
        var hp: Int = 0, var atp: Int = 0, var dfp: Int = 0, var ata: Int = 0,
        var evp: Int = 0, var lck: Int = 0, var exp: Int = 0, var meseta: Int = 0,
        var efr: Int = 0, var eic: Int = 0, var eth: Int = 0, var elt: Int = 0, var edk: Int = 0,
    )

    val byDifficulty = linkedMapOf<String, MutableMap<String, Row>>()

    for (difficulty in difficulties) {
        val rows = linkedMapOf<String, Row>()
        for (entry in episode.getValue(difficulty).jsonArray) {
            val e = entry.jsonObject

            fun section(name: String): JsonObject = e.getValue(name).jsonObject
            fun JsonObject.enemies(): List<String> =
                this["Enemies"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            fun JsonObject.i(key: String): Int = this[key]?.jsonPrimitive?.int ?: 0

            val stats = section("Stats")
            for (name in stats.enemies()) {
                rows.getOrPut(name) { Row() }.apply {
                    hp = stats.i("HP"); atp = stats.i("ATP"); dfp = stats.i("DFP")
                    ata = stats.i("ATA"); evp = stats.i("EVP"); lck = stats.i("LCK")
                    exp = stats.i("EXP"); meseta = stats.i("Meseta")
                }
            }
            val resist = section("ResistData")
            for (name in resist.enemies()) {
                rows.getOrPut(name) { Row() }.apply {
                    efr = resist.i("EFR"); eic = resist.i("EIC"); eth = resist.i("ETH")
                    elt = resist.i("ELT"); edk = resist.i("EDK")
                }
            }
        }
        byDifficulty[difficulty] = rows
    }

    val outFile = File(args[1])
    outFile.writeText(buildString {
        appendLine("// GENERATED FILE -- do not edit. Produced by :web:assets-generation's")
        appendLine("// GenerateEnemyStats.kt from data/newserv-tables/battle-params.json (newserv, MIT):")
        appendLine("// Episode 1, Solo party size, all four difficulties.")
        appendLine("package world.phantasmal.web.mobileGame.world")
        appendLine()
        appendLine("internal class GeneratedEnemyRow(")
        appendLine("    val hp: Int, val atp: Int, val dfp: Int, val ata: Int, val evp: Int, val lck: Int,")
        appendLine("    val exp: Int, val meseta: Int,")
        appendLine("    val efr: Int, val eic: Int, val eth: Int, val elt: Int, val edk: Int,")
        appendLine(")")
        appendLine()
        appendLine("internal object GeneratedEnemyStats {")
        for ((difficulty, rows) in byDifficulty) {
            val propName = when (difficulty) {
                "Very Hard" -> "veryHard"
                else -> difficulty.lowercase()
            }
            appendLine("    val $propName: Map<String, GeneratedEnemyRow> = mapOf(")
            for ((name, r) in rows.toSortedMap()) {
                appendLine(
                    "        \"$name\" to GeneratedEnemyRow(${r.hp}, ${r.atp}, ${r.dfp}, ${r.ata}, " +
                        "${r.evp}, ${r.lck}, ${r.exp}, ${r.meseta}, " +
                        "${r.efr}, ${r.eic}, ${r.eth}, ${r.elt}, ${r.edk}),"
                )
            }
            appendLine("    )")
            appendLine()
        }
        appendLine("}")
    })

    println("Wrote ${outFile.absolutePath} (${outFile.length() / 1024} KB)")
    val booma = byDifficulty.getValue("Normal").getValue("BOOMA")
    println("Sanity: BOOMA Normal = hp ${booma.hp} atp ${booma.atp} ata ${booma.ata} exp ${booma.exp}")
}
