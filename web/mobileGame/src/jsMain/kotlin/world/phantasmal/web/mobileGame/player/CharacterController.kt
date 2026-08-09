package world.phantasmal.web.mobileGame.player

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.world.GameMap
import world.phantasmal.web.mobileGame.world.WallCollider
import world.phantasmal.web.mobileGame.world.findGroundHeight

/**
 * Drives [position] (expected to be the player mesh's own `Object3D.position`, mutated directly
 * so there's no separate copy step each frame) from joystick input rotated into camera-relative
 * space, then applies wall push-out and ground-height snapping. Tracks the resulting facing [yaw]
 * and [isMoving] for the caller to drive animation state with.
 */
class CharacterController(
    gameMap: GameMap,
    val position: Vector3,
    bSphereRadius: Double,
    /** Doorways carved through the collision -- see [WallCollider]. */
    private val passageZones: List<world.phantasmal.web.mobileGame.world.PassageZone> = emptyList(),
) {
    var yaw: Double = 0.0
        private set

    /**
     * Faces [angle] immediately. The focus lock uses this when an attack or cast snaps the
     * character onto its target -- without also updating the controller's own yaw, the next
     * joystick frame would snap the character straight back to where it was walking.
     */
    fun faceToward(angle: Double) {
        yaw = angle
    }

    var isMoving: Boolean = false
        private set

    /**
     * True while the stick is past the run threshold. PSO has no jog: input is either below the
     * deadzone, walking, or running, with nothing in between (see [RUN_THRESHOLD]).
     */
    var isRunning: Boolean = false
        private set

    /**
     * Moves the character instantly, for a teleporter pad. Clears [fallVelocity] so the arrival
     * doesn't inherit whatever downward speed had built up before the warp -- otherwise warping
     * while airborne lands you at the destination already falling fast.
     */
    fun teleportTo(x: Double, y: Double, z: Double, newYaw: Double) {
        position.set(x, y, z)
        yaw = newYaw
        fallVelocity = 0.0
    }

    /**
     * DEBUG: noclip flight, toggled by [world.phantasmal.web.mobileGame.input.FlyToggleButton] --
     * skips wall push-out and ground snapping entirely so the map can be explored freely (through
     * walls, unaffected by gravity) to find a good spawn coordinate by eye. Turning it back off
     * resumes real gravity (see [fallVelocity]) so the character drops back down onto whatever's
     * below instead of staying stuck floating at flying height.
     */
    var flying: Boolean = false

    /**
     * Accumulated downward speed while falling (not flying, and not already resting on ground
     * within [maxStepHeight]) -- real acceleration rather than an instant snap, since a snap only
     * conditioned on the *current* frame's raycast landing a hit leaves the character floating
     * forever the moment it's above a gap in the walkable mesh (a real possibility: walkable
     * triangles are a sparse, scattered subset of the whole mesh, see [findGroundHeight]'s doc
     * comment) or too far above a hit to treat as a normal walking step. Reset to zero the instant
     * the character is grounded or flying.
     */
    private var fallVelocity = 0.0

    /** Whether the stick sat inside the deadzone last frame -- see the flick-turn note in update. */
    private var stickWasCentred = true

    private val maxStepHeight = bSphereRadius * MAX_STEP_HEIGHT_FACTOR

    // Authored collision means every wall is a real wall: no stepping over low ones (that
    // workaround exists for synthesized collision misreading curbs as walls -- see WallCollider's
    // stepHeight doc), and flags decide wall-versus-floor rather than slope.
    private val wallCollider = WallCollider(
        gameMap.collisionGeometry,
        stepHeight = if (gameMap.hasAuthoredCollision) 0.0 else maxStepHeight,
        authoredFlags = gameMap.hasAuthoredCollision,
        passageZones = passageZones,
    )
    private val walkable = gameMap.walkableCollisionObject
    private val radius = bSphereRadius * HITBOX_RADIUS_FACTOR
    private val verticalTolerance = bSphereRadius * VERTICAL_TOLERANCE_FACTOR
    private val runSpeed = bSphereRadius * SPEED_FACTOR
    private val walkSpeed = runSpeed / RUN_TO_WALK_RATIO
    private val flySpeed = bSphereRadius * FLY_SPEED_FACTOR
    private val gravity = bSphereRadius * GRAVITY_FACTOR

    /**
     * [joystickX]/[joystickY] are the raw joystick input in [-1, 1]; [cameraYaw] is the camera's
     * current effective facing, used to rotate the joystick's screen-relative input into
     * world-relative movement (joystick "up" = away from the camera). While [isAttacking], the
     * character commits to its swing: facing stays locked and movement is cut to a small fraction
     * of normal speed (a slight drift, not a full stop) rather than continuing at full speed.
     * [ascend]/[descend] only matter while [flying] -- see [FlightVerticalControls][world.phantasmal.web.mobileGame.input.FlightVerticalControls].
     */
    fun update(
        deltaTime: Double,
        joystickX: Double,
        joystickY: Double,
        cameraYaw: Double,
        isAttacking: Boolean,
        ascend: Boolean = false,
        descend: Boolean = false,
    ) {
        val inputLength = sqrt(joystickX * joystickX + joystickY * joystickY)
        isMoving = inputLength > DEAD_ZONE
        isRunning = inputLength >= RUN_THRESHOLD

        if (isMoving) {
            val sinYaw = sin(cameraYaw)
            val cosYaw = cos(cameraYaw)

            // Rotate joystick space (x = right, y = forward) into world XZ space, using the same
            // forward/right basis as ThirdPersonCameraController.
            val worldDirX = joystickY * sinYaw - joystickX * cosYaw
            val worldDirZ = joystickY * cosYaw + joystickX * sinYaw

            // Two discrete speeds rather than an analog throttle: past the deadzone you walk,
            // past [RUN_THRESHOLD] you run, and there is nothing in between.
            val effectiveSpeed = when {
                flying -> flySpeed
                isAttacking -> runSpeed * ATTACK_SPEED_FACTOR
                isRunning -> runSpeed
                else -> walkSpeed
            }
            position.x += worldDirX / inputLength * effectiveSpeed * deltaTime
            position.z += worldDirZ / inputLength * effectiveSpeed * deltaTime

            if (!isAttacking) {
                val targetYaw = atan2(worldDirX, worldDirZ)

                // Turning mid-run sweeps through an arc instead of snapping, which costs ground.
                // Letting the stick fall back through the deadzone and flicking again skips that
                // -- the standing trick for changing direction without losing momentum -- so a
                // push that starts from centre always turns instantly.
                yaw = if (isRunning && !stickWasCentred) {
                    approachAngle(yaw, targetYaw, RUN_TURN_RATE * deltaTime)
                } else {
                    targetYaw
                }
            }
        }

        stickWasCentred = inputLength <= DEAD_ZONE

        if (flying) {
            fallVelocity = 0.0
            if (ascend) position.y += flySpeed * deltaTime
            if (descend) position.y -= flySpeed * deltaTime
            return
        }

        wallCollider.resolve(position, radius, verticalTolerance)

        val groundY = findGroundHeight(walkable, position.x, position.z)

        if (groundY != null && abs(groundY - position.y) <= maxStepHeight) {
            // Close enough to count as normal walking (stairs, slopes) -- real terrain height only
            // ever changes gradually frame-to-frame, never in one large jump, so snap directly.
            position.y = groundY
            fallVelocity = 0.0
        } else if (groundY == null || position.y > groundY) {
            // Either no walkable surface directly below at all (the sparse-mesh gap case above) or
            // well above one (e.g. just came out of flying, or the initial spawn drop) -- actually
            // fall under gravity instead of teleporting or doing nothing, so both cases look and
            // feel like landing rather than leaving the character floating in place indefinitely.
            fallVelocity += gravity * deltaTime
            position.y -= fallVelocity * deltaTime

            if (groundY != null && position.y <= groundY) {
                position.y = groundY
                fallVelocity = 0.0
            }
        }
        // else: well below a found "ground" surface (e.g. under an overhead walkway/deck) -- do
        // nothing, same as before; gravity has no reason to pull the character up through it.
    }

    /** Steps [current] towards [target] by at most [maxDelta], taking the shorter way round. */
    private fun approachAngle(current: Double, target: Double, maxDelta: Double): Double {
        var diff = (target - current) % TAU
        if (diff > PI) diff -= TAU
        if (diff < -PI) diff += TAU
        return current + diff.coerceIn(-maxDelta, maxDelta)
    }

    companion object {
        // PSO reads the stick as a magnitude from 0 (centred) to 127 (full tilt) and splits it
        // into three bands: ignore below 25, walk to 90, run beyond. Expressed here against the
        // normalized 0..1 magnitude VirtualJoystick already produces.
        private const val DEAD_ZONE = 25.0 / 127.0
        private const val RUN_THRESHOLD = 91.0 / 127.0

        /**
         * Run speed divided by walk speed. PSO's own figures are 0.14 and 0.06 units per frame,
         * but those aren't in the same scale as this project's world coordinates -- taken
         * literally a run would cross Pioneer 2's plaza in about two minutes. The ratio between
         * them is the part that matters and is what's kept; [SPEED_FACTOR] still sets the actual
         * run speed, unchanged from the value that already felt right.
         */
        private const val RUN_TO_WALK_RATIO = 0.14 / 0.06

        /** How fast the character can swing its facing while running, in radians per second. */
        private const val RUN_TURN_RATE = 3.2

        private const val TAU = 2 * PI
        /**
         * The character's cylinder radius, as a fraction of its bounding sphere. This is PSO's
         * 1.0-unit player hitbox -- see psoUnit, which anchors every other range on it.
         */
        const val HITBOX_RADIUS_FACTOR = 0.3
        private const val VERTICAL_TOLERANCE_FACTOR = 3.0
        /**
         * Run speed, as a multiple of the character's own bounding-sphere radius per second.
         * Walk follows from it via [RUN_TO_WALK_RATIO], so raising this raises both and keeps
         * PSO's real 0.14-to-0.06 relationship between them intact. This is the one knob for
         * overall movement pace.
         *
         * Raised from 2.4 to fix "treadmilling": the run clip's leg cadence implies a longer
         * stride than 2.4 actually covered, so feet visibly slid. Public because enemy chase
         * speed derives from it (see EnemyAI) -- PSO states chasing as a fraction of the
         * player's run, and that relationship must survive any retune of this knob.
         */
        const val SPEED_FACTOR = 3.4
        private const val FLY_SPEED_FACTOR = 6.0
        private const val ATTACK_SPEED_FACTOR = 0.15

        /** Shared with GameRenderer's own enemy WallCollider so both agree on "short enough to step over". */
        const val MAX_STEP_HEIGHT_FACTOR = 2.0
        private const val GRAVITY_FACTOR = 9.0
    }
}
