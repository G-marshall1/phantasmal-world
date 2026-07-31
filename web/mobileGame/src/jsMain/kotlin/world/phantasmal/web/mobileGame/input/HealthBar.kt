package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable

/** Simple on-screen health bar, top-left, plus a "You Died" overlay for when it hits zero. */
class HealthBar(container: HTMLElement) : TrackedDisposable() {
    private val frame = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;top:20px;left:20px;width:200px;height:24px;" +
                "border-radius:6px;background:rgba(0,0,0,0.4);" +
                "border:2px solid rgba(255,255,255,0.4);z-index:15;overflow:hidden;"
        container.appendChild(el)
    }

    private val fill = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "height:100%;width:100%;background:linear-gradient(90deg,#e53935,#ef5350);" +
                "transition:width 0.15s ease-out;"
        frame.appendChild(el)
    }

    private val label = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;top:22px;left:30px;width:180px;height:20px;line-height:20px;" +
                "font:bold 13px sans-serif;color:white;text-shadow:0 1px 2px black;z-index:16;" +
                "pointer-events:none;user-select:none;"
        container.appendChild(el)
    }

    private val deathOverlay = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "YOU DIED"
        el.style.cssText =
            "position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);" +
                "font:bold 48px sans-serif;color:#e53935;text-shadow:0 2px 6px black;" +
                "z-index:20;display:none;pointer-events:none;user-select:none;"
        container.appendChild(el)
    }

    fun setHealth(current: Int, max: Int) {
        val pct = (current.toDouble() / max * 100).coerceIn(0.0, 100.0)
        fill.style.width = "$pct%"
        label.textContent = "$current / $max"
        deathOverlay.style.display = if (current <= 0) "block" else "none"
    }

    override fun dispose() {
        frame.remove()
        label.remove()
        deathOverlay.remove()
        super.dispose()
    }
}
