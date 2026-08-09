package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * Opens the in-game menu (see [world.phantasmal.web.mobileGame.menu.GameMenu]).
 *
 * PSO opens its menu with a controller button, which a phone doesn't have, so it needs somewhere
 * to tap. Bottom centre: clear of the joystick on the left and the action palette on the right,
 * reachable with either thumb, and far enough from both that it can't be hit while fighting or
 * moving. Deliberately not near the attack button -- opening the menu mid-swing by accident would
 * be worse than it being a slight reach.
 */
class MenuButton(container: HTMLElement, onOpen: () -> Unit) : TrackedDisposable() {
    private val button = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "MENU"
        el.style.cssText = STYLE
        container.appendChild(el)
    }

    private val listener = button.disposableListener<PointerEvent>("pointerdown", {
        it.stopPropagation()
        onOpen()
    })

    fun setVisible(visible: Boolean) {
        button.style.display = if (visible) "flex" else "none"
    }

    override fun dispose() {
        listener.dispose()
        button.remove()
        super.dispose()
    }

    private companion object {
        const val STYLE =
            "position:fixed;bottom:calc(22px + var(--pw-safe-bottom));left:50%;transform:translateX(-50%);" +
                "width:96px;height:40px;border-radius:20px;display:flex;" +
                "align-items:center;justify-content:center;z-index:20;touch-action:none;" +
                "font:bold 14px 'PWTitleFont', sans-serif;letter-spacing:2px;color:#cfefff;" +
                "user-select:none;background:rgba(6,26,44,0.8);" +
                "border:2px solid rgba(90,210,255,0.6);"
    }
}
