package world.phantasmal.web.mobileGame.player

import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToSkinnedMesh
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader
import world.phantasmal.web.viewer.models.CharacterClass

/** The skinned mesh plus the source [NjObject] the animation clips are built from. */
class PlayerMeshData(val mesh: SkinnedMesh, val njObject: NjObject)

/**
 * Thin wrapper around the Viewer's [CharacterClassAssetLoader] (moved into :web:rendering) for
 * spawning a playable character. Hardcoded to HUmar for the first playable version.
 */
class PlayerAssetLoader(private val characterClassAssetLoader: CharacterClassAssetLoader) {
    suspend fun loadPlayerMesh(): PlayerMeshData {
        val njObject = characterClassAssetLoader.loadNinjaObject(CharacterClass.HUmar)
        val textures = characterClassAssetLoader.loadXvrTextures(
            CharacterClass.HUmar,
            SectionId.Viridia,
            body = 0,
        )

        val mesh = ninjaObjectToSkinnedMesh(njObject, textures, boundingVolumes = true)
        return PlayerMeshData(mesh, njObject)
    }
}
