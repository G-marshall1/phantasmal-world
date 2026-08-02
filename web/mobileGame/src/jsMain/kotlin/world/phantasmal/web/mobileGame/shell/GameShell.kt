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
import world.phantasmal.web.mobileGame.rendering.GameRenderer
import world.phantasmal.web.mobileGame.shell.titlescreen.TitleScreen
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader

/**
 * Orchestrates the meta-game flow: Loading -> Title -> Login -> Character Select/Create ->
 * gameplay (Pioneer 2 hub). Each screen is a raw-DOM [TrackedDisposable] (see LoadingScreen.kt
 * etc.); this just swaps one at a time.
 */
class GameShell(
    private val assetLoader: AssetLoader,
    private val createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
) {
    private val characterClassAssetLoader = CharacterClassAssetLoader(assetLoader)
    private val characterStore = CharacterStore()
    private var currentScreen: TrackedDisposable? = null
    private var nickname: String = characterStore.loadLastNickname() ?: ""

    fun start() {
        showLoading("Loading...")
        MainScope().launch { showTitle() }
    }

    private fun <T : TrackedDisposable> setScreen(screen: T): T {
        currentScreen?.dispose()
        currentScreen = screen
        return screen
    }

    private fun showLoading(message: String) {
        setScreen(LoadingScreen(document.body!!, message))
    }

    private fun showTitle() {
        setScreen(TitleScreen(document.body!!, onStart = ::showLogin))
    }

    private fun showLogin() {
        setScreen(
            LoginScreen(document.body!!, nickname) { nick ->
                nickname = nick
                characterStore.saveLastNickname(nick)
                showCharacterSelect()
            }
        )
    }

    private fun showCharacterSelect() {
        setScreen(
            CharacterSelectScreen(
                document.body!!,
                characterStore.loadAll(),
                onSelect = ::enterGame,
                onCreateNew = ::showCharacterCreate,
                onDelete = { save ->
                    characterStore.delete(save.id)
                    showCharacterSelect()
                },
            )
        )
    }

    private fun showCharacterCreate() {
        setScreen(
            CharacterCreateScreen(
                document.body!!,
                assetLoader,
                characterClassAssetLoader,
                createThreeRenderer,
                onCreate = { save ->
                    characterStore.save(save)
                    enterGame(save)
                },
                onCancel = ::showCharacterSelect,
            )
        )
    }

    private fun enterGame(save: CharacterSave) {
        showLoading("Entering Pioneer 2...")

        MainScope().launch {
            val appearance = save.toPlayerAppearance() ?: PlayerAppearance.DEFAULT
            val renderer = GameRenderer(assetLoader, createThreeRenderer, "pioneer2", appearance)

            currentScreen?.dispose()
            currentScreen = null

            document.body!!.appendChild(renderer.canvas)

            fun resize() {
                renderer.setSize(window.innerWidth, window.innerHeight)
            }

            window.addEventListener("resize", { resize() })
            resize()

            renderer.startRendering()
        }
    }
}
