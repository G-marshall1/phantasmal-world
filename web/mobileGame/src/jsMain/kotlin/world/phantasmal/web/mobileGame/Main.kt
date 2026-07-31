package world.phantasmal.web.mobileGame

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.url.URLSearchParams
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.externals.three.WebGLRenderer
import world.phantasmal.web.mobileGame.debug.runItemViewer
import world.phantasmal.web.mobileGame.orientation.lockLandscapeOrientation
import world.phantasmal.web.mobileGame.rendering.GameRenderer
import world.phantasmal.web.mobileGame.world.ObjectAssetLoader
import world.phantasmal.web.mobileGame.world.WeaponAssetLoader
import world.phantasmal.webui.obj

fun main() {
    if (document.body != null) {
        init()
    } else {
        window.addEventListener("DOMContentLoaded", { init() })
    }
}

private fun init() {
    lockLandscapeOrientation()

    val params = URLSearchParams(window.location.search)

    // TEMP DIAGNOSTIC: ?viewWeapon=<slug> or ?viewObject=<slug> shows just that item model
    // spinning in isolation instead of starting the game, see WeaponViewer.kt.
    params.get("viewWeapon")?.let { slug ->
        val assetLoader = AssetLoader()
        MainScope().launch {
            runItemViewer({ WeaponAssetLoader(assetLoader).loadWeapon(slug) }, ::createThreeRenderer)
        }
        return
    }
    params.get("viewObject")?.let { slug ->
        val assetLoader = AssetLoader()
        MainScope().launch {
            runItemViewer({ ObjectAssetLoader(assetLoader).loadObject(slug) }, ::createThreeRenderer)
        }
        return
    }

    // ?map=<slug> lets a specific area be loaded for testing (see MAP_SPECS in
    // :web:assets-generation's MapSpecs.kt for the full list of slugs); defaults to Forest 1.
    val mapSlug = params.get("map") ?: "forest01"
    val renderer = GameRenderer(AssetLoader(), ::createThreeRenderer, mapSlug)

    document.body!!.appendChild(renderer.canvas)

    fun resize() {
        renderer.setSize(window.innerWidth, window.innerHeight)
    }

    window.addEventListener("resize", { resize() })
    resize()

    renderer.startRendering()
}

private fun createThreeRenderer(canvas: HTMLCanvasElement): DisposableThreeRenderer =
    object : TrackedDisposable(), DisposableThreeRenderer {
        override val renderer = WebGLRenderer(obj {
            this.canvas = canvas
            antialias = true
            alpha = false
        })

        init {
            renderer.debug.checkShaderErrors = false
            renderer.setPixelRatio(window.devicePixelRatio)
        }

        override fun dispose() {
            renderer.dispose()
            super.dispose()
        }
    }
