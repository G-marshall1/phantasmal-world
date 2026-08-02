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
import world.phantasmal.web.mobileGame.player.CREATABLE_CLASSES
import world.phantasmal.web.mobileGame.player.PLAYER_APPEARANCE_OPTIONS
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.mobileGame.player.PlayerAssetLoader
import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader
import world.phantasmal.webui.dom.disposableListener
import world.phantasmal.webui.dom.disposablePointerDrag

/**
 * Full character creator: class/hairstyle/accessory/head pickers (only shown for options the
 * chosen class actually has -- see PlayerAppearanceOptions.kt) plus a live rotating 3D preview.
 * Section ID/body color are intentionally not exposed here -- verified psov2 ships exactly one
 * texture color per class, so a picker for either would visibly do nothing.
 */
class CharacterCreateScreen(
    container: HTMLElement,
    private val assetLoader: AssetLoader,
    private val characterClassAssetLoader: CharacterClassAssetLoader,
    createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
    private val onCreate: (CharacterSave) -> Unit,
    private val onCancel: () -> Unit,
) : TrackedDisposable() {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val listeners = mutableListOf<Disposable>()

    private var characterClass = CREATABLE_CLASSES.first()
    private var hairIndex = 0
    private var headIndex = 0
    private var accessoryEquipped = false
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

    // -- Overlay controls --

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;inset:0;z-index:30;display:flex;flex-direction:column;" +
                "align-items:center;justify-content:space-between;padding:20px;" +
                "pointer-events:none;"
        container.appendChild(el)
    }

    private val heading = (document.createElement("div") as HTMLElement).also { el ->
        el.textContent = "CREATE CHARACTER"
        el.style.cssText =
            "font:bold 18px sans-serif;color:white;letter-spacing:1px;" +
                "text-shadow:0 1px 4px black;"
        root.appendChild(el)
    }

    private val nameInput = (document.createElement("input") as HTMLInputElement).also { el ->
        el.type = "text"
        el.maxLength = 16
        el.placeholder = "Character name"
        el.style.cssText =
            "pointer-events:auto;width:min(240px,70vw);padding:10px 12px;font:15px sans-serif;" +
                "text-align:center;border-radius:8px;border:2px solid rgba(255,255,255,0.4);" +
                "background:rgba(0,0,0,0.5);color:white;outline:none;margin-top:10px;"
        root.appendChild(el)
    }

    private val controls = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "pointer-events:auto;display:flex;flex-direction:column;align-items:center;" +
                "gap:10px;width:min(320px,90vw);"
        root.appendChild(el)
    }

    private val classRow = pickerRow("Class") { characterClass.slug }
    private val hairRow = pickerRow("Hair") { "${hairIndex + 1}" }
    private val headRow = pickerRow("Head") { "${headIndex + 1}" }
    private val accessoryRow = toggleRow("Accessory") { accessoryEquipped }

    private val buttonsRow = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText = "display:flex;gap:16px;margin-top:6px;"
        controls.appendChild(el)
    }

    private val cancelButton = actionButton(buttonsRow, "Cancel", background = "rgba(255,255,255,0.15)", color = "white") {
        onCancel()
    }
    private val createButton = actionButton(buttonsRow, "Create", background = "#ff8a3d", color = "#181818") {
        val name = nameInput.value.trim()
        if (name.isEmpty()) return@actionButton

        val now = Date.now()
        onCreate(
            CharacterSave(
                id = "${now}_${kotlin.random.Random.nextInt()}",
                name = name,
                characterClassSlug = characterClass.slug,
                sectionId = SectionId.Viridia,
                headIndex = headIndex,
                hairIndex = hairIndex,
                accessoryEquipped = accessoryEquipped,
                createdAtEpochMs = now,
            )
        )
    }

    init {
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

    private fun refreshPreview() {
        val options = PLAYER_APPEARANCE_OPTIONS.getValue(characterClass)
        hairIndex = hairIndex.coerceIn(0, (options.hairCount - 1).coerceAtLeast(0))
        headIndex = headIndex.coerceIn(0, (options.headCount - 1).coerceAtLeast(0))
        if (hairIndex !in options.accessoryHairIndices) accessoryEquipped = false

        hairRow.container.style.display = if (options.hairCount > 0) "flex" else "none"
        headRow.container.style.display = if (options.headCount > 1) "flex" else "none"
        accessoryRow.container.style.display =
            if (hairIndex in options.accessoryHairIndices) "flex" else "none"

        classRow.refresh()
        hairRow.refresh()
        headRow.refresh()
        accessoryRow.refresh()

        val appearance = PlayerAppearance(
            characterClass = characterClass,
            headIndex = headIndex,
            hairIndex = hairIndex,
            accessoryEquipped = accessoryEquipped,
        )

        scope.launch {
            val meshData = PlayerAssetLoader(characterClassAssetLoader).loadPlayerMesh(appearance)
            if (disposed) return@launch

            previewMesh?.let { context.scene.remove(it) }
            previewMesh = meshData.mesh
            context.scene.add(meshData.mesh)

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
            el.style.cssText =
                "display:flex;align-items:center;justify-content:space-between;width:100%;" +
                    "background:rgba(0,0,0,0.5);border-radius:8px;padding:8px 12px;"
            controls.appendChild(el)
        }

        val labelEl = (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = label
            el.style.cssText = "font:bold 13px sans-serif;color:white;"
            row.appendChild(el)
        }

        val prev = arrowButton(row, "‹")
        val valueEl = (document.createElement("div") as HTMLElement).also { el ->
            el.style.cssText = "font:14px sans-serif;color:white;min-width:60px;text-align:center;"
            row.appendChild(el)
        }
        val next = arrowButton(row, "›")

        fun refresh() {
            valueEl.textContent = valueText()
        }

        when (label) {
            "Class" -> {
                listeners.add(prev.disposableListener<PointerEvent>("pointerdown", { cycleClass(-1) }))
                listeners.add(next.disposableListener<PointerEvent>("pointerdown", { cycleClass(1) }))
            }
            "Hair" -> {
                listeners.add(prev.disposableListener<PointerEvent>("pointerdown", { cycleHair(-1) }))
                listeners.add(next.disposableListener<PointerEvent>("pointerdown", { cycleHair(1) }))
            }
            "Head" -> {
                listeners.add(prev.disposableListener<PointerEvent>("pointerdown", { cycleHead(-1) }))
                listeners.add(next.disposableListener<PointerEvent>("pointerdown", { cycleHead(1) }))
            }
        }

        refresh()
        return Picker(row, ::refresh)
    }

    private fun toggleRow(label: String, valueBool: () -> Boolean): Picker {
        val row = (document.createElement("div") as HTMLElement).also { el ->
            el.style.cssText =
                "display:flex;align-items:center;justify-content:space-between;width:100%;" +
                    "background:rgba(0,0,0,0.5);border-radius:8px;padding:8px 12px;"
            controls.appendChild(el)
        }

        val labelEl = (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = label
            el.style.cssText = "font:bold 13px sans-serif;color:white;"
            row.appendChild(el)
        }

        val valueEl = (document.createElement("div") as HTMLElement).also { el ->
            el.style.cssText =
                "font:bold 13px sans-serif;color:#ff8a3d;padding:4px 14px;border-radius:12px;" +
                    "background:rgba(255,255,255,0.1);"
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

    private fun arrowButton(row: HTMLElement, text: String): HTMLElement =
        (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = text
            el.style.cssText =
                "font:bold 18px sans-serif;color:white;padding:0 12px;touch-action:none;user-select:none;"
            row.appendChild(el)
        }

    private fun actionButton(
        row: HTMLElement,
        text: String,
        background: String,
        color: String,
        onClick: () -> Unit,
    ): HTMLElement =
        (document.createElement("div") as HTMLElement).also { el ->
            el.textContent = text
            el.style.cssText =
                "padding:12px 28px;border-radius:22px;font:bold 15px sans-serif;" +
                    "background:$background;color:$color;touch-action:none;user-select:none;"
            row.appendChild(el)
            listeners.add(el.disposableListener<PointerEvent>("pointerdown", { onClick() }))
        }

    private fun cycleClass(direction: Int) {
        val index = CREATABLE_CLASSES.indexOf(characterClass)
        characterClass = CREATABLE_CLASSES[(index + direction + CREATABLE_CLASSES.size) % CREATABLE_CLASSES.size]
        hairIndex = 0
        headIndex = 0
        accessoryEquipped = false
        refreshPreview()
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
        super.dispose()
    }
}
