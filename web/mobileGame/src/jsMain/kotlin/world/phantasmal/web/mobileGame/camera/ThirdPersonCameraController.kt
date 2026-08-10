package world.phantasmal.web.mobileGame.camera

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.core.rendering.InputManager
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.PerspectiveCamera
import world.phantasmal.web.externals.three.Raycaster
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.webui.dom.disposableListener

/**
 * Third-person follow camera, framed like the original game's: centred behind the character
 * (no shoulder offset), pivoting on the head, from a mildly elevated default angle. Deliberately
 * not built on [world.phantasmal.web.core.rendering.OrbitalCameraInputManager] -- that class
 * wraps three.js OrbitControls, which claims exclusive ownership of all pointer events on the
 * canvas to orbit a *static* target; incompatible with the movement joystick's reserved screen
 * region and a moving target.
 *
 * Controls, all on the right half of the screen:
 *  - one-finger drag: orbit -- horizontal turns the camera, vertical tilts it (drag up looks
 *    up, swinging the camera down, and vice versa)
 *  - two-finger pinch: zoom, clamped so the camera can neither enter the character nor pull
 *    back into a map-wide view
 *  - mouse wheel (desktop/rig): same zoom
 *
 * The camera also never passes through the map: each frame a ray from the pivot toward the
 * desired position is clamped to the nearest collision hit (see [collider]), pulling the camera
 * in front of walls and doorframes instead of letting the view leave the room -- walking against
 * a wall used to put the camera inside it, hiding the character entirely.
 */
class ThirdPersonCameraController(
    private val canvas: HTMLCanvasElement,
    private val camera: PerspectiveCamera,
) : TrackedDisposable(), InputManager {
    /** Updated every frame by the game loop from the player's current position/facing. */
    var targetPosition: Vector3 = Vector3()
    var targetYaw: Double = 0.0

    /** [targetYaw] plus the user's manual drag offset -- the camera's actual facing. */
    val effectiveYaw: Double get() = targetYaw + userYawOffset

    /**
     * What the camera collides with: the map's full collision geometry (every triangle, walls
     * included -- not just the walkable subset). Set by GameRenderer at map load; null (during
     * loading) simply skips the clamp.
     */
    var collider: Object3D? = null

    // PSO's Ninja model coordinate units aren't meters -- there's no fixed "human scale" to
    // assume. These start at a placeholder and get set for real via [setScale] once the player
    // mesh is loaded and its actual size (bounding sphere radius) is known.
    private var baseDistance = 1.0
    private var eyeHeight = 1.0
    private var collisionMargin = 0.1
    private var minDistance = 0.5

    private var userYawOffset = 0.0

    /** DEBUG (rig): points the camera at an absolute yaw, as the drag would. */
    fun debugSetYawOffset(offset: Double) {
        userYawOffset = offset
    }

    /** Camera elevation above the horizontal, in radians. Dragging vertically moves it. */
    private var pitch = DEFAULT_PITCH

    /** Distance multiplier the pinch/wheel zoom drives. 1 is the authored framing. */
    private var zoom = 1.0

    /** Right-half pointers being tracked: id -> last seen (x, y). Two of them is a pinch. */
    private val activePointers = LinkedHashMap<Int, Pair<Int, Int>>()
    private var lastPinchSpan: Double? = null

    private val desiredPos = Vector3()
    private val pivot = Vector3()
    private val rayDirection = Vector3()
    private val raycaster = Raycaster()

    private val pointerDownListener =
        canvas.disposableListener<PointerEvent>("pointerdown", ::onPointerDown)
    private val pointerMoveListener =
        canvas.disposableListener<PointerEvent>("pointermove", ::onPointerMove)
    private val pointerUpListener =
        canvas.disposableListener<PointerEvent>("pointerup", ::onPointerUp)
    private val pointerCancelListener =
        canvas.disposableListener<PointerEvent>("pointercancel", ::onPointerUp)
    private val wheelListener =
        canvas.disposableListener<WheelEvent>("wheel", ::onWheel)

    override fun dispose() {
        pointerDownListener.dispose()
        pointerMoveListener.dispose()
        pointerUpListener.dispose()
        pointerCancelListener.dispose()
        wheelListener.dispose()
        super.dispose()
    }

    override fun setSize(width: Int, height: Int) {
        if (width == 0 || height == 0) return

        camera.aspect = width.toDouble() / height
        camera.updateProjectionMatrix()
    }

    override fun resetCamera() {
        userYawOffset = 0.0
        pitch = DEFAULT_PITCH
        zoom = 1.0
    }

    override fun beforeRender() {
        // No-op: the camera is advanced explicitly via [update] with a delta time, called from
        // GameRenderer's render loop alongside the player/world update.
    }

    /**
     * Sets the camera's follow distances relative to the player's actual size, given the player
     * mesh's bounding sphere [radius]. Same idea as the Viewer's MeshRenderer sizing its camera
     * off the loaded model's bounding sphere rather than a hardcoded distance.
     */
    fun setScale(radius: Double) {
        baseDistance = radius * DISTANCE_FACTOR
        eyeHeight = radius * EYE_HEIGHT_FACTOR
        collisionMargin = radius * COLLISION_MARGIN_FACTOR
        minDistance = radius * MIN_DISTANCE_FACTOR
    }

    /** Smoothly moves the camera towards its orbit position this frame. */
    fun update(deltaTime: Double) {
        val yaw = effectiveYaw
        pivot.set(targetPosition.x, targetPosition.y + eyeHeight, targetPosition.z)

        // Looking up: the orbit is allowed below the horizon, and instead of grinding into
        // the ground the camera slides in toward the character as the pitch dips -- the
        // usual action-game trick for letting the player tilt up at the sky (or a flying
        // boss) from a low angle.
        val lowPitchShrink =
            if (pitch >= PITCH_SHRINK_START) 1.0
            else {
                val t = (PITCH_SHRINK_START - pitch) / (PITCH_SHRINK_START - PITCH_MIN)
                1.0 - t * (1.0 - LOW_PITCH_DISTANCE_SCALE)
            }

        val distance = baseDistance * zoom * lowPitchShrink
        val horizontal = cos(pitch) * distance
        desiredPos.set(
            pivot.x + sin(yaw) * -horizontal,
            pivot.y + sin(pitch) * distance,
            pivot.z + cos(yaw) * -horizontal,
        )

        // The wall clamp: cut the follow distance to the nearest collision hit between the
        // character and the camera, so the camera stays inside the room the character is in.
        val allowed = allowedDistanceToward(desiredPos.x, desiredPos.y, desiredPos.z, distance)
        if (allowed < distance) {
            val scale = allowed / distance
            desiredPos.set(
                pivot.x + (desiredPos.x - pivot.x) * scale,
                pivot.y + (desiredPos.y - pivot.y) * scale,
                pivot.z + (desiredPos.z - pivot.z) * scale,
            )
        }

        val smoothing = 1.0 - exp(-DAMPING * deltaTime)
        camera.position.lerp(desiredPos, smoothing)

        // The lerp trails the clamped target, so a fresh clamp can still leave this frame's
        // actual position behind a wall for the several frames the lerp would take to catch
        // up -- exactly the "camera in the wall" moment. Snap the overshoot in immediately;
        // relaxing back out when the wall clears stays smooth via the ordinary lerp above.
        val currentDistance = distanceFromPivot()
        val allowedNow = allowedDistanceToward(
            camera.position.x, camera.position.y, camera.position.z, currentDistance,
        )
        if (allowedNow < currentDistance) {
            val scale = allowedNow / currentDistance
            camera.position.set(
                pivot.x + (camera.position.x - pivot.x) * scale,
                pivot.y + (camera.position.y - pivot.y) * scale,
                pivot.z + (camera.position.z - pivot.z) * scale,
            )
        }

        camera.lookAt(pivot)

        // Scale the clip planes to the same unknown world scale, exactly like
        // OrbitalCameraInputManager.beforeRender() does for the Viewer/Quest Editor.
        camera.near = maxOf(.01, baseDistance / 100)
        camera.far = maxOf(2_000.0, 10 * baseDistance)
        camera.updateProjectionMatrix()
    }

    /**
     * How far from the pivot the camera may sit along the ray toward ([endX],[endY],[endZ]):
     * [wanted], or just in front of the nearest wall, never less than [minDistance].
     */
    private fun allowedDistanceToward(endX: Double, endY: Double, endZ: Double, wanted: Double): Double {
        val map = collider ?: return wanted
        val dx = endX - pivot.x
        val dy = endY - pivot.y
        val dz = endZ - pivot.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1e-6) return wanted
        rayDirection.set(dx / length, dy / length, dz / length)

        raycaster.set(pivot, rayDirection)
        val hit = raycaster.intersectObject(map, recursive = true)
            .minByOrNull { it.distance } ?: return wanted
        if (hit.distance >= wanted + collisionMargin) return wanted
        return maxOf(minDistance, hit.distance - collisionMargin)
    }

    private fun distanceFromPivot(): Double {
        val dx = camera.position.x - pivot.x
        val dy = camera.position.y - pivot.y
        val dz = camera.position.z - pivot.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun onPointerDown(e: PointerEvent) {
        if (activePointers.size < 2 && e.clientX > canvas.clientWidth / 2) {
            activePointers[e.pointerId] = e.clientX to e.clientY
            lastPinchSpan = if (activePointers.size == 2) pinchSpan() else null
        }
    }

    private fun onPointerMove(e: PointerEvent) {
        val last = activePointers[e.pointerId] ?: return

        if (activePointers.size == 2) {
            // Pinch: the pair's spread drives zoom; individual finger motion is not an orbit.
            activePointers[e.pointerId] = e.clientX to e.clientY
            val span = pinchSpan()
            lastPinchSpan?.let { previous ->
                if (previous > 1.0 && span > 1.0) {
                    zoom = (zoom * previous / span).coerceIn(ZOOM_MIN, ZOOM_MAX)
                }
            }
            lastPinchSpan = span
            return
        }

        val deltaX = e.clientX - last.first
        val deltaY = e.clientY - last.second
        activePointers[e.pointerId] = e.clientX to e.clientY

        userYawOffset -= deltaX * YAW_SPEED
        // Drag up looks up (the camera swings down), drag down looks down (it rises) -- the
        // ordinary non-inverted touch-look convention.
        pitch = (pitch + deltaY * PITCH_SPEED).coerceIn(PITCH_MIN, PITCH_MAX)
    }

    private fun onPointerUp(e: PointerEvent) {
        activePointers.remove(e.pointerId)
        lastPinchSpan = null
    }

    private fun onWheel(e: WheelEvent) {
        zoom = (zoom * (1.0 + e.deltaY * WHEEL_ZOOM_SPEED)).coerceIn(ZOOM_MIN, ZOOM_MAX)
    }

    private fun pinchSpan(): Double {
        val points = activePointers.values.toList()
        if (points.size < 2) return 0.0
        val dx = (points[0].first - points[1].first).toDouble()
        val dy = (points[0].second - points[1].second).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val DISTANCE_FACTOR = 5.0
        private const val EYE_HEIGHT_FACTOR = 1.0
        private const val COLLISION_MARGIN_FACTOR = 0.35
        private const val MIN_DISTANCE_FACTOR = 1.2

        /**
         * The resting elevation, ~24 degrees -- the original game's view: high enough to read
         * the room ahead over the character's head, not so high it reads top-down. The old rig
         * sat visibly lower (~17 degrees off a shoulder offset) and framed mostly ground.
         */
        private const val DEFAULT_PITCH = 0.42
        /** Below the horizon: the low-pitch dolly-in above keeps this off the ground. */
        private const val PITCH_MIN = -0.5
        private const val PITCH_MAX = 1.25

        /** Where the look-up dolly-in starts, and how close it pulls at full tilt. */
        private const val PITCH_SHRINK_START = 0.2
        private const val LOW_PITCH_DISTANCE_SCALE = 0.35

        private const val ZOOM_MIN = 0.5
        private const val ZOOM_MAX = 1.7

        private const val DAMPING = 8.0
        private const val YAW_SPEED = 0.005
        private const val PITCH_SPEED = 0.004
        private const val WHEEL_ZOOM_SPEED = 0.001
    }
}
