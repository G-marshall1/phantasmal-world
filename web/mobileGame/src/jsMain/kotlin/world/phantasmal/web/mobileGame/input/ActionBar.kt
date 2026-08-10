package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.player.ActionBarConfig
import world.phantasmal.web.mobileGame.player.iconCell
import world.phantasmal.web.mobileGame.player.GameAction
import world.phantasmal.webui.dom.disposableListener

/**
 * Blue Burst's numbered ability bar: nine hexes in a row along the bottom of the screen, each
 * holding any [GameAction] -- a heal under 1, techniques under the middle fingers, an antidote
 * at the end. Deliberately smaller than the action cluster's hexes so the bottom of the screen
 * doesn't crowd; the number in each corner is the slot's name, as on the PC HUD where they map
 * to the 1-9 keys.
 *
 * Class names are its own (`pw-hud-abar-*`): the status panel already owns the bare bar class
 * for its HP/TP strips, and sharing the palette's root class once dragged this bar into the
 * cluster's fixed box -- both learned the hard way. The menu hides it via the rule below.
 * Editing happens in the menu's ACTIONS pane, same as the cluster.
 */
class ActionBar(
    container: HTMLElement,
    private val config: ActionBarConfig,
    private val onAction: (GameAction) -> Unit,
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-abar"
        container.appendChild(el)
    }

    private val icons = mutableListOf<HTMLElement>()

    init {
        for (i in 0 until ActionBarConfig.SLOT_COUNT) {
            val cell = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-hud-abar-hex"
                root.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-hud-abar-inner"
                cell.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-hud-abar-num"
                el.textContent = "${i + 1}"
                cell.appendChild(el)
            }
            val icon = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-hud-abar-icon"
                cell.appendChild(el)
            }
            icons.add(icon)

            listeners.add(cell.disposableListener<PointerEvent>("pointerdown", { e ->
                e.stopPropagation()
                cell.classList.add("pw-hud-abar-down")
                onAction(config[i])
            }))
            listeners.add(cell.disposableListener<PointerEvent>("pointerup", {
                cell.classList.remove("pw-hud-abar-down")
            }))
            listeners.add(cell.disposableListener<PointerEvent>("pointerleave", {
                cell.classList.remove("pw-hud-abar-down")
            }))
        }
        refresh()
    }

    /** Re-reads the assignments, after the player has edited them in the menu. */
    fun refresh() {
        for (i in 0 until ActionBarConfig.SLOT_COUNT) {
            val action = config[i]
            val icon = icons[i]
            icon.textContent = ""
            icon.style.cssText = when {
                action.tool != null -> toolIconStyle(action.tool.iconCell, BAR_ITEM_ICON_SCALE)
                action.actionIcon != null -> actionIconStyle(action.actionIcon, BAR_ACTION_ICON_SCALE)
                action.itemIcon != null -> itemIconStyle(action.itemIcon, BAR_ITEM_ICON_SCALE)
                action.technique != null -> hudSpriteStyle(
                    HudSprites.hexTile(action.technique.icon.iconCol, action.technique.icon.iconRow),
                    BAR_TECH_TILE_SCALE,
                )
                else -> ""
            }
        }
    }

    override fun dispose() {
        listeners.forEach { it.dispose() }
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    private companion object {
        /** Icon scales tuned to sit inside the bar's smaller hexes. */
        const val BAR_ACTION_ICON_SCALE = 0.5
        const val BAR_ITEM_ICON_SCALE = 1.3
        const val BAR_TECH_TILE_SCALE = 0.52

        const val STYLESHEET = """
            body.pw-menu-open .pw-hud-abar { display: none !important; }
            .pw-hud-abar {
              position: fixed;
              left: 50%;
              bottom: calc(8px + var(--pw-safe-bottom));
              transform: translateX(-50%);
              display: flex;
              gap: 5px;
              z-index: 40;
              touch-action: none;
              user-select: none;
            }
            .pw-hud-abar-hex {
              position: relative;
              width: 38px;
              height: 42px;
              clip-path: polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%);
              background: rgba(120, 220, 255, 0.5);
              cursor: pointer;
            }
            .pw-hud-abar-inner {
              position: absolute;
              inset: 2px;
              clip-path: polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%);
              background:
                repeating-linear-gradient(180deg, rgba(90,200,225,.10) 0px, rgba(90,200,225,.10) 1px,
                  rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px),
                linear-gradient(180deg, #071824 0%, #030b12 100%);
            }
            .pw-hud-abar-down .pw-hud-abar-inner {
              background: linear-gradient(180deg, #1c4f66 0%, #0d2a3a 100%);
            }
            .pw-hud-abar-num {
              position: absolute;
              top: 3px;
              left: 0;
              right: 0;
              text-align: center;
              color: #7fc9e6;
              font: bold 8px sans-serif;
              pointer-events: none;
              z-index: 2;
            }
            .pw-hud-abar-icon {
              position: absolute;
              left: 50%;
              top: 55%;
              transform: translate(-50%, -50%);
              pointer-events: none;
            }
        """
    }
}
