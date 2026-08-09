package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable

/**
 * Bottom-left panel showing the currently focused enemy: its name with its health bar underneath,
 * so how much fight is left in a target is readable at a glance. Sits where the original game
 * puts its own target-info box (see the reference screenshots -- the bottom-right corner belongs
 * to the action palette). Driven once per frame by GameRenderer with the real focus target's
 * name and health -- no "Attribute" line, since no element/attribute data exists anywhere for
 * enemies in this codebase.
 */
class TargetInfoPanel(container: HTMLElement) : TrackedDisposable() {
    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-target-root"
        container.appendChild(el)
    }

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val nameLabel = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-target-name"
        root.appendChild(el)
    }

    private val barTrack = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-target-bar-track"
        root.appendChild(el)
    }

    private val barFill = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-target-bar-fill"
        barTrack.appendChild(el)
    }

    private var currentName: String? = null
    private var currentFraction = -1.0

    fun setTarget(name: String, hp: Int, maxHp: Int) {
        if (currentName != name) {
            currentName = name
            nameLabel.textContent = name
            root.style.display = "block"
        }

        val fraction =
            if (maxHp > 0) (hp.toDouble() / maxHp).coerceIn(0.0, 1.0) else 0.0
        if (fraction != currentFraction) {
            currentFraction = fraction
            barFill.style.width = "${fraction * 100}%"
            // The fill reads green while healthy and burns down to red, like the player's own.
            barFill.style.background = when {
                fraction > 0.5 -> "linear-gradient(180deg, #7dff6a, #2ecb18)"
                fraction > 0.25 -> "linear-gradient(180deg, #ffe066, #e8a812)"
                else -> "linear-gradient(180deg, #ff7a66, #d92c12)"
            }
        }
    }

    fun clear() {
        if (currentName == null) return
        currentName = null
        currentFraction = -1.0
        root.style.display = "none"
    }

    override fun dispose() {
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    companion object {
        private const val STYLESHEET = """
            .pw-hud-target-root {
              position: fixed;
              bottom: calc(24px + var(--pw-safe-bottom));
              left: calc(16px + var(--pw-safe-left));
              z-index: 15;
              display: none;
              min-width: 210px;
              padding: 10px 18px 12px;
              border: 2px solid #6fe4f7;
              border-radius: 10px;
              background:
                repeating-linear-gradient(180deg,
                  rgba(90,200,225,.16) 0px, rgba(90,200,225,.16) 1px,
                  rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px),
                linear-gradient(180deg, #04141f 0%, #020a12 100%);
              box-shadow: 0 0 6px rgba(0,0,0,.75), inset 0 0 10px rgba(0,0,0,.7);
              pointer-events: none;
              user-select: none;
            }
            .pw-hud-target-name {
              font: bold 16px sans-serif;
              color: #e8f6f6;
              text-shadow: 0 1px 2px black;
              letter-spacing: 1px;
              margin-bottom: 7px;
            }
            .pw-hud-target-bar-track {
              height: 10px;
              border-radius: 5px;
              border: 1px solid rgba(140,220,220,.55);
              background: rgba(2,10,16,.9);
              overflow: hidden;
            }
            .pw-hud-target-bar-fill {
              height: 100%;
              width: 100%;
              border-radius: 4px;
              transition: width .15s linear;
            }
        """
    }
}
