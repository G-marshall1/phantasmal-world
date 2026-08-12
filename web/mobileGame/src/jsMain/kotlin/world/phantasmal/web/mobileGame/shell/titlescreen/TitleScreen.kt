package world.phantasmal.web.mobileGame.shell.titlescreen

import kotlin.math.max
import kotlin.random.Random
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * Full-screen title card recreating PSO's title screen. Ported from the reference project
 * "pso-title-screen-master" (a Vue3 + Two.js app whose own README calls itself "incredibly
 * laggy"), reading its actual source to fix the specific causes rather than just copying the
 * look:
 *
 * - **Sigil**: the reference's rotating emblem group has an SVG filter
 *   (`feFlood`/`feComposite`/`feMorphology`/`feGaussianBlur`/`feMerge`) applied directly to it for
 *   a glow. Since the group rotates continuously, the browser has to recompute that blur+dilate
 *   every single frame -- the reference's own source comment: `// wish this wasnt so laggy...`.
 *   [TitleScreenAssets.SIGIL_SVG_INNER] already has that filter stripped; the rotation itself
 *   (three `transform: rotate()` `@keyframes`, all GPU-composited) is kept as-is, it was never
 *   the problem.
 * - **StarStream + twinkle**: the reference simulates ~300 particles per frame in JS, drawn to a
 *   CPU Canvas2D context (`Two.Types.canvas`) with an expensive `globalCompositeOperation =
 *   'screen'` blend, then blurs the whole canvas with a CSS filter on top every frame. Replaced
 *   here with the reference's own (unused-by-default) CSS-only technique from its `/js-free`
 *   route: plain `<div>`s, each given a randomized-once `transform`/`animation-delay`/
 *   `animation-duration`, driven entirely by shared `@keyframes` -- zero JS per frame. One
 *   refinement beyond what that route does: each particle's color is fixed once rather than
 *   interpolated through keyframe stops (animating `background-color` forces a repaint every
 *   frame; a fixed color plus `opacity`/`transform` easing is composite-only).
 * - **Whole-page blur**: the reference blurs its *entire* composited page every frame (again,
 *   with its own comment: `// wish this wasn't laggy either`), which is the single most expensive
 *   thing a page like this can do since everything animating underneath has to be re-blurred each
 *   frame. Not ported at all -- the vignette (a static inset `box-shadow`, kept below) supplies
 *   plenty of depth on its own.
 * - **hexgrid2's "ping" mask**: the reference animates the mask circle's `r` *attribute*, which
 *   forces SVG layout recalculation every frame. Ported as `transform: scale()` instead (see
 *   [hexPingLayer]), same visual growth curve, GPU-composited.
 *
 * Tapping anywhere goes straight to character select ([onContinue]), the way the original
 * client's title flows into its character screen -- the old in-place New Game/Continue/Settings/
 * Exit menu was cut by request; its layer code remains but nothing shows it, and [onNewGame]
 * (the nickname/login path) is kept wired for the day a session concept needs it.
 */
class TitleScreen(
    container: HTMLElement,
    private val onNewGame: () -> Unit,
    private val onContinue: () -> Unit,
) : TrackedDisposable() {
    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText = ROOT_STYLE
        container.appendChild(el)
    }

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val listeners = mutableListOf<Disposable>()

    private val actionText = actionTextLayer()
    private val menu = menuLayer()
    private val settingsNotice = settingsNoticeLayer()

    init {
        root.appendChild(hexBlurredLayer())
        root.appendChild(bgTextLayer())
        root.appendChild(hexPingLayer())
        root.appendChild(sigilLayer())
        root.appendChild(orbsLayer())
        root.appendChild(titleLayer())
        root.appendChild(twinkleLayer())
        root.appendChild(starStreamLayer())
        root.appendChild(actionText)
        root.appendChild(menu)
        root.appendChild(settingsNotice)
        root.appendChild(scanLinesLayer())
        root.appendChild(buildStampLayer())

        // Tap anywhere on screen to continue straight to character select -- the original
        // client's own flow off its title.
        listeners.add(root.disposableListener<PointerEvent>("pointerdown", { onContinue() }))
    }

    private var menuOpen = false

    private fun showMenu() {
        menuOpen = true
        actionText.style.display = "none"
        menu.style.display = "flex"
    }

    private fun hideMenu() {
        menuOpen = false
        settingsNotice.style.display = "none"
        menu.style.display = "none"
        actionText.style.display = "block"
    }

    override fun dispose() {
        for (listener in listeners) listener.dispose()
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    // ---- Layers, bottom to top ----

    private fun hexBlurredLayer(): HTMLElement =
        div("pw-title-hex1").apply { innerHTML = hexSvg() }

    private fun bgTextLayer(): HTMLElement {
        val el = div("pw-title-bgtext")
        el.appendChild(bgTextColumn("pw-title-bgtext-left"))
        el.appendChild(bgTextColumn("pw-title-bgtext-right"))
        return el
    }

    private fun bgTextColumn(className: String): HTMLElement {
        val col = div(className)
        repeat(BG_TEXT_WORDS_PER_COLUMN) {
            val p = document.createElement("p") as HTMLElement
            p.textContent = genBgWord()
            col.appendChild(p)
        }
        return col
    }

    /** See this class's own doc comment for why this animates `transform`, not the `r` attribute. */
    private fun hexPingLayer(): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.className = "pw-title-hex2"
        el.innerHTML = """
            <svg viewBox="0 0 8465 8477" class="pw-title-hex2-outer">
              <mask id="pw-title-hex-mask">
                <circle cx="50%" cy="50%" r="100%" stroke-width="8%" stroke="white" fill="black" class="pw-title-hex-ping"/>
              </mask>
              <svg viewBox="0 0 8465 8477" width="140vw" class="pw-title-hex-svg" mask="url(#pw-title-hex-mask)">
                $HEX_GRID_SVG_INNER
              </svg>
            </svg>
        """.trimIndent()
        return el
    }

    private fun sigilLayer(): HTMLElement =
        div("pw-title-sigil").apply {
            innerHTML = """<svg viewBox="0 0 295.00 295.00" width="120vw" class="pw-hex-svg">$SIGIL_SVG_INNER</svg>"""
        }

    private fun orbsLayer(): HTMLElement =
        div("pw-title-orbs").apply {
            innerHTML = """<svg viewBox="0 0 282.52 209.17">$ORBS_SVG_INNER</svg>"""
        }

    private fun titleLayer(): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.className = "pw-title-wrapper"
        el.innerHTML = """
            <div class="pw-title-blurred-wrapper">
              <h1 class="pw-title-blurred">PHANTASY STAR<br>ONLINE</h1>
            </div>
            <h1 class="pw-title-still">PHANTASY STAR<br>ONLINE</h1>
        """.trimIndent()
        return el
    }

    private fun twinkleLayer(): HTMLElement {
        val el = div("pw-title-twinkle")
        repeat(TWINKLE_RAY_COUNT) { i -> el.appendChild(twinkleRay(i, TWINKLE_RAY_COUNT)) }
        return el
    }

    private fun starStreamLayer(): HTMLElement {
        val el = div("pw-title-starstream")
        repeat(STREAM_PARTICLE_COUNT) { i -> el.appendChild(streamParticle(i)) }
        repeat(STREAM_STATIC_COUNT) { staticParticle().let(el::appendChild) }
        return el
    }

    private fun actionTextLayer(): HTMLElement {
        val el = div("pw-title-action")
        val p = document.createElement("p") as HTMLElement
        p.textContent = "TAP TO START"
        el.appendChild(p)
        return el
    }

    /**
     * The deployed bundle's stamp, bottom corner of the title. The tester's phone gives no
     * other way to tell whether an Xcode run actually picked up a fresh `cap copy` -- a stale
     * app looks exactly like "the fix did nothing". Bump [BUILD_STAMP] on every deploy.
     */
    private fun buildStampLayer(): HTMLElement {
        val el = div("pw-title-build")
        el.textContent = BUILD_STAMP
        return el
    }

    private fun menuLayer(): HTMLElement {
        val el = div("pw-title-menu")
        el.appendChild(menuButton("New Game") { onNewGame() })
        el.appendChild(menuButton("Continue") { onContinue() })
        el.appendChild(menuButton("Settings") { showSettingsNotice() })
        el.appendChild(menuButton("Exit") { hideMenu() })
        return el
    }

    private fun menuButton(label: String, onClick: () -> Unit): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.className = "pw-title-menu-button"
        el.textContent = label
        listeners.add(el.disposableListener<PointerEvent>("pointerdown", { e ->
            e.stopPropagation()
            onClick()
        }))
        return el
    }

    private fun settingsNoticeLayer(): HTMLElement {
        val el = div("pw-title-settings-notice")
        el.textContent = "Settings -- coming soon"
        listeners.add(el.disposableListener<PointerEvent>("pointerdown", { e ->
            e.stopPropagation()
            el.style.display = "none"
        }))
        return el
    }

    private fun showSettingsNotice() {
        settingsNotice.style.display = "flex"
    }

    private fun scanLinesLayer(): HTMLElement {
        val img = document.createElement("img") as HTMLElement
        img.className = "pw-title-scanlines"
        img.setAttribute("src", "/assets/title/scanlines.png")
        return img
    }

    // ---- Particle generation -- the runtime equivalent of the reference's Sass @for + random()
    // compile-time particle generation, done here at construction time instead. ----

    private fun twinkleRay(index: Int, total: Int): HTMLElement {
        val rotationDeg = index.toDouble() / total * 360.0
        val length = max(Random.nextDouble() * 40.0, 13.0)
        val translateY = -(length / 2.0) - Random.nextDouble() * 3.0
        val hue = 190.0 + Random.nextDouble() * 65.0
        val delay = Random.nextDouble() * 3.0
        return (document.createElement("div") as HTMLElement).apply {
            className = "pw-title-ray"
            style.cssText =
                "height:${length}vw;" +
                    "background-color:hsl($hue,100%,87%);" +
                    "transform:rotate(${rotationDeg}deg) translateY(${translateY}vw);" +
                    "animation-delay:${delay}s;"
        }
    }

    private fun streamParticle(index: Int): HTMLElement {
        val r = max(Random.nextDouble() * 4.0, 3.0)
        val translateY = randomSign() * Random.nextDouble() * r * 0.2
        val translateX = randomSign() * Random.nextDouble() * 8.0
        val duration = max(Random.nextDouble() * 30.0, 15.0)
        val delay = Random.nextDouble() * 15.0 - 2.0
        val lightness = (100.0 * r / 4.0).coerceIn(0.0, 100.0)
        val animName = if (index % 2 == 0) "pw-title-stream-even" else "pw-title-stream-odd"
        return (document.createElement("div") as HTMLElement).apply {
            className = "pw-title-particle"
            style.cssText =
                "height:${r}vw;width:${r}vw;" +
                    "background-color:hsl(206,100%,${lightness}%);" +
                    "transform:translateY(${translateY}vw) translateX(${translateX}vw);" +
                    "animation:$animName ${duration}s linear infinite ${delay}s;"
        }
    }

    private fun staticParticle(): HTMLElement {
        val marginTop = randomSign() * Random.nextDouble() * STATIC_PARTICLE_SIZE_VW * 0.3
        val translateX = randomSign() * Random.nextDouble() * 16.0
        return (document.createElement("div") as HTMLElement).apply {
            className = "pw-title-particle"
            style.cssText =
                "height:${STATIC_PARTICLE_SIZE_VW}vw;width:${STATIC_PARTICLE_SIZE_VW}vw;" +
                    "background-color:hsl(206,100%,100%);" +
                    "margin-top:${marginTop}vw;transform:translateX(${translateX}vw);"
        }
    }

    private fun genBgWord(): String {
        val len = max((Random.nextDouble() * 30).toInt(), 5)
        return buildString { repeat(len + 1) { append(BG_TEXT_CHARSET.random()) } }
    }

    private fun div(className: String): HTMLElement =
        (document.createElement("div") as HTMLElement).also { it.className = className }

    private fun randomSign(): Int = if (Random.nextBoolean()) 1 else -1

    private fun hexSvg(): String =
        """<svg viewBox="0 0 8465 8477" width="140vw" class="pw-hex-svg">$HEX_GRID_SVG_INNER</svg>"""

    companion object {
        /** Deploy fingerprint shown on the title screen; bump on every `cap copy ios`. */
        private const val BUILD_STAMP = "BUILD 0812-I"

        private const val BG_TEXT_CHARSET = " abcdefghijklmnopqrstuvwxyz0123456789 "
        private const val BG_TEXT_WORDS_PER_COLUMN = 50
        private const val TWINKLE_RAY_COUNT = 36
        private const val STREAM_PARTICLE_COUNT = 200
        private const val STREAM_STATIC_COUNT = 21
        private const val STATIC_PARTICLE_SIZE_VW = 3.3

        private const val ROOT_STYLE =
            "position:fixed;inset:0;overflow:hidden;z-index:30;touch-action:none;user-select:none;" +
            "padding:var(--pw-safe-top) var(--pw-safe-right) var(--pw-safe-bottom) var(--pw-safe-left);" +
            "box-sizing:border-box;" +
                "display:flex;align-items:center;justify-content:center;" +
                "background-image:linear-gradient(148deg,#000c38 0%,#000 83%,#000 100%);" +
                "box-shadow:0 0 12vw #000 inset,0 0 12vw rgba(0,5,87,.8) inset;"

        private val STYLESHEET = """
            @font-face {
              font-family: 'PWTitleFont';
              src: url(/assets/title/pso_font.TTF) format('truetype');
            }

            .pw-hex-svg { overflow: visible; transform: scale(.5); }

            .pw-title-hex1 {
              position: absolute;
              filter: blur(.35rem);
              opacity: .75;
              transform: translateX(-6vw);
              z-index: 1;
            }

            .pw-title-bgtext {
              position: absolute;
              inset: 0;
              font-family: 'PWTitleFont', sans-serif;
              display: grid;
              grid-template-columns: 50% 50%;
              opacity: .45;
              z-index: 1;
              pointer-events: none;
            }
            .pw-title-bgtext-left, .pw-title-bgtext-right { padding: 0 1vw; }
            .pw-title-bgtext p { margin: 0; font-size: 1.3vw; color: #fff; white-space: nowrap; }
            .pw-title-bgtext-left { text-align: left; }
            .pw-title-bgtext-right { text-align: right; }

            .pw-title-hex2 { position: absolute; z-index: 3; }
            .pw-title-hex2-outer { overflow: visible; transform: scale(.5); width: 140vw; }
            .pw-title-hex-svg { stroke: rgba(255,255,255,.5); fill: none; }
            .pw-title-hex-ping {
              transform-box: fill-box;
              transform-origin: 50% 50%;
              transform: scale(0);
              animation: pw-title-hex-ping 13.3s linear infinite;
            }
            @keyframes pw-title-hex-ping {
              0% { transform: scale(0); opacity: 1; }
              2% { transform: scale(.2); }
              3% { transform: scale(.3); }
              6% { transform: scale(.5); opacity: .35; }
              9% { transform: scale(1); opacity: 0; }
              100% { transform: scale(1); opacity: 0; }
            }

            .pw-title-sigil {
              position: absolute;
              z-index: 4;
              opacity: .7;
              animation: pw-title-sigil-flicker 13.3s linear infinite;
            }
            @keyframes pw-title-sigil-flicker {
              0% { fill: rgba(183,224,255,.95); opacity: .7; }
              2% { fill: rgba(255,255,218,.76); }
              6% { fill: rgb(255,255,255); }
              10% { fill: rgba(183,224,255,.95); opacity: .7; }
              100% { fill: rgba(183,224,255,.95); }
            }
            .pw-title-sigil .main-frame {
              transform-origin: 49.5% 45%;
              animation: pw-title-sigil-rot 40s linear infinite;
            }
            .pw-title-sigil .minor-circle {
              transform-origin: 49.5% 45%;
              animation: pw-title-sigil-rot 40s linear infinite;
            }
            .pw-title-sigil .minor-circle g {
              animation: pw-title-sigil-rot 40s linear infinite;
            }
            .pw-title-sigil .rot-text {
              transform-origin: 49.5% 45%;
              animation: pw-title-sigil-rot-text 40s linear infinite;
            }
            @keyframes pw-title-sigil-rot {
              0% { transform: rotate(0deg); }
              100% { transform: rotate(-360deg); }
            }
            @keyframes pw-title-sigil-rot-text {
              0% { transform: rotate(0deg); }
              100% { transform: rotate(360deg); }
            }

            .pw-title-orbs {
              position: absolute;
              z-index: 5;
              width: 45vw;
              transform: translate(1vw,-3.75vw);
            }
            .pw-title-orbs svg { width: 100%; overflow: visible; }
            .pw-title-orbs .all { filter: url(#white-glow) url(#glow); }

            .pw-title-wrapper {
              position: relative;
              z-index: 6;
              font-size: 3vw;
              transform: scale(1.4,1);
              text-align: center;
              display: flex;
              justify-content: center;
              margin-top: -6vw;
              width: 100%;
            }
            .pw-title-blurred-wrapper {
              position: absolute;
              inset: 0;
              filter: blur(.6vw);
            }
            .pw-title-blurred {
              margin: 0;
              color: transparent;
              transform: scaleX(1.12);
              background: linear-gradient(to right,rgba(0,149,255,1),rgba(0,149,255,.5),rgba(0,149,255,1));
              background-clip: text;
              -webkit-background-clip: text;
              -webkit-text-fill-color: transparent;
            }
            .pw-title-still {
              position: relative;
              margin: 0;
              font-family: 'Times New Roman', Times, serif;
              font-weight: lighter;
              color: #252525;
              opacity: .9;
              -webkit-text-stroke: .1vw rgba(255,255,255,.271);
              text-shadow: 0 0 .3vw #fff;
              filter: drop-shadow(0 0 .14vw rgba(250,250,255,1))
                      drop-shadow(0 0 .14vw rgba(250,250,255,.8))
                      drop-shadow(0 0 .14vw rgba(250,250,255,.8));
            }

            .pw-title-twinkle {
              position: absolute;
              inset: 0;
              margin-top: -6vw;
              z-index: 2;
              filter: blur(.12vw);
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .pw-title-ray {
              position: absolute;
              width: 1.4vw;
              opacity: 0;
              clip-path: polygon(41% 68%,45% 90%,51% 100%,57% 90%,61% 68%,62% 55%,51% 0,40% 56%);
              animation-name: pw-title-twinkle-flash;
              animation-duration: 3s;
              animation-iteration-count: infinite;
              animation-timing-function: linear;
            }
            @keyframes pw-title-twinkle-flash {
              0% { opacity: 0; }
              20% { opacity: 1; }
              60% { opacity: 0; }
              100% { opacity: 0; }
            }

            .pw-title-starstream {
              position: absolute;
              inset: 0;
              margin-top: -6vw;
              z-index: 7;
              filter: blur(.2vw);
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .pw-title-particle {
              position: absolute;
              border-radius: 50%;
              mix-blend-mode: screen;
            }
            @keyframes pw-title-stream-even {
              0% { opacity: 1; }
              100% { transform: translate(55vw,0) scale(.1); opacity: .15; }
            }
            @keyframes pw-title-stream-odd {
              0% { opacity: 1; }
              100% { transform: translate(-55vw,0) scale(.1); opacity: .15; }
            }

            .pw-title-build {
              position: absolute;
              z-index: 9;
              right: calc(8px + var(--pw-safe-right));
              bottom: calc(6px + var(--pw-safe-bottom));
              color: rgba(200, 230, 255, 0.55);
              font-size: 10px;
              letter-spacing: 2px;
            }
            .pw-title-action {
              position: absolute;
              z-index: 9;
              transform: translateY(20vw);
              animation: pw-title-action-pulse 1.4s ease-in-out infinite;
            }
            .pw-title-action p {
              margin: 0;
              color: #000;
              font: bold 16px sans-serif;
              letter-spacing: 1px;
              text-shadow: 0 0 .2vw #fff,0 0 .2vw #fff;
              filter: drop-shadow(0 0 .1vw #fff) drop-shadow(0 0 .1vw #fff);
            }
            @keyframes pw-title-action-pulse {
              0%, 100% { opacity: .15; }
              50% { opacity: .85; }
            }

            .pw-title-menu {
              display: none;
              position: absolute;
              z-index: 9;
              transform: translateY(14vw);
              flex-direction: column;
              align-items: center;
              gap: 10px;
            }
            .pw-title-menu-button {
              width: 46vw;
              max-width: 260px;
              padding: 10px 0;
              border-radius: 20px;
              text-align: center;
              background: rgba(0,10,30,.55);
              border: 1px solid rgba(255,255,255,.4);
              color: white;
              font: bold 15px sans-serif;
              letter-spacing: 1px;
              text-shadow: 0 0 .3vw rgba(140,200,255,.9);
              touch-action: none;
              user-select: none;
            }

            .pw-title-settings-notice {
              display: none;
              position: absolute;
              z-index: 11;
              align-items: center;
              justify-content: center;
              padding: 14px 26px;
              border-radius: 12px;
              background: rgba(0,10,30,.85);
              border: 1px solid rgba(255,255,255,.5);
              color: white;
              font: bold 14px sans-serif;
              text-shadow: 0 1px 3px black;
              touch-action: none;
              user-select: none;
            }

            .pw-title-scanlines {
              position: absolute;
              inset: 0;
              width: 100%;
              height: 100%;
              z-index: 10;
              opacity: .5;
              mix-blend-mode: overlay;
              min-width: 940px;
              min-height: 600px;
              pointer-events: none;
            }
        """.trimIndent()
    }
}
