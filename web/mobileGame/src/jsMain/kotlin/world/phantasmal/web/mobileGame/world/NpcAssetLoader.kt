package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseXvm
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToSkinnedMesh
import world.phantasmal.web.externals.three.SkinnedMesh

/** The skinned mesh plus the source [NjObject] its animation clips are built from. */
class NpcMeshData(val mesh: SkinnedMesh, val njObject: NjObject)

/**
 * Loads any of the converted town NPCs (see NPC_SPECS in :web:assets-generation's NpcSpecs.kt for
 * the full list of 6 slugs) -- purely decorative hub/lobby characters, not enemies. Shares the
 * player's own skeleton closely enough that psov2 drives these with the same plymotiondata.rlc
 * clip set (see AnimationAssetLoader), so a spawned NPC can play e.g. the player's idle clip.
 */
class NpcAssetLoader(private val assetLoader: AssetLoader) {
    suspend fun loadNpc(slug: String): NpcMeshData {
        val njObject = parseNj(
            assetLoader.loadArrayBuffer("/npcCharacters/$slug.nj").cursor(Endianness.Little)
        ).unwrap().first()

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/npcCharacters/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val mesh = ninjaObjectToSkinnedMesh(njObject, xvm.textures, boundingVolumes = true)
        return NpcMeshData(mesh, njObject)
    }

    /**
     * Loads one of the 14 city NPCs sourced as standalone ".rel" entries (NPC_REL_SPECS in
     * :web:assets-generation's NpcSpecs.kt) -- same idea as [loadNpc], but the root object graph
     * has to be located via [parseNpcRelObject]'s footer-pointer walk instead of psolib's regular
     * top-level chunk format.
     */
    suspend fun loadCityNpc(slug: String): NpcMeshData {
        val njObject = parseNpcRelObject(
            assetLoader.loadArrayBuffer("/npcCharacters/$slug.rel").cursor(Endianness.Little)
        )

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/npcCharacters/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val mesh = ninjaObjectToSkinnedMesh(njObject, xvm.textures, boundingVolumes = true)
        return NpcMeshData(mesh, njObject)
    }
}
