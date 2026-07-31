package world.phantasmal.web.mobileGame.debug

import kotlin.math.roundToInt
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.externals.three.Object3D

/**
 * Temporary dev tool: on-screen buttons to nudge the placeholder weapon's position in small steps
 * along each of the *bone's local* axes (before rotation is applied), to fix it not sitting
 * correctly in the hand. Adjust until it looks right, read off the label, then hardcode that
 * offset and remove this.
 */
class WeaponOrientationDebugOverlay(
    container: HTMLElement,
    private val weapon: Object3D,
    bSphereRadius: Double,
) : TrackedDisposable() {
    private val step = bSphereRadius * 0.02

    private val label = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;bottom:16px;left:50%;transform:translateX(-50%);" +
                "background:rgba(0,0,0,0.6);color:white;font:14px sans-serif;padding:4px 10px;" +
                "border-radius:6px;z-index:20;pointer-events:none;white-space:nowrap;"
        container.appendChild(el)
    }

    private val buttons = listOf(
        button("X-", container, "calc(50% - 220px)") { nudge(0, -1) },
        button("X+", container, "calc(50% - 170px)") { nudge(0, 1) },
        button("Y-", container, "calc(50% - 60px)") { nudge(1, -1) },
        button("Y+", container, "calc(50% - 10px)") { nudge(1, 1) },
        button("Z-", container, "calc(50% + 100px)") { nudge(2, -1) },
        button("Z+", container, "calc(50% + 150px)") { nudge(2, 1) },
    )

    init {
        updateLabel()
    }

    override fun dispose() {
        label.remove()
        buttons.forEach { it.remove() }
        super.dispose()
    }

    private fun nudge(axis: Int, direction: Int) {
        val delta = direction * step

        when (axis) {
            0 -> weapon.position.x += delta
            1 -> weapon.position.y += delta
            2 -> weapon.position.z += delta
        }

        updateLabel()
    }

    private fun updateLabel() {
        fun fmt(v: Double) = (v * 100).roundToInt() / 100.0
        label.textContent =
            "weapon position: x=${fmt(weapon.position.x)} y=${fmt(weapon.position.y)} z=${fmt(weapon.position.z)}"
    }

    private fun button(
        text: String,
        container: HTMLElement,
        x: String,
        onClick: () -> Unit,
    ): HTMLElement =
        (document.createElement("button") as HTMLElement).also { el ->
            el.textContent = text
            el.style.cssText =
                "position:fixed;bottom:56px;left:$x;width:44px;height:36px;z-index:20;" +
                    "font:14px sans-serif;"
            el.addEventListener("click", { onClick() })
            container.appendChild(el)
        }
}
