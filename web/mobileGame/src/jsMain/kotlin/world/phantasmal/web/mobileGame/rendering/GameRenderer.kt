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
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.Clock
import world.phantasmal.web.externals.three.PerspectiveCamera
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.mobileGame.camera.ThirdPersonCameraController
import world.phantasmal.web.mobileGame.input.AttackButton
import world.phantasmal.web.mobileGame.input.VirtualJoystick
import world.phantasmal.web.mobileGame.player.CharacterController
import world.phantasmal.web.mobileGame.player.CombatController
import world.phantasmal.web.mobileGame.player.Enemy
import world.phantasmal.web.mobileGame.player.Weapon
import world.phantasmal.web.mobileGame.player.PlayerAnimations
import world.phantasmal.web.mobileGame.player.PlayerAnimator
import world.phantasmal.web.mobileGame.player.PlayerAssetLoader
import world.phantasmal.web.mobileGame.world.EnemyAssetLoader
import world.phantasmal.web.mobileGame.world.MapAssetLoader
import world.phantasmal.web.mobileGame.world.ObjectAssetLoader
import world.phantasmal.web.mobileGame.world.findNearestGroundHeight
import world.phantasmal.web.viewer.loading.AnimationAssetLoader
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader

/**
 * Loads [mapSlug]'s area (see MAP_SPECS in :web:assets-generation's MapSpecs.kt for the full list
 * of 10 slugs), spawns a player character standing on the ground, and drives it with joystick
 * input + wall/ground collision, followed by a third-person camera. Also spawns a few enemies as
 * combat test dummies for the attack button, which cycles through a 3-hit saber combo.
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
            val map = MapAssetLoader(assetLoader).loadArea(mapSlug)
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
            val attackMotions = PlayerAnimations.ATTACKS.map { animationAssetLoader.loadAnimation(animationPath(it)) }
            animator.playClip(idleMotion)

            Weapon.attach(assetLoader, mesh)

            // A spread of enemy test dummies, one from each biome's species roster (all 69
            // converted enemies were swept for load errors; this is a representative sample, not
            // the full set), arranged in an arc ahead of the player's starting facing direction so
            // there's always another one to test attacks against. Each one's own "movement"-ish
            // clip, picked from its own animationNames (see EnemySpecs.kt; naming varies per
            // enemy family, there's no universal clip name, and a few stationary species -- e.g.
            // Canadine, a turret -- don't have one at all, so their idle "wait" clip is used
            // instead).
            val enemyLoader = EnemyAssetLoader(assetLoader)
            val enemyKinds = listOf(
                "Booma" to "walk_bm1_s_wala_body.njm",
                "Rappy" to "walk_re3_b_base.njm",
                "Hildebear" to "walk_bm2f_s_moj_body.njm",
                "Mothmant" to "fly_bm3_fly_body.njm",
                "SavageWolf" to "walk_bm5_s_kem_body.njm",
                "PanArms" to "walk_bm7_s_paa_body.njm",
                "GrassAssasin" to "walk_re1_b_base.njm",
                "Canadine" to "wait01_me1_y_mb.njm",
                "Gilchic" to "walk01_me2_y_me2.njm",
                "Delsaber" to "walk_df1_s_kil_body.njm",
                "DarkGunner" to "move_re5_b_body.njm",
            )

            val arcRadius = bSphereRadius * 5.0

            for ((i, kind) in enemyKinds.withIndex()) {
                val (slug, walkClipName) = kind
                // A 120-degree arc centered on the player's forward (+z) direction.
                val angle = (i.toDouble() / (enemyKinds.size - 1) - 0.5) * (2.0 * PI / 3.0)
                val x = spawnX + arcRadius * sin(angle)
                val z = spawnZ + arcRadius * cos(angle)
                val (enemyX, enemyGroundY, enemyZ) =
                    findNearestGroundHeight(map.walkableCollisionObject, x, z) ?: Triple(x, .0, z)
                val enemyMeshData = enemyLoader.loadEnemy(slug)
                val enemyMesh = enemyMeshData.mesh
                enemyMesh.position.set(enemyX, enemyGroundY, enemyZ)
                context.scene.add(enemyMesh)

                val walkMotion = enemyLoader.loadAnimation(slug, walkClipName)
                val enemyMixer = AnimationMixer(enemyMesh)
                enemyMixer.clipAction(createAnimationClip(enemyMeshData.njObject, walkMotion)).play()

                enemies.add(Enemy(enemyMesh, hp = 1, animationMixer = enemyMixer))
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
                attackMotions = attackMotions,
            )

            addDisposable(
                AttackButton(document.body!!) {
                    player?.let { p ->
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

        player?.let { p ->
            p.combat.update(deltaTime)

            // Movement direction is computed relative to the camera's *current* facing, so the
            // camera's yaw must not be re-derived from the character's facing in this same pass
            // -- doing so previously created a feedback loop (character yaw -> camera yaw ->
            // next frame's movement basis -> character yaw...) that spiralled into circles for
            // any input with a sideways component. The camera's facing is driven purely by the
            // user's manual drag now; the character's facing is a one-way visual output.
            p.controller.update(
                deltaTime,
                joystick.x,
                joystick.y,
                inputManager.effectiveYaw,
                p.combat.isAttacking,
            )
            p.mesh.rotation.y = p.controller.yaw

            p.animator.playClip(
                when {
                    p.combat.isAttacking -> p.currentAttackMotion ?: p.idleMotion
                    p.controller.isMoving -> p.walkMotion
                    else -> p.idleMotion
                }
            )
            p.animator.update(deltaTime)

            inputManager.targetPosition.copy(p.controller.position)
        }

        for (enemy in enemies) {
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
        val attackMotions: List<NjMotion>,
    ) {
        var comboIndex: Int = 0
        var currentAttackMotion: NjMotion? = null
    }
}
