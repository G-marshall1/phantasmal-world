package world.phantasmal.web.mobileGame.player

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import world.phantasmal.web.core.rendering.conversion.PSO_FRAME_RATE_DOUBLE
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.externals.three.Vector3

class Enemy(
    val mesh: SkinnedMesh,
    var hp: Int,
    val name: String,
    val animationMixer: AnimationMixer? = null,
    /** Mutable so a boss can shed the generic brain when its own fight controller takes over. */
    var ai: EnemyAI? = null,
    /** Radius of this enemy's cylinder, in world units -- see psoUnit. */
    val hitboxRadius: Double = 0.0,
    /** This species' defence power, subtracted from the player's attack power on every hit. */
    val dfp: Int = 0,
    /** This species' attack power. */
    val atp: Int = 0,
    /** This species' luck, which sets its critical rate at half its value as a percentage. */
    val lck: Int = 0,
    /** Evasion, which the player's accuracy is checked against. */
    val evp: Int = 0,
    val maxHp: Int = hp,
    /** Elemental/special resistances, checked by weapon special attacks. */
    val resistances: world.phantasmal.web.mobileGame.world.Resistances =
        world.phantasmal.web.mobileGame.world.Resistances(),
    /** The stat-table slug this enemy spawned from, for death-drop lookups. */
    val slug: String = "",
    /**
     * The spawn table's room this enemy belongs to. PSO wakes a room's monsters when the
     * player walks in, so the AI needs to know which room that is; -1 means unplaced.
     */
    val section: Int = -1,
) {
    /**
     * Jellen and Zalure: multipliers on this enemy's attack and defense while the debuff runs.
     * 1.0 is unaffected; the countdowns live in GameRenderer's enemy pass.
     */
    var jellenFactor: Double = 1.0
    var jellenRemaining: Double = 0.0
    var zalureFactor: Double = 1.0
    var zalureRemaining: Double = 0.0

    /** Defense as the fight sees it -- Zalure's sap applied. */
    val effectiveDfp: Int get() = (dfp * zalureFactor).toInt()

    /** Attack as the fight sees it -- Jellen's sap applied. */
    val effectiveAtp: Int get() = (atp * jellenFactor).toInt()

    /**
     * Seconds left of this enemy's death animation. Set when hp reaches zero and counted down by
     * GameRenderer, which removes the body once it hits zero -- enemies used to vanish the instant
     * they died, giving their death clips nowhere to play. Stays at 0 for enemies with no death
     * clip catalogued (see ENEMY_ANIMATIONS), which are removed immediately as before.
     */
    var dyingRemaining: Double = 0.0

    /** True once hp has run out, whether or not a death animation is still playing. */
    val isDead: Boolean get() = hp <= 0

    /**
     * True while this enemy can't be aimed at or hurt -- the Dragon burrowing under the arena
     * floor. Target selection skips it; it comes back when the enemy resurfaces.
     */
    var untargetable: Boolean = false

    /**
     * The Dubwitch loop: while positive, this corpse is a downed Dubchic waiting to get back
     * up. Counted down by the death pass; at zero it revives if its room's Dubwitch still
     * stands, and dies for real if not. The getting-up clip rides along from the clip set.
     */
    var reviveRemaining: Double = 0.0
    var reviveMotion: world.phantasmal.psolib.fileFormats.ninja.NjMotion? = null

    /**
     * The body's visible bounding radius in world units, model scale included -- what the
     * lock-on reticle squeezes down onto. Set at spawn; 0 falls back to the hitbox.
     */
    var visualRadius: Double = 0.0

    /**
     * The model's bounding-box top above its own origin, world units, scale included -- where
     * "head level" is. The bounding *sphere* radius overstates it badly on wide models (a
     * Hildebear's arm span floated the reticle into the canopy); the box top is the head.
     */
    var visualTop: Double = 0.0
}

/**
 * Handles attack timing (locks out repeated attacks until the current swing finishes) and simple
 * distance + facing-based hit detection against enemies in front of the player.
 */
class CombatController(
    private val playerPosition: Vector3,
    private val bSphereRadius: Double,
) {
    /** World units per PSO unit -- every range below is stated in PSO's own numbers. */
    private val unit = psoUnit(bSphereRadius)

    /**
     * The wielder's own cylinder, added to every weapon's reach.
     *
     * A weapon's published distance is how far the swing carries past the body, not from the
     * body's centre -- without this a Saber's 1.4 left only 0.4 units of air between the two
     * hitboxes, so a swing that visibly passed through a Booma registered nothing unless the
     * player was standing inside it. Enemies already measure their own reach this way (see
     * EnemyAI.attackRange), so this also makes the exchange symmetric.
     */
    private val PLAYER_BODY_UNITS = 1.0
    /**
     * True while input is locked out. This ends at the swing's *combo* length -- the point the
     * frame data calls the earliest cancel -- so the next attack can be thrown, or the character
     * can move, before the animation has finished.
     */
    var isAttacking: Boolean = false
        private set

    /**
     * True while the swing's animation should still be on screen, which runs to the *full*
     * length. It outlives [isAttacking]: between the two, the character is free to act but the
     * animation plays on if they don't. Cutting the clip at the combo length instead left the
     * first two swings of every combo visibly unfinished.
     */
    var isSwinging: Boolean = false
        private set

    /**
     * The equipped weapon's strike zone, in PSO units. Set whenever the weapon changes; defaults
     * to the bare-handed reach so combat still works before anything is equipped.
     */
    var reach: Double = WeaponType.FIST.reach
    var maxTargets: Int = WeaponType.FIST.maxTargets

    /**
     * Tangent of the strike cone's half-angle, precomputed from the equipped weapon's
     * [WeaponType.angleDegrees] since the hit test needs it every call.
     */
    var angleTan: Double = tanDegrees(WeaponType.FIST.angleDegrees)
        private set

    /** Sets the strike cone from the equipped weapon's published angle. */
    fun setAngleDegrees(degrees: Double) {
        angleTan = tanDegrees(degrees)
    }

    /**
     * How far into a three-hit combo the next swing is, 0-based. Later steps are far more likely
     * to connect (see COMBO_STEP_ACCURACY), which is what makes finishing a combo worth doing
     * even with heavy or special attacks in it.
     */
    var comboStep: Int = 0
        private set

    /**
     * Counts down the window in which the next tap continues the combo. Letting it lapse drops
     * you back to the first step -- PSO's combo is a *timed* sequence, not a free-running counter.
     *
     * This is the swing's *full* length, while the input lockout above is its shorter *combo*
     * length. Between the two, an attack is over as far as input goes but the animation is still
     * playing out: tap in that gap and the swing is cut into the next one, which is exactly what
     * makes a chained combo faster than the same attacks thrown separately.
     */
    private var comboWindowRemaining = 0.0

    /** How long the swing just thrown will run for, if it turns out to be the last one. */
    var currentAttackDuration: Double = 0.0

    /** What the swing's animation should be paced to -- see tryAttack. */
    var currentSwingOccupancy: Double = 0.0
        private set

    private var attackTimeRemaining = 0.0
    private var swingRemaining = 0.0
    private val toEnemy = Vector3()

    /**
     * The hit-test and damage for the swing in flight, deferred to its contact point -- see
     * [tryAttack]. Null when no swing is waiting to connect.
     */
    private var pendingStrike: (() -> Boolean)? = null

    /** How long the blade stays live after contact -- see tryAttack. */
    private var strikeWindowRemaining = 0.0

    /** Counts down the charge in front of a heavy or special -- see tryAttack. */
    var chargeRemaining: Double = 0.0
        private set

    /** True while the character is winding up and the stroke hasn't started. */
    val isCharging: Boolean get() = chargeRemaining > 0
    private var pendingStrikeRemaining = 0.0

    /**
     * Instant hit-test on swing start; does nothing (and returns false) if already mid-attack, so
     * callers must only advance their own combo/animation state when this returns true --
     * otherwise a spammed attack button advances the combo clip while the *previous* swing's
     * timer is still running, which fights the animator's crossfade every tap and looks like
     * stuttering/frame skips. [duration] is the chosen attack animation's length, since callers
     * cycle through several attack clips (combo) with different lengths.
     */
    fun tryAttack(
        playerYaw: Double,
        enemies: MutableList<Enemy>,
        /** The weapon class being swung, which sets the timing -- see [attackFrames]. */
        weapon: WeaponType,
        /** The player's own attack power, which with the target's defence sets the damage. */
        attackPower: Int,
        /** The player's luck, as a critical-hit rate -- see criticalChance. */
        luck: Int,
        /** The player's total accuracy, checked against each target's evasion. */
        totalAta: Int,
        /** How this swing trades accuracy for damage -- see [AttackType]. */
        type: AttackType = AttackType.NORMAL,
        /**
         * Replaces [AttackType.damageModifier] when set -- a sacrificial special swings at 3.33x
         * where an ordinary special deals 0.56x, and only the caller knows what's equipped.
         */
        damageModifierOverride: Double? = null,
        /** Called when a swing connects hard enough to knock the target off its feet. */
        onKnockdown: (Enemy) -> Unit = {},
        /** Called for each target the swing failed its accuracy roll against. */
        onMiss: (Enemy) -> Unit = {},
        /**
         * Called for each enemy the swing connects with, after its hp has been reduced, with the
         * damage dealt and whether it was a critical.
         */
        onHit: (Enemy, damage: Int, critical: Boolean) -> Unit = { _, _, _ -> },
        /**
         * Called once when the swing's strike resolves, with how many enemies the blade
         * reached (hit or miss). The caller spends the rest of the weapon's target budget on
         * crates and traps -- the fix for a one-target saber smashing a whole row of boxes.
         */
        onStrikeResolved: (reached: Int) -> Unit = {},
        /**
         * Extra strikes this weapon can land on one body past the first: 0 for everything
         * ordinary, and one per additional targetable region the sweep covers on a multi-part
         * boss -- a five-round shot burst peppers the Dragon's head AND both feet instead of
         * collapsing into a single pellet. Each extra strike spends one unit of the weapon's
         * target budget and rolls its own accuracy. Evaluated at the contact frame.
         */
        bonusHitsFor: (Enemy) -> Int = { 0 },
    ): Boolean {
        if (isAttacking) return false

        val timing = attackFrames(weapon, type, comboStep)
        // The charge a heavy or special carries in front of the stroke -- see chargeFramesAgainst.
        val chargeFrames = timing.chargeFramesAgainst(attackFrames(weapon, AttackType.NORMAL, comboStep))
        chargeRemaining = chargeFrames / PSO_FRAME_RATE_DOUBLE

        // Input unlocks after the combo length, so the next tap can cut this swing short. The
        // animation itself is allowed to run the full length if no tap comes.
        isAttacking = true
        isSwinging = true
        attackTimeRemaining = timing.seconds(chaining = true)
        currentAttackDuration = timing.seconds(chaining = false)

        // How long the *stroke* occupies the character, with the charge taken out: the blade
        // then travels at the same speed whatever the attack type, which is what makes a heavy
        // read as "wind up, then swing" rather than "swing slowly".
        //
        // The first two steps of a combo are also cancelled into the next swing well before
        // their clip would end, so pacing their animation to the full length played them in
        // slow motion and then cut them off -- which is why the opening swings felt heavier
        // than the finisher instead of quicker than it.
        currentSwingOccupancy =
            (timing.seconds(chaining = true) - chargeRemaining).coerceAtLeast(MIN_SWING_SECONDS)
        swingRemaining = currentAttackDuration

        // The step this swing was thrown at, before the advance below -- the deferred hit-test
        // needs it for its accuracy roll.
        val step = comboStep

        // The hit itself is deferred to the swing's contact frame rather than resolved on the
        // tap: the weapon connects partway through the animation, so an enemy can close into
        // reach during the windup and be hit, or die to something else first and not be. The
        // frame is the frame data's own "attack comes out on" (see AttackFrames.contact) --
        // notably the finisher of a melee combo connects near the *start* of its long clip, with
        // the rest being follow-through. Facing is locked for the whole swing (see
        // CharacterController), so the yaw captured here is the yaw at contact too.
        // Contact as a *share* of the swing rather than an absolute time.
        //
        // The wiki's frame data and the animation files disagree on how long a swing is: a
        // saber's opening attack is 29 frames in the data but 19 in the clip, and its finisher
        // 31 against 59. Treating the published contact frame as a number of seconds therefore
        // fired the hit test at the wrong point in the animation -- late on the opening swing,
        // early on the finisher -- which is why a blade that was visibly inside an enemy could
        // still register nothing. The share of the swing at which the blow lands is the part
        // that transfers between the two; applying it to however long this swing actually
        // plays for keeps the hit and the picture together.
        // Contact measured within the stroke, after the charge -- so a heavy connects at the
        // same point of its swing that a normal one does, just later in wall-clock time.
        val strokeFrames = (timing.full - chargeFrames).coerceAtLeast(1)
        val contactShare =
            ((timing.contact - chargeFrames).toDouble() / strokeFrames).coerceIn(0.05, 0.95)
        pendingStrikeRemaining = chargeRemaining + currentSwingOccupancy * contactShare

        // The blade stays live for the rest of the swing rather than for a single instant.
        // A real weapon sweeps through an arc over several frames, and PSO is forgiving in the
        // same way: starting a swing just before an enemy closes should still connect when it
        // arrives, instead of testing once on one frame and silently whiffing while the sword
        // passes visibly through them.
        strikeWindowRemaining = currentSwingOccupancy * STRIKE_WINDOW_SHARE
        // The strike retries frames while its window is open (to catch an enemy walking in
        // late), but the caller's crate/trap budget must be spent exactly once -- on contact.
        var strikeResolvedReported = false
        pendingStrike = {
            val forwardX = sin(playerYaw)
            val forwardZ = cos(playerYaw)

            // Nearest first, so a weapon limited to one target hits the thing in front of you
            // rather than whichever enemy happens to sit earliest in the list.
            val inReach = enemies
                .filter { !it.isDead && reaches(it, forwardX, forwardZ) }
                .sortedBy { centreDistance(it) }
                .take(maxTargets)

            // Budget the sweep didn't spend on separate bodies can strike extra regions
            // of one -- what multi-target weapons are for against a boss-sized target.
            var spareBudget = maxTargets - inReach.size
            for (enemy in inReach) {
                val extra = bonusHitsFor(enemy).coerceIn(0, spareBudget)
                spareBudget -= extra

                for (strike in 0..extra) {
                    if (enemy.isDead) break
                    // Accuracy is rolled per strike, from the attacker's ATA against this
                    // enemy's evasion, scaled by the attack type and how far into the combo
                    // this swing is. A miss still costs the wind-up -- that's the trade heavy
                    // and special make.
                    val accuracy = accuracyPercent(totalAta, type, step, enemy.evp)

                    if (Random.nextDouble() * 100.0 >= accuracy) {
                        onMiss(enemy)
                        continue
                    }

                    val base =
                        if (damageModifierOverride != null)
                            physicalDamageWithModifier(attackPower, enemy.effectiveDfp, damageModifierOverride)
                        else physicalDamage(attackPower, enemy.effectiveDfp, type)
                    val critical = Random.nextDouble() < criticalChance(luck)
                    val damage = (if (critical) base * CRITICAL_MULTIPLIER else base.toDouble())
                        .toInt()
                        .coerceAtLeast(1)

                    enemy.hp -= damage
                    onHit(enemy, damage, critical)

                    // A quarter of the target's health in one blow puts it on the floor.
                    if (isKnockdown(damage, enemy.maxHp)) onKnockdown(enemy)
                }
            }

            if (!strikeResolvedReported) {
                strikeResolvedReported = true
                onStrikeResolved(inReach.size)
            }

            // Whether the blade found anything at all this frame -- a miss roll still counts,
            // since the weapon did reach the target.
            inReach.isNotEmpty()
        }

        // The swing advances the combo whether or not anything was hit. The third step always
        // ends the sequence, so it resets rather than opening another window.
        comboStep += 1

        if (comboStep >= COMBO_STEP_ACCURACY.size) {
            comboStep = 0
            comboWindowRemaining = 0.0
        } else {
            comboWindowRemaining = currentAttackDuration
        }

        return true
    }

    /**
     * Whether a swing facing ([forwardX], [forwardZ]) connects with this enemy's cylinder.
     *
     * PSO checks the strike zone against the cylinder's *edge*, not its centre, so a wide enemy is
     * genuinely easier to reach -- a Monest's 3.0-unit body can be hit by a dagger that would fall
     * well short of a Booma standing at the same distance. [WeaponType.angleDegrees] then decides how
     * far off dead-ahead the target can be: a narrow weapon needs it lined up, a partisan sweeps.
     */
    private fun reaches(enemy: Enemy, forwardX: Double, forwardZ: Double): Boolean {
        toEnemy.subVectors(enemy.mesh.position, playerPosition)
        toEnemy.y = 0.0
        val distance = toEnemy.length()
        if (distance <= 0) return true

        if (distance > (reach + PLAYER_BODY_UNITS) * unit + enemy.hitboxRadius) return false

        // The real game's strike area is a cone: within [reach] and within the weapon's
        // horizontal angle of dead-ahead, with the target caught if its cylinder overlaps the
        // cone anywhere. `across <= tan(angle) * along` is that angular test in the swing's own
        // axes, widened by the enemy's radius so a wide body clips the cone's edge the way a
        // Monest should.
        val along = toEnemy.x * forwardX + toEnemy.z * forwardZ
        if (along < 0) return false

        val across = kotlin.math.abs(toEnemy.x * forwardZ - toEnemy.z * forwardX)
        return across <= angleTan * along + enemy.hitboxRadius
    }

    private fun centreDistance(enemy: Enemy): Double {
        toEnemy.subVectors(enemy.mesh.position, playerPosition)
        toEnemy.y = 0.0
        return toEnemy.length()
    }

    /**
     * Read-only counterpart to [tryAttack]'s hit-test: the nearest living enemy within the same
     * range/facing cone, or null if nothing qualifies. Drives [world.phantasmal.web.mobileGame.input.TargetInfoPanel]
     * with a real value every frame rather than a fake/static one.
     */
    fun findNearestTarget(playerYaw: Double, enemies: List<Enemy>): Enemy? {
        val forwardX = sin(playerYaw)
        val forwardZ = cos(playerYaw)

        return enemies
            .filter { !it.isDead && reaches(it, forwardX, forwardZ) }
            .minByOrNull { centreDistance(it) }
    }

    /**
     * Ends the swing animation early, for when the character does something else with those
     * frames -- walking out of it, or being hit. Without this a cancelled swing would keep its
     * clip on screen for the rest of its full length.
     */
    fun cancelSwing() {
        isSwinging = false
        swingRemaining = 0.0
    }



    fun update(deltaTime: Double) {
        if (chargeRemaining > 0) chargeRemaining -= deltaTime
        // The swing in flight connects. Resolved before the input-lockout countdown so the hit
        // can never be skipped by the same frame that unlocks the next attack.
        pendingStrike?.let { strike ->
            pendingStrikeRemaining -= deltaTime

            if (pendingStrikeRemaining <= 0) {
                // Connected, or the arc has finished passing through: either way it's spent.
                if (strike() || strikeWindowRemaining <= 0) {
                    pendingStrike = null
                } else {
                    strikeWindowRemaining -= deltaTime
                }
            }
        }

        if (isAttacking) {
            attackTimeRemaining -= deltaTime

            if (attackTimeRemaining <= 0) {
                isAttacking = false
            }
        }

        if (isSwinging) {
            swingRemaining -= deltaTime

            if (swingRemaining <= 0) {
                isSwinging = false
            }
        }

        // Let the combo lapse if the next swing doesn't come soon enough after the last.
        if (comboWindowRemaining > 0) {
            comboWindowRemaining -= deltaTime
            if (comboWindowRemaining <= 0) comboStep = 0
        }
    }

    companion object {
        /**
         * How much of the swing the blade stays live for, past the contact point. Long enough
         * to cover the visible arc, short enough that a swing can't keep hunting for a target
         * well after the animation has moved on.
         */
        private const val STRIKE_WINDOW_SHARE = 0.5

        /** A stroke always gets some time, even if the charge ate most of the swing. */
        private const val MIN_SWING_SECONDS = 0.12

        private fun tanDegrees(degrees: Double): Double =
            kotlin.math.tan(degrees * kotlin.math.PI / 180.0)
    }

}
