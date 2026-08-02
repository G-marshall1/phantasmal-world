package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * DEBUG: hold-to-move "up"/"down" buttons for the vertical axis while flying -- the joystick only
 * drives the horizontal plane (see [FlyToggleButton]), so free flight needs a separate vertical
 * control. Hidden via [setVisible] outside of fly mode, since holding either does nothing on the
 * ground.
 */
class FlightVerticalControls(container: HTMLElement) : TrackedDisposable() {
    var ascending = false
        private set
    var descending = false
        private set

    private val upButton = makeButton(container, "▲", top = 74)
    private val downButton = makeButton(container, "▼", top = 144)

    private val listeners = listOf(
        upButton.disposableListener<PointerEvent>("pointerdown", { ascending = true }),
        upButton.disposableListener<PointerEvent>("pointerup", { ascending = false }),
        upButton.disposableListener<PointerEvent>("pointercancel", { ascending = false }),
        downButton.disposableListener<PointerEvent>("pointerdown", { descending = true }),
        downButton.disposableListener<PointerEvent>("pointerup", { descending = false }),
        downButton.disposableListener<PointerEvent>("pointercancel", { descending = false }),
    )

    fun setVisible(visible: Boolean) {
        val display = if (visible) "flex" else "none"
        upButton.style.display = display
        downButton.style.display = display

        if (!visible) {
            ascending = false
            descending = false
        }
    }

    override fun dispose() {
        listeners.forEach(Disposable::dispose)
        upButton.remove()
        downButton.remove()
        super.dispose()
    }

    private fun makeButton(container: HTMLElement, label: String, top: Int): HTMLElement =
        (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = label
            el.style.cssText =
                "position:fixed;top:${top}px;right:20px;width:70px;height:56px;" +
                    "border-radius:10px;background:rgba(255,255,255,0.25);" +
                    "border:2px solid rgba(255,255,255,0.4);display:none;align-items:center;" +
                    "justify-content:center;font-size:22px;z-index:15;touch-action:none;" +
                    "color:white;user-select:none;"
            container.appendChild(el)
        }
}
