package world.phantasmal.web.mobileGame.persistence

import kotlinx.serialization.Serializable
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * A locally-saved character. [characterClassSlug] is stored as a plain string rather than
 * [CharacterClass] directly -- that enum lives in :web:rendering, which doesn't apply the
 * kotlinx.serialization plugin, and adding it there would be a much larger blast-radius change
 * than this feature needs.
 */
@Serializable
data class CharacterSave(
    val id: String,
    val name: String,
    val characterClassSlug: String,
    val sectionId: SectionId,
    val headIndex: Int,
    val hairIndex: Int,
    val accessoryEquipped: Boolean,
    val createdAtEpochMs: Double,
) {
    fun toPlayerAppearance(): PlayerAppearance? {
        val characterClass = CharacterClass.VALUES_LIST.find { it.slug == characterClassSlug } ?: return null
        return PlayerAppearance(
            characterClass = characterClass,
            sectionId = sectionId,
            headIndex = headIndex,
            hairIndex = hairIndex,
            accessoryEquipped = accessoryEquipped,
        )
    }
}
