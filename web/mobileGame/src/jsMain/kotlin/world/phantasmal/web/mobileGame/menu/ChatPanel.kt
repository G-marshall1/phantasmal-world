package world.phantasmal.web.mobileGame.menu

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * One emote the player can perform, by clip index -- see PlayerAnimations for the numbering and
 * the 1-off between the animation list and these files.
 *
 * These are the real emote clips from the master animation list (#209-231 there, so 208-230
 * here), not a made-up set: performing one plays the actual animation on the character.
 */
class Emote(val label: String, val clip: Int)

val EMOTES: List<Emote> = listOf(
    Emote("Wave", 209),
    Emote("Salute", 221),
    Emote("Thumbs Up", 210),
    Emote("Shrug", 212),
    Emote("Bow", 222),
    Emote("Clap", 214),
    Emote("Victory", 216),
    Emote("Beckon", 219),
    Emote("Kneel", 215),
    Emote("Jump", 213),
    Emote("Dance", 224),
    Emote("Bored", 230),
)

/**
 * Chat and emotes, opened from the green hex of the action palette.
 *
 * Emotes are real: picking one plays that clip on the character, then hands control back. The
 * message box is local only -- there is no networking anywhere in this game, so a typed line goes
 * into this log and nowhere else. It's labelled as such in the panel rather than left to look
 * like it might be reaching someone.
 *
 * Text is set in PSO's own font (the same face the title screen loads), and messages are stored
 * so the log survives the panel being closed and reopened within a session.
 */
class ChatPanel(
    container: HTMLElement,
    private val onEmote: (Emote) -> Unit,
    private val onClose: () -> Unit,
    /**
     * Typed commands like `?fly?`. Returns the line to echo back, or null if the text wasn't a
     * command and should be said normally. Keeping debug tools here rather than on the HUD
     * costs no screen space and matches how PSO's own chat commands work.
     */
    private val onCommand: (String) -> String? = { null },
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()
    private val log = mutableListOf<String>()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-chat-root"
        el.style.display = "none"
        container.appendChild(el)
    }

    private val logEl = (document.createElement("div") as HTMLElement).also { it.className = "pw-chat-log" }
    private val input = (document.createElement("input") as HTMLInputElement).also { el ->
        el.className = "pw-chat-input"
        el.setAttribute("placeholder", "Say something...")
        el.setAttribute("maxlength", "96")
    }

    var isOpen: Boolean = false
        private set

    init {
        val backdrop = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-backdrop"
            root.appendChild(el)
        }
        listeners.add(backdrop.disposableListener<PointerEvent>("pointerdown", { close() }))

        val panel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-panel"
            root.appendChild(el)
        }
        listeners.add(panel.disposableListener<PointerEvent>("pointerdown", { it.stopPropagation() }))

        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-title"
            el.textContent = "CHAT"
            panel.appendChild(el)
        }

        panel.appendChild(logEl)

        val row = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-row"
            panel.appendChild(el)
        }
        row.appendChild(input)

        val send = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-send"
            el.textContent = "SEND"
            row.appendChild(el)
        }
        listeners.add(send.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            submit()
        }))
        listeners.add(input.disposableListener<org.w3c.dom.events.KeyboardEvent>("keydown", {
            if (it.key == "Enter") submit()
        }))

        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-emote-title"
            el.textContent = "EMOTES"
            panel.appendChild(el)
        }

        val grid = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-emotes"
            panel.appendChild(el)
        }

        for (emote in EMOTES) {
            val el = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-chat-emote"
                el.textContent = emote.label
                grid.appendChild(el)
            }
            listeners.add(el.disposableListener<PointerEvent>("pointerdown", {
                it.stopPropagation()
                onEmote(emote)
                close()
            }))
        }

        val close = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-chat-close"
            el.textContent = "CLOSE"
            panel.appendChild(el)
        }
        listeners.add(close.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            close()
        }))

        renderLog()
    }

    fun open() {
        isOpen = true
        root.style.display = "block"
        renderLog()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        root.style.display = "none"
        onClose()
    }

    private fun submit() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        input.value = ""

        // A recognised command runs instead of being said, and answers in the log.
        val response = onCommand(text)
        log.add(response ?: text)
        renderLog()
    }

    private fun renderLog() {
        logEl.innerHTML = ""

        if (log.isEmpty()) {
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-chat-empty"
                el.textContent =
                    "Nobody else is here. This game has no networking yet, so messages stay on " +
                        "this device. Emotes below are real and play on your character."
                logEl.appendChild(el)
            }
            return
        }

        for (line in log) {
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-chat-line"
                el.textContent = line
                logEl.appendChild(el)
            }
        }
        logEl.scrollTop = logEl.scrollHeight.toDouble()
    }

    override fun dispose() {
        listeners.forEach { it.dispose() }
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    private companion object {
        const val STYLESHEET = """
            .pw-chat-root { position:fixed; inset:0; z-index:70; font-family:'PWTitleFont', sans-serif; }
            .pw-chat-backdrop { position:absolute; inset:0; background:rgba(0,10,20,0.55); }
            .pw-chat-panel {
              position:absolute;
              left:calc(10px + var(--pw-safe-left)); right:calc(10px + var(--pw-safe-right));
              top:calc(10px + var(--pw-safe-top)); bottom:calc(10px + var(--pw-safe-bottom));
              display:flex; flex-direction:column; gap:6px; padding:10px 14px;
              border:2px solid rgba(90,210,255,0.55); border-radius:10px;
              background:rgba(6,26,44,0.94); box-sizing:border-box;
            }
            .pw-chat-title, .pw-chat-emote-title {
              color:#ffd36b; font-size:13px; letter-spacing:2px; flex:none;
            }
            .pw-chat-log {
              flex:1 1 auto; min-height:0; overflow-y:auto; touch-action:pan-y;
              border:1px solid rgba(90,210,255,0.25); border-radius:6px; padding:8px;
              background:rgba(0,0,0,0.3);
            }
            .pw-chat-empty { color:#7fa8c0; font-size:11px; line-height:1.6; }
            .pw-chat-line { color:#cfefff; font-size:13px; padding:2px 0; }
            .pw-chat-row { display:flex; gap:8px; flex:none; }
            .pw-chat-input {
              flex:1; min-width:0; padding:7px 10px; border-radius:6px;
              border:1px solid rgba(90,210,255,0.45); background:rgba(0,0,0,0.4);
              color:#ffffff; font-family:'PWTitleFont', sans-serif; font-size:13px;
            }
            .pw-chat-input::placeholder { color:#5b7d92; }
            .pw-chat-send, .pw-chat-close {
              padding:7px 14px; border-radius:6px; color:#cfefff; font-size:12px;
              letter-spacing:2px; background:rgba(10,50,80,0.9);
              border:1px solid rgba(90,210,255,0.6); user-select:none;
              touch-action:manipulation; text-align:center;
            }
            .pw-chat-close { flex:none; margin-top:2px; }
            .pw-chat-emotes {
              flex:none; display:grid; gap:5px; overflow-y:auto; touch-action:pan-y;
              grid-template-columns:repeat(auto-fill, minmax(84px, 1fr)); max-height:34%;
            }
            .pw-chat-emote {
              padding:7px 4px; border-radius:6px; text-align:center; color:#cfefff;
              font-size:12px; background:rgba(10,50,80,0.85);
              border:1px solid rgba(90,210,255,0.4); user-select:none;
              touch-action:manipulation;
            }
        """
    }
}
