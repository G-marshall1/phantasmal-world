package world.phantasmal.web.mobileGame.player

import kotlin.math.PI
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.externals.three.Box3
import world.phantasmal.web.externals.three.Group
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.world.WeaponAssetLoader

/**
 * Attaches real converted weapon models (see WEAPON_SPECS in :web:assets-generation's
 * WeaponSpecs.kt for the full list of 227 slugs) to the player's hand bones -- found visually via
 * [world.phantasmal.web.mobileGame.debug.BoneDebugOverlay]. [ROTATION_X]/Y/Z and [POSITION_X]/Y/Z
 * were originally hand-tuned via
 * [world.phantasmal.web.mobileGame.debug.WeaponOrientationDebugOverlay] for a procedural
 * placeholder box (blade running along the group's local +Y); real item models' blades instead run
 * along local +Z with the origin at the grip (measured empirically: the Saber model's bounding box
 * is roughly x/y in [-1.6, 1.6] and z in [-3, 14]), so [ALIGN_BLADE_TO_Y] rotates that -90 degrees
 * around X first to match the convention the rest of the tuned transform expects.
 *
 * That +Z-out-of-the-grip convention is NOT universal: the models were authored on whichever axis
 * suited each item, so a shared transform holds some of them sideways or backwards.
 * [applyModelConvention] normalizes each measured deviation onto the Saber convention first, and
 * [NO_FACING_FLIP] then decides which families still need the half-turn that reverses which way
 * the weapon points out of the fist (see its doc for whose word that is).
 *
 * Rules are keyed on [WeaponType] rather than on model slug, so a rare with its own model (a
 * Varista, say) is oriented like the rest of its family instead of falling through to the
 * untransformed default.
 */
object Weapon {
    private const val RIGHT_HAND_BONE_INDEX = 46

    private val ROTATION_X = 150.0.toRadians()
    private val ROTATION_Y = 270.0.toRadians()
    private val ROTATION_Z = 75.0.toRadians()
    private const val POSITION_X = 0.59
    private const val POSITION_Y = 0.39
    private const val POSITION_Z = 0.39

    private const val ALIGN_BLADE_TO_Y = -PI / 2

    /**
     * The families that do NOT take the blanket half-turn, for one of two reasons.
     *
     * Confirmed correct on device as they were: "only the rifle and the shot were facing the
     * right way. The sword, double saber and saber looks fine." The launcher rides with the
     * guns rather than being flipped blind -- Rifle, Shot and Panzer Faust are all authored
     * barrel-down-Y under one convention fix, and two of the three are confirmed right under
     * it, so flipping the third would break what its own family proves.
     *
     * The pistols are here for the other reason: they are barrel-down-Y guns like the rifles
     * and take that family's own correction instead (see the note in [applyModelConvention]).
     *
     * The Wand is here on the same report that put the blanket turn in: "the but of the staff is
     * the front and the top of the staff is the but". It is authored centred on its own middle
     * rather than gripped at the origin (z [-6.18, 6.18], dead symmetric -- the Rod is the same
     * at z [-11.33, 11.97], while the Cane's z [-3.55, 8.32] follows the Saber's grip-at-origin
     * convention). For a centred model the half-turn is the only thing deciding which end leads,
     * so dropping it is the whole fix.
     */
    private val NO_FACING_FLIP: Set<WeaponType> = setOf(
        WeaponType.SABER, WeaponType.SWORD, WeaponType.DOUBLE_SABER,
        WeaponType.RIFLE, WeaponType.SHOT, WeaponType.LAUNCHER,
        WeaponType.HANDGUN, WeaponType.MECHGUN,
        WeaponType.WAND,
    )

    /**
     * The families PSO puts in *both* hands, from the items' own descriptions: a Dagger is a
     * matched pair, Sange & Yasha is "a pair of Katanas that work perfectly in concert with one
     * another" (the Twin Sword family), and mechguns are the twin autopistols they look like.
     *
     * Deliberately excluded: the Claw, whose description says outright that it attacks "with
     * right hand", and the Double Saber and Twin Brand, which are single double-ended staves
     * held in one fist.
     */
    private val DUAL_WIELDED: Set<WeaponType> = setOf(
        WeaponType.DAGGER, WeaponType.MECHGUN, WeaponType.TWIN_SWORD,
    )

    /**
     * Every model attached for this weapon: one for a single-handed weapon, two for a
     * [DUAL_WIELDED] pair. The caller detaches whatever it's given when the weapon changes.
     */
    suspend fun attach(
        assetLoader: AssetLoader,
        mesh: SkinnedMesh,
        slug: String = "Saber",
        photonColor: Int? = null,
    ): List<Object3D> {
        val type = weaponType(slug)
        val loader = WeaponAssetLoader(assetLoader)

        val attachments = mutableListOf<Object3D>()
        attachments.add(
            attachToBone(loader.loadWeapon(slug).also { tintPhoton(it, photonColor) },
                type, mesh, RIGHT_HAND_BONE_INDEX)
        )

        if (type in DUAL_WIELDED) {
            // A second, independently built model -- the same mesh can't hang off two bones.
            leftHandBoneIndex(mesh)?.let { boneIndex ->
                attachments.add(
                    attachToBone(loader.loadWeapon(slug).also { tintPhoton(it, photonColor) },
                        type, mesh, boneIndex)
                )
            }
        }

        return attachments
    }

    /**
     * Lights a common weapon with its tier's photon colour -- how PSO states a common's rank on
     * the model rather than in a menu, so a Gladius reads as gold across the room.
     *
     * Emissive rather than diffuse: the blade is meant to be a light source, and tinting the
     * base colour instead would just stain the hilt and grip along with it. Rares pass null and
     * keep their own authored materials, which is the point of having their own models.
     *
     * Materials on these meshes are an array, one per texture -- reading a single `.material`
     * silently misses most of the model, the same trap the rare box tinting hit.
     */
    private fun tintPhoton(mesh: Mesh, photonColor: Int?) {
        if (photonColor == null) return
        val material = mesh.asDynamic().material ?: return

        // Walked by index rather than turned into a Kotlin list. `material` is dynamic, so any
        // Kotlin collection call on it is emitted as a *JavaScript* method call on the value --
        // and a JS array has no `toList`, so it threw. That throw landed inside the equip
        // coroutine, which is why a failed tint stopped the new model being attached at all
        // and the old weapon stayed in hand.
        if (js("Array.isArray")(material) as Boolean) {
            val count = material.length as Int
            for (i in 0 until count) applyPhoton(material[i], photonColor)
        } else {
            applyPhoton(material, photonColor)
        }
    }

    /**
     * Lights one material. Not every converted model uses a material that *has* an emissive
     * channel -- the plain unlit ones have no such property -- so this tints the base colour
     * for those instead of silently doing nothing.
     */
    private fun applyPhoton(material: dynamic, photonColor: Int) {
        if (material == null) return
        if (material.emissive != null) {
            material.emissive.setHex(photonColor)
            material.emissiveIntensity = PHOTON_EMISSIVE
        } else if (material.color != null) {
            material.color.setHex(photonColor)
        }
    }

    private const val PHOTON_EMISSIVE = 0.85

    private fun attachToBone(
        weaponMesh: Mesh,
        type: WeaponType,
        mesh: SkinnedMesh,
        boneIndex: Int,
    ): Object3D {
        applyModelConvention(weaponMesh, type)

        // Nested groups keep the rotations composing in a fixed, readable order: per-model
        // normalization (innermost, on the mesh), then the facing half-turn, then blade-axis
        // alignment, then the tuned hand transform (outermost). The half-turn sits in the
        // normalized frame, where the blade always runs +Z, so it reverses which way the weapon
        // points whatever axis the model was authored on.
        val facing = Group().apply {
            add(weaponMesh)
            if (needsFacingFlip(weaponMesh, type)) rotation.y = PI
        }

        val aligned = Group().apply {
            add(facing)
            rotation.x = ALIGN_BLADE_TO_Y
        }

        val weapon = Group().apply {
            add(aligned)
            rotation.set(ROTATION_X, ROTATION_Y, ROTATION_Z)
            position.set(POSITION_X, POSITION_Y, POSITION_Z)
        }

        mesh.skeleton.bones[boneIndex].add(weapon)
        return weapon
    }

    /**
     * Whether this particular model still needs the half-turn, asked of the geometry rather than
     * of a list of weapon families.
     *
     * The blanket turn was applied to everything outside [NO_FACING_FLIP] on the assumption that
     * a model not known-good was authored backwards. That assumption is wrong often enough to
     * keep producing reports -- the wand, then the partisan, the toy hammer and the wok. Many
     * models already follow the saber's convention of running out +Z from the grip, and turning
     * those reverses a weapon that was correct to begin with.
     *
     * So the question is asked of the model: with its own normalization applied, does its bulk
     * sit forward of the grip? If it does it is already pointing the right way and is left
     * alone; only a model whose mass sits behind the origin gets turned. [NO_FACING_FLIP] stays
     * as an override for the families whose answer is settled -- the guns in particular are
     * authored down -Y rather than along Z, so their Z extent says nothing useful.
     */
    private fun needsFacingFlip(weaponMesh: Mesh, type: WeaponType): Boolean {
        if (type in NO_FACING_FLIP) return false

        val geometry = weaponMesh.asDynamic().geometry ?: return true
        if (geometry.boundingBox == null) geometry.computeBoundingBox()

        // Measured through the model's own rotation, so a model normalized by
        // applyModelConvention is judged as it will actually hang.
        val box = Box3()
        box.min.set(
            geometry.boundingBox.min.x as Double,
            geometry.boundingBox.min.y as Double,
            geometry.boundingBox.min.z as Double,
        )
        box.max.set(
            geometry.boundingBox.max.x as Double,
            geometry.boundingBox.max.y as Double,
            geometry.boundingBox.max.z as Double,
        )
        weaponMesh.updateMatrix()
        box.applyMatrix4(weaponMesh.matrix)

        return (box.min.z + box.max.z) <= 0.0
    }

    /**
     * The left hand, found as the bone mirroring the right hand across the character's own
     * centre line rather than hardcoded: the skeleton is symmetric, so the match is unambiguous,
     * and searching for it survives a change of model where a second magic index would quietly
     * point at an elbow.
     */
    private fun leftHandBoneIndex(mesh: SkinnedMesh): Int? {
        val bones = mesh.skeleton.bones
        if (RIGHT_HAND_BONE_INDEX !in bones.indices) return null

        mesh.updateMatrixWorld(true)

        val right = Vector3()
        bones[RIGHT_HAND_BONE_INDEX].getWorldPosition(right)
        mesh.worldToLocal(right)

        val probe = Vector3()
        var best = -1
        var bestDistance = Double.MAX_VALUE

        for (index in bones.indices) {
            if (index == RIGHT_HAND_BONE_INDEX) continue
            bones[index].getWorldPosition(probe)
            mesh.worldToLocal(probe)
            // The mirror of (x, y, z) is (-x, y, z) in the character's own space.
            val dx = probe.x + right.x
            val dy = probe.y - right.y
            val dz = probe.z - right.z
            val distance = dx * dx + dy * dy + dz * dz
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }

        return best.takeIf { it >= 0 }
    }

    /**
     * Rotates a model whose authored axes deviate from the conventions the shared hand transform
     * expects. Measured from the geometry's own bounds (the `?viewWeapon=<slug>` route's
     * VIEWER_BOUNDS log) and verified against front/profile screenshots of the model in hand:
     *
     * - Most models are authored blade-out-+Z with the origin at the grip (Saber z [-3.0, 14.0])
     *   and need no normalization at all.
     * - Every gun is authored barrel-down-Y with its grip running +Z, and they all take the same
     *   half-turn about Z to level them at the target: Rifle (y [-13.0, 1.6]), Shot
     *   (y [-11.5, 7.0]), Panzer Faust (y [-9.5, 5.2]), and -- measured after they came out
     *   wrong on device -- Handgun (y [-2.7, 1.0], z [-0.9, 2.3]) and Mechgun (y [-5.1, 1.1]).
     *   The pistols carrying no correction was the original bug; they read as "aiming dead at
     *   the target with no adjustment" in an early check, which the device disproved twice. The
     *   "obvious" pitch of -Y onto +Z instead stands them muzzle-up at the fire frame, because
     *   each attack clip orients the hand's axes differently than the geometry alone suggests.
     * - The Claw's blades run -Y too (y [-7.4, 2.8]) but rake with the punch, so the pitch IS
     *   its correct normalization: blades sweep down-forward through the target.
     * - The Dagger is authored blade-backwards (-Z, z in [-5.1, 2.0]), so it starts a half-turn
     *   about Y from the shared convention.
     * The pistols took three rounds to place, and the path is worth recording so nobody
     * relitigates it: the blanket turn put them under the hand backwards; a half-turn about X
     * and another about Y left them aiming right but upside down; undoing that left them right
     * side up but aiming backwards. Those two reports are consistent only if the barrel runs
     * -Y, which the measured bounds then confirmed -- so the answer was the gun family's own
     * Z half-turn all along, which keeps the authored roll and reverses the aim.
     */
    private fun applyModelConvention(mesh: Mesh, type: WeaponType) {
        when (type) {
            WeaponType.RIFLE, WeaponType.SHOT, WeaponType.LAUNCHER,
            WeaponType.HANDGUN, WeaponType.MECHGUN,
            -> mesh.rotation.z = PI
            WeaponType.CLAW -> mesh.rotation.x = -PI / 2
            WeaponType.DAGGER -> mesh.rotation.y = PI
            else -> Unit
        }
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
