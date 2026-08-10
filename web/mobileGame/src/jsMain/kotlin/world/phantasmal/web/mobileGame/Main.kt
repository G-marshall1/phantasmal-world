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
import world.phantasmal.web.mobileGame.debug.runAnimatedItemViewer
import world.phantasmal.web.mobileGame.debug.runItemViewer
import world.phantasmal.web.mobileGame.orientation.lockLandscapeOrientation
import world.phantasmal.web.mobileGame.rendering.GameRenderer
import world.phantasmal.web.mobileGame.shell.GameShell
import world.phantasmal.web.mobileGame.world.NpcAssetLoader
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

    // ?viewNpc=<slug> / ?viewCityNpc=<slug> show one town NPC model in isolation. The two entry
    // points mirror NpcAssetLoader's own split (bml-sourced vs standalone ".rel" city NPCs). Used
    // to identify which model corresponds to which quest NpcType by eye -- psov2's filenames encode
    // body type as terse abbreviations (bm_n_e{b,c,f,fs,m,o,t}{m,w}_i_body) that can't be mapped to
    // "Male Fat"/"Male Dwarf"/etc. with any confidence without actually looking at the meshes.
    // ?viewAnimObject=<slug> shows an animated prop (the city teleporter beams) playing its clip
    // beside a 20-unit height reference, to judge how far the animation actually travels.
    params.get("viewAnimObject")?.let { slug ->
        val assetLoader = AssetLoader()
        MainScope().launch {
            runAnimatedItemViewer(
                { ObjectAssetLoader(assetLoader).loadAnimatedObject(slug) },
                ::createThreeRenderer,
            )
        }
        return
    }

    params.get("viewNpc")?.let { slug ->
        val assetLoader = AssetLoader()
        MainScope().launch {
            runItemViewer({ NpcAssetLoader(assetLoader).loadNpc(slug).mesh }, ::createThreeRenderer)
        }
        return
    }

    params.get("viewCityNpc")?.let { slug ->
        val assetLoader = AssetLoader()
        MainScope().launch {
            runItemViewer({ NpcAssetLoader(assetLoader).loadCityNpc(slug).mesh }, ::createThreeRenderer)
        }
        return
    }

    // ?map=<slug> lets a specific area be loaded for testing, bypassing the title/login/character
    // shell entirely with a single hardcoded-appearance scene (see MAP_SPECS in
    // :web:assets-generation's MapSpecs.kt for the full list of slugs, plus STAGE_SPECS in
    // StageSpecs.kt for static hub stages like "pioneer2"). A real (param-less) page load goes
    // through GameShell instead, ending in this exact same GameRenderer with a real chosen
    // character and mapSlug = "pioneer2".
    val mapSlugParam = params.get("map")

    if (mapSlugParam != null) {
        // DEBUG: ?spawn=x,z drops the player somewhere other than the stage's usual origin, for
        // walking up to a specific piece of geometry without crossing the whole map first.
        // "x,z" uses the ground search; "x,z,y" pins the height too.
        val spawnParts = params.get("spawn")
            ?.split(",")
            ?.mapNotNull { it.trim().toDoubleOrNull() }
        val spawnOverride = spawnParts?.takeIf { it.size >= 2 }?.let { it[0] to it[1] }
        val spawnYOverride = spawnParts?.takeIf { it.size >= 3 }?.get(2)

        val renderer = GameRenderer(
            AssetLoader(),
            ::createThreeRenderer,
            mapSlugParam,
            spawnOverride = spawnOverride,
            spawnYOverride = spawnYOverride,
            // This route builds a bare renderer with no shell behind it, so there's nothing to
            // rebuild the world -- just reload onto the destination map. Keeps teleporters
            // testable from here instead of only through the full character flow.
            showAnimationBrowser = params.get("animBrowser") != null,
            // DEBUG: ?layout=<name> pins which of the area's encounter tables is played (see
            // AreaSpawnTable), which is otherwise picked at random every time the area loads.
            layoutOverride = params.get("layout"),
            // DEBUG: ?weapon=<slug> equips any shipped weapon model (see WEAPON_TYPES), which
            // also switches the character's whole motion set to that weapon's class.
            weaponSlug = params.get("weapon") ?: "Saber",
            // DEBUG: ?prop=w,h reproduces a customized character's body proportions -- weapon
            // attachment bugs only visible on scaled skeletons need this to show headlessly.
            appearance = run {
                // DEBUG: ?class=<slug> puts the debug character in another class's body --
                // per-class skeletons can hold an attached weapon differently, so orientation
                // must be checked on more bodies than the default HUmar.
                val base = params.get("class")
                    ?.let { slug ->
                        world.phantasmal.web.viewer.models.CharacterClass.VALUES_LIST
                            .find { it.slug.lowercase() == slug.lowercase() }
                    }
                    ?.let { world.phantasmal.web.mobileGame.player.PlayerAppearance(it) }
                    ?: world.phantasmal.web.mobileGame.player.PlayerAppearance.DEFAULT
                params.get("prop")
                    ?.split(",")
                    ?.mapNotNull { it.trim().toDoubleOrNull() }
                    ?.takeIf { it.size == 2 }
                    ?.let { base.copy(proportionWidth = it[0], proportionHeight = it[1]) }
                    ?: base
            },
            // DEBUG: ?face=<degrees> turns the character at spawn -- 180 faces the camera,
            // for judging how a weapon sits in the hand from the front.
            facingOverride = params.get("face")?.toDoubleOrNull()?.let { it * kotlin.math.PI / 180.0 },
            // DEBUG: ?pose=<clip>,<frame> freezes the character mid-animation -- the way to
            // inspect how a weapon sits at an attack's contact frame.
            boneScan = params.get("bonescan") != null,
            // DEBUG: ?bindpose=1 spawns enemies frozen in their bind pose, no AI, no clips --
            // for telling skeleton-conversion bugs apart from animation bugs.
            debugBindPose = params.get("bindpose") != null,
            // DEBUG: ?fxsheet=technic lays that fx .xvm archive out as a ground-grid contact
            // sheet; &fxpage=N pages through the bigger archives.
            fxSheet = params.get("fxsheet"),
            fxSheetPage = params.get("fxpage")?.toIntOrNull() ?: 0,
            // DEBUG: ?fxslow=1 stretches timed effects 20x for headless capture.
            fxSlowMotion = params.get("fxslow") != null,
            poseOverride = params.get("pose")
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.size == 2 }
                ?.let { it[0] to it[1] },
            onAreaTransition = { destination ->
                window.location.search = "?map=$destination"
            },
        )

        document.body!!.appendChild(renderer.canvas)

        fun resize() {
            renderer.setSize(window.innerWidth, window.innerHeight)
        }

        window.addEventListener("resize", { resize() })
        resize()

        renderer.startRendering()
    } else {
        GameShell(AssetLoader(), ::createThreeRenderer).start()
    }
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
