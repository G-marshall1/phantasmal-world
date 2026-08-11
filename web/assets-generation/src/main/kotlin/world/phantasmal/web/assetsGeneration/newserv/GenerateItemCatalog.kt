package world.phantasmal.web.assetsGeneration.newserv

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Converts newserv's `item-parameter-table-bb-v4.json` + `names-v4.json` (the BB client's own
 * ItemPMT and item names, MIT -- see data/newserv-tables/) into the mobile game's generated item
 * catalogue: every weapon in the series this game can animate, and every frame and barrier, with
 * the game's true stats, stat ranges, grind caps, equip requirements and star ratings.
 *
 * Weapon item codes are 00SSNN -- SS selects the weapon series (01 sabers .. 0C wands, then one
 * series per rare family), NN the tier within it. A series maps to the WeaponType whose motion
 * set it swings with; series this game has no motion set for are skipped and reported.
 *
 * Stars come from the shared StarValues array indexed by `item.ID - StarValueBaseIndex`, matching
 * how the client itself resolves rarity.
 *
 * Run with `./gradlew :web:assets-generation:generateNewservItemCatalog`.
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: GenerateItemCatalog <item-parameter-table-bb-v4.json> <names-v4.json> <out.kt>" }

    fun loadJson(path: String): JsonObject {
        val text = File(path).readText()
            .replace(Regex("//[^\n]*"), "")
            .replace(Regex("0x([0-9A-Fa-f]+)")) { m -> m.groupValues[1].toLong(16).toString() }
        return Json.parseToJsonElement(text).jsonObject
    }

    val table = loadJson(args[0])
    val names = loadJson(args[1]).mapValues { it.value.jsonPrimitive.content }
    val outFile = File(args[2])

    val items = table.getValue("Items").jsonObject
    val starValues = table.getValue("StarValues").jsonArray.map { it.jsonPrimitive.int }
    val starBase = table.getValue("StarValueBaseIndex").jsonPrimitive.int

    fun starsFor(id: Int): Int {
        val index = id - starBase
        return if (index in starValues.indices) starValues[index] else 0
    }

    // Weapon series -> the WeaponType enum constant whose motion set swings it.
    val seriesToType = mapOf(
        0x01 to "SABER", 0x02 to "SWORD", 0x03 to "DAGGER", 0x04 to "PARTISAN",
        0x05 to "SLICER", 0x06 to "HANDGUN", 0x07 to "RIFLE", 0x08 to "MECHGUN",
        0x09 to "SHOT", 0x0A to "CANE", 0x0B to "ROD", 0x0C to "WAND",
        0x0D to "CLAW", 0x0E to "DOUBLE_SABER", 0x0F to "CLAW", 0x10 to "KATANA",
        0x29 to "KATANA", 0x4E to "LAUNCHER", 0x5C to "TWIN_SWORD",
        // The Force rares: Psycho Wand (the game's best technique weapon) and the
        // Caduceus/Mercurius Rod line. Both series swing with the Rod motion set, and both
        // ship their own models -- see the technique-boost table in ClassRules.kt.
        0x1D to "ROD", 0x22 to "ROD",
    )

    class W(
        val code: String, val name: String, val type: String,
        val atpMin: Int, val atpMax: Int, val ata: Int, val maxGrind: Int,
        val atpRequired: Int, val ataRequired: Int, val mstRequired: Int,
        val usability: Int,
        val stars: Int, val specialId: Int,
    )

    class A(
        val name: String, val dfp: Int, val dfpRange: Int, val evp: Int, val evpRange: Int,
        val levelReq: Int, val stars: Int,
        val efr: Int, val eic: Int, val eth: Int, val elt: Int, val edk: Int,
    )

    val weapons = mutableListOf<W>()
    val frames = mutableListOf<A>()
    val barriers = mutableListOf<A>()
    val skippedSeries = sortedMapOf<Int, MutableList<String>>()

    for ((code, element) in items) {
        val e = element.jsonObject
        fun f(key: String): Int = e[key]?.jsonPrimitive?.int ?: 0
        val name = names[code] ?: continue
        // The table pads families with unnamed placeholder rows; they aren't items.
        if (name.isBlank() || name.all { it == '?' }) continue

        when {
            code.startsWith("00") && code.length == 6 -> {
                val series = code.substring(2, 4).toInt(16)
                val type = seriesToType[series]
                if (type == null) {
                    if (series != 0) skippedSeries.getOrPut(series) { mutableListOf() }.add(name)
                    continue
                }
                weapons.add(
                    W(
                        code, name, type,
                        atpMin = f("ATPMin"), atpMax = f("ATPMax"), ata = f("ATA"),
                        maxGrind = f("MaxGrind"),
                        atpRequired = f("ATPRequired"), ataRequired = f("ATARequired"),
                        usability = f("UsabilityFlags"),
                        mstRequired = f("MSTRequired"),
                        stars = starsFor(f("ID")), specialId = f("Special"),
                    )
                )
            }

            code.startsWith("0101") -> frames.add(
                A(
                    name, f("DFP"), f("DFPRange"), f("EVP"), f("EVPRange"),
                    f("RequiredLevel"), starsFor(f("ID")),
                    f("EFR"), f("EIC"), f("ETH"), f("ELT"), f("EDK"),
                )
            )

            code.startsWith("0102") -> barriers.add(
                A(
                    name, f("DFP"), f("DFPRange"), f("EVP"), f("EVPRange"),
                    f("RequiredLevel"), starsFor(f("ID")),
                    f("EFR"), f("EIC"), f("ETH"), f("ELT"), f("EDK"),
                )
            )
        }
    }

    weapons.sortBy { it.code }

    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

    outFile.writeText(buildString {
        appendLine("// GENERATED FILE -- do not edit. Produced by :web:assets-generation's")
        appendLine("// GenerateItemCatalog.kt from data/newserv-tables/item-parameter-table-bb-v4.json")
        appendLine("// and names-v4.json (newserv, MIT). Stats, ranges, grind caps, requirements and")
        appendLine("// star ratings are the BB client's own.")
        appendLine("package world.phantasmal.web.mobileGame.player")
        appendLine()
        appendLine("internal class GeneratedWeapon(")
        appendLine("    val code: String, val name: String, val type: String,")
        appendLine("    val atpMin: Int, val atpMax: Int, val ata: Int, val maxGrind: Int,")
        appendLine("    val atpRequired: Int, val ataRequired: Int, val mstRequired: Int,")
        appendLine("    val stars: Int, val specialId: Int,")
        appendLine("    /** The client's own equip mask: profession, race and sex bits -- see usableBy. */")
        appendLine("    val usability: Int,")
        appendLine(")")
        appendLine()
        appendLine("internal class GeneratedArmor(")
        appendLine("    val name: String, val dfp: Int, val dfpRange: Int, val evp: Int, val evpRange: Int,")
        appendLine("    val levelReq: Int, val stars: Int,")
        appendLine("    val efr: Int, val eic: Int, val eth: Int, val elt: Int, val edk: Int,")
        appendLine(")")
        appendLine()
        appendLine("internal object GeneratedItemCatalog {")
        appendLine("    val weapons: List<GeneratedWeapon> = listOf(")
        for (w in weapons) {
            appendLine(
                "        GeneratedWeapon(\"${w.code}\", \"${esc(w.name)}\", \"${w.type}\", " +
                    "${w.atpMin}, ${w.atpMax}, ${w.ata}, ${w.maxGrind}, " +
                    "${w.atpRequired}, ${w.ataRequired}, ${w.mstRequired}, ${w.stars}, ${w.specialId}, ${w.usability}),"
            )
        }
        appendLine("    )")
        appendLine()
        for ((listName, list) in listOf("frames" to frames, "barriers" to barriers)) {
            appendLine("    val $listName: List<GeneratedArmor> = listOf(")
            for (a in list) {
                appendLine(
                    "        GeneratedArmor(\"${esc(a.name)}\", ${a.dfp}, ${a.dfpRange}, ${a.evp}, ${a.evpRange}, " +
                        "${a.levelReq}, ${a.stars}, ${a.efr}, ${a.eic}, ${a.eth}, ${a.elt}, ${a.edk}),"
                )
            }
            appendLine("    )")
            appendLine()
        }
        appendLine("}")
    })

    println("Wrote ${outFile.absolutePath} (${outFile.length() / 1024} KB)")
    println("weapons=${weapons.size} frames=${frames.size} barriers=${barriers.size}")
    println("Sanity: Saber = ${weapons.find { it.name == "Saber" }?.let { "${it.atpMin}-${it.atpMax} ATA ${it.ata} grind ${it.maxGrind} stars ${it.stars}" }}")
    println("Sanity: YAMIGARASU = ${weapons.find { it.name == "YAMIGARASU" }?.let { "${it.atpMin}-${it.atpMax} ATA ${it.ata} stars ${it.stars}" }}")
    println("Skipped series (no motion set): ${skippedSeries.size} -> " +
        skippedSeries.entries.take(30).joinToString { "0x%02X %s".format(it.key, it.value.first()) })
}
