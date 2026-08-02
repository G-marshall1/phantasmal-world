package world.phantasmal.web.mobileGame.player

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
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
) {
    var yaw: Double = 0.0
        private set

    var isMoving: Boolean = false
        private set

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

    private val maxStepHeight = bSphereRadius * MAX_STEP_HEIGHT_FACTOR
    private val wallCollider = WallCollider(gameMap.collisionGeometry, minHeight = maxStepHeight)
    private val walkable = gameMap.walkableCollisionObject
    private val radius = bSphereRadius * RADIUS_FACTOR
    private val verticalTolerance = bSphereRadius * VERTICAL_TOLERANCE_FACTOR
    private val speed = bSphereRadius * SPEED_FACTOR
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

        if (isMoving) {
            val sinYaw = sin(cameraYaw)
            val cosYaw = cos(cameraYaw)

            // Rotate joystick space (x = right, y = forward) into world XZ space, using the same
            // forward/right basis as ThirdPersonCameraController.
            val worldDirX = joystickY * sinYaw - joystickX * cosYaw
            val worldDirZ = joystickY * cosYaw + joystickX * sinYaw

            // Analog throttle: speed scales with how far the stick is pushed (up to inputLength
            // 1.0, already normalized by VirtualJoystick), not an instant full-speed snap the
            // moment it clears the dead zone.
            val throttle = min(inputLength, 1.0)
            val effectiveSpeed = when {
                flying -> flySpeed
                isAttacking -> speed * ATTACK_SPEED_FACTOR
                else -> speed
            }
            position.x += worldDirX / inputLength * throttle * effectiveSpeed * deltaTime
            position.z += worldDirZ / inputLength * throttle * effectiveSpeed * deltaTime

            if (!isAttacking) {
                yaw = atan2(worldDirX, worldDirZ)
            }
        }

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

    companion object {
        private const val DEAD_ZONE = 0.15
        private const val RADIUS_FACTOR = 0.3
        private const val VERTICAL_TOLERANCE_FACTOR = 3.0
        private const val SPEED_FACTOR = 1.5
        private const val FLY_SPEED_FACTOR = 6.0
        private const val ATTACK_SPEED_FACTOR = 0.15

        /** Shared with GameRenderer's own enemy WallCollider so both agree on "short enough to step over". */
        const val MAX_STEP_HEIGHT_FACTOR = 2.0
        private const val GRAVITY_FACTOR = 9.0
    }
}
