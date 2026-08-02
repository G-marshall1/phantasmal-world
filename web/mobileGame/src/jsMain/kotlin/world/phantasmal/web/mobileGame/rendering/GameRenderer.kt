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
import world.phantasmal.web.mobileGame.input.CoordinateDisplay
import world.phantasmal.web.mobileGame.input.FlightVerticalControls
import world.phantasmal.web.mobileGame.input.FlyToggleButton
import world.phantasmal.web.mobileGame.input.VirtualJoystick
import world.phantasmal.web.mobileGame.input.HealthBar
import world.phantasmal.web.mobileGame.player.CharacterController
import world.phantasmal.web.mobileGame.player.CombatController
import world.phantasmal.web.mobileGame.player.Enemy
import world.phantasmal.web.mobileGame.player.EnemyAI
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.mobileGame.player.Weapon
import world.phantasmal.web.mobileGame.player.PlayerAnimations
import world.phantasmal.web.mobileGame.player.PlayerAnimator
import world.phantasmal.web.mobileGame.player.PlayerAssetLoader
import world.phantasmal.web.mobileGame.world.EnemyAssetLoader
import world.phantasmal.web.mobileGame.world.EnemyFragmentRef
import world.phantasmal.web.mobileGame.world.MapAssetLoader
import world.phantasmal.web.mobileGame.world.NpcAssetLoader
import world.phantasmal.web.mobileGame.world.NpcMeshData
import world.phantasmal.web.mobileGame.world.ObjectAssetLoader
import world.phantasmal.web.mobileGame.world.WallCollider
import world.phantasmal.web.mobileGame.world.randomAreaLayoutSlug
import world.phantasmal.web.mobileGame.world.findNearestGroundHeight
import world.phantasmal.web.mobileGame.world.findNearestStableGroundHeight
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
    appearance: PlayerAppearance = PlayerAppearance.DEFAULT,
) : Renderer() {
    private val clock = Clock()
    private val characterClassAssetLoader = addDisposable(CharacterClassAssetLoader(assetLoader))
    private val animationAssetLoader = addDisposable(AnimationAssetLoader(assetLoader))
    private val joystick = addDisposable(VirtualJoystick(document.body!!))
    private val healthBar = addDisposable(HealthBar(document.body!!))
    private val flyToggleButton = addDisposable(FlyToggleButton(document.body!!) { toggleFlying() })
    private val flightVerticalControls = addDisposable(FlightVerticalControls(document.body!!))
    private val coordinateDisplay = addDisposable(CoordinateDisplay(document.body!!))

    private var player: Player? = null
    private val enemies = mutableListOf<Enemy>()
    private val npcMixers = mutableListOf<AnimationMixer>()

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
                // Cave/Mine/Ruins pick a random one of their several layout variants each time,
                // same as the real game -- see randomAreaLayoutSlug's doc comment.
                mapAssetLoader.loadArea(randomAreaLayoutSlug(mapSlug))
            }
            context.scene.add(map.renderObject)

            val playerMeshData = PlayerAssetLoader(characterClassAssetLoader).loadPlayerMesh(appearance)
            val mesh = playerMeshData.mesh
            val bSphereRadius = boundingSphere(mesh).radius

            // Real terrain's walkable (near-flat) triangles are a sparse, scattered subset of the
            // whole mesh, so (0,0) itself has no guarantee of landing on one -- search nearby
            // rather than silently defaulting to y=0, see findNearestGroundHeight's doc comment.
            // Stage-format hubs need their own known-good origin (see STAGE_SPAWN_ORIGINS) and the
            // "stable" ground search: Pioneer 2 in particular has several stacked walkable surfaces
            // (an elevated walkway deck, a teleporter dais top, and only then the real street) plus
            // thin decorative details (counter trim, railings) that a single raycast can land on and
            // mistake for solid floor -- see findNearestStableGroundHeight's doc comment. Field/
            // dungeon Room-format maps haven't shown either problem, so they keep the plain search.
            val (originX, originZ) = STAGE_SPAWN_ORIGINS[mapSlug] ?: (.0 to .0)
            val isStage = mapSlug in STAGE_SLUGS
            val (spawnX, groundY, spawnZ) = (
                if (isStage) {
                    findNearestStableGroundHeight(map.walkableCollisionObject, originX, originZ)
                } else {
                    findNearestGroundHeight(map.walkableCollisionObject, originX, originZ)
                }
                ) ?: Triple(originX, .0, originZ)
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

            // Field/dungeon maps only -- hub/lobby/boss-arena/VS-arena stages (STAGE_SLUGS)
            // are meant to be player + friendly-NPC only, matching the real game; monsters
            // belong in their own biome's map, not standing around Pioneer 2.
            if (mapSlug !in STAGE_SLUGS) {
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
                    // "Dubchic"/"Dubchic Damaged" turned out to be single-mesh once actually checked
                    // against AssetEnemies.js, despite originally being grouped in with the genuinely
                    // multi-part bosses -- see EnemySpec.fragments' doc comment.
                    Triple("Dubchic", "walk01_me2_y_me2.njm", "scratch01_me2_me2.njm"),
                    Triple("DubchicDamaged", "walk01_me2_y_me2.njm", "scratch01_me2_me2.njm"),
                )

                val arcRadius = bSphereRadius * 6.0

                // Shared across every enemy's AI so the per-map wall-triangle list (see WallCollider's
                // own doc comment on why it's a brute-force scan) is only built once, not once per
                // enemy.
                val enemyWallCollider = WallCollider(
                    map.collisionGeometry,
                    minHeight = bSphereRadius * CharacterController.MAX_STEP_HEIGHT_FACTOR,
                )

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

                // Multi-part enemies -- exercises both fragment-sourcing patterns EnemyFragment
                // supports (see its doc comment in :web:assets-generation's EnemySpecs.kt): De Rol Le
                // and its Ultimate reskin Dal Ral Lie share their fragments' texture with the main
                // body (pvmName left null); Garanz's fragments each carry their own separate texture.
                // Spawned dead ahead of the player at a few different angles, well beyond the regular
                // arc, both so they read as distinct "boss-tier" encounters and so their much larger
                // bounding spheres don't overlap the rest of the roster. None of these fragments are
                // bone-attached to their owner's skeleton (psov2 doesn't attach them either -- see
                // AssetEnemies.js's own loaders), so they're added as plain children of the main
                // mesh: they track its root position/rotation as EnemyAI moves it, just not the
                // skeleton's own animation.
                val multiPartKinds = listOf(
                    Triple("DeRolLe", "forward_boss2_b_body.njm", "l_bite_boss2_b_body.njm"),
                    Triple("DalRalLie", "forward_boss2_b_body.njm", "l_bite_boss2_b_body.njm"),
                    Triple("Garanz", "walk01_me4_y_me4.njm", "attack_me4_y_me4.njm"),
                )
                val derolleFragments = listOf(
                    EnemyFragmentRef("boss2_b_derorure_fin_b.nj"), EnemyFragmentRef("boss2_b_derorure_fin_a.nj"),
                    EnemyFragmentRef("boss2_b_derorure_sting.nj"), EnemyFragmentRef("boss2_b_derorure_tentacle.nj"),
                    EnemyFragmentRef("boss2_b_helm_break.nj"), EnemyFragmentRef("boss2_b_shell_break.nj"),
                )
                val garanzFragments = listOf(
                    EnemyFragmentRef("me4_y_hahen01.nj", "me4_y_hahen01.pvm"),
                    EnemyFragmentRef("me4_y_hahen02.nj", "me4_y_hahen02.pvm"),
                    EnemyFragmentRef("me4_y_hahen03.nj", "me4_y_hahen03.pvm"),
                    EnemyFragmentRef("me4_y_mine.nj", "me4_y_mine.pvm"),
                    EnemyFragmentRef("me4_y_missile.nj", "me4_y_missile.pvm"),
                )

                for ((i, kind) in multiPartKinds.withIndex()) {
                    val (slug, walkClipName, attackClipName) = kind
                    val angle = (i.toDouble() / multiPartKinds.size - 0.5) * (PI / 2.0)
                    val bossX = spawnX + arcRadius * 1.6 * sin(angle)
                    val bossZ = spawnZ + arcRadius * 1.6 * cos(angle)
                    val (groundX, bossGroundY, groundZ) =
                        findNearestGroundHeight(map.walkableCollisionObject, bossX, bossZ)
                            ?: Triple(bossX, .0, bossZ)

                    val bossMeshData = enemyLoader.loadEnemy(
                        slug,
                        fragments = if (slug == "Garanz") garanzFragments else derolleFragments,
                    )
                    val bossMesh = bossMeshData.mesh
                    bossMesh.position.set(groundX, bossGroundY, groundZ)
                    for (fragment in bossMeshData.fragments) {
                        bossMesh.add(fragment)
                    }
                    context.scene.add(bossMesh)

                    val bossWalkMotion = enemyLoader.loadAnimation(slug, walkClipName)
                    val bossAttackMotion = enemyLoader.loadAnimation(slug, attackClipName)
                    val bossMixer = AnimationMixer(bossMesh)

                    val bossAi = EnemyAI(
                        bossMesh,
                        bossMeshData.njObject,
                        bossMixer,
                        bossWalkMotion,
                        bossAttackMotion,
                        enemyWallCollider,
                        map.walkableCollisionObject,
                        boundingSphere(bossMesh).radius,
                    )

                    enemies.add(
                        Enemy(bossMesh, hp = if (slug == "Garanz") 3 else 30, animationMixer = bossMixer, ai = bossAi)
                    )
                }
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
                val (propX, propGroundY, propZ) = (
                    if (isStage) {
                        findNearestStableGroundHeight(map.walkableCollisionObject, x, z)
                    } else {
                        findNearestGroundHeight(map.walkableCollisionObject, x, z)
                    }
                    ) ?: Triple(x, .0, z)
                val propMesh = objectLoader.loadObject(slug)
                propMesh.position.set(propX, propGroundY, propZ)
                context.scene.add(propMesh)
            }

            // A few town NPCs standing around, bringing Pioneer 2 to life -- exercises the NPC
            // conversion pipeline (see NpcSpecs.kt) the same way the enemy/object rosters above
            // exercise their own specs. Purely decorative (no dialogue/shops), and only spawned in
            // Pioneer 2 itself since NPCs standing around amid the enemy roster everywhere else
            // wouldn't make sense. Each plays the player's own idle clip -- NPCs share the
            // player's skeleton closely enough that psov2 itself drives them with the same
            // plymotiondata.rlc, see NpcAssetLoader's doc comment.
            if (mapSlug == "pioneer2") {
                val npcLoader = NpcAssetLoader(assetLoader)
                val npcSlugs = listOf(
                    "RedRingRico", "Hakase", "Nurse", "Hisyo", "Soutoku", "Citizen",
                    "CitizenWoman2", "CitizenWoman3", "CitizenWoman4", "CitizenWoman5",
                    "CitizenWoman6", "CitizenWoman7", "CitizenMan1", "CitizenMan2", "CitizenMan3",
                    "CitizenMan4", "CitizenMan5", "CitizenMan6", "GovStaff1", "GovStaff2",
                    "GovStaff3", "GovStaff4", "GovStaff5", "GovStaff6", "GovStaff7", "GovStaff8",
                    "GovStaff9", "GovStaff10", "GuildStaff1", "GuildStaff2", "Trunk",
                )

                // The 14 remaining city NPCs, sourced from the standalone ".rel" footer-pointer
                // format instead of a nested bml (see NpcRelSpec's doc comment in
                // :web:assets-generation's NpcSpecs.kt) -- loaded via loadCityNpc instead of
                // loadNpc, otherwise spawned exactly the same way, sharing the same ring.
                val cityNpcSlugs = listOf(
                    "CityNpcA00", "CityNpcB00", "CityNpcD00", "CityNpcE00", "CityNpcF00",
                    "CityNpcG00", "CityNpcH00", "CityNpcI00", "CityNpcB01", "CityNpcC01",
                    "CityNpcD01", "CityNpcG01", "CityNpcH01", "CityNpcI01",
                )

                val totalNpcCount = npcSlugs.size + cityNpcSlugs.size

                // A wide single ring -- scales with the roster size so 45 NPCs don't crowd each
                // other the way the original 6-NPC radius would.
                val npcRingRadius = bSphereRadius * (2.0 + totalNpcCount * 0.3)

                fun spawnNpc(index: Int, npcMeshData: NpcMeshData) {
                    val angle = (index.toDouble() / totalNpcCount) * 2.0 * PI
                    val x = spawnX + npcRingRadius * sin(angle)
                    val z = spawnZ + npcRingRadius * cos(angle)
                    val (npcX, npcGroundY, npcZ) =
                        findNearestStableGroundHeight(map.walkableCollisionObject, x, z) ?: Triple(x, .0, z)
                    val npcMesh = npcMeshData.mesh
                    npcMesh.position.set(npcX, npcGroundY, npcZ)
                    npcMesh.rotation.y = -angle
                    context.scene.add(npcMesh)

                    val npcMixer = AnimationMixer(npcMesh)
                    npcMixer.clipAction(createAnimationClip(npcMeshData.njObject, idleMotion)).play()
                    npcMixers.add(npcMixer)
                }

                for ((i, slug) in npcSlugs.withIndex()) {
                    spawnNpc(i, npcLoader.loadNpc(slug))
                }
                for ((i, slug) in cityNpcSlugs.withIndex()) {
                    spawnNpc(npcSlugs.size + i, npcLoader.loadCityNpc(slug))
                }
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

    private fun toggleFlying() {
        val controller = player?.controller ?: return
        controller.flying = !controller.flying
        flyToggleButton.setActive(controller.flying)
        flightVerticalControls.setVisible(controller.flying)
    }

    override fun render() {
        val deltaTime = clock.getDelta()
        val p = player

        if (p != null) {
            coordinateDisplay.update(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z)

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
                    flightVerticalControls.ascending,
                    flightVerticalControls.descending,
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

        for (npcMixer in npcMixers) {
            npcMixer.update(deltaTime)
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

        /**
         * Per-stage (x,z) to search for ground from, instead of world (0,0) -- most stages haven't
         * needed one (their ground floor happens to be reachable from world origin), but Pioneer 2
         * does, since (0,0) itself sits under a large elevated walkway deck rather than directly
         * above the real floor. This coordinate was found and confirmed by the simplest possible
         * method: flying around Pioneer 2 in noclip (see FlyToggleButton) and reading the live
         * coordinate display (see CoordinateDisplay) off the exact spot that visually matched the
         * real game's ground floor, then landing there to confirm it was standable.
         *
         * That confirmation step is what caught a real bug, not just a bad guess: this exact floor
         * initially wasn't standable at all -- gravity fell straight through it. Pioneer 2's Stage
         * sections turn out not to share one consistent triangle winding order; MapAssetLoader's
         * buildCollisionMesh computes each triangle's normal with one fixed cross-product order,
         * which happens to come out pointing up for most sections but pointed down for this one,
         * failing isWalkable's up-facing slope test even though the floor is genuinely flat. Fixed
         * by making that check sign-independent (`abs()`) instead of trying to special-case the
         * winding per section -- see isWalkable's own doc comment. Add more entries here if another
         * stage turns out to need one too.
         */
        private val STAGE_SPAWN_ORIGINS: Map<String, Pair<Double, Double>> = mapOf(
            "pioneer2" to (0.75 to -13.43),
        )
    }
}
