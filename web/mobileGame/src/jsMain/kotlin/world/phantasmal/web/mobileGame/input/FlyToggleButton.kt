package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * DEBUG: on-screen tap button, top-right, toggling [world.phantasmal.web.mobileGame.player.CharacterController.flying]
 * noclip flight -- lets a map be explored freely (through walls, no gravity) to find a good spawn
 * coordinate by eye instead of guessing from screenshots, see [CoordinateDisplay] and
 * [FlightVerticalControls].
 */
class FlyToggleButton(container: HTMLElement, onToggle: () -> Unit) : TrackedDisposable() {
    private val button = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "FLY"
        el.style.cssText = INACTIVE_STYLE
        container.appendChild(el)
    }

    private val listener =
        button.disposableListener<PointerEvent>("pointerdown", { onToggle() })

    fun setActive(active: Boolean) {
        button.style.cssText = if (active) ACTIVE_STYLE else INACTIVE_STYLE
    }

    override fun dispose() {
        listener.dispose()
        button.remove()
        super.dispose()
    }

    companion object {
        private const val BASE_STYLE =
            // Below the radar, which owns the top-right corner.
            "position:fixed;top:calc(144px + var(--pw-safe-top));right:calc(20px + var(--pw-safe-right));width:70px;height:44px;" +
                "border-radius:10px;display:flex;align-items:center;justify-content:center;" +
                "font:bold 15px sans-serif;z-index:15;touch-action:none;color:white;" +
                "user-select:none;"
        private const val INACTIVE_STYLE =
            "$BASE_STYLE background:rgba(255,255,255,0.25);border:2px solid rgba(255,255,255,0.4);"
        private const val ACTIVE_STYLE =
            "$BASE_STYLE background:rgba(80,200,255,0.65);border:2px solid rgba(80,220,255,0.9);"
    }
}
