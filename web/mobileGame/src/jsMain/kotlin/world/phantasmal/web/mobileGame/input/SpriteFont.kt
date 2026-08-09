package world.phantasmal.web.mobileGame.input

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Renders text using the PSOGC HiRes HUD Font (a Dolphin-emulator texture-replacement resource
 * pack for real PSO GameCube, by eleriaqueen -- https://github.com/eleriaqueen/pso-highres-hud-font-resource-pack,
 * CC BY-NC-SA 4.0) instead of a system sans-serif font, for HUD text that should look like the
 * genuine game's font rather than a browser default.
 *
 * The pack ships 3 DDS textures; only one is used here (`pso-hud-font.png`, converted from the
 * third DDS in `psogc.hr.hud.font.v0.5.0/textures/GPO/`) -- a monospace glyph atlas covering
 * printable ASCII (space..~, 0x20-0x7E) in a 16-column x 6-row grid of 64x64 cells, starting at
 * pixel (0,128); grid position for a character is `(code - 32) % 16, (code - 32) / 16`. The other
 * two DDS textures are pre-rendered English system-menu labels ("Item Pack", "Customize", "Chat",
 * ...) keyed to specific in-game menu screens this project doesn't implement, so they aren't used.
 */
private const val FONT_SHEET_URL = "/assets/hud/pso-hud-font.png"
private const val SHEET_SIZE = 1024
private const val CELL_SIZE = 64
private const val GRID_ORIGIN_Y = 128
private const val COLUMNS = 16
private const val FIRST_CHAR = 32
private const val LAST_CHAR = 32 + 16 * 6 - 1

/**
 * A monospace, sprite-font-rendered text label. [displaySize] is the rendered width/height of one
 * character cell in px (the 64x64 source glyphs are scaled down to this via `background-size`).
 */
class SpriteLabel(container: HTMLElement, private val displaySize: Int) {
    val el: HTMLElement = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText = "display:flex;"
        container.appendChild(el)
    }

    private var currentText: String? = null

    fun setText(text: String) {
        if (text == currentText) return
        currentText = text

        el.innerHTML = ""

        val scale = displaySize.toDouble() / CELL_SIZE
        val scaledSheetSize = SHEET_SIZE * scale

        for (char in text) {
            val code = char.code
            val glyph = document.createElement("div") as HTMLElement

            if (code in FIRST_CHAR..LAST_CHAR) {
                val index = code - FIRST_CHAR
                val col = index % COLUMNS
                val row = index / COLUMNS
                val x = col * CELL_SIZE * scale
                val y = (GRID_ORIGIN_Y + row * CELL_SIZE) * scale

                glyph.style.cssText =
                    "flex:0 0 auto;width:${displaySize}px;height:${displaySize}px;" +
                        "background-image:url($FONT_SHEET_URL);" +
                        "background-position:-${x}px -${y}px;" +
                        "background-size:${scaledSheetSize}px ${scaledSheetSize}px;" +
                        "background-repeat:no-repeat;"
            } else {
                // Outside the atlas's covered range (e.g. non-ASCII in a player-entered name) --
                // leave a blank monospace-width cell rather than silently dropping the character
                // and throwing off spacing for the rest of the string.
                glyph.style.cssText = "flex:0 0 auto;width:${displaySize}px;height:${displaySize}px;"
            }

            el.appendChild(glyph)
        }
    }
}
