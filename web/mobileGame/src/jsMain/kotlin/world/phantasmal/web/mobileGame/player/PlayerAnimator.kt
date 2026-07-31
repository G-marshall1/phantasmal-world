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

    // Player only ever cycles a small, fixed set of motions (idle/walk/3 attacks), so caching one
    // AnimationClip per NjMotion and reusing it keeps AnimationMixer.clipAction returning the same
    // cached AnimationAction every time instead of registering a fresh one per switch. This used
    // to build a brand-new AnimationClip on every single playClip call and clean the old one up
    // via mixer.uncacheAction() after its fade-out finished -- under fast repeat switching (e.g.
    // mashing the attack button through the combo) that tore down an action's bindings while the
    // mixer's own active-bindings bookkeeping still expected it, throwing deep inside
    // AnimationMixer.update ("Cannot read properties of undefined (reading 'apply')") and killing
    // the whole render loop. Reusing cached actions and letting fadeIn/fadeOut do their normal
    // crossfade -- the mixer's actual designed use case -- sidesteps that path entirely.
    private val clipCache = mutableMapOf<NjMotion, AnimationClip>()
    private var currentAction: AnimationAction? = null
    private var currentMotion: NjMotion? = null

    /**
     * No-ops if [njMotion] is already playing, so callers can call this every frame cheaply.
     * Crossfades into the new clip over [FADE_DURATION] seconds rather than cutting instantly.
     */
    fun playClip(njMotion: NjMotion) {
        if (njMotion === currentMotion) return

        val clip = clipCache.getOrPut(njMotion) {
            createAnimationClip(njObject, stripTranslation(njMotion))
        }
        val newAction = mixer.clipAction(clip)
        newAction.reset()
        newAction.play()

        currentAction?.let { oldAction ->
            newAction.fadeIn(FADE_DURATION)
            oldAction.fadeOut(FADE_DURATION)
        }

        currentAction = newAction
        currentMotion = njMotion
    }

    fun update(deltaTime: Double) {
        mixer.update(deltaTime)
    }

    override fun dispose() {
        mixer.stopAllAction()
        super.dispose()
    }

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
