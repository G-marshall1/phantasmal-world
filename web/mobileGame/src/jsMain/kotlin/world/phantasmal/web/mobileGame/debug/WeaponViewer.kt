package world.phantasmal.web.mobileGame.debug

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import world.phantasmal.web.core.boundingSphere
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.core.rendering.RenderContext
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.Clock
import world.phantasmal.web.externals.three.Color
import world.phantasmal.web.externals.three.CylinderGeometry
import world.phantasmal.web.externals.three.MeshBasicMaterial
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.world.AnimatedObjectData
import world.phantasmal.webui.obj
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

    // The camera frames by bounding sphere, which normalises apparent size -- a short model and a
    // tall one look identical on screen. Log the real dimensions so relative height/build can
    // actually be compared between models (used to match psov2's terse citizen model names to the
    // quest format's NpcType names).
    itemMesh.geometry.computeBoundingBox()
    itemMesh.geometry.boundingBox?.let { bb ->
        console.log(
            "VIEWER_BBOX width=${bb.max.x - bb.min.x} height=${bb.max.y - bb.min.y} " +
                "depth=${bb.max.z - bb.min.z} radius=${bSphere.radius} " +
                "minY=${bb.min.y} maxY=${bb.max.y} " +
                "centreX=${(bb.min.x + bb.max.x) / 2} centreZ=${(bb.min.z + bb.max.z) / 2}"
        )
        // The full per-axis bounds: which axis a model's business end runs along, and in which
        // direction from the grip-at-origin, is exactly what per-model orientation fixes need.
        console.log(
            "VIEWER_BOUNDS x=[${bb.min.x}, ${bb.max.x}] y=[${bb.min.y}, ${bb.max.y}] " +
                "z=[${bb.min.z}, ${bb.max.z}]"
        )
    }

    val clock = Clock()

    fun render() {
        itemMesh.rotation.y += clock.getDelta() * 0.6
        threeRenderer.render(context.scene, camera)
        window.requestAnimationFrame { render() }
    }
    render()
}

/**
 * TEMP DIAGNOSTIC: like [runItemViewer] but drives the prop's animation clip, and holds the camera
 * still at a fixed side-on distance instead of framing by bounding sphere -- so the animated
 * vertical extent can actually be judged against a known scale (a 20-unit reference bar is drawn
 * beside it, roughly a player's height). See ?viewAnimObject=<slug> in Main.kt.
 */
suspend fun runAnimatedItemViewer(
    load: suspend () -> AnimatedObjectData,
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

    val data = load()
    context.scene.add(data.mesh)

    // 20-unit tall reference column at the origin's edge -- a player is about this tall.
    val reference = Mesh(
        CylinderGeometry(radiusTop = 0.6, radiusBottom = 0.6, height = 20.0, radialSegments = 8),
        MeshBasicMaterial(obj { color = Color(0xff3366) }),
    )
    reference.position.set(30.0, 10.0, 0.0)
    context.scene.add(reference)

    camera.position.set(0.0, 18.0, 95.0)
    camera.lookAt(Vector3(0.0, 12.0, 0.0))

    val mixer = AnimationMixer(data.mesh)
    mixer.clipAction(createAnimationClip(data.njObject, data.motion)).play()

    val clock = Clock()

    fun render() {
        mixer.update(clock.getDelta())
        threeRenderer.render(context.scene, camera)
        window.requestAnimationFrame { render() }
    }
    render()
}
