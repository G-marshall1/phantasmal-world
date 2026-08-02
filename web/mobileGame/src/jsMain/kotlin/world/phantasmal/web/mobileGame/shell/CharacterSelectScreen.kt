package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.persistence.CharacterSave
import world.phantasmal.webui.dom.disposableListener

/** Lists locally-saved characters plus a "Create New Character" entry. */
class CharacterSelectScreen(
    container: HTMLElement,
    saves: List<CharacterSave>,
    onSelect: (CharacterSave) -> Unit,
    onCreateNew: () -> Unit,
    onDelete: (CharacterSave) -> Unit,
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;inset:0;background:#181818;display:flex;flex-direction:column;" +
                "align-items:center;padding:40px 24px;overflow-y:auto;z-index:30;"
        container.appendChild(el)
    }

    private val heading = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "SELECT CHARACTER"
        el.style.cssText =
            "font:bold 20px sans-serif;color:white;letter-spacing:1px;margin-bottom:24px;" +
                "text-shadow:0 1px 2px black;"
        root.appendChild(el)
    }

    private val list = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText = "display:flex;flex-direction:column;gap:12px;width:min(360px,90vw);"
        root.appendChild(el)
    }

    init {
        for (save in saves) {
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.style.cssText =
                    "display:flex;align-items:center;padding:14px 16px;border-radius:10px;" +
                        "background:rgba(255,255,255,0.08);border:2px solid rgba(255,255,255,0.15);" +
                        "touch-action:none;user-select:none;"
            }

            val info = (document.createElement("div") as HTMLElement).also { el ->
                el.style.cssText = "flex:1;"
                el.innerHTML =
                    "<div style=\"font:bold 16px sans-serif;color:white;\">${save.name}</div>" +
                        "<div style=\"font:13px sans-serif;color:#aaa;margin-top:2px;\">${save.characterClassSlug}</div>"
            }
            row.appendChild(info)

            val deleteButton = (document.createElement("div") as HTMLElement).also { el ->
                el.textContent = "✕"
                el.style.cssText =
                    "padding:6px 12px;color:#e57373;font:bold 16px sans-serif;touch-action:none;"
            }
            row.appendChild(deleteButton)

            listeners.add(row.disposableListener<PointerEvent>("pointerdown", { onSelect(save) }))
            listeners.add(
                deleteButton.disposableListener<PointerEvent>("pointerdown", { e ->
                    e.stopPropagation()
                    onDelete(save)
                })
            )

            list.appendChild(row)
        }

        val createRow = (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = "+ Create New Character"
            el.style.cssText =
                "padding:16px;border-radius:10px;text-align:center;" +
                    "background:#ff8a3d;color:#181818;font:bold 15px sans-serif;" +
                    "touch-action:none;user-select:none;"
        }
        listeners.add(createRow.disposableListener<PointerEvent>("pointerdown", { onCreateNew() }))
        list.appendChild(createRow)
    }

    override fun dispose() {
        for (listener in listeners) listener.dispose()
        root.remove()
        super.dispose()
    }
}
