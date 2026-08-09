package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.externals.three.Camera
import world.phantasmal.web.externals.three.Vector3

/**
 * Damage figures that pop above whatever was hit and drift upward as they fade.
 *
 * Set in PSO's own HUD font (see [SpriteLabel]) rather than a web typeface, so they read as part
 * of the game. Criticals are tinted and slightly larger, and a swing that fails its accuracy roll
 * shows "MISS" instead of a number -- otherwise a missed heavy attack is indistinguishable from
 * one that hit for very little.
 *
 * Positions are world-space and re-projected every frame, so a number stays over its target as
 * the camera moves rather than being pinned where the screen happened to be at the moment of the
 * hit.
 */
class DamageNumbers(private val container: HTMLElement) : TrackedDisposable() {
    private class Floater(
        val label: SpriteLabel,
        val worldX: Double,
        val worldY: Double,
        val worldZ: Double,
        var age: Double = 0.0,
    )

    private val floaters = mutableListOf<Floater>()
    private val projected = Vector3()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    /** A number over a target that was hit. [y] should be the top of its body, not its feet. */
    fun showDamage(x: Double, y: Double, z: Double, damage: Int, critical: Boolean) {
        add(x, y, z, damage.toString(), if (critical) "pw-dmg-crit" else "pw-dmg-hit")
    }

    /** A swing that failed its accuracy roll against this target. */
    fun showMiss(x: Double, y: Double, z: Double) {
        add(x, y, z, "MISS", "pw-dmg-miss")
    }

    private fun add(x: Double, y: Double, z: Double, text: String, cssClass: String) {
        // Cheap guard against a wide sweep spawning more labels than can be read anyway.
        if (floaters.size >= MAX_FLOATERS) {
            floaters.removeAt(0).label.el.remove()
        }

        val label = SpriteLabel(container, if (cssClass == "pw-dmg-crit") CRIT_SIZE else SIZE)
        label.el.className = "pw-dmg $cssClass"
        label.setText(text)
        floaters.add(Floater(label, x, y, z))
    }

    /**
     * Re-projects every live number and ages it out. [width]/[height] are the canvas size in CSS
     * pixels, which is what the projection has to be scaled into.
     */
    fun update(deltaTime: Double, camera: Camera, width: Double, height: Double) {
        val finished = mutableListOf<Floater>()

        for (floater in floaters) {
            floater.age += deltaTime

            if (floater.age >= LIFETIME) {
                finished.add(floater)
                continue
            }

            val progress = floater.age / LIFETIME

            projected.set(floater.worldX, floater.worldY + RISE * progress, floater.worldZ)
            projected.project(camera)

            // Behind the camera: three.js still returns a point, mirrored, so it has to be
            // rejected explicitly or numbers appear over the wrong shoulder.
            if (projected.z > 1.0) {
                floater.label.el.style.display = "none"
                continue
            }

            floater.label.el.style.display = "flex"
            floater.label.el.style.left = "${(projected.x * 0.5 + 0.5) * width}px"
            floater.label.el.style.top = "${(-projected.y * 0.5 + 0.5) * height}px"
            // Hold full opacity for the first part of the life, then fade.
            floater.label.el.style.opacity =
                ((1.0 - progress) / (1.0 - FADE_START)).coerceAtMost(1.0).toString()
        }

        for (floater in finished) {
            floater.label.el.remove()
            floaters.remove(floater)
        }
    }

    override fun dispose() {
        floaters.forEach { it.label.el.remove() }
        floaters.clear()
        styleTag.remove()
        super.dispose()
    }

    private companion object {
        const val SIZE = 28
        const val CRIT_SIZE = 38

        /** Seconds a number stays on screen. */
        const val LIFETIME = 0.9

        /** Fraction of the lifetime spent at full opacity before fading. */
        const val FADE_START = 0.35

        /** How far a number drifts upward over its life, in world units. */
        const val RISE = 18.0

        const val MAX_FLOATERS = 24

        val STYLESHEET = """
            .pw-dmg {
              position: fixed;
              transform: translate(-50%, -50%);
              pointer-events: none;
              z-index: 14;
              white-space: nowrap;
              filter: drop-shadow(0 2px 2px rgba(0,0,0,.9));
            }
            /* The HUD font's glyphs are white, so a tint is all that separates the three kinds. */
            .pw-dmg-hit { filter: drop-shadow(0 2px 2px rgba(0,0,0,.9)); }
            .pw-dmg-crit {
              filter: drop-shadow(0 0 4px #ffb43c) drop-shadow(0 2px 2px rgba(0,0,0,.9))
                      saturate(0) sepia(1) saturate(6) hue-rotate(-18deg);
            }
            .pw-dmg-miss { opacity: 0.75; filter: grayscale(1) brightness(0.85); }
        """
    }
}
