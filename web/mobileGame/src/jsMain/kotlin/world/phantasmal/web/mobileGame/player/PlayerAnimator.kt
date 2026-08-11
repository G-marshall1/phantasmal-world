package world.phantasmal.web.mobileGame.player

import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.psolib.fileFormats.Vec3
import world.phantasmal.psolib.fileFormats.ninja.NjKeyframe
import world.phantasmal.psolib.fileFormats.ninja.NjKeyframeTrack
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.NjMotionData
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationAction
import world.phantasmal.web.externals.three.AnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.LoopOnce
import world.phantasmal.web.externals.three.LoopRepeat
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
     * Playback rate for the clip currently playing, 1.0 being the clip's own speed.
     *
     * Attacks set this so the swing animation lasts exactly as long as PSO's frame data says the
     * attack does (see AttackFrames): the clips are whatever length they were authored at, and
     * without rescaling they drift out of step with the timing that actually governs input.
     */
    var timeScale: Double = 1.0
        set(value) {
            field = value
            currentAction?.timeScale = value
        }

    /**
     * No-ops if [njMotion] is already playing, so callers can call this every frame cheaply.
     * Crossfades into the new clip over [fadeDuration] seconds rather than cutting instantly --
     * the default suits locomotion; an attack passes something much shorter, since PSO cuts into
     * a swing nearly instantly and a soft blend reads as sluggish frames.
     *
     * [restart] forces the clip back to frame 0 even when it's already the current one, and
     * [oneShot] plays it once and holds the final pose instead of looping. Both exist for attack
     * swings: a weapon whose combo reuses one clip for every step (the guns) must visibly fire
     * again on each tap, and a swing that runs slightly past its timer must hold its follow-
     * through rather than snapping back to frame 0 for a stray frame.
     */
    fun playClip(
        njMotion: NjMotion,
        fadeDuration: Double = FADE_DURATION,
        restart: Boolean = false,
        oneShot: Boolean = false,
    ) {
        if (njMotion === currentMotion) {
            // The scale may have changed while this same clip stayed up -- a combo replays one
            // attack clip at different speeds as it advances through its steps.
            currentAction?.timeScale = timeScale
            if (restart) currentAction?.reset()?.play()
            return
        }

        val clip = clipCache.getOrPut(njMotion) {
            createAnimationClip(njObject, stripTranslation(njMotion))
        }
        val newAction = mixer.clipAction(clip)
        newAction.reset()

        if (oneShot) {
            newAction.setLoop(LoopOnce, 1)
            newAction.clampWhenFinished = true
        } else {
            newAction.setLoop(LoopRepeat, Int.MAX_VALUE)
        }

        newAction.timeScale = timeScale
        newAction.play()

        currentAction?.let { oldAction ->
            newAction.fadeIn(fadeDuration)
            oldAction.fadeOut(fadeDuration)
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
        /** The bone the controller owns the ground position of -- see stripTranslation. */
        private const val ROOT_BONE_INDEX = 0

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
        /**
         * Removes the root bone's *horizontal* drift and nothing else.
         *
         * The character's position on the ground belongs to the controller, so a clip that also
         * walks the root forward fights it and the character slides. Dropping every position
         * track everywhere was too blunt a cure: it also threw away the root's vertical motion,
         * which is what lowers the body to the floor in a knockdown or a death, so a floored
         * character lay flat at standing height -- floating in mid-air. Non-root bones keep
         * their translation outright; a clip that shifts a limb was never fighting anything.
         */
        private fun stripTranslation(motion: NjMotion): NjMotion =
            NjMotion(
                motionData = motion.motionData.mapIndexed { boneIndex, data ->
                    if (boneIndex != ROOT_BONE_INDEX) return@mapIndexed data
                    NjMotionData(
                        data.tracks.map { track ->
                            if (track !is NjKeyframeTrack.Position) track
                            else NjKeyframeTrack.Position(
                                track.keyframes.map { key ->
                                    NjKeyframe.Vector(
                                        key.frame,
                                        Vec3(0f, key.value.y, 0f),
                                    )
                                }
                            )
                        }
                    )
                },
                frameCount = motion.frameCount,
                type = motion.type,
                interpolation = motion.interpolation,
                elementCount = motion.elementCount,
            )
    }
}
