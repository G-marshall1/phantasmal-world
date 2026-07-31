package world.phantasmal.web.mobileGame.camera

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.core.rendering.InputManager
import world.phantasmal.web.externals.three.PerspectiveCamera
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.webui.dom.disposableListener

/**
 * Third-person follow camera. Deliberately not built on [world.phantasmal.web.core.rendering.OrbitalCameraInputManager]
 * -- that class wraps three.js OrbitControls, which claims exclusive ownership of all pointer
 * events on the canvas to orbit a *static* target. That's incompatible with reserving screen
 * regions for the movement joystick (Phase 2) and with a camera that follows a *moving* target.
 * This tracks a single pointer on the right half of the screen for an optional one-finger yaw
 * drag, leaving the rest of the canvas free.
 */
class ThirdPersonCameraController(
    private val canvas: HTMLCanvasElement,
    private val camera: PerspectiveCamera,
) : TrackedDisposable(), InputManager {
    /** Updated every frame by the game loop from the player's current position/facing. */
    var targetPosition: Vector3 = Vector3()
    var targetYaw: Double = 0.0

    /** [targetYaw] plus the user's manual one-finger drag offset -- the camera's actual facing. */
    val effectiveYaw: Double get() = targetYaw + userYawOffset

    // PSO's Ninja model coordinate units aren't meters -- there's no fixed "human scale" to
    // assume. These start at a placeholder and get set for real via [setScale] once the player
    // mesh is loaded and its actual size (bounding sphere radius) is known, mirroring how the
    // Viewer's MeshRenderer sizes its camera off the loaded model's bounding sphere rather than a
    // hardcoded distance.
    private var distance = 1.0
    private var height = 1.0
    private var eyeHeight = 1.0
    private var shoulderOffset = 1.0

    private var userYawOffset: Double = 0.0
    private var dragPointerId: Int? = null
    private var lastDragX: Int = 0

    private val desiredPos = Vector3()
    private val lookAtPos = Vector3()

    private val pointerDownListener =
        canvas.disposableListener<PointerEvent>("pointerdown", ::onPointerDown)
    private val pointerMoveListener =
        canvas.disposableListener<PointerEvent>("pointermove", ::onPointerMove)
    private val pointerUpListener =
        canvas.disposableListener<PointerEvent>("pointerup", ::onPointerUp)
    private val pointerCancelListener =
        canvas.disposableListener<PointerEvent>("pointercancel", ::onPointerUp)

    override fun dispose() {
        pointerDownListener.dispose()
        pointerMoveListener.dispose()
        pointerUpListener.dispose()
        pointerCancelListener.dispose()
        super.dispose()
    }

    override fun setSize(width: Int, height: Int) {
        if (width == 0 || height == 0) return

        camera.aspect = width.toDouble() / height
        camera.updateProjectionMatrix()
    }

    override fun resetCamera() {
        userYawOffset = 0.0
    }

    override fun beforeRender() {
        // No-op: the camera is advanced explicitly via [update] with a delta time, called from
        // GameRenderer's render loop alongside the player/world update.
    }

    /**
     * Sets the camera's follow distance/height relative to the player's actual size, given the
     * player mesh's bounding sphere [radius]. Same idea as [world.phantasmal.web.viewer.rendering.MeshRenderer]'s
     * `CAMERA_POS * (bSphere.radius * cameraDistFactor)`.
     */
    fun setScale(radius: Double) {
        distance = radius * DISTANCE_FACTOR
        height = radius * HEIGHT_FACTOR
        eyeHeight = radius * EYE_HEIGHT_FACTOR
        shoulderOffset = radius * SHOULDER_OFFSET_FACTOR
    }

    /** Smoothly moves the camera towards [targetPosition]/[targetYaw] this frame. */
    fun update(deltaTime: Double) {
        val yaw = effectiveYaw
        val sinYaw = sin(yaw)
        val cosYaw = cos(yaw)

        // Offset the camera sideways along the character's right vector so it sits over the
        // right shoulder (Fortnite-style) instead of dead-center behind the character. Looking at
        // the character from this offset position -- rather than also shifting the look-at point
        // -- is what pushes the character to the left side of the frame.
        val rightX = -cosYaw
        val rightZ = sinYaw

        desiredPos.set(
            targetPosition.x + sinYaw * -distance + rightX * shoulderOffset,
            targetPosition.y + height,
            targetPosition.z + cosYaw * -distance + rightZ * shoulderOffset,
        )

        val smoothing = 1.0 - exp(-DAMPING * deltaTime)
        camera.position.lerp(desiredPos, smoothing)

        lookAtPos.set(targetPosition.x, targetPosition.y + eyeHeight, targetPosition.z)
        camera.lookAt(lookAtPos)

        // Scale the clip planes to the same unknown world scale, exactly like
        // OrbitalCameraInputManager.beforeRender() does for the Viewer/Quest Editor.
        camera.near = maxOf(.01, distance / 100)
        camera.far = maxOf(2_000.0, 10 * distance)
        camera.updateProjectionMatrix()
    }

    private fun onPointerDown(e: PointerEvent) {
        if (dragPointerId == null && e.clientX > canvas.clientWidth / 2) {
            dragPointerId = e.pointerId
            lastDragX = e.clientX
        }
    }

    private fun onPointerMove(e: PointerEvent) {
        if (e.pointerId == dragPointerId) {
            val deltaX = e.clientX - lastDragX
            lastDragX = e.clientX
            userYawOffset -= deltaX * YAW_SPEED
        }
    }

    private fun onPointerUp(e: PointerEvent) {
        if (e.pointerId == dragPointerId) {
            dragPointerId = null
        }
    }

    companion object {
        private const val DISTANCE_FACTOR = 4.5
        private const val HEIGHT_FACTOR = 2.2
        private const val EYE_HEIGHT_FACTOR = 0.8
        private const val SHOULDER_OFFSET_FACTOR = 1.2
        private const val DAMPING = 8.0
        private const val YAW_SPEED = 0.005
    }
}
