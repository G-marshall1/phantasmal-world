package world.phantasmal.web.mobileGame.debug

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import world.phantasmal.web.core.boundingSphere
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.core.rendering.RenderContext
import world.phantasmal.web.externals.three.Clock
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.PerspectiveCamera

/**
 * TEMP DIAGNOSTIC: renders one item model alone (a weapon or a decorative map prop -- anything
 * loadable as a single static [Mesh]), slowly spinning, with no player/map/camera-rig complexity
 * in the way -- isolates "does this asset decode and render correctly" from "is it attached
 * correctly", see ?viewWeapon=<slug>/?viewObject=<slug> in Main.kt. Reuses [RenderContext] for its
 * default scene/lighting, same as [world.phantasmal.web.mobileGame.rendering.GameRenderer].
 */
suspend fun runItemViewer(
    loadMesh: suspend () -> Mesh,
    createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
) {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    document.body!!.appendChild(canvas)
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
    canvas.style.width = "100%"
    canvas.style.height = "100%"

    val camera = PerspectiveCamera(
        fov = 60.0,
        aspect = window.innerWidth.toDouble() / window.innerHeight,
        near = 0.1,
        far = 3000.0,
    )
    val context = RenderContext(canvas, camera)
    val threeRenderer = createThreeRenderer(canvas).renderer
    threeRenderer.setSize(window.innerWidth.toDouble(), window.innerHeight.toDouble())

    val itemMesh = loadMesh()
    context.scene.add(itemMesh)

    val bSphere = boundingSphere(itemMesh)
    camera.position.set(bSphere.center.x, bSphere.center.y, bSphere.center.z + bSphere.radius * 2.5)
    camera.lookAt(bSphere.center)

    val clock = Clock()

    fun render() {
        itemMesh.rotation.y += clock.getDelta() * 0.6
        threeRenderer.render(context.scene, camera)
        window.requestAnimationFrame { render() }
    }
    render()
}
