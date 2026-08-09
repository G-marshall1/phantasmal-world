package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.mobileGame.player.CREATABLE_CLASSES
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader
import world.phantasmal.web.viewer.models.CharacterClass
import world.phantasmal.webui.dom.disposableListener

/**
 * The class picker, laid out like the original CHARACTER CREATION screen (per the user's three
 * reference captures, one per profession): the angular white banner top-left, the twelve class
 * rows grouped under vertical HUNTER/RANGER/FORCE tabs on the left, and on the right the
 * selected profession's name plate, group artwork panel, and information text, with OK in the
 * top corner.
 *
 * The row strips, name plates and group artwork are cropped from the user's own captures into
 * the git-ignored `assets/skin/classpicker/` folder (Sega art -- personal build only, never
 * committed); every image element quietly removes itself if its file is missing, leaving the
 * CSS-drawn colored bars and labels underneath, so a clean checkout still gets a functional
 * screen. Chrome and text are this project's own CSS in the original's arrangement.
 *
 * Classes psov2 ships no data for (anything outside [CREATABLE_CLASSES]) keep their slots but
 * render locked and un-selectable, as before.
 *
 * [characterClassAssetLoader]/[createThreeRenderer] are kept for signature compatibility with
 * the shell but unused -- the previous live-rendered 3D thumbnails are replaced by the
 * reference's own 2D presentation.
 */
class ClassPickerScreen(
    container: HTMLElement,
    @Suppress("UNUSED_PARAMETER") characterClassAssetLoader: CharacterClassAssetLoader,
    @Suppress("UNUSED_PARAMETER") createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
    private val onConfirm: (CharacterClass) -> Unit,
    private val onCancel: () -> Unit,
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cp-root"
        container.appendChild(el)
    }

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private var selected: CharacterClass = CharacterClass.HUmar

    /**
     * Two stages, per the reference captures: first the profession is chosen (group art and
     * group info on the right), then OK moves into picking the individual class inside it
     * (class plate, class info and class art on the right). CANCEL steps back out.
     */
    private var pickingClass = false

    private val rowElements = mutableMapOf<CharacterClass, RowParts>()
    private lateinit var cancel: HTMLElement
    private val tabImage: HTMLImageElement
    private val tabLabel: HTMLElement
    private val artImage: HTMLImageElement
    private val classArtImage: HTMLImageElement
    private val infoTab: HTMLElement
    private val infoText: HTMLElement

    private class RowParts(val root: HTMLElement, val image: HTMLImageElement)

    init {
        // Header banner + OK + back.
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-banner"
            el.textContent = "CHARACTER CREATION"
            root.appendChild(el)
        }
        val ok = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-ok"
            el.innerHTML = "<span class='pw-cp-ok-key'>Enter</span> OK"
            root.appendChild(el)
        }
        listeners.add(ok.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            if (pickingClass) {
                onConfirm(selected)
            } else {
                pickingClass = true
                cancel.style.display = "flex"
                select(selected)
            }
        }))
        cancel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-cancel"
            el.innerHTML = "<span class='pw-cp-cancel-key'>Esc</span> CANCEL"
            root.appendChild(el)
        }
        listeners.add(cancel.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            pickingClass = false
            cancel.style.display = "none"
            select(selected)
        }))
        val back = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-back"
            el.textContent = "BACK"
            root.appendChild(el)
        }
        listeners.add(back.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            onCancel()
        }))

        // Left: the three profession groups with their twelve rows.
        val left = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-left"
            root.appendChild(el)
        }

        for (group in GROUPS) {
            val section = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cp-group pw-cp-group-${group.slug}"
                left.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cp-group-tab"
                el.textContent = group.label
                section.appendChild(el)
            }
            val rows = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cp-rows"
                section.appendChild(el)
            }

            for (characterClass in group.classes) {
                val creatable = characterClass in CREATABLE_CLASSES

                val row = (document.createElement("div") as HTMLElement).also { el ->
                    el.className =
                        if (creatable) "pw-cp-row" else "pw-cp-row pw-cp-row-locked"
                    rows.appendChild(el)
                }
                (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-cp-row-label"
                    el.textContent = characterClass.name
                    row.appendChild(el)
                }
                val image = (document.createElement("img") as HTMLImageElement).also { el ->
                    el.className = "pw-cp-row-img"
                    el.onerror = { _, _, _, _, _ -> el.remove() }
                    row.appendChild(el)
                }
                rowElements[characterClass] = RowParts(row, image)

                if (creatable) {
                    // "click" rather than pointerdown: the browser only synthesizes it for a
                    // tap that didn't turn into a scroll, which is what lets the list pan.
                    listeners.add(row.disposableListener<org.w3c.dom.events.MouseEvent>("click", {
                        it.stopPropagation()
                        // While picking an individual class, rows outside the chosen
                        // profession are inert -- CANCEL steps back out to change group.
                        if (!pickingClass || characterClass in GROUPS.first { g -> selected in g.classes }.classes) {
                            select(characterClass)
                        }
                    }))
                }
            }
        }

        // Right: profession plate, artwork panel, information.
        val right = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-right"
            root.appendChild(el)
        }
        val tabWrap = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-tabwrap"
            right.appendChild(el)
        }
        tabLabel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-tab-label"
            tabWrap.appendChild(el)
        }
        tabImage = (document.createElement("img") as HTMLImageElement).also { el ->
            el.className = "pw-cp-tab-img"
            el.onerror = { _, _, _, _, _ -> el.style.display = "none" }
            tabWrap.appendChild(el)
        }

        val panel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-panel"
            right.appendChild(el)
        }
        artImage = (document.createElement("img") as HTMLImageElement).also { el ->
            el.className = "pw-cp-art-img"
            el.onerror = { _, _, _, _, _ -> el.style.display = "none" }
            panel.appendChild(el)
        }
        classArtImage = (document.createElement("img") as HTMLImageElement).also { el ->
            el.className = "pw-cp-class-art-img"
            el.onerror = { _, _, _, _, _ -> el.style.display = "none" }
            panel.appendChild(el)
        }
        infoTab = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-info-tab"
            el.textContent = "information"
            panel.appendChild(el)
        }
        infoText = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cp-info-text"
            panel.appendChild(el)
        }

        select(CharacterClass.HUmar)
    }

    private fun select(characterClass: CharacterClass) {
        selected = characterClass
        val group = GROUPS.first { characterClass in it.classes }

        for ((cc, parts) in rowElements) {
            val inGroup = cc in group.classes
            val state = if (inGroup) "sel" else "dim"
            parts.image.src = "assets/skin/classpicker/row_${cc.name.lowercase()}_$state.png"
            parts.root.classList.toggle(
                "pw-cp-row-current",
                cc == characterClass && (pickingClass || !pickingClass && cc == characterClass),
            )
            parts.root.classList.toggle("pw-cp-row-in-group", inGroup)
        }

        infoTab.style.background = group.color

        if (pickingClass) {
            // Individual class stage: the class's own plate, art and information.
            tabImage.style.display = "none"
            tabLabel.textContent = characterClass.name
            tabLabel.style.background = group.color

            artImage.style.display = "none"
            classArtImage.style.display = ""
            classArtImage.src =
                "assets/skin/classpicker/class_${characterClass.name.lowercase()}.png"

            infoText.textContent =
                raceLine(characterClass) + ". " + (CLASS_INFO[characterClass] ?: "")
        } else {
            // Profession stage: the group's plate, art and information.
            tabLabel.textContent = group.label.lowercase().replaceFirstChar { it.uppercase() }
            tabLabel.style.background = group.color
            tabImage.style.display = ""
            tabImage.src = "assets/skin/classpicker/tab_${group.slug}.png"

            classArtImage.style.display = "none"
            artImage.style.display = ""
            artImage.src = "assets/skin/classpicker/art_${group.slug}.png"
            infoText.textContent = group.description
        }
    }

    /** "Hunter / Human / Male" style line, derived from the class name's own encoding. */
    private fun raceLine(characterClass: CharacterClass): String {
        val profession = GROUPS.first { characterClass in it.classes }
            .label.lowercase().replaceFirstChar { it.uppercase() }
        val n = characterClass.name.lowercase()
        val race = when {
            "cas" in n -> "Android"
            "new" in n -> "Newman"
            else -> "Human"
        }
        val female = n.endsWith("l")
        return "$profession / $race / " + if (female) "Female" else "Male"
    }

    override fun dispose() {
        for (listener in listeners) listener.dispose()
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    private class Group(
        val slug: String,
        val label: String,
        val color: String,
        val classes: List<CharacterClass>,
        val description: String,
    )

    companion object {
        // Descriptions paraphrase the game's own one-line class summaries.
        private val GROUPS = listOf(
            Group(
                "hunter", "HUNTER", "linear-gradient(180deg,#e0512e,#9c2a10)",
                listOf(
                    CharacterClass.HUmar, CharacterClass.HUnewearl,
                    CharacterClass.HUcast, CharacterClass.HUcaseal,
                ),
                "Experts with bladed weapons who can wield most others too. Accuracy runs low " +
                    "but attack power runs high. A good fit for beginners.",
            ),
            Group(
                "ranger", "RANGER", "linear-gradient(180deg,#4fc242,#1f7a18)",
                listOf(
                    CharacterClass.RAmar, CharacterClass.RAmarl,
                    CharacterClass.RAcast, CharacterClass.RAcaseal,
                ),
                "Gun specialists whose excellent accuracy lands hits from a distance, traded " +
                    "against lower attack power. A good fit for mid-level players.",
            ),
            Group(
                "force", "FORCE", "linear-gradient(180deg,#5a6ae8,#2733a8)",
                listOf(
                    CharacterClass.FOmar, CharacterClass.FOmarl,
                    CharacterClass.FOnewm, CharacterClass.FOnewearl,
                ),
                "Technique specialists and excellent support. Powerful abilities offset their " +
                    "low HP. A good fit for advanced players.",
            ),
        )

        /** Brief per-class summaries in this project's own words. */
        private val CLASS_INFO: Map<CharacterClass, String> = mapOf(
            CharacterClass.HUmar to
                "A close-range fighter with the most balanced growth, plus some recovery and " +
                "attack techniques.",
            CharacterClass.HUnewearl to
                "Adept up close and carries the strongest techniques of any Hunter, at the " +
                "cost of the lowest HP in the class.",
            CharacterClass.HUcast to
                "A pure melee machine with the highest attack power potential of any class -- " +
                "but no techniques at all.",
            CharacterClass.HUcaseal to
                "A close-combat expert whose reflexes give her outstanding accuracy for a " +
                "Hunter. No techniques.",
            CharacterClass.RAmar to
                "A ranged-combat all-rounder with the highest accuracy potential, plus some " +
                "recovery and attack techniques.",
            CharacterClass.RAmarl to
                "Low HP for a Ranger, offset by high mental strength and strong support " +
                "techniques.",
            CharacterClass.RAcast to
                "The heaviest-hitting Ranger, trading techniques away entirely for raw " +
                "firepower.",
            CharacterClass.RAcaseal to
                "A precise machine markswoman; durable and accurate, with no techniques.",
            CharacterClass.FOmar to
                "Low defence and HP make him demanding to play, but his technique mastery " +
                "rewards it.",
            CharacterClass.FOmarl to
                "Excels at support techniques while staying more capable with weapons than " +
                "other Forces.",
            CharacterClass.FOnewm to
                "A well-balanced Force who stands out across many attack techniques.",
            CharacterClass.FOnewearl to
                "Limited with weapons but supreme in techniques, attack and support alike.",
        )

        private const val STYLESHEET = """
            .pw-cp-root {
              position: fixed;
              inset: 0;
              z-index: 30;
              overflow: hidden;
              background:
                linear-gradient(90deg, rgba(12,60,66,.85) 0%, rgba(12,60,66,.85) 46%,
                  rgba(2,6,8,.92) 54%, rgba(2,6,8,.92) 100%),
                repeating-linear-gradient(60deg, rgba(60,140,150,.12) 0 2px, rgba(0,0,0,0) 2px 26px),
                repeating-linear-gradient(-60deg, rgba(60,140,150,.12) 0 2px, rgba(0,0,0,0) 2px 26px),
                #041416;
              display: grid;
              grid-template-columns: minmax(300px, 46%) 1fr;
              grid-template-rows: 52px 1fr;
              gap: 4px 14px;
              padding: calc(8px + var(--pw-safe-top)) calc(16px + var(--pw-safe-right))
                       calc(10px + var(--pw-safe-bottom)) calc(16px + var(--pw-safe-left));
              box-sizing: border-box;
              touch-action: none;
              user-select: none;
            }
            .pw-cp-banner {
              grid-column: 1;
              align-self: center;
              justify-self: start;
              padding: 6px 26px 6px 14px;
              clip-path: polygon(0 0, 100% 0, calc(100% - 16px) 100%, 0 100%);
              background: linear-gradient(180deg, #f4f4f4, #b9bcc4 55%, #888e9a);
              font: bold 19px sans-serif;
              letter-spacing: 1px;
              color: #16181e;
              text-shadow: 0 1px 0 rgba(255,255,255,.6);
            }
            .pw-cp-ok {
              position: absolute;
              top: calc(10px + var(--pw-safe-top));
              right: calc(18px + var(--pw-safe-right));
              display: flex;
              align-items: center;
              gap: 8px;
              font: bold 20px sans-serif;
              color: #f6f6f6;
              text-shadow: 0 2px 3px black;
              cursor: pointer;
              touch-action: none;
              z-index: 2;
            }
            .pw-cp-ok:active { filter: brightness(1.5); }
            .pw-cp-cancel {
              position: absolute;
              top: calc(10px + var(--pw-safe-top));
              right: calc(140px + var(--pw-safe-right));
              display: none;
              align-items: center;
              gap: 8px;
              font: bold 20px sans-serif;
              color: #f6f6f6;
              text-shadow: 0 2px 3px black;
              cursor: pointer;
              touch-action: none;
              z-index: 2;
            }
            .pw-cp-cancel:active { filter: brightness(1.5); }
            .pw-cp-cancel-key {
              padding: 3px 10px;
              border-radius: 5px;
              background: linear-gradient(180deg, #3a6ae8, #10309c);
              border: 1px solid rgba(170,200,255,.7);
              font: bold 13px sans-serif;
              color: #e2ecff;
            }
            .pw-cp-ok-key {
              padding: 3px 10px;
              border-radius: 5px;
              background: linear-gradient(180deg, #e8503a, #9c1808);
              border: 1px solid rgba(255,190,170,.7);
              font: bold 13px sans-serif;
              color: #ffe9e2;
            }
            .pw-cp-back {
              position: absolute;
              bottom: calc(8px + var(--pw-safe-bottom));
              left: calc(18px + var(--pw-safe-left));
              font: bold 13px sans-serif;
              letter-spacing: 2px;
              color: #9fc7cc;
              text-shadow: 0 1px 2px black;
              cursor: pointer;
              touch-action: none;
              padding: 6px 10px;
              z-index: 2;
            }
            /* --- left column --- */
            /*
             * pan-y so the list actually scrolls under a thumb -- the root's touch-action:none
             * would otherwise swallow the gesture and strand Force off-screen. Row taps use
             * click events, which the browser only synthesizes for non-scroll taps.
             */
            .pw-cp-left {
              grid-column: 1;
              grid-row: 2;
              display: flex;
              flex-direction: column;
              gap: 6px;
              overflow-y: auto;
              min-height: 0;
              touch-action: pan-y;
              -webkit-overflow-scrolling: touch;
            }
            .pw-cp-group { display: flex; }
            .pw-cp-group-tab {
              flex: 0 0 22px;
              writing-mode: vertical-rl;
              transform: rotate(180deg);
              display: flex;
              align-items: center;
              justify-content: center;
              font: bold 12px sans-serif;
              letter-spacing: 3px;
              color: rgba(255,255,255,.85);
              text-shadow: 0 1px 2px black;
              border: 1px solid rgba(0,0,0,.5);
            }
            .pw-cp-group-hunter .pw-cp-group-tab { background: linear-gradient(0deg,#8e2610,#5e1608); }
            .pw-cp-group-ranger .pw-cp-group-tab { background: linear-gradient(0deg,#2a7a1c,#1a4e10); }
            .pw-cp-group-force  .pw-cp-group-tab { background: linear-gradient(0deg,#2b37a0,#1a2268); }
            .pw-cp-rows {
              flex: 1;
              display: flex;
              flex-direction: column;
            }
            .pw-cp-row {
              position: relative;
              flex: 0 0 34px;
              height: 34px;
              border: 1px solid rgba(0,0,0,.55);
              cursor: pointer;
              touch-action: pan-y;
              overflow: hidden;
            }
            .pw-cp-group-hunter .pw-cp-row { background: linear-gradient(180deg,#a5391c,#701f0c); }
            .pw-cp-group-ranger .pw-cp-row { background: linear-gradient(180deg,#2f8a20,#1d5a12); }
            .pw-cp-group-force  .pw-cp-row { background: linear-gradient(180deg,#3743b4,#232c7e); }
            .pw-cp-row-label {
              position: absolute;
              left: 8px;
              top: 50%;
              transform: translateY(-50%);
              font: bold 14px sans-serif;
              color: #f4f4f4;
              text-shadow: 0 1px 2px black;
            }
            .pw-cp-row-img {
              position: absolute;
              inset: 0;
              width: 100%;
              height: 100%;
              object-fit: fill;
              image-rendering: pixelated;
              pointer-events: none;
            }
            .pw-cp-row-current { outline: 2px solid #ffffff; outline-offset: -2px; z-index: 1; }
            .pw-cp-row:not(.pw-cp-row-in-group) { filter: brightness(0.75); }
            .pw-cp-row-locked { filter: grayscale(0.7) brightness(0.5) !important; cursor: default; }
            /* --- right column --- */
            .pw-cp-right {
              grid-column: 2;
              grid-row: 2;
              display: flex;
              flex-direction: column;
              min-height: 0;
            }
            .pw-cp-tabwrap {
              position: relative;
              height: 34px;
              margin-bottom: 6px;
              flex: 0 0 auto;
            }
            .pw-cp-tab-label {
              position: absolute;
              left: 0;
              top: 0;
              padding: 5px 34px 6px 22px;
              clip-path: polygon(8px 0, 100% 0, calc(100% - 12px) 100%, 0 100%);
              border: 1px solid rgba(255,255,255,.7);
              font: bold 16px sans-serif;
              color: #fff;
              text-shadow: 0 1px 2px black;
            }
            .pw-cp-tab-img {
              position: absolute;
              left: 0;
              top: 0;
              height: 34px;
              image-rendering: pixelated;
            }
            .pw-cp-panel {
              position: relative;
              flex: 1;
              min-height: 0;
              border: 2px solid rgba(120,230,220,.8);
              border-radius: 10px;
              background:
                repeating-linear-gradient(0deg, rgba(90,200,210,.10) 0 1px, rgba(0,0,0,0) 1px 7px),
                repeating-linear-gradient(90deg, rgba(90,200,210,.10) 0 1px, rgba(0,0,0,0) 1px 7px),
                linear-gradient(180deg, #0a3b40, #06272c);
              box-shadow: inset 0 0 16px rgba(0,0,0,.65);
              display: flex;
              flex-direction: column;
              overflow: hidden;
            }
            .pw-cp-class-art-img {
              position: absolute;
              top: 6px;
              right: 8px;
              height: calc(100% - 12px);
              max-width: 46%;
              object-fit: contain;
              object-position: right center;
              image-rendering: pixelated;
              display: none;
            }
            .pw-cp-art-img {
              position: absolute;
              top: 6px;
              left: 6px;
              width: calc(100% - 12px);
              height: 58%;
              object-fit: contain;
              object-position: center top;
              image-rendering: pixelated;
            }
            .pw-cp-info-tab {
              position: relative;
              align-self: flex-start;
              margin: auto 0 0 10px;
              padding: 2px 22px 3px 12px;
              clip-path: polygon(0 0, 100% 0, calc(100% - 10px) 100%, 0 100%);
              border: 1px solid rgba(255,255,255,.7);
              font: bold 13px sans-serif;
              color: #fff;
              text-shadow: 0 1px 2px black;
            }
            .pw-cp-info-text {
              position: relative;
              flex: 0 0 auto;
              padding: 8px 14px 12px;
              font: 15px monospace;
              line-height: 1.5;
              color: #f2fbfb;
              text-shadow: 0 1px 2px black;
              min-height: 80px;
            }
        """
    }
}
