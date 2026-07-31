package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseXvm
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.conversion.MeshBuilder
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToMeshBuilder
import world.phantasmal.web.externals.three.Mesh

/**
 * Loads any of the converted weapon/shield/mag/unit models (see WEAPON_SPECS in
 * :web:assets-generation's WeaponSpecs.kt for the full list of 227 slugs). Item models are plain
 * (non-skinned) Ninja bone/chunk object graphs -- the same NJCM format as everything else, just
 * with no skinning data -- so this reuses the exact same static-mesh path map decorative props go
 * through (ninjaObjectToMeshBuilder + a plain, non-skinned buildMesh), rather than
 * ninjaObjectToSkinnedMesh's bone/skeleton path enemy and player models need.
 */
class WeaponAssetLoader(private val assetLoader: AssetLoader) {
    suspend fun loadWeapon(slug: String): Mesh {
        val njObject = parseNj(
            assetLoader.loadArrayBuffer("/weapons/$slug.nj").cursor(Endianness.Little)
        ).unwrap().first()

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/weapons/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val builder = MeshBuilder(xvm.textures)
        ninjaObjectToMeshBuilder(njObject, builder)
        return builder.buildMesh(boundingVolumes = true)
    }
}
