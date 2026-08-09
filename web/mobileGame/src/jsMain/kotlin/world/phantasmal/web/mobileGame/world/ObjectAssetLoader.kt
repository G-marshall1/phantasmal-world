package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseNjm
import world.phantasmal.psolib.fileFormats.ninja.parseXvm
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.conversion.MeshBuilder
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToMeshBuilder
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToSkinnedMesh
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.SkinnedMesh

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

    /**
     * Loads a prop that has an accompanying "{slug}.njm" clip (see GSL_OBJECT_SPECS in
     * :web:assets-generation's ObjectSpecs.kt -- currently the city teleporter beams). Built as a
     * skinned mesh rather than the plain static mesh [loadObject] produces, because the clip
     * animates the object's bones and [createAnimationClip] needs the source [NjObject] plus a
     * skinned mesh to drive.
     */
    suspend fun loadAnimatedObject(slug: String): AnimatedObjectData {
        val njObject = parseNj(
            assetLoader.loadArrayBuffer("/objects/$slug.nj").cursor(Endianness.Little)
        ).unwrap().first()

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/objects/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        // Pass the model's bone count -- without it parseNjm's v2 path guesses where the per-bone
        // offset table ends and stops short, so only the lowest few rings of a beam animate and
        // the rest stand still (CityBeam has 6 bones but yields 3 entries when left to guess).
        var boneCount = 0
        while (njObject.getBone(boneCount) != null) boneCount++

        val motion = parseNjm(
            assetLoader.loadArrayBuffer("/objects/$slug.njm").cursor(Endianness.Little),
            boneCount = boneCount,
        )

        val mesh = ninjaObjectToSkinnedMesh(njObject, xvm.textures, boundingVolumes = true)
        return AnimatedObjectData(mesh, njObject, motion)
    }
}

/** A prop's skinned mesh plus what's needed to build its animation clip. */
class AnimatedObjectData(
    val mesh: SkinnedMesh,
    val njObject: NjObject,
    val motion: NjMotion,
)
