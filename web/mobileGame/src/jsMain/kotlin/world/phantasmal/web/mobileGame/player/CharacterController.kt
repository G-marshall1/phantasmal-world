package world.phantasmal.web.mobileGame.player

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

    private val wallCollider = WallCollider(gameMap.collisionGeometry)
    private val walkable = gameMap.walkableCollisionObject
    private val radius = bSphereRadius * RADIUS_FACTOR
    private val verticalTolerance = bSphereRadius * VERTICAL_TOLERANCE_FACTOR
    private val speed = bSphereRadius * SPEED_FACTOR

    /**
     * [joystickX]/[joystickY] are the raw joystick input in [-1, 1]; [cameraYaw] is the camera's
     * current effective facing, used to rotate the joystick's screen-relative input into
     * world-relative movement (joystick "up" = away from the camera). While [isAttacking], the
     * character commits to its swing: facing stays locked and movement is cut to a small fraction
     * of normal speed (a slight drift, not a full stop) rather than continuing at full speed.
     */
    fun update(
        deltaTime: Double,
        joystickX: Double,
        joystickY: Double,
        cameraYaw: Double,
        isAttacking: Boolean,
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
            val effectiveSpeed = if (isAttacking) speed * ATTACK_SPEED_FACTOR else speed
            position.x += worldDirX / inputLength * throttle * effectiveSpeed * deltaTime
            position.z += worldDirZ / inputLength * throttle * effectiveSpeed * deltaTime

            if (!isAttacking) {
                yaw = atan2(worldDirX, worldDirZ)
            }
        }

        wallCollider.resolve(position, radius, verticalTolerance)

        findGroundHeight(walkable, position.x, position.z)?.let { groundY ->
            position.y = groundY
        }
    }

    companion object {
        private const val DEAD_ZONE = 0.15
        private const val RADIUS_FACTOR = 0.3
        private const val VERTICAL_TOLERANCE_FACTOR = 3.0
        private const val SPEED_FACTOR = 1.5
        private const val ATTACK_SPEED_FACTOR = 0.15
    }
}
