package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * A player character's chosen appearance. [sectionId] indexes a real chest-emblem texture slot in
 * the body archives (ordinal-offset in CharacterClassAssetLoader), so with the personal-asset
 * overlay's full archives the ID's emblem renders on the torso. It isn't a picker: the game
 * derives it from the character's name (see computeSectionId).
 */
data class PlayerAppearance(
    val characterClass: CharacterClass,
    val sectionId: SectionId = SectionId.Viridia,
    val headIndex: Int = 0,
    val hairIndex: Int = 0,
    val accessoryEquipped: Boolean = false,
    /**
     * Which of the class's real body-texture variants to wear (0-based; see
     * CharacterClass.bodyStyleCount -- 18 for most classes, 25 for the casts). The full variant
     * sets ship in the bundled per-class texture archives; only section-ID recolors don't.
     */
    val bodyIndex: Int = 0,
    /** Visual-only proportion scales, 1.0 = the authored model. */
    val proportionHeight: Double = 1.0,
    val proportionWidth: Double = 1.0,
) {
    companion object {
        /** Reproduces the mobile game's original fully-hardcoded player exactly. */
        val DEFAULT = PlayerAppearance(CharacterClass.HUmar)
    }
}
