package world.phantasmal.web.mobileGame.shell

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.mobileGame.persistence.CharacterSave
import world.phantasmal.web.mobileGame.persistence.CharacterStore
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.mobileGame.player.starterWeaponSlug
import world.phantasmal.web.mobileGame.player.weaponTierByName
import world.phantasmal.web.mobileGame.rendering.GameRenderer
import world.phantasmal.web.mobileGame.shell.titlescreen.TitleScreen
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * Orchestrates the meta-game flow: Loading -> Title -> Login -> Character Select/Create ->
 * gameplay (Pioneer 2 hub). Each screen is a raw-DOM [TrackedDisposable] (see LoadingScreen.kt
 * etc.); this just swaps one at a time.
 */
class GameShell(
    private val assetLoader: AssetLoader,
    private val createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
) {
    // Skin-aware: prefers the personal-build body-variant texture archives when present.
    private val characterClassAssetLoader =
        CharacterClassAssetLoader(world.phantasmal.web.mobileGame.loading.SkinAssetLoader())
    private val characterStore = CharacterStore()
    private var currentScreen: TrackedDisposable? = null
    private var nickname: String = characterStore.loadLastNickname() ?: ""

    /** The live world, if one is running. Kept so it can be torn down on an area change. */
    private var gameRenderer: GameRenderer? = null

    fun start() {
        showLoading("Loading...")
        MainScope().launch { showTitle() }
    }

    /**
     * Swaps screens, disposing the outgoing one *before* the incoming one is built. Taking the
     * new screen as a factory rather than a value is the whole point: passing it as an argument
     * constructed it first, so two screens briefly coexisted -- and several of them own a WebGL
     * context, which mobile browsers allow only a few of at once.
     */
    private fun <T : TrackedDisposable> setScreen(create: () -> T): T {
        currentScreen?.dispose()
        currentScreen = null
        val screen = create()
        currentScreen = screen
        return screen
    }

    private fun showLoading(message: String) {
        setScreen { LoadingScreen(document.body!!, message) }
    }

    private fun showTitle() {
        setScreen {
            TitleScreen(
                document.body!!,
                onNewGame = ::showLogin,
                // Skips nickname entry and reuses whatever's already saved -- there's no other
                // "session" concept yet to distinguish this from New Game beyond that.
                onContinue = ::showCharacterSelect,
            )
        }
    }

    private fun showLogin() {
        setScreen {
            LoginScreen(document.body!!, nickname) { nick ->
                nickname = nick
                characterStore.saveLastNickname(nick)
                showCharacterSelect()
            }
        }
    }

    private fun showCharacterSelect() {
        setScreen {
            CharacterSelectScreen(
                document.body!!,
                characterStore.loadAll(),
                onSelect = ::enterGame,
                onCreateNew = ::showClassPicker,
                onDelete = { save ->
                    characterStore.delete(save.id)
                    showCharacterSelect()
                },
            )
        }
    }

    private fun showClassPicker() {
        setScreen {
            ClassPickerScreen(
                document.body!!,
                characterClassAssetLoader,
                createThreeRenderer,
                onConfirm = ::showCharacterCreate,
                onCancel = ::showCharacterSelect,
            )
        }
    }

    private fun showCharacterCreate(characterClass: CharacterClass) {
        setScreen {
            CharacterCreateScreen(
                document.body!!,
                characterClass,
                assetLoader,
                characterClassAssetLoader,
                createThreeRenderer,
                onCreate = { save ->
                    characterStore.save(save)
                    enterGame(save)
                },
                onCancel = ::showClassPicker,
            )
        }
    }

    private fun enterGame(save: CharacterSave) {
        enterMap(save, "pioneer2", "Entering Pioneer 2...")
    }

    /**
     * Builds the world for [mapSlug] and hands control to it, tearing down whatever was running
     * before. Area transitions go through here too: [GameRenderer] fixes its map at construction
     * and builds the entire scene around it, so moving between areas means replacing the renderer
     * rather than mutating it. Disposing the old one first matters -- each holds a WebGL context,
     * and leaking them exhausts the device's budget after a few transitions.
     */
    private fun enterMap(save: CharacterSave, mapSlug: String, loadingMessage: String) {
        // Progress writes land here so an area transition carries the *latest* save rather than
        // the one this map was entered with.
        var liveSave = save

        gameRenderer?.let {
            it.stopRendering()
            it.canvas.remove()
            it.dispose()
        }
        gameRenderer = null

        showLoading(loadingMessage)

        // A watchdog, because a loading screen that never clears leaves no way back into the
        // game at all -- the player has to force-quit. If the world hasn't started by now,
        // something failed silently; drop back to character select rather than spin forever.
        val attempt = ++enterMapAttempt
        window.setTimeout({
            if (attempt == enterMapAttempt && gameRenderer == null) {
                console.error("Timed out entering $mapSlug")
                showCharacterSelect()
            }
        }, ENTER_MAP_TIMEOUT_MS)

        MainScope().launch {
            val renderer = try {
                val appearance = save.toPlayerAppearance() ?: PlayerAppearance.DEFAULT
                GameRenderer(
                    assetLoader,
                    createThreeRenderer,
                    mapSlug,
                    appearance,
                    save.name,
                    // The saved weapon's own model where one is equipped -- the renderer
                    // builds the whole motion set from this slug, so handing it the starter
                    // while a rifle is equipped would draw the wrong class entirely.
                    weaponSlug = save.equippedWeapon
                        ?.let { weaponTierByName(it.tierName)?.modelSlug }
                        ?: starterWeaponSlug(appearance.characterClass),
                    save = liveSave,
                    onProgress = { updated ->
                        liveSave = updated
                        characterStore.save(updated)
                    },
                    onSetupError = {
                        // The world failed to build; go back rather than sit on a black screen.
                        gameRenderer?.let { r ->
                            r.stopRendering()
                            r.canvas.remove()
                            r.dispose()
                        }
                        gameRenderer = null
                        showCharacterSelect()
                    },
                    onAreaTransition = { destination ->
                        // Fired from inside the render loop, so the teardown is deferred to its
                        // own turn rather than disposing the renderer mid-frame.
                        MainScope().launch {
                            enterMap(liveSave, destination, "Entering ${areaName(destination)}...")
                        }
                    },
                )
            } catch (e: Throwable) {
                // GameRenderer's constructor synchronously creates a new WebGL context before its
                // own asset loading even starts; if that throws (context creation failing is the
                // main realistic cause -- e.g. a prior screen's context wasn't released and the
                // device's concurrent-context budget is exhausted), there was previously nothing
                // catching it, so the coroutine died right here and the loading screen this
                // function just showed stayed up forever with no way out. Falling back to
                // character select at least gives the user a screen to retry from instead of a
                // permanently stuck spinner.
                console.error("Failed to enter $mapSlug", e)
                showCharacterSelect()
                return@launch
            }

            // Anything thrown from here on would strand the loading screen too, so the rest of
            // the handover is guarded the same way.
            try {

                currentScreen?.dispose()
                currentScreen = null
                gameRenderer = renderer

                document.body!!.appendChild(renderer.canvas)

                fun resize() {
                    renderer.setSize(window.innerWidth, window.innerHeight)
                }

                window.addEventListener("resize", { resize() })
                resize()

                renderer.startRendering()
            } catch (e: Throwable) {
                console.error("Failed to start $mapSlug", e)
                renderer.stopRendering()
                renderer.canvas.remove()
                renderer.dispose()
                gameRenderer = null
                showCharacterSelect()
            }
        }
    }

    /** Counts entry attempts so a stale watchdog can't tear down a later, healthy one. */
    private var enterMapAttempt = 0

    /** Display name for a loading screen. Falls back to the slug for maps without one. */
    private fun areaName(mapSlug: String): String = when (mapSlug) {
        "pioneer2" -> "Pioneer 2"
        "forest01" -> "Forest 1"
        "forest02" -> "Forest 2"
        "bossArea1" -> "the Dragon's Lair"
        else -> mapSlug
    }

    private companion object {
        /** How long to wait for a world to start before giving up and going back. */
        const val ENTER_MAP_TIMEOUT_MS = 20_000
    }
}
