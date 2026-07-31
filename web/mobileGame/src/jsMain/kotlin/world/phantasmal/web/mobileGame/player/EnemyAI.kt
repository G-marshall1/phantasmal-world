package world.phantasmal.web.mobileGame.player

import kotlin.math.atan2
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.world.WallCollider
import world.phantasmal.web.mobileGame.world.findGroundHeight

/**
 * Gives one enemy a minimal chase-and-melee brain: outside [aggroRadius] it just idles in place
 * (looping [walkMotion] -- there's no universal "wait" clip name across every enemy family, see
 * EnemySpecs.kt, so reusing the walk clip stationary is the simplest thing that works for all of
 * them); within [aggroRadius] but outside [attackRange] it walks straight at the player, using the
 * same [WallCollider]/[findGroundHeight] the player's own [CharacterController] is built on so it
 * doesn't clip through walls or float off terrain; within [attackRange] it stops, faces the
 * player, and attacks on a [attackCooldown] timer. No pathfinding -- straight-line chase is enough
 * for these open field areas, and wall collision alone keeps it from clipping through geometry
 * even if the straight line briefly grazes a wall.
 */
class EnemyAI(
    private val mesh: SkinnedMesh,
    private val njObject: NjObject,
    private val mixer: AnimationMixer,
    private val walkMotion: NjMotion,
    private val attackMotion: NjMotion,
    private val wallCollider: WallCollider,
    private val walkable: Object3D,
    bSphereRadius: Double,
) {
    private val radius = bSphereRadius * RADIUS_FACTOR
    private val verticalTolerance = bSphereRadius * VERTICAL_TOLERANCE_FACTOR
    private val speed = bSphereRadius * SPEED_FACTOR
    private val aggroRadius = bSphereRadius * AGGRO_RADIUS_FACTOR
    private val attackRange = bSphereRadius * ATTACK_RANGE_FACTOR

    private var attackCooldownRemaining = 0.0
    private var currentMotion: NjMotion? = null
    private val toPlayer = Vector3()

    // See PlayerAnimator's own clipCache for why: reusing one AnimationClip (and, by extension,
    // one cached AnimationAction) per motion instead of creating a fresh one on every switch
    // avoids a real Three.js AnimationMixer crash under fast repeated switching.
    private val clipCache = mutableMapOf<NjMotion, AnimationClip>()

    init {
        playClip(walkMotion)
    }

    /** Returns true on exactly the frame this enemy's attack lands, for the caller to apply damage. */
    fun update(deltaTime: Double, playerPosition: Vector3): Boolean {
        attackCooldownRemaining -= deltaTime

        toPlayer.subVectors(playerPosition, mesh.position)
        toPlayer.y = .0
        val distance = toPlayer.length()

        if (distance > MIN_DISTANCE) {
            mesh.rotation.y = atan2(toPlayer.x, toPlayer.z)
        }

        var didAttack = false

        when {
            distance <= attackRange -> {
                if (attackCooldownRemaining <= 0) {
                    attackCooldownRemaining = ATTACK_COOLDOWN
                    playClip(attackMotion)
                    didAttack = true
                }
                // Else: leave whichever clip is already playing alone (don't stomp an in-progress
                // attack swing every frame just because we're still in range and on cooldown).
            }

            distance <= aggroRadius -> {
                playClip(walkMotion)

                if (distance > MIN_DISTANCE) {
                    mesh.position.x += toPlayer.x / distance * speed * deltaTime
                    mesh.position.z += toPlayer.z / distance * speed * deltaTime
                }

                wallCollider.resolve(mesh.position, radius, verticalTolerance)
                findGroundHeight(walkable, mesh.position.x, mesh.position.z)?.let {
                    mesh.position.y = it
                }
            }

            else -> playClip(walkMotion)
        }

        return didAttack
    }

    private fun playClip(motion: NjMotion) {
        if (motion === currentMotion) return

        val clip = clipCache.getOrPut(motion) { createAnimationClip(njObject, motion) }
        mixer.stopAllAction()
        mixer.clipAction(clip).play()
        currentMotion = motion
    }

    companion object {
        private const val RADIUS_FACTOR = 0.3
        private const val VERTICAL_TOLERANCE_FACTOR = 3.0
        private const val SPEED_FACTOR = 0.9
        private const val AGGRO_RADIUS_FACTOR = 8.0
        private const val ATTACK_RANGE_FACTOR = 2.5
        private const val ATTACK_COOLDOWN = 1.5
        private const val MIN_DISTANCE = 0.001
    }
}
