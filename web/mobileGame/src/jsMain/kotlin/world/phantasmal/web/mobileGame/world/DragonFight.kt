package world.phantasmal.web.mobileGame.world

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.web.core.rendering.conversion.PSO_FRAME_RATE_DOUBLE
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AdditiveBlending
import world.phantasmal.web.externals.three.AnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.Color
import world.phantasmal.web.externals.three.CylinderGeometry
import world.phantasmal.web.externals.three.DoubleSide
import world.phantasmal.web.externals.three.LoopOnce
import world.phantasmal.web.externals.three.LoopRepeat
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.MeshBasicMaterial
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.SphereGeometry
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.mobileGame.player.Enemy
import world.phantasmal.webui.obj

/**
 * A targetable region of the boss, anchored to one of its skeleton bones. The bone indices were
 * read off a posed bone scan of the walking Dragon (rig DRAGONSCAN run); radii are stated in
 * PSO units at the scaled body.
 */
class DragonPart(val name: String, val boneIndex: Int, val radiusUnits: Double)

/** The Dragon's full clip roster -- see npcs/Dragon/. Only [walk] and [fire] are essential. */
class DragonClips(
    val stand: NjMotion?,
    val walk: NjMotion,
    val fire: NjMotion,
    val wingsOpen: NjMotion?,
    val fly: NjMotion,
    val flyShot: NjMotion?,
    val land: NjMotion?,
    val burstOut: NjMotion?,
    /** tukomi -- the head-first plunge into the ground that opens the burrow. */
    val plunge: NjMotion?,
    val knockFall: NjMotion?,
    val knockDown: NjMotion?,
    val knockRise: NjMotion?,
    val roar: NjMotion?,
    val death: NjMotion?,
)

/**
 * The Dragon boss fight, transcribed from the wiki's own description of it
 * (wiki.pioneer2.net/w/Dragon):
 *
 * Phase 1 (above 1/3 HP): it walks toward the nearest target -- its feet deal damage on contact
 * -- and periodically stops to breathe fire in a large frontal cone. Damaged enough, it staggers,
 * drops its head to the ground and lies there about five seconds; while it's down its HP can't be
 * beaten below the 1/3 mark (the game constantly resets it), so the second phase can't be skipped
 * by burning it down on the floor. After rising -- and after every fire breath -- it lifts into
 * the air, shoots three volleys of fireballs, then lands and repeats the process.
 *
 * Phase 2 (below 1/3 HP): it roars, lifts off, then dives into the ground, leaving a damaging
 * lava circle where it went under. It tunnels from one side of the arena to the other, homing in
 * on targets near its path, three passes with increasing speed -- then explodes out of the
 * ground (a second lava circle), lands and goes back to stomping. The dive is the *only* time
 * the Dragon is ever below the floor; every grounded state clamps to the arena's flat surface.
 *
 * The generic [world.phantasmal.web.mobileGame.player.EnemyAI] is removed from the boss when
 * this takes over -- its per-frame terrain-following is what had the Dragon popping between the
 * arena's stacked walkable surfaces (the outer shell's dome and the real floor).
 */
class DragonFight(
    val enemy: Enemy,
    private val njObject: NjObject,
    private val mixer: AnimationMixer,
    private val clips: DragonClips,
    private val scene: Object3D,
    /** World units per PSO unit -- every range below is stated in PSO's own numbers. */
    private val unitScale: Double,
    /** The arena floor's height. Probed flat at 0 across the whole field. */
    private val floorY: Double = 0.0,
    /** How far from the arena's centre the fight is allowed to roam, in world units. */
    private val arenaRadius: Double = 150.0,
    /**
     * Lands one enemy blow on the player through the same evasion/defence/i-frame path melee
     * hits use: (atpMultiplier, forceKnockdown). The i-frame window inside caps how often the
     * per-frame contact and breath checks can actually connect.
     */
    private val strikePlayer: (Double, Boolean) -> Unit,
    /**
     * Lands one of the Dragon's *fixed-damage* attacks: (damage, forceKnockdown). The wiki
     * publishes these as flat figures per difficulty -- fire breath 26, the fireball spread 24,
     * the underground charge 36 on Normal -- unmodified by the player's DFP, which is why a
     * fresh character and a tank take the same burn. Rate-capped by the same i-frame window.
     */
    private val strikePlayerFixed: (Int, Boolean) -> Unit,
) {
    private enum class State {
        APPROACH, BREATH,
        TAKEOFF, AIR_VOLLEYS, LANDING,
        KNOCK_FALL, KNOCK_DOWN, KNOCK_RISE,
        ROAR, RISE_HIGH, DIVE, TUNNEL, ERUPT,
        DEAD,
    }

    private var state = State.APPROACH
    private var stateRemaining = 0.0

    private val mesh = enemy.mesh

    /**
     * The boss's focusable regions -- head, feet, wings, tail. The player locks onto one at a
     * time (nearest by default, tap to choose); ranged fire flies at the locked part.
     */
    val parts: List<DragonPart> = DRAGON_PARTS

    /** Writes [parts]`[index]`'s current world position -- the bone rides the animation. */
    fun partPosition(index: Int, out: Vector3) {
        val bone = mesh.skeleton.bones.getOrNull(parts[index].boneIndex)
        if (bone != null) bone.getWorldPosition(out) else out.copy(mesh.position)
    }

    /** The jaw the breath and fireballs come out of. */
    private val mouthBone = mesh.skeleton.bones.getOrNull(MOUTH_BONE_INDEX)

    private fun mouthPosition(out: Vector3) {
        if (mouthBone != null) mouthBone.getWorldPosition(out)
        else out.set(
            mesh.position.x,
            mesh.position.y + FIREBALL_MOUTH_UNITS * unitScale,
            mesh.position.z,
        )
    }

    /** Damage taken since the last stagger; crossing the threshold knocks the Dragon down. */
    private var damageSinceKnockdown = 0
    private var lastSeenHp = enemy.hp

    private val roarThreshold = enemy.maxHp / 3
    private var inPhase2 = false

    /** Counts down between phase-2 dive cycles, so it stomps a while between burrows. */
    private var diveCooldown = 0.0

    private var breathCooldown = FIRST_BREATH_DELAY
    private var volleysFired = 0
    private var volleyTimer = 0.0
    private var tunnelPass = 0
    private var tunnelDirX = 0.0
    private var tunnelDirZ = 0.0
    private var tunnelHitThisPass = false
    private var eruptionApplied = false
    private var breathTickTimer = 0.0

    private class Fireball(val mesh: Mesh, val velocity: Vector3, var remaining: Double)

    private class LavaCircle(val mesh: Mesh, val x: Double, val z: Double, val radius: Double, var remaining: Double)

    private val fireballs = mutableListOf<Fireball>()
    private val lavaCircles = mutableListOf<LavaCircle>()

    /**
     * The breath's visible flame: short-lived glowing motes pouring from the jaw down the cone.
     * Purely visual -- the burn itself is the cone check in [updateBreath]. Without these the
     * fire clip read as the Dragon silently yawning; the player reported never seeing breath
     * at all.
     */
    private val flames = mutableListOf<Fireball>()
    private var flameTimer = 0.0
    private val flameScratch = Vector3()

    /** The underground pass's visible wake: a low dark mound racing along the floor. */
    private val tunnelMound = Mesh(
        SphereGeometry(6.0 * unitScale, 16, 8),
        MeshBasicMaterial(obj { color = Color(0x5a4632) }),
    ).also {
        it.scale.y = 0.35
        it.visible = false
        scene.add(it)
    }

    private val clipCache = mutableMapOf<NjMotion, AnimationClip>()
    private var currentMotion: NjMotion? = null

    /** How long the body is held after death, for the death clip. */
    val deathDuration: Double =
        clips.death?.let { (it.frameCount - 1) / PSO_FRAME_RATE_DOUBLE + DEATH_HOLD } ?: .0

    init {
        playClip(clips.walk)
        mesh.position.y = floorY
    }

    /** While positive, the toppling corpse still deals contact damage -- see [updateDeath]. */
    private var deathContactRemaining = 0.0

    fun onDeath() {
        if (state == State.DEAD) return
        state = State.DEAD
        deathContactRemaining = DEATH_HIT_WINDOW
        enemy.untargetable = false
        // A death below the floor or in the air still ends on the ground, upright.
        mesh.visible = true
        mesh.position.y = floorY
        mesh.rotation.x = 0.0
        tunnelMound.visible = false
        clearHazards()
        clips.death?.let { playClip(it, oneShot = true) }
    }

    /**
     * The wiki's parting gift: "The dragon's death animation can hit players and kill them."
     * While the body is still falling, standing under it is a real mistake.
     */
    private fun updateDeath(deltaTime: Double, playerPosition: Vector3) {
        if (deathContactRemaining <= 0) return
        deathContactRemaining -= deltaTime
        val dx = playerPosition.x - mesh.position.x
        val dz = playerPosition.z - mesh.position.z
        if (dx * dx + dz * dz < square(enemy.hitboxRadius * DEATH_HIT_RADIUS_FACTOR)) {
            strikePlayer(1.0, true)
        }
    }

    fun update(deltaTime: Double, playerPosition: Vector3) {
        if (state == State.DEAD) {
            updateDeath(deltaTime, playerPosition)
            return
        }
        if (enemy.isDead) {
            onDeath()
            return
        }

        // The wiki's HP lock: while knocked down, HP is constantly reset to a minimum of 1/3,
        // so the dive phase always gets its entrance.
        if (state == State.KNOCK_FALL || state == State.KNOCK_DOWN || state == State.KNOCK_RISE) {
            if (enemy.hp < roarThreshold) enemy.hp = roarThreshold
        }

        // Track damage for the stagger. In phase 2 knockdown gets much harder, as the wiki says.
        if (enemy.hp < lastSeenHp) damageSinceKnockdown += lastSeenHp - enemy.hp
        lastSeenHp = enemy.hp

        if (!inPhase2 && enemy.hp < roarThreshold) inPhase2 = true

        updateHazards(deltaTime, playerPosition)

        stateRemaining -= deltaTime
        when (state) {
            State.APPROACH -> updateApproach(deltaTime, playerPosition)
            State.BREATH -> updateBreath(deltaTime, playerPosition)
            State.TAKEOFF -> updateTakeoff(deltaTime, playerPosition)
            State.AIR_VOLLEYS -> updateAirVolleys(deltaTime, playerPosition)
            State.LANDING -> updateLanding(deltaTime)
            State.KNOCK_FALL -> if (stateRemaining <= 0) enterKnockDown()
            State.KNOCK_DOWN -> if (stateRemaining <= 0) enterKnockRise()
            State.KNOCK_RISE -> if (stateRemaining <= 0) enterTakeoff()
            State.ROAR -> if (stateRemaining <= 0) enterRiseHigh()
            State.RISE_HIGH -> updateRiseHigh(deltaTime)
            State.DIVE -> updateDive(deltaTime, playerPosition)
            State.TUNNEL -> updateTunnel(deltaTime, playerPosition)
            State.ERUPT -> updateErupt(deltaTime, playerPosition)
            State.DEAD -> {}
        }
    }

    // --- Phase 1: stomping, breath, knockdown, the air volleys ---

    private fun updateApproach(deltaTime: Double, playerPosition: Vector3) {
        mesh.position.y = floorY
        breathCooldown -= deltaTime
        diveCooldown -= deltaTime

        // The roar only starts from solid ground -- a mid-air phase crossing finishes its
        // volley run first, which is also what gives ranged players the wiki's chance to
        // kill it in the air before a dive ever happens.
        if (inPhase2 && diveCooldown <= 0) {
            enterRoar()
            return
        }

        if (checkKnockdown()) return

        val dx = playerPosition.x - mesh.position.x
        val dz = playerPosition.z - mesh.position.z
        val distance = sqrt(dx * dx + dz * dz)

        faceToward(playerPosition, deltaTime, TURN_RATE)

        // Feet deal damage on contact. The reach is the species' own scaled cylinder -- the
        // whole fight is conducted at the Dragon's ankles.
        val contactRange = enemy.hitboxRadius + PLAYER_BODY_UNITS * unitScale
        if (distance < contactRange) {
            strikePlayer(1.0, false)
        }

        // In reach and facing the player: stop and breathe fire.
        if (breathCooldown <= 0 &&
            distance < BREATH_RANGE_UNITS * unitScale &&
            facingError(playerPosition) < BREATH_START_CONE
        ) {
            enterBreath()
            return
        }

        // Lumber toward the player, clamped inside the arena.
        if (distance > contactRange * 0.8) {
            val step = WALK_UNITS_PER_SECOND * unitScale * deltaTime
            moveClamped(dx / distance * step, dz / distance * step)
        }
    }

    private fun enterBreath() {
        state = State.BREATH
        stateRemaining = clipSeconds(clips.fire)
        breathTickTimer = 0.0
        playClip(clips.fire, oneShot = true)
    }

    private fun updateBreath(deltaTime: Double, playerPosition: Vector3) {
        mesh.position.y = floorY
        if (checkKnockdown()) return

        // The flame pours out over the middle of the clip; the head-rearing windup and the
        // recovery at the ends don't burn.
        val total = clipSeconds(clips.fire)
        val elapsed = total - stateRemaining
        if (elapsed > total * BREATH_START_FRACTION && elapsed < total * BREATH_END_FRACTION) {
            breathTickTimer -= deltaTime
            if (breathTickTimer <= 0 && playerInBreathCone(playerPosition)) {
                strikePlayerFixed(BREATH_DAMAGE, false)
                breathTickTimer = BREATH_TICK_SECONDS
            }

            flameTimer -= deltaTime
            while (flameTimer <= 0) {
                flameTimer += FLAME_INTERVAL
                spawnFlame()
            }
        }

        if (stateRemaining <= 0) {
            // After fire breathing it lifts off for the volleys -- the wiki's own loop.
            breathCooldown = BREATH_COOLDOWN
            enterTakeoff()
        }
    }

    private fun checkKnockdown(): Boolean {
        val threshold =
            (enemy.maxHp * if (inPhase2) KNOCKDOWN_FRACTION_PHASE2 else KNOCKDOWN_FRACTION).toInt()
        if (damageSinceKnockdown < threshold) return false
        damageSinceKnockdown = 0
        state = State.KNOCK_FALL
        stateRemaining = clipSeconds(clips.knockFall, fallback = 1.2)
        playClip(clips.knockFall ?: clips.walk, oneShot = clips.knockFall != null)
        return true
    }

    private fun enterKnockDown() {
        state = State.KNOCK_DOWN
        stateRemaining = KNOCKDOWN_SECONDS
        clips.knockDown?.let { playClip(it) }
    }

    private fun enterKnockRise() {
        state = State.KNOCK_RISE
        stateRemaining = clipSeconds(clips.knockRise, fallback = 1.5)
        playClip(clips.knockRise ?: clips.walk, oneShot = clips.knockRise != null)
    }

    private fun enterTakeoff() {
        state = State.TAKEOFF
        stateRemaining = TAKEOFF_SECONDS
        playClip(clips.wingsOpen ?: clips.fly, oneShot = clips.wingsOpen != null)
    }

    private fun updateTakeoff(deltaTime: Double, playerPosition: Vector3) {
        faceToward(playerPosition, deltaTime, TURN_RATE_AIR)
        val progress = 1.0 - (stateRemaining / TAKEOFF_SECONDS).coerceIn(0.0, 1.0)
        mesh.position.y = floorY + (FLY_HEIGHT_UNITS * unitScale) * progress
        if (progress > 0.4 && currentMotion !== clips.fly) playClip(clips.fly)
        if (stateRemaining <= 0) {
            state = State.AIR_VOLLEYS
            volleysFired = 0
            volleyTimer = FIRST_VOLLEY_DELAY
            clips.flyShot?.let { playClip(it) } ?: playClip(clips.fly)
        }
    }

    private fun updateAirVolleys(deltaTime: Double, playerPosition: Vector3) {
        mesh.position.y = floorY + FLY_HEIGHT_UNITS * unitScale
        faceToward(playerPosition, deltaTime, TURN_RATE_AIR)

        volleyTimer -= deltaTime
        if (volleyTimer <= 0 && volleysFired < VOLLEY_COUNT) {
            fireVolley(playerPosition)
            volleysFired++
            volleyTimer = VOLLEY_INTERVAL
        }

        if (volleysFired >= VOLLEY_COUNT && volleyTimer <= 0) {
            state = State.LANDING
            stateRemaining = LANDING_SECONDS
            playClip(clips.land ?: clips.fly, oneShot = clips.land != null)
        }
    }

    private fun updateLanding(deltaTime: Double) {
        val progress = 1.0 - (stateRemaining / LANDING_SECONDS).coerceIn(0.0, 1.0)
        mesh.position.y = floorY + (FLY_HEIGHT_UNITS * unitScale) * (1.0 - progress)
        if (stateRemaining <= 0) {
            mesh.position.y = floorY
            state = State.APPROACH
            playClip(clips.walk)
        }
    }

    /** One spread of three fireballs, out of the jaw, aimed at where the player stands. */
    private fun fireVolley(playerPosition: Vector3) {
        mouthPosition(flameScratch)
        val sourceX = flameScratch.x
        val sourceY = flameScratch.y
        val sourceZ = flameScratch.z
        val baseAngle = atan2(
            playerPosition.x - sourceX,
            playerPosition.z - sourceZ,
        )
        for (spread in -1..1) {
            val angle = baseAngle + spread * VOLLEY_SPREAD_RADIANS
            val dx = sin(angle)
            val dz = cos(angle)
            // Aimed to arrive at chest height where the player stands.
            val horizontal = FIREBALL_UNITS_PER_SECOND * unitScale
            val targetDx = playerPosition.x - sourceX
            val targetDz = playerPosition.z - sourceZ
            val targetDistance = sqrt(targetDx * targetDx + targetDz * targetDz).coerceAtLeast(1.0)
            val flightTime = targetDistance / horizontal
            val vy = (playerPosition.y + PLAYER_BODY_UNITS * unitScale - sourceY) / flightTime

            val ball = Mesh(
                SphereGeometry(FIREBALL_VISUAL_UNITS * unitScale, 12, 8),
                MeshBasicMaterial(obj {
                    color = Color(FIREBALL_COLOR)
                    blending = AdditiveBlending
                    transparent = true
                }).also { it.depthWrite = false },
            )
            ball.position.set(sourceX + dx * 2.0 * unitScale, sourceY, sourceZ + dz * 2.0 * unitScale)
            scene.add(ball)
            fireballs.add(
                Fireball(ball, Vector3(dx * horizontal, vy, dz * horizontal), FIREBALL_LIFETIME)
            )
        }
    }

    /** One mote of the breath's flame, pouring from the jaw down and out along the facing. */
    private fun spawnFlame() {
        mouthPosition(flameScratch)
        val yaw = mesh.rotation.y + (kotlin.random.Random.nextDouble() - 0.5) * FLAME_SPREAD
        val speed = FLAME_SPEED_UNITS * unitScale * (0.75 + kotlin.random.Random.nextDouble() * 0.5)
        // The mouth is high on the reared head; the flame washes down toward the ground ahead.
        val vy = -flameScratch.y / (BREATH_RANGE_UNITS * unitScale / speed)
        val mote = Mesh(
            SphereGeometry(FLAME_VISUAL_UNITS * unitScale, 8, 6),
            MeshBasicMaterial(obj {
                color = Color(if (kotlin.random.Random.nextDouble() < 0.5) FIREBALL_COLOR else FLAME_HOT_COLOR)
                blending = AdditiveBlending
                transparent = true
            }).also { it.depthWrite = false },
        )
        mote.position.copy(flameScratch)
        scene.add(mote)
        flames.add(
            Fireball(
                mote,
                Vector3(sin(yaw) * speed, vy, cos(yaw) * speed),
                FLAME_LIFETIME * (0.7 + kotlin.random.Random.nextDouble() * 0.6),
            )
        )
    }

    // --- Phase 2: the roar, the dive, the tunnelling, the eruption ---

    private fun enterRoar() {
        state = State.ROAR
        stateRemaining = clipSeconds(clips.roar, fallback = 1.4)
        playClip(clips.roar ?: clips.stand ?: clips.walk, oneShot = clips.roar != null)
    }

    private fun enterRiseHigh() {
        state = State.RISE_HIGH
        stateRemaining = RISE_HIGH_SECONDS
        playClip(clips.fly)
    }

    private fun updateRiseHigh(deltaTime: Double) {
        val progress = 1.0 - (stateRemaining / RISE_HIGH_SECONDS).coerceIn(0.0, 1.0)
        mesh.position.y = floorY + (DIVE_HEIGHT_UNITS * unitScale) * progress
        if (stateRemaining <= 0) {
            state = State.DIVE
            stateRemaining = DIVE_SECONDS
            // The plunge: head over, straight down, fast -- not a hover flapping its way
            // underground.
            playClip(clips.plunge ?: clips.fly, oneShot = clips.plunge != null)
        }
    }

    private fun updateDive(deltaTime: Double, playerPosition: Vector3) {
        val progress = 1.0 - (stateRemaining / DIVE_SECONDS).coerceIn(0.0, 1.0)
        val top = floorY + DIVE_HEIGHT_UNITS * unitScale
        val bottom = floorY - TUNNEL_DEPTH_UNITS * unitScale
        mesh.position.y = top + (bottom - top) * progress
        // Nose-down as it plunges.
        mesh.rotation.x = DIVE_PITCH * progress

        if (stateRemaining <= 0) {
            // Under the floor now -- the one legitimate time. It went in hard enough to leave
            // molten ground behind.
            mesh.rotation.x = 0.0
            spawnLavaCircle(mesh.position.x, mesh.position.z)
            enemy.untargetable = true
            mesh.visible = false
            tunnelMound.visible = true
            tunnelPass = 0
            eruptRun = false
            startTunnelPass(playerPosition)
        }
    }

    /** How far the current pass has run, so a rim launch isn't instantly "out of bounds". */
    private var tunnelTraveled = 0.0

    /** True on the final underground run: it stops at the target and erupts there. */
    private var eruptRun = false
    private var eruptTargetX = 0.0
    private var eruptTargetZ = 0.0

    /**
     * One underground charge: the body snaps to the arena's rim (where it is now, clamped
     * out), aims once at the player's position -- their last known location -- and launches.
     * A projectile, not a chaser: nothing steers after launch.
     */
    private fun startTunnelPass(playerPosition: Vector3) {
        tunnelPass++
        tunnelHitThisPass = false
        tunnelTraveled = 0.0
        state = State.TUNNEL
        stateRemaining = TUNNEL_PASS_TIMEOUT

        // Snap to the rim it launches from.
        val distance = sqrt(square(mesh.position.x) + square(mesh.position.z))
        if (distance > 1e-3) {
            val scale = arenaRadius / distance
            mesh.position.x *= scale
            mesh.position.z *= scale
        } else {
            mesh.position.z = -arenaRadius
        }

        val angle = atan2(
            playerPosition.x - mesh.position.x,
            playerPosition.z - mesh.position.z,
        )
        tunnelDirX = sin(angle)
        tunnelDirZ = cos(angle)
    }

    private fun updateTunnel(deltaTime: Double, playerPosition: Vector3) {
        val speed = (TUNNEL_BASE_SPEED_UNITS + tunnelPass * TUNNEL_SPEED_STEP_UNITS) * unitScale
        val step = speed * deltaTime
        mesh.position.x += tunnelDirX * step
        mesh.position.z += tunnelDirZ * step
        mesh.position.y = floorY - TUNNEL_DEPTH_UNITS * unitScale
        mesh.rotation.y = atan2(tunnelDirX, tunnelDirZ)
        tunnelTraveled += step

        tunnelMound.position.set(mesh.position.x, floorY + MOUND_LIFT_UNITS * unitScale, mesh.position.z)

        // Caught in its path: hit hard and put on the floor.
        if (!tunnelHitThisPass) {
            val dx = playerPosition.x - mesh.position.x
            val dz = playerPosition.z - mesh.position.z
            if (dx * dx + dz * dz < square(TUNNEL_HIT_UNITS * unitScale)) {
                strikePlayerFixed(CHARGE_DAMAGE, true)
                tunnelHitThisPass = true
            }
        }

        if (eruptRun) {
            // The last run stops where the player was and bursts out from under them.
            val dx = eruptTargetX - mesh.position.x
            val dz = eruptTargetZ - mesh.position.z
            if (dx * tunnelDirX + dz * tunnelDirZ <= 0 || stateRemaining <= 0) enterErupt()
            return
        }

        // The pass is over once it has genuinely crossed the field and run out the far side.
        val outOfBounds = tunnelTraveled > arenaRadius * 0.5 &&
            sqrt(square(mesh.position.x) + square(mesh.position.z)) > arenaRadius
        if (outOfBounds || stateRemaining <= 0) {
            if (tunnelPass >= TUNNEL_PASSES) {
                // Finale: line up one more run that ends at the player's last known location.
                eruptRun = true
                eruptTargetX = playerPosition.x
                eruptTargetZ = playerPosition.z
                startTunnelPass(playerPosition)
            } else {
                startTunnelPass(playerPosition)
            }
        }
    }

    private fun enterErupt() {
        // Erupt inside the field, not at the wall it just overran.
        val distance = sqrt(square(mesh.position.x) + square(mesh.position.z))
        if (distance > arenaRadius * 0.85) {
            val scale = arenaRadius * 0.85 / distance
            mesh.position.x *= scale
            mesh.position.z *= scale
        }
        state = State.ERUPT
        stateRemaining = clipSeconds(clips.burstOut, fallback = 1.6)
        eruptionApplied = false
        mesh.visible = true
        mesh.rotation.x = 0.0
        tunnelMound.visible = false
        enemy.untargetable = false
        spawnLavaCircle(mesh.position.x, mesh.position.z)
        playClip(clips.burstOut ?: clips.fly, oneShot = clips.burstOut != null)
    }

    private fun updateErupt(deltaTime: Double, playerPosition: Vector3) {
        val total = clipSeconds(clips.burstOut, fallback = 1.6)
        val progress = (1.0 - stateRemaining / total).coerceIn(0.0, 1.0)
        val bottom = floorY - TUNNEL_DEPTH_UNITS * unitScale
        // Bursts up fast, most of the travel in the first third.
        val rise = if (progress < 0.35) progress / 0.35 else 1.0
        mesh.position.y = bottom + (floorY - bottom) * rise

        if (!eruptionApplied && progress > 0.15) {
            eruptionApplied = true
            val dx = playerPosition.x - mesh.position.x
            val dz = playerPosition.z - mesh.position.z
            if (dx * dx + dz * dz < square(ERUPTION_RADIUS_UNITS * unitScale)) {
                strikePlayerFixed(CHARGE_DAMAGE, true)
            }
        }

        if (stateRemaining <= 0) {
            mesh.position.y = floorY
            diveCooldown = DIVE_COOLDOWN
            state = State.APPROACH
            playClip(clips.walk)
        }
    }

    // --- Hazards: the fireballs in flight and the lava circles left behind ---

    private fun spawnLavaCircle(x: Double, z: Double) {
        val radius = LAVA_RADIUS_UNITS * unitScale
        val circle = Mesh(
            CylinderGeometry(radius, radius, LAVA_THICKNESS, 32),
            MeshBasicMaterial(obj {
                color = Color(LAVA_COLOR)
                blending = AdditiveBlending
                transparent = true
                side = DoubleSide
                opacity = LAVA_OPACITY
            }).also { it.depthWrite = false },
        )
        circle.position.set(x, floorY + LAVA_LIFT, z)
        scene.add(circle)
        lavaCircles.add(LavaCircle(circle, x, z, radius, LAVA_LIFETIME))
    }

    private fun updateHazards(deltaTime: Double, playerPosition: Vector3) {
        val balls = fireballs.iterator()
        while (balls.hasNext()) {
            val ball = balls.next()
            ball.remaining -= deltaTime
            ball.mesh.position.x += ball.velocity.x * deltaTime
            ball.mesh.position.y += ball.velocity.y * deltaTime
            ball.mesh.position.z += ball.velocity.z * deltaTime

            val dx = playerPosition.x - ball.mesh.position.x
            val dy = playerPosition.y + PLAYER_BODY_UNITS * unitScale - ball.mesh.position.y
            val dz = playerPosition.z - ball.mesh.position.z
            val hit = dx * dx + dy * dy + dz * dz < square(FIREBALL_HIT_UNITS * unitScale)
            if (hit) strikePlayerFixed(FIREBALL_DAMAGE, false)

            if (hit || ball.remaining <= 0 || ball.mesh.position.y < floorY) {
                scene.remove(ball.mesh)
                balls.remove()
            }
        }

        val moteIterator = flames.iterator()
        while (moteIterator.hasNext()) {
            val mote = moteIterator.next()
            mote.remaining -= deltaTime
            if (mote.remaining <= 0 || mote.mesh.position.y < floorY) {
                scene.remove(mote.mesh)
                moteIterator.remove()
                continue
            }
            mote.mesh.position.x += mote.velocity.x * deltaTime
            mote.mesh.position.y += mote.velocity.y * deltaTime
            mote.mesh.position.z += mote.velocity.z * deltaTime
            val shrink = 1.0 - 1.8 * deltaTime
            mote.mesh.scale.set(
                mote.mesh.scale.x * shrink,
                mote.mesh.scale.y * shrink,
                mote.mesh.scale.z * shrink,
            )
        }

        val circles = lavaCircles.iterator()
        while (circles.hasNext()) {
            val circle = circles.next()
            circle.remaining -= deltaTime
            if (circle.remaining <= 0) {
                scene.remove(circle.mesh)
                circles.remove()
                continue
            }
            if (circle.remaining < LAVA_FADE) {
                forEachOpacity(circle.mesh, LAVA_OPACITY * circle.remaining / LAVA_FADE)
            }
            val dx = playerPosition.x - circle.x
            val dz = playerPosition.z - circle.z
            if (dx * dx + dz * dz < square(circle.radius)) {
                strikePlayer(LAVA_DAMAGE_FACTOR, false)
            }
        }
    }

    private fun forEachOpacity(mesh: Mesh, value: Double) {
        // Not on the typed externals -- the fade writes through the dynamic side. The receiver
        // is already dynamic, so calling asDynamic() ON it would compile into a real runtime
        // method call that doesn't exist (the "b.material.asDynamic is not a function" toast
        // storm the moment the first lava circle started fading).
        val material: dynamic = mesh.material
        material.opacity = value
    }

    private fun clearHazards() {
        for (ball in fireballs) scene.remove(ball.mesh)
        fireballs.clear()
        for (mote in flames) scene.remove(mote.mesh)
        flames.clear()
        for (circle in lavaCircles) scene.remove(circle.mesh)
        lavaCircles.clear()
        scene.remove(tunnelMound)
    }

    // --- Shared movement and animation helpers ---

    private fun moveClamped(dx: Double, dz: Double) {
        val x = mesh.position.x + dx
        val z = mesh.position.z + dz
        val distance = sqrt(x * x + z * z)
        if (distance <= arenaRadius) {
            mesh.position.x = x
            mesh.position.z = z
        }
    }

    private fun faceToward(target: Vector3, deltaTime: Double, turnRate: Double) {
        val desired = atan2(target.x - mesh.position.x, target.z - mesh.position.z)
        var delta = desired - mesh.rotation.y
        while (delta > PI) delta -= 2 * PI
        while (delta < -PI) delta += 2 * PI
        mesh.rotation.y += delta.coerceIn(-turnRate * deltaTime, turnRate * deltaTime)
    }

    /** Absolute angle between where the Dragon faces and where the player stands. */
    private fun facingError(target: Vector3): Double {
        val desired = atan2(target.x - mesh.position.x, target.z - mesh.position.z)
        var delta = desired - mesh.rotation.y
        while (delta > PI) delta -= 2 * PI
        while (delta < -PI) delta += 2 * PI
        return if (delta < 0) -delta else delta
    }

    private fun playerInBreathCone(playerPosition: Vector3): Boolean {
        val dx = playerPosition.x - mesh.position.x
        val dz = playerPosition.z - mesh.position.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance > BREATH_RANGE_UNITS * unitScale) return false
        return facingError(playerPosition) < BREATH_CONE_HALF_ANGLE
    }

    private fun clipSeconds(motion: NjMotion?, fallback: Double = 1.0): Double =
        motion?.let { (it.frameCount - 1) / PSO_FRAME_RATE_DOUBLE } ?: fallback

    private fun playClip(motion: NjMotion, oneShot: Boolean = false) {
        if (motion.frameCount <= 1) return
        if (motion === currentMotion && !oneShot) return
        val clip = clipCache.getOrPut(motion) { createAnimationClip(njObject, motion) }
        mixer.stopAllAction()
        val action = mixer.clipAction(clip)
        action.reset()
        if (oneShot) {
            action.setLoop(LoopOnce, 1)
            action.clampWhenFinished = true
        } else {
            action.setLoop(LoopRepeat, Int.MAX_VALUE)
        }
        action.play()
        currentMotion = motion
    }

    private fun square(v: Double) = v * v

    companion object {
        // Distances in PSO units (multiplied by unitScale), stated at the Dragon's *scaled*
        // size -- see EnemyStats.modelScale. Contact reach comes from the stat table's own
        // scaled cylinder at the call site.
        private const val PLAYER_BODY_UNITS = 1.0
        private const val BREATH_RANGE_UNITS = 26.0
        private const val FIREBALL_MOUTH_UNITS = 12.0
        private const val FIREBALL_VISUAL_UNITS = 1.8
        private const val FIREBALL_HIT_UNITS = 3.0
        private const val LAVA_RADIUS_UNITS = 9.0
        private const val TUNNEL_HIT_UNITS = 7.5
        private const val ERUPTION_RADIUS_UNITS = 13.0
        private const val FLY_HEIGHT_UNITS = 40.0
        private const val DIVE_HEIGHT_UNITS = 42.0
        private const val TUNNEL_DEPTH_UNITS = 24.0
        private const val MOUND_LIFT_UNITS = 0.4

        // Speeds in PSO units per second. The player runs at 4.2 (0.14/frame at 30fps): the
        // walk is escapable, the tunnel passes are not outrun -- they're sidestepped.
        private const val WALK_UNITS_PER_SECOND = 3.2
        private const val FIREBALL_UNITS_PER_SECOND = 14.0

        // The underground charge SHOOTS across the arena -- each pass crosses the whole field
        // in a few seconds, faster and with a better turn radius each time (the wiki's own
        // words). The old figures had it creeping after the player, which read as a slow
        // underground chase instead of three dodge-or-be-hit charges.
        private const val TUNNEL_BASE_SPEED_UNITS = 32.0
        private const val TUNNEL_SPEED_STEP_UNITS = 9.0

        private const val TURN_RATE = 1.6
        private const val TURN_RATE_AIR = 2.4

        // The wiki's phase script.
        private const val VOLLEY_COUNT = 3
        private const val TUNNEL_PASSES = 3
        private const val KNOCKDOWN_SECONDS = 5.0
        private const val KNOCKDOWN_FRACTION = 0.16
        private const val KNOCKDOWN_FRACTION_PHASE2 = 0.4

        private const val FIRST_BREATH_DELAY = 4.0
        private const val BREATH_COOLDOWN = 6.0
        private const val BREATH_TICK_SECONDS = 0.5
        private const val BREATH_START_FRACTION = 0.25
        private const val BREATH_END_FRACTION = 0.8
        private const val BREATH_CONE_HALF_ANGLE = 0.65
        private const val BREATH_START_CONE = 0.9

        private const val TAKEOFF_SECONDS = 1.8
        private const val LANDING_SECONDS = 1.4
        private const val FIRST_VOLLEY_DELAY = 0.6
        private const val VOLLEY_INTERVAL = 1.3
        private const val VOLLEY_SPREAD_RADIANS = 0.22
        private const val FIREBALL_LIFETIME = 4.0

        private const val RISE_HIGH_SECONDS = 2.0
        private const val DIVE_SECONDS = 0.45
        /** Nose-down pitch at full plunge, radians. */
        private const val DIVE_PITCH = 1.25
        private const val TUNNEL_PASS_TIMEOUT = 7.0
        private const val DIVE_COOLDOWN = 16.0

        private const val LAVA_LIFETIME = 9.0
        private const val LAVA_FADE = 1.5
        private const val LAVA_THICKNESS = 0.25
        private const val LAVA_LIFT = 0.15
        private const val LAVA_OPACITY = 0.55
        private const val LAVA_COLOR = 0xff5a1e
        private const val FIREBALL_COLOR = 0xff8a30

        private const val DEATH_HOLD = 1.5
        private const val DEATH_HIT_WINDOW = 1.6
        private const val DEATH_HIT_RADIUS_FACTOR = 1.15

        // The wiki's fixed damage table, Normal difficulty: these attacks deal flat damage
        // regardless of the target's DFP. (Other difficulties publish their own figures --
        // wire them up when a difficulty setting exists.)
        private const val BREATH_DAMAGE = 26
        private const val FIREBALL_DAMAGE = 24
        private const val CHARGE_DAMAGE = 36

        // The unscripted hits stay on the species' own attack power.
        private const val LAVA_DAMAGE_FACTOR = 0.55

        // The breath's flame visual.
        private const val FLAME_INTERVAL = 0.04
        private const val FLAME_SPREAD = 0.5
        private const val FLAME_SPEED_UNITS = 26.0
        private const val FLAME_VISUAL_UNITS = 1.0
        private const val FLAME_LIFETIME = 0.6
        private const val FLAME_HOT_COLOR = 0xffd050

        /** The jaw bone the breath and volleys emit from -- see the DRAGONSCAN bone survey. */
        private const val MOUTH_BONE_INDEX = 63

        /** The focusable regions: bone anchors from the same survey. */
        private val DRAGON_PARTS = listOf(
            DragonPart("head", 63, 5.0),
            DragonPart("left foot", 39, 3.5),
            DragonPart("right foot", 49, 3.5),
            DragonPart("left wing", 114, 6.0),
            DragonPart("right wing", 22, 6.0),
            DragonPart("tail", 83, 4.5),
        )
    }
}
