package world.phantasmal.web.mobileGame.menu

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.input.ItemIcon
import world.phantasmal.web.mobileGame.input.disposableTap
import world.phantasmal.web.mobileGame.input.itemIconStyle
import world.phantasmal.webui.dom.disposableListener

/** One tappable line in an NPC's window -- a purchase, a sale, a bank move. */
class DialogRow(
    val k: String,
    val v: String,
    val d: String? = null,
    /** The category glyph shown beside the name -- see ItemIcons.kt. */
    val icon: ItemIcon? = null,
    /**
     * Null renders an inert header/info line. Last so a trailing lambda still binds to it --
     * putting [icon] after this made every `DialogRow(..., icon = X) { ... }` call fail to
     * compile, since the trailing lambda always targets the final parameter.
     */
    val onTap: (() -> Unit)? = null,
)

class NpcDialogState(
    val npcName: String,
    val text: String,
    val rows: List<DialogRow> = emptyList(),
)

/**
 * The talk window that opens at an NPC: PSO's bottom dialog box -- name tab riding the frame,
 * the NPC's line, then whatever the NPC deals in as tappable rows (wares, the bank's ledger).
 * Tapping the backdrop or CLOSE dismisses it; the renderer also closes it when the player just
 * walks away, like the real game.
 */
class NpcDialog(
    container: HTMLElement,
    private val onClose: () -> Unit = {},
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-npc-root"
        el.style.display = "none"
        container.appendChild(el)
    }

    private val panel: HTMLElement
    private val nameTab: HTMLElement
    private val textEl: HTMLElement
    private val rowsEl: HTMLElement

    var isOpen: Boolean = false
        private set

    init {
        val backdrop = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-npc-backdrop"
            root.appendChild(el)
        }
        listeners.add(backdrop.disposableListener<PointerEvent>("pointerdown", { close() }))

        panel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-npc-panel"
            root.appendChild(el)
        }
        listeners.add(panel.disposableListener<PointerEvent>("pointerdown", { it.stopPropagation() }))

        nameTab = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-npc-name"
            panel.appendChild(el)
        }
        textEl = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-npc-text"
            panel.appendChild(el)
        }
        rowsEl = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-npc-rows"
            panel.appendChild(el)
        }

        val closeButton = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-npc-close"
            el.textContent = "CLOSE"
            panel.appendChild(el)
        }
        listeners.add(closeButton.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            close()
        }))
    }

    fun open(state: NpcDialogState) {
        isOpen = true
        root.style.display = "block"
        nameTab.textContent = state.npcName
        textEl.textContent = state.text

        rowsEl.innerHTML = ""
        for (row in state.rows) {
            val rowEl = (document.createElement("div") as HTMLElement).also { el ->
                el.className =
                    if (row.onTap != null) "pw-npc-row pw-npc-row-tap"
                    else "pw-npc-row pw-npc-row-info"
                rowsEl.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-npc-row-k"
                el.textContent = row.k
                rowEl.appendChild(el)
            }
            row.icon?.let { icon ->
                (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-npc-row-icon"
                    el.style.cssText = itemIconStyle(icon, ITEM_ICON_SCALE)
                    rowEl.appendChild(el)
                }
            }
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-npc-row-v"
                el.textContent = row.v
                rowEl.appendChild(el)
            }
            row.d?.let { detail ->
                (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-npc-row-d"
                    el.textContent = detail
                    rowEl.appendChild(el)
                }
            }
            row.onTap?.let { onTap ->
                // A tap, not a press: these lists are long and are scrolled constantly, and
                // acting on pointerdown chose whatever row a drag happened to start on.
                listeners.add(rowEl.disposableTap {
                    selectRow(rowEl)
                    onTap()
                })
            }
        }
    }

    /** Lights the row the player just chose, in the HUD sheet's own green. */
    private fun selectRow(rowEl: HTMLElement) {
        selectedRow?.classList?.remove("pw-npc-row-on")
        rowEl.classList.add("pw-npc-row-on")
        selectedRow = rowEl
    }

    private var selectedRow: HTMLElement? = null

    fun close() {
        if (!isOpen) return
        isOpen = false
        root.style.display = "none"
        selectedRow = null
        onClose()
    }

    override fun dispose() {
        listeners.forEach { it.dispose() }
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    private companion object {
        /** The 16px glyphs sit comfortably beside 13px row text at this scale. */
        const val ITEM_ICON_SCALE = 1.0

        const val STYLESHEET = """
            .pw-npc-root { position: fixed; inset: 0; z-index: 58; font-family: 'PWTitleFont', sans-serif; }
            .pw-npc-backdrop { position: absolute; inset: 0; }
            .pw-npc-panel {
              position: absolute;
              left: 50%;
              transform: translateX(-50%);
              bottom: calc(14px + var(--pw-safe-bottom));
              width: min(560px, 92vw);
              max-height: 62vh;
              display: flex;
              flex-direction: column;
              border: 2px solid rgba(90, 210, 255, 0.65);
              border-radius: 10px;
              background:
                repeating-linear-gradient(180deg, rgba(90,200,225,.12) 0px, rgba(90,200,225,.12) 1px,
                  rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px),
                linear-gradient(180deg, rgba(5,22,36,.97) 0%, rgba(2,10,18,.97) 100%);
              padding: 16px 14px 10px;
              box-sizing: border-box;
            }
            .pw-npc-name {
              position: absolute;
              top: -13px;
              left: 12px;
              padding: 2px 16px 3px 10px;
              clip-path: polygon(0 0, calc(100% - 12px) 0, 100% 100%, 0 100%);
              background: linear-gradient(180deg, #0a2f77, #061a48);
              border: 2px solid rgba(90, 180, 255, 0.85);
              font-size: 12px;
              letter-spacing: 1px;
              color: #eef6ff;
              text-shadow: 0 0 6px rgba(90, 180, 255, 0.9);
            }
            .pw-npc-text {
              color: #e8f6ff;
              font-size: 13px;
              line-height: 1.55;
              margin-bottom: 8px;
              flex: none;
            }
            .pw-npc-rows {
              overflow-y: auto;
              touch-action: pan-y;
              min-height: 0;
            }
            .pw-npc-row {
              display: flex;
              align-items: baseline;
              gap: 10px;
              padding: 5px 6px;
              border-bottom: 1px solid rgba(90, 210, 255, 0.14);
            }
            .pw-npc-row-tap { cursor: pointer; }
            .pw-npc-row-tap:active { background: rgba(90, 210, 255, 0.18); }
            .pw-npc-row-on {
              background:
                repeating-linear-gradient(180deg, rgba(120,255,170,.20) 0px, rgba(120,255,170,.20) 1px,
                  rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px),
                linear-gradient(180deg, rgba(40,150,80,.55) 0%, rgba(20,90,50,.55) 100%);
              box-shadow: inset 0 0 0 1px rgba(140, 255, 190, .55);
            }
            .pw-npc-row-info .pw-npc-row-v { color: #ffd36b; letter-spacing: 1px; }
            .pw-npc-row-k { color: #8fc9e6; font-size: 10px; letter-spacing: 1px; min-width: 54px; }
            .pw-npc-row-icon { flex: none; opacity: .92; align-self: center; }
            .pw-npc-row-v { color: #ffffff; font-size: 13px; flex: 1; }
            .pw-npc-row-d { color: #7fd6a0; font-size: 10px; text-align: right; }
            .pw-npc-close {
              margin-top: 8px;
              align-self: flex-end;
              padding: 4px 16px;
              border-radius: 14px;
              border: 2px solid rgba(90, 210, 255, 0.6);
              background: rgba(6, 26, 44, 0.95);
              color: #cfefff;
              font-size: 11px;
              letter-spacing: 2px;
              flex: none;
              cursor: pointer;
            }
        """
    }
}
