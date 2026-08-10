package world.phantasmal.web.mobileGame.world

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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

/** The clips the raft fight actually drives -- a subset of the archive's seventeen. */
class DeRolLeClips(
    val enter: NjMotion?,
    /** The undulating swim alongside the raft -- the fight's resting loop. */
    val forward: NjMotion,
    val biteLeft: NjMotion?,
    val biteRight: NjMotion?,
    /** Side-to-side leaps over the raft -- how it changes flanks. */
    val jumpLeftToRight: NjMotion?,
    val jumpRightToLeft: NjMotion?,
    /** The mine scatter throw. */
    val scatter: NjMotion?,
    val beamCharge: NjMotion?,
    val beam: NjMotion?,
    val death: NjMotion?,
)

/**
 * De Rol Le, transcribed from the wiki (wiki.pioneer2.net/w/De_Rol_Le): the worm that stalks
 * the raft through the underground waterway. It swims alongside the platform, undulating, and
 * cycles a fixed attack rotation -- mines flung onto the deck in an X, a wide purple barrage,
 * tentacle strikes at whoever stands near its flank, mines again (aimed this time), a rock
 * shower shaken loose from the tunnel roof, and a charged beam swept across a band of the deck.
 * Between rounds it dives and resurfaces on the other flank. On Normal the wiki's flat damage
 * figures are: spike mine 31, orb barrage 61, beam 85 -- unmodified by DFP, dodged or worn.
 *
 * The host owns every deck effect (mines, orbs, rocks, the beam) through the callbacks;
 * this class owns the boss's body, timing, and rotation.
 */
class DeRolLeFight(
    val enemy: Enemy,
    private val njObject: NjObject,
    private val mixer: AnimationMixer,
    private val clips: DeRolLeClips,
    /** World units per PSO unit. */
    private val unitScale: Double,
    /** The raft deck's height, and its half-extents in world units. */
    private val deckY: Double = 0.0,
    private val raftHalfX: Double = 55.0,
    private val raftHalfZ: Double = 80.0,
    /** A melee-path blow (tentacles): (atpMultiplier, forceKnockdown). Rolls to hit, can crit. */
    private val strikePlayer: (Double, Boolean) -> Unit,
    /** A wiki flat-damage hit: (damage, forceKnockdown). Rate-capped by the i-frame window. */
    private val strikePlayerFixed: (Int, Boolean) -> Unit,
    /** Drops one blinking mine on the deck; the host detonates it for [MINE_DAMAGE]. */
    private val spawnMine: (x: Double, z: Double) -> Unit,
    /** Fires one purple orb from the boss across the deck; [ORB_DAMAGE] on contact. */
    private val spawnOrb: (fromX: Double, fromY: Double, fromZ: Double, dirX: Double, dirZ: Double) -> Unit,
    /** A rock breaking from the roof toward the deck point; mine-grade damage on landing. */
    private val spawnRock: (x: Double, z: Double) -> Unit,
    /** The beam: telegraph then fire across the deck band centred on [z] (world units). */
    private val fireBeam: (z: Double) -> Unit,
) {
    private enum class State { ENTER, SWIM, TENTACLES, SCATTER, BEAM_CHARGE, BEAM_FIRE, SWITCH, DEAD }

    /** The rotation, straight from the wiki's listing. */
    private enum class Move { MINES_X, BARRAGE, TENTACLES, MINES_AIMED, ROCKS, BEAM, SWITCH }

    private val rotation = listOf(
        Move.MINES_X, Move.BARRAGE, Move.TENTACLES, Move.BARRAGE, Move.MINES_AIMED,
        Move.BARRAGE, Move.TENTACLES, Move.ROCKS, Move.BEAM, Move.SWITCH,
        Move.MINES_X, Move.BEAM,
    )
    private var rotationIndex = 0

    private var state = State.ENTER
    private var stateRemaining = ENTER_SECONDS
    private val mesh = enemy.mesh

    /** Which flank of the raft the worm rides: +1 is the player's right (+x). */
    private var side = 1.0

    /** Drift along the raft's length while swimming. */
    private var driftPhase = 0.0

    private var currentMotion: NjMotion? = null
    private val clipCache = HashMap<NjMotion, AnimationClip>()

    /** Sub-timers inside multi-hit states (tentacle strikes, beam count). */
    private var hitsRemaining = 0
    private var hitTimer = 0.0
    private var beamZ = 0.0

    val deathDuration: Double =
        clips.death?.let { (it.frameCount - 1) / PSO_FRAME_RATE_DOUBLE } ?: 3.0

    fun onDeath() {
        state = State.DEAD
        enemy.untargetable = false
        clips.death?.let { playClip(it, oneShot = true) }
    }

    fun update(deltaTime: Double, playerPosition: Vector3) {
        if (state != State.DEAD && enemy.isDead) onDeath()
        mixer.update(deltaTime)
        if (state == State.DEAD) return

        stateRemaining -= deltaTime

        when (state) {
            State.ENTER -> {
                // Rising alongside the raft's right flank as the fight opens.
                positionBeside(deltaTime)
                if (stateRemaining <= 0) beginSwim(SWIM_BETWEEN_MOVES_SECONDS)
            }

            State.SWIM -> {
                positionBeside(deltaTime)
                if (stateRemaining <= 0) startNextMove(playerPosition)
            }

            State.TENTACLES -> {
                positionBeside(deltaTime)
                hitTimer -= deltaTime
                if (hitTimer <= 0 && hitsRemaining > 0) {
                    hitsRemaining--
                    hitTimer = TENTACLE_INTERVAL_SECONDS
                    playClip(
                        (if (side > 0) clips.biteRight else clips.biteLeft)
                            ?: clips.forward,
                        oneShot = true,
                    )
                    // The thrust reaches whoever stands near this flank of the deck.
                    val nearFlank =
                        abs(playerPosition.x - side * raftHalfX) < TENTACLE_REACH_UNITS * unitScale &&
                            abs(playerPosition.z - mesh.position.z) < TENTACLE_SPAN_UNITS * unitScale
                    if (nearFlank) strikePlayer(1.0, false)
                }
                if (hitsRemaining <= 0 && hitTimer <= 0) beginSwim(SWIM_BETWEEN_MOVES_SECONDS)
            }

            State.SCATTER -> {
                positionBeside(deltaTime)
                if (stateRemaining <= 0) beginSwim(SWIM_BETWEEN_MOVES_SECONDS)
            }

            State.BEAM_CHARGE -> {
                positionBeside(deltaTime)
                if (stateRemaining <= 0) {
                    playClip(clips.beam ?: clips.forward, oneShot = true)
                    fireBeam(beamZ)
                    hitsRemaining--
                    if (hitsRemaining > 0) {
                        // The sweep steps up the deck, band by band.
                        beamZ += BEAM_STEP_UNITS * unitScale
                        state = State.BEAM_CHARGE
                        stateRemaining = BEAM_REFIRE_SECONDS
                    } else {
                        state = State.BEAM_FIRE
                        stateRemaining = BEAM_RECOVER_SECONDS
                    }
                }
            }

            State.BEAM_FIRE -> {
                positionBeside(deltaTime)
                if (stateRemaining <= 0) beginSwim(SWIM_BETWEEN_MOVES_SECONDS)
            }

            State.SWITCH -> {
                // Mid-leap over the raft: untargetable, crossing to the other flank.
                val progress = 1.0 - (stateRemaining / SWITCH_SECONDS).coerceIn(0.0, 1.0)
                val fromX = side * (raftHalfX + FLANK_MARGIN_UNITS * unitScale)
                val toX = -fromX
                mesh.position.x = fromX + (toX - fromX) * progress
                // An arc over the deck.
                mesh.position.y = deckY + BODY_SINK_UNITS * unitScale +
                    sin(progress * PI) * JUMP_ARC_UNITS * unitScale
                if (stateRemaining <= 0) {
                    side = -side
                    enemy.untargetable = false
                    beginSwim(SWIM_BETWEEN_MOVES_SECONDS)
                }
            }

            State.DEAD -> Unit
        }
    }

    /** Holds the worm at its flank lane, drifting slowly along the raft's length. */
    private fun positionBeside(deltaTime: Double) {
        driftPhase += deltaTime * DRIFT_SPEED
        val laneX = side * (raftHalfX + FLANK_MARGIN_UNITS * unitScale)
        mesh.position.x = laneX
        mesh.position.z = sin(driftPhase) * raftHalfZ * DRIFT_SPAN
        mesh.position.y = deckY + BODY_SINK_UNITS * unitScale
        // Facing along the raft, nose toward its drift direction; body broadside to the deck.
        mesh.rotation.y = if (kotlin.math.cos(driftPhase) >= 0) 0.0 else PI
    }

    private fun beginSwim(seconds: Double) {
        state = State.SWIM
        stateRemaining = seconds
        playClip(clips.forward)
    }

    private fun startNextMove(playerPosition: Vector3) {
        val move = rotation[rotationIndex % rotation.size]
        rotationIndex++

        when (move) {
            Move.MINES_X -> {
                // Five mines in an X across the middle of the deck.
                playClip(clips.scatter ?: clips.forward, oneShot = true)
                val reach = MINE_X_SPREAD_UNITS * unitScale
                spawnMine(0.0, mesh.position.z)
                spawnMine(reach, mesh.position.z + reach)
                spawnMine(-reach, mesh.position.z + reach)
                spawnMine(reach, mesh.position.z - reach)
                spawnMine(-reach, mesh.position.z - reach)
                state = State.SCATTER
                stateRemaining = SCATTER_SECONDS
            }

            Move.MINES_AIMED -> {
                // The aimed set lands around whoever is standing wherever they're standing.
                playClip(clips.scatter ?: clips.forward, oneShot = true)
                val spread = MINE_AIMED_SPREAD_UNITS * unitScale
                spawnMine(playerPosition.x, playerPosition.z)
                spawnMine(playerPosition.x + spread, playerPosition.z)
                spawnMine(playerPosition.x - spread, playerPosition.z)
                spawnMine(playerPosition.x, playerPosition.z + spread)
                spawnMine(playerPosition.x, playerPosition.z - spread)
                state = State.SCATTER
                stateRemaining = SCATTER_SECONDS
            }

            Move.BARRAGE -> {
                // A fan of purple orbs from the flank, crossing the whole deck.
                playClip(clips.forward)
                val fromX = mesh.position.x
                val fromY = deckY + ORB_HEIGHT_UNITS * unitScale
                val fromZ = mesh.position.z
                var i = 0
                while (i < BARRAGE_COUNT) {
                    // Fanned across the deck, angled toward the player's half.
                    val t = (i.toDouble() / (BARRAGE_COUNT - 1)) * 2.0 - 1.0
                    val dirX = -side
                    val dirZ = t * BARRAGE_FAN
                    val length = kotlin.math.sqrt(dirX * dirX + dirZ * dirZ)
                    spawnOrb(fromX, fromY, fromZ, dirX / length, dirZ / length)
                    i++
                }
                state = State.SCATTER
                stateRemaining = BARRAGE_SECONDS
            }

            Move.TENTACLES -> {
                state = State.TENTACLES
                hitsRemaining = TENTACLE_COUNT
                hitTimer = 0.4
                stateRemaining = 10.0
            }

            Move.ROCKS -> {
                // The roof shaken loose: rocks rain across the deck.
                playClip(clips.scatter ?: clips.forward, oneShot = true)
                var i = 0
                while (i < ROCK_COUNT) {
                    val x = (kotlin.random.Random.nextDouble() * 2 - 1) * raftHalfX * 0.9
                    val z = (kotlin.random.Random.nextDouble() * 2 - 1) * raftHalfZ * 0.9
                    spawnRock(x, z)
                    i++
                }
                // One of them aimed to keep standing still dishonest.
                spawnRock(playerPosition.x, playerPosition.z)
                state = State.SCATTER
                stateRemaining = ROCKS_SECONDS
            }

            Move.BEAM -> {
                playClip(clips.beamCharge ?: clips.forward, oneShot = true)
                hitsRemaining = BEAM_COUNT
                // The sweep opens at the deck end nearer the boss's drift and walks up it.
                beamZ = -raftHalfZ * 0.7
                state = State.BEAM_CHARGE
                stateRemaining = BEAM_CHARGE_SECONDS
            }

            Move.SWITCH -> {
                playClip(
                    (if (side > 0) clips.jumpRightToLeft else clips.jumpLeftToRight)
                        ?: clips.forward,
                    oneShot = true,
                )
                enemy.untargetable = true
                state = State.SWITCH
                stateRemaining = SWITCH_SECONDS
            }
        }
    }

    private fun playClip(motion: NjMotion, oneShot: Boolean = false) {
        if (motion.frameCount <= 1) return
        if (motion === currentMotion && !oneShot) return
        val clip = clipCache.getOrPut(motion) { createAnimationClip(njObject, motion) }
        mixer.stopAllAction()
        val action = mixer.clipAction(clip)
        action.reset()
        if (oneShot) {
            action.setLoop(LoopOnce, 1)
            action.clampWhenFinished = true
        } else {
            action.setLoop(LoopRepeat, Int.MAX_VALUE)
        }
        action.play()
        currentMotion = motion
    }

    companion object {
        // The wiki's flat damage figures on Normal.
        const val MINE_DAMAGE = 31
        const val ORB_DAMAGE = 61
        const val BEAM_DAMAGE = 85

        private const val ENTER_SECONDS = 3.2
        private const val SWIM_BETWEEN_MOVES_SECONDS = 2.6
        private const val SCATTER_SECONDS = 2.2
        private const val BARRAGE_SECONDS = 2.4
        private const val ROCKS_SECONDS = 2.8
        private const val SWITCH_SECONDS = 1.6

        private const val TENTACLE_COUNT = 4
        private const val TENTACLE_INTERVAL_SECONDS = 1.1
        private const val TENTACLE_REACH_UNITS = 14.0
        private const val TENTACLE_SPAN_UNITS = 12.0

        private const val BEAM_COUNT = 4
        private const val BEAM_CHARGE_SECONDS = 1.6
        private const val BEAM_REFIRE_SECONDS = 0.9
        private const val BEAM_RECOVER_SECONDS = 1.4
        private const val BEAM_STEP_UNITS = 18.0

        private const val BARRAGE_COUNT = 7
        private const val BARRAGE_FAN = 0.85
        private const val ORB_HEIGHT_UNITS = 4.0

        private const val MINE_X_SPREAD_UNITS = 14.0
        private const val MINE_AIMED_SPREAD_UNITS = 7.0
        private const val ROCK_COUNT = 6

        /** How far off the raft's edge the body rides, and how deep it sits in the water. */
        private const val FLANK_MARGIN_UNITS = 10.0
        private const val BODY_SINK_UNITS = -2.0
        private const val JUMP_ARC_UNITS = 16.0

        /** The slow patrol along the raft's length. */
        private const val DRIFT_SPEED = 0.35
        private const val DRIFT_SPAN = 0.55
    }
}
