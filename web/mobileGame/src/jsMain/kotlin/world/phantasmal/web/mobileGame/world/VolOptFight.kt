package world.phantasmal.web.mobileGame.world

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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

/** The two bodies' clips: the core's small set and the machine's full one. */
class VolOptClips(
    val coreWait: NjMotion,
    val coreAttack: NjMotion?,
    val robotStart: NjMotion?,
    val robotWait: NjMotion,
    val robotPunchFront: NjMotion?,
    val robotPunchLeft: NjMotion?,
    val robotPunchRight: NjMotion?,
    val robotAttackBack: NjMotion?,
    val robotDeath: NjMotion?,
)

/**
 * Vol Opt, transcribed from the wiki (wiki.pioneer2.net/w/Vol_Opt): the Mines' control system,
 * fought in two forms.
 *
 * Form 1 -- the monitor room. The core travels the ring of wall monitors, docking at one at a
 * time; while docked it is the only thing that can be hurt. Each dock raises lightning pillars
 * from the floor: the red one casts Gizonde at the player (48 flat on Normal) until destroyed,
 * the blue ones soak hits. Core down, the room goes dark.
 *
 * Form 2 -- the machine under the hatch rises (4000 HP One-Person) and runs the wiki's fixed
 * rotation: homing missiles, two stomper pillars dropped on the player's marked position, a
 * self-heal, then the prison -- a slow ball that cages whoever it touches. Punches answer
 * anyone standing against its base. Weak to lightning, exactly as the stat table says.
 */
class VolOptFight(
    val core: Enemy,
    val robot: Enemy,
    private val coreNjObject: NjObject,
    private val robotNjObject: NjObject,
    private val coreMixer: AnimationMixer,
    private val robotMixer: AnimationMixer,
    private val clips: VolOptClips,
    private val unitScale: Double,
    private val floorY: Double = 0.0,
    /** The monitor ring the core travels, world units, and how high the screens sit. */
    private val monitorRadius: Double = 85.0,
    private val monitorHeight: Double = 22.0,
    /** Raises one pillar; red ones cast. Returns the standing add, or null. */
    private val spawnPillar: (x: Double, z: Double, red: Boolean) -> Enemy?,
    /** The red pillar's Gizonde at the player: [GIZONDE_DAMAGE] flat. */
    private val castGizonde: (fromX: Double, fromY: Double, fromZ: Double) -> Unit,
    /** Launches one slow homing missile from the machine. */
    private val fireMissile: (fromX: Double, fromY: Double, fromZ: Double) -> Unit,
    /** Marks the player's spot and drops a stomper pillar on it. */
    private val stompAt: (x: Double, z: Double) -> Unit,
    /** Launches the prison ball toward the player. */
    private val launchPrison: () -> Unit,
    /** The machine's self-heal, [RESTA_HEAL] hp. */
    private val healRobot: (Int) -> Unit,
    /** A melee-path punch: (atpMultiplier, forceKnockdown). */
    private val strikePlayer: (Double, Boolean) -> Unit,
) {
    private enum class State { F1_DOCKED, F1_MOVING, TRANSITION, F2_RISE, F2_FIGHT, DEAD }

    private enum class Move { MISSILES, STOMP_A, STOMP_B, RESTA, PRISON }

    private val rotation = listOf(Move.MISSILES, Move.STOMP_A, Move.STOMP_B, Move.RESTA, Move.PRISON)
    private var rotationIndex = 0

    private var state = State.F1_DOCKED
    private var stateRemaining = DOCK_SECONDS

    /** Which of the six wall monitors the core is docked at. */
    private var monitorIndex = 0
    private var moveFrom = 0.0
    private var moveTo = 0.0

    private val pillars = mutableListOf<Pair<Enemy, Boolean>>()
    private var gizondeTimer = GIZONDE_INTERVAL_SECONDS
    private var punchTimer = 0.0
    private var moveTimer = 0.0

    private var currentCoreMotion: NjMotion? = null
    private var currentRobotMotion: NjMotion? = null
    private val coreClipCache = HashMap<NjMotion, AnimationClip>()
    private val robotClipCache = HashMap<NjMotion, AnimationClip>()

    init {
        // The machine waits under the hatch, invisible and unhittable, until its turn.
        robot.mesh.visible = false
        robot.untargetable = true
        robot.mesh.position.set(0.0, floorY - ROBOT_SUNK_UNITS * unitScale, 0.0)
        dockCore(0)
        playCore(clips.coreWait)
    }

    val deathDuration: Double =
        clips.robotDeath?.let { (it.frameCount - 1) / PSO_FRAME_RATE_DOUBLE } ?: 3.0

    fun onDeath() {
        state = State.DEAD
        clips.robotDeath?.let { playRobot(it, oneShot = true) }
    }

    fun update(deltaTime: Double, playerPosition: Vector3) {
        coreMixer.update(deltaTime)
        robotMixer.update(deltaTime)
        if (state == State.DEAD) return
        if (robot.isDead) { onDeath(); return }

        stateRemaining -= deltaTime

        when (state) {
            State.F1_DOCKED -> {
                updatePillars(deltaTime, playerPosition)
                if (core.isDead) { beginTransition(); return }
                if (stateRemaining <= 0) {
                    // Undock and travel the ring to the next monitor.
                    moveFrom = monitorAngle(monitorIndex)
                    monitorIndex = (monitorIndex + 1 + (0..1).random()) % MONITOR_COUNT
                    moveTo = monitorAngle(monitorIndex)
                    if (moveTo < moveFrom) moveTo += 2 * PI
                    state = State.F1_MOVING
                    stateRemaining = MOVE_SECONDS
                }
            }

            State.F1_MOVING -> {
                updatePillars(deltaTime, playerPosition)
                if (core.isDead) { beginTransition(); return }
                val progress = 1.0 - (stateRemaining / MOVE_SECONDS).coerceIn(0.0, 1.0)
                val angle = moveFrom + (moveTo - moveFrom) * progress
                positionCore(angle)
                if (stateRemaining <= 0) {
                    dockCore(monitorIndex)
                    state = State.F1_DOCKED
                    stateRemaining = DOCK_SECONDS
                }
            }

            State.TRANSITION -> {
                if (stateRemaining <= 0) {
                    robot.mesh.visible = true
                    robot.untargetable = false
                    clips.robotStart?.let { playRobot(it, oneShot = true) }
                    state = State.F2_RISE
                    stateRemaining = RISE_SECONDS
                }
            }

            State.F2_RISE -> {
                // The machine climbs out of the hatch to its standing height.
                val progress = 1.0 - (stateRemaining / RISE_SECONDS).coerceIn(0.0, 1.0)
                robot.mesh.position.y =
                    floorY - ROBOT_SUNK_UNITS * unitScale * (1.0 - progress)
                if (stateRemaining <= 0) {
                    robot.mesh.position.y = floorY
                    playRobot(clips.robotWait)
                    state = State.F2_FIGHT
                    stateRemaining = MOVE_GAP_SECONDS
                }
            }

            State.F2_FIGHT -> {
                // Punches answer anyone leaning on the machine, between rotation moves.
                punchTimer -= deltaTime
                val dx = playerPosition.x - robot.mesh.position.x
                val dz = playerPosition.z - robot.mesh.position.z
                val punchReach = PUNCH_REACH_UNITS * unitScale
                if (punchTimer <= 0 && dx * dx + dz * dz <= punchReach * punchReach) {
                    punchTimer = PUNCH_COOLDOWN_SECONDS
                    val punch = when {
                        dz < 0 && clips.robotPunchFront != null -> clips.robotPunchFront
                        dx < 0 -> clips.robotPunchLeft ?: clips.robotWait
                        else -> clips.robotPunchRight ?: clips.robotWait
                    }
                    playRobot(punch, oneShot = true)
                    strikePlayer(1.0, false)
                }

                if (stateRemaining <= 0) {
                    startNextMove(playerPosition)
                    stateRemaining = moveTimer
                }
            }

            State.DEAD -> Unit
        }
    }

    /** Where monitor [index] sits on the room's wall ring. */
    private fun monitorAngle(index: Int): Double = index * (2 * PI / MONITOR_COUNT)

    private fun positionCore(angle: Double) {
        core.mesh.position.set(
            sin(angle) * monitorRadius,
            floorY + monitorHeight,
            cos(angle) * monitorRadius,
        )
        // Facing the room's centre from its wall.
        core.mesh.rotation.y = angle + PI
    }

    private fun dockCore(index: Int) {
        positionCore(monitorAngle(index))
        clips.coreAttack?.let { playCore(it, oneShot = true) }

        // Each dock raises its pillars on the floor before the monitor: one red caster, and a
        // blue soak to its side.
        val angle = monitorAngle(index)
        val pillarRadius = monitorRadius * PILLAR_RING_FACTOR
        raisePillar(sin(angle) * pillarRadius, cos(angle) * pillarRadius, red = true)
        val offset = angle + PILLAR_SIDE_OFFSET
        raisePillar(sin(offset) * pillarRadius, cos(offset) * pillarRadius, red = false)
    }

    private fun raisePillar(x: Double, z: Double, red: Boolean) {
        if (pillars.count { !it.first.isDead } >= MAX_PILLARS) return
        spawnPillar(x, z, red)?.let { pillars.add(it to red) }
    }

    private fun updatePillars(deltaTime: Double, playerPosition: Vector3) {
        gizondeTimer -= deltaTime
        if (gizondeTimer <= 0) {
            gizondeTimer = GIZONDE_INTERVAL_SECONDS
            for ((pillar, red) in pillars) {
                if (red && !pillar.isDead) {
                    castGizonde(
                        pillar.mesh.position.x,
                        pillar.mesh.position.y + PILLAR_BOLT_HEIGHT_UNITS * unitScale,
                        pillar.mesh.position.z,
                    )
                }
            }
        }
    }

    private fun beginTransition() {
        // The room powers down: every standing pillar dies with the core.
        for ((pillar, _) in pillars) {
            if (!pillar.isDead) pillar.hp = 0
        }
        state = State.TRANSITION
        stateRemaining = TRANSITION_SECONDS
    }

    private fun startNextMove(playerPosition: Vector3) {
        val move = rotation[rotationIndex % rotation.size]
        rotationIndex++

        val mouthY = floorY + MISSILE_HEIGHT_UNITS * unitScale
        when (move) {
            Move.MISSILES -> {
                clips.robotAttackBack?.let { playRobot(it, oneShot = true) }
                var i = 0
                while (i < MISSILE_COUNT) {
                    fireMissile(robot.mesh.position.x, mouthY, robot.mesh.position.z)
                    i++
                }
                moveTimer = MISSILES_GAP_SECONDS
            }
            Move.STOMP_A, Move.STOMP_B -> {
                clips.robotPunchFront?.let { playRobot(it, oneShot = true) }
                stompAt(playerPosition.x, playerPosition.z)
                moveTimer = STOMP_GAP_SECONDS
            }
            Move.RESTA -> {
                healRobot(RESTA_HEAL)
                moveTimer = RESTA_GAP_SECONDS
            }
            Move.PRISON -> {
                launchPrison()
                moveTimer = PRISON_GAP_SECONDS
            }
        }
    }

    private fun playCore(motion: NjMotion, oneShot: Boolean = false) {
        if (motion.frameCount <= 1) return
        if (motion === currentCoreMotion && !oneShot) return
        val clip = coreClipCache.getOrPut(motion) { createAnimationClip(coreNjObject, motion) }
        coreMixer.stopAllAction()
        val action = coreMixer.clipAction(clip)
        action.reset()
        if (oneShot) { action.setLoop(LoopOnce, 1); action.clampWhenFinished = true }
        else action.setLoop(LoopRepeat, Int.MAX_VALUE)
        action.play()
        currentCoreMotion = motion
    }

    private fun playRobot(motion: NjMotion, oneShot: Boolean = false) {
        if (motion.frameCount <= 1) return
        if (motion === currentRobotMotion && !oneShot) return
        val clip = robotClipCache.getOrPut(motion) { createAnimationClip(robotNjObject, motion) }
        robotMixer.stopAllAction()
        val action = robotMixer.clipAction(clip)
        action.reset()
        if (oneShot) { action.setLoop(LoopOnce, 1); action.clampWhenFinished = true }
        else action.setLoop(LoopRepeat, Int.MAX_VALUE)
        action.play()
        currentRobotMotion = motion
    }

    companion object {
        // The wiki's flat figures on Normal.
        const val GIZONDE_DAMAGE = 48
        const val STOMP_DAMAGE = 45
        const val PRISON_DAMAGE = 40
        const val MISSILE_DAMAGE = 35
        const val RESTA_HEAL = 300

        private const val MONITOR_COUNT = 6
        private const val DOCK_SECONDS = 8.0
        private const val MOVE_SECONDS = 1.4
        private const val TRANSITION_SECONDS = 2.5
        private const val RISE_SECONDS = 3.0
        private const val MOVE_GAP_SECONDS = 2.0

        private const val MAX_PILLARS = 4
        private const val PILLAR_RING_FACTOR = 0.62
        private const val PILLAR_SIDE_OFFSET = 0.5
        private const val PILLAR_BOLT_HEIGHT_UNITS = 6.0
        private const val GIZONDE_INTERVAL_SECONDS = 4.0

        private const val MISSILE_COUNT = 4
        private const val MISSILE_HEIGHT_UNITS = 12.0
        private const val MISSILES_GAP_SECONDS = 5.0
        private const val STOMP_GAP_SECONDS = 4.0
        private const val RESTA_GAP_SECONDS = 3.0
        private const val PRISON_GAP_SECONDS = 6.5

        private const val PUNCH_REACH_UNITS = 14.0
        private const val PUNCH_COOLDOWN_SECONDS = 2.2

        private const val ROBOT_SUNK_UNITS = 30.0
    }
}
