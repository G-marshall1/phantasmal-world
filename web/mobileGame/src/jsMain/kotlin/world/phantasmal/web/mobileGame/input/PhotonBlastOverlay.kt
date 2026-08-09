package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.player.PhotonBlast
import world.phantasmal.webui.dom.disposableListener

/**
 * The armed Photon Blast layer, summoned by tapping the HUD's PB dial (see PlayerStatusPanel)
 * with a full gauge. It sits exactly over the action palette and keeps its four-hex shape: the
 * bottom (thumb) hex becomes the blast -- its icon tile glowing amber, the blast's name on a
 * plate beneath -- while the other three dim, since a Mag carries one blast until Mag evolution
 * exists. Tapping the blast hex fires it; firing (or tapping the dial again) clears the layer
 * and the ordinary attacks show through again.
 */
class PhotonBlastOverlay(container: HTMLElement, onFire: () -> Unit) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-pb-root"
        container.appendChild(el)
    }

    private val blastIcon: HTMLElement
    private val nameLabel: SpriteLabel

    init {
        // One dimming face per inactive hex, at the same measured centres the palette uses.
        for (slot in listOf(ActionHex.LEFT, ActionHex.TOP, ActionHex.RIGHT)) {
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-pb-hex pw-pb-hex-dim"
                el.style.left = "${slot.cx * 100}%"
                el.style.top = "${slot.cy * 100}%"
                root.appendChild(el)
            }
        }

        // The blast hex, on the thumb slot.
        val blastHex = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-pb-hex pw-pb-hex-blast"
            el.style.left = "${ActionHex.BOTTOM.cx * 100}%"
            el.style.top = "${ActionHex.BOTTOM.cy * 100}%"
            root.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-pb-hex-glow"
            blastHex.appendChild(el)
        }
        blastIcon = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-pb-icon"
            blastHex.appendChild(el)
        }

        listeners.add(blastHex.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            onFire()
        }))

        // The blast's name on a small plate hanging under the cluster.
        val namePlate = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-pb-name"
            root.appendChild(el)
        }
        nameLabel = SpriteLabel(namePlate, displaySize = 13)
    }

    var isShowing = false
        private set

    fun show(blast: PhotonBlast) {
        isShowing = true
        blastIcon.style.cssText =
            hudSpriteStyle(HudSprites.hexTile(blast.iconCol, blast.iconRow), ICON_SCALE)
        nameLabel.setText(blast.displayName.uppercase())
        root.style.display = "block"
    }

    fun hide() {
        isShowing = false
        root.style.display = "none"
    }

    override fun dispose() {
        listeners.forEach { it.dispose() }
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    private companion object {
        /** The icon tile drawn inside the hex, sized to sit within its face. */
        const val ICON_SCALE = 0.78

        val STYLESHEET = """
            .pw-pb-root {
              position: fixed;
              bottom: calc(14px + var(--pw-safe-bottom));
              right: calc(0px + var(--pw-safe-right));
              width: ${ActionPalette.CLUSTER_W}px;
              height: ${ActionPalette.CLUSTER_H}px;
              z-index: 18;
              display: none;
              touch-action: none;
              user-select: none;
            }
            .pw-pb-hex {
              position: absolute;
              width: ${ActionPalette.HEX_W}px;
              height: ${ActionPalette.HEX_H}px;
              margin-left: -${ActionPalette.HEX_W / 2}px;
              margin-top: -${ActionPalette.HEX_H / 2}px;
              clip-path: ${ActionPalette.HEX_CLIP};
            }
            .pw-pb-hex-dim {
              background: rgba(2, 8, 14, 0.82);
            }
            .pw-pb-hex-blast {
              touch-action: none;
              cursor: pointer;
              background: rgba(2, 8, 14, 0.9);
            }
            .pw-pb-hex-blast:active { filter: brightness(1.5); }
            .pw-pb-hex-glow {
              position: absolute;
              inset: 0;
              clip-path: ${ActionPalette.HEX_CLIP};
              box-shadow: inset 0 0 16px rgba(255,182,61,.95);
              animation: pw-pb-glow 0.55s ease-in-out infinite alternate;
              pointer-events: none;
            }
            @keyframes pw-pb-glow {
              from { opacity: 0.5; }
              to   { opacity: 1.0; }
            }
            .pw-pb-icon {
              position: absolute;
              left: 50%;
              top: 50%;
              transform: translate(-50%, -50%);
              pointer-events: none;
            }
            .pw-pb-name {
              position: absolute;
              left: 50%;
              bottom: -20px;
              transform: translateX(-50%);
              padding: 2px 10px;
              border: 1px solid #ffb63d;
              border-radius: 9px;
              background: rgba(4, 16, 26, 0.92);
              filter: brightness(1.5) drop-shadow(0 1px 2px black);
              white-space: nowrap;
            }
        """
    }
}
