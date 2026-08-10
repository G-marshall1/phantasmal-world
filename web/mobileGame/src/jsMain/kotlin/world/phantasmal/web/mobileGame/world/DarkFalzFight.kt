package world.phantasmal.web.mobileGame.world

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.PSO_FRAME_RATE_DOUBLE
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.LoopOnce
import world.phantasmal.web.externals.three.LoopRepeat
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.player.Enemy

/** The two Normal-difficulty bodies' clips. */
class DarkFalzClips(
    val mountWait: NjMotion,
    val mountBeamLeft: NjMotion?,
    val mountBeamRight: NjMotion?,
    val mountSpawn: NjMotion?,
    val mountCharge: NjMotion?,
    val mountDeath: NjMotion?,
    val soulWait: NjMotion,
    val soulBeamLeft: NjMotion?,
    val soulBeamRight: NjMotion?,
    val soulSlam: NjMotion?,
    val soulCharge: NjMotion?,
    val soulDeath: NjMotion?,
)

/**
 * Dark Falz on Normal difficulty, transcribed from the wiki (wiki.pioneer2.net/w/Dark_Falz):
 * the ancient darkness under the Ruins, fought in the phases Normal actually has.
 *
 * The swarm: the fight opens with Darvants pouring into the arena; contact burns 16 flat.
 * Cutting down enough of them calls the first form up.
 *
 * The mount (2500 HP One-Person): Falz rides the three-headed beast in a slow circuit of the
 * arena, breathing lines of fire, freezing the ground underfoot, dropping Divine Punishment on
 * the player's position (140 flat, telegraphed), disgorging fresh Darvants, and draining
 * whoever stands against it (130, and the wound feeds it).
 *
 * The soul (3500 HP): the mount falls and the true body hovers the arena's edge, throwing
 * bouncing lines of fire and ice -- the ice freezes one time in five -- slamming the ground
 * (knockdown), and keeping Divine Punishment (130). Kill it and Ragol is free.
 *
 * The Hard-and-above ring phase is deliberately absent: this game runs Normal.
 */
class DarkFalzFight(
    val mount: Enemy,
    val soul: Enemy,
    private val mountNjObject: NjObject,
    private val soulNjObject: NjObject,
    private val mountMixer: AnimationMixer,
    private val soulMixer: AnimationMixer,
    private val clips: DarkFalzClips,
    private val unitScale: Double,
    private val floorY: Double = 0.0,
    /** The circuit both forms ride, world units from the arena's centre. */
    private val orbitRadius: Double = 65.0,
    /** Spawns one Darvant at (x, z); returns it standing, or null. */
    private val spawnDarvant: (x: Double, z: Double) -> Enemy?,
    /** A line of fire orbs from (x,y,z) toward the player. */
    private val fireVolley: (fromX: Double, fromY: Double, fromZ: Double, ice: Boolean) -> Unit,
    /** Divine Punishment: telegraph at (x, z), then [damage] flat where it lands. */
    private val divineAt: (x: Double, z: Double, damage: Int) -> Unit,
    /** The ground slam: flat damage + knockdown in its circle around the soul. */
    private val slamAround: (x: Double, z: Double, radiusUnits: Double, damage: Int) -> Unit,
    /** The drain: flat damage to the player, and the same amount back to the boss. */
    private val drainPlayer: (boss: Enemy, damage: Int) -> Unit,
    /** A flat contact burn, i-frame capped -- the Darvants' touch. */
    private val strikePlayerFixed: (Int, Boolean) -> Unit,
) {
    private enum class State { SWARM, MOUNT, TRANSITION, SOUL, DEAD }

    private enum class Move { FIRE, ICE, DIVINE, DARVANTS, DRAIN }

    private val mountRotation = listOf(Move.FIRE, Move.DIVINE, Move.ICE, Move.DARVANTS, Move.FIRE, Move.DRAIN)
    private val soulRotation = listOf(Move.FIRE, Move.ICE, Move.DIVINE, Move.ICE, Move.DRAIN)
    private var rotationIndex = 0

    private var state = State.SWARM
    private var stateRemaining = 0.0
    private var moveTimer = SWARM_FIRST_WAVE_DELAY

    /** The swarm phase's scoreboard: how many Darvants have been put down. */
    private var darvantsKilled = 0
    private val darvants = mutableListOf<Enemy>()
    private var swarmBobPhase = 0.0

    /** Both forms ride the same circuit; the soul rides it faster. */
    private var orbitAngle = PI

    private var currentMountMotion: NjMotion? = null
    private var currentSoulMotion: NjMotion? = null
    private val mountClipCache = HashMap<NjMotion, AnimationClip>()
    private val soulClipCache = HashMap<NjMotion, AnimationClip>()

    init {
        // Both bodies wait out of sight below the altar until their phase calls them up.
        mount.mesh.visible = false
        mount.untargetable = true
        mount.mesh.position.set(0.0, floorY - SUNK_UNITS * unitScale, orbitRadius)
        soul.mesh.visible = false
        soul.untargetable = true
        soul.mesh.position.set(0.0, floorY - SUNK_UNITS * unitScale, 0.0)
    }

    val deathDuration: Double =
        clips.soulDeath?.let { (it.frameCount - 1) / PSO_FRAME_RATE_DOUBLE } ?: 4.0

    fun onDeath() {
        state = State.DEAD
        clips.soulDeath?.let { playSoul(it, oneShot = true) }
    }

    fun update(deltaTime: Double, playerPosition: Vector3) {
        mountMixer.update(deltaTime)
        soulMixer.update(deltaTime)
        if (state == State.DEAD) return
        if (soul.isDead) { onDeath(); return }

        updateDarvants(deltaTime, playerPosition)
        stateRemaining -= deltaTime
        moveTimer -= deltaTime

        when (state) {
            State.SWARM -> {
                if (moveTimer <= 0) {
                    moveTimer = SWARM_WAVE_SECONDS
                    spawnDarvantRing(SWARM_WAVE_SIZE)
                }
                if (darvantsKilled >= SWARM_KILLS_TO_ADVANCE) {
                    // The swarm parts, and the mount climbs out of the dark.
                    mount.mesh.visible = true
                    mount.untargetable = false
                    playMount(clips.mountWait)
                    state = State.MOUNT
                    stateRemaining = RISE_SECONDS
                    moveTimer = RISE_SECONDS + MOVE_GAP_SECONDS
                    rotationIndex = 0
                }
            }

            State.MOUNT -> {
                if (mount.isDead) {
                    // The beast falls; the true body tears free of it.
                    clips.mountDeath?.let { playMount(it, oneShot = true) }
                    state = State.TRANSITION
                    stateRemaining = TRANSITION_SECONDS
                    return
                }
                orbit(mount, deltaTime, MOUNT_ORBIT_SPEED)
                if (moveTimer <= 0) {
                    startMove(mount, mountRotation, playerPosition, mounted = true)
                }
            }

            State.TRANSITION -> {
                if (stateRemaining <= 0) {
                    mount.mesh.visible = false
                    soul.mesh.visible = true
                    soul.untargetable = false
                    soul.mesh.position.set(
                        mount.mesh.position.x, floorY + SOUL_HOVER_UNITS * unitScale,
                        mount.mesh.position.z,
                    )
                    playSoul(clips.soulWait)
                    state = State.SOUL
                    moveTimer = MOVE_GAP_SECONDS
                    rotationIndex = 0
                }
            }

            State.SOUL -> {
                orbit(soul, deltaTime, SOUL_ORBIT_SPEED)
                soul.mesh.position.y = floorY + SOUL_HOVER_UNITS * unitScale +
                    sin(orbitAngle * 3) * SOUL_BOB_UNITS * unitScale
                if (moveTimer <= 0) {
                    startMove(soul, soulRotation, playerPosition, mounted = false)
                }
            }

            State.DEAD -> Unit
        }
    }

    /** Rides the circuit, always facing the arena's centre. */
    private fun orbit(body: Enemy, deltaTime: Double, speed: Double) {
        orbitAngle += deltaTime * speed
        body.mesh.position.x = sin(orbitAngle) * orbitRadius
        body.mesh.position.z = cos(orbitAngle) * orbitRadius
        if (body === mount) body.mesh.position.y = floorY
        body.mesh.rotation.y = orbitAngle + PI
    }

    private fun startMove(body: Enemy, rotation: List<Move>, playerPosition: Vector3, mounted: Boolean) {
        val move = rotation[rotationIndex % rotation.size]
        rotationIndex++

        val mouthY = floorY + (if (mounted) MOUNT_MOUTH_UNITS else SOUL_MOUTH_UNITS) * unitScale
        val divineDamage = if (mounted) DIVINE_DAMAGE_MOUNT else DIVINE_DAMAGE_SOUL
        val drainDamage = if (mounted) DRAIN_DAMAGE_MOUNT else DRAIN_DAMAGE_SOUL

        when (move) {
            Move.FIRE -> {
                play(body, if (Random.nextBoolean()) beamLeft(mounted) else beamRight(mounted))
                fireVolley(body.mesh.position.x, mouthY, body.mesh.position.z, false)
                moveTimer = FIRE_GAP_SECONDS
            }
            Move.ICE -> {
                play(body, beamLeft(mounted))
                fireVolley(body.mesh.position.x, mouthY, body.mesh.position.z, true)
                moveTimer = FIRE_GAP_SECONDS
            }
            Move.DIVINE -> {
                play(body, charge(mounted))
                divineAt(playerPosition.x, playerPosition.z, divineDamage)
                moveTimer = DIVINE_GAP_SECONDS
            }
            Move.DARVANTS -> {
                play(body, if (mounted) clips.mountSpawn else null)
                spawnDarvantRing(MOUNT_DARVANT_WAVE)
                moveTimer = DARVANT_GAP_SECONDS
            }
            Move.DRAIN -> {
                val dx = playerPosition.x - body.mesh.position.x
                val dz = playerPosition.z - body.mesh.position.z
                val reach = DRAIN_REACH_UNITS * unitScale
                if (dx * dx + dz * dz <= reach * reach) {
                    play(body, charge(mounted))
                    drainPlayer(body, drainDamage)
                } else if (!mounted) {
                    // Out of reach: the soul slams the ground instead.
                    play(body, clips.soulSlam)
                    slamAround(
                        body.mesh.position.x, body.mesh.position.z,
                        SLAM_RADIUS_UNITS, SLAM_DAMAGE,
                    )
                }
                moveTimer = DRAIN_GAP_SECONDS
            }
        }
    }

    private fun beamLeft(mounted: Boolean) = if (mounted) clips.mountBeamLeft else clips.soulBeamLeft
    private fun beamRight(mounted: Boolean) = if (mounted) clips.mountBeamRight else clips.soulBeamRight
    private fun charge(mounted: Boolean) = if (mounted) clips.mountCharge else clips.soulCharge

    private fun play(body: Enemy, motion: NjMotion?) {
        if (motion == null) return
        if (body === mount) playMount(motion, oneShot = true) else playSoul(motion, oneShot = true)
    }

    /** A ring of Darvants pouring in from the arena's rim. */
    private fun spawnDarvantRing(count: Int) {
        var spawned = 0
        var i = 0
        while (i < count) {
            val angle = Random.nextDouble() * 2 * PI
            val darvant = spawnDarvant(
                sin(angle) * DARVANT_SPAWN_RADIUS,
                cos(angle) * DARVANT_SPAWN_RADIUS,
            )
            if (darvant != null) {
                darvants.add(darvant)
                spawned++
            }
            i++
        }
    }

    /** The swarm: each Darvant drifts straight at the player, burning on contact. */
    private fun updateDarvants(deltaTime: Double, playerPosition: Vector3) {
        swarmBobPhase += deltaTime * DARVANT_BOB_SPEED
        val iterator = darvants.iterator()
        while (iterator.hasNext()) {
            val darvant = iterator.next()
            if (darvant.isDead) {
                darvantsKilled++
                iterator.remove()
                continue
            }
            val dx = playerPosition.x - darvant.mesh.position.x
            val dz = playerPosition.z - darvant.mesh.position.z
            val distance = kotlin.math.sqrt(dx * dx + dz * dz)
            if (distance > 1e-3) {
                val step = DARVANT_SPEED_UNITS * unitScale * deltaTime
                darvant.mesh.position.x += dx / distance * step
                darvant.mesh.position.z += dz / distance * step
                darvant.mesh.rotation.y = atan2(dx, dz)
            }
            darvant.mesh.position.y =
                floorY + (DARVANT_HOVER_UNITS + sin(swarmBobPhase + darvant.mesh.position.x) * 0.6) * unitScale

            val contact = (DARVANT_CONTACT_UNITS + 1.0) * unitScale
            if (distance <= contact) {
                strikePlayerFixed(
                    if (state == State.SOUL) DARVANT_CONTACT_DAMAGE_SOUL else DARVANT_CONTACT_DAMAGE,
                    false,
                )
            }
        }
    }

    private fun playMount(motion: NjMotion, oneShot: Boolean = false) {
        if (motion.frameCount <= 1) return
        if (motion === currentMountMotion && !oneShot) return
        val clip = mountClipCache.getOrPut(motion) { createAnimationClip(mountNjObject, motion) }
        mountMixer.stopAllAction()
        val action = mountMixer.clipAction(clip)
        action.reset()
        if (oneShot) { action.setLoop(LoopOnce, 1); action.clampWhenFinished = true }
        else action.setLoop(LoopRepeat, Int.MAX_VALUE)
        action.play()
        currentMountMotion = motion
    }

    private fun playSoul(motion: NjMotion, oneShot: Boolean = false) {
        if (motion.frameCount <= 1) return
        if (motion === currentSoulMotion && !oneShot) return
        val clip = soulClipCache.getOrPut(motion) { createAnimationClip(soulNjObject, motion) }
        soulMixer.stopAllAction()
        val action = soulMixer.clipAction(clip)
        action.reset()
        if (oneShot) { action.setLoop(LoopOnce, 1); action.clampWhenFinished = true }
        else action.setLoop(LoopRepeat, Int.MAX_VALUE)
        action.play()
        currentSoulMotion = motion
    }

    companion object {
        // The wiki's flat figures on Normal.
        const val DARVANT_CONTACT_DAMAGE = 16
        const val DARVANT_CONTACT_DAMAGE_SOUL = 13
        const val DIVINE_DAMAGE_MOUNT = 140
        const val DIVINE_DAMAGE_SOUL = 130
        const val DRAIN_DAMAGE_MOUNT = 130
        const val DRAIN_DAMAGE_SOUL = 125
        const val SLAM_DAMAGE = 60
        const val SLAM_RADIUS_UNITS = 16.0
        const val ICE_FREEZE_CHANCE = 0.2

        private const val SWARM_FIRST_WAVE_DELAY = 1.5
        private const val SWARM_WAVE_SECONDS = 7.0
        private const val SWARM_WAVE_SIZE = 5
        private const val SWARM_KILLS_TO_ADVANCE = 8
        private const val MOUNT_DARVANT_WAVE = 3

        private const val RISE_SECONDS = 3.0
        private const val TRANSITION_SECONDS = 3.0
        private const val MOVE_GAP_SECONDS = 2.5
        private const val FIRE_GAP_SECONDS = 3.4
        private const val DIVINE_GAP_SECONDS = 4.2
        private const val DARVANT_GAP_SECONDS = 4.5
        private const val DRAIN_GAP_SECONDS = 4.0

        private const val MOUNT_ORBIT_SPEED = 0.14
        private const val SOUL_ORBIT_SPEED = 0.3
        private const val MOUNT_MOUTH_UNITS = 14.0
        private const val SOUL_MOUTH_UNITS = 10.0
        private const val SOUL_HOVER_UNITS = 8.0
        private const val SOUL_BOB_UNITS = 1.5
        private const val SUNK_UNITS = 40.0

        private const val DRAIN_REACH_UNITS = 16.0

        private const val DARVANT_SPAWN_RADIUS = 95.0
        private const val DARVANT_SPEED_UNITS = 4.2
        private const val DARVANT_HOVER_UNITS = 1.2
        private const val DARVANT_CONTACT_UNITS = 1.2
        private const val DARVANT_BOB_SPEED = 3.0
    }
}
