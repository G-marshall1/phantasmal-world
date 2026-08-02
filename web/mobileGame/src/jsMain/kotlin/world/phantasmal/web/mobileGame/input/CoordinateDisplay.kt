package world.phantasmal.web.mobileGame.input

import kotlin.math.round
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable

/**
 * DEBUG: live x/y/z readout, top-center -- lets a coordinate found by eye (via [FlyToggleButton]'s
 * noclip flight) be read straight off the screen and reported back, instead of guessing spawn
 * points from screenshots alone.
 */
class CoordinateDisplay(container: HTMLElement) : TrackedDisposable() {
    private val label = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;top:20px;left:50%;transform:translateX(-50%);" +
                "font:bold 14px monospace;color:white;text-shadow:0 1px 3px black;" +
                "z-index:15;pointer-events:none;user-select:none;white-space:nowrap;" +
                "background:rgba(0,0,0,0.35);padding:4px 10px;border-radius:6px;"
        container.appendChild(el)
    }

    fun update(x: Double, y: Double, z: Double) {
        label.textContent = "x=${format(x)}  y=${format(y)}  z=${format(z)}"
    }

    private fun format(v: Double): String = (round(v * 100) / 100).toString()

    override fun dispose() {
        label.remove()
        super.dispose()
    }
}
