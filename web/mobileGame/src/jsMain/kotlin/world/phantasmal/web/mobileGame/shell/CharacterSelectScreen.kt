package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.persistence.CharacterSave
import world.phantasmal.web.mobileGame.shell.titlescreen.HEX_GRID_SVG_INNER
import world.phantasmal.webui.dom.disposableListener

/**
 * The character select, laid out like the original client's screen (per the user's reference
 * capture): the orange SELECT CHARACTER banner across the top with angular circuit trim, a
 * framed window carrying a CHARACTERS tab, exactly four slot bars inside it, and the
 * "Please select character." prompt underneath. Every slot exists whether or not a save fills
 * it: an empty one reads "New Character" and starts creation, a filled one shows the save's
 * name and enters the game, with a small delete affordance since these are local saves.
 *
 * All chrome is drawn in CSS -- the deep-blue grid field, banner, frame and slot gradients are
 * this project's own art in the original's arrangement.
 */
class CharacterSelectScreen(
    container: HTMLElement,
    saves: List<CharacterSave>,
    onSelect: (CharacterSave) -> Unit,
    onCreateNew: () -> Unit,
    onDelete: (CharacterSave) -> Unit,
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cs-root"
        container.appendChild(el)
    }

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    init {
        // The blue grid field behind everything.
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cs-hexbg"
            el.innerHTML =
                """<svg viewBox="0 0 8465 8477" class="pw-cs-hex-svg">$HEX_GRID_SVG_INNER</svg>"""
            root.appendChild(el)
        }

        // Top banner with its angular trim lines.
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cs-banner-wrap"
            el.innerHTML =
                "<div class='pw-cs-trim pw-cs-trim-top'></div>" +
                    "<div class='pw-cs-banner'>SELECT CHARACTER</div>" +
                    "<div class='pw-cs-trim pw-cs-trim-bottom'></div>"
            root.appendChild(el)
        }

        // The framed window: CHARACTERS tab riding its top edge, four slots inside.
        val frame = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cs-frame"
            root.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cs-tab"
            el.textContent = "CHARACTERS"
            frame.appendChild(el)
        }
        val slots = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cs-slots"
            frame.appendChild(el)
        }

        repeat(SLOT_COUNT) { i ->
            val save = saves.getOrNull(i)

            val slot = (document.createElement("div") as HTMLElement).also { el ->
                el.className = if (save != null) "pw-cs-slot pw-cs-slot-filled" else "pw-cs-slot"
                slots.appendChild(el)
            }

            val label = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cs-slot-label"
                el.textContent = save?.name ?: "New Character"
                slot.appendChild(el)
            }

            if (save != null) {
                // The class in small text on the bar's right, then the delete cross.
                (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-cs-slot-class"
                    el.textContent = save.characterClassSlug
                    slot.appendChild(el)
                }
                val deleteButton = (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-cs-delete"
                    el.textContent = "✕"
                    slot.appendChild(el)
                }
                listeners.add(deleteButton.disposableListener<PointerEvent>("pointerdown", { e ->
                    e.stopPropagation()
                    onDelete(save)
                }))
                listeners.add(slot.disposableListener<PointerEvent>("pointerdown", { onSelect(save) }))
            } else {
                listeners.add(slot.disposableListener<PointerEvent>("pointerdown", { onCreateNew() }))
            }
        }

        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cs-prompt"
            el.textContent = "Please select character."
            root.appendChild(el)
        }
    }

    override fun dispose() {
        for (listener in listeners) listener.dispose()
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    companion object {
        /** The original screen carries exactly four slots. */
        private const val SLOT_COUNT = 4

        private const val STYLESHEET = """
            .pw-cs-root {
              position: fixed;
              inset: 0;
              z-index: 30;
              overflow: hidden;
              background:
                radial-gradient(circle at 50% 45%, rgba(20,60,140,.35) 0%, rgba(0,0,0,0) 60%),
                linear-gradient(170deg, #061a3a 0%, #030b1d 60%, #010409 100%);
              display: flex;
              flex-direction: column;
              align-items: center;
              padding: calc(14px + var(--pw-safe-top)) calc(24px + var(--pw-safe-right))
                       calc(16px + var(--pw-safe-bottom)) calc(24px + var(--pw-safe-left));
              box-sizing: border-box;
              overflow-y: auto;
              touch-action: none;
              user-select: none;
            }
            .pw-cs-hexbg {
              position: absolute;
              inset: -10%;
              opacity: .22;
              pointer-events: none;
            }
            .pw-cs-hex-svg {
              width: 100%;
              height: 100%;
              stroke: rgba(70,130,230,.55);
              fill: none;
            }
            /* --- banner --- */
            .pw-cs-banner-wrap {
              position: relative;
              width: min(560px, 94vw);
              flex: 0 0 auto;
            }
            .pw-cs-trim {
              height: 3px;
              background: linear-gradient(90deg, rgba(90,180,255,.9), rgba(90,180,255,.15));
            }
            .pw-cs-trim-top {
              margin-bottom: 5px;
              clip-path: polygon(0 0, 78% 0, 82% 100%, 100% 100%, 100% 100%, 0 100%);
            }
            .pw-cs-trim-bottom {
              margin-top: 5px;
              clip-path: polygon(0 0, 22% 0, 26% 100%, 0 100%);
              background: linear-gradient(90deg, rgba(90,180,255,.9), rgba(90,180,255,.35));
            }
            .pw-cs-banner {
              padding: 8px 0 9px;
              text-align: center;
              font: bold 26px Georgia, 'Times New Roman', serif;
              letter-spacing: 3px;
              color: #fdf3e3;
              text-shadow: 0 0 10px rgba(255,150,60,.9), 0 2px 3px black;
              background:
                linear-gradient(180deg, rgba(140,60,10,.0) 0%, rgba(160,72,12,.75) 45%,
                  rgba(120,48,8,.75) 55%, rgba(60,22,4,.0) 100%),
                rgba(10,6,2,.55);
            }
            /* --- window frame + tab --- */
            .pw-cs-frame {
              position: relative;
              margin-top: 30px;
              width: min(380px, 88vw);
              border: 2px solid rgba(120,230,170,.75);
              box-shadow: 0 0 10px rgba(60,160,255,.35), inset 0 0 14px rgba(0,0,0,.6);
              background: rgba(4,16,34,.55);
              padding: 16px 14px 14px;
              box-sizing: border-box;
              flex: 0 0 auto;
            }
            .pw-cs-tab {
              position: absolute;
              top: -15px;
              left: 10px;
              padding: 3px 22px 4px 14px;
              clip-path: polygon(0 0, calc(100% - 14px) 0, 100% 100%, 0 100%);
              background: linear-gradient(180deg, #0a2f77, #061a48);
              border: 2px solid rgba(90,180,255,.85);
              font: bold 14px sans-serif;
              letter-spacing: 1px;
              color: #eef6ff;
              text-shadow: 0 0 6px rgba(90,180,255,.9);
            }
            /* --- slots --- */
            .pw-cs-slots {
              display: flex;
              flex-direction: column;
              gap: 11px;
            }
            .pw-cs-slot {
              position: relative;
              display: flex;
              align-items: center;
              height: 34px;
              padding: 0 10px;
              background: linear-gradient(180deg, rgba(90,190,180,.45) 0%,
                rgba(40,110,110,.45) 55%, rgba(16,60,66,.5) 100%);
              border: 1px solid rgba(150,240,220,.4);
              touch-action: none;
              cursor: pointer;
            }
            .pw-cs-slot:active {
              background: linear-gradient(180deg, #e8b464 0%, #c98a2e 55%, #8f5c14 100%);
              border-color: rgba(255,220,150,.8);
            }
            .pw-cs-slot-label {
              flex: 1;
              font: 15px sans-serif;
              color: #f4fbfb;
              text-shadow: 0 1px 2px black;
            }
            .pw-cs-slot-filled .pw-cs-slot-label { font-weight: bold; }
            .pw-cs-slot-class {
              font: 12px sans-serif;
              color: #cfe8e8;
              margin-right: 10px;
              text-shadow: 0 1px 2px black;
            }
            .pw-cs-delete {
              padding: 2px 8px;
              color: #ff9d94;
              font: bold 15px sans-serif;
              touch-action: none;
            }
            /* --- prompt --- */
            .pw-cs-prompt {
              margin-top: 18px;
              font: bold 15px sans-serif;
              letter-spacing: 1px;
              color: #f2f8f8;
              text-shadow: 0 1px 2px black, 0 0 8px rgba(60,160,255,.5);
              flex: 0 0 auto;
            }
        """
    }
}
