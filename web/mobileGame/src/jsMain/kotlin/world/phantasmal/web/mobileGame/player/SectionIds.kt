package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * The game's own section-ID assignment: every character of the name contributes its character
 * code modulo 10, Blue Burst adds a per-class offset (so the same name rolls different IDs on
 * different classes), and the sum's last digit indexes the ID table -- whose canonical order is
 * exactly [SectionId]'s declaration order, Viridia (0) through Whitill (9).
 */
fun computeSectionId(name: String, characterClass: CharacterClass): SectionId {
    val nameSum = name.sumOf { it.code % 10 }
    val offset = CLASS_SECTION_OFFSETS[characterClass.slug] ?: 0
    return SectionId.entries[(nameSum + offset) % 10]
}

/** Blue Burst's per-class offsets, keyed by class slug. */
private val CLASS_SECTION_OFFSETS: Map<String, Int> = mapOf(
    "humar" to 5,
    "hunewearl" to 6,
    "hucast" to 7,
    "hucaseal" to 4,
    "ramar" to 8,
    "ramarl" to 6,
    "racast" to 9,
    "racaseal" to 0,
    "fomar" to 5,
    "fomarl" to 1,
    "fonewm" to 2,
    "fonewearl" to 3,
)

/** Each ID's badge color, for drawing the summary screen's marker. */
val SECTION_ID_COLORS: Map<SectionId, String> = mapOf(
    SectionId.Viridia to "#2e8b3a",
    SectionId.Greenill to "#7ed957",
    SectionId.Skyly to "#5bc8f5",
    SectionId.Bluefull to "#2a4fd7",
    SectionId.Purplenum to "#9040d0",
    SectionId.Pinkal to "#f272c8",
    SectionId.Redria to "#e03a2e",
    SectionId.Oran to "#f5a02a",
    SectionId.Yellowboze to "#f5e02a",
    SectionId.Whitill to "#f2f2f2",
)
