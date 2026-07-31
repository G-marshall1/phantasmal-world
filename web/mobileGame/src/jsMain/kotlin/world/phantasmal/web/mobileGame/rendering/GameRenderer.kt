package world.phantasmal.web.mobileGame.rendering

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.web.core.boundingSphere
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.core.rendering.RenderContext
import world.phantasmal.web.core.rendering.Renderer
import world.phantasmal.web.core.rendering.conversion.PSO_FRAME_RATE_DOUBLE
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.Clock
import world.phantasmal.web.externals.three.PerspectiveCamera
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.mobileGame.camera.ThirdPersonCameraController
import world.phantasmal.web.mobileGame.input.AttackButton
import world.phantasmal.web.mobileGame.input.VirtualJoystick
import world.phantasmal.web.mobileGame.input.HealthBar
import world.phantasmal.web.mobileGame.player.CharacterController
import world.phantasmal.web.mobileGame.player.CombatController
import world.phantasmal.web.mobileGame.player.Enemy
import world.phantasmal.web.mobileGame.player.EnemyAI
import world.phantasmal.web.mobileGame.player.Weapon
import world.phantasmal.web.mobileGame.player.PlayerAnimations
import world.phantasmal.web.mobileGame.player.PlayerAnimator
import world.phantasmal.web.mobileGame.player.PlayerAssetLoader
import world.phantasmal.web.mobileGame.world.EnemyAssetLoader
import world.phantasmal.web.mobileGame.world.MapAssetLoader
import world.phantasmal.web.mobileGame.world.ObjectAssetLoader
import world.phantasmal.web.mobileGame.world.WallCollider
import world.phantasmal.web.mobileGame.world.findNearestGroundHeight
import world.phantasmal.web.viewer.loading.AnimationAssetLoader
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader

/**
 * Loads [mapSlug]'s area (see MAP_SPECS in :web:assets-generation's MapSpecs.kt for the full list
 * of 10 slugs), spawns a player character standing on the ground, and drives it with joystick
 * input + wall/ground collision, followed by a third-person camera. Also spawns a roster of
 * enemies, each with a basic chase-and-melee brain (see EnemyAI.kt) that damages the player on
 * contact, and the player's own attack button, which cycles through a 3-hit saber combo.
 */
class GameRenderer(
    assetLoader: AssetLoader,
    createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
    mapSlug: String = "forest01",
) : Renderer() {
    private val clock = Clock()
    private val characterClassAssetLoader = addDisposable(CharacterClassAssetLoader(assetLoader))
    private val animationAssetLoader = addDisposable(AnimationAssetLoader(assetLoader))
    private val joystick = addDisposable(VirtualJoystick(document.body!!))
    private val healthBar = addDisposable(HealthBar(document.body!!))

    private var player: Player? = null
    private val enemies = mutableListOf<Enemy>()

    override val context = addDisposable(
        RenderContext(
            createCanvas(),
            PerspectiveCamera(fov = 60.0, aspect = 1.0, near = 0.1, far = 3_000.0),
        )
    )

    override val threeRenderer = addDisposable(createThreeRenderer(context.canvas)).renderer

    override val inputManager = addDisposable(
        ThirdPersonCameraController(context.canvas, context.camera as PerspectiveCamera)
    )

    init {
        MainScope().launch {
            // Static hub stages (currently just Pioneer 2 -- see STAGE_SPECS in
            // :web:assets-generation's StageSpecs.kt) use a structurally different section format
            // than field areas, needing MapAssetLoader's separate loadStage entry point; see
            // Psov2StageGeometry.kt for why.
            val mapAssetLoader = MapAssetLoader(assetLoader)
            val map = if (mapSlug in STAGE_SLUGS) {
                mapAssetLoader.loadStage(mapSlug)
            } else {
                mapAssetLoader.loadArea(mapSlug)
            }
            context.scene.add(map.renderObject)

            val playerMeshData = PlayerAssetLoader(characterClassAssetLoader).loadPlayerMesh()
            val mesh = playerMeshData.mesh
            val bSphereRadius = boundingSphere(mesh).radius

            // Real terrain's walkable (near-flat) triangles are a sparse, scattered subset of the
            // whole mesh, so (0,0) itself has no guarantee of landing on one -- search nearby
            // rather than silently defaulting to y=0, see findNearestGroundHeight's doc comment.
            val (spawnX, groundY, spawnZ) =
                findNearestGroundHeight(map.walkableCollisionObject, .0, .0) ?: Triple(.0, .0, .0)
            mesh.position.set(spawnX, groundY, spawnZ)
            context.scene.add(mesh)

            inputManager.setScale(bSphereRadius)

            val animator = addDisposable(PlayerAnimator(playerMeshData.njObject, mesh))
            val idleMotion = animationAssetLoader.loadAnimation(animationPath(PlayerAnimations.IDLE))
            val walkMotion = animationAssetLoader.loadAnimation(animationPath(PlayerAnimations.WALK))
            val deadMotion = animationAssetLoader.loadAnimation(animationPath(PlayerAnimations.DEAD))
            val attackMotions = PlayerAnimations.ATTACKS.map { animationAssetLoader.loadAnimation(animationPath(it)) }
            animator.playClip(idleMotion)

            Weapon.attach(assetLoader, mesh)

            // A spread of enemies across every biome's species roster (all 69 converted enemies
            // were swept for load errors; this is a broad sample, not the full set), arranged in a
            // wide arc around the player's starting position. Each one's own walk/attack clip
            // pair, picked from its own animationNames (see EnemySpecs.kt; naming varies per enemy
            // family, there's no universal clip name). A few stationary species (plants, turrets,
            // waiting ninjas) don't have a real walk clip at all, so their idle/wait clip is
            // reused as "walk" -- same as EnemyAI's own no-aggro idle loop already does with it,
            // just visibly sliding instead of stepping while closing distance.
            val enemyLoader = EnemyAssetLoader(assetLoader)
            val enemyKinds = listOf(
                Triple("Booma", "walk_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm"),
                Triple("GoBooma", "walk_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm"),
                Triple("GigaBooma", "walk_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm"),
                Triple("Rappy", "walk_re3_b_base.njm", "attack_re3_b_base.njm"),
                Triple("Hildebear", "walk_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm"),
                Triple("Hildebaby", "walk_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm"),
                Triple("Mothmant", "fly_bm3_fly_body.njm", "atack_bm3_fly_body.njm"),
                Triple("SavageWolf", "walk_bm5_s_kem_body.njm", "okil_bm5_s_kem_body.njm"),
                Triple("BarbarousWolf", "walk_bm5_s_kem_body.njm", "okil_bm5_s_kem_body.njm"),
                Triple("PanArms", "walk_bm7_s_paa_body.njm", "beamdwn_bm7_s_paa_body.njm"),
                Triple("Hidoom", "walk_bm7_s_pal_body.njm", "atack_bm7_s_pal_body.njm"),
                Triple("Migium", "walk_bm7_s_par_body.njm", "atack_bm7_s_par_body.njm"),
                Triple("GrassAssasin", "walk_re1_b_base.njm", "lattack_re1_b_base.njm"),
                Triple("EvilShark", "walk_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm"),
                Triple("NanoDragoon", "walk_bm6_s_drc_body.njm", "beam_bm6_s_drc_body.njm"),
                Triple("PoisonLily", "waito_re2_b_root.njm", "attack_re2_b_root.njm"),
                Triple("Canadine", "wait01_me1_y_mb.njm", "change01_me1_y_mb.njm"),
                Triple("Gilchic", "walk01_me2_y_me2.njm", "scratch01_me2_me2.njm"),
                Triple("SinowBeat", "wait_me3_y_me3.njm", "sword_me3_y_me3.njm"),
                Triple("Delsaber", "walk_df1_s_kil_body.njm", "atack_df1_s_kil_body.njm"),
                Triple("Dimenian", "walk_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm"),
                Triple("DarkBelra", "walk_re7_b_body.njm", "attack_re7_b_body.njm"),
                Triple("ChaosBringer", "walk_bm8_s_kb_body.njm", "beam_bm8_s_kb_body.njm"),
                Triple("DarkGunner", "move_re5_b_body.njm", "attack_re5_b_body.njm"),
            )

            val arcRadius = bSphereRadius * 6.0

            // Shared across every enemy's AI so the per-map wall-triangle list (see WallCollider's
            // own doc comment on why it's a brute-force scan) is only built once, not once per
            // enemy.
            val enemyWallCollider = WallCollider(map.collisionGeometry)

            for ((i, kind) in enemyKinds.withIndex()) {
                val (slug, walkClipName, attackClipName) = kind
                // A 300-degree arc centered on the player's forward (+z) direction, leaving a
                // 60-degree gap directly behind spawn -- wide enough that this many enemies (more
                // than double the original 11-enemy roster) don't overlap each other.
                val angle = (i.toDouble() / (enemyKinds.size - 1) - 0.5) * (5.0 * PI / 3.0)
                val x = spawnX + arcRadius * sin(angle)
                val z = spawnZ + arcRadius * cos(angle)
                val (enemyX, enemyGroundY, enemyZ) =
                    findNearestGroundHeight(map.walkableCollisionObject, x, z) ?: Triple(x, .0, z)
                val enemyMeshData = enemyLoader.loadEnemy(slug)
                val enemyMesh = enemyMeshData.mesh
                enemyMesh.position.set(enemyX, enemyGroundY, enemyZ)
                context.scene.add(enemyMesh)

                val walkMotion = enemyLoader.loadAnimation(slug, walkClipName)
                val attackMotion = enemyLoader.loadAnimation(slug, attackClipName)
                val enemyMixer = AnimationMixer(enemyMesh)

                // EnemyAI registers the initial walk clip itself (see its init block), so the
                // mixer starts idle here -- no separate up-front clipAction/.play() call needed.
                val ai = EnemyAI(
                    enemyMesh,
                    enemyMeshData.njObject,
                    enemyMixer,
                    walkMotion,
                    attackMotion,
                    enemyWallCollider,
                    map.walkableCollisionObject,
                    boundingSphere(enemyMesh).radius,
                )

                enemies.add(Enemy(enemyMesh, hp = 3, animationMixer = enemyMixer, ai = ai))
            }

            // A few decorative loot props scattered behind the player (opposite the enemy arc),
            // exercising the object-prop conversion pipeline (see ObjectSpecs.kt) the same way the
            // enemy roster above exercises EnemySpecs.kt. Purely decorative for now -- no pickup
            // interaction yet.
            val objectLoader = ObjectAssetLoader(assetLoader)
            val propSlugs = listOf("ItemBox", "WeaponBox", "ArmorBox", "Meseta")

            for ((i, slug) in propSlugs.withIndex()) {
                val x = spawnX + (i - (propSlugs.size - 1) / 2.0) * bSphereRadius * 1.5
                val z = spawnZ - bSphereRadius * 2.5
                val (propX, propGroundY, propZ) =
                    findNearestGroundHeight(map.walkableCollisionObject, x, z) ?: Triple(x, .0, z)
                val propMesh = objectLoader.loadObject(slug)
                propMesh.position.set(propX, propGroundY, propZ)
                context.scene.add(propMesh)
            }

            val combat = CombatController(mesh.position, bSphereRadius)

            player = Player(
                mesh = mesh,
                controller = CharacterController(map, mesh.position, bSphereRadius),
                animator = animator,
                combat = combat,
                idleMotion = idleMotion,
                walkMotion = walkMotion,
                deadMotion = deadMotion,
                attackMotions = attackMotions,
                spawnX = spawnX,
                spawnY = groundY,
                spawnZ = spawnZ,
            )
            healthBar.setHealth(player!!.hp, player!!.maxHp)

            addDisposable(
                AttackButton(document.body!!) {
                    player?.let { p ->
                        if (p.hp <= 0) return@let

                        val comboMotion = p.attackMotions[p.comboIndex]
                        val duration = (comboMotion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
                        val started = combat.tryAttack(p.mesh.rotation.y, enemies, duration)

                        // Only advance the combo/animation if a swing actually started -- a
                        // spammed tap while already mid-swing must be dropped entirely, not
                        // queued, or it fights the current swing's animation and timer.
                        if (started) {
                            p.currentAttackMotion = comboMotion
                            p.comboIndex = (p.comboIndex + 1) % p.attackMotions.size
                        }
                    }
                }
            )
        }
    }

    override fun render() {
        val deltaTime = clock.getDelta()
        val p = player

        if (p != null) {
            p.combat.update(deltaTime)

            val alive = p.hp > 0

            if (alive) {
                // Movement direction is computed relative to the camera's *current* facing, so the
                // camera's yaw must not be re-derived from the character's facing in this same
                // pass -- doing so previously created a feedback loop (character yaw -> camera
                // yaw -> next frame's movement basis -> character yaw...) that spiralled into
                // circles for any input with a sideways component. The camera's facing is driven
                // purely by the user's manual drag now; the character's facing is a one-way
                // visual output.
                p.controller.update(
                    deltaTime,
                    joystick.x,
                    joystick.y,
                    inputManager.effectiveYaw,
                    p.combat.isAttacking,
                )
                p.mesh.rotation.y = p.controller.yaw
            }

            p.animator.playClip(
                when {
                    !alive -> p.deadMotion
                    p.combat.isAttacking -> p.currentAttackMotion ?: p.idleMotion
                    p.controller.isMoving -> p.walkMotion
                    else -> p.idleMotion
                }
            )
            p.animator.update(deltaTime)

            inputManager.targetPosition.copy(p.controller.position)
        }

        if (p != null) {
            p.invulnerableRemaining -= deltaTime

            if (p.hp <= 0) {
                // Started the moment hp first hit zero (see below); once it counts down, drop the
                // player back at their original spawn point rather than leaving the game in a dead
                // end that only a page reload can get out of.
                p.respawnRemaining -= deltaTime

                if (p.respawnRemaining <= 0) {
                    p.mesh.position.set(p.spawnX, p.spawnY, p.spawnZ)
                    p.hp = p.maxHp
                    // A moment of post-respawn grace so materializing back in doesn't just walk
                    // straight into another hit from whatever was already standing on the spot.
                    p.invulnerableRemaining = INVULNERABILITY_DURATION
                    healthBar.setHealth(p.hp, p.maxHp)
                }
            }
        }

        for (enemy in enemies) {
            if (p != null && p.hp > 0) {
                val landed = enemy.ai?.update(deltaTime, p.mesh.position) ?: false

                // Each attacking enemy runs its own independent cooldown, so with several enemies
                // surrounding the player their landed hits land on different frames rather than
                // neatly taking turns -- without a shared cooldown on the *player's* side, several
                // of those hits can stack within the same second or two and chain straight through
                // the whole health bar. A brief invulnerability window after any hit (standard
                // action-game i-frames) caps the effective damage rate regardless of how many
                // enemies are attacking at once.
                if (landed && p.invulnerableRemaining <= 0) {
                    p.hp = (p.hp - ENEMY_DAMAGE).coerceAtLeast(0)
                    p.invulnerableRemaining = INVULNERABILITY_DURATION
                    healthBar.setHealth(p.hp, p.maxHp)

                    if (p.hp <= 0) {
                        p.respawnRemaining = RESPAWN_DELAY
                    }
                }
            }

            enemy.animationMixer?.update(deltaTime)
        }

        inputManager.update(deltaTime)

        super.render()
    }

    private fun animationPath(index: Int): String =
        "/player/animation/animation_${index.toString().padStart(3, '0')}.njm"

    private class Player(
        val mesh: SkinnedMesh,
        val controller: CharacterController,
        val animator: PlayerAnimator,
        val combat: CombatController,
        val idleMotion: NjMotion,
        val walkMotion: NjMotion,
        val deadMotion: NjMotion,
        val attackMotions: List<NjMotion>,
        val spawnX: Double,
        val spawnY: Double,
        val spawnZ: Double,
    ) {
        var comboIndex: Int = 0
        var currentAttackMotion: NjMotion? = null
        var hp: Int = MAX_HP
        val maxHp: Int = MAX_HP
        var invulnerableRemaining: Double = 0.0
        var respawnRemaining: Double = 0.0

        companion object {
            private const val MAX_HP = 100
        }
    }

    companion object {
        private const val ENEMY_DAMAGE = 10
        private const val INVULNERABILITY_DURATION = 1.0
        private const val RESPAWN_DELAY = 3.0
        private val STAGE_SLUGS = setOf(
            "pioneer2",
            "lobbyBlack", "lobbyBlue", "lobbyBluegreen", "lobbyGreen", "lobbyOrange",
            "lobbyPurple", "lobbyRed", "lobbyWhite", "lobbyYellow", "lobbyYellowGreen",
            "bossArea1", "bossArea2", "bossArea3", "bossArea4",
            "ultimateBossArea1", "ultimateBossArea2", "ultimateBossArea3",
            "spaceship00", "spaceship01", "spaceship02",
            "temple00", "temple01", "temple02",
        )
    }
}
