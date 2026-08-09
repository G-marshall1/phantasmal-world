package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * Fake local "login" -- no backend exists. Just a nickname and a Start button, matching the
 * shape of a real login screen without pretending to authenticate against anything.
 */
class LoginScreen(
    container: HTMLElement,
    initialNickname: String,
    private val onSubmit: (String) -> Unit,
) : TrackedDisposable() {
    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;inset:0;background:#181818;display:flex;flex-direction:column;" +
            "padding:var(--pw-safe-top) var(--pw-safe-right) var(--pw-safe-bottom) var(--pw-safe-left);" +
            "box-sizing:border-box;" +
                "align-items:center;justify-content:center;z-index:30;padding:0 24px;"
        container.appendChild(el)
    }

    private val label = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "ENTER NICKNAME"
        el.style.cssText =
            "font:bold 15px sans-serif;color:white;letter-spacing:1px;margin-bottom:14px;" +
                "text-shadow:0 1px 2px black;"
        root.appendChild(el)
    }

    private val input = (document.createElement("input") as HTMLInputElement).also { el ->
        el.type = "text"
        el.value = initialNickname
        el.maxLength = 16
        el.placeholder = "Nickname"
        el.style.cssText =
            "width:min(280px,80vw);padding:12px 14px;font:16px sans-serif;text-align:center;" +
                "border-radius:8px;border:2px solid rgba(255,255,255,0.4);" +
                "background:rgba(255,255,255,0.08);color:white;outline:none;"
        root.appendChild(el)
    }

    private val startButton = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "Start"
        el.style.cssText =
            "margin-top:20px;padding:12px 40px;border-radius:24px;" +
                "background:#ff8a3d;color:#181818;font:bold 16px sans-serif;" +
                "touch-action:none;user-select:none;"
        root.appendChild(el)
    }

    private fun submit() {
        val nickname = input.value.trim()
        if (nickname.isNotEmpty()) {
            onSubmit(nickname)
        }
    }

    private val startListener = startButton.disposableListener<PointerEvent>("pointerdown", { submit() })
    private val enterListener = input.disposableListener<KeyboardEvent>("keydown", { e ->
        if (e.key == "Enter") submit()
    })

    override fun dispose() {
        startListener.dispose()
        enterListener.dispose()
        root.remove()
        super.dispose()
    }
}
