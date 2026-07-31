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
 * Loads any of the converted decorative map props (see OBJECT_SPECS in :web:assets-generation's
 * ObjectSpecs.kt for the full list of 7 slugs) -- item/weapon/armor boxes, meseta, and a couple of
 * effect props. Same plain static-mesh path [world.phantasmal.web.mobileGame.world.WeaponAssetLoader]
 * uses, since these are the same non-skinned NJCM item format.
 */
class ObjectAssetLoader(private val assetLoader: AssetLoader) {
    suspend fun loadObject(slug: String): Mesh {
        val njObject = parseNj(
            assetLoader.loadArrayBuffer("/objects/$slug.nj").cursor(Endianness.Little)
        ).unwrap().first()

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/objects/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val builder = MeshBuilder(xvm.textures)
        ninjaObjectToMeshBuilder(njObject, builder)
        return builder.buildMesh(boundingVolumes = true)
    }
}
