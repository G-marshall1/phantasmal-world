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

/**
 * One extra static piece rendered alongside an enemy's main body -- mirrors EnemyFragment in
 * :web:assets-generation's EnemySpecs.kt (see its doc comment for the two sourcing patterns).
 * [pvmName] null means the fragment shares the main body's own texture pack; set means it was
 * generated with its own "${njName-without-extension}"-adjacent .xvm, named after [pvmName].
 */
class EnemyFragmentRef(val njName: String, val pvmName: String? = null)

/**
 * The skinned mesh plus the source [NjObject] its animation clips are built from. [fragments] are
 * a multi-part enemy's decorative/component pieces (see [EnemyFragmentRef]) -- empty for every
 * regular enemy. They're plain meshes, not bone-attached to [mesh]'s skeleton; the caller is
 * expected to add them as children of [mesh] so they at least track its root position/rotation.
 */
class EnemyMeshData(val mesh: SkinnedMesh, val njObject: NjObject, val fragments: List<SkinnedMesh> = emptyList())

/**
 * Loads any of the converted enemy models (see ENEMY_SPECS in :web:assets-generation's
 * EnemySpecs.kt for the full list of 77 slugs) as a combat test dummy. NjObject-based NPC models
 * use the same skinned-mesh pipeline as player characters (see the Viewer's MeshRenderer, which
 * branches on NjObject vs XjObject the same way).
 */
class EnemyAssetLoader(private val assetLoader: AssetLoader) {
    suspend fun loadEnemy(slug: String, fragments: List<EnemyFragmentRef> = emptyList()): EnemyMeshData {
        val njObject = parseNj(
            assetLoader.loadArrayBuffer("/npcs/$slug.nj").cursor(Endianness.Little)
        ).unwrap().first()

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/npcs/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val mesh = ninjaObjectToSkinnedMesh(njObject, xvm.textures, boundingVolumes = true)

        val fragmentMeshes = fragments.map { fragment ->
            val fragmentObject = parseNj(
                assetLoader.loadArrayBuffer("/npcs/$slug/${fragment.njName}").cursor(Endianness.Little)
            ).unwrap().first()

            val fragmentTextures = if (fragment.pvmName == null) {
                xvm.textures
            } else {
                parseXvm(
                    assetLoader.loadArrayBuffer("/npcs/$slug/${fragment.pvmName}.xvm")
                        .cursor(Endianness.Little)
                ).unwrap().textures
            }

            ninjaObjectToSkinnedMesh(fragmentObject, fragmentTextures, boundingVolumes = true)
        }

        return EnemyMeshData(mesh, njObject, fragmentMeshes)
    }

    /** [clipFileName] is psov2's own clip name, e.g. "walk_bm1_s_wala_body.njm". */
    suspend fun loadAnimation(slug: String, clipFileName: String): NjMotion =
        parseNjm(assetLoader.loadArrayBuffer("/npcs/$slug/$clipFileName").cursor(Endianness.Little))
}
