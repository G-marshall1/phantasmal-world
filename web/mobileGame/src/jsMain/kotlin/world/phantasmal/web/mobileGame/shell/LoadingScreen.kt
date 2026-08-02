package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable

/** Full-screen splash shown while transitioning between shell screens. */
class LoadingScreen(container: HTMLElement, message: String) : TrackedDisposable() {
    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;inset:0;background:#181818;display:flex;flex-direction:column;" +
                "align-items:center;justify-content:center;z-index:30;"
        container.appendChild(el)
    }

    private val spinner = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "width:48px;height:48px;border-radius:50%;" +
                "border:4px solid rgba(255,255,255,0.15);border-top-color:#ff8a3d;" +
                "animation:phantasmal-spin 0.9s linear infinite;"
        root.appendChild(el)
    }

    private val label = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = message
        el.style.cssText =
            "margin-top:18px;font:bold 16px sans-serif;color:white;" +
                "text-shadow:0 1px 2px black;user-select:none;"
        root.appendChild(el)
    }

    private val keyframes = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent =
            "@keyframes phantasmal-spin { to { transform: rotate(360deg); } }"
        document.head!!.appendChild(el)
    }

    fun setMessage(text: String) {
        label.textContent = text
    }

    override fun dispose() {
        root.remove()
        keyframes.remove()
        super.dispose()
    }
}
