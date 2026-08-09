package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.viewer.models.CharacterClass

/**
 * How many head/hair mesh variants a class has, and which hair indices have a matching accessory
 * mesh -- must match what is actually on disk under `resources/assets/player/`.
 *
 * Deliberately independent of CharacterClass.headStyleCount/hairStyleCount/hairStylesWithAccessory
 * (in :web:rendering), which describe the TRUE full PSO game's data rather than what this module
 * ships. A future maintainer must not "simplify" by switching back to those fields -- they can
 * disagree with the files present here.
 *
 * Nine of these entries mirror PlayerClassSpec in :web:assets-generation's PlayerClassSpecs.kt,
 * which is the source of truth for the psov2-converted classes; keep those two tables in sync by
 * hand across the JVM/JS module boundary. The remaining three (HUcaseal, RAmarl, FOmar) have no
 * PlayerClassSpec because the psov2 mobile generator was never given specs for them -- their
 * assets were copied in from the :web module, which ships all 12 classes. Their counts below were
 * read off the copied files, not off a spec, so re-running GeneratePsov2MobileAssets will not
 * regenerate them; the copies are the only source.
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
    CharacterClass.HUcaseal to PlayerAppearanceOptions(headCount = 5, hairCount = 0, accessoryHairIndices = emptySet()),
    CharacterClass.RAmar to PlayerAppearanceOptions(headCount = 1, hairCount = 7, accessoryHairIndices = (0..6).toSet()),
    CharacterClass.RAmarl to PlayerAppearanceOptions(headCount = 1, hairCount = 10, accessoryHairIndices = (0..9).toSet()),
    CharacterClass.RAcast to PlayerAppearanceOptions(headCount = 5, hairCount = 0, accessoryHairIndices = emptySet()),
    CharacterClass.RAcaseal to PlayerAppearanceOptions(headCount = 5, hairCount = 0, accessoryHairIndices = emptySet()),
    CharacterClass.FOmar to PlayerAppearanceOptions(headCount = 1, hairCount = 10, accessoryHairIndices = (0..9).toSet()),
    CharacterClass.FOmarl to PlayerAppearanceOptions(headCount = 1, hairCount = 10, accessoryHairIndices = (0..9).toSet()),
    CharacterClass.FOnewm to PlayerAppearanceOptions(headCount = 1, hairCount = 7, accessoryHairIndices = (0..6).toSet()),
    CharacterClass.FOnewearl to PlayerAppearanceOptions(headCount = 1, hairCount = 10, accessoryHairIndices = (0..9).toSet()),
)

/** All 12 [CharacterClass] values -- every class this module ships player meshes for. */
val CREATABLE_CLASSES: List<CharacterClass> = PLAYER_APPEARANCE_OPTIONS.keys.toList()
