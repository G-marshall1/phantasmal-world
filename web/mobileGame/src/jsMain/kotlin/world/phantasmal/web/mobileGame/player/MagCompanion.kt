package world.phantasmal.web.mobileGame.player

import kotlin.math.sin
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.externals.three.Group
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.mobileGame.world.WeaponAssetLoader

/**
 * The Mag's model, hovering over the character's shoulder.
 *
 * Attached to the character's root rather than to a bone, unlike a weapon: a Mag isn't held, it
 * floats alongside under its own power, so it should follow the character's position and facing
 * without inheriting an arm's swing. [update] gives it a slow bob so it reads as hovering rather
 * than welded in place.
 *
 * Mag models come from the same item archive as weapons (they're items too), so the existing
 * weapon loader handles them -- all 58 of them, from the starter Mag through every evolution.
 */
class MagCompanion(private val root: Object3D, private val baseScale: Double) {
    private var elapsed = 0.0

    fun update(deltaTime: Double) {
        elapsed += deltaTime
        // Bobs along the model's own up axis, which is Z here -- see the offsets below.
        root.position.z = OFFSET_Z * baseScale + sin(elapsed * BOB_SPEED) * BOB_AMPLITUDE
        root.rotation.z = elapsed * SPIN_SPEED
    }

    companion object {
        /**
         * Offsets are in the character's own local space, scaled by its bounding sphere so the
         * Mag sits correctly for a small FOnewearl and a large HUcast alike.
         */
        suspend fun attach(
            assetLoader: AssetLoader,
            mesh: SkinnedMesh,
            bSphereRadius: Double,
            slug: String = "Mag",
        ): MagCompanion {
            val magMesh = WeaponAssetLoader(assetLoader).loadWeapon(slug)

            val holder = Group().apply {
                add(magMesh)
                position.set(
                    bSphereRadius * OFFSET_X,
                    bSphereRadius * OFFSET_Y,
                    bSphereRadius * OFFSET_Z,
                )
                scale.set(SCALE, SCALE, SCALE)
            }

            mesh.add(holder)
            return MagCompanion(holder, bSphereRadius)
        }

        /**
         * Over the left shoulder and slightly behind, out of the way of the weapon arm.
         *
         * In the character mesh's own local space. Ninja models don't stand up the Y axis there
         * -- Weapon.kt hits the same thing with blades running along local +Z -- so "up" for an
         * attached object is +Z, not +Y. Found by placing the Mag and looking; the two spaces
         * don't relate by any constant worth deriving.
         */
        private const val OFFSET_X = -1.2
        private const val OFFSET_Y = -0.8
        private const val OFFSET_Z = 2.9

        private const val SCALE = 1.0

        private const val BOB_SPEED = 2.0
        private const val BOB_AMPLITUDE = 1.2
        private const val SPIN_SPEED = 0.4
    }
}
