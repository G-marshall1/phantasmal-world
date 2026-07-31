package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseNjm
import world.phantasmal.psolib.fileFormats.ninja.parseXvm
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToSkinnedMesh
import world.phantasmal.web.externals.three.SkinnedMesh

/** The skinned mesh plus the source [NjObject] its animation clips are built from. */
class EnemyMeshData(val mesh: SkinnedMesh, val njObject: NjObject)

/**
 * Loads any of the converted enemy models (see ENEMY_SPECS in :web:assets-generation's
 * EnemySpecs.kt for the full list of 69 slugs) as a combat test dummy. NjObject-based NPC models
 * use the same skinned-mesh pipeline as player characters (see the Viewer's MeshRenderer, which
 * branches on NjObject vs XjObject the same way).
 */
class EnemyAssetLoader(private val assetLoader: AssetLoader) {
    suspend fun loadEnemy(slug: String): EnemyMeshData {
        val njObject = parseNj(
            assetLoader.loadArrayBuffer("/npcs/$slug.nj").cursor(Endianness.Little)
        ).unwrap().first()

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/npcs/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val mesh = ninjaObjectToSkinnedMesh(njObject, xvm.textures, boundingVolumes = true)
        return EnemyMeshData(mesh, njObject)
    }

    /** [clipFileName] is psov2's own clip name, e.g. "walk_bm1_s_wala_body.njm". */
    suspend fun loadAnimation(slug: String, clipFileName: String): NjMotion =
        parseNjm(assetLoader.loadArrayBuffer("/npcs/$slug/$clipFileName").cursor(Endianness.Little))
}
