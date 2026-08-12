package world.phantasmal.web.mobileGame.player

import kotlin.math.atan2
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.PSO_FRAME_RATE_DOUBLE
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.LoopOnce
import world.phantasmal.web.externals.three.LoopRepeat
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.world.WallCollider
import world.phantasmal.web.mobileGame.world.findGroundHeight

/**
 * Gives one enemy a minimal chase-and-melee brain: outside [aggroRadius] it just idles in place
 * (looping [walkMotion] -- there's no universal "wait" clip name across every enemy family, see
 * EnemySpecs.kt, so reusing the walk clip stationary is the simplest thing that works for all of
 * them); within [aggroRadius] but outside [attackRange] it walks straight at the player, using the
 * same [WallCollider]/[findGroundHeight] the player's own [CharacterController] is built on so it
 * doesn't clip through walls or float off terrain; within [attackRange] it stops, faces the
 * player, and attacks on a [attackCooldown] timer. No pathfinding -- straight-line chase is enough
 * for these open field areas, and wall collision alone keeps it from clipping through geometry
 * even if the straight line briefly grazes a wall.
 */
class EnemyAI(
    private val mesh: SkinnedMesh,
    private val njObject: NjObject,
    private val mixer: AnimationMixer,
    private val walkMotion: NjMotion,
    private val attackMotion: NjMotion,
    private val wallCollider: WallCollider,
    private val walkable: Object3D,
    bSphereRadius: Double,
    /** Standing clip for outside aggro range. Null falls back to [walkMotion], as before. */
    private val waitMotion: NjMotion? = null,
    /** Flinch clip. Null means this enemy simply doesn't react to being hit. */
    private val damageMotion: NjMotion? = null,
    /** Death clip. Null means the body is removed the moment hp runs out. */
    private val deathMotion: NjMotion? = null,
    /** Chase clip. Null keeps [walkMotion] while closing distance. */
    private val runMotion: NjMotion? = null,
    /** Played once when the player first enters aggro range. */
    private val wakeUpMotion: NjMotion? = null,
    /**
     * This species' own cylinder radius and strike distance, in PSO units -- see EnemyStats. Both
     * default to a Booma's, which is the Forest's baseline foot soldier.
     */
    private val hitboxUnits: Double = 1.2,
    private val attackRangeUnits: Double = 1.4,
    /**
     * World units per PSO unit. Anchored on the *player's* cylinder, not this enemy's -- PSO
     * states every range relative to the player's 1.0-unit hitbox, so an enemy's own size must
     * not change what a "unit" means. See psoUnit.
     */
    private val unitScale: Double = 0.0,
    /** Alternate strike clip, so repeated swings don't replay the same claw -- see [attackMotion]. */
    private val attackMotionAlt: NjMotion? = null,
    /** Longer stagger clip for a heavy hit. Falls back to the flinch when a species has none. */
    private val stunMotion: NjMotion? = null,
    /**
     * The ambush entrance: Boomas burst out of the ground rather than simply being there. Played
     * once when the wave spawns, before the enemy will move or strike.
     */
    private val appearMotion: NjMotion? = null,
    /** Played when a single blow takes a quarter of this enemy's health -- see [onKnockedDown]. */
    private val knockDownMotion: NjMotion? = null,
    /**
     * A rooted enemy: it never walks and never strikes. A Monest is a hive bolted to the ground
     * that produces Mothmants; it has no attack of its own, and letting the ordinary chase logic
     * run made it march across the map and claw at the player, which is nothing like the real
     * thing.
     */
    private val isStationary: Boolean = false,
    /**
     * A rooted species that still fights: a Poison Lily never leaves its patch but bites
     * anything that steps into reach. Plain [isStationary] (the Monest) neither walks nor
     * strikes.
     */
    private val strikesWhileRooted: Boolean = false,
    /** Hover height for flying species, in PSO units -- a Mothmant never touches the ground. */
    hoverUnits: Double = 0.0,
    /** This species' own pace as a multiple of the shared chase rate -- see EnemyStats. */
    private val speedFactor: Double = 1.0,
    /** A hive's deploy-and-collapse clips. Null for everything that isn't one. */
    private val hiveClips: world.phantasmal.web.mobileGame.world.HiveClips? = null,
    /** How close the player must come before a hanging hive drops, in PSO units. */
    private val hiveDeployRangeUnits: Double = 18.0,
    /**
     * A ranged attacker's reach, in PSO units. Zero means melee only. Beyond melee but inside
     * this, the species fires instead of closing -- a Nano Dragon's nano laser carries across a
     * whole cave room, a Poison Lily's venom spit rather less far.
     */
    private val rangedRangeUnits: Double = 0.0,
    /** The clip played when firing. Falls back to the melee swing. */
    private val rangedMotion: NjMotion? = null,
    /** Fires the actual shot -- the host spawns the projectile and resolves the hit. */
    private val onRangedAttack: (() -> Unit)? = null,
    /**
     * How close is too close, in PSO units: inside this the species backs away rather than
     * trading blows. The Nano Dragon breaks off and resumes shooting from a safe distance.
     */
    private val fleeRangeUnits: Double = 0.0,
) {
    private val radius = bSphereRadius * RADIUS_FACTOR
    private val verticalTolerance = bSphereRadius * VERTICAL_TOLERANCE_FACTOR

    /**
     * Chase speed, anchored on the *player's* cylinder like every other distance here -- not on
     * this enemy's own bounding sphere, which is what it used to use. A Hildebear's model is far
     * larger than a Booma's, so that made the boss genuinely faster than everything else and
     * faster than the player could walk away from: it stayed glued to the player's back.
     * Every species now closes at the same rate; a species that should be quick can carry its
     * own multiplier when there's a reason for one.
     */
    private val speed =
        (if (unitScale > 0) unitScale / CharacterController.HITBOX_RADIUS_FACTOR * SPEED_FACTOR
        else bSphereRadius * SPEED_FACTOR) * speedFactor

    private val aggroRadius = bSphereRadius * AGGRO_RADIUS_FACTOR

    /**
     * Centre-to-centre distance at which this enemy stops walking and strikes: its own reach plus
     * the player's cylinder, since PSO measures reach to the target's edge. A Gigobooma's 1.6
     * therefore genuinely out-ranges a Saber's 1.5 -- charging one head-on loses the exchange.
     */
    private val attackRange = (attackRangeUnits + PLAYER_HITBOX_UNITS) * unitScale
    private val hoverHeight = hoverUnits * unitScale

    /** How close it can physically get before bodies overlap. */
    private val bodyRadius = hitboxUnits * unitScale

    private var attackCooldownRemaining = 0.0

    private val rangedRange = rangedRangeUnits * unitScale
    private val fleeRange = fleeRangeUnits * unitScale
    private var rangedCooldownRemaining = RANGED_FIRST_DELAY

    /**
     * True while the player is in this enemy's own room. PSO wakes a room's monsters when you
     * walk in and loses interest when you leave, rather than each one waiting for you to cross
     * its personal radius -- set per frame by the host from the spawn table's section ids.
     */
    var roomAggro: Boolean = false

    /**
     * Counts down the whole swing clip. While positive the enemy is committed: it doesn't turn,
     * walk, or pick a different clip until the swing has played out.
     */
    private var attackAnimRemaining = 0.0

    /**
     * Counts down to the swing's contact point -- the moment the claw actually arrives, at
     * [CONTACT_FRACTION] of the clip. Damage used to land the instant the swing *began*, which
     * both hurt the player before the animation had visibly moved and made the windup
     * undodgeable; now [update] reports the hit at contact, and only if the player is still in
     * reach then.
     */
    private var attackContactRemaining = 0.0

    /** Flips per swing so a two-clawed enemy alternates sides -- see [attackMotionAlt]. */
    private var useAltClaw = false

    /** Counts down the stagger a heavy hit puts this enemy in -- see [onStunned]. */
    private var stunRemaining = 0.0
    private var damageRemaining = 0.0
    private var wakeUpRemaining = 0.0

    /** Reset when the player leaves aggro range, so re-approaching triggers the notice again. */
    private var hasNoticedPlayer = false

    /** Length of [deathMotion], or 0 if this enemy has none. */
    val deathDuration: Double =
        deathMotion?.let { (it.frameCount - 1) / PSO_FRAME_RATE_DOUBLE } ?: .0
    private var currentMotion: NjMotion? = null
    private val toPlayer = Vector3()

    // See PlayerAnimator's own clipCache for why: reusing one AnimationClip (and, by extension,
    // one cached AnimationAction) per motion instead of creating a fresh one on every switch
    // avoids a real Three.js AnimationMixer crash under fast repeated switching.
    private val clipCache = mutableMapOf<NjMotion, AnimationClip>()

    /** Counts down the spawn entrance, during which the enemy neither moves nor attacks. */
    private var appearRemaining = 0.0

    init {
        if (appearMotion != null) {
            appearRemaining = (appearMotion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
            playClip(appearMotion, oneShot = true)
        } else {
            playClip(walkMotion, timeScale = IDLE_ANIM_SCALE)
        }
    }

    /**
     * Plays the flinch clip, if this enemy has one. Called when the player's swing connects.
     *
     * A hit that arrives while the enemy is *already* reacting is absorbed rather than starting
     * the clip again. Restarting it per connecting swing was catastrophic: a saber combo lands
     * about every 0.4s and the flinch runs 0.67s, so every hit reset the animation to frame
     * zero and refreshed the AI's hold -- the enemy never visibly flinched, never finished the
     * clip, and never got another turn. It simply stood there rigid for as long as the player
     * kept attacking. [FLINCH_RECOVERY] then guarantees a window to act in before it can be
     * made to flinch again, so a fast weapon can't stun-lock anything indefinitely.
     */
    fun onDamaged() {
        // A hive that has been knocked over flinches from the floor, not on its feet.
        val motion = (if (hiveIsDown) hiveClips?.downDamage else null) ?: damageMotion ?: return
        // A full stagger owns the body; a flinch does not.
        if (stunRemaining > 0) return

        // Every hit shows a reaction, even one landing mid-flinch. A weapon faster than the
        // clip used to be swallowed by a lockout, so the enemy stood there absorbing hits
        // silently -- the original instead *quickens* the reaction so it can play again, which
        // is what reads as being hit twice in quick succession. Speeding it up rather than
        // restarting it at full length is also what stops a fast weapon from freezing an enemy
        // in place forever.
        val clipSeconds = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
        val repeat = damageRemaining > 0
        val speed = if (repeat) FLINCH_REPEAT_SPEEDUP else 1.0
        damageRemaining = clipSeconds / speed
        flinchLockoutRemaining = .0
        // Taking a hit interrupts a swing in progress -- the windup is lost along with the turn.
        cancelSwing()
        playClip(motion, oneShot = true, timeScale = speed)
    }

    /**
     * Staggers the enemy: a longer hold than [onDamaged]'s flinch, played from the species' own
     * stun clip. Used for a heavy hit, where the reward for the risk is that the enemy loses its
     * turn rather than trading with you.
     */
    fun onStunned() {
        val motion = stunMotion ?: return onDamaged()
        // Same rule as the flinch, and for the same reason -- but the recovery window matters
        // even more here, because a stagger is a full second long.
        //
        // Knockdown fires at a quarter of maximum health, so which enemies this affects is a
        // matter of their health pool: a 60 HP Booma is knocked down by almost every saber hit
        // while an 85 HP Gobooma is barely ever, which is why Boomas alone stood locked in
        // place. Without a recovery window the next hit re-staggered them the instant the last
        // one ended, and they never got a frame in which to move or swing.
        if (stunRemaining > 0 || flinchLockoutRemaining > 0) return

        stunRemaining = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
        flinchLockoutRemaining = stunRemaining + FLINCH_RECOVERY
        attackCooldownRemaining = stunRemaining + ATTACK_COOLDOWN
        cancelSwing()
        playClip(motion, oneShot = true)
    }

    /**
     * Shoves the enemy directly away from [from] and staggers it. This is what a heavy attack
     * buys: the target loses ground and its turn, where a normal hit only makes it flinch.
     *
     * Rooted enemies keep their footing -- a Monest is bolted down -- but still take the stagger.
     */
    fun onPushedBack(from: Vector3) {
        if (!isStationary) {
            toPlayer.subVectors(mesh.position, from)
            toPlayer.y = .0
            val distance = toPlayer.length()

            if (distance > MIN_DISTANCE) {
                val push = PUSHBACK_UNITS * unitScale
                mesh.position.x += toPlayer.x / distance * push
                mesh.position.z += toPlayer.z / distance * push
                wallCollider.resolve(mesh.position, radius, verticalTolerance)
                findGroundHeight(walkable, mesh.position.x, mesh.position.z)?.let {
                    mesh.position.y = it + hoverHeight
                }
            }
        }

        onStunned()
    }

    /**
     * Knocked off its feet by a single blow taking a quarter or more of its health. Holds longer
     * than a stagger and plays the species' knockdown clip where it has one.
     */
    fun onKnockedDown() {
        val motion = knockDownMotion ?: return onStunned()
        stunRemaining = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
        attackCooldownRemaining = stunRemaining + ATTACK_COOLDOWN
        cancelSwing()
        playClip(motion, oneShot = true)
    }

    /**
     * Plays this enemy's attack-slot clip as a one-shot without any strike semantics, held like a
     * flinch so the ordinary idle doesn't stomp it on the next frame. For a Monest hive the
     * attack slot carries its "exit" (mouth-open release) clip, and this is what the hive
     * production logic plays each time a Mothmant emerges.
     */
    fun onProduce() {
        // Upright it opens and casts them off the top; knocked over it works from the wreck.
        val motion = if (hiveIsDown) hiveClips?.downRelease ?: attackMotion else attackMotion
        produceRemaining = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
        playClip(motion, oneShot = true)
    }

    private var produceRemaining = 0.0

    /**
     * Holds this enemy inert for [seconds] -- the freeze/paralysis/confusion specials. Rides the
     * stagger hold: the enemy keeps whatever clip it was playing but neither moves nor strikes.
     * An approximation of the real statuses (no ice shell or wander), documented as such.
     */
    fun onStatusHeld(seconds: Double) {
        stunRemaining = maxOf(stunRemaining, seconds)
        attackCooldownRemaining = maxOf(attackCooldownRemaining, seconds + ATTACK_COOLDOWN)
        cancelSwing()
    }

    /**
     * A revived Dubchic getting back on its feet: plays the getting-up clip and clears every
     * combat timer so the machine comes back calm rather than mid-swing.
     */
    fun onRevived(reviveMotion: NjMotion?) {
        attackAnimRemaining = 0.0
        attackContactRemaining = 0.0
        attackCooldownRemaining = ATTACK_COOLDOWN
        stunRemaining = 0.0
        wakeUpRemaining = 0.0
        inMeleeStance = false
        val motion = reviveMotion
        if (motion != null) {
            // Held briefly as a one-shot so the rise plays out before the brain resumes.
            wakeUpRemaining = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
            playClip(motion, oneShot = true)
        } else {
            playClip(waitMotion ?: walkMotion, timeScale = IDLE_ANIM_SCALE)
        }
    }

    /** Switches to the death clip, if this enemy has one. */
    fun onDeath() {
        // One-shot with clamp, so the body holds its final collapsed pose until it's removed
        // instead of looping the fall.
        deathMotion?.let { playClip(it, oneShot = true) }
    }

    /**
     * Shoves this enemy clear of another body, then puts it back on the ground and out of any
     * wall it was pushed into. Bodies used to pass straight through one another, so a pack
     * closing on the player collapsed into one spot and several enemies attacked from inside
     * each other. Keeping them apart is also what makes them break formation and come around
     * an obstruction, since a blocked enemy is displaced sideways by the ones behind it.
     *
     * Rooted species don't move: a Monest is bolted down and the pack flows around it.
     */
    fun separate(deltaX: Double, deltaZ: Double) {
        if (isStationary) return
        moveBy(deltaX, deltaZ)
    }

    /** Moves the body, then puts it back on the ground and out of any wall it entered. */
    private fun moveBy(deltaX: Double, deltaZ: Double) {
        mesh.position.x += deltaX
        mesh.position.z += deltaZ
        wallCollider.resolve(mesh.position, radius, verticalTolerance)
        findGroundHeight(walkable, mesh.position.x, mesh.position.z)?.let {
            mesh.position.y = it + hoverHeight
        }
    }

    /** Plays the firing clip and lets the host put the shot in the world. */
    private fun fireRanged() {
        val motion = rangedMotion ?: attackMotion
        val length = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE / ATTACK_ANIM_SCALE
        attackAnimRemaining = length
        // No melee contact: the projectile carries the damage.
        attackContactRemaining = 0.0
        playClip(motion, oneShot = true, timeScale = ATTACK_ANIM_SCALE)
        onRangedAttack?.invoke()
    }

    /**
     * A one-line summary of what this enemy's animation layer is actually doing, for the
     * `?diag?` chat command. Reading state off the device beats another round of guessing at
     * which clip failed to play.
     */
    fun debugState(): String {
        fun t(v: Double) = ((v * 100).toInt() / 100.0).toString()
        return buildString {
            append("clips dmg=").append(damageMotion != null)
            append(" death=").append(deathMotion != null)
            append(" stun=").append(stunMotion != null)
            append(" hive=").append(hiveClips != null)
            append(" | deathDur=").append(t(deathDuration))
            append(" phase=").append(if (hiveClips != null) hivePhase.name else "-")
            append(" | holds dmg=").append(t(damageRemaining))
            append(" stun=").append(t(stunRemaining))
            append(" prod=").append(t(produceRemaining))
            append(" atk=").append(t(attackAnimRemaining))
            append(" | playing=").append(currentMotion?.frameCount ?: -1).append("f")
        }
    }

    /** Abandons a swing in progress -- the flinch/stagger that caused it takes the clip over. */
    private fun cancelSwing() {
        attackAnimRemaining = 0.0
        attackContactRemaining = 0.0
    }

    /** Returns true on exactly the frame this enemy's attack lands, for the caller to apply damage. */
    fun update(
        deltaTime: Double,
        playerPosition: Vector3,
        /** Fraction of maximum health remaining -- only a hive uses it, to decide when to fall. */
        healthFraction: Double = 1.0,
    ): Boolean {
        attackCooldownRemaining -= deltaTime

        // A flyer holds its height whether or not it is going anywhere. Hover used to be
        // applied only by the movement helpers, so a rooted one -- a Sorcerer, its Bees --
        // simply sat at whatever height it was placed at, which is the floor. Re-seating it
        // here also catches a flyer moved by something other than its own AI, like the
        // Sorcerer's teleport.
        if (hoverHeight > 0) {
            findGroundHeight(walkable, mesh.position.x, mesh.position.z)?.let {
                mesh.position.y = it + hoverHeight
            }
        }

        // Runs down whether or not the enemy is reacting, so the window to act always arrives.
        if (flinchLockoutRemaining > 0) flinchLockoutRemaining -= deltaTime

        // The spawn entrance runs to completion before anything else -- an enemy still climbing
        // out of the ground shouldn't be swinging.
        if (appearRemaining > 0) {
            appearRemaining -= deltaTime
            return false
        }

        // A stagger holds longer than a flinch and can't be interrupted by either.
        if (stunRemaining > 0) {
            stunRemaining -= deltaTime
            return false
        }

        // Let a flinch play out before the usual walk/attack logic takes the clip back over.
        if (damageRemaining > 0) {
            damageRemaining -= deltaTime
            return false
        }

        // Same for the one-shot notice: hold it, and don't start chasing mid-animation.
        if (wakeUpRemaining > 0) {
            wakeUpRemaining -= deltaTime
            return false
        }

        // And the hive's release clip -- see onProduce.
        if (produceRemaining > 0) {
            produceRemaining -= deltaTime
            return false
        }


        toPlayer.subVectors(playerPosition, mesh.position)
        toPlayer.y = .0
        val distance = toPlayer.length()

        // A hive runs its own life cycle rather than the chase/strike logic below.
        if (updateHive(deltaTime, distance, healthFraction)) return false

        // A swing in progress plays out before anything else gets a say: the enemy is committed
        // to the direction it swung in (no turning to track the player mid-swing) and its clip
        // isn't stomped by walk/idle. The blow itself lands at the clip's contact point -- and
        // only if the player is still in reach then, so backing out of the windup dodges it,
        // exactly the read-and-react rhythm the real game's melee has.
        if (attackAnimRemaining > 0) {
            attackAnimRemaining -= deltaTime

            var landed = false
            if (attackContactRemaining > 0) {
                attackContactRemaining -= deltaTime
                if (attackContactRemaining <= 0 &&
                    distance <= attackRange * CONTACT_RANGE_GRACE
                ) {
                    landed = true
                }
            }
            return landed
        }

        if (distance > MIN_DISTANCE && (!isStationary || strikesWhileRooted)) {
            mesh.rotation.y = atan2(toPlayer.x, toPlayer.z)
        }

        val strikeReach = attackRange * (if (inMeleeStance) MELEE_EXIT_MARGIN else 1.0)

        rangedCooldownRemaining -= deltaTime

        // A ranged species opens fire the moment the player is past its melee reach but still
        // inside its own -- and a Nano Dragon that has been closed down backs off to get its
        // distance again rather than standing there being hit.
        val engaged = roomAggro || distance <= aggroRadius
        val wantsToFlee = fleeRange > 0 && distance < fleeRange && engaged
        val canShoot = rangedRange > 0 && engaged && distance > strikeReach && distance <= rangedRange

        if (engaged && (canShoot || wantsToFlee) && !isStationary) {
            mesh.rotation.y = atan2(toPlayer.x, toPlayer.z)
        }

        if (wantsToFlee && !isStationary) {
            // Backing away, still facing the player.
            val step = speed * FLEE_SPEED_FACTOR * deltaTime
            if (distance > MIN_DISTANCE) {
                moveBy(-toPlayer.x / distance * step, -toPlayer.z / distance * step)
            }
            if (rangedCooldownRemaining <= 0) {
                rangedCooldownRemaining = RANGED_COOLDOWN
                fireRanged()
            } else {
                playClip(runMotion ?: walkMotion, timeScale = CHASE_ANIM_SCALE)
            }
            return false
        }

        if (canShoot && rangedCooldownRemaining <= 0) {
            rangedCooldownRemaining = RANGED_COOLDOWN
            fireRanged()
            return false
        }

        when {
            // A hive stays where it was placed: no chase, no strike, just its idle loop. A
            // rooted STRIKER (a Lily) also never moves, but falls through to the strike branch
            // when the player steps into its reach.
            isStationary && !(strikesWhileRooted && distance <= strikeReach) -> {
                playClip(waitMotion ?: walkMotion, timeScale = IDLE_ANIM_SCALE)
            }

            // Hysteresis: once at striking distance the enemy holds that stance until the
            // player is clearly away, rather than switching the instant they cross the line.
            // Walking backwards out of a Hildebear's reach used to sit exactly on the boundary,
            // flipping between the chase clip and the ready clip every frame -- each switch
            // restarting the animation from frame zero, which is what made it judder in place.
            distance <= strikeReach -> {
                inMeleeStance = true
                if (attackCooldownRemaining <= 0) {
                    attackCooldownRemaining = ATTACK_COOLDOWN

                    // Alternate claws where the species has both, so a flurry reads as a real
                    // combination rather than the same swing looping.
                    val alt = attackMotionAlt
                    val motion = if (alt != null && useAltClaw) alt else attackMotion
                    useAltClaw = !useAltClaw

                    // The swing runs slower than authored, so its own timings stretch to match
                    // -- contact still lands at the same point *within* the animation.
                    val length =
                        (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE / ATTACK_ANIM_SCALE
                    attackAnimRemaining = length
                    attackContactRemaining = length * CONTACT_FRACTION
                    playClip(motion, oneShot = true, timeScale = ATTACK_ANIM_SCALE)
                } else {
                    // Between swings, stand at the ready. This used to leave the last attack clip
                    // looping through the whole cooldown, which read as a constant flail with
                    // damage attached to none of it.
                    playClip(waitMotion ?: walkMotion, timeScale = IDLE_ANIM_SCALE)
                }
            }

            // Room aggro: everything in the player's own room comes for them, whatever the
            // distance, and loses interest once they leave it.
            distance <= aggroRadius || roomAggro -> {
                inMeleeStance = false
                // First time the player comes within range, play the notice before giving chase.
                if (!hasNoticedPlayer) {
                    hasNoticedPlayer = true

                    wakeUpMotion?.let {
                        wakeUpRemaining = (it.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
                        playClip(it, oneShot = true)
                        return false
                    }
                }

                playClip(runMotion ?: walkMotion, timeScale = CHASE_ANIM_SCALE)

                if (distance > MIN_DISTANCE) {
                    mesh.position.x += toPlayer.x / distance * speed * deltaTime
                    mesh.position.z += toPlayer.z / distance * speed * deltaTime
                }

                wallCollider.resolve(mesh.position, radius, verticalTolerance)
                findGroundHeight(walkable, mesh.position.x, mesh.position.z)?.let {
                    mesh.position.y = it + hoverHeight
                }
            }

            else -> {
                hasNoticedPlayer = false
                inMeleeStance = false
                playClip(waitMotion ?: walkMotion, timeScale = IDLE_ANIM_SCALE)
            }
        }

        return false
    }

    /**
     * [oneShot] plays [motion] once from frame 0 and holds its final pose (attack, flinch, death
     * -- anything that happens and is over) instead of the default looping used for walk/idle.
     * A one-shot always restarts, even when it's already the current clip: the second flinch in
     * a row, or a repeat of the same swing, must visibly play again rather than silently
     * continuing from wherever the last one finished -- which is exactly what "enemies attack
     * with no animation at all" looked like.
     */
    /**
     * [timeScale] is how fast the clip runs: below 1 slows it down. The authored clips are paced
     * for the real game's own movement rates, and played at full speed against this project's
     * chase speed every enemy read as sprinting flat out, with Boomas swinging so fast the
     * flurry looked continuous. See CHASE_ANIM_SCALE and ATTACK_ANIM_SCALE.
     */
    private fun playClip(motion: NjMotion, oneShot: Boolean = false, timeScale: Double = 1.0) {
        // A clip with no frames has no tracks to drive the skeleton: playing it stops whatever
        // was running and leaves the body stuck in its last pose, which reads exactly like the
        // enemy freezing. Better to keep the current animation than to freeze on an empty one.
        if (motion.frameCount <= 1) return
        if (motion === currentMotion && !oneShot && lastTimeScale == timeScale) return

        val clip = clipCache.getOrPut(motion) { createAnimationClip(njObject, motion) }
        mixer.stopAllAction()
        val action = mixer.clipAction(clip)
        action.reset()
        action.timeScale = timeScale

        if (oneShot) {
            action.setLoop(LoopOnce, 1)
            action.clampWhenFinished = true
        } else {
            action.setLoop(LoopRepeat, Int.MAX_VALUE)
        }

        action.play()
        currentMotion = motion
        lastTimeScale = timeScale
    }

    /** The rate the current clip is running at, so a rate change alone can restart it. */
    private var lastTimeScale = 1.0

    /** True while standing at striking distance -- see the hysteresis in [update]. */
    private var inMeleeStance = false

    /** Blocks a new flinch until the enemy has had a chance to act -- see [onDamaged]. */
    private var flinchLockoutRemaining = 0.0

    /**
     * A hive's life, in order. It hangs closed in the canopy until someone comes near, drops and
     * sets down, works upright until it's beaten past [HIVE_COLLAPSE_FRACTION] of its health,
     * then falls and keeps releasing from the floor until it's finished.
     */
    private enum class HivePhase { HANGING, LANDING, DEPLOYED, COLLAPSING, DOWNED }

    private var hivePhase = HivePhase.HANGING
    private var hivePhaseRemaining = 0.0

    /** True once the hive is set down and able to work -- upright or on its side. */
    val hiveCanProduce: Boolean
        get() = hiveClips == null ||
            hivePhase == HivePhase.DEPLOYED ||
            hivePhase == HivePhase.DOWNED

    /** True once it has been knocked over, which is where it releases from. */
    val hiveIsDown: Boolean get() = hivePhase == HivePhase.DOWNED

    /**
     * Drives the hive's own state machine. Returns true when it has handled the frame, so the
     * ordinary chase/strike logic stays out of the way of a species that does neither.
     */
    private fun updateHive(deltaTime: Double, distance: Double, healthFraction: Double): Boolean {
        val clips = hiveClips ?: return false

        if (hivePhaseRemaining > 0) {
            hivePhaseRemaining -= deltaTime
            if (hivePhaseRemaining > 0) return true

            // Whatever one-shot just finished decides what it becomes.
            hivePhase = when (hivePhase) {
                HivePhase.LANDING -> HivePhase.DEPLOYED
                HivePhase.COLLAPSING -> HivePhase.DOWNED
                else -> hivePhase
            }
        }

        when (hivePhase) {
            HivePhase.HANGING -> {
                playClip(clips.hang, timeScale = IDLE_ANIM_SCALE)
                if (distance <= hiveDeployRangeUnits * unitScale) {
                    hivePhase = HivePhase.LANDING
                    hivePhaseRemaining = (clips.land.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
                    playClip(clips.land, oneShot = true)
                }
            }

            // Held by the countdown above; nothing to do but let the clip run.
            HivePhase.LANDING, HivePhase.COLLAPSING -> Unit

            HivePhase.DEPLOYED -> {
                if (healthFraction <= HIVE_COLLAPSE_FRACTION) {
                    hivePhase = HivePhase.COLLAPSING
                    hivePhaseRemaining = (clips.down.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
                    playClip(clips.down, oneShot = true)
                } else {
                    playClip(waitMotion ?: walkMotion, timeScale = IDLE_ANIM_SCALE)
                }
            }

            HivePhase.DOWNED -> playClip(clips.downWait, timeScale = IDLE_ANIM_SCALE)
        }

        return true
    }

    companion object {
        private const val RADIUS_FACTOR = 0.3
        private const val VERTICAL_TOLERANCE_FACTOR = 3.0
        /**
         * Chase speed as a fraction of the player's run speed. On Normal a Booma chases at 0.08
         * units per frame against the player's 0.14, so roughly 57% -- slow enough that running
         * away actually works. Derived from the player's own speed constant so a retune of
         * movement pace keeps this authentic relationship rather than silently changing how
         * escapable enemies are. Later difficulties raise the ratio until Ultimate outpaces the
         * player entirely; there's no difficulty setting yet, so Normal is what's modelled.
         */
        private const val SPEED_FACTOR = CharacterController.SPEED_FACTOR * (0.058 / 0.14)
        /** The player's cylinder is 1.0 units across every class -- see psoUnit. */
        private const val PLAYER_HITBOX_UNITS = 1.0

        /** How far a heavy hit shoves a target, in PSO units. This project's own figure. */
        private const val PUSHBACK_UNITS = 0.8

        private const val AGGRO_RADIUS_FACTOR = 8.0
        private const val ATTACK_RANGE_FACTOR = 2.5
        private const val ATTACK_COOLDOWN = 2.1
        private const val MIN_DISTANCE = 0.001

        /**
         * How far into the swing clip the blow connects. There's no per-species contact frame in
         * the source data to transcribe, so this is authored by eye against the Booma family's
         * claw swings: the arm arrives just before mid-clip, with the rest being follow-through.
         */
        private const val CONTACT_FRACTION = 0.45

        /**
         * How fast enemy clips run. The authored motions are paced for the real game's own
         * movement rates; at full speed against this project's chase speed everything read as
         * sprinting flat out, and a Booma's swing came round so fast the flurry looked
         * continuous. Chase and swing are slowed most, idles only a little.
         */
        /**
         * How far past its reach the player must get before an enemy drops out of its melee
         * stance. Anything at or near 1.0 lets the two states alternate frame by frame.
         */
        private const val MELEE_EXIT_MARGIN = 1.35

        /** Breathing room after a reaction before the enemy can be made to flinch again. */
        private const val FLINCH_RECOVERY = 0.4

        /**
         * How much faster the flinch replays when a hit lands while one is already running.
         * See onDamaged: this is what lets a fast weapon show a second reaction without the
         * enemy being stunlocked by an ever-restarting clip.
         */
        private const val FLINCH_REPEAT_SPEEDUP = 2.0

        /** Ranged fire: the pause between shots, and the grace before the first one. */
        private const val RANGED_COOLDOWN = 3.0
        private const val RANGED_FIRST_DELAY = 1.2

        /** How fast a fleeing species backs off, against its own chase speed. */
        private const val FLEE_SPEED_FACTOR = 0.9

        /** Health at which a hive is knocked off its feet and works from the ground. */
        private const val HIVE_COLLAPSE_FRACTION = 0.5

        private const val CHASE_ANIM_SCALE = 0.62
        private const val ATTACK_ANIM_SCALE = 0.7
        private const val IDLE_ANIM_SCALE = 0.85

        /**
         * Reach multiplier applied at the contact check, not the decision to swing. Slightly
         * generous so a player drifting at the range boundary still gets clipped -- dodging a
         * windup should take a deliberate step away, not a millimetre of drift.
         */
        private const val CONTACT_RANGE_GRACE = 1.3
    }
}
