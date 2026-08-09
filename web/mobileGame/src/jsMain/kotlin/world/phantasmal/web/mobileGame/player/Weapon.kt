package world.phantasmal.web.mobileGame.player

import kotlin.math.PI
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.externals.three.Group
import world.phantasmal.web.externals.three.Mesh
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
 * model's bounding box is roughly x/y in [-1.6, 1.6] and z in [-3, 14]), so [ALIGN_BLADE_TO_Y]
 * rotates that -90 degrees around X first to match the convention the rest of the tuned transform
 * expects.
 *
 * That +Z-out-of-the-grip convention is NOT universal: the models were authored on whichever axis
 * suited each item, so a shared transform holds some of them sideways or backwards.
 * [applyModelConvention] normalizes each measured deviation onto the Saber convention first --
 * measurements from the `?viewWeapon=<slug>` route's VIEWER_BOUNDS log.
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
        val weaponMesh = WeaponAssetLoader(assetLoader).loadWeapon(slug)
        applyModelConvention(weaponMesh, slug)

        // Nested groups keep the three rotations composing in a fixed, readable order:
        // per-model normalization (innermost), then blade-axis alignment, then the tuned
        // hand transform (outermost).
        val aligned = Group().apply {
            add(weaponMesh)
            rotation.x = ALIGN_BLADE_TO_Y
        }

        val weapon = Group().apply {
            add(aligned)
            rotation.set(ROTATION_X, ROTATION_Y, ROTATION_Z)
            position.set(POSITION_X, POSITION_Y, POSITION_Z)
        }

        mesh.skeleton.bones[HAND_BONE_INDEX].add(weapon)
        return weapon
    }

    /**
     * Rotates a model whose authored axes deviate from the conventions the shared hand
     * transform expects. Every entry is verified against front-view screenshots of the model
     * in hand (?map=forest01&weapon=<slug>&face=180), with the geometry's measured bounds
     * (the ?viewWeapon route's VIEWER_BOUNDS log) saying which way the axes run.
     *
     * The deciding evidence is each weapon's FIRE/CONTACT pose in profile
     * (?map=...&face=90&pose=<attackClip>,<contactFrame>), where aim direction is
     * unambiguous. Screenshot-verified:
     *
     * - Handgun and Mechgun aim dead at the target with no adjustment, as does every
     *   +Z-bladed melee model (Saber z [-3.0, 14.0], Sword, Partisan, Slicer, Cane,
     *   Yamigarasu) and the mid-shaft-gripped Rod/Wand/Double Saber/Twinkle Star.
     * - Rifle (y [-13.0, 1.6]), Shot (y [-11.5, 7.0]) and Panzer Faust (y [-9.5, 5.2])
     *   were authored barrel-down-Y and fired backwards or into the ground under the
     *   shared transform. The half-turn about Z is what actually levels them at the
     *   target -- the "obvious" pitch of -Y onto +Z instead stood them muzzle-up at the
     *   fire frame, because each attack clip orients the hand's axes differently than
     *   the geometry alone suggests.
     * - The Claw's blades run -Y too (y [-7.4, 2.8]) but rake with the punch, so the
     *   pitch IS its correct fix: blades sweep down-forward through the target.
     * - The Dagger is authored blade-backwards (-Z, z in [-5.1, 2.0]); the half-turn
     *   about Y points it out the front of the fist.
     */
    private fun applyModelConvention(mesh: Mesh, slug: String) {
        when (slug) {
            "Rifle", "Shot", "PanzerFaust" -> mesh.rotation.z = PI
            "Claw" -> mesh.rotation.x = -PI / 2
            "Dagger" -> mesh.rotation.y = PI
        }
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
