package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.js.Date
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.core.boundingSphere
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.core.rendering.RenderContext
import world.phantasmal.web.externals.three.Clock
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.PerspectiveCamera
import world.phantasmal.web.mobileGame.persistence.CharacterSave
import world.phantasmal.web.mobileGame.persistence.SavedWeapon
import world.phantasmal.web.mobileGame.player.starterWeaponSlug
import world.phantasmal.web.mobileGame.input.HudSprites
import world.phantasmal.web.mobileGame.input.hudSpriteStyleXY
import world.phantasmal.web.mobileGame.player.PLAYER_APPEARANCE_OPTIONS
import world.phantasmal.web.mobileGame.player.SECTION_ID_COLORS
import world.phantasmal.web.mobileGame.player.computeSectionId
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.mobileGame.player.PlayerAssetLoader
import world.phantasmal.web.mobileGame.shell.titlescreen.HEX_GRID_SVG_INNER
import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader
import world.phantasmal.web.viewer.models.CharacterClass
import world.phantasmal.webui.dom.disposableListener
import world.phantasmal.webui.dom.disposablePointerDrag

/**
 * Character creation laid out like the original's menu (per the user's reference capture): the
 * hex-tab section list down the left -- HEAD, BODY, PROPORTION, CHARACTER NAME, AUTO, OK -- with
 * the live rotating 3D preview filling the screen behind it, and Enter OK / Esc BACK top-right
 * (BACK is tappable and cancels). The class itself was chosen on the previous screen.
 *
 * What each section drives:
 *  - HEAD: the class's real face/hair/accessory mesh variants ([PLAYER_APPEARANCE_OPTIONS]).
 *  - BODY: eighteen costume colors. psov2 ships exactly one texture color per class (verified),
 *    so the original's costume variants are *synthesized* by material tinting -- see
 *    PlayerAssetLoader.applyCostumeTint for the trade involved.
 *  - PROPORTION: height/build steps, visual-only scaling (never gameplay -- see GameRenderer).
 *  - AUTO: randomizes the lot.
 *  - OK: creates, once a name is entered.
 */
class CharacterCreateScreen(
    container: HTMLElement,
    private val characterClass: CharacterClass,
    private val assetLoader: AssetLoader,
    private val characterClassAssetLoader: CharacterClassAssetLoader,
    createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
    private val onCreate: (CharacterSave) -> Unit,
    private val onCancel: () -> Unit,
) : TrackedDisposable() {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val listeners = mutableListOf<Disposable>()

    private var hairIndex = 0
    private var headIndex = 0
    private var accessoryEquipped = false

    /** Which of the class's real body-texture variants is worn -- see PlayerAssetLoader. */
    private var bodyIndex = 0

    /**
     * The confirmation stage: customization done, the screen shows the model with TYPE, name and
     * the section ID computed from that name -- the badge is real, since the ID feeds the body
     * texture's section slot. Enter creates; Esc returns to editing.
     */
    private var showingSummary = false

    /** Indices into [PROPORTION_STEPS]; the middle step is the authored model. */
    private var heightStep = PROPORTION_DEFAULT_STEP
    private var widthStep = PROPORTION_DEFAULT_STEP

    private var previewMesh: Mesh? = null



    // -- Preview canvas (fills the whole screen, controls overlaid on top) --

    private val canvas = (document.createElement("canvas") as HTMLCanvasElement).also { el ->
        el.style.cssText = "position:fixed;inset:0;z-index:29;touch-action:none;"
        container.appendChild(el)
    }

    private val camera = PerspectiveCamera(
        fov = 60.0,
        aspect = window.innerWidth.toDouble() / window.innerHeight,
        near = 0.1,
        far = 3_000.0,
    )
    private val context = RenderContext(canvas, camera)
    private val disposableRenderer = createThreeRenderer(canvas)
    private val threeRenderer = disposableRenderer.renderer
    private val clock = Clock()

    private var dragging = false
    private var autoRotate = 0.0

    // -- Overlay --

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-root"
        container.appendChild(el)
    }

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val hexBg = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-hexbg"
        el.innerHTML = """<svg viewBox="0 0 8465 8477" class="pw-cc-hex-svg">$HEX_GRID_SVG_INNER</svg>"""
        root.appendChild(el)
    }

    private val header = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-header"
        el.textContent = "CHARACTER CREATION"
        root.appendChild(el)
    }

    private val prompts = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-prompts"
        el.innerHTML =
            "<div class=\"pw-cc-prompt\"><span class=\"pw-cc-key\">Enter</span> OK</div>" +
                "<div class=\"pw-cc-prompt\"><span class=\"pw-cc-key\">Esc</span> BACK</div>"
        root.appendChild(el)
    }

    private val okPrompt = prompts.firstElementChild as HTMLElement

    private val menu = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-menu"
        root.appendChild(el)
    }

    private val classLabel = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-class-label"
        el.textContent = characterClass.uiName
        menu.appendChild(el)
    }

    private val summary = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-summary"
        el.style.display = "none"
        root.appendChild(el)
    }

    /**
     * The summary stage's confirm: the HUD sheet's green capsule sprite 3-sliced into a wide
     * PSO-style button (same technique as the status bars -- only the x-uniform middle
     * stretches, so the rounded caps keep their authored shape), pinned bottom-centre.
     */
    private val enterButton = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-enter-btn"
        el.style.display = "none"

        val sprite = HudSprites.CAPSULE_GREEN
        val cap = HudSprites.CAPSULE_CAP
        val scaleY = ENTER_BUTTON_H / sprite.h
        val midW = ENTER_BUTTON_W - 2 * cap * scaleY
        val scaleXMid = midW / (sprite.w - 2 * cap)

        val slices = (document.createElement("div") as HTMLElement).also { sl ->
            sl.className = "pw-cc-enter-slices"
            el.appendChild(sl)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText = hudSpriteStyleXY(sprite, sprite.x, cap, scaleY, scaleY)
            slices.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText =
                hudSpriteStyleXY(sprite, sprite.x + cap, sprite.w - 2 * cap, scaleXMid, scaleY)
            slices.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also {
            it.style.cssText =
                hudSpriteStyleXY(sprite, sprite.x + sprite.w - cap, cap, scaleY, scaleY)
            slices.appendChild(it)
        }
        (document.createElement("div") as HTMLElement).also { label ->
            label.className = "pw-cc-enter-label"
            label.textContent = "OK"
            el.appendChild(label)
        }
        root.appendChild(el)
    }

    private val tabBar = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-cc-tabs"
        menu.appendChild(el)
    }

    // One container per section; the active tab shows its container and hides the rest.
    private val sectionHead = sectionContainer()
    private val sectionBody = sectionContainer()
    private val sectionProportion = sectionContainer()
    private val sectionName = sectionContainer()

    private val tabs = mutableMapOf<String, HTMLElement>()

    private val nameInput = (document.createElement("input") as HTMLInputElement).also { el ->
        el.type = "text"
        el.maxLength = 16
        el.placeholder = "Character name"
        el.className = "pw-cc-name-input"
        sectionName.appendChild(el)
    }

    private val headRow = pickerRow("FACE") { "${headIndex + 1}" }
    private val hairRow = pickerRow("HAIR") { "${hairIndex + 1}" }
    private val accessoryRow = toggleRow("ACCESSORY") { accessoryEquipped }
    private val bodyRow = bodyColorRow()
    private val heightRow = proportionRow("HEIGHT", { heightStep }) { heightStep = it }
    private val widthRow = proportionRow("BUILD", { widthStep }) { widthStep = it }

    private fun sectionContainer(): HTMLElement =
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-section"
            el.style.display = "none"
            menu.appendChild(el)
        }

    private fun confirmCreate() {
        val name = nameInput.value.trim()
        if (name.isEmpty()) {
            showSection("CHARACTER NAME")
            return
        }

        if (!showingSummary) {
            enterSummary(name)
            return
        }

        val now = Date.now()
        onCreate(
            CharacterSave(
                id = "${now}_${kotlin.random.Random.nextInt()}",
                name = name,
                characterClassSlug = characterClass.slug,
                sectionId = computeSectionId(name, characterClass),
                headIndex = headIndex,
                hairIndex = hairIndex,
                accessoryEquipped = accessoryEquipped,
                createdAtEpochMs = now,
                bodyIndex = bodyIndex,
                equippedWeapon = SavedWeapon(tierName = starterWeaponSlug(characterClass)),
                proportionHeight = PROPORTION_STEPS[heightStep],
                proportionWidth = PROPORTION_STEPS[widthStep],
            )
        )
    }

    private fun enterSummary(name: String) {
        showingSummary = true
        menu.style.display = "none"

        summary.innerHTML = ""
        val sectionId = computeSectionId(name, characterClass)

        fun row(label: String, value: String, emblem: world.phantasmal.web.shared.dto.SectionId? = null) {
            val rowEl = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cc-sum-row"
                summary.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cc-sum-label"
                el.textContent = label
                rowEl.appendChild(el)
            }
            emblem?.let { id ->
                // The genuine emblem, decoded from the ID's chest-texture slot in the personal
                // archives. If the overlay isn't present the image 404s and the plain colored
                // marker takes its place.
                val fallback = (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-cc-sum-badge"
                    el.style.background = SECTION_ID_COLORS[id] ?: "#888"
                    el.style.display = "none"
                }
                (document.createElement("img") as HTMLImageElement).also { el ->
                    el.className = "pw-cc-sum-emblem"
                    el.src = "assets/skin/sectionids/${id.name.lowercase()}.png"
                    el.onerror = { _, _, _, _, _ ->
                        el.style.display = "none"
                        fallback.style.display = "block"
                        null
                    }
                    rowEl.appendChild(el)
                }
                rowEl.appendChild(fallback)
            }
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cc-sum-value"
                el.textContent = value
                rowEl.appendChild(el)
            }
        }

        row("TYPE", characterClass.uiName)
        row("CHARACTER NAME", name)
        row("SECTION ID", sectionId.uiName, sectionId)

        summary.style.display = "flex"
        okPrompt.style.display = "none"
        enterButton.style.display = "block"

        // Reload the model wearing its real section badge -- the ID indexes an actual texture
        // slot in the body archive, so the marker appears on the torso like the reference.
        refreshPreview(sectionId)
    }

    private fun exitSummary() {
        showingSummary = false
        summary.style.display = "none"
        okPrompt.style.display = ""
        enterButton.style.display = "none"
        menu.style.display = "flex"
        refreshPreview()
    }

    private fun showSection(name: String) {
        for ((tabName, tab) in tabs) {
            tab.classList.toggle("pw-cc-tab-active", tabName == name)
        }
        sectionHead.style.display = if (name == "HEAD") "flex" else "none"
        sectionBody.style.display = if (name == "BODY") "flex" else "none"
        sectionProportion.style.display = if (name == "PROPORTION") "flex" else "none"
        sectionName.style.display = if (name == "CHARACTER NAME") "flex" else "none"
    }

    private fun randomizeAll() {
        val options = PLAYER_APPEARANCE_OPTIONS.getValue(characterClass)
        if (options.headCount > 0) headIndex = kotlin.random.Random.nextInt(options.headCount)
        if (options.hairCount > 0) hairIndex = kotlin.random.Random.nextInt(options.hairCount)
        accessoryEquipped =
            hairIndex in options.accessoryHairIndices && kotlin.random.Random.nextBoolean()
        bodyIndex = kotlin.random.Random.nextInt(characterClass.bodyStyleCount)
        heightStep = kotlin.random.Random.nextInt(PROPORTION_STEPS.size)
        widthStep = kotlin.random.Random.nextInt(PROPORTION_STEPS.size)
        bodyRow.refresh()
        heightRow.refresh()
        widthRow.refresh()
        refreshPreview()
    }

    init {
        for (name in listOf("HEAD", "BODY", "PROPORTION", "CHARACTER NAME", "AUTO", "OK")) {
            val tab = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-cc-tab"
                el.innerHTML = "<span class='pw-cc-tab-hex'>⬢</span>" + name
                tabBar.appendChild(el)
            }
            tabs[name] = tab
            listeners.add(tab.disposableListener<PointerEvent>("pointerdown", {
                it.stopPropagation()
                when (name) {
                    "AUTO" -> randomizeAll()
                    "OK" -> confirmCreate()
                    else -> showSection(name)
                }
            }))
        }

        showSection("HEAD")

        // The reference's Esc BACK is the way out -- the prompt itself is the tap target. From
        // the summary it steps back into editing rather than abandoning the character.
        listeners.add(prompts.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            if (showingSummary) exitSummary() else onCancel()
        }))

        listeners.add(enterButton.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            confirmCreate()
        }))

        listeners.add(
            canvas.disposablePointerDrag(
                onPointerDown = { dragging = true; true },
                onPointerMove = { movedX, _, _ ->
                    previewMesh?.rotation?.let { it.y += movedX * 0.01 }
                    true
                },
                onPointerUp = { dragging = false },
            )
        )
        listeners.add(window.disposableListener<Event>("resize", { onResize() }))
        onResize()
        refreshPreview()
        render()
    }

    private fun onResize() {
        canvas.width = window.innerWidth
        canvas.height = window.innerHeight
        camera.aspect = window.innerWidth.toDouble() / window.innerHeight
        camera.updateProjectionMatrix()
        threeRenderer.setSize(window.innerWidth.toDouble(), window.innerHeight.toDouble())
    }

    private fun render() {
        if (disposed) return

        val delta = clock.getDelta()
        if (!dragging) {
            autoRotate += delta * 0.5
            previewMesh?.rotation?.y = autoRotate
        }
        threeRenderer.render(context.scene, camera)
        window.requestAnimationFrame { render() }
    }

    private fun refreshPreview(sectionId: world.phantasmal.web.shared.dto.SectionId? = null) {
        val options = PLAYER_APPEARANCE_OPTIONS.getValue(characterClass)
        hairIndex = hairIndex.coerceIn(0, (options.hairCount - 1).coerceAtLeast(0))
        headIndex = headIndex.coerceIn(0, (options.headCount - 1).coerceAtLeast(0))
        if (hairIndex !in options.accessoryHairIndices) accessoryEquipped = false

        hairRow.container.style.display = if (options.hairCount > 0) "flex" else "none"
        headRow.container.style.display = if (options.headCount > 1) "flex" else "none"
        accessoryRow.container.style.display =
            if (hairIndex in options.accessoryHairIndices) "flex" else "none"

        hairRow.refresh()
        headRow.refresh()
        accessoryRow.refresh()

        val appearance = PlayerAppearance(
            characterClass = characterClass,
            headIndex = headIndex,
            hairIndex = hairIndex,
            accessoryEquipped = accessoryEquipped,
            bodyIndex = bodyIndex,
            sectionId = sectionId ?: world.phantasmal.web.shared.dto.SectionId.Viridia,
        )

        scope.launch {
            val meshData = PlayerAssetLoader(characterClassAssetLoader).loadPlayerMesh(appearance)
            if (disposed) return@launch

            previewMesh?.let { context.scene.remove(it) }
            previewMesh = meshData.mesh
            context.scene.add(meshData.mesh)
            applyPreviewProportions()

            val bSphere = boundingSphere(meshData.mesh)
            camera.position.set(
                bSphere.center.x,
                bSphere.center.y + bSphere.radius * 0.2,
                bSphere.center.z + bSphere.radius * 2.2,
            )
            camera.lookAt(bSphere.center)
        }
    }

    // -- Small UI-building helpers --

    private class Picker(val container: HTMLElement, val refresh: () -> Unit)

    private fun pickerRow(label: String, valueText: () -> String): Picker {
        val row = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-row"
            sectionHead.appendChild(el)
        }

        val labelEl = (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = label
            el.className = "pw-cc-row-label"
            row.appendChild(el)
        }

        val prev = arrowButton(row, "‹")
        val valueEl = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-row-value"
            row.appendChild(el)
        }
        val next = arrowButton(row, "›")

        fun refresh() {
            valueEl.textContent = valueText()
        }

        val cycle: (Int) -> Unit = if (label == "FACE") ::cycleHead else ::cycleHair
        listeners.add(prev.disposableListener<PointerEvent>("pointerdown", { cycle(-1) }))
        listeners.add(next.disposableListener<PointerEvent>("pointerdown", { cycle(1) }))

        refresh()
        return Picker(row, ::refresh)
    }

    private fun toggleRow(label: String, valueBool: () -> Boolean): Picker {
        val row = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-row"
            sectionHead.appendChild(el)
        }

        val labelEl = (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = label
            el.className = "pw-cc-row-label"
            row.appendChild(el)
        }

        val valueEl = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-toggle-value"
            row.appendChild(el)
        }

        fun refresh() {
            valueEl.textContent = if (valueBool()) "ON" else "OFF"
        }

        listeners.add(
            row.disposableListener<PointerEvent>("pointerdown", {
                accessoryEquipped = !accessoryEquipped
                refreshPreview()
            })
        )

        refresh()
        return Picker(row, ::refresh)
    }

    private fun proportionRow(label: String, get: () -> Int, set: (Int) -> Unit): Picker {
        val row = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-row"
            sectionProportion.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = label
            el.className = "pw-cc-row-label"
            row.appendChild(el)
        }
        val prev = arrowButton(row, "‹")
        val valueEl = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-row-value"
            row.appendChild(el)
        }
        val next = arrowButton(row, "›")

        fun refresh() {
            valueEl.textContent = "${get() + 1}/${PROPORTION_STEPS.size}"
        }

        fun step(direction: Int) {
            set((get() + direction).coerceIn(0, PROPORTION_STEPS.size - 1))
            refresh()
            applyPreviewProportions()
        }
        listeners.add(prev.disposableListener<PointerEvent>("pointerdown", { step(-1) }))
        listeners.add(next.disposableListener<PointerEvent>("pointerdown", { step(1) }))

        refresh()
        return Picker(row, ::refresh)
    }

    /**
     * The class's real body-texture variants (18 for most classes, 25 for the casts), cycled
     * like the other pickers. These are the same variants phantasmal.world's own Viewer exposes
     * as `body=` -- the full sets ship in the bundled per-class texture archives.
     */
    private fun bodyColorRow(): Picker {
        val row = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-row"
            sectionBody.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = "COLOR"
            el.className = "pw-cc-row-label"
            row.appendChild(el)
        }
        val prev = arrowButton(row, "‹")
        val valueEl = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-cc-row-value"
            row.appendChild(el)
        }
        val next = arrowButton(row, "›")

        fun refresh() {
            valueEl.textContent = "${bodyIndex + 1}/${characterClass.bodyStyleCount}"
        }

        fun step(direction: Int) {
            val count = characterClass.bodyStyleCount
            bodyIndex = (bodyIndex + direction + count) % count
            refresh()
            refreshPreview()
        }
        listeners.add(prev.disposableListener<PointerEvent>("pointerdown", { step(-1) }))
        listeners.add(next.disposableListener<PointerEvent>("pointerdown", { step(1) }))

        refresh()
        return Picker(row, ::refresh)
    }

    /** Proportions scale the preview mesh directly -- no reload needed. */
    private fun applyPreviewProportions() {
        previewMesh?.scale?.set(
            PROPORTION_STEPS[widthStep],
            PROPORTION_STEPS[heightStep],
            PROPORTION_STEPS[widthStep],
        )
    }

    private fun arrowButton(row: HTMLElement, text: String): HTMLElement =
        (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = text
            el.className = "pw-cc-arrow"
            row.appendChild(el)
        }

    private fun actionButton(row: HTMLElement, text: String, primary: Boolean, onClick: () -> Unit): HTMLElement =
        (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = text
            el.className = if (primary) "pw-cc-button pw-cc-button-primary" else "pw-cc-button"
            row.appendChild(el)
            listeners.add(el.disposableListener<PointerEvent>("pointerdown", { onClick() }))
        }

    private fun cycleHair(direction: Int) {
        val hairCount = PLAYER_APPEARANCE_OPTIONS.getValue(characterClass).hairCount
        if (hairCount == 0) return
        hairIndex = (hairIndex + direction + hairCount) % hairCount
        refreshPreview()
    }

    private fun cycleHead(direction: Int) {
        val headCount = PLAYER_APPEARANCE_OPTIONS.getValue(characterClass).headCount
        if (headCount == 0) return
        headIndex = (headIndex + direction + headCount) % headCount
        refreshPreview()
    }

    override fun dispose() {
        scope.cancel()
        for (listener in listeners) listener.dispose()
        context.dispose()
        disposableRenderer.dispose()
        canvas.remove()
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    companion object {
        /** On-screen size of the summary's green capsule OK button. */
        private const val ENTER_BUTTON_W = 250.0
        private const val ENTER_BUTTON_H = 44.0

        /** Height/build steps, the middle being the authored model. Visual-only. */
        private val PROPORTION_STEPS =
            listOf(0.90, 0.925, 0.95, 0.975, 1.0, 1.025, 1.05, 1.075, 1.10)
        private const val PROPORTION_DEFAULT_STEP = 4

        private const val STYLESHEET = """
            .pw-cc-root {
              position: fixed;
              inset: 0;
              z-index: 30;
              overflow: hidden;
              pointer-events: none;
              touch-action: none;
              user-select: none;
              padding: var(--pw-safe-top) var(--pw-safe-right) var(--pw-safe-bottom) var(--pw-safe-left);
              box-sizing: border-box;
            }
            .pw-cc-hexbg {
              position: absolute;
              inset: -10%;
              width: 42%;
              opacity: .16;
              pointer-events: none;
            }
            .pw-cc-hex-svg {
              width: 100%;
              height: 100%;
              stroke: rgba(120,220,220,.5);
              fill: none;
            }
            .pw-cc-header {
              position: absolute;
              top: 14px;
              left: 20px;
              padding: 8px 34px 8px 16px;
              font: bold 18px sans-serif;
              letter-spacing: 1px;
              color: #222;
              background: linear-gradient(180deg,#e8eef0,#b7c4c6);
              clip-path: polygon(0 0, calc(100% - 18px) 0, 100% 100%, 0 100%);
            }
            .pw-cc-prompts {
              position: absolute;
              top: 14px;
              right: 18px;
              display: flex;
              gap: 14px;
              pointer-events: auto;
              touch-action: none;
              cursor: pointer;
            }
            .pw-cc-prompts:active { filter: brightness(1.5); }
            .pw-cc-prompt {
              display: flex;
              align-items: center;
              gap: 6px;
              font: bold 13px sans-serif;
              color: #e8f6f6;
            }
            .pw-cc-key {
              background: rgba(255,255,255,.15);
              border: 1px solid rgba(255,255,255,.5);
              border-radius: 4px;
              padding: 2px 8px;
              font-size: 11px;
            }
            .pw-cc-menu {
              position: absolute;
              top: 56px;
              left: 16px;
              bottom: 16px;
              width: min(240px, 70vw);
              display: flex;
              flex-direction: column;
              gap: 8px;
              padding: 12px;
              border-radius: 10px;
              border: 1px solid rgba(140,220,220,.35);
              background: rgba(2,20,24,.6);
              pointer-events: auto;
              overflow-y: auto;
              touch-action: pan-y;
              -webkit-overflow-scrolling: touch;
              padding-bottom: 24px;
            }
            /* --- section tabs, per the reference's hex list --- */
            .pw-cc-tabs {
              display: flex;
              flex-direction: column;
              gap: 6px;
            }
            .pw-cc-tab {
              display: flex;
              align-items: center;
              gap: 8px;
              padding: 7px 14px;
              font: bold 13px sans-serif;
              letter-spacing: 1px;
              color: #eafafa;
              background: linear-gradient(180deg, rgba(60,160,170,.55), rgba(20,80,90,.55));
              border: 1px solid rgba(160,240,240,.5);
              clip-path: polygon(0 0, calc(100% - 12px) 0, 100% 50%, calc(100% - 12px) 100%, 0 100%);
              flex: 0 0 auto;
              touch-action: pan-y;
              cursor: pointer;
            }
            .pw-cc-tab-hex { color: rgba(200,240,240,.7); font-size: 15px; }
            .pw-cc-tab-active {
              background: linear-gradient(180deg, #ffab4a, #e07414);
              color: #1c1104;
              border-color: rgba(255,230,190,.9);
            }
            .pw-cc-tab-active .pw-cc-tab-hex { color: #7a3c02; }
            .pw-cc-tab:active { filter: brightness(1.3); }
            .pw-cc-section {
              display: flex;
              flex-direction: column;
              gap: 8px;
              margin-top: 2px;
            }
            /* --- final confirmation stage --- */
            .pw-cc-enter-btn {
              position: absolute;
              left: 50%;
              bottom: calc(26px + var(--pw-safe-bottom));
              transform: translateX(-50%);
              pointer-events: auto;
              touch-action: none;
              cursor: pointer;
              filter: drop-shadow(0 3px 6px rgba(0,0,0,.55));
            }
            .pw-cc-enter-btn:active {
              filter: brightness(1.3) drop-shadow(0 3px 6px rgba(0,0,0,.55));
            }
            .pw-cc-enter-slices { display: flex; }
            .pw-cc-enter-label {
              position: absolute;
              inset: 0;
              display: flex;
              align-items: center;
              justify-content: center;
              font: bold 19px sans-serif;
              letter-spacing: 3px;
              color: #f6fff8;
              text-shadow: 0 1px 2px rgba(0,0,0,.85), 0 0 8px rgba(20,110,50,.9);
              pointer-events: none;
            }
            .pw-cc-summary {
              position: absolute;
              top: 50%;
              left: 8%;
              transform: translateY(-50%);
              display: flex;
              flex-direction: column;
              gap: 18px;
              pointer-events: auto;
              touch-action: none;
              cursor: pointer;
            }
            .pw-cc-sum-row {
              display: flex;
              align-items: center;
              gap: 14px;
            }
            .pw-cc-sum-label {
              min-width: 210px;
              text-align: right;
              font: bold 19px sans-serif;
              letter-spacing: 1px;
              color: #f2fbfb;
              text-shadow: 0 2px 3px black;
            }
            .pw-cc-sum-value {
              font: 17px sans-serif;
              color: #ffffff;
              text-shadow: 0 2px 3px black;
            }
            .pw-cc-sum-emblem {
              width: 34px;
              height: 34px;
              image-rendering: pixelated;
              filter: drop-shadow(0 2px 3px rgba(0,0,0,.7));
            }
            .pw-cc-sum-badge {
              width: 26px;
              height: 26px;
              border-radius: 50%;
              border: 3px solid #111;
              box-shadow: 0 0 0 2px rgba(255,255,255,.75), 0 2px 4px rgba(0,0,0,.6);
            }
            .pw-cc-class-label {
              font: bold 15px sans-serif;
              color: #ff8a3d;
              letter-spacing: .5px;
              margin-bottom: 2px;
            }
            .pw-cc-name-input {
              padding: 8px 10px;
              font: 14px sans-serif;
              text-align: center;
              border-radius: 6px;
              border: 1px solid rgba(140,220,220,.4);
              background: rgba(255,255,255,.06);
              color: white;
              outline: none;
            }
            .pw-cc-row {
              display: flex;
              align-items: center;
              justify-content: space-between;
              flex: 0 0 auto;
              padding: 8px 22px;
              background: rgba(140,220,220,.1);
              border: 1px solid rgba(140,220,220,.3);
              clip-path: polygon(6% 0%, 94% 0%, 100% 50%, 94% 100%, 6% 100%, 0% 50%);
              touch-action: pan-y;
            }
            .pw-cc-row-label {
              font: bold 12px sans-serif;
              letter-spacing: .5px;
              color: #eafafa;
            }
            .pw-cc-row-value {
              font: 13px sans-serif;
              color: #eafafa;
              min-width: 40px;
              text-align: center;
            }
            .pw-cc-arrow {
              font: bold 18px sans-serif;
              color: #eafafa;
              padding: 0 10px;
              touch-action: none;
            }
            .pw-cc-toggle-value {
              font: bold 12px sans-serif;
              color: #ff8a3d;
              padding: 3px 12px;
              border-radius: 10px;
              background: rgba(255,255,255,.1);
            }
            .pw-cc-buttons {
              display: flex;
              gap: 10px;
              margin-top: 6px;
            }
            .pw-cc-button {
              flex: 1;
              text-align: center;
              padding: 10px 14px;
              font: bold 13px sans-serif;
              background: rgba(255,255,255,.12);
              color: white;
              clip-path: polygon(8% 0%, 92% 0%, 100% 50%, 92% 100%, 8% 100%, 0% 50%);
              touch-action: none;
            }
            .pw-cc-button-primary {
              background: #ff8a3d;
              color: #181818;
            }
        """
    }
}
