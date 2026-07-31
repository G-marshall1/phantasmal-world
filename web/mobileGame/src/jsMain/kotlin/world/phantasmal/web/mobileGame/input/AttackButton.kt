package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/** Simple on-screen tap button, bottom-right, for triggering an attack. */
class AttackButton(container: HTMLElement, onAttack: () -> Unit) : TrackedDisposable() {
    private val button = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "⚔"
        el.style.cssText =
            "position:fixed;bottom:40px;right:40px;width:80px;height:80px;" +
                "border-radius:50%;background:rgba(255,255,255,0.25);" +
                "border:2px solid rgba(255,255,255,0.4);display:flex;align-items:center;" +
                "justify-content:center;font-size:32px;z-index:15;touch-action:none;" +
                "color:white;user-select:none;"
        container.appendChild(el)
    }

    private val listener =
        button.disposableListener<PointerEvent>("pointerdown", { onAttack() })

    override fun dispose() {
        listener.dispose()
        button.remove()
        super.dispose()
    }
}
