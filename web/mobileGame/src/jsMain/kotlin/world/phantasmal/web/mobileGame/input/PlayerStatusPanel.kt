package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener
import world.phantasmal.web.mobileGame.player.Technique

/**
 * Top-left status HUD, replicating the real PSO v2/PC in-game HUD, whose every element tracks a
 * specific live stat:
 *
 *  - The round dial on the left is the **Photon Blast gauge**: its ring fills clockwise as the
 *    character deals and takes damage (see PhotonBlastGauge), and flashes once full -- a blast is
 *    ready. The number beside it is the PB **chain-order marker**, which in a party shows this
 *    player's position in a chained blast; solo it reads 0, which is genuinely what the real
 *    game shows.
 *  - HP row: label, `current/max`, and the green capsule bar -- which swaps to the sheet's own
 *    **orange** capsule when health falls below [HP_ORANGE_FRACTION], the real HUD's low-health
 *    warning state.
 *  - TP row: same, in blue.
 *  - A `Lv` pill hangs off the frame's bottom-left with the character name beside it.
 *
 * Art sources, all from the ripped v2 UI sheet (see HudSprites):
 *  - bars: [HudSprites.CAPSULE_GREEN]/[HudSprites.CAPSULE_ORANGE]/[HudSprites.CAPSULE_TRACK],
 *    3-sliced (see [capsuleBar]) so the rounded caps stay round at any length.
 *  - PB dial gem: [HudSprites.GEM_RED].
 *  - all text: the PSO HUD font atlas via [SpriteLabel] (see SpriteFont.kt).
 *
 * Two deliberate departures, both because the v1/v2 sprite sheet simply does not contain the
 * piece:
 *  - The sheet ships dark/orange/green capsule fills but no blue one, and the real HUD's TP bar
 *    is blue. TP therefore draws the green sprite through an SVG feColorMatrix that pushes green
 *    into blue while leaving the blue border blue (a plain CSS hue-rotate would swing the border
 *    to magenta along with the fill).
 *  - The frame/ring/Lv-pill outlines are drawn in CSS; the PB ring's fill arc is a conic
 *    gradient masked to the ring's band, since the sheet stores no per-percentage dial art.
 *
 * HP and TP are real, driven by [setHealth]/[setTp] from the character's class statline (see
 * BASE_STATS_LEVEL_1) -- an android's TP genuinely reads 0/0, since Casts have none. The PB ring
 * is real via [setPhotonBlast]. Level still reads "Lv 1" as a constant rather than a
 * placeholder: there's no levelling system, so every character really is level 1.
 */
class PlayerStatusPanel(
    container: HTMLElement,
    characterName: String,
    /** Tapping the right end of the frame (past the bars) opens the menu -- the HUD is the button. */
    private val onMenuTap: () -> Unit = {},
    /** Tapping the PB dial arms/fires the photon blast -- see GameRenderer.activatePhotonBlast. */
    private val onPhotonBlastTap: () -> Unit = {},
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-root"
        container.appendChild(el)
    }

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    // feColorMatrix: R'=R, G'=0.55G, B'=B+0.9G -- turns the green capsule's core blue while the
    // border, which is already blue-dominant, stays blue.
    private val filterDefs = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText = "position:absolute;width:0;height:0;overflow:hidden;"
        el.innerHTML = """
            <svg xmlns="http://www.w3.org/2000/svg"><defs>
              <filter id="pw-hud-tp-blue" color-interpolation-filters="sRGB">
                <feColorMatrix type="matrix" values="
                  1    0    0 0 0
                  0    0.55 0 0 0
                  0    0.9  1 0 0
                  0    0    0 1 0"/>
              </filter>
            </defs></svg>
        """.trimIndent()
        container.appendChild(el)
    }

    private val hpClip: HTMLElement
    private val hpFillGreen: HTMLElement
    private val hpFillOrange: HTMLElement
    private val hpValue: SpriteLabel
    private lateinit var levelLabel: SpriteLabel
    private val tpClip: HTMLElement
    private val tpValue: SpriteLabel
    private val pbRingFill: HTMLElement
    private val shiftaIcon: HTMLElement
    private val debandIcon: HTMLElement

    private var lastPbTenths = -1

    init {
        val frame = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-frame"
            root.appendChild(it)
        }

        // --- Photon Blast dial: ring + fill arc + gem + chain-order counter ---
        val badge = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-badge pw-hud-tappable"
            frame.appendChild(it)
        }
        listeners.add(badge.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            onPhotonBlastTap()
        }))
        (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-ring"
            badge.appendChild(it)
        }
        pbRingFill = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-ring-fill"
            badge.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-hex"
            it.style.cssText += hudSpriteStyle(HudSprites.GEM_RED, HEX_SCALE)
            badge.appendChild(it)
        }
        val counter = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-counter"
            badge.appendChild(it)
        }
        SpriteLabel(counter, displaySize = TEXT_SIZE).setText("0")

        // --- HP / TP rows ---
        val rows = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-rows"
            frame.appendChild(it)
        }

        val hp = buildRow(rows, "HP", tpBlue = false)
        hpClip = hp.clip
        hpValue = hp.value
        hpFillGreen = chevronBar(HudSprites.CHEVRON_GREEN)
        hpFillOrange = chevronBar(HudSprites.CHEVRON_ORANGE)
        hpFillOrange.style.display = "none"
        hpClip.appendChild(hpFillGreen)
        hpClip.appendChild(hpFillOrange)

        val tp = buildRow(rows, "TP", tpBlue = true)
        tpClip = tp.clip
        tpValue = tp.value
        val tpFill = chevronBar(HudSprites.CHEVRON_GREEN)
        tpFill.style.filter = "url(#pw-hud-tp-blue) brightness(1.18)"
        tpClip.appendChild(tpFill)

        // The menu hotspot: an invisible zone over the frame's right end. The whole HUD reads
        // as one plate; its far edge is the door into the menu, replacing the old MENU button.
        (document.createElement("div") as HTMLElement).also { zone ->
            zone.className = "pw-hud-menu-zone pw-hud-tappable"
            frame.appendChild(zone)
            listeners.add(zone.disposableListener<PointerEvent>("pointerdown", {
                it.stopPropagation()
                onMenuTap()
            }))
        }

        // --- Lv pill + name, below the frame ---
        val below = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-below"
            root.appendChild(it)
        }
        val lvPill = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-lv"
            below.appendChild(it)
        }
        levelLabel = SpriteLabel(lvPill, displaySize = TEXT_SIZE)
        levelLabel.setText("Lv1")

        val nameEl = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-name"
            below.appendChild(it)
        }
        SpriteLabel(nameEl, displaySize = TEXT_SIZE).setText(characterName)

        // --- Buff strip: what is currently running on this character, beside their plate ---
        val buffs = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-buffs"
            below.appendChild(it)
        }
        shiftaIcon = buffIcon(buffs, Technique.SHIFTA)
        debandIcon = buffIcon(buffs, Technique.DEBAND)
    }

    /**
     * One slot in the buff strip, carrying the technique's own hex tile -- the same art the
     * action palette casts it from, so the icon that grants the buff is the icon that reports it.
     * Built hidden; [setBuffs] is what brings it up.
     */
    private fun buffIcon(parent: HTMLElement, technique: Technique): HTMLElement =
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-hud-buff"
            el.style.cssText += hudSpriteStyle(
                HudSprites.hexTile(technique.icon.iconCol, technique.icon.iconRow),
                BUFF_ICON_SCALE,
            )
            el.style.display = "none"
            parent.appendChild(el)
        }

    /**
     * Shows the buffs standing on this character. In a party this is the local player's plate;
     * the same call drives an ally's once there are allies to drive it for.
     */
    fun setBuffs(shifta: Boolean, deband: Boolean) {
        shiftaIcon.style.display = if (shifta) "block" else "none"
        debandIcon.style.display = if (deband) "block" else "none"
    }

    private val deathOverlay = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-death-overlay"
        el.textContent = "YOU DIED"
        container.appendChild(el)
    }

    private class Row(val clip: HTMLElement, val value: SpriteLabel)

    private fun buildRow(parent: HTMLElement, label: String, tpBlue: Boolean): Row {
        val row = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-row"
            parent.appendChild(it)
        }

        val head = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-row-head"
            row.appendChild(it)
        }
        val labelWrap = (document.createElement("div") as HTMLElement).also { head.appendChild(it) }
        SpriteLabel(labelWrap, displaySize = TEXT_SIZE).setText(label)
        val valueWrap = (document.createElement("div") as HTMLElement).also { head.appendChild(it) }
        val value = SpriteLabel(valueWrap, displaySize = TEXT_SIZE)

        val bar = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-bar"
            it.style.cssText = "width:${BAR_W}px;height:${BAR_H}px;"
            row.appendChild(it)
        }
        // Unfilled remainder.
        (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-bar-layer"
            it.appendChild(chevronBar(HudSprites.CHEVRON_TRACK))
            bar.appendChild(it)
        }
        val clip = (document.createElement("div") as HTMLElement).also {
            it.className = "pw-hud-bar-layer pw-hud-bar-clip"
            bar.appendChild(it)
        }

        return Row(clip, value)
    }

    /**
     * 3-slices a chevron bar sprite into [left cap | stretched middle | right cap] so the bar can
     * be any width while its near-straight left edge and pointed right end keep their authored
     * shapes. The middle of the source is uniform along x, so stretching only it is lossless.
     */
    private fun chevronBar(sprite: HudSprite): HTMLElement {
        val capL = HudSprites.CHEVRON_CAP_LEFT
        val capR = HudSprites.CHEVRON_CAP_RIGHT
        val scaleY = BAR_H / sprite.h
        val midW = BAR_W - (capL + capR) * scaleY
        val scaleXMid = midW / (sprite.w - capL - capR)

        val el = (document.createElement("div") as HTMLElement).also {
            it.style.cssText = "position:absolute;top:0;left:0;display:flex;width:${BAR_W}px;height:${BAR_H}px;"
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText = hudSpriteStyleXY(sprite, sprite.x, capL, scaleY, scaleY)
            el.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText =
                hudSpriteStyleXY(sprite, sprite.x + capL, sprite.w - capL - capR, scaleXMid, scaleY)
            el.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText =
                hudSpriteStyleXY(sprite, sprite.x + sprite.w - capR, capR, scaleY, scaleY)
            el.appendChild(it)
        }
        return el
    }

    /**
     * Androids have no technique points at all, so their bar is empty rather than full -- a max of
     * zero would otherwise divide to NaN and leave the bar at whatever width it last had.
     */
    /** The pill under the bars. Was written once at construction and never updated again. */
    fun setLevel(level: Int) {
        levelLabel.setText("Lv$level")
    }

    fun setTp(current: Int, max: Int) {
        tpClip.style.width =
            if (max <= 0) "0%" else "${(current.toDouble() / max * 100).coerceIn(0.0, 100.0)}%"
        tpValue.setText("$current/$max")
    }

    fun setHealth(current: Int, max: Int) {
        val fraction = (current.toDouble() / max).coerceIn(0.0, 1.0)
        hpClip.style.width = "${fraction * 100}%"
        hpValue.setText("$current/$max")

        // The real HUD's low-health warning: the fill capsule itself changes to the sheet's
        // orange art, rather than tinting the green one.
        val low = fraction <= HP_ORANGE_FRACTION
        hpFillGreen.style.display = if (low) "none" else "flex"
        hpFillOrange.style.display = if (low) "flex" else "none"

        deathOverlay.style.display = if (current <= 0) "block" else "none"
    }

    /**
     * Photon Blast charge in [0, 1]. The dial's ring fills clockwise from the top and pulses once
     * the blast is ready. Cheap to call every frame: the style is only touched when the shown
     * tenth-of-a-turn actually changes.
     */
    fun setPhotonBlast(fraction: Double) {
        val clamped = fraction.coerceIn(0.0, 1.0)
        val tenths = (clamped * 40).toInt()
        if (tenths == lastPbTenths) return
        lastPbTenths = tenths

        val turn = tenths / 40.0
        pbRingFill.style.background =
            "conic-gradient(#ffb63d 0turn ${turn}turn, transparent ${turn}turn 1turn)"

        if (clamped >= 1.0) pbRingFill.classList.add("pw-hud-ring-full")
        else pbRingFill.classList.remove("pw-hud-ring-full")
    }

    override fun dispose() {
        listeners.forEach { it.dispose() }
        root.remove()
        filterDefs.remove()
        deathOverlay.remove()
        styleTag.remove()
        super.dispose()
    }

    companion object {
        private const val BAR_W = 168.0
        private const val BAR_H = 14.0
        /**
         * The buff strip's tiles, a shade under the palette's so the report reads as smaller
         * than the button that caused it.
         */
        private const val BUFF_ICON_SCALE = 0.34

        private const val TEXT_SIZE = 15
        private const val HEX_SCALE = 1.0

        /** Below this HP fraction the fill swaps to the sheet's orange capsule. */
        private const val HP_ORANGE_FRACTION = 0.3

        private const val STYLESHEET = """
            .pw-hud-root {
              position: fixed;
              top: calc(12px + var(--pw-safe-top));
              left: calc(12px + var(--pw-safe-left));
              z-index: 15;
              pointer-events: none;
              user-select: none;
            }
            .pw-hud-tappable {
              pointer-events: auto;
              touch-action: none;
              cursor: pointer;
            }
            .pw-hud-tappable:active { filter: brightness(1.5); }
            .pw-hud-menu-zone {
              position: absolute;
              top: 0;
              right: 0;
              width: 58px;
              height: 100%;
              border-radius: 0 22px 22px 0;
            }
            .pw-hud-frame {
              position: relative;
              display: flex;
              align-items: center;
              gap: 10px;
              padding: 7px 16px 7px 8px;
              border: 2px solid #6fe4f7;
              border-radius: 22px;
              background:
                repeating-linear-gradient(180deg,
                  rgba(90,200,225,.16) 0px, rgba(90,200,225,.16) 1px,
                  rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px),
                linear-gradient(180deg, #04141f 0%, #020a12 100%);
              box-shadow: 0 0 6px rgba(0,0,0,.75), inset 0 0 10px rgba(0,0,0,.7);
            }
            .pw-hud-badge {
              position: relative;
              flex: 0 0 auto;
              width: 52px;
              height: 44px;
            }
            .pw-hud-ring {
              position: absolute;
              left: 0;
              top: 0;
              width: 44px;
              height: 44px;
              border-radius: 50%;
              border: 5px solid #253742;
              box-shadow: 0 0 0 1px rgba(110,220,245,.55);
              box-sizing: border-box;
            }
            /*
             * The PB charge arc: a conic-gradient disc masked down to the ring's 5px band so it
             * reads as the dial's rim filling, exactly where the ring border sits.
             */
            .pw-hud-ring-fill {
              position: absolute;
              left: 0;
              top: 0;
              width: 44px;
              height: 44px;
              border-radius: 50%;
              -webkit-mask: radial-gradient(closest-side, transparent 74%, #000 76%, #000 98%, transparent 100%);
              mask: radial-gradient(closest-side, transparent 74%, #000 76%, #000 98%, transparent 100%);
            }
            .pw-hud-ring-full {
              animation: pw-hud-pb-pulse 0.5s ease-in-out infinite alternate;
            }
            @keyframes pw-hud-pb-pulse {
              from { filter: brightness(0.9); }
              to   { filter: brightness(1.7); }
            }
            .pw-hud-hex {
              position: absolute;
              left: 9px;
              top: 11px;
            }
            .pw-hud-counter {
              position: absolute;
              left: 32px;
              top: 50%;
              transform: translateY(-50%);
              padding: 1px 5px 1px 7px;
              border-radius: 9px;
              background: #101d26;
              filter: brightness(1.6);
            }
            .pw-hud-rows {
              display: flex;
              flex-direction: column;
              gap: 5px;
            }
            .pw-hud-row-head {
              display: flex;
              justify-content: space-between;
              align-items: center;
              width: ${BAR_W}px;
              margin-bottom: 1px;
              filter: brightness(1.65) drop-shadow(0 1px 1px rgba(0,0,0,.9));
            }
            .pw-hud-bar {
              position: relative;
            }
            .pw-hud-bar-layer {
              position: absolute;
              top: 0;
              left: 0;
            }
            .pw-hud-bar-clip {
              overflow: hidden;
              width: 100%;
              height: 100%;
              transition: width 0.15s ease-out;
            }
            .pw-hud-below {
              display: flex;
              align-items: center;
              gap: 12px;
              margin: -2px 0 0 14px;
            }
            .pw-hud-buffs {
              display: flex;
              align-items: center;
              gap: 5px;
            }
            /* The tiles breathe, so a buff reads as running rather than as a static badge. */
            .pw-hud-buff {
              width: 22px;
              height: 22px;
              animation: pw-hud-buff-pulse 1.5s ease-in-out infinite;
            }
            @keyframes pw-hud-buff-pulse {
              0%, 100% { opacity: 0.72; transform: scale(0.94); }
              50%      { opacity: 1;    transform: scale(1.06); }
            }
            .pw-hud-lv {
              padding: 2px 12px;
              border: 2px solid #6fe4f7;
              border-radius: 12px;
              background: linear-gradient(180deg, #04141f, #020a12);
              filter: brightness(1.5);
            }
            .pw-hud-name {
              filter: brightness(1.75) drop-shadow(0 1px 2px rgba(0,0,0,.95));
            }
            .pw-hud-death-overlay {
              position: fixed;
              top: 50%;
              left: 50%;
              transform: translate(-50%,-50%);
              font: bold 48px sans-serif;
              color: #e53935;
              text-shadow: 0 2px 6px black;
              z-index: 20;
              display: none;
              pointer-events: none;
              user-select: none;
            }
        """
    }
}
