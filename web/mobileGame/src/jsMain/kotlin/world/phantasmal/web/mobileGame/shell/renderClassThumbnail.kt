package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import world.phantasmal.web.core.boundingSphere
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.core.rendering.RenderContext
import world.phantasmal.web.externals.three.PerspectiveCamera
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.mobileGame.player.PlayerAssetLoader
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * No real per-class portrait art exists anywhere in psov2's data (the only class-indexed texture
 * family that looked portrait-like turned out to be a single icon byte-identical across every
 * class -- confirmed by direct inspection). [ClassPickerScreen] uses a live-rendered snapshot of
 * each class's actual model instead. Renders each class in [classes] once to a shared offscreen
 * canvas (reused sequentially, not one live WebGL context per class -- mobile browsers cap
 * concurrent contexts low enough that 9 at once would be a real risk) and reads back a PNG data
 * URL immediately after each render, same idea as [world.phantasmal.web.mobileGame.debug.runItemViewer]'s
 * single-mesh framing but a one-shot snapshot instead of a continuous animation loop.
 *
 * Each class's default appearance (head 0, hair 0, no accessory) goes through the exact same
 * [CharacterClassAssetLoader] instance the rest of the shell uses, so its underlying NjObject
 * cache is warm by the time [CharacterCreateScreen] loads that same default appearance for real,
 * if the user picks it.
 */
suspend fun renderClassThumbnails(
    characterClassAssetLoader: CharacterClassAssetLoader,
    createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
    classes: List<CharacterClass>,
    size: Int = 96,
): Map<CharacterClass, String> {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = size
    canvas.height = size

    val camera = PerspectiveCamera(fov = 45.0, aspect = 1.0, near = 0.1, far = 3_000.0)
    val context = RenderContext(canvas, camera)
    val disposableRenderer = createThreeRenderer(canvas)
    val threeRenderer = disposableRenderer.renderer
    threeRenderer.setSize(size.toDouble(), size.toDouble())

    val playerAssetLoader = PlayerAssetLoader(characterClassAssetLoader)
    val thumbnails = mutableMapOf<CharacterClass, String>()

    try {
        for (characterClass in classes) {
            val meshData = playerAssetLoader.loadPlayerMesh(PlayerAppearance(characterClass))
            context.scene.add(meshData.mesh)

            val bSphere = boundingSphere(meshData.mesh)
            camera.position.set(
                bSphere.center.x,
                bSphere.center.y + bSphere.radius * 0.15,
                bSphere.center.z + bSphere.radius * 2.4,
            )
            camera.lookAt(bSphere.center)
            camera.updateProjectionMatrix()

            threeRenderer.render(context.scene, camera)
            thumbnails[characterClass] = canvas.toDataURL("image/png")

            context.scene.remove(meshData.mesh)
        }
    } finally {
        // A cancelled coroutine (e.g. the screen is disposed -- user confirms a class or cancels
        // out -- before all thumbnails finish) throws out of the loop above via a suspend point
        // inside loadPlayerMesh; without this finally, that skips disposal entirely and leaks a
        // WebGL context every time it happens. Mobile browsers cap concurrent contexts low enough
        // that a handful of leaked ones (e.g. from repeatedly entering/backing out of the class
        // picker) can exhaust the budget and make the next real context creation (GameRenderer's)
        // throw silently inside an unguarded coroutine, permanently stalling that screen's loading
        // spinner with no visible error.
        context.dispose()
        disposableRenderer.dispose()
    }

    return thumbnails
}
