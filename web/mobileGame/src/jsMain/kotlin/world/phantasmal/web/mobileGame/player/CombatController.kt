package world.phantasmal.web.mobileGame.player

import kotlin.math.cos
import kotlin.math.sin
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.externals.three.Vector3

class Enemy(
    val mesh: SkinnedMesh,
    var hp: Int,
    val animationMixer: AnimationMixer? = null,
    val ai: EnemyAI? = null,
)

/**
 * Handles attack timing (locks out repeated attacks until the current swing finishes) and simple
 * distance + facing-based hit detection against enemies in front of the player.
 */
class CombatController(
    private val playerPosition: Vector3,
    bSphereRadius: Double,
) {
    var isAttacking: Boolean = false
        private set

    private var attackTimeRemaining = 0.0
    private val range = bSphereRadius * ATTACK_RANGE_FACTOR
    private val toEnemy = Vector3()

    /**
     * Instant hit-test on swing start; does nothing (and returns false) if already mid-attack, so
     * callers must only advance their own combo/animation state when this returns true --
     * otherwise a spammed attack button advances the combo clip while the *previous* swing's
     * timer is still running, which fights the animator's crossfade every tap and looks like
     * stuttering/frame skips. [duration] is the chosen attack animation's length, since callers
     * cycle through several attack clips (combo) with different lengths.
     */
    fun tryAttack(playerYaw: Double, enemies: MutableList<Enemy>, duration: Double): Boolean {
        if (isAttacking) return false

        isAttacking = true
        attackTimeRemaining = duration

        val forwardX = sin(playerYaw)
        val forwardZ = cos(playerYaw)

        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val enemy = iterator.next()
            toEnemy.subVectors(enemy.mesh.position, playerPosition)
            val distance = toEnemy.length()

            if (distance <= range && distance > 0) {
                toEnemy.normalize()
                val facingDot = toEnemy.x * forwardX + toEnemy.z * forwardZ

                if (facingDot >= FACING_THRESHOLD) {
                    enemy.hp -= 1

                    if (enemy.hp <= 0) {
                        enemy.mesh.parent?.remove(enemy.mesh)
                        iterator.remove()
                    }
                }
            }
        }

        return true
    }

    fun update(deltaTime: Double) {
        if (isAttacking) {
            attackTimeRemaining -= deltaTime

            if (attackTimeRemaining <= 0) {
                isAttacking = false
            }
        }
    }

    companion object {
        private const val ATTACK_RANGE_FACTOR = 3.0

        // Roughly within 60 degrees of dead-ahead.
        private const val FACING_THRESHOLD = 0.5
    }
}
