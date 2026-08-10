package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.player.ActionPaletteConfig
import world.phantasmal.web.mobileGame.player.iconCell
import world.phantasmal.web.mobileGame.player.GameAction
import world.phantasmal.webui.dom.disposableListener

/**
 * The four actions available in a dungeon, bottom right on the thumb side.
 *
 * Four hexes -- red under the thumb, blue right, yellow left, green top -- each holding whichever
 * action the player has assigned to it (see ActionPaletteConfig). All four are always present:
 * they're the layout the player edits, so one vanishing because its action happens to be unusable
 * right now would both change the cluster's shape mid-fight and hide a slot they may want to
 * reassign. An unusable action is dimmed and inert instead.
 *
 * The hexes are drawn rather than sliced out of the UI sheet. The sheet's cluster is a single
 * interlocking image, so cropping it dragged in slivers of whatever sits next to it and clipped
 * the top hex, and one image can't drop a hex when the special is unavailable. Building each hex
 * from three clipped layers -- cyan rim, coloured ring, dark scanlined face -- keeps PSO's look,
 * stays crisp at any size, and makes each hex an independent element that can be hidden or moved.
 *
 * The arrangement and proportions are still the sheet's: hex size and centres were measured off
 * the original sprite and are reproduced here as fractions, so the cluster sits exactly as it does
 * in the game (see [ActionHex]).
 */
class ActionPalette(
    container: HTMLElement,
    private val config: ActionPaletteConfig,
    private val onAction: (GameAction) -> Unit,
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-action-root"
        container.appendChild(el)
    }

    private val hexes = mutableMapOf<ActionHex, HTMLElement>()
    private val unusable = mutableSetOf<GameAction>()

    init {
        // The real interlocked cluster, drawn as the one piece it's stored as (see
        // ACTION_CLUSTER) -- the hexes share edges, so assembling it from four separate drawn
        // hexes never matched the game's own art.
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-hud-act-cluster"
            el.style.cssText +=
                "background-image:url($ACTION_CLUSTER_URL);" +
                    "background-size:${CLUSTER_W}px ${CLUSTER_H}px;" +
                    "background-repeat:no-repeat;" +
                    "width:${CLUSTER_W}px;height:${CLUSTER_H}px;" +
                    "image-rendering:pixelated;"
            root.appendChild(el)
        }

        for (slot in listOf(ActionHex.TOP, ActionHex.LEFT, ActionHex.RIGHT, ActionHex.BOTTOM)) {
            hexes[slot] = hex(slot)
        }

        refresh()
    }

    /**
     * Marks an action as currently unusable -- a weapon special with no special on the weapon, say.
     * The hex stays put and stays assignable; it just reads as inert and does nothing when tapped.
     * Removing it instead would make the cluster change shape as weapons are swapped, and would
     * hide a slot the player may want to reassign.
     */
    fun setUnusable(action: GameAction, isUnusable: Boolean) {
        if (isUnusable) unusable.add(action) else unusable.remove(action)
        refresh()
    }

    /** Re-reads the assignments, after the player has edited them. */
    fun refresh() {
        for ((slot, el) in hexes) {
            val action = config[slot]
            val icon = el.querySelector(".pw-hud-act-icon") as? HTMLElement ?: continue

            icon.textContent = ""
            icon.style.cssText = when {
                action.trap != null -> toolIconStyle(action.trap.iconCell, ITEM_ICON_SCALE)
                action.tool != null -> toolIconStyle(action.tool.iconCell, ITEM_ICON_SCALE)
                action.actionIcon != null -> actionIconStyle(action.actionIcon, ACTION_ICON_SCALE)
                action.itemIcon != null -> itemIconStyle(action.itemIcon, ITEM_ICON_SCALE)
                // A technique's own black-hex glyph tile from the UI sheet, sized to sit inside
                // the cluster hex like the attack glyphs do.
                action.technique != null -> hudSpriteStyle(
                    HudSprites.hexTile(action.technique.icon.iconCol, action.technique.icon.iconRow),
                    TECH_TILE_SCALE,
                )
                else -> ""
            }

            if (action in unusable) el.classList.add("pw-hud-act-off")
            else el.classList.remove("pw-hud-act-off")
        }
    }

    private fun hex(slot: ActionHex): HTMLElement {
        val el = (document.createElement("div") as HTMLElement).also { el ->
            // Position-named, not action-named: what a slot does is the player's choice, but
            // where it sits never changes.
            el.className = "pw-hud-act pw-hud-act-${slot.name.lowercase()}"
            el.style.left = "${slot.cx * 100}%"
            el.style.top = "${slot.cy * 100}%"
            root.appendChild(el)
        }

        // One hex-clipped overlay per slot on top of the cluster art: transparent normally,
        // lit while pressed, darkened while the slot's action is unusable.
        (document.createElement("div") as HTMLElement).also { overlay ->
            overlay.className = "pw-hud-act-overlay"
            el.appendChild(overlay)
        }

        (document.createElement("div") as HTMLElement).also { glyph ->
            glyph.className = "pw-hud-act-icon"
            el.appendChild(glyph)
        }

        listeners.add(el.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            val action = config[slot]
            if (action in unusable) return@disposableListener
            el.classList.add("pw-hud-act-down")
            onAction(action)
        }))

        // Cleared on up, cancel and leave alike -- a thumb sliding off would otherwise leave the
        // hex lit permanently.
        for (event in listOf("pointerup", "pointercancel", "pointerleave")) {
            listeners.add(el.disposableListener<PointerEvent>(event, {
                el.classList.remove("pw-hud-act-down")
            }))
        }

        return el
    }

    override fun dispose() {
        listeners.forEach { it.dispose() }
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    companion object {
        /** Technique glyph tiles are 50x53 on the sheet; this fits them inside a cluster hex. */
        private const val TECH_TILE_SCALE = 0.72

        /** Cluster and hex geometry, in the original sprite's units, scaled by [S]. */
        const val S = 1.25
        const val CLUSTER_W = (ACTION_CLUSTER_W * S).toInt()
        const val CLUSTER_H = (ACTION_CLUSTER_H * S).toInt()
        const val HEX_W = (56 * S).toInt()
        const val HEX_H = (46 * S).toInt()

        /** The attack glyphs are 48px art; the category glyphs are 16px. Tuned at S = 1.5 and
         *  scaled with [S] since, so resizing the cluster keeps the glyph-to-hex proportion. */
        const val ACTION_ICON_SCALE = 0.62 * S / 1.5
        const val ITEM_ICON_SCALE = 1.7 * S / 1.5

        /** A flat-topped hexagon: points at left and right, level top and bottom edges. */
        const val HEX_CLIP = "polygon(25% 0%, 75% 0%, 100% 50%, 75% 100%, 25% 100%, 0% 50%)"

        val STYLESHEET = """
            .pw-hud-action-root {
              position: fixed;
              bottom: calc(14px + var(--pw-safe-bottom));
              right: calc(0px + var(--pw-safe-right));
              width: ${CLUSTER_W}px; height: ${CLUSTER_H}px;
              z-index: 15;
              touch-action: none;
              user-select: none;
            }
            .pw-hud-act-cluster {
              position: absolute;
              left: 0; top: 0;
              pointer-events: none;
              filter: drop-shadow(0 2px 3px rgba(0,0,0,.75));
            }
            .pw-hud-act {
              position: absolute;
              width: ${HEX_W}px; height: ${HEX_H}px;
              margin-left: -${HEX_W / 2}px; margin-top: -${HEX_H / 2}px;
              touch-action: none;
            }
            /*
             * State overlay clipped to the hex's footprint over the cluster art: lit while
             * pressed, darkened while unusable. An action that can't be used right now stays put
             * and stays assignable -- it just reads as inert.
             */
            .pw-hud-act-overlay {
              position: absolute; inset: 2px;
              clip-path: $HEX_CLIP;
              pointer-events: none;
            }
            .pw-hud-act-down .pw-hud-act-overlay { background: rgba(120,200,255,0.35); }
            .pw-hud-act-off .pw-hud-act-overlay { background: rgba(0,0,0,0.55); }
            .pw-hud-act-off .pw-hud-act-icon { opacity: 0.3; }

            .pw-hud-act-icon {
              position: absolute; left: 50%; top: 50%;
              transform: translate(-50%, -50%);
              pointer-events: none;
            }
        """
    }
}
