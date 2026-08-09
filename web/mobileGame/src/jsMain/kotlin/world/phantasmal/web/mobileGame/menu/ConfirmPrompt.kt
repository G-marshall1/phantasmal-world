package world.phantasmal.web.mobileGame.menu

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.input.HudSprites
import world.phantasmal.web.mobileGame.input.disposableTap
import world.phantasmal.web.mobileGame.input.hudSpriteStyleXY

/**
 * The little "are you sure" box PSO puts up before it spends something: the item's name, the
 * action above, and CANCEL below.
 *
 * It exists because using an item is irreversible and the lists it's tapped from are also the
 * lists players scroll -- a mis-tap that drinks a Trimate is a real loss. The confirm button is
 * the HUD sheet's own green capsule, 3-sliced like the status bars so it keeps its rounded caps.
 */
class ConfirmPrompt(container: HTMLElement) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-confirm-root"
        el.style.display = "none"
        container.appendChild(el)
    }

    private val title: HTMLElement
    private val confirmLabel: HTMLElement
    private var onConfirm: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    var isOpen: Boolean = false
        private set

    init {
        val backdrop = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-confirm-backdrop"
            root.appendChild(el)
        }
        listeners.add(backdrop.disposableTap { cancel() })

        val panel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-confirm-panel"
            root.appendChild(el)
        }

        title = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-confirm-title"
            panel.appendChild(el)
        }

        val confirmButton = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-confirm-yes"
            panel.appendChild(el)
        }
        // The green capsule, 3-sliced: rounded cap, stretched middle, rounded cap.
        val sprite = HudSprites.CAPSULE_GREEN
        val cap = HudSprites.CAPSULE_CAP
        val scaleY = BUTTON_H / sprite.h
        val scaleXMid = (BUTTON_W - 2 * cap * scaleY) / (sprite.w - 2 * cap)
        val slices = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-confirm-slices"
            confirmButton.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText = hudSpriteStyleXY(sprite, sprite.x, cap, scaleY, scaleY)
            slices.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText =
                hudSpriteStyleXY(sprite, sprite.x + cap, sprite.w - 2 * cap, scaleXMid, scaleY)
            slices.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText =
                hudSpriteStyleXY(sprite, sprite.x + sprite.w - cap, cap, scaleY, scaleY)
            slices.appendChild(it)
        }
        confirmLabel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-confirm-yes-label"
            confirmButton.appendChild(el)
        }
        listeners.add(confirmButton.disposableTap {
            val action = onConfirm
            close()
            action?.invoke()
        })

        val cancelButton = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-confirm-no"
            el.textContent = "CANCEL"
            panel.appendChild(el)
        }
        listeners.add(cancelButton.disposableTap { cancel() })
    }

    fun open(
        prompt: String,
        confirmText: String = "USE",
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit,
    ) {
        this.onConfirm = onConfirm
        this.onCancel = onCancel
        title.textContent = prompt
        confirmLabel.textContent = confirmText
        isOpen = true
        root.style.display = "block"
    }

    private fun cancel() {
        val action = onCancel
        close()
        action?.invoke()
    }

    fun close() {
        isOpen = false
        root.style.display = "none"
        onConfirm = null
        onCancel = null
    }

    override fun dispose() {
        listeners.forEach { it.dispose() }
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    private companion object {
        const val BUTTON_W = 190.0
        const val BUTTON_H = 40.0

        const val STYLESHEET = """
            .pw-confirm-root {
              position: fixed; inset: 0; z-index: 70;
              font-family: 'PWTitleFont', sans-serif;
            }
            .pw-confirm-backdrop {
              position: absolute; inset: 0;
              background: rgba(0, 8, 16, 0.55);
            }
            .pw-confirm-panel {
              position: absolute;
              left: 50%; top: 50%;
              transform: translate(-50%, -50%);
              min-width: 240px;
              display: flex; flex-direction: column; align-items: center; gap: 12px;
              padding: 18px 20px 16px;
              border: 2px solid rgba(90, 210, 255, 0.7);
              border-radius: 10px;
              background:
                repeating-linear-gradient(180deg, rgba(90,200,225,.12) 0px, rgba(90,200,225,.12) 1px,
                  rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px),
                linear-gradient(180deg, rgba(5,22,36,.98) 0%, rgba(2,10,18,.98) 100%);
              box-shadow: 0 6px 24px rgba(0,0,0,.5);
            }
            .pw-confirm-title {
              color: #e8f6ff; font-size: 14px; letter-spacing: 1px; text-align: center;
              margin-bottom: 2px;
            }
            .pw-confirm-yes {
              position: relative; cursor: pointer;
              filter: drop-shadow(0 2px 4px rgba(0,0,0,.5));
            }
            .pw-confirm-yes:active { filter: brightness(1.3) drop-shadow(0 2px 4px rgba(0,0,0,.5)); }
            .pw-confirm-slices { display: flex; }
            .pw-confirm-yes-label {
              position: absolute; inset: 0;
              display: flex; align-items: center; justify-content: center;
              font: bold 16px sans-serif; letter-spacing: 3px; color: #f6fff8;
              text-shadow: 0 1px 2px rgba(0,0,0,.85), 0 0 8px rgba(20,110,50,.9);
              pointer-events: none;
            }
            .pw-confirm-no {
              padding: 7px 26px; border-radius: 16px; cursor: pointer;
              border: 2px solid rgba(90, 210, 255, 0.6);
              background: rgba(6, 26, 44, 0.95);
              color: #cfefff; font-size: 12px; letter-spacing: 2px;
            }
            .pw-confirm-no:active { background: rgba(16, 46, 74, 0.95); }
        """
    }
}
