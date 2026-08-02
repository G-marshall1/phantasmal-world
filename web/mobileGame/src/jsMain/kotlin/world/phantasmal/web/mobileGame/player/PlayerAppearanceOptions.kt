package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.viewer.models.CharacterClass

/**
 * How many head/hair mesh variants a class has, and which hair indices have a matching accessory
 * mesh -- mirrors PlayerClassSpec in :web:assets-generation's PlayerClassSpecs.kt (the real
 * source of truth; keep these two tables in sync by hand across the JVM/JS module boundary).
 *
 * Deliberately independent of CharacterClass.headStyleCount/hairStyleCount/hairStylesWithAccessory
 * (in :web:rendering), which describe the TRUE full PSO game's data, not what psov2 actually
 * shipped and this project actually converted. A future maintainer must not "simplify" by
 * switching back to those fields -- they're the wrong numbers for what's on disk here.
 */
class PlayerAppearanceOptions(
    val headCount: Int,
    val hairCount: Int,
    val accessoryHairIndices: Set<Int>,
)

val PLAYER_APPEARANCE_OPTIONS: Map<CharacterClass, PlayerAppearanceOptions> = mapOf(
    CharacterClass.HUmar to PlayerAppearanceOptions(headCount = 1, hairCount = 7, accessoryHairIndices = setOf(6)),
    CharacterClass.HUnewearl to PlayerAppearanceOptions(headCount = 1, hairCount = 10, accessoryHairIndices = emptySet()),
    CharacterClass.HUcast to PlayerAppearanceOptions(headCount = 5, hairCount = 0, accessoryHairIndices = emptySet()),
    CharacterClass.RAmar to PlayerAppearanceOptions(headCount = 1, hairCount = 7, accessoryHairIndices = (0..6).toSet()),
    CharacterClass.RAcast to PlayerAppearanceOptions(headCount = 5, hairCount = 0, accessoryHairIndices = emptySet()),
    CharacterClass.RAcaseal to PlayerAppearanceOptions(headCount = 5, hairCount = 0, accessoryHairIndices = emptySet()),
    CharacterClass.FOmarl to PlayerAppearanceOptions(headCount = 1, hairCount = 10, accessoryHairIndices = (0..9).toSet()),
    CharacterClass.FOnewm to PlayerAppearanceOptions(headCount = 1, hairCount = 7, accessoryHairIndices = (0..6).toSet()),
    CharacterClass.FOnewearl to PlayerAppearanceOptions(headCount = 1, hairCount = 10, accessoryHairIndices = (0..9).toSet()),
)

/** The 9 classes psov2 actually has data for -- not all 12 [CharacterClass] values. */
val CREATABLE_CLASSES: List<CharacterClass> = PLAYER_APPEARANCE_OPTIONS.keys.toList()
