package world.phantasmal.web.mobileGame.player

import kotlin.math.PI
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.externals.three.Group
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.mobileGame.world.WeaponAssetLoader

/**
 * Attaches a real converted weapon model (see WEAPON_SPECS in :web:assets-generation's
 * WeaponSpecs.kt for the full list of 227 slugs) to the player's right-hand bone -- found visually
 * via [world.phantasmal.web.mobileGame.debug.BoneDebugOverlay]. [ROTATION_X]/Y/Z and [POSITION_X]/
 * Y/Z were originally hand-tuned via [world.phantasmal.web.mobileGame.debug.WeaponOrientationDebugOverlay]
 * for a procedural placeholder box (blade running along the group's local +Y); real item models'
 * blades instead run along local +Z with the origin at the grip (measured empirically: the Saber
 * model's bounding box is roughly x/y in [-1.6, 1.6] and z in [-3, 14]), so [alignBladeToY] rotates
 * that -90 degrees around X first to match the convention the rest of the tuned transform expects.
 */
object Weapon {
    private const val HAND_BONE_INDEX = 46

    private val ROTATION_X = 150.0.toRadians()
    private val ROTATION_Y = 270.0.toRadians()
    private val ROTATION_Z = 75.0.toRadians()
    private const val POSITION_X = 0.59
    private const val POSITION_Y = 0.39
    private const val POSITION_Z = 0.39

    private const val ALIGN_BLADE_TO_Y = -PI / 2

    suspend fun attach(assetLoader: AssetLoader, mesh: SkinnedMesh, slug: String = "Saber"): Object3D {
        val weaponMesh = WeaponAssetLoader(assetLoader).loadWeapon(slug).apply {
            rotation.x = ALIGN_BLADE_TO_Y
        }

        val weapon = Group().apply {
            add(weaponMesh)
            rotation.set(ROTATION_X, ROTATION_Y, ROTATION_Z)
            position.set(POSITION_X, POSITION_Y, POSITION_Z)
        }

        mesh.skeleton.bones[HAND_BONE_INDEX].add(weapon)
        return weapon
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
