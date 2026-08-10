package world.phantasmal.web.mobileGame.persistence

import kotlinx.serialization.Serializable
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.mobileGame.player.BarrierItem
import world.phantasmal.web.mobileGame.player.FrameItem
import world.phantasmal.web.mobileGame.player.SpecialFamily
import world.phantasmal.web.mobileGame.player.barrierSpecByName
import world.phantasmal.web.mobileGame.player.frameSpecByName
import world.phantasmal.web.mobileGame.player.WeaponItem
import world.phantasmal.web.mobileGame.player.weaponSpecial
import world.phantasmal.web.mobileGame.player.weaponTierByName
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
    /** Legacy field from the short-lived tint approach; ignored. Kept so old saves parse. */
    val costumeHue: Int = -1,
    /** Which of the class's real body-texture variants this character wears. */
    val bodyIndex: Int = 0,
    /** Proportion scales, 1.0 being the authored model. Applied visually, never to gameplay. */
    val proportionHeight: Double = 1.0,
    val proportionWidth: Double = 1.0,
    /** Lifetime EXP -- the level is always derived from this, never stored separately. */
    val totalExp: Int = 0,
    /** Every hunter steps onto Pioneer 2 with pocket money for their first tools. */
    val meseta: Int = 500,
    /** Tool stacks, keyed by [world.phantasmal.web.mobileGame.player.ToolType] name. */
    val tools: Map<String, Int> = emptyMap(),
    /** Banked weapons; the drawn one sits in [equippedWeapon]. */
    val weapons: List<SavedWeapon> = emptyList(),
    val equippedWeapon: SavedWeapon? = null,
    /** Rare trophies, by [world.phantasmal.web.mobileGame.player.TreasureType] name. */
    val treasures: List<String> = emptyList(),
    /** Stat Materials consumed -- each a permanent +2 to its stat. */
    val materialPower: Int = 0,
    val materialMind: Int = 0,
    val materialHp: Int = 0,
    val materialEvade: Int = 0,
    val materialDef: Int = 0,
    val materialLuck: Int = 0,
    val materialTp: Int = 0,
    /** Learned techniques, by [Technique] name to level. Empty on legacy saves (see loader). */
    val techLevels: Map<String, Int> = emptyMap(),
    /** Unused technique disks, each "NAME:level". */
    val techDisks: List<String> = emptyList(),
    /** The Mag's feed experience per stat (100 = one stat point), synchro and IQ. */
    val magDefExp: Int = 500,
    val magPowExp: Int = 0,
    val magDexExp: Int = 0,
    val magMindExp: Int = 0,
    val magSynchro: Int = 20,
    val magIq: Int = 0,
    /**
     * The Mag's evolution (model slug). Blank on saves from before evolutions persisted --
     * the loader derives those the way the old build did (level 10+ wears the class's first
     * form), which is always right for them since nothing could evolve past that.
     */
    val magForm: String = "",
    /** The feeding window: feeds left, and when the current 3:30 window ends (epoch ms). */
    val magFeedsLeft: Int = 3,
    val magWindowEndMs: Double = 0.0,
    /** The checkroom: everything left with the clerk on Pioneer 2. */
    val bankMeseta: Int = 0,
    val bankTools: Map<String, Int> = emptyMap(),
    val bankWeapons: List<SavedWeapon> = emptyList(),
    val bankTreasures: List<String> = emptyList(),
    /** The armor closet: owned pieces, what's worn, and what the checkroom holds. */
    val ownedFrames: List<SavedFrame> = emptyList(),
    val ownedBarriers: List<SavedBarrier> = emptyList(),
    val ownedUnits: List<String> = emptyList(),
    val equippedFrame: SavedFrame? = null,
    val equippedBarrier: SavedBarrier? = null,
    val equippedUnits: List<String> = emptyList(),
    val bankFrames: List<SavedFrame> = emptyList(),
    val bankBarriers: List<SavedBarrier> = emptyList(),
    val bankUnits: List<String> = emptyList(),
    /** Whether the checkroom's one-time outfitting delivery has been claimed into this save. */
    val bankKitGranted: Boolean = false,
    /** Bosses this character has felled ("dragon", ...) -- the Ragol teleporter gates on these. */
    val defeatedBosses: List<String> = emptyList(),
) {
    fun toPlayerAppearance(): PlayerAppearance? {
        val characterClass = CharacterClass.VALUES_LIST.find { it.slug == characterClassSlug } ?: return null
        return PlayerAppearance(
            characterClass = characterClass,
            sectionId = sectionId,
            headIndex = headIndex,
            hairIndex = hairIndex,
            accessoryEquipped = accessoryEquipped,
            bodyIndex = bodyIndex,
            proportionHeight = proportionHeight,
            proportionWidth = proportionWidth,
        )
    }
}

/** A weapon in a save: its tier by name plus what makes this copy itself. */
@Serializable
data class SavedWeapon(
    val tierName: String,
    val grind: Int = 0,
    val specialFamily: String? = null,
    val specialTier: Int = 0,
)

/** Null if the save names a tier this build no longer ships -- the item is dropped, not crashed on. */
fun SavedWeapon.toWeaponItem(): WeaponItem? {
    val tier = weaponTierByName(tierName) ?: return null
    val special = specialFamily
        ?.let { name -> SpecialFamily.entries.find { it.name == name } }
        ?.let { weaponSpecial(it, specialTier) }
    return WeaponItem(tier, grind = grind, specialAttack = special)
}

fun WeaponItem.toSaved(): SavedWeapon = SavedWeapon(
    tierName = tier.name,
    grind = grind,
    specialFamily = specialAttack?.family?.name,
    specialTier = specialAttack?.tier ?: 0,
)

/** A frame in a save: the tier by name plus this copy's rolled stats and slots. */
@Serializable
data class SavedFrame(val name: String, val dfp: Int, val evp: Int, val slots: Int)

@Serializable
data class SavedBarrier(val name: String, val dfp: Int, val evp: Int)

fun SavedFrame.toItem(): FrameItem? =
    frameSpecByName(name)?.let { FrameItem(it, dfp, evp, slots) }

fun FrameItem.toSaved(): SavedFrame = SavedFrame(spec.name, dfp, evp, slots)

fun SavedBarrier.toItem(): BarrierItem? =
    barrierSpecByName(name)?.let { BarrierItem(it, dfp, evp) }

fun BarrierItem.toSaved(): SavedBarrier = SavedBarrier(spec.name, dfp, evp)
