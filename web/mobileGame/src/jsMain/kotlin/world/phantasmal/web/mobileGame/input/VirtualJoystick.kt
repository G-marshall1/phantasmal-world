package world.phantasmal.web.mobileGame.input

import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * On-screen virtual joystick for the bottom-left quadrant of the screen. Deliberately built
 * directly on Pointer Events rather than the desktop-oriented `webui` widget toolkit -- this
 * needs to coexist with [world.phantasmal.web.mobileGame.camera.ThirdPersonCameraController]'s own
 * pointer handling on the right half of the screen, tracking its own independent pointer id.
 *
 * The base/knob appear wherever the user first touches down ("floating" joystick), rather than at
 * a fixed position, which is friendlier for one-handed landscape phone holds.
 */
class VirtualJoystick(private val container: HTMLElement) : TrackedDisposable() {
    /** Current input, both components in [-1, 1]. Both zero when not being touched. */
    var x: Double = 0.0
        private set
    var y: Double = 0.0
        private set

    private var pointerId: Int? = null
    private var originX: Double = 0.0
    private var originY: Double = 0.0

    private val base = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;width:${BASE_RADIUS * 2}px;height:${BASE_RADIUS * 2}px;" +
                "border-radius:50%;background:rgba(255,255,255,0.15);" +
                "border:2px solid rgba(255,255,255,0.3);display:none;pointer-events:none;" +
                "transform:translate(-50%,-50%);z-index:10;"
        container.appendChild(el)
    }

    private val knob = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;width:${KNOB_RADIUS * 2}px;height:${KNOB_RADIUS * 2}px;" +
                "border-radius:50%;background:rgba(255,255,255,0.4);display:none;" +
                "pointer-events:none;transform:translate(-50%,-50%);z-index:11;"
        container.appendChild(el)
    }

    private val pointerDownListener =
        container.disposableListener<PointerEvent>("pointerdown", ::onPointerDown)
    private val pointerMoveListener =
        container.disposableListener<PointerEvent>("pointermove", ::onPointerMove)
    private val pointerUpListener =
        container.disposableListener<PointerEvent>("pointerup", ::onPointerUp)
    private val pointerCancelListener =
        container.disposableListener<PointerEvent>("pointercancel", ::onPointerUp)

    override fun dispose() {
        pointerDownListener.dispose()
        pointerMoveListener.dispose()
        pointerUpListener.dispose()
        pointerCancelListener.dispose()
        base.remove()
        knob.remove()
        super.dispose()
    }

    private fun onPointerDown(e: PointerEvent) {
        if (pointerId != null || e.clientX >= container.clientWidth / 2) return

        pointerId = e.pointerId
        originX = e.clientX.toDouble()
        originY = e.clientY.toDouble()

        base.style.display = "block"
        base.style.left = "${originX}px"
        base.style.top = "${originY}px"
        knob.style.display = "block"
        knob.style.left = "${originX}px"
        knob.style.top = "${originY}px"
    }

    private fun onPointerMove(e: PointerEvent) {
        if (e.pointerId != pointerId) return

        val dx = e.clientX - originX
        val dy = e.clientY - originY
        val length = sqrt(dx * dx + dy * dy)
        val clampedLength = min(length, BASE_RADIUS)
        val knobX = if (length > 0) originX + dx / length * clampedLength else originX
        val knobY = if (length > 0) originY + dy / length * clampedLength else originY

        knob.style.left = "${knobX}px"
        knob.style.top = "${knobY}px"

        x = (knobX - originX) / BASE_RADIUS
        // Screen Y grows downward; forward (positive y here) should be up/away from the finger.
        y = -(knobY - originY) / BASE_RADIUS
    }

    private fun onPointerUp(e: PointerEvent) {
        if (e.pointerId != pointerId) return

        pointerId = null
        x = 0.0
        y = 0.0
        base.style.display = "none"
        knob.style.display = "none"
    }

    companion object {
        private const val BASE_RADIUS = 55.0
        private const val KNOB_RADIUS = 25.0
    }
}
