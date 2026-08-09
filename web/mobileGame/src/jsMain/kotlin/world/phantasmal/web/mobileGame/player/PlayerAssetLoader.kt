package world.phantasmal.web.mobileGame.player

import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToSkinnedMesh
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader

/** The skinned mesh plus the source [NjObject] the animation clips are built from. */
class PlayerMeshData(val mesh: SkinnedMesh, val njObject: NjObject)

/**
 * Thin wrapper around the Viewer's [CharacterClassAssetLoader] (moved into :web:rendering) for
 * spawning a playable character with a chosen [PlayerAppearance].
 */
class PlayerAssetLoader(private val characterClassAssetLoader: CharacterClassAssetLoader) {
    suspend fun loadPlayerMesh(appearance: PlayerAppearance = PlayerAppearance.DEFAULT): PlayerMeshData {
        val accessoryNo = if (appearance.accessoryEquipped) appearance.hairIndex else null
        val njObject = characterClassAssetLoader.loadNinjaObject(
            appearance.characterClass,
            appearance.headIndex,
            appearance.hairIndex,
            accessoryNo,
        )
        val textures = characterClassAssetLoader.loadXvrTextures(
            appearance.characterClass,
            appearance.sectionId,
            body = appearance.bodyIndex.coerceIn(0, appearance.characterClass.bodyStyleCount - 1),
        )

        val mesh = ninjaObjectToSkinnedMesh(njObject, textures, boundingVolumes = true)
        return PlayerMeshData(mesh, njObject)
    }

}
