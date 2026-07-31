package world.phantasmal.web.mobileGame.debug

import kotlin.math.roundToInt
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.externals.three.Color
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.MeshBasicMaterial
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.externals.three.SphereGeometry
import world.phantasmal.web.externals.three.Vector3

/**
 * Temporary dev tool: on-screen prev/next buttons to cycle through the player skeleton's bones,
 * highlighting the current one with a bright, always-on-top marker sphere (depth test disabled,
 * high render order, so it's visible even if it's technically inside/behind the character mesh),
 * and showing its live world position -- in case the marker itself is hard to spot on a small
 * screen. Remove once the right-hand bone index is known and hardcoded.
 */
class BoneDebugOverlay(
    container: HTMLElement,
    private val mesh: SkinnedMesh,
    markerRadius: Double,
) : TrackedDisposable() {
    private var index = 0
    private val worldPos = Vector3()

    private val marker = Mesh(
        SphereGeometry(markerRadius, 12, 12),
        MeshBasicMaterial().apply {
            color = Color(0xff0000)
            depthTest = false
            depthWrite = false
        },
    ).apply {
        renderOrder = 999
    }

    private val label = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;top:$TOP;left:50%;transform:translateX(-50%);" +
                "background:rgba(0,0,0,0.6);color:white;font:14px sans-serif;padding:4px 10px;" +
                "border-radius:6px;z-index:20;pointer-events:none;white-space:nowrap;"
        container.appendChild(el)
    }

    private val prevButton = button("<", container, "calc(50% - 110px)") { changeIndex(-1) }
    private val nextButton = button(">", container, "calc(50% + 70px)") { changeIndex(1) }

    init {
        attachMarker()
    }

    override fun dispose() {
        marker.parent?.remove(marker)
        label.remove()
        prevButton.remove()
        nextButton.remove()
        super.dispose()
    }

    /** Call every frame so the displayed position reflects the currently-animating bone pose. */
    fun update() {
        marker.getWorldPosition(worldPos)
        label.textContent = "bone $index  (${fmt(worldPos.x)}, ${fmt(worldPos.y)}, ${fmt(worldPos.z)})"
    }

    private fun fmt(v: Double): String = (v.roundToInt()).toString()

    private fun changeIndex(delta: Int) {
        val boneCount = mesh.skeleton.bones.size
        index = (index + delta + boneCount) % boneCount
        attachMarker()
    }

    private fun attachMarker() {
        marker.parent?.remove(marker)
        mesh.skeleton.bones[index].add(marker)
        marker.position.set(.0, .0, .0)
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
                "position:fixed;top:$TOP;left:$x;width:40px;height:40px;z-index:20;" +
                    "font:18px sans-serif;"
            el.addEventListener("click", { onClick() })
            container.appendChild(el)
        }

    companion object {
        private const val TOP = "calc(env(safe-area-inset-top, 0px) + 16px)"
    }
}
