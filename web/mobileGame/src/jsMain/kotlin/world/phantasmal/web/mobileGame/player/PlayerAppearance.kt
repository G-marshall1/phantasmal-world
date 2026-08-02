package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * A player character's chosen appearance. [sectionId] is stored for lore/future-proofing but has
 * no visual effect today -- verified psov2 ships exactly one texture color per class, no real
 * section-ID recolor art (see PlayerAppearanceOptions.kt), so this is deliberately not exposed as
 * a picker in the character creator.
 */
data class PlayerAppearance(
    val characterClass: CharacterClass,
    val sectionId: SectionId = SectionId.Viridia,
    val headIndex: Int = 0,
    val hairIndex: Int = 0,
    val accessoryEquipped: Boolean = false,
) {
    companion object {
        /** Reproduces the mobile game's original fully-hardcoded player exactly. */
        val DEFAULT = PlayerAppearance(CharacterClass.HUmar)
    }
}
