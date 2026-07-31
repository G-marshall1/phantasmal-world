package world.phantasmal.web.mobileGame.player

import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.psolib.fileFormats.ninja.NjKeyframeTrack
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.NjMotionData
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationAction
import world.phantasmal.web.externals.three.AnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.Object3D

/**
 * Drives the player skinned mesh's animation. Same createAnimationClip/AnimationMixer pipeline as
 * the Viewer's MeshRenderer, but the mixer is kept alive across clip switches (walk/idle/etc.)
 * instead of being rebuilt every time, since this plays continuously during gameplay.
 */
class PlayerAnimator(
    private val njObject: NjObject,
    root: Object3D,
) : TrackedDisposable() {
    private val mixer = AnimationMixer(root)
    private var currentAction: AnimationAction? = null
    private var currentClip: AnimationClip? = null
    private var currentMotion: NjMotion? = null

    // Every switch that had something to fade out gets its own independent timer here, since
    // switches can happen closer together than FADE_DURATION apart (e.g. a short attack clip
    // finishing right into another combo hit) -- assuming only one fade is ever in flight at a
    // time and hard-stopping "the previous one" on the next switch was cutting still-blending
    // actions off mid-fade, which is what caused the stutter/early-cutoff glitching.
    private val fadingOut = mutableListOf<FadingAction>()

    /**
     * No-ops if [njMotion] is already playing, so callers can call this every frame cheaply.
     * Crossfades into the new clip over [FADE_DURATION] seconds rather than cutting instantly.
     */
    fun playClip(njMotion: NjMotion) {
        if (njMotion === currentMotion) return

        val newClip = createAnimationClip(njObject, stripTranslation(njMotion))
        val newAction = mixer.clipAction(newClip)
        newAction.reset()
        newAction.play()

        val oldAction = currentAction
        val oldClip = currentClip

        if (oldAction != null && oldClip != null) {
            newAction.fadeIn(FADE_DURATION)
            oldAction.fadeOut(FADE_DURATION)
            fadingOut.add(FadingAction(oldAction, oldClip, FADE_DURATION))
        }

        currentAction = newAction
        currentClip = newClip
        currentMotion = njMotion
    }

    fun update(deltaTime: Double) {
        mixer.update(deltaTime)

        val iterator = fadingOut.iterator()

        while (iterator.hasNext()) {
            val fading = iterator.next()
            fading.timeRemaining -= deltaTime

            if (fading.timeRemaining <= 0) {
                fading.action.stop()
                mixer.uncacheAction(fading.clip)
                iterator.remove()
            }
        }
    }

    override fun dispose() {
        mixer.stopAllAction()
        super.dispose()
    }

    private class FadingAction(
        val action: AnimationAction,
        val clip: AnimationClip,
        var timeRemaining: Double,
    )

    companion object {
        private const val FADE_DURATION = 0.15

        /**
         * PSO's walk/run clips bake in forward translation on the root bone (presumably meant to
         * be authoritative in the original client-server movement model). Left in, it fights with
         * [CharacterController]'s own positioning and, since [AnimationMixer] just jumps back to
         * frame 0 on loop rather than accounting for cumulative motion, produces a walk-forward-
         * then-snap-back "slingshot" every loop. Bone position tracks only ever carry this kind of
         * overall translation (child bones are otherwise fixed-offset from their parent and only
         * rotate), so stripping every Position track keeps the limbs animating in place with no
         * assumption needed about which bone index is "the" root.
         */
        private fun stripTranslation(motion: NjMotion): NjMotion =
            NjMotion(
                motionData = motion.motionData.map { data ->
                    NjMotionData(data.tracks.filterNot { it is NjKeyframeTrack.Position })
                },
                frameCount = motion.frameCount,
                type = motion.type,
                interpolation = motion.interpolation,
                elementCount = motion.elementCount,
            )
    }
}
