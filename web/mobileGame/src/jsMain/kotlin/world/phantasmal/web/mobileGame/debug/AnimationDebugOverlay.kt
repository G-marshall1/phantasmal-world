package world.phantasmal.web.mobileGame.debug

import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.player.PlayerAnimator
import world.phantasmal.web.viewer.loading.AnimationAssetLoader

/**
 * Temporary dev tool: on-screen prev/next buttons to cycle through the 572 numbered
 * `animation_NNN.njm` files and visually identify which indices are idle/walk/run/etc, since
 * nothing in this codebase names them. Remove once the right indices are known and hardcoded.
 */
class AnimationDebugOverlay(
    container: HTMLElement,
    private val scope: CoroutineScope,
    private val animationAssetLoader: AnimationAssetLoader,
    private val playerAnimator: PlayerAnimator,
) : TrackedDisposable() {
    private var index = 0

    private val label = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;top:$TOP;left:50%;transform:translateX(-50%);" +
                "background:rgba(0,0,0,0.6);color:white;font:14px sans-serif;padding:4px 10px;" +
                "border-radius:6px;z-index:20;pointer-events:none;"
        container.appendChild(el)
    }

    private val jumpBackButton = button("«", container, "calc(50% - 160px)") { changeIndex(-25) }
    private val prevButton = button("<", container, "calc(50% - 105px)") { changeIndex(-1) }
    private val nextButton = button(">", container, "calc(50% + 65px)") { changeIndex(1) }
    private val jumpForwardButton = button("»", container, "calc(50% + 120px)") { changeIndex(25) }

    init {
        updateLabel()
        load()
    }

    override fun dispose() {
        label.remove()
        jumpBackButton.remove()
        prevButton.remove()
        nextButton.remove()
        jumpForwardButton.remove()
        super.dispose()
    }

    private fun changeIndex(delta: Int) {
        index = (index + delta + ANIMATION_COUNT) % ANIMATION_COUNT
        updateLabel()
        load()
    }

    private fun updateLabel() {
        label.textContent = "animation_${index.toString().padStart(3, '0')}"
    }

    private fun load() {
        val path = "/player/animation/animation_${index.toString().padStart(3, '0')}.njm"

        scope.launch {
            val motion = animationAssetLoader.loadAnimation(path)
            playerAnimator.playClip(motion)
        }
    }

    private fun button(
        text: String,
        container: HTMLElement,
        x: String,
        onClick: () -> Unit,
    ): HTMLElement =
        (document.createElement("button") as HTMLElement).also { el ->
            el.textContent = text
            el.style.cssText =
                "position:fixed;top:$TOP;left:$x;width:40px;height:40px;z-index:20;" +
                    "font:18px sans-serif;"
            el.addEventListener("click", { onClick() })
            container.appendChild(el)
        }

    companion object {
        private const val ANIMATION_COUNT = 572

        // Extra padding below the safe area, in case the status bar isn't hidden on some device/
        // launch path -- keeps these usable either way.
        private const val TOP = "calc(env(safe-area-inset-top, 0px) + 16px)"
    }
}
