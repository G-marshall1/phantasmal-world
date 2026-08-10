package world.phantasmal.web.mobileGame.rendering

import kotlin.math.PI
import kotlin.random.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.webui.dom.disposableListener
import org.w3c.dom.HTMLElement
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.parseXj
import world.phantasmal.psolib.fileFormats.ninja.parseXvm
import world.phantasmal.web.core.rendering.conversion.xvrTextureToThree
import world.phantasmal.web.core.boundingSphere
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.DisposableThreeRenderer
import world.phantasmal.web.core.rendering.RenderContext
import world.phantasmal.web.core.rendering.Renderer
import world.phantasmal.web.core.rendering.conversion.PSO_FRAME_RATE_DOUBLE
import world.phantasmal.web.core.rendering.conversion.collisionGeometryToGroup
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToMesh
import world.phantasmal.web.core.rendering.conversion.createAnimationClip
import world.phantasmal.web.externals.three.AnimationAction
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.externals.three.Clock
import world.phantasmal.web.externals.three.Material
import world.phantasmal.web.externals.three.PerspectiveCamera
import world.phantasmal.web.externals.three.SkinnedMesh
import world.phantasmal.web.mobileGame.camera.ThirdPersonCameraController
import world.phantasmal.web.mobileGame.debug.AnimationDebugOverlay
import world.phantasmal.web.mobileGame.input.ActionPalette
import world.phantasmal.web.mobileGame.input.DamageNumbers
import world.phantasmal.web.mobileGame.input.FlightVerticalControls
import world.phantasmal.web.mobileGame.input.VirtualJoystick
import world.phantasmal.web.mobileGame.input.PhotonBlastOverlay
import world.phantasmal.web.mobileGame.input.MapDoor
import world.phantasmal.web.mobileGame.input.MapRoom
import world.phantasmal.web.mobileGame.input.MiniMap
import world.phantasmal.web.mobileGame.input.PlayerStatusPanel
import world.phantasmal.web.mobileGame.input.TargetInfoPanel
import world.phantasmal.web.mobileGame.menu.ChatPanel
import world.phantasmal.web.mobileGame.menu.Emote
import world.phantasmal.web.mobileGame.menu.GameMenu
import world.phantasmal.web.mobileGame.menu.MenuRoom
import world.phantasmal.web.mobileGame.menu.MenuState
import world.phantasmal.web.mobileGame.player.CharacterController
import world.phantasmal.web.mobileGame.player.ActionPaletteConfig
import world.phantasmal.web.mobileGame.player.AttackType
import world.phantasmal.web.mobileGame.player.effectiveAtp
import world.phantasmal.web.mobileGame.player.isKnockdown
import world.phantasmal.web.mobileGame.player.isAndroid
import world.phantasmal.web.mobileGame.player.isRanged
import world.phantasmal.web.mobileGame.player.isFemaleCharacter
import world.phantasmal.web.mobileGame.player.professionOf
import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.mobileGame.player.GameAction
import world.phantasmal.web.mobileGame.player.BaseStats
import world.phantasmal.web.mobileGame.player.Mag
import world.phantasmal.web.mobileGame.player.MagCompanion
import world.phantasmal.web.mobileGame.player.PhotonBlastGauge
import world.phantasmal.web.mobileGame.player.CombatController
import world.phantasmal.web.mobileGame.player.CRITICAL_MULTIPLIER
import world.phantasmal.web.mobileGame.player.criticalChance
import world.phantasmal.web.mobileGame.player.physicalDamage
import world.phantasmal.web.mobileGame.player.psoUnit
import world.phantasmal.web.mobileGame.player.Enemy
import world.phantasmal.web.mobileGame.player.EnemyAI
import world.phantasmal.web.mobileGame.player.PlayerAppearance
import world.phantasmal.web.mobileGame.player.CANE_LINE
import world.phantasmal.web.mobileGame.player.HANDGUN_LINE
import world.phantasmal.web.mobileGame.player.SABER_LINE
import world.phantasmal.web.mobileGame.player.SpecialFamily
import world.phantasmal.web.mobileGame.player.Weapon
import world.phantasmal.web.mobileGame.player.WeaponItem
import world.phantasmal.web.mobileGame.player.rollForestWeaponDrop
import world.phantasmal.web.mobileGame.player.startingPhotonBlast
import world.phantasmal.web.mobileGame.player.specialEffectiveness
import world.phantasmal.web.mobileGame.player.WeaponType
import world.phantasmal.web.mobileGame.player.effectiveReach
import world.phantasmal.web.mobileGame.player.weaponType
import world.phantasmal.web.mobileGame.player.PlayerAnimations
import world.phantasmal.web.mobileGame.player.PlayerAnimator
import world.phantasmal.web.mobileGame.player.PlayerAssetLoader
import world.phantasmal.web.mobileGame.world.AREA_ENEMIES
import world.phantasmal.web.mobileGame.world.areaDisplayName
import world.phantasmal.web.mobileGame.world.DragonClips
import world.phantasmal.web.mobileGame.world.DragonFight
import world.phantasmal.web.mobileGame.world.DeRolLeClips
import world.phantasmal.web.mobileGame.world.DeRolLeFight
import world.phantasmal.web.mobileGame.world.VolOptClips
import world.phantasmal.web.mobileGame.world.VolOptFight
import world.phantasmal.web.mobileGame.world.DarkFalzClips
import world.phantasmal.web.mobileGame.world.DarkFalzFight
import world.phantasmal.web.mobileGame.world.EnemyAssetLoader
import world.phantasmal.web.mobileGame.world.EnemyClipSet
import world.phantasmal.web.mobileGame.world.enemyStats
import world.phantasmal.web.mobileGame.world.EnemyFragmentRef
import world.phantasmal.web.mobileGame.world.FieldGates
import world.phantasmal.web.mobileGame.world.MapAssetLoader
import world.phantasmal.web.mobileGame.world.QUEST_FLOOR_FOR_MAP
import world.phantasmal.web.mobileGame.world.QuestDef
import world.phantasmal.web.mobileGame.world.QuestHost
import world.phantasmal.web.mobileGame.world.QuestIndexEntry
import world.phantasmal.web.mobileGame.world.QuestSession
import world.phantasmal.web.mobileGame.world.QuestVm
import world.phantasmal.web.mobileGame.world.loadQuestDef
import world.phantasmal.web.mobileGame.world.loadQuestIndex
import world.phantasmal.web.mobileGame.world.questFieldLayout
import world.phantasmal.web.mobileGame.world.questGeometrySlug
import world.phantasmal.web.mobileGame.world.RICO_MESSAGES
import world.phantasmal.web.mobileGame.world.RoomWaveDirector
import world.phantasmal.web.mobileGame.world.TriggerVolume
import world.phantasmal.web.mobileGame.world.SpawnLayout
import world.phantasmal.web.mobileGame.world.SpawnObject
import world.phantasmal.web.mobileGame.world.SpawnedEnemy
import world.phantasmal.web.mobileGame.world.loadAreaSpawnTable
import world.phantasmal.web.mobileGame.world.loadClipSet
import world.phantasmal.web.mobileGame.world.pickSoloLayout
import world.phantasmal.web.mobileGame.world.NpcAssetLoader
import world.phantasmal.web.mobileGame.world.PIONEER2_DOORS
import world.phantasmal.web.mobileGame.world.PIONEER2_NPCS
import world.phantasmal.web.mobileGame.world.PIONEER2_TELEPORTERS
import world.phantasmal.web.mobileGame.world.Pioneer2Teleporter
import world.phantasmal.web.mobileGame.world.ObjectAssetLoader
import world.phantasmal.web.mobileGame.world.WeaponAssetLoader
import world.phantasmal.web.mobileGame.world.AREA_BOSSES
import world.phantasmal.web.mobileGame.world.ActiveTelepipe
import world.phantasmal.web.mobileGame.world.BossEncounter
import world.phantasmal.web.mobileGame.world.FieldBox
import world.phantasmal.web.mobileGame.world.BoxShard
import world.phantasmal.web.mobileGame.player.rollBoxDrop
import world.phantasmal.web.mobileGame.world.WallCollider
import world.phantasmal.web.mobileGame.world.randomAreaLayoutSlug
import world.phantasmal.web.mobileGame.world.findNearestGroundHeight
import world.phantasmal.web.mobileGame.world.findNearestStableGroundHeight
import world.phantasmal.web.mobileGame.persistence.CharacterSave
import world.phantasmal.web.mobileGame.persistence.toSaved
import world.phantasmal.web.mobileGame.persistence.toWeaponItem
import world.phantasmal.web.mobileGame.player.Drop
import world.phantasmal.web.mobileGame.player.MAX_LEVEL
import world.phantasmal.web.mobileGame.player.MAX_MESETA
import world.phantasmal.web.mobileGame.player.ToolType
import world.phantasmal.web.mobileGame.player.TreasureType
import world.phantasmal.web.mobileGame.player.levelForTotalExp
import world.phantasmal.web.mobileGame.player.rollEnemyDrop
import world.phantasmal.web.mobileGame.player.accuracyPercent
import world.phantasmal.web.mobileGame.player.statsAtLevel
import world.phantasmal.web.mobileGame.player.totalExpForLevel
import kotlin.js.Date
import world.phantasmal.web.externals.three.Matrix4
import world.phantasmal.web.externals.three.PlaneGeometry
import world.phantasmal.web.externals.three.Quaternion
import world.phantasmal.web.externals.three.DoubleSide
import world.phantasmal.web.externals.three.AdditiveBlending
import world.phantasmal.web.externals.three.Texture
import world.phantasmal.web.externals.three.TextureLoader
import world.phantasmal.web.externals.three.Sprite
import world.phantasmal.web.externals.three.SpriteMaterial
import world.phantasmal.web.externals.three.SphereGeometry
import world.phantasmal.web.externals.three.CylinderGeometry
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.MeshBasicMaterial
import world.phantasmal.web.externals.three.Color
import world.phantasmal.web.mobileGame.player.Technique
import world.phantasmal.web.mobileGame.player.techniqueDamage
import world.phantasmal.web.mobileGame.player.ICE_FREEZE_CHANCE
import world.phantasmal.web.mobileGame.player.supportBoostFraction
import world.phantasmal.web.mobileGame.player.supportDurationSeconds
import world.phantasmal.web.mobileGame.player.restaHeal
import world.phantasmal.web.mobileGame.player.Profession
import world.phantasmal.web.mobileGame.player.firstEvolutionOf
import world.phantasmal.web.mobileGame.player.TESTING_ARMORY
import world.phantasmal.web.mobileGame.player.armsShopStock
import world.phantasmal.web.mobileGame.player.weaponSellPrice
import world.phantasmal.web.mobileGame.player.TREASURE_SELL_PRICE
import world.phantasmal.web.mobileGame.player.WeaponTier
import world.phantasmal.webui.obj
import world.phantasmal.web.mobileGame.input.ActionBar
import world.phantasmal.web.mobileGame.player.ActionBarConfig
import world.phantasmal.web.mobileGame.player.weaponTierByName
import world.phantasmal.web.mobileGame.player.starterWeaponSlug
import world.phantasmal.web.mobileGame.input.ItemIcon
import world.phantasmal.web.mobileGame.menu.DialogRow
import world.phantasmal.web.mobileGame.menu.NpcDialog
import world.phantasmal.web.mobileGame.menu.NpcDialogState
import world.phantasmal.web.mobileGame.world.NpcRole
import world.phantasmal.web.mobileGame.world.PassageZone
import world.phantasmal.web.mobileGame.world.Pioneer2Npc
import world.phantasmal.web.mobileGame.player.TOOL_SHOP
import world.phantasmal.web.mobileGame.player.shopPrice
import world.phantasmal.web.mobileGame.player.toolSellPrice
import world.phantasmal.web.mobileGame.player.weaponBuyPrice
import world.phantasmal.web.mobileGame.persistence.toItem
import world.phantasmal.web.mobileGame.player.BarrierItem
import world.phantasmal.web.mobileGame.player.BarrierSpec
import world.phantasmal.web.mobileGame.player.FrameItem
import world.phantasmal.web.mobileGame.player.FRAME_SPECS
import world.phantasmal.web.mobileGame.player.BARRIER_SPECS
import world.phantasmal.web.mobileGame.player.FrameSpec
import world.phantasmal.web.mobileGame.player.RECOVERY_BARRIER_SPEC
import world.phantasmal.web.mobileGame.player.UNIT_SHOP
import world.phantasmal.web.mobileGame.player.UnitType
import world.phantasmal.web.mobileGame.player.armorShopBarriers
import world.phantasmal.web.mobileGame.player.armorShopFrames
import world.phantasmal.web.mobileGame.player.barrierSellPrice
import world.phantasmal.web.mobileGame.player.frameSellPrice
import world.phantasmal.web.mobileGame.player.rollBarrier
import world.phantasmal.web.mobileGame.player.rollFrame
import world.phantasmal.web.mobileGame.player.itemIcon
import world.phantasmal.web.mobileGame.player.unitByName
import world.phantasmal.web.mobileGame.player.unitSellPrice
import world.phantasmal.web.viewer.loading.AnimationAssetLoader
import world.phantasmal.web.viewer.loading.CharacterClassAssetLoader
import world.phantasmal.web.viewer.models.CharacterClass

/**
 * Loads [mapSlug]'s area (see MAP_SPECS in :web:assets-generation's MapSpecs.kt for the full list
 * of 10 slugs), spawns a player character standing on the ground, and drives it with joystick
 * input + wall/ground collision, followed by a third-person camera. Also spawns a roster of
 * enemies, each with a basic chase-and-melee brain (see EnemyAI.kt) that damages the player on
 * contact, and the player's own attack button, which cycles through a 3-hit saber combo.
 */
class GameRenderer(
    private val assetLoader: AssetLoader,
    createThreeRenderer: (HTMLCanvasElement) -> DisposableThreeRenderer,
    private val mapSlug: String = "forest01",
    private val appearance: PlayerAppearance = PlayerAppearance.DEFAULT,
    private val characterName: String = "Guest",
    /**
     * The save behind this session, if one exists (the debug ?map= route has none). Progress --
     * EXP, meseta, the pack, the equipped weapon -- writes back through [onProgress] after every
     * change, so a mid-fight app kill loses nothing.
     */
    private val save: CharacterSave? = null,
    private val onProgress: (CharacterSave) -> Unit = {},
    /** DEBUG: overrides the stage's usual spawn (x, z), for inspecting a far-off spot. */
    private val spawnOverride: Pair<Double, Double>? = null,
    /**
     * DEBUG: skips the ground search and spawns at this exact Y. Needed around the shop and guild
     * doors, where the search reports y = -182.5 even though the real floor is y = 0.
     */
    private val spawnYOverride: Double? = null,
    /** DEBUG: spawn facing in radians -- pi shows the character's front to the camera. */
    private val facingOverride: Double? = null,
    /** DEBUG (rig): absolute camera yaw in radians -- the camera's own facing, not the character's. */
    private val cameraYawOverride: Double? = null,
    /** DEBUG: freeze the character at (clip index, frame) -- weapon-orientation inspection. */
    private val poseOverride: Pair<Int, Int>? = null,
    /** DEBUG: log every bone's world position once posed -- finds the hand bone per class. */
    private val boneScan: Boolean = false,
    /**
     * DEBUG: spawn enemies with no AI and no clip playing, so they hold their bind pose. The
     * discriminator for skeleton bugs: a correct bind pose means the mesh/skeleton conversion
     * is sound and the animation side is at fault; a broken one blames the conversion itself.
     */
    private val debugBindPose: Boolean = false,
    /**
     * DEBUG: lay the named fx texture archive (assets/fx/<name>.xvm) out as a numbered ground
     * grid around the spawn -- the contact sheet the technique-effect texture indices are read
     * off of. [fxSheetPage] pages through archives bigger than one grid.
     */
    private val fxSheet: String? = null,
    private val fxSheetPage: Int = 0,
    /**
     * DEBUG: stretch every timed effect 20x (rates scaled to match). Headless captures under
     * --virtual-time-budget fast-forward the clock, so a sub-second effect expires before any
     * screenshot -- this keeps the shipped code paths while making them photographable.
     */
    private val fxSlowMotion: Boolean = false,
    /**
     * Invoked once when the player steps onto a pad that leaves this map (see
     * Pioneer2Teleporter.destinationMap), with the slug to load. The renderer can't swap its own
     * map -- mapSlug is fixed at construction and the whole scene is built around it -- so the
     * owner rebuilds instead; see GameShell.enterGame.
     */
    private val onAreaTransition: ((String) -> Unit)? = null,
    /**
     * Invoked if the world fails to build. Everything after the constructor happens in a
     * coroutine, so without this a failed asset load left a black screen with no way out --
     * indistinguishable, to the player, from the game simply being broken.
     */
    private val onSetupError: ((Throwable) -> Unit)? = null,
    /**
     * DEBUG: shows on-screen prev/next controls that cycle the player through all 572 numbered
     * clips, with the weapon equipped, so the right index for a given pose can be read off
     * directly rather than guessed. See ?animBrowser=1 in Main.kt.
     */
    private val showAnimationBrowser: Boolean = false,
    /**
     * DEBUG: pins this area's encounter table by name instead of picking one at random, so a
     * particular room's waves can be walked through repeatedly. See ?layout= in Main.kt.
     */
    private val layoutOverride: String? = null,
    /** DEBUG (rig): pins the area's random geometry roll so ?layout= and the world agree. */
    private val geometryOverride: String? = null,
    /**
     * Which weapon to equip, by model slug -- see WEAPON_TYPES for everything shipped. Decides
     * both the model in the character's hand and the whole motion set they use. There's no
     * inventory yet, so this is how a weapon gets chosen; see ?weapon= in Main.kt.
     */
    // Mutable: runtime weapon-class switching re-points it at the newly drawn model.
    private var weaponSlug: String = "Saber",
) : Renderer() {
    // Real PSO doesn't allow combat in town/lobby hubs -- no weapon drawn, no action palette, no
    // radar. Deliberately narrower than STAGE_SLUGS, which also covers boss arenas/spaceship/
    // temple stages where you do fight with a weapon drawn.
    private val isPeacefulHub = mapSlug in PEACEFUL_HUB_SLUGS

    private val clock = Clock()
    // Skin-aware: prefers the personal-build body-variant texture archives when present.
    private val characterClassAssetLoader =
        addDisposable(CharacterClassAssetLoader(world.phantasmal.web.mobileGame.loading.SkinAssetLoader()))
    private val animationAssetLoader = addDisposable(AnimationAssetLoader(assetLoader))
    private val joystick = addDisposable(VirtualJoystick(document.body!!))
    private val playerStatusPanel = addDisposable(
        PlayerStatusPanel(
            document.body!!,
            characterName,
            onMenuTap = { openMenu() },
            onPhotonBlastTap = { activatePhotonBlast() },
        )
    )
    private val miniMap = if (isPeacefulHub) null else addDisposable(MiniMap(document.body!!))
    private val targetInfoPanel =
        if (isPeacefulHub) null else addDisposable(TargetInfoPanel(document.body!!))
    // Flight is a chat command (`?fly?`), not a HUD button -- it's a debug tool, and the screen
    // edge is worth more than a permanent control most sessions never touch.
    private val flightVerticalControls = addDisposable(FlightVerticalControls(document.body!!))
    private val damageNumbers = addDisposable(DamageNumbers(document.body!!))
    private val gameMenu = addDisposable(
        GameMenu(
            document.body!!,
            onClose = {},
            onPaletteChanged = {
                actionPalette?.refresh()
                actionBar?.refresh()
            },
        )
    )
    private val chatPanel = addDisposable(
        ChatPanel(
            document.body!!,
            onEmote = { playEmote(it) },
            onClose = {},
            onCommand = { runChatCommand(it) },
        )
    )
    /** Armed by tapping the HUD's PB dial with a full gauge -- see activatePhotonBlast. */
    private val photonBlastOverlay =
        addDisposable(PhotonBlastOverlay(document.body!!) { firePhotonBlast() })

    private var player: Player? = null

    /** Which action sits in which hex. Editable by the player; persisted per device. */
    private val paletteConfig = ActionPaletteConfig.load()

    private var actionPalette: ActionPalette? = null

    /** The floating Mag model, once it has loaded. */
    private var magCompanion: MagCompanion? = null

    /** Emote clips, loaded on first use and kept -- see playEmote. */
    private val emoteMotions = mutableMapOf<Int, NjMotion>()

    /** The equipped weapon's attack power, added to the character's own on every swing. */
    private var equippedWeaponAtp: Int = 0

    /** DEBUG: while set, the render loop leaves the frozen pose clip alone -- see poseOverride. */
    private var poseLocked = false

    /** The model group on the hand bone, kept so a weapon-class switch can replace it. */
    private var weaponAttachment: Object3D? = null

    /** Runs this area's rooms and waves, once its encounter table has loaded. Null in hubs. */
    private var roomWaveDirector: RoomWaveDirector? = null

    /** The area's doors/fences/switches, or null on maps without object placements. */
    private var fieldGates: FieldGates? = null

    /**
     * An attack tapped while the current swing still had input locked, waiting to fire the frame
     * the lockout ends -- see [swing]. The last tap wins, so a player who changes their mind
     * mid-swing (normal tapped, then heavy) gets the heavy.
     */
    private var bufferedAttack: AttackType? = null

    /**
     * Spawns one enemy of a rostered species into the world, set once the field map's enemy
     * pipeline is up -- the same assembly the wave director uses, exposed for Monest hives'
     * runtime Mothmant production.
     */
    private var spawnEnemy: ((String, Double, Double, Double, Double) -> Enemy?)? = null

    /**
     * One living Monest and its brood. The hive emits a Mothmant every [HIVE_EMIT_INTERVAL]
     * seconds while the player is within [HIVE_PRODUCTION_RANGE_UNITS] and fewer than
     * [HIVE_MAX_MOTHMANTS] of its brood are alive, playing its "exit" (mouth-open) clip per
     * emission -- the real game's hive behaviour. Production stops for good when the hive dies;
     * its already-flying brood fights on.
     *
     * Brood Mothmants deliberately don't register with the wave director: production is
     * open-ended, so counting them toward a wave would let a hive hold its room's doors shut
     * forever. Killing the Monest itself is what clears its wave slot, exactly like the real
     * game.
     */
    private class HiveState(val hive: Enemy) {
        val brood = mutableListOf<Enemy>()
        var emitCooldown = 0.0
    }

    private val hives = mutableListOf<HiveState>()
    private var hiveProductionRangeSq = 0.0
    private var hiveEmitOffset = 0.0

    /**
     * The equipped weapon as an item -- tier stats, per-swing ATP roll, and possibly a special
     * attack. Null for weapon classes without an item line yet, which then use the old flat
     * class ATP.
     */
    private var equippedItem: WeaponItem? = null

    /** World-space blast radius, set once the player's unit scale is known at setup. */
    private var photonBlastRadiusSq = 0.0

    /** World units per PSO unit, set at setup -- what technique ranges are measured in. */
    private var worldUnit = 1.0

    private var actionBar: ActionBar? = null

    // --- Breakable crates ---

    private val fieldBoxes = mutableListOf<FieldBox>()
    private val boxShards = mutableListOf<BoxShard>()

    /** The fragments the current area's crates burst into -- set with the box model. */
    private var boxShardSlugs: List<String> = listOf("ForestBoxShardA", "ForestBoxShardB")

    /**
     * Stands the map's own crates. One mesh is loaded and cloned per placement rather than
     * fetched fifty times -- Forest 1 carries about fifty boxes a layout.
     */
    private suspend fun spawnFieldBoxes(layout: SpawnLayout, unitScale: Double) {
        val placements = layout.objects.filter { it.typeId in SpawnObject.BREAKABLE_BOX_TYPES }
        if (placements.isEmpty()) return

        // Each area breaks its own crates: the Ruins' dark containers come apart into their
        // own fragments, everything else still uses the Forest's wooden box.
        val boxSlug = if (mapSlug.startsWith("ruins")) "RuinsBox" else "ForestBox"
        boxShardSlugs =
            if (mapSlug.startsWith("ruins")) listOf("RuinsBoxShardA", "RuinsBoxShardB")
            else listOf("ForestBoxShardA", "ForestBoxShardB")
        val prototype = ObjectAssetLoader(assetLoader).loadObject(boxSlug)

        // Measured off the crate itself rather than guessed: the model's own footprint is what
        // decides where its sides are. The *half-extents*, not the half-diagonal -- an earlier
        // pass used the diagonal to cover the corners, which made every crate read 40% fatter
        // than it looks: you'd bump into air and break boxes the blade visibly missed. Walking
        // collision now tests the true rectangle; the strike test uses the widest half-extent.
        val extents = boxFootprintExtents(prototype)
        val halfX = extents?.first ?: (BOX_HIT_RADIUS_UNITS * unitScale)
        val halfZ = extents?.second ?: (BOX_HIT_RADIUS_UNITS * unitScale)
        val height = extents?.third ?: 0.0
        val radius = maxOf(halfX, halfZ)

        for (placement in placements) {
            val mesh = prototype.clone() as Mesh
            mesh.position.set(placement.x, placement.y, placement.z)
            mesh.rotation.y = placement.yaw
            context.scene.add(mesh)
            fieldBoxes.add(
                FieldBox(
                    mesh, placement.typeId, placement.x, placement.y, placement.z, radius,
                    halfX = halfX, halfZ = halfZ, yaw = placement.yaw, height = height,
                )
            )
        }
    }

    /** The crate's half-extents (x, z) and full height, world units. Null if unmeasurable. */
    private fun boxFootprintExtents(mesh: Object3D): Triple<Double, Double, Double>? {
        val geometry = mesh.asDynamic().geometry ?: return null
        if (geometry.boundingBox == null) geometry.computeBoundingBox()
        val box = geometry.boundingBox ?: return null
        val halfX = ((box.max.x as Double) - (box.min.x as Double)) / 2
        val halfZ = ((box.max.z as Double) - (box.min.z as Double)) / 2
        val height = (box.max.y as Double) - (box.min.y as Double)
        if (halfX <= 0.0 || halfZ <= 0.0) return null
        return Triple(halfX, halfZ, height)
    }

    /**
     * A swing that lands near a crate smashes it; a swing that lands on a visible trap sets it
     * off where it stands -- destroying traps from outside their blast is exactly how the real
     * game's androids clear them. Boxes take one hit in PSO, so this is a reach test rather
     * than a damage exchange, run with the weapon's own cone.
     *
     * [budget] is what's left of the weapon's target count after the enemies it reached -- a
     * saber that struck a Booma has spent its single target and passes through crates behind
     * it, and a saber that hit nothing breaks exactly one box, not the whole row.
     */
    private fun breakBoxesInSwing(p: Player, budget: Int) {
        if (budget <= 0) return
        if (fieldBoxes.isEmpty() && fieldTraps.isEmpty()) return

        val yaw = p.mesh.rotation.y
        val forwardX = sin(yaw)
        val forwardZ = cos(yaw)
        val reach = (p.weaponType.effectiveReach + PLAYER_HITBOX_UNITS_FOR_BOXES) * worldUnit
        val angleTan = tan(p.weaponType.angleDegrees * PI / 180.0)

        fun inCone(x: Double, z: Double, radius: Double): Double? {
            val dx = x - p.mesh.position.x
            val dz = z - p.mesh.position.z
            val along = dx * forwardX + dz * forwardZ
            if (along < 0 || along > reach + radius) return null
            val lateral = dx * forwardZ - dz * forwardX
            val halfWidth = angleTan * along + radius
            if (lateral < -halfWidth || lateral > halfWidth) return null
            return dx * dx + dz * dz
        }

        // Everything the blade could touch, nearest first, cut to the remaining budget.
        val struckBoxes = fieldBoxes.mapNotNull { box ->
            if (box.broken) null else inCone(box.x, box.z, box.radius)?.let { box to it }
        }
        val struckTraps = fieldTraps.mapNotNull { trap ->
            if (trap.spent || !trapVisibleTo(p, trap)) null
            else inCone(trap.x, trap.z, TRAP_MARKER_RADIUS_UNITS * worldUnit)?.let { trap to it }
        }
        val ordered = (struckBoxes.map { Triple(it.second, it.first, null as FieldTrap?) } +
            struckTraps.map { Triple(it.second, null as FieldBox?, it.first) })
            .sortedBy { it.first }
            .take(budget)

        for ((_, box, trap) in ordered) {
            box?.let { smashBox(it) }
            trap?.let { detonateTrap(it, p) }
        }
    }

    /** Whether this player can see (and so target) an unarmed trap -- android trap vision. */
    private fun trapVisibleTo(p: Player, trap: FieldTrap): Boolean =
        trap.armed || isAndroid(p.characterClass)

    /** Bursts a crate: fragments thrown clear, and whatever it was holding left behind. */
    private fun smashBox(box: FieldBox) {
        box.broken = true
        box.mesh.parent?.remove(box.mesh)

        // The Ruins' blob jar: breaking it splashes venom over whoever stands close. No
        // fragments, no loot -- the jar is the trap.
        if (box.typeId == SpawnObject.TYPE_RUINS_POISON_BLOB) {
            val burst = effectSprite(
                "burst_bright", BLOB_SPLASH_RADIUS_UNITS * 1.5, colorHex = LILY_SPIT_COLOR,
            )
            burst.position.set(box.x, box.y + 2.0 * worldUnit, box.z)
            addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 3.0))
            player?.let { p ->
                val dx = p.mesh.position.x - box.x
                val dz = p.mesh.position.z - box.z
                val reach = BLOB_SPLASH_RADIUS_UNITS * worldUnit
                if (dx * dx + dz * dz <= reach * reach && p.hp > 0) applyPoison(p)
            }
            return
        }

        MainScope().launch {
            val loader = ObjectAssetLoader(assetLoader)
            for (slug in boxShardSlugs) {
                val shard = loader.loadObject(slug)
                shard.position.set(box.x, box.y + 0.5 * worldUnit, box.z)
                context.scene.add(shard)
                boxShards.add(
                    BoxShard(
                        shard,
                        velocityX = (Random.nextDouble() - 0.5) * SHARD_SPREAD_UNITS * worldUnit,
                        velocityY = SHARD_RISE_UNITS * worldUnit,
                        velocityZ = (Random.nextDouble() - 0.5) * SHARD_SPREAD_UNITS * worldUnit,
                        spin = (Random.nextDouble() - 0.5) * SHARD_SPIN,
                        remaining = SHARD_SECONDS,
                    )
                )
            }
        }

        if (box.hidesEnemy) {
            // The crates that hide a monster: it bursts out where the box stood.
            val slug = enemies.firstOrNull()?.slug ?: "Booma"
            spawnEnemy?.invoke(slug, box.x, box.y, box.z, Random.nextDouble() * 2 * PI)
            showToast("Something was inside!")
            return
        }

        val drop = rollBoxDrop(appearance.sectionId, areaTier)
        MainScope().launch {
            val mesh = ObjectAssetLoader(assetLoader).loadObject(dropModelSlug(drop))
            if (drop.rare) tintRare(mesh)
            mesh.position.set(box.x, box.y + groundClearance(mesh), box.z)
            context.scene.add(mesh)
            pickups.add(DropPickup(mesh, drop))
        }
    }

    /**
     * Crates are solid: walk into one and you're pushed back out of it. Run straight after
     * movement so the player never renders inside a box, and only in the horizontal plane --
     * a crate blocks the way past it, it isn't something to stand on.
     */
    private fun resolveBoxCollisions(p: Player) {
        if (fieldBoxes.isEmpty()) return
        val playerRadius = PLAYER_HITBOX_UNITS_FOR_BOXES * worldUnit

        for (box in fieldBoxes) {
            if (box.broken) continue
            val dx = p.controller.position.x - box.x
            val dz = p.controller.position.z - box.z

            // Into the crate's own frame, so the test is against its actual rectangle rather
            // than a circle drawn round it -- the circle made square crates block at their
            // corners' distance on every side.
            val cosYaw = cos(box.yaw)
            val sinYaw = sin(box.yaw)
            val localX = dx * cosYaw - dz * sinYaw
            val localZ = dx * sinYaw + dz * cosYaw

            val limitX = box.halfX + playerRadius
            val limitZ = box.halfZ + playerRadius
            if (localX <= -limitX || localX >= limitX) continue
            if (localZ <= -limitZ || localZ >= limitZ) continue

            // Push out through the nearest face.
            val pushX = if (localX >= 0) limitX - localX else -limitX - localX
            val pushZ = if (localZ >= 0) limitZ - localZ else -limitZ - localZ
            val outX: Double
            val outZ: Double
            if (kotlin.math.abs(pushX) <= kotlin.math.abs(pushZ)) {
                outX = pushX; outZ = 0.0
            } else {
                outX = 0.0; outZ = pushZ
            }
            // Back to world axes.
            p.controller.position.x += outX * cosYaw + outZ * sinYaw
            p.controller.position.z += -outX * sinYaw + outZ * cosYaw
        }
    }

    /** Smashes the first standing crate within [radius] of a point. Used by the techniques. */
    private fun breakBoxNear(x: Double, z: Double, radius: Double): Boolean {
        for (box in fieldBoxes) {
            if (box.broken) continue
            val dx = box.x - x
            val dz = box.z - z
            val reach = radius + box.radius
            if (dx * dx + dz * dz <= reach * reach) {
                smashBox(box)
                return true
            }
        }
        return false
    }

    /** Smashes every standing crate under a straight cast -- Barta's freezing line. */
    private fun breakBoxesInLine(
        originX: Double,
        originZ: Double,
        dirX: Double,
        dirZ: Double,
        length: Double,
        halfWidth: Double,
    ) {
        for (box in fieldBoxes) {
            if (box.broken) continue
            val dx = box.x - originX
            val dz = box.z - originZ
            val along = dx * dirX + dz * dirZ
            if (along < 0 || along > length) continue
            val lateral = dx * dirZ - dz * dirX
            if (lateral < -(halfWidth + box.radius) || lateral > halfWidth + box.radius) continue
            smashBox(box)
        }
    }

    /** Tumbles the fragments outward and fades them out. */
    private fun updateBoxShards(deltaTime: Double) {
        val iterator = boxShards.iterator()
        while (iterator.hasNext()) {
            val shard = iterator.next()
            shard.remaining -= deltaTime
            if (shard.remaining <= 0) {
                shard.mesh.parent?.remove(shard.mesh)
                iterator.remove()
                continue
            }

            val fallen = (SHARD_SECONDS - shard.remaining)
            shard.mesh.position.x += shard.velocityX * deltaTime
            shard.mesh.position.z += shard.velocityZ * deltaTime
            shard.mesh.position.y +=
                (shard.velocityY - SHARD_GRAVITY_UNITS * worldUnit * fallen) * deltaTime
            shard.mesh.rotation.y += shard.spin * deltaTime

            val scale = (shard.remaining / SHARD_SECONDS).coerceAtLeast(0.05)
            shard.mesh.scale.set(scale, scale, scale)
        }
    }

    // --- The area's mini-boss and the way home it guards ---

    private val bossEncounter: BossEncounter? = AREA_BOSSES[mapSlug]

    /** The guardians, once the player has walked into the boss room. */
    private val bossEnemies = mutableListOf<Enemy>()
    private var bossEngaged = false
    private var bossDefeated = false

    /**
     * The Dragon's own fight controller, installed in place of the generic EnemyAI when the
     * arena engages -- see DragonFight for the phase script it runs.
     */
    private var dragonFight: DragonFight? = null
    private var deRolLeFight: DeRolLeFight? = null
    private var volOptFight: VolOptFight? = null
    private var darkFalzFight: DarkFalzFight? = null

    /** The area's enemy loader, kept past setup so the boss's extra clips can be fetched. */
    private var fieldEnemyLoader: EnemyAssetLoader? = null

    /** Which geometry variant loadArea resolved to -- the caves' spawn tables key off it. */
    private var resolvedGeometrySlug: String? = null

    /**
     * A shot fired by an enemy: the Nano Dragon's nano laser and the Poison Lily's venom spit.
     * Enemy fire is a real projectile rather than the hitscan the player's guns use -- these
     * cross a whole room, so they have to be dodgeable in flight.
     */
    private class EnemyShot(
        val mesh: Object3D,
        var dirX: Double,
        val dirY: Double,
        var dirZ: Double,
        val speed: Double,
        var remaining: Double,
        val damage: Int,
        /** Venom: poisons on contact. */
        val poisons: Boolean,
        /** 0 flies straight; above it, the shot steers toward the player each second. */
        val homing: Double = 0.0,
        /** Dark Falz's ice lines: each hit has a one-in-five chance to freeze. */
        val freezeChance: Double = 0.0,
    )

    private val enemyShots = mutableListOf<EnemyShot>()

    /**
     * Puts one enemy shot in the world, aimed from the firing body at the player's chest.
     * [poisons] marks the Lily's venom; the laser simply hurts.
     */
    private fun fireEnemyShot(
        enemy: Enemy,
        speedUnits: Double,
        damage: Int,
        poisons: Boolean,
        colorHex: Int,
        sizeUnits: Double,
    ) {
        val p = player ?: return
        val fromX = enemy.mesh.position.x
        val fromY = enemy.mesh.position.y +
            (if (enemy.visualTop > 0) enemy.visualTop * 0.65 else enemy.hitboxRadius)
        val fromZ = enemy.mesh.position.z

        val dx = p.mesh.position.x - fromX
        val dy = (p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit) - fromY
        val dz = p.mesh.position.z - fromZ
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1e-3) return

        spawnEnemyShotRaw(
            fromX, fromY, fromZ,
            dx / length, dy / length, dz / length,
            speedUnits, damage, colorHex, sizeUnits,
            poisons = poisons, stretch = !poisons,
        )
    }

    /** The low-level shot: direction given outright rather than aimed at the player. */
    private fun spawnEnemyShotRaw(
        fromX: Double,
        fromY: Double,
        fromZ: Double,
        dirX: Double,
        dirY: Double,
        dirZ: Double,
        speedUnits: Double,
        damage: Int,
        colorHex: Int,
        sizeUnits: Double,
        poisons: Boolean = false,
        stretch: Boolean = false,
        homing: Double = 0.0,
        freezeChance: Double = 0.0,
    ) {
        val mesh = Mesh(
            SphereGeometry(sizeUnits * worldUnit, 10, 8),
            MeshBasicMaterial(obj {
                color = Color(colorHex)
                blending = AdditiveBlending
                transparent = true
            }).also { it.depthWrite = false },
        )
        // The laser reads as a bolt rather than a ball: stretched along its flight.
        if (stretch) mesh.scale.set(1.0, 1.0, ENEMY_LASER_STRETCH)
        mesh.position.set(fromX, fromY, fromZ)
        mesh.rotation.y = atan2(dirX, dirZ)
        context.scene.add(mesh)

        enemyShots.add(
            EnemyShot(
                mesh,
                dirX, dirY, dirZ,
                speedUnits * worldUnit,
                ENEMY_SHOT_LIFETIME,
                damage,
                poisons,
                homing = homing,
                freezeChance = freezeChance,
            )
        )
    }

    /** Flies every enemy shot, and resolves the ones that reach the player. */
    private fun updateEnemyShots(deltaTime: Double) {
        val p = player
        val iterator = enemyShots.iterator()

        while (iterator.hasNext()) {
            val shot = iterator.next()
            shot.remaining -= deltaTime

            // A homing shot leans toward the player as it flies -- Vol Opt's missiles.
            if (shot.homing > 0 && p != null) {
                val tx = p.mesh.position.x - shot.mesh.position.x
                val tz = p.mesh.position.z - shot.mesh.position.z
                val length = sqrt(tx * tx + tz * tz)
                if (length > 1e-3) {
                    val blend = (shot.homing * deltaTime).coerceAtMost(1.0)
                    val newX = shot.dirX + (tx / length - shot.dirX) * blend
                    val newZ = shot.dirZ + (tz / length - shot.dirZ) * blend
                    val norm = sqrt(newX * newX + newZ * newZ)
                    if (norm > 1e-6) {
                        shot.dirX = newX / norm
                        shot.dirZ = newZ / norm
                    }
                }
            }

            val step = shot.speed * deltaTime
            shot.mesh.position.x += shot.dirX * step
            shot.mesh.position.y += shot.dirY * step
            shot.mesh.position.z += shot.dirZ * step

            var hit = false
            if (p != null && p.hp > 0 && p.invulnerableRemaining <= 0) {
                val dx = p.mesh.position.x - shot.mesh.position.x
                val dy = (p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit) -
                    shot.mesh.position.y
                val dz = p.mesh.position.z - shot.mesh.position.z
                val reach = ENEMY_SHOT_HIT_UNITS * worldUnit
                if (dx * dx + dy * dy + dz * dz <= reach * reach) {
                    hit = true
                    hurtPlayerFlat(p, shot.damage)
                    if (shot.poisons) applyPoison(p)
                    if (shot.freezeChance > 0 && Random.nextDouble() < shot.freezeChance) {
                        if (p.paralysisRemaining <= 0) showToast("Frozen!")
                        p.paralysisRemaining = TRAP_FREEZE_SECONDS
                    }
                }
            }

            if (hit || shot.remaining <= 0) {
                shot.mesh.parent?.remove(shot.mesh)
                iterator.remove()
            }
        }
    }

    /** Flat damage on the player from a source that doesn't roll to hit -- enemy fire. */
    private fun hurtPlayerFlat(p: Player, damage: Int) {
        p.hp = (p.hp - damage).coerceAtLeast(0)
        p.photonBlast.onDamageTaken(damage, p.level)
        p.invulnerableRemaining = INVULNERABILITY_DURATION
        p.hitReactionRemaining = HIT_REACTION_DURATION
        playerStatusPanel.setHealth(p.hp, p.maxHp)
        if (p.hp <= 0) handlePlayerDowned(p)
    }

    /** Venom: ticking damage that can wound but never finish the job. */
    private fun applyPoison(p: Player) {
        if (p.poisonRemaining <= 0) showToast("Poisoned!")
        p.poisonRemaining = POISON_SECONDS
        p.poisonTickRemaining = POISON_TICK_SECONDS
    }

    /**
     * A Lily's screech at whoever shoots it from range. Androids don't have a nervous system to
     * seize -- the wiki is explicit that paralysis simply doesn't apply to them.
     */
    private fun applyParalysis(p: Player) {
        if (isAndroid(p.characterClass)) return
        if (p.paralysisRemaining > 0) return
        p.paralysisRemaining = PARALYSIS_SECONDS
        showToast("Paralyzed!")
    }

    /** Runs the player's own status clocks: venom ticks, paralysis wearing off. */
    private fun updatePlayerStatuses(p: Player, deltaTime: Double) {
        if (p.paralysisRemaining > 0) p.paralysisRemaining -= deltaTime
        if (p.confusedRemaining > 0) p.confusedRemaining -= deltaTime

        if (p.poisonRemaining > 0) {
            p.poisonRemaining -= deltaTime
            p.poisonTickRemaining -= deltaTime
            if (p.poisonTickRemaining <= 0) {
                p.poisonTickRemaining = POISON_TICK_SECONDS
                // Poison wounds but never kills: PSO always leaves the last point of health.
                if (p.hp > 1) {
                    p.hp = (p.hp - POISON_TICK_DAMAGE).coerceAtLeast(1)
                    playerStatusPanel.setHealth(p.hp, p.maxHp)
                    damageNumbers.showDamage(
                        p.mesh.position.x,
                        p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit,
                        p.mesh.position.z,
                        POISON_TICK_DAMAGE,
                        false,
                    )
                }
            }
        }
    }

    /**
     * One armed floor trap. PSO's elemental traps are invisible until they trigger -- only
     * androids, with their permanent trap vision, see them beforehand. Stepping into the
     * trigger radius starts the arm timer (blinking all the while); when it runs out the trap
     * detonates: fire traps burn, the Mines' freeze traps hold you in place, its confuse traps
     * turn your controls around. Traps sharing a link id detonate together, which is what makes
     * the Caves' corridor chains go off as one.
     */
    private class FieldTrap(
        val mesh: Mesh,
        val x: Double,
        val y: Double,
        val z: Double,
        /** 0 = fire, 17 = freeze, 18 = confuse (the subtype byte the map data carries). */
        val subtype: Int,
        /** Traps with the same non-zero link detonate as one chain. */
        val link: Int,
        val triggerRadius: Double,
        val armSeconds: Double,
        /** The large floor traps blast half again as wide. */
        val blastScale: Double = 1.0,
    ) {
        var armed = false
        var armRemaining = 0.0
        var spent = false
    }

    private val fieldTraps = mutableListOf<FieldTrap>()

    /** Builds the trap's marker mesh: hidden from humans, a faint red disc to an android. */
    private fun spawnFieldTrap(obj: SpawnObject, blastScale: Double = 1.0) {
        val subtype = obj.paramsI.getOrNull(1) ?: 0
        val radiusParam = (obj.paramsI.getOrNull(0) ?: 0).toDouble()
        // The radius parameter is stored at 10x scale; clamp odd data to something playable.
        val triggerUnits = (radiusParam / 10.0).coerceIn(2.5, 8.0)
        val delayFrames = (obj.paramsI.getOrNull(2) ?: 20).toDouble()
        val armSeconds = (delayFrames / 30.0).coerceIn(0.5, 2.0)

        val mesh = Mesh(
            SphereGeometry(TRAP_MARKER_RADIUS_UNITS * worldUnit, 12, 8),
            MeshBasicMaterial(obj {
                color = Color(trapColor(subtype))
                transparent = true
            }).also { it.depthWrite = false },
        )
        mesh.scale.y = 0.35
        mesh.position.set(obj.x, obj.y + TRAP_MARKER_RADIUS_UNITS * worldUnit * 0.3, obj.z)
        // Invisible to everyone but an android until it arms.
        val material: dynamic = mesh.material
        material.opacity = 0.0
        context.scene.add(mesh)

        fieldTraps.add(
            FieldTrap(
                mesh,
                obj.x, obj.y, obj.z,
                subtype = subtype,
                link = obj.doorId,
                triggerRadius = triggerUnits * worldUnit,
                armSeconds = armSeconds,
                blastScale = blastScale,
            )
        )
    }

    private fun trapColor(subtype: Int): Int = when (subtype) {
        TRAP_SUBTYPE_FREEZE -> 0x66ccff
        TRAP_SUBTYPE_CONFUSE -> 0xcc66ff
        else -> 0xff5533
    }

    /** Arms, blinks, and detonates the field's traps. */
    private fun updateFieldTraps(p: Player, deltaTime: Double) {
        if (fieldTraps.isEmpty()) return
        val androidVision = isAndroid(p.characterClass)

        for (trap in fieldTraps) {
            if (trap.spent) continue
            val material: dynamic = trap.mesh.material

            if (!trap.armed) {
                // Android trap vision: the marker sits faintly visible before it arms.
                material.opacity = if (androidVision) 0.4 else 0.0

                val dx = p.mesh.position.x - trap.x
                val dz = p.mesh.position.z - trap.z
                if (dx * dx + dz * dz <= trap.triggerRadius * trap.triggerRadius) {
                    armTrapChain(trap)
                }
            } else {
                trap.armRemaining -= deltaTime
                // The blink accelerates as the timer runs down.
                val phase = trap.armRemaining * (10.0 + (trap.armSeconds - trap.armRemaining) * 14.0)
                material.opacity = if (phase.toInt() % 2 == 0) 1.0 else 0.35
                if (trap.armRemaining <= 0) detonateTrap(trap, p)
            }
        }

        fieldTraps.removeAll { trap ->
            if (trap.spent) trap.mesh.parent?.remove(trap.mesh) != null || true else false
        }
    }

    /** Arming one trap arms its whole chain -- linked traps detonate together. */
    private fun armTrapChain(trap: FieldTrap) {
        val chain =
            if (trap.link != 0) fieldTraps.filter { !it.spent && it.link == trap.link }
            else listOf(trap)
        for (t in chain) {
            if (t.armed) continue
            t.armed = true
            t.armRemaining = t.armSeconds
        }
    }

    /** The detonation: a burst at the trap, and its effect on anyone standing in the blast. */
    private fun detonateTrap(trap: FieldTrap, p: Player) {
        trap.spent = true

        val color = trapColor(trap.subtype)
        val blastUnits = TRAP_BLAST_RADIUS_UNITS * trap.blastScale
        val burst = effectSprite("burst_orange", blastUnits * 1.6, colorHex = color)
        burst.position.set(trap.x, trap.y + 2.0 * worldUnit, trap.z)
        addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 4.0))
        spawnExplosionDome(trap.x, trap.y, trap.z, blastUnits * worldUnit * 0.7, color)

        val dx = p.mesh.position.x - trap.x
        val dz = p.mesh.position.z - trap.z
        val blast = blastUnits * worldUnit
        if (dx * dx + dz * dz > blast * blast || p.hp <= 0) return

        when (trap.subtype) {
            TRAP_SUBTYPE_FREEZE -> {
                // Freeze holds androids too -- it's mechanical, not neural.
                if (p.paralysisRemaining <= 0) showToast("Frozen!")
                p.paralysisRemaining = TRAP_FREEZE_SECONDS
            }
            TRAP_SUBTYPE_CONFUSE -> {
                if (!isAndroid(p.characterClass)) {
                    if (p.confusedRemaining <= 0) showToast("Confused!")
                    p.confusedRemaining = TRAP_CONFUSE_SECONDS
                }
            }
            else -> {
                if (p.invulnerableRemaining <= 0) hurtPlayerFlat(p, TRAP_FIRE_DAMAGE)
            }
        }
    }

    /**
     * The slimes' two lives. A Pofuilly Slime slides the floor as a puddle -- flattened,
     * untargetable, immune to everything but traps -- and rises to its full body to strike
     * when it reaches its prey, which is the only window it can be hurt in. Struck without
     * being killed, it splits: the room fills with slimes until they're put down properly.
     */
    private class SlimeState(
        var risen: Boolean = false,
        var risenRemaining: Double = 0.0,
    )

    private val slimeStates = HashMap<Enemy, SlimeState>()

    private fun isSlime(slug: String) = slug == "PofuillySlimeBlue" || slug == "PouillySlimeRed"

    /** Runs every slime's puddle-and-rise cycle. */
    private fun updateSlimes(p: Player, deltaTime: Double) {
        for (enemy in enemies) {
            if (!isSlime(enemy.slug) || enemy.isDead) continue
            val state = slimeStates.getOrPut(enemy) { SlimeState() }
            val baseScale = enemyStats(enemy.slug).modelScale

            if (!state.risen) {
                // The puddle: flat on the stone and impossible to pin down.
                enemy.mesh.scale.set(baseScale * 1.3, baseScale * SLIME_PUDDLE_FLATTEN, baseScale * 1.3)
                enemy.untargetable = true

                val dx = p.mesh.position.x - enemy.mesh.position.x
                val dz = p.mesh.position.z - enemy.mesh.position.z
                val rise = (SLIME_RISE_RANGE_UNITS + enemy.hitboxRadius / worldUnit) * worldUnit
                if (dx * dx + dz * dz <= rise * rise) {
                    state.risen = true
                    state.risenRemaining = SLIME_RISEN_SECONDS
                    enemy.mesh.scale.set(baseScale, baseScale, baseScale)
                    enemy.untargetable = false
                }
            } else {
                state.risenRemaining -= deltaTime
                if (state.risenRemaining <= 0) {
                    state.risen = false
                }
            }
        }
    }

    /**
     * A slime hit without being killed divides. Capped by how many slimes already crowd the
     * spot, so a careless sword can't farm a room into a flood.
     */
    private fun trySplitSlime(enemy: Enemy) {
        if (!isSlime(enemy.slug) || enemy.isDead) return

        val nearby = enemies.count { other ->
            isSlime(other.slug) && !other.isDead &&
                run {
                    val dx = other.mesh.position.x - enemy.mesh.position.x
                    val dz = other.mesh.position.z - enemy.mesh.position.z
                    val range = SLIME_CAP_RANGE_UNITS * worldUnit
                    dx * dx + dz * dz <= range * range
                }
        }
        if (nearby >= SLIME_CAP) return

        val angle = Random.nextDouble() * 2 * PI
        val offset = SLIME_SPLIT_OFFSET_UNITS * worldUnit
        spawnEnemy?.invoke(
            enemy.slug,
            enemy.mesh.position.x + sin(angle) * offset,
            enemy.mesh.position.y,
            enemy.mesh.position.z + cos(angle) * offset,
            Random.nextDouble() * 2 * PI,
        )
    }

    /**
     * The Ruins' ceiling pillar: it hangs high over its spot until someone walks underneath,
     * then comes down like a hammer -- crushing damage in a small circle -- rests a moment,
     * and winds back up to do it again. Never disarmed, only avoided.
     */
    private class FieldPillar(
        val mesh: Object3D,
        val x: Double,
        val groundY: Double,
        val z: Double,
        val hangY: Double,
    ) {
        var state = STATE_HANGING
        var restRemaining = 0.0

        companion object {
            const val STATE_HANGING = 0
            const val STATE_FALLING = 1
            const val STATE_RESTING = 2
            const val STATE_RISING = 3
        }
    }

    private val fieldPillars = mutableListOf<FieldPillar>()

    private fun updateFieldPillars(p: Player, deltaTime: Double) {
        for (pillar in fieldPillars) {
            when (pillar.state) {
                FieldPillar.STATE_HANGING -> {
                    val dx = p.mesh.position.x - pillar.x
                    val dz = p.mesh.position.z - pillar.z
                    val trigger = PILLAR_TRIGGER_UNITS * worldUnit
                    if (dx * dx + dz * dz <= trigger * trigger && p.hp > 0) {
                        pillar.state = FieldPillar.STATE_FALLING
                    }
                }
                FieldPillar.STATE_FALLING -> {
                    pillar.mesh.position.y -= PILLAR_FALL_UNITS_PER_SECOND * worldUnit * deltaTime
                    if (pillar.mesh.position.y <= pillar.groundY) {
                        pillar.mesh.position.y = pillar.groundY
                        pillar.state = FieldPillar.STATE_RESTING
                        pillar.restRemaining = PILLAR_REST_SECONDS

                        // The slam: dust, and crushing damage to anyone under it.
                        val burst = effectSprite("burst_orange", PILLAR_CRUSH_RADIUS_UNITS * 1.6, colorHex = 0xc9b18a)
                        burst.position.set(pillar.x, pillar.groundY + 1.5 * worldUnit, pillar.z)
                        addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 3.5))

                        val dx = p.mesh.position.x - pillar.x
                        val dz = p.mesh.position.z - pillar.z
                        val crush = PILLAR_CRUSH_RADIUS_UNITS * worldUnit
                        if (dx * dx + dz * dz <= crush * crush && p.hp > 0 &&
                            p.invulnerableRemaining <= 0
                        ) {
                            hurtPlayerFlat(p, PILLAR_CRUSH_DAMAGE)
                        }
                    }
                }
                FieldPillar.STATE_RESTING -> {
                    pillar.restRemaining -= deltaTime
                    if (pillar.restRemaining <= 0) pillar.state = FieldPillar.STATE_RISING
                }
                FieldPillar.STATE_RISING -> {
                    pillar.mesh.position.y += PILLAR_RISE_UNITS_PER_SECOND * worldUnit * deltaTime
                    if (pillar.mesh.position.y >= pillar.hangY) {
                        pillar.mesh.position.y = pillar.hangY
                        pillar.state = FieldPillar.STATE_HANGING
                    }
                }
            }
        }
    }

    /** The Caves' heal rings: world position, and whether this visit has spent it. */
    private val healRings = mutableListOf<Triple<Double, Double, Double>>()
    private val spentHealRings = mutableSetOf<Int>()

    /** Restores the player when they step into a heal ring they haven't used yet. */
    private fun updateHealRings(p: Player) {
        if (healRings.isEmpty() || p.hp <= 0) return
        val radius = HEAL_RING_RADIUS_UNITS * worldUnit

        for ((index, ring) in healRings.withIndex()) {
            if (index in spentHealRings) continue
            val dx = p.mesh.position.x - ring.first
            val dz = p.mesh.position.z - ring.third
            if (dx * dx + dz * dz > radius * radius) continue
            if (p.hp >= p.maxHp) continue

            spentHealRings.add(index)
            p.hp = p.maxHp
            playerStatusPanel.setHealth(p.hp, p.maxHp)
            supportRing(ring.first, ring.second, ring.third, "resta_ring", RESTA_COLOR)
            spawnHealLights(ring.first, ring.second, ring.third, RESTA_COLOR)
            showToast("The ring restores you")
            persistProgress()
        }
    }

    /** The warp home, which only exists once the boss falls. */
    private var returnWarp: Object3D? = null

    /**
     * A warp only fires once the player has been outside it at least once. It opens where the
     * boss died -- often right where the player is standing -- and a warp that triggers the
     * instant it appears would end the run without the player choosing to leave.
     */
    private var returnWarpArmed = false

    /**
     * The area's walkable collision, kept so the warp can be stood on the ground long after
     * setup's own GameMap local has gone out of scope.
     */
    private var walkableCollision: Object3D? = null

    /**
     * Wakes the boss when the player sets foot in its room. If the randomly chosen encounter
     * layout already placed Hildebears there, those *are* the boss -- adopting them keeps the
     * room from holding one more bear than the map intended.
     */
    private fun updateBossRoom() {
        val encounter = bossEncounter ?: return
        if (bossEngaged || bossDefeated) return
        if (roomWaveDirector?.currentSectionId != encounter.sectionId) return

        bossEngaged = true

        val bossSlugs = encounter.enemies.map { it.slug }.toSet()
        val alreadyThere = enemies.filter { !it.isDead && it.slug in bossSlugs }
        if (alreadyThere.isNotEmpty()) {
            bossEnemies.addAll(alreadyThere)
        } else {
            val spawn = spawnEnemy
            for (boss in encounter.enemies) {
                // Stand the boss on the arena's real floor: the synthetic arenas author y = 0,
                // but the stage's terrain is nowhere near it (the Dragon spawned inside the
                // mound and the fight looked empty).
                // lowest = true: the arena's walkable set holds both the outer shell's dome
                // and the real floor object beneath it -- the fight belongs on the floor.
                val grounded = walkableCollision
                    ?.let { findNearestGroundHeight(it, boss.x, boss.z, maxRadius = 120.0, lowest = true) }
                val bossX = grounded?.first ?: boss.x
                val bossY = grounded?.second ?: boss.y
                val bossZ = grounded?.third ?: boss.z
                spawn?.invoke(boss.slug, bossX, bossY, bossZ, boss.yaw)?.let { bossEnemies.add(it) }
            }
        }

        if (bossEnemies.isEmpty()) {
            // The boss didn't spawn -- stay un-engaged so entering the room again retries.
            //
            // This used to "rescue" the player by opening the warp home at their own feet, which
            // was far worse than the problem: the boss species wasn't being preloaded, so the
            // spawn always failed, and the warp opened under the player and fired instantly.
            // Every trip into the Forest ended the moment they moved. A fallback that silently
            // teleports someone out of the level is not a safe default.
            bossEngaged = false
            return
        }

        if (encounter.bossKey == "dragon" && !debugBindPose) {
            bossEnemies.firstOrNull { it.slug == "Dragon" }?.let {
                installDragonFight(it)
                registerDragonPartTapListeners()
            }
        }

        if (encounter.bossKey == "deRolLe" && !debugBindPose) {
            bossEnemies.firstOrNull { it.slug == "DeRolLe" }?.let { installDeRolLeFight(it) }
        }

        if (encounter.bossKey == "volOpt" && !debugBindPose) {
            val coreEnemy = bossEnemies.firstOrNull { it.slug == "VolOptForm1" }
            val robotEnemy = bossEnemies.firstOrNull { it.slug == "VolOpt" }
            if (coreEnemy != null && robotEnemy != null) {
                installVolOptFight(coreEnemy, robotEnemy)
            }
        }

        if (encounter.bossKey == "darkFalz" && !debugBindPose) {
            val mountEnemy = bossEnemies.firstOrNull { it.slug == "DarkFalzForm1Body" }
            val soulEnemy = bossEnemies.firstOrNull { it.slug == "DarkFalzForm2Body" }
            if (mountEnemy != null && soulEnemy != null) {
                installDarkFalzFight(mountEnemy, soulEnemy)
            }
        }

        showToast(encounter.arrivalMessage)
    }

    /**
     * Swaps the Dragon's generic chase brain for its real fight (see DragonFight). The generic
     * AI comes off immediately -- its terrain-following against the arena's stacked walkable
     * surfaces is what had the boss popping in and out of the floor -- and the controller takes
     * over as soon as its clip roster has loaded.
     */
    private fun installDragonFight(boss: Enemy) {
        val loader = fieldEnemyLoader ?: return
        val prototype = loader.prototype(boss.slug) ?: return
        val mixer = boss.animationMixer ?: return
        boss.ai = null
        boss.mesh.position.y = .0

        MainScope().launch {
            suspend fun clip(name: String): NjMotion? = try {
                loader.loadAnimation(boss.slug, "${name}_boss1_s_nb_dragon.njm", prototype.njObject)
            } catch (e: Throwable) {
                console.warn("Dragon clip $name failed to load: ${e.message}")
                null
            }

            val walk = clip("walk") ?: return@launch
            val fire = clip("fire") ?: return@launch
            dragonFight = DragonFight(
                enemy = boss,
                njObject = prototype.njObject,
                mixer = mixer,
                clips = DragonClips(
                    stand = clip("stand"),
                    walk = walk,
                    fire = fire,
                    wingsOpen = clip("wngopn"),
                    fly = clip("fly") ?: walk,
                    flyShot = clip("flyshot"),
                    land = clip("land"),
                    burstOut = clip("tobidasi"),
                    plunge = clip("tukomi"),
                    knockFall = clip("nkdown"),
                    knockDown = clip("down"),
                    knockRise = clip("nkup"),
                    roar = clip("nobi"),
                    death = clip("dead"),
                ),
                scene = context.scene,
                unitScale = worldUnit,
                floorY = .0,
                arenaRadius = DRAGON_ARENA_RADIUS,
                strikePlayer = ::dragonStrikesPlayer,
                strikePlayerFixed = ::dragonStrikesPlayerFixed,
            )
        }
    }

    /** The Dragon part the lock is on: the player's manual tap choice while it lasts. */
    private var dragonPartOverride = -1
    private var dragonPartOverrideRemaining = 0.0
    private val partScratch = Vector3()

    // A quick tap (short, unmoved) on the world picks the Dragon part nearest the fingertip.
    // Registered directly rather than through the camera controller: taps that land on HUD
    // buttons never reach the canvas, and the camera's own drag logic ignores unmoved taps.
    private var tapPointerId = -1
    private var tapStartX = 0
    private var tapStartY = 0
    private var tapStartMs = 0.0

    private fun registerDragonPartTapListeners() {
        addDisposable(
            context.canvas.disposableListener("pointerdown", { e: PointerEvent ->
                if (tapPointerId == -1) {
                    tapPointerId = e.pointerId
                    tapStartX = e.clientX
                    tapStartY = e.clientY
                    tapStartMs = window.performance.now()
                }
            })
        )
        addDisposable(
            context.canvas.disposableListener("pointerup", { e: PointerEvent ->
                if (e.pointerId == tapPointerId) {
                    tapPointerId = -1
                    val moved = abs(e.clientX - tapStartX) + abs(e.clientY - tapStartY)
                    if (moved <= 12 && window.performance.now() - tapStartMs <= 350) {
                        selectDragonPartAt(e.clientX.toDouble(), e.clientY.toDouble())
                    }
                }
            })
        )
    }

    /** Locks the Dragon part whose screen projection is nearest the tap, if any is close. */
    private fun selectDragonPartAt(screenX: Double, screenY: Double) {
        val fight = dragonFight ?: return
        if (fight.enemy.isDead || fight.enemy.untargetable) return

        var best = -1
        var bestD = DRAGON_PART_TAP_RANGE_PX * DRAGON_PART_TAP_RANGE_PX
        for (i in fight.parts.indices) {
            fight.partPosition(i, partScratch)
            partScratch.project(context.camera)
            if (partScratch.z > 1.0) continue
            val px = (partScratch.x + 1) / 2 * context.canvas.clientWidth
            val py = (1 - partScratch.y) / 2 * context.canvas.clientHeight
            val dx = px - screenX
            val dy = py - screenY
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best >= 0) {
            dragonPartOverride = best
            dragonPartOverrideRemaining = DRAGON_PART_OVERRIDE_SECONDS
            showToast("Targeting the ${fight.parts[best].name}")
        }
    }

    /** Which of the boss's parts the lock is on: the tapped one, else whichever is nearest. */
    private fun currentDragonPart(fight: DragonFight): Int {
        if (dragonPartOverrideRemaining > 0 && dragonPartOverride in fight.parts.indices) {
            return dragonPartOverride
        }
        val p = player ?: return 0
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in fight.parts.indices) {
            fight.partPosition(i, partScratch)
            val dx = partScratch.x - p.mesh.position.x
            val dy = partScratch.y - p.mesh.position.y
            val dz = partScratch.z - p.mesh.position.z
            val d = dx * dx + dy * dy + dz * dz
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    /**
     * Where the current lock actually points, in the world: the Dragon's focused part, or head
     * level on an ordinary enemy. This is the point the reticle draws on and ranged fire flies
     * at. Returns the lock's world radius, for sizing the reticle to the body.
     */
    private fun focusAimPoint(target: Enemy, out: Vector3): Double {
        val fight = dragonFight
        if (fight != null && fight.enemy === target) {
            val part = currentDragonPart(fight)
            fight.partPosition(part, out)
            return fight.parts[part].radiusUnits * worldUnit
        }
        // Head level is the model's own bounding-box top, not the bounding sphere -- the
        // sphere's radius is inflated by wide poses (arm spans, wings) and floated the lock
        // high over everything bigger than a Booma.
        val headY =
            if (target.visualTop > 0) target.mesh.position.y + target.visualTop * HEAD_LEVEL_FACTOR
            else target.mesh.position.y + target.visualRadius
        out.set(target.mesh.position.x, headY, target.mesh.position.z)
        return maxOf(target.hitboxRadius, target.visualRadius * 0.55)
    }

    /**
     * One Dragon blow, through the same evasion/defence/i-frame path a field enemy's melee hit
     * takes -- the fight's per-frame contact and breath checks are rate-capped by the same
     * i-frame window, so standing in the flame burns per window, not per frame.
     */
    private fun dragonStrikesPlayer(atpMultiplier: Double, forceKnockdown: Boolean) {
        val boss = dragonFight?.enemy ?: deRolLeFight?.enemy ?: return
        val p = player ?: return
        if (p.hp <= 0 || gameMenu.isOpen || p.invulnerableRemaining > 0) return

        val hitChance = accuracyPercent(
            totalAta = enemyStats(boss.slug).ata,
            type = AttackType.NORMAL,
            comboStep = 0,
            targetEvp = p.stats.evp,
        )
        if (Random.nextDouble() * 100.0 >= hitChance) {
            p.blockRemaining = BLOCK_REACTION_DURATION
            p.invulnerableRemaining = INVULNERABILITY_DURATION
            damageNumbers.showMiss(
                p.mesh.position.x,
                p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit,
                p.mesh.position.z,
            )
            return
        }

        val base = physicalDamage((boss.effectiveAtp * atpMultiplier).toInt(), p.stats.dfp)
        val critical = Random.nextDouble() < criticalChance(boss.lck, monster = true)
        val damage = (if (critical) base * CRITICAL_MULTIPLIER else base.toDouble())
            .toInt()
            .coerceAtLeast(1)

        p.hp = (p.hp - damage).coerceAtLeast(0)
        p.photonBlast.onDamageTaken(damage, p.level)
        if (forceKnockdown || isKnockdown(damage, p.maxHp)) {
            p.knockedDownRemaining = KNOCKDOWN_DURATION
        }
        p.invulnerableRemaining = INVULNERABILITY_DURATION
        p.hitReactionRemaining = HIT_REACTION_DURATION
        playerStatusPanel.setHealth(p.hp, p.maxHp)

        if (p.hp <= 0) handlePlayerDowned(p)
    }

    /**
     * One of the Dragon's fixed-damage attacks (see the wiki's damage table): the exact figure
     * lands regardless of the player's DFP, with no evasion roll -- these are area attacks you
     * dodge with your feet, not your EVP. The shared i-frame window still rate-caps them.
     */
    private fun dragonStrikesPlayerFixed(damage: Int, forceKnockdown: Boolean) {
        val p = player ?: return
        if (p.hp <= 0 || gameMenu.isOpen || p.invulnerableRemaining > 0) return

        p.hp = (p.hp - damage).coerceAtLeast(0)
        p.photonBlast.onDamageTaken(damage, p.level)
        if (forceKnockdown || isKnockdown(damage, p.maxHp)) {
            p.knockedDownRemaining = KNOCKDOWN_DURATION
        }
        p.invulnerableRemaining = INVULNERABILITY_DURATION
        p.hitReactionRemaining = HIT_REACTION_DURATION
        playerStatusPanel.setHealth(p.hp, p.maxHp)

        if (p.hp <= 0) handlePlayerDowned(p)
    }

    /** A carried Scape Doll fires by itself; with none left the respawn clock starts. */
    /**
     * De Rol Le's raft fight: clips loaded, the generic AI stripped, and the worm handed to
     * its controller. The deck hazards (mines, orbs, rocks, the beam) are all host-drawn
     * through the callbacks -- see the small systems below.
     */
    private fun installDeRolLeFight(boss: Enemy) {
        val loader = fieldEnemyLoader ?: return
        val prototype = loader.prototype(boss.slug) ?: return
        val mixer = boss.animationMixer ?: return
        boss.ai = null

        MainScope().launch {
            suspend fun clip(name: String): NjMotion? = try {
                loader.loadAnimation(boss.slug, "${name}_boss2_b_body.njm", prototype.njObject)
            } catch (e: Throwable) {
                console.warn("De Rol Le clip $name failed to load: ${e.message}")
                null
            }

            val forward = clip("forward") ?: return@launch
            deRolLeFight = DeRolLeFight(
                enemy = boss,
                njObject = prototype.njObject,
                mixer = mixer,
                clips = DeRolLeClips(
                    enter = clip("enter"),
                    forward = forward,
                    biteLeft = clip("l_bite"),
                    biteRight = clip("r_bite"),
                    jumpLeftToRight = clip("lrjump"),
                    jumpRightToLeft = clip("rljump"),
                    scatter = clip("scatter"),
                    beamCharge = clip("beamwait"),
                    beam = clip("beam_a"),
                    death = clip("die"),
                ),
                unitScale = worldUnit,
                deckY = 0.0,
                raftHalfX = DEROLLE_RAFT_HALF_X,
                raftHalfZ = DEROLLE_RAFT_HALF_Z,
                strikePlayer = ::dragonStrikesPlayer,
                strikePlayerFixed = ::dragonStrikesPlayerFixed,
                spawnMine = { x, z -> spawnDeckMine(x, z) },
                spawnOrb = { fx, fy, fz, dx, dz ->
                    spawnEnemyShotRaw(
                        fx, fy, fz, dx, 0.0, dz,
                        speedUnits = DEROLLE_ORB_SPEED_UNITS,
                        damage = DeRolLeFight.ORB_DAMAGE,
                        colorHex = 0xbb44ff,
                        sizeUnits = 1.4,
                    )
                },
                spawnRock = { x, z -> spawnDeckRock(x, z) },
                fireBeam = { z -> fireDeckBeam(z) },
            )
        }
    }

    /**
     * Vol Opt's two bodies handed to their controller, the room dressed with its monitors, and
     * every form-2 weapon wired through the host's small systems below.
     */
    private fun installVolOptFight(coreEnemy: Enemy, robotEnemy: Enemy) {
        val loader = fieldEnemyLoader ?: return
        val corePrototype = loader.prototype(coreEnemy.slug) ?: return
        val robotPrototype = loader.prototype(robotEnemy.slug) ?: return
        val coreMixer = coreEnemy.animationMixer ?: return
        val robotMixer = robotEnemy.animationMixer ?: return
        coreEnemy.ai = null
        robotEnemy.ai = null

        MainScope().launch {
            suspend fun coreClip(name: String): NjMotion? = try {
                loader.loadAnimation(coreEnemy.slug, "${name}_me5p01_y_all.njm", corePrototype.njObject)
            } catch (e: Throwable) { null }
            suspend fun robotClip(name: String): NjMotion? = try {
                loader.loadAnimation(robotEnemy.slug, "${name}_me5p02_y_all.njm", robotPrototype.njObject)
            } catch (e: Throwable) { null }

            val coreWait = coreClip("wait") ?: return@launch
            val robotWait = robotClip("wait") ?: return@launch

            // The room's furniture: a monitor bolted to each wall of the hexagon.
            val objectLoader = ObjectAssetLoader(assetLoader)
            var i = 0
            while (i < 6) {
                val angle = i * (2 * PI / 6)
                val monitor = objectLoader.loadObject("VolOptMonitorBlue")
                monitor.position.set(
                    sin(angle) * VOLOPT_MONITOR_RADIUS, 0.0, cos(angle) * VOLOPT_MONITOR_RADIUS,
                )
                monitor.rotation.y = angle + PI
                context.scene.add(monitor)
                i++
            }

            volOptFight = VolOptFight(
                core = coreEnemy,
                robot = robotEnemy,
                coreNjObject = corePrototype.njObject,
                robotNjObject = robotPrototype.njObject,
                coreMixer = coreMixer,
                robotMixer = robotMixer,
                clips = VolOptClips(
                    coreWait = coreWait,
                    coreAttack = coreClip("attack"),
                    robotStart = robotClip("start"),
                    robotWait = robotWait,
                    robotPunchFront = robotClip("f_attack"),
                    robotPunchLeft = robotClip("l_attack"),
                    robotPunchRight = robotClip("r_attack"),
                    robotAttackBack = robotClip("b_attack"),
                    robotDeath = robotClip("death"),
                ),
                unitScale = worldUnit,
                floorY = 0.0,
                monitorRadius = VOLOPT_MONITOR_RADIUS,
                monitorHeight = VOLOPT_MONITOR_HEIGHT,
                spawnPillar = { x, z, red ->
                    spawnEnemy?.invoke("VolOptPillar", x, 0.0, z, 0.0)?.also { pillar ->
                        if (red) {
                            // The caster wears a warning light; the soaks stand plain.
                            val light = Mesh(
                                SphereGeometry(1.2 * worldUnit, 8, 6),
                                MeshBasicMaterial(obj {
                                    color = Color(0xff3b30)
                                    blending = AdditiveBlending
                                    transparent = true
                                }).also { it.depthWrite = false },
                            )
                            light.position.y = (pillar.visualTop.takeIf { it > 0 } ?: 8.0) + 1.5
                            pillar.mesh.add(light)
                        }
                    }
                },
                castGizonde = { fx, fy, fz ->
                    player?.let { p ->
                        spawnLightningCrawl(
                            p.mesh.position.x, p.mesh.position.y, p.mesh.position.z,
                            count = 5, spreadWorld = 4.0 * worldUnit,
                        )
                        dragonStrikesPlayerFixed(VolOptFight.GIZONDE_DAMAGE, false)
                    }
                },
                fireMissile = { fx, fy, fz ->
                    val p = player
                    val dx = (p?.mesh?.position?.x ?: 0.0) - fx
                    val dz = (p?.mesh?.position?.z ?: 1.0) - fz
                    val length = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-3)
                    spawnEnemyShotRaw(
                        fx, fy, fz, dx / length, 0.0, dz / length,
                        speedUnits = VOLOPT_MISSILE_SPEED_UNITS,
                        damage = VolOptFight.MISSILE_DAMAGE,
                        colorHex = 0xff8833,
                        sizeUnits = 1.2,
                        homing = VOLOPT_MISSILE_HOMING,
                    )
                },
                stompAt = { x, z -> spawnDeckRock(x, z, damage = VolOptFight.STOMP_DAMAGE) },
                launchPrison = { launchVolPrison() },
                healRobot = { amount ->
                    robotEnemy.hp = (robotEnemy.hp + amount).coerceAtMost(robotEnemy.maxHp)
                    val glow = effectSprite("burst_bright", 14.0, colorHex = 0x66ff88)
                    glow.position.set(
                        robotEnemy.mesh.position.x,
                        robotEnemy.mesh.position.y + 10.0 * worldUnit,
                        robotEnemy.mesh.position.z,
                    )
                    addEffect(TimedEffect(glow, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 2.0))
                    showToast("Vol Opt repairs itself!")
                },
                strikePlayer = ::dragonStrikesPlayer,
            )
        }
    }

    /**
     * Dark Falz's two bodies handed to their controller. Divine Punishment and the elemental
     * volleys run through the host systems below and the shared enemy-shot pipeline.
     */
    private fun installDarkFalzFight(mountEnemy: Enemy, soulEnemy: Enemy) {
        val loader = fieldEnemyLoader ?: return
        val mountPrototype = loader.prototype(mountEnemy.slug) ?: return
        val soulPrototype = loader.prototype(soulEnemy.slug) ?: return
        val mountMixer = mountEnemy.animationMixer ?: return
        val soulMixer = soulEnemy.animationMixer ?: return
        mountEnemy.ai = null
        soulEnemy.ai = null

        MainScope().launch {
            suspend fun mountClip(name: String): NjMotion? = try {
                loader.loadAnimation(mountEnemy.slug, "${name}_df1_s_body.njm", mountPrototype.njObject)
            } catch (e: Throwable) { null }
            suspend fun soulClip(name: String): NjMotion? = try {
                loader.loadAnimation(soulEnemy.slug, "${name}_df2_s_body.njm", soulPrototype.njObject)
            } catch (e: Throwable) { null }

            val mountWait = mountClip("wait") ?: return@launch
            val soulWait = soulClip("wait") ?: return@launch

            darkFalzFight = DarkFalzFight(
                mount = mountEnemy,
                soul = soulEnemy,
                mountNjObject = mountPrototype.njObject,
                soulNjObject = soulPrototype.njObject,
                mountMixer = mountMixer,
                soulMixer = soulMixer,
                clips = DarkFalzClips(
                    mountWait = mountWait,
                    mountBeamLeft = mountClip("beaml"),
                    mountBeamRight = mountClip("beamr"),
                    mountSpawn = mountClip("hoe"),
                    mountCharge = mountClip("ltame"),
                    mountDeath = mountClip("dead1"),
                    soulWait = soulWait,
                    soulBeamLeft = soulClip("beaml"),
                    soulBeamRight = soulClip("beamr"),
                    soulSlam = soulClip("jisin"),
                    soulCharge = soulClip("ltame"),
                    soulDeath = soulClip("dead"),
                ),
                unitScale = worldUnit,
                floorY = 0.0,
                orbitRadius = FALZ_ORBIT_RADIUS,
                spawnDarvant = { x, z -> spawnEnemy?.invoke("Darvant", x, 0.0, z, 0.0) },
                fireVolley = { fx, fy, fz, ice ->
                    val p = player ?: return@DarkFalzFight
                    var i = 0
                    while (i < FALZ_VOLLEY_COUNT) {
                        val spread = (i - (FALZ_VOLLEY_COUNT - 1) / 2.0) * FALZ_VOLLEY_SPREAD
                        val dx = p.mesh.position.x - fx
                        val dz = p.mesh.position.z - fz
                        val base = atan2(dx, dz) + spread
                        spawnEnemyShotRaw(
                            fx, fy, fz, sin(base), 0.0, cos(base),
                            speedUnits = FALZ_VOLLEY_SPEED_UNITS,
                            damage = FALZ_VOLLEY_DAMAGE,
                            colorHex = if (ice) 0x77ccff else 0xff6633,
                            sizeUnits = 1.2,
                            freezeChance = if (ice) DarkFalzFight.ICE_FREEZE_CHANCE else 0.0,
                        )
                        i++
                    }
                },
                divineAt = { x, z, damage -> queueDivineStrike(x, z, damage) },
                slamAround = { x, z, radiusUnits, damage ->
                    val p = player ?: return@DarkFalzFight
                    val dx = p.mesh.position.x - x
                    val dz = p.mesh.position.z - z
                    val reach = radiusUnits * worldUnit
                    val burst = effectSprite("burst_orange", radiusUnits * 1.6, colorHex = 0xaa88ff)
                    burst.position.set(x, 2.0 * worldUnit, z)
                    addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 4.0))
                    if (dx * dx + dz * dz <= reach * reach) {
                        dragonStrikesPlayerFixed(damage, true)
                    }
                },
                drainPlayer = { boss, damage ->
                    val p = player ?: return@DarkFalzFight
                    if (p.hp > 0 && p.invulnerableRemaining <= 0) {
                        dragonStrikesPlayerFixed(damage, false)
                        boss.hp = (boss.hp + damage).coerceAtMost(boss.maxHp)
                        showToast("Your life is drained!")
                    }
                },
                strikePlayerFixed = ::dragonStrikesPlayerFixed,
            )
        }
    }

    /** One Divine Punishment: the marked circle, then the pillar of light that lands in it. */
    private class DivineStrike(val marker: Object3D, val x: Double, val z: Double, var remaining: Double, val damage: Int)

    private val divineStrikes = mutableListOf<DivineStrike>()

    private fun queueDivineStrike(x: Double, z: Double, damage: Int) {
        val marker = effectGroundQuad("nt_circle_gold", FALZ_DIVINE_RADIUS_UNITS * 2.2, FALZ_DIVINE_RADIUS_UNITS * 2.2, 0.0, 0xffd24d)
        marker.position.set(x, 0.4, z)
        context.scene.add(marker)
        divineStrikes.add(DivineStrike(marker, x, z, FALZ_DIVINE_DELAY_SECONDS, damage))
    }

    private fun updateDivineStrikes(p: Player, deltaTime: Double) {
        val iterator = divineStrikes.iterator()
        while (iterator.hasNext()) {
            val strike = iterator.next()
            strike.remaining -= deltaTime
            if (strike.remaining <= 0) {
                spawnLightPillar(strike.x, 0.0, strike.z, 0xffd24d)
                val dx = p.mesh.position.x - strike.x
                val dz = p.mesh.position.z - strike.z
                val reach = FALZ_DIVINE_RADIUS_UNITS * worldUnit
                if (dx * dx + dz * dz <= reach * reach && p.hp > 0) {
                    dragonStrikesPlayerFixed(strike.damage, true)
                }
                strike.marker.parent?.remove(strike.marker)
                iterator.remove()
            }
        }
    }

    /** The prison ball in flight, and the cage it drops on whoever it touches. */
    private var volPrisonBall: Mesh? = null
    private var volCage: Object3D? = null
    private var volCageRemaining = 0.0

    private fun launchVolPrison() {
        if (volPrisonBall != null || volCage != null) return
        val boss = volOptFight?.robot ?: return
        val mesh = Mesh(
            SphereGeometry(1.6 * worldUnit, 10, 8),
            MeshBasicMaterial(obj {
                color = Color(0xffdd55)
                blending = AdditiveBlending
                transparent = true
            }).also { it.depthWrite = false },
        )
        mesh.position.set(
            boss.mesh.position.x,
            boss.mesh.position.y + 12.0 * worldUnit,
            boss.mesh.position.z,
        )
        context.scene.add(mesh)
        volPrisonBall = mesh
    }

    private fun updateVolPrison(p: Player, deltaTime: Double) {
        volPrisonBall?.let { ball ->
            // Slow homing flight straight at the player's chest.
            val tx = p.mesh.position.x - ball.position.x
            val ty = (p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit) - ball.position.y
            val tz = p.mesh.position.z - ball.position.z
            val length = sqrt(tx * tx + ty * ty + tz * tz)
            if (length < VOLOPT_PRISON_CATCH_UNITS * worldUnit) {
                // Caught: the cage drops over the player and holds them for its charge.
                ball.parent?.remove(ball)
                volPrisonBall = null
                MainScope().launch {
                    val cage = ObjectAssetLoader(assetLoader).loadObject("VolOptCage")
                    cage.position.set(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z)
                    context.scene.add(cage)
                    volCage = cage
                    volCageRemaining = VOLOPT_CAGE_SECONDS
                    p.paralysisRemaining = VOLOPT_CAGE_SECONDS
                    showToast("Caged!")
                }
            } else {
                val step = VOLOPT_PRISON_SPEED_UNITS * worldUnit * deltaTime
                ball.position.x += tx / length * step
                ball.position.y += ty / length * step
                ball.position.z += tz / length * step
            }
        }

        volCage?.let { cage ->
            volCageRemaining -= deltaTime
            if (volCageRemaining <= 0) {
                // The charge lands on whoever is still inside.
                val dx = p.mesh.position.x - cage.position.x
                val dz = p.mesh.position.z - cage.position.z
                val reach = VOLOPT_CAGE_RADIUS_UNITS * worldUnit
                if (dx * dx + dz * dz <= reach * reach) {
                    dragonStrikesPlayerFixed(VolOptFight.PRISON_DAMAGE, false)
                }
                cage.parent?.remove(cage)
                volCage = null
            }
        }
    }

    /** A blinking spike mine on the deck; it detonates on its own fuse. */
    private class DeckMine(val mesh: Mesh, val x: Double, val z: Double, var fuse: Double)

    /** A rock shaken from the tunnel roof, falling toward the deck. */
    private class DeckRock(val mesh: Mesh, val x: Double, val z: Double, val damage: Int)

    /** One beam band: brief telegraph, then the damage lands across it. */
    private class DeckBeam(val mesh: Mesh, val z: Double, var remaining: Double, var fired: Boolean)

    private val deckMines = mutableListOf<DeckMine>()
    private val deckRocks = mutableListOf<DeckRock>()
    private val deckBeams = mutableListOf<DeckBeam>()

    private fun spawnDeckMine(x: Double, z: Double) {
        val mesh = Mesh(
            SphereGeometry(DEROLLE_MINE_RADIUS_UNITS * worldUnit, 10, 8),
            MeshBasicMaterial(obj {
                color = Color(0xff5533)
                transparent = true
            }).also { it.depthWrite = false },
        )
        mesh.scale.y = 0.6
        mesh.position.set(x, DEROLLE_MINE_RADIUS_UNITS * worldUnit * 0.5, z)
        context.scene.add(mesh)
        deckMines.add(DeckMine(mesh, x, z, DEROLLE_MINE_FUSE_SECONDS))
    }

    private fun spawnDeckRock(x: Double, z: Double, damage: Int = DeRolLeFight.MINE_DAMAGE) {
        val mesh = Mesh(
            SphereGeometry(DEROLLE_ROCK_RADIUS_UNITS * worldUnit, 8, 6),
            MeshBasicMaterial(obj { color = Color(0x7a6a55) }),
        )
        mesh.scale.set(1.0, 0.8, 0.9)
        mesh.position.set(x, DEROLLE_ROCK_DROP_HEIGHT * worldUnit, z)
        context.scene.add(mesh)
        deckRocks.add(DeckRock(mesh, x, z, damage))
    }

    private fun fireDeckBeam(z: Double) {
        val geometry = CylinderGeometry(
            DEROLLE_BEAM_HALF_WIDTH * worldUnit, DEROLLE_BEAM_HALF_WIDTH * worldUnit,
            DEROLLE_RAFT_HALF_X * 2.4, 8,
        )
        val mesh = Mesh(
            geometry,
            MeshBasicMaterial(obj {
                color = Color(0xff3355)
                blending = AdditiveBlending
                transparent = true
            }).also { it.depthWrite = false },
        )
        // Lying across the deck, spanning its width at the band's z.
        mesh.rotation.z = PI / 2
        mesh.position.set(0.0, 2.5 * worldUnit, z)
        val material: dynamic = mesh.material
        material.opacity = 0.35
        context.scene.add(mesh)
        deckBeams.add(DeckBeam(mesh, z, DEROLLE_BEAM_TELEGRAPH_SECONDS, fired = false))
    }

    /** Runs the raft's live hazards: fuses, falling rocks, and the beam's telegraph-then-burn. */
    private fun updateBossDeckHazards(p: Player, deltaTime: Double) {
        if (deckMines.isEmpty() && deckRocks.isEmpty() && deckBeams.isEmpty()) return

        val mines = deckMines.iterator()
        while (mines.hasNext()) {
            val mine = mines.next()
            mine.fuse -= deltaTime
            val material: dynamic = mine.mesh.material
            material.opacity = if ((mine.fuse * 8).toInt() % 2 == 0) 1.0 else 0.5
            if (mine.fuse <= 0) {
                val burst = effectSprite("burst_orange", DEROLLE_MINE_BLAST_UNITS * 1.8)
                burst.position.set(mine.x, 2.0 * worldUnit, mine.z)
                addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 4.0))
                val dx = p.mesh.position.x - mine.x
                val dz = p.mesh.position.z - mine.z
                val blast = DEROLLE_MINE_BLAST_UNITS * worldUnit
                if (dx * dx + dz * dz <= blast * blast) {
                    dragonStrikesPlayerFixed(DeRolLeFight.MINE_DAMAGE, false)
                }
                mine.mesh.parent?.remove(mine.mesh)
                mines.remove()
            }
        }

        val rocks = deckRocks.iterator()
        while (rocks.hasNext()) {
            val rock = rocks.next()
            rock.mesh.position.y -= DEROLLE_ROCK_FALL_UNITS_PER_SECOND * worldUnit * deltaTime
            if (rock.mesh.position.y <= DEROLLE_ROCK_RADIUS_UNITS * worldUnit * 0.6) {
                val burst = effectSprite("burst_bright", DEROLLE_ROCK_BLAST_UNITS * 1.5, colorHex = 0xc9b18a)
                burst.position.set(rock.x, 1.5 * worldUnit, rock.z)
                addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 3.0))
                val dx = p.mesh.position.x - rock.x
                val dz = p.mesh.position.z - rock.z
                val blast = DEROLLE_ROCK_BLAST_UNITS * worldUnit
                if (dx * dx + dz * dz <= blast * blast) {
                    dragonStrikesPlayerFixed(rock.damage, false)
                }
                rock.mesh.parent?.remove(rock.mesh)
                rocks.remove()
            }
        }

        val beams = deckBeams.iterator()
        while (beams.hasNext()) {
            val beam = beams.next()
            beam.remaining -= deltaTime
            val material: dynamic = beam.mesh.material
            if (!beam.fired) {
                // Telegraph brightening toward the burn.
                material.opacity = 0.35 + (1.0 - beam.remaining / DEROLLE_BEAM_TELEGRAPH_SECONDS) * 0.4
                if (beam.remaining <= 0) {
                    beam.fired = true
                    beam.remaining = DEROLLE_BEAM_BURN_SECONDS
                    material.opacity = 1.0
                    if (abs(p.mesh.position.z - beam.z) <= DEROLLE_BEAM_HALF_WIDTH * worldUnit) {
                        dragonStrikesPlayerFixed(DeRolLeFight.BEAM_DAMAGE, true)
                    }
                }
            } else if (beam.remaining <= 0) {
                beam.mesh.parent?.remove(beam.mesh)
                beams.remove()
            }
        }
    }

    private fun handlePlayerDowned(p: Player) {
        val dolls = p.tools[ToolType.SCAPE_DOLL] ?: 0
        if (dolls > 0) {
            if (dolls <= 1) p.tools.remove(ToolType.SCAPE_DOLL)
            else p.tools[ToolType.SCAPE_DOLL] = dolls - 1
            p.hp = p.maxHp
            p.invulnerableRemaining = INVULNERABILITY_DURATION
            playerStatusPanel.setHealth(p.hp, p.maxHp)
            showToast("The Scape Doll shattered in your place!")
            persistProgress()
        } else {
            p.respawnRemaining = RESPAWN_DELAY
        }
    }

    /** Watches the engaged boss and opens the warp where it fell. */
    private fun updateBossProgress() {
        if (!bossEngaged || bossDefeated || bossEnemies.isEmpty()) return
        if (bossEnemies.any { !it.isDead }) return

        bossDefeated = true
        val fallen = bossEnemies.first()
        bossEncounter?.let { returnWarpDestination = it.destinationMap }
        openReturnWarp(fallen.mesh.position.x, fallen.mesh.position.z)
        bossEncounter?.let { encounter ->
            showToast(encounter.clearedMessage)
            encounter.bossKey?.let { key ->
                player?.defeatedBosses?.add(key)
                persistProgress()
            }
        }
    }

    /** Raises the city's own warp beam where the boss fell, standing on the ground beneath it. */
    private fun openReturnWarp(x: Double, z: Double) {
        MainScope().launch {
            val beam = ObjectAssetLoader(assetLoader).loadAnimatedObject("CityBeamBig")
            // The search returns the nearest walkable *point*, so the warp snaps onto real
            // ground rather than hanging wherever the boss happened to die.
            val grounded = walkableCollision?.let { findNearestGroundHeight(it, x, z) }
            val warpX = grounded?.first ?: x
            val warpY = grounded?.second ?: player?.mesh?.position?.y ?: .0
            val warpZ = grounded?.third ?: z
            beam.mesh.position.set(warpX, warpY, warpZ)
            beam.mesh.asDynamic().scale.set(
                RETURN_WARP_SCALE, RETURN_WARP_SCALE, RETURN_WARP_SCALE,
            )
            // Same transparency treatment the city's beams need, or the ground vanishes around it.
            forEachMaterial(beam.mesh) { material ->
                material.transparent = true
                material.depthWrite = false
                material.asDynamic().blending = AdditiveBlending
            }
            context.scene.add(beam.mesh)

            val mixer = AnimationMixer(beam.mesh)
            mixer.clipAction(createAnimationClip(beam.njObject, beam.motion)).play()
            npcMixers.add(mixer)

            returnWarp = beam.mesh
        }
    }

    /** Stepping into the open warp ends the run and rebuilds the area fresh on the way back. */
    /** Where the open warp leads. Ryuker and the boss warp share the same pad home. */
    private var returnWarpDestination: String = "pioneer2"

    private fun updateReturnWarp(p: Player) {
        val warp = returnWarp ?: return
        val dx = p.mesh.position.x - warp.position.x
        val dz = p.mesh.position.z - warp.position.z
        val radius = RETURN_WARP_RADIUS_UNITS * worldUnit
        if (dx * dx + dz * dz > radius * radius) {
            returnWarpArmed = true
            return
        }
        if (!returnWarpArmed) return

        if (!areaTransitionStarted) {
            areaTransitionStarted = true
            onAreaTransition?.invoke(returnWarpDestination)
        }
    }

    // --- Town NPCs: talk, shops, the bank ---

    private class ActiveNpc(val spec: Pioneer2Npc, val mesh: Object3D)

    /** A tappable field prop -- Rico's message pods. Opens a read-only dialog. */
    private class FieldInteractable(
        val mesh: Object3D,
        val prompt: String,
        val title: String,
        val text: String,
    )

    private val activeNpcs = mutableListOf<ActiveNpc>()

    // --- The quest system's renderer half ---

    private var questIndex: List<QuestIndexEntry> = emptyList()
    private var questDef: QuestDef? = null
    private var questVm: QuestVm? = null

    /** Quest-cast NPCs: mesh identity to the script label their talk runs. */
    private val questNpcLabels = HashMap<Object3D, Int>()

    /** The classic bottom message window the quest scripts speak through. */
    private val questMessageBox = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-quest-msg"
        el.style.cssText = "position:fixed;left:50%;bottom:calc(96px + var(--pw-safe-bottom));" +
            "transform:translateX(-50%);width:min(620px,86vw);display:none;z-index:60;" +
            "background:rgba(6,22,34,.92);border:1px solid rgba(140,220,220,.55);" +
            "border-radius:8px;padding:14px 16px;color:#e8f6f6;font-size:15px;" +
            "line-height:1.5;white-space:pre-wrap;font-family:inherit;" +
            "box-shadow:0 4px 18px rgba(0,0,0,.5);-webkit-tap-highlight-color:transparent;"
        document.body!!.appendChild(el)
    }

    private val questChoiceBox = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText = "position:fixed;left:50%;bottom:calc(200px + var(--pw-safe-bottom));" +
            "transform:translateX(-50%);display:none;z-index:61;min-width:200px;" +
            "background:rgba(6,22,34,.95);border:1px solid rgba(140,220,220,.55);" +
            "border-radius:8px;padding:8px;color:#e8f6f6;font-size:15px;"
        document.body!!.appendChild(el)
    }

    init {
        questMessageBox.addEventListener("pointerdown", {
            it.preventDefault()
            questVm?.advanceUi()
        })
    }
    private val fieldInteractables = mutableListOf<FieldInteractable>()
    private var currentInteractable: FieldInteractable? = null
    private var currentTalkNpc: ActiveNpc? = null
    private val talkProjection = Vector3()

    /** True while the open dialog belongs to a teleporter pad, not an NPC or a pod. */
    private var teleporterDialogOpen = false

    private val npcDialog = addDisposable(
        NpcDialog(document.body!!, onClose = { teleporterDialogOpen = false })
    )

    private val talkBubble = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-talk"
        el.textContent = "TALK"
        el.style.cssText = "position:fixed;transform:translate(-50%,-100%);padding:5px 16px;" +
            "border-radius:14px;border:2px solid rgba(90,210,255,.85);background:rgba(6,26,44,.92);" +
            "color:#e8f6ff;font:bold 12px sans-serif;letter-spacing:2px;" +
            "box-shadow:0 0 8px rgba(60,160,255,.5);z-index:45;display:none;cursor:pointer;" +
            "touch-action:none;user-select:none;"
        document.body!!.appendChild(el)
        el.addEventListener("pointerdown", { e ->
            e.stopPropagation()
            currentTalkNpc?.let { openNpcDialog(it) }
            currentInteractable?.let { pod ->
                npcDialog.open(NpcDialogState(pod.title, pod.text))
            }
        })
    }

    /**
     * Keeps the TALK prompt over the nearest NPC in speaking range, and closes an open window
     * when its owner walks away -- talking is a place you stand, not a menu you carry.
     */
    private fun updateTalkPrompt() {
        val p = player
        if (p == null || (activeNpcs.isEmpty() && fieldInteractables.isEmpty()) || gameMenu.isOpen) {
            talkBubble.style.display = "none"
            if (activeNpcs.isNotEmpty() || fieldInteractables.isNotEmpty()) npcDialog.close()
            currentTalkNpc = null
            currentInteractable = null
            return
        }

        val range = TALK_RANGE_UNITS * worldUnit
        var nearest: ActiveNpc? = null
        var nearestD2 = range * range
        for (npc in activeNpcs) {
            val dx = npc.mesh.position.x - p.mesh.position.x
            val dz = npc.mesh.position.z - p.mesh.position.z
            val d2 = dx * dx + dz * dz
            if (d2 < nearestD2) {
                nearest = npc
                nearestD2 = d2
            }
        }
        currentTalkNpc = nearest

        // A pod only wins when no NPC is in range -- the field has no NPCs today anyway.
        var nearestPod: FieldInteractable? = null
        if (nearest == null) {
            var podD2 = range * range
            for (pod in fieldInteractables) {
                val dx = pod.mesh.position.x - p.mesh.position.x
                val dz = pod.mesh.position.z - p.mesh.position.z
                val d2 = dx * dx + dz * dz
                if (d2 < podD2) {
                    nearestPod = pod
                    podD2 = d2
                }
            }
        }
        currentInteractable = nearestPod

        if (npcDialog.isOpen) {
            talkBubble.style.display = "none"
            // Walking away closes an NPC's or pod's window -- but a teleporter's dialog belongs
            // to the pad underfoot, not to anyone in talk range, and updateTeleporters owns its
            // lifetime. (This close used to fire the same frame the teleporter menu opened,
            // which read as the pad doing nothing at all.)
            if (nearest == null && nearestPod == null && !teleporterDialogOpen) npcDialog.close()
            return
        }

        nearestPod?.let { pod ->
            talkBubble.textContent = pod.prompt
            val podHeight = meshHeightWorld(pod.mesh) ?: (3.0 * worldUnit)
            talkProjection.set(
                pod.mesh.position.x,
                pod.mesh.position.y + podHeight * TALK_PROMPT_HEIGHT_FRACTION,
                pod.mesh.position.z,
            )
            talkProjection.project(context.camera)
            if (talkProjection.z > 1.0) {
                talkBubble.style.display = "none"
                return
            }
            talkBubble.style.display = "block"
            talkBubble.style.left = "${(talkProjection.x + 1) / 2 * context.canvas.clientWidth}px"
            talkBubble.style.top = "${(1 - talkProjection.y) / 2 * context.canvas.clientHeight}px"
            return
        }

        if (nearest == null) {
            talkBubble.style.display = "none"
            return
        }
        talkBubble.textContent = "TALK"

        // Above this NPC's own head rather than a fixed number of units up: the citizens vary
        // a lot in build, and a unit-based height floated over the short ones.
        val npcHeight = meshHeightWorld(nearest.mesh)
        val promptY = npcHeight?.let { it * TALK_PROMPT_HEIGHT_FRACTION }
            ?: (TALK_PROMPT_HEIGHT_UNITS * worldUnit)

        talkProjection.set(
            nearest.mesh.position.x,
            nearest.mesh.position.y + promptY,
            nearest.mesh.position.z,
        )
        talkProjection.project(context.camera)
        if (talkProjection.z > 1.0) {
            talkBubble.style.display = "none"
            return
        }
        talkBubble.style.display = "block"
        talkBubble.style.left = "${(talkProjection.x + 1) / 2 * context.canvas.clientWidth}px"
        talkBubble.style.top = "${(1 - talkProjection.y) / 2 * context.canvas.clientHeight}px"
    }

    /**
     * The Main Ragol Teleporter's destination menu: the zone entrances, in the real game's
     * order, gated the real game's way -- each zone opens when the previous zone's boss falls.
     * Only Forest is walkable today; the locked rows say exactly what will open them.
     */
    private fun openRagolDestinationMenu() {
        teleporterDialogOpen = true
        val dragonDown = player?.defeatedBosses?.contains("dragon") == true
        val rows = mutableListOf(
            DialogRow("GO", "Forest 1") {
                npcDialog.close()
                transitionTo("forest01")
            },
            if (dragonDown) DialogRow("GO", "Cave 1") {
                npcDialog.close()
                transitionTo("cave01")
            } else DialogRow("", "Cave 1", "defeat the Dragon"),
            // De Rol Le and Vol Opt aren't built yet, so the underground zones open together
            // once the Dragon falls -- the gate moves onto the real bosses when they exist.
            if (dragonDown) DialogRow("GO", "Mine 1") {
                npcDialog.close()
                transitionTo("mines01")
            } else DialogRow("", "Mine 1", "defeat the Dragon"),
            if (dragonDown) DialogRow("GO", "Ruins 1") {
                npcDialog.close()
                transitionTo("ruins01")
            } else DialogRow("", "Ruins 1", "defeat the Dragon"),
        )
        npcDialog.open(
            NpcDialogState(
                npcName = "Ragol Teleporter",
                text = "Transport to the surface. Select a destination.",
                rows = rows,
            )
        )
    }

    /** Opens the NPC's window: their line, then whatever they deal in as rows. */
    /** The character's name as the quest scripts address them. */
    private fun characterDisplayName(): String =
        save?.name ?: "hunter"

    /** The world side of the quest VM: message windows, doors, meseta, the player's body. */
    private inner class RendererQuestHost : QuestHost {
        override fun showMessagePage(npcId: Int, text: String) {
            // The scripts' markup: <color N> spans and player-name tokens.
            val clean = text
                .replace(Regex("<color [0-9]+>"), "")
                .replace("<name hero>", characterDisplayName())
                .replace("<name job>", professionOf(appearance.characterClass).name
                    .lowercase().replaceFirstChar { it.uppercase() })
            questMessageBox.textContent = clean + "\n\u25BC"
            questMessageBox.style.display = "block"
        }

        override fun closeMessage() {
            questMessageBox.style.display = "none"
        }

        override fun showWindow(text: String) = showMessagePage(-1, text)

        override fun closeWindow() = closeMessage()

        override fun showChoice(options: List<String>) {
            questChoiceBox.innerHTML = ""
            for ((index, option) in options.withIndex()) {
                (document.createElement("div") as HTMLElement).also { row ->
                    row.textContent = option
                    row.style.cssText = "padding:8px 18px;cursor:pointer;border-radius:6px;"
                    row.addEventListener("pointerdown", {
                        it.preventDefault()
                        questChoiceBox.style.display = "none"
                        questVm?.chooseUi(index)
                    })
                    questChoiceBox.appendChild(row)
                }
            }
            questChoiceBox.style.display = "block"
        }

        override fun addMeseta(amount: Int) {
            player?.let { it.meseta += amount; persistProgress() }
        }

        override fun unlockDoor(doorId: Int) {
            fieldGates?.doors?.find { it.doorId == doorId }?.open()
        }

        override fun lockDoor(doorId: Int) = Unit

        override fun setPlayerPosition(x: Double, y: Double, z: Double, yaw: Double) {
            player?.let { p ->
                p.controller.position.set(x, y, z)
                p.mesh.position.set(x, y, z)
                p.mesh.rotation.y = yaw * PI / 180.0
            }
        }

        override fun isSwitchPressed(switchId: Int): Boolean = false

        override fun characterClassId(): Int = appearance.characterClass.ordinal

        override fun giveItem(code0: Int, code1: Int, code2: Int) {
            showToast("The client hands you a reward")
        }

        override fun goFloor(floor: Int) {
            val destination = QUEST_FLOOR_FOR_MAP.entries.find { it.value == floor }?.key
            if (destination != null && destination != mapSlug) {
                QuestSession.persist()
                onAreaTransition?.invoke(destination)
            }
        }

        override fun returnToGuild() {
            QuestSession.persist()
            if (mapSlug != "pioneer2") onAreaTransition?.invoke("pioneer2")
        }

        override fun questExit() {
            QuestSession.complete()
            showToast("Quest complete!")
            persistProgress()
        }

        override fun anyBossDead(): Boolean =
            player?.defeatedBosses?.isNotEmpty() == true

        override fun playerHp(): Int = player?.hp ?: 0
    }

    /**
     * Enters quest mode for this map: the quest's own cast stands in the world, the script's
     * label 0 runs (handlers, designations), and the floor's handler fires.
     */
    private suspend fun setupQuestMode() {
        if (!QuestSession.active) return
        val def = questDef ?: return
        val vm = QuestVm(def, RendererQuestHost())
        questVm = vm

        val floor = QUEST_FLOOR_FOR_MAP[mapSlug] ?: return

        // The quest's story cast for Pioneer 2 -- only the figures the base hub doesn't
        // already stand (the Principal and his secretary). Spawning the whole quest roster
        // would double every citizen the city already has.
        if (floor == 0) {
            val npcLoader = NpcAssetLoader(assetLoader)
            for (npc in def.npcs.filter { it.area == 0 && it.type in QUEST_STORY_NPCS }) {
                val model = QUEST_NPC_MODELS[npc.type] ?: continue
                try {
                    val meshData = npcLoader.loadNpc(model)
                    val mesh = meshData.mesh
                    mesh.position.set(npc.x, npc.y, npc.z)
                    mesh.rotation.y = npc.yaw
                    context.scene.add(mesh)
                    questNpcLabels[mesh] = npc.script
                    activeNpcs.add(
                        ActiveNpc(
                            Pioneer2Npc(
                                npc.type, model, npc.x, npc.y, npc.z,
                                npc.yaw * 180.0 / PI,
                                idleAnimation = 0,
                                displayName = QUEST_NPC_NAMES[npc.type] ?: npc.type,
                                role = NpcRole.CHAT,
                            ),
                            mesh,
                        )
                    )
                } catch (e: Throwable) {
                    console.warn("Quest NPC ${npc.type} ($model) failed to load")
                }
            }
        }

        vm.currentFloor = floor

        // Label 0 registers the floor handlers and reads the flags; then this floor's handler.
        vm.startThread(0)
        QuestSession.floorHandlers[floor]?.let { vm.startThread(it) }
    }

    private fun openNpcDialog(active: ActiveNpc, page: String? = null) {
        val p = player ?: return
        val spec = active.spec
        val rows = mutableListOf<DialogRow>()

        // A quest-cast NPC speaks through the script, not the dialog rows.
        questNpcLabels[active.mesh]?.let { label ->
            questVm?.startThread(label)
            return
        }

        when (spec.role) {
            NpcRole.CHAT -> Unit

            NpcRole.GUILD -> {
                val activeSlug = QuestSession.slug
                if (activeSlug != null) {
                    val questName = questIndex.find { it.slug == activeSlug }?.name ?: activeSlug
                    rows.add(DialogRow("", "In progress: $questName"))
                    // The quest board: whatever the script has posted (collected key items,
                    // reports) -- each entry runs its own script label.
                    for ((slot, entry) in QuestSession.boardHandlers.entries.sortedBy { it.key }) {
                        val (label, name) = entry
                        rows.add(DialogRow("BOARD", name.ifEmpty { "Entry ${slot + 1}" }) {
                            questVm?.startThread(label)
                            npcDialog.close()
                        })
                    }
                    rows.add(DialogRow("TURN IN", "Report the job done") {
                        val vm = questVm
                        if (vm != null && vm.qtSuccessLabel >= 0) {
                            vm.startThread(vm.qtSuccessLabel)
                            // The Normal-difficulty award label, straight from the script.
                            vm.startThread(21)
                        }
                        QuestSession.complete()
                        npcDialog.close()
                        showToast("Quest complete!")
                        persistProgress()
                    })
                    rows.add(DialogRow("CANCEL", "Abandon the job") {
                        QuestSession.abandon()
                        npcDialog.close()
                        onAreaTransition?.invoke("pioneer2")
                    })
                } else {
                    rows.add(DialogRow("", "-- GOVERNMENT JOBS --"))
                    for ((index, entry) in questIndex.withIndex()) {
                        val done = entry.slug in QuestSession.completed
                        val unlocked = index == 0 ||
                            questIndex[index - 1].slug in QuestSession.completed
                        when {
                            done -> rows.add(DialogRow("", "${entry.name}  \u2713"))
                            !unlocked -> rows.add(DialogRow("", "${entry.name}  (locked)"))
                            else -> rows.add(
                                DialogRow("ACCEPT", entry.name, entry.short.replace("\n", " ")) {
                                    QuestSession.begin(entry.slug)
                                    npcDialog.close()
                                    onAreaTransition?.invoke("pioneer2")
                                }
                            )
                        }
                    }
                }
            }

            NpcRole.TOOL_SHOP -> when (page) {
                null -> {
                    rows.add(DialogRow("GO", "Buy", "Browse the counter's stock") {
                        openNpcDialog(active, "buy")
                    })
                    rows.add(DialogRow("GO", "Sell", "Sell from your pack") {
                        openNpcDialog(active, "sell")
                    })
                }
                "buy" -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    for ((tool, fullPrice) in TOOL_SHOP) {
                        val price = shopPrice(fullPrice)
                        rows.add(DialogRow("BUY", tool.uiName, "$price Meseta", icon = tool.itemIcon) {
                            if (buyTool(tool, price)) openNpcDialog(active, "buy")
                        })
                    }
                }
                else -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    val sellable = p.tools.entries.mapNotNull { (tool, count) ->
                        toolSellPrice(tool)?.let { Triple(tool, count, it) }
                    }
                    if (sellable.isEmpty() && p.treasures.isEmpty()) {
                        rows.add(DialogRow("", "Nothing in the pack I'd buy."))
                    }
                    for ((tool, count, price) in sellable) {
                        rows.add(DialogRow("SELL", "${tool.uiName}  x$count", "$price Meseta each", icon = tool.itemIcon) {
                            if (sellTool(tool, price)) openNpcDialog(active, "sell")
                        })
                    }
                    for (treasureItem in p.treasures.toList()) {
                        rows.add(DialogRow("SELL", treasureItem.uiName, "$TREASURE_SELL_PRICE Meseta", icon = treasureItem.itemIcon) {
                            if (sellTreasure(treasureItem)) openNpcDialog(active, "sell")
                        })
                    }
                }
            }

            // The orange counter: weapons only (the orange item box's contents).
            NpcRole.WEAPON_SHOP -> when (page) {
                null -> {
                    rows.add(DialogRow("GO", "Buy", "Browse the weapon racks") {
                        openNpcDialog(active, "buy")
                    })
                    rows.add(DialogRow("GO", "Sell", "Sell weapons from your pack") {
                        openNpcDialog(active, "sell")
                    })
                }
                "buy" -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    for (tier in armsShopStock(p.level)) {
                        val price = weaponBuyPrice(tier)
                        rows.add(DialogRow("BUY", tier.name, "${tier.atpMin}-${tier.atpMax} ATP  ·  $price Meseta", icon = tier.type.itemIcon) {
                            if (buyWeapon(tier, price)) openNpcDialog(active, "buy")
                        })
                    }
                }
                else -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    // A ???? can't be sold until it's appraised -- no price on a mystery.
                    val sellableWeapons = inventory.filter { !it.unidentified }
                    if (sellableWeapons.isEmpty()) {
                        rows.add(DialogRow("", "No weapons in the pack I'd buy."))
                    }
                    for (item in sellableWeapons.toList()) {
                        rows.add(DialogRow("SELL", item.displayName, "${weaponSellPrice(item)} Meseta", icon = item.itemIcon) {
                            if (sellWeapon(item)) openNpcDialog(active, "sell")
                        })
                    }
                }
            }

            // The blue counter: frames, barriers and units (the blue item box).
            NpcRole.ARMOR_SHOP -> when (page) {
                null -> {
                    rows.add(DialogRow("GO", "Buy", "Browse frames, barriers and units") {
                        openNpcDialog(active, "buy")
                    })
                    rows.add(DialogRow("GO", "Sell", "Sell gear from your pack") {
                        openNpcDialog(active, "sell")
                    })
                }
                "buy" -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    rows.add(DialogRow("", "-- FRAMES --"))
                    for (spec in armorShopFrames(p.level)) {
                        rows.add(DialogRow("BUY", spec.name, "frame · DFP ${spec.dfpMin}-${spec.dfpMax} · ${shopPrice(spec.price)} Meseta", icon = ItemIcon.ARMOR) {
                            if (buyFrame(spec)) openNpcDialog(active, "buy")
                        })
                    }
                    rows.add(DialogRow("", "-- BARRIERS --"))
                    for (spec in armorShopBarriers(p.level)) {
                        rows.add(DialogRow("BUY", spec.name, "barrier · EVP ${spec.evpMin}-${spec.evpMax} · ${shopPrice(spec.price)} Meseta", icon = ItemIcon.UNIT) {
                            if (buyBarrier(spec)) openNpcDialog(active, "buy")
                        })
                    }
                    rows.add(DialogRow("", "-- UNITS --"))
                    for (unit in UNIT_SHOP) {
                        rows.add(DialogRow("BUY", unit.uiName, "${unit.detail} · ${shopPrice(unit.price)} Meseta", icon = unit.itemIcon) {
                            if (buyUnit(unit)) openNpcDialog(active, "buy")
                        })
                    }
                }
                else -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    val hasArmor = p.ownedFrames.isNotEmpty() || p.ownedBarriers.isNotEmpty() ||
                        p.ownedUnits.isNotEmpty()
                    if (!hasArmor) {
                        rows.add(DialogRow("", "No gear in the pack I'd buy."))
                    }
                    for (item in p.ownedFrames.toList()) {
                        rows.add(DialogRow("SELL", item.displayName, "${frameSellPrice(item)} Meseta", icon = item.itemIcon) {
                            if (sellFrame(item)) openNpcDialog(active, "sell")
                        })
                    }
                    for (item in p.ownedBarriers.toList()) {
                        rows.add(DialogRow("SELL", item.displayName, "${barrierSellPrice(item)} Meseta", icon = item.itemIcon) {
                            if (sellBarrier(item)) openNpcDialog(active, "sell")
                        })
                    }
                    for (unit in p.ownedUnits.toList()) {
                        rows.add(DialogRow("SELL", unit.uiName, "${unitSellPrice(unit)} Meseta", icon = unit.itemIcon) {
                            if (sellUnit(unit)) openNpcDialog(active, "sell")
                        })
                    }
                }
            }

            NpcRole.TEKKER -> {
                val unidentifiedItems = inventory.filter { it.unidentified }
                if (unidentifiedItems.isEmpty()) {
                    rows.add(DialogRow("", "Bring me anything marked ????, and I'll tell you what it truly is."))
                } else {
                    rows.add(DialogRow("", "-- APPRAISE ($TEKKER_FEE Meseta each) --"))
                    for (item in unidentifiedItems) {
                        rows.add(DialogRow("TEKK", "????", "an unappraised rare", icon = item.itemIcon) {
                            tekkWeapon(item)
                            openNpcDialog(active)
                        })
                    }
                }
            }

            NpcRole.BANK -> when (page) {
                null -> {
                    rows.add(DialogRow("", "carrying ${p.meseta}  ·  banked ${p.bankMeseta}"))
                    rows.add(DialogRow("GO", "Deposit", "Store Meseta and items") {
                        openNpcDialog(active, "deposit")
                    })
                    rows.add(DialogRow("GO", "Withdraw", "Take Meseta and items back") {
                        openNpcDialog(active, "withdraw")
                    })
                }

                "deposit" -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    rows.add(DialogRow("", "carrying ${p.meseta} Meseta"))
                    rows.add(DialogRow("MESETA", "Deposit 100", icon = ItemIcon.MESETA) { if (bankMesetaDeposit(100)) openNpcDialog(active, "deposit") })
                    rows.add(DialogRow("MESETA", "Deposit all", icon = ItemIcon.MESETA) { if (bankMesetaDeposit(p.meseta)) openNpcDialog(active, "deposit") })
                    for ((tool, count) in p.tools.entries.toList()) {
                        rows.add(DialogRow("IN", "${tool.uiName}  x$count", "Tap to store one", icon = tool.itemIcon) {
                            if (bankDepositTool(tool)) openNpcDialog(active, "deposit")
                        })
                    }
                    for (item in inventory.toList()) {
                        rows.add(DialogRow("IN", item.displayName, "Tap to store", icon = item.itemIcon) {
                            if (bankDepositWeapon(item)) openNpcDialog(active, "deposit")
                        })
                    }
                    for (treasureItem in p.treasures.toList()) {
                        rows.add(DialogRow("IN", treasureItem.uiName, "Tap to store", icon = treasureItem.itemIcon) {
                            if (bankDepositTreasure(treasureItem)) openNpcDialog(active, "deposit")
                        })
                    }
                    for (item in p.ownedFrames.toList()) {
                        rows.add(DialogRow("IN", item.displayName, "Tap to store", icon = item.itemIcon) {
                            if (moveItem { p.ownedFrames.remove(item) && p.bankFrames.add(item) }) openNpcDialog(active, "deposit")
                        })
                    }
                    for (item in p.ownedBarriers.toList()) {
                        rows.add(DialogRow("IN", item.displayName, "Tap to store", icon = item.itemIcon) {
                            if (moveItem { p.ownedBarriers.remove(item) && p.bankBarriers.add(item) }) openNpcDialog(active, "deposit")
                        })
                    }
                    for (unit in p.ownedUnits.toList()) {
                        rows.add(DialogRow("IN", unit.uiName, "Tap to store", icon = unit.itemIcon) {
                            if (moveItem { p.ownedUnits.remove(unit) && p.bankUnits.add(unit) }) openNpcDialog(active, "deposit")
                        })
                    }
                }

                else -> {
                    rows.add(DialogRow("BACK", "Back") { openNpcDialog(active) })
                    rows.add(DialogRow("", "banked ${p.bankMeseta} Meseta"))
                    rows.add(DialogRow("MESETA", "Withdraw 100", icon = ItemIcon.MESETA) { if (bankMesetaWithdraw(100)) openNpcDialog(active, "withdraw") })
                    rows.add(DialogRow("MESETA", "Withdraw all", icon = ItemIcon.MESETA) { if (bankMesetaWithdraw(p.bankMeseta)) openNpcDialog(active, "withdraw") })
                    for ((tool, count) in p.bankTools.entries.toList()) {
                        rows.add(DialogRow("OUT", "${tool.uiName}  x$count", "Tap to take one", icon = tool.itemIcon) {
                            if (bankWithdrawTool(tool)) openNpcDialog(active, "withdraw")
                        })
                    }
                    for (item in p.bankWeapons.toList()) {
                        rows.add(DialogRow("OUT", item.displayName, "Tap to take", icon = item.itemIcon) {
                            if (bankWithdrawWeapon(item)) openNpcDialog(active, "withdraw")
                        })
                    }
                    for (treasureItem in p.bankTreasures.toList()) {
                        rows.add(DialogRow("OUT", treasureItem.uiName, "Tap to take", icon = treasureItem.itemIcon) {
                            if (bankWithdrawTreasure(treasureItem)) openNpcDialog(active, "withdraw")
                        })
                    }
                    for (item in p.bankFrames.toList()) {
                        rows.add(DialogRow("OUT", item.displayName, "Tap to take", icon = item.itemIcon) {
                            if (moveItem { p.bankFrames.remove(item) && p.ownedFrames.add(item) }) openNpcDialog(active, "withdraw")
                        })
                    }
                    for (item in p.bankBarriers.toList()) {
                        rows.add(DialogRow("OUT", item.displayName, "Tap to take", icon = item.itemIcon) {
                            if (moveItem { p.bankBarriers.remove(item) && p.ownedBarriers.add(item) }) openNpcDialog(active, "withdraw")
                        })
                    }
                    for (unit in p.bankUnits.toList()) {
                        rows.add(DialogRow("OUT", unit.uiName, "Tap to take", icon = unit.itemIcon) {
                            if (moveItem { p.bankUnits.remove(unit) && p.ownedUnits.add(unit) }) openNpcDialog(active, "withdraw")
                        })
                    }
                }
            }
        }

        npcDialog.open(NpcDialogState(spec.displayName, spec.dialog, rows))
    }

    /** A bank move: runs [move], persists on success. */
    private fun moveItem(move: () -> Boolean): Boolean {
        if (!move()) return false
        persistProgress()
        return true
    }

    private fun buyFrame(spec: FrameSpec): Boolean {
        val p = player ?: return false
        val price = shopPrice(spec.price)
        if (p.meseta < price) {
            showToast("Not enough Meseta")
            return false
        }
        p.meseta -= price
        val item = rollFrame(spec)
        p.ownedFrames.add(item)
        showToast("Got ${item.displayName}")
        persistProgress()
        return true
    }

    private fun buyBarrier(spec: BarrierSpec): Boolean {
        val p = player ?: return false
        val price = shopPrice(spec.price)
        if (p.meseta < price) {
            showToast("Not enough Meseta")
            return false
        }
        p.meseta -= price
        val item = rollBarrier(spec)
        p.ownedBarriers.add(item)
        showToast("Got ${item.displayName}")
        persistProgress()
        return true
    }

    private fun buyUnit(unit: UnitType): Boolean {
        val p = player ?: return false
        val price = shopPrice(unit.price)
        if (p.meseta < price) {
            showToast("Not enough Meseta")
            return false
        }
        p.meseta -= price
        p.ownedUnits.add(unit)
        persistProgress()
        return true
    }

    private fun sellFrame(item: FrameItem): Boolean {
        val p = player ?: return false
        if (!p.ownedFrames.remove(item)) return false
        p.meseta = (p.meseta + frameSellPrice(item)).coerceAtMost(MAX_MESETA)
        persistProgress()
        return true
    }

    private fun sellBarrier(item: BarrierItem): Boolean {
        val p = player ?: return false
        if (!p.ownedBarriers.remove(item)) return false
        p.meseta = (p.meseta + barrierSellPrice(item)).coerceAtMost(MAX_MESETA)
        persistProgress()
        return true
    }

    private fun sellUnit(unit: UnitType): Boolean {
        val p = player ?: return false
        if (!p.ownedUnits.remove(unit)) return false
        p.meseta = (p.meseta + unitSellPrice(unit)).coerceAtMost(MAX_MESETA)
        persistProgress()
        return true
    }

    /**
     * Wears the frame at [index] of the pack, or takes the current one off when null. The old
     * piece goes back into the pack, so nothing is ever destroyed by a swap.
     */
    private fun equipFrameAt(index: Int?) {
        val p = player ?: return
        val next = index?.let { p.ownedFrames.getOrNull(it) }
        if (index != null && next == null) return
        if (next != null && next.spec.levelReq > p.level) {
            showToast("Needs level ${next.spec.levelReq}")
            return
        }

        p.equippedFrame?.let { p.ownedFrames.add(it) }
        next?.let { p.ownedFrames.remove(it) }
        p.equippedFrame = next

        // A smaller frame carries fewer slots; displaced units go back into the pack.
        val slots = next?.slots ?: 0
        while (p.equippedUnits.size > slots) {
            p.ownedUnits.add(p.equippedUnits.removeAt(p.equippedUnits.size - 1))
        }
        clampVitals(p)
        persistProgress()
    }

    private fun equipBarrierAt(index: Int?) {
        val p = player ?: return
        val next = index?.let { p.ownedBarriers.getOrNull(it) }
        if (index != null && next == null) return
        if (next != null && next.spec.levelReq > p.level) {
            showToast("Needs level ${next.spec.levelReq}")
            return
        }

        p.equippedBarrier?.let { p.ownedBarriers.add(it) }
        next?.let { p.ownedBarriers.remove(it) }
        p.equippedBarrier = next
        clampVitals(p)
        persistProgress()
    }

    private fun equipUnitAt(slot: Int, index: Int?) {
        val p = player ?: return
        if (slot >= (p.equippedFrame?.slots ?: 0)) return

        val current = p.equippedUnits.getOrNull(slot)
        val next = index?.let { p.ownedUnits.getOrNull(it) }
        if (index != null && next == null) return

        current?.let { p.ownedUnits.add(it) }
        next?.let { p.ownedUnits.remove(it) }
        when {
            current != null && next == null -> p.equippedUnits.removeAt(slot)
            current == null && next != null -> p.equippedUnits.add(next)
            next != null -> p.equippedUnits[slot] = next
        }
        clampVitals(p)
        persistProgress()
    }

    /** Unequipping HP/TP gear can shrink the pools below their current fill. */
    private fun clampVitals(p: Player) {
        p.hp = p.hp.coerceAtMost(p.maxHp)
        p.tp = p.tp.coerceAtMost(p.stats.tp)
        playerStatusPanel.setHealth(p.hp, p.maxHp)
        playerStatusPanel.setTp(p.tp, p.stats.tp)
    }

    private fun sellTool(tool: ToolType, price: Int): Boolean {
        val p = player ?: return false
        val count = p.tools[tool] ?: return false
        if (count <= 1) p.tools.remove(tool) else p.tools[tool] = count - 1
        p.meseta = (p.meseta + price).coerceAtMost(MAX_MESETA)
        persistProgress()
        return true
    }

    private fun bankMesetaDeposit(amount: Int): Boolean {
        val p = player ?: return false
        val moved = minOf(amount, p.meseta, MAX_MESETA - p.bankMeseta)
        if (moved <= 0) return false
        p.meseta -= moved
        p.bankMeseta += moved
        persistProgress()
        return true
    }

    private fun bankMesetaWithdraw(amount: Int): Boolean {
        val p = player ?: return false
        val moved = minOf(amount, p.bankMeseta, MAX_MESETA - p.meseta)
        if (moved <= 0) return false
        p.bankMeseta -= moved
        p.meseta += moved
        persistProgress()
        return true
    }

    private fun bankDepositTool(tool: ToolType): Boolean {
        val p = player ?: return false
        val count = p.tools[tool] ?: return false
        if (count <= 1) p.tools.remove(tool) else p.tools[tool] = count - 1
        p.bankTools[tool] = (p.bankTools[tool] ?: 0) + 1
        persistProgress()
        return true
    }

    private fun bankWithdrawTool(tool: ToolType): Boolean {
        val p = player ?: return false
        val banked = p.bankTools[tool] ?: return false
        if ((p.tools[tool] ?: 0) >= tool.maxStack) {
            showToast("You can't carry any more")
            return false
        }
        if (banked <= 1) p.bankTools.remove(tool) else p.bankTools[tool] = banked - 1
        p.tools[tool] = (p.tools[tool] ?: 0) + 1
        persistProgress()
        return true
    }

    private fun bankDepositWeapon(item: WeaponItem): Boolean {
        val p = player ?: return false
        if (!inventory.remove(item)) return false
        p.bankWeapons.add(item)
        persistProgress()
        return true
    }

    private fun bankWithdrawWeapon(item: WeaponItem): Boolean {
        val p = player ?: return false
        if (!p.bankWeapons.remove(item)) return false
        inventory.add(item)
        persistProgress()
        return true
    }

    private fun bankDepositTreasure(treasure: TreasureType): Boolean {
        val p = player ?: return false
        if (!p.treasures.remove(treasure)) return false
        p.bankTreasures.add(treasure)
        persistProgress()
        return true
    }

    private fun bankWithdrawTreasure(treasure: TreasureType): Boolean {
        val p = player ?: return false
        if (!p.bankTreasures.remove(treasure)) return false
        p.treasures.add(treasure)
        persistProgress()
        return true
    }

    /**
     * PSO's focus lock: the nearest living enemy in range is THE target. Attacks and techniques
     * snap the character to face it -- that's how the real game lets you fight without steering
     * every swing by hand -- and the green reticle marks it on screen.
     */
    private var focusedEnemy: Enemy? = null

    /** The lock, of any kind -- [focusedEnemy] mirrors it when the lock is on an enemy. */
    private var focusedTarget: FocusTarget? = null

    // --- The Mag, floating at its owner's left shoulder ---

    private var magMesh: Mesh? = null

    /**
     * The player skeleton's bone count, set the moment the mesh loads. Every player and NPC clip
     * parse needs it: the v2 NJM parser's guess at the per-bone table's end can stop short,
     * leaving the upper spine and arms frozen mid-clip -- the same truncation that once froze
     * the teleporter beams' top rings. See AnimationAssetLoader.
     */
    private var playerBoneCount: Int? = null
    private var magFormLoaded: String? = null
    private var magBobPhase = 0.0

    private fun currentMagForm(p: Player): String = p.mag.form

    /**
     * Loads the Mag's model -- or swaps it after an evolution. The forms are the game's own
     * .nj models from the weapons catalogue (Mag, Varuna, Kalki, Vritra all ship); a standalone
     * .nj carries no world scale, so the mesh is normalised to Mag size by bounding sphere.
     */
    private fun refreshMagMesh() {
        val p = player ?: return
        val form = currentMagForm(p)
        if (form == magFormLoaded) return
        magFormLoaded = form

        MainScope().launch {
            val mesh = WeaponAssetLoader(assetLoader).loadWeapon(form)
            val radius = boundingSphere(mesh).radius
            if (radius > 0) {
                val scale = MAG_RADIUS_UNITS * worldUnit / radius
                mesh.scale.set(scale, scale, scale)
            }
            magMesh?.let { it.parent?.remove(it) }
            magMesh = mesh
            context.scene.add(mesh)
        }
    }

    /**
     * Keeps the Mag at PSO's spot: floating behind the left shoulder, gently bobbing, turning
     * with its owner. Followed per frame rather than parented to the skinned mesh so the
     * proportion sliders never stretch it.
     */
    private fun updateMag(deltaTime: Double) {
        val p = player ?: return
        val mesh = magMesh ?: return
        magBobPhase += deltaTime

        val yaw = p.mesh.rotation.y
        val forwardX = sin(yaw)
        val forwardZ = cos(yaw)
        // The character's left side is up x forward.
        val leftX = forwardZ
        val leftZ = -forwardX

        val side = MAG_SIDE_UNITS * worldUnit
        val back = MAG_BACK_UNITS * worldUnit

        // Just over the shoulder of *this* character, measured off the body.
        val bodyHeight = meshHeightWorld(p.mesh)
        val shoulderY = bodyHeight?.let { it * MAG_HEIGHT_FRACTION }
            ?: (MAG_HEIGHT_UNITS * worldUnit)
        val bob = sin(magBobPhase * MAG_BOB_RATE) * MAG_BOB_UNITS * worldUnit

        mesh.position.set(
            p.mesh.position.x + leftX * side - forwardX * back,
            p.mesh.position.y + shoulderY + bob,
            p.mesh.position.z + leftZ * side - forwardZ * back,
        )
        mesh.rotation.y = yaw
    }

    private val reticleProjection = Vector3()
    private val reticleAimScratch = Vector3()
    private val reticleSideScratch = Vector3()

    /**
     * The original game's lock-on: three small triangles at the corners of an invisible
     * larger triangle, every tip aiming at the target between them. Each corner div carries a
     * CSS border-trick triangle (drawn pointing up), moved out to its corner and rotated so
     * the tip faces the middle -- top corner points straight down, the two lower corners
     * point up and inward.
     */
    /** The darts with the unit offsets they sit at -- repositioned per frame to fit the lock. */
    /** One on-screen lock: the root div and its three darts with their unit offsets. */
    private class ReticleElement(
        val root: HTMLElement,
        val corners: List<Triple<HTMLElement, Double, Double>>,
    )

    /**
     * The reticle pool. One lock used to be the whole story; a sword or shot puts a reticle on
     * every target its sweep can reach, so locks are pooled and grown on demand.
     */
    private val reticlePool = mutableListOf<ReticleElement>()

    private fun reticleElement(index: Int): ReticleElement {
        while (reticlePool.size <= index) {
            val el = (document.createElement("div") as HTMLElement).also { el ->
                el.style.cssText = "position:fixed;width:0;height:0;" +
                    "pointer-events:none;z-index:12;display:none;" +
                    "filter:drop-shadow(0 0 4px rgba(80,255,140,.7));"
            }
            // Narrow darts, not equilateral triangles: an equilateral piece has 120-degree
            // rotational symmetry, so rotated to aim at the centre it *reads* as pointing down
            // regardless -- verified in a standalone render. A long thin dart is unambiguous.
            val triangle =
                "width:0;height:0;position:absolute;" +
                    "border-left:6px solid transparent;border-right:6px solid transparent;" +
                    "border-bottom:18px solid rgba(80,255,140,.95);" +
                    "margin-left:-6px;margin-top:-9px;"
            val corners = mutableListOf<Triple<HTMLElement, Double, Double>>()
            // (unit offset around the target, tip rotation): corners at 90/210/330 degrees,
            // each rotated so its point crosses to the centre. The pixel distance is written
            // per frame, squeezing the lock onto the target's silhouette.
            listOf(
                Triple(0.0, -1.0, 180.0),
                Triple(-0.866, 0.5, 60.0),
                Triple(0.866, 0.5, -60.0),
            ).forEach { (ux, uy, rotation) ->
                (document.createElement("div") as HTMLElement).also { corner ->
                    corner.style.cssText = triangle +
                        "left:${ux * RETICLE_RADIUS_PX}px;top:${uy * RETICLE_RADIUS_PX}px;" +
                        "transform:rotate(${rotation}deg);"
                    el.appendChild(corner)
                    corners.add(Triple(corner, ux, uy))
                }
            }
            document.body!!.appendChild(el)
            reticlePool.add(ReticleElement(el, corners))
        }
        return reticlePool[index]
    }

    /**
     * Anything the lock can settle on: an enemy, a crate, a visible trap, or a drop waiting to
     * be picked up. Exactly one of the four is set.
     */
    private class FocusTarget(
        val enemy: Enemy? = null,
        val box: FieldBox? = null,
        val trap: FieldTrap? = null,
        val pickup: DropPickup? = null,
    ) {
        /** Drops are pointed at, not swung at -- they never join a swing's target list. */
        val attackable: Boolean get() = pickup == null
    }

    /** The BB-style numbered bar's layout, per device like the palette's. */
    private val barConfig = ActionBarConfig.load(defaultBarActions())

    /** A dropped item sitting on the ground, waiting to be walked over. */
    private class DropPickup(val mesh: Object3D, val drop: Drop) {
        /** Set once the player has been told why this one can't be taken, so it says it once. */
        var refusalShown = false
    }

    private val pickups = mutableListOf<DropPickup>()

    /**
     * Rolls an enemy's death drop (rollEnemyDrop: the species' DAR gate, then the real rare
     * chart, then the common split) and places the box where it fell. The box mesh loads per
     * drop; drops are spaced enough that the fetch (browser-cached after the first) never shows.
     */
    private fun maybeDrop(enemy: Enemy) {
        val drop = rollEnemyDrop(enemy.slug, appearance.sectionId, areaTier) ?: return
        val x = enemy.mesh.position.x
        val y = enemy.mesh.position.y
        val z = enemy.mesh.position.z

        MainScope().launch {
            val mesh = ObjectAssetLoader(assetLoader).loadObject(dropModelSlug(drop))
            if (drop.rare) tintRare(mesh)
            mesh.position.set(x, y + groundClearance(mesh), z)
            context.scene.add(mesh)
            pickups.add(DropPickup(mesh, drop))
        }
    }

    /**
     * Which prop marks a drop where it fell. psov2 ships a box per item family and they are
     * already the right colours in their own textures -- the orange weapon crate, the blue
     * armour crate, the green item crate, and meseta's gold gem -- so the kind of thing on the
     * ground is readable before it's picked up, with no tinting at all.
     *
     * A rare is the exception: PSO's red box is universal, whatever it holds.
     */
    private fun dropModelSlug(drop: Drop): String = when {
        drop.rare -> "ItemBox"
        drop is Drop.MesetaDrop -> "Meseta"
        drop is Drop.WeaponDrop -> "WeaponBox"
        drop is Drop.FrameDrop || drop is Drop.BarrierDrop || drop is Drop.UnitDrop -> "ArmorBox"
        else -> "ItemBox"
    }

    /**
     * How far to lift a dropped prop so it rests *on* the ground instead of through it. These
     * models are authored around their own centre, so placing one at the enemy's foot height
     * buries its lower half. The model's own bounding box says exactly how deep that is, which
     * works for the tall crates and the small meseta gem alike, plus a little hover so the box
     * reads as sitting on the surface rather than sunk into it.
     */
    private fun groundClearance(mesh: Object3D): Double {
        val geometry = mesh.asDynamic().geometry ?: return DROP_HOVER_UNITS * worldUnit
        if (geometry.boundingBox == null) geometry.computeBoundingBox()
        val minY = (geometry.boundingBox?.min?.y as? Double) ?: 0.0
        return -minY + DROP_HOVER_UNITS * worldUnit
    }

    /**
     * Paints a box red for a rare. The materials a box mesh carries are an *array* (one per
     * texture the model uses), and reading `.color` straight off that array silently found
     * nothing -- which is why an earlier tinting attempt left every drop the plain weapon crate.
     */
    private fun tintRare(mesh: Object3D) {
        mesh.traverse { child ->
            val material = child.asDynamic().material ?: return@traverse
            if (js("Array.isArray")(material) as Boolean) {
                val materials = material as Array<dynamic>
                for (m in materials) m.color?.setHex(RARE_BOX_COLOR)
            } else {
                material.color?.setHex(RARE_BOX_COLOR)
            }
        }
    }

    /**
     * Weapons banked from walk-over collection; equip them from the Items pane. A same-class
     * upgrade still equips on the spot (better rolled minimum, or a special over none) --
     * swapping to a different weapon class needs the model/animation reload that doesn't exist
     * at runtime yet.
     */
    private val inventory = mutableListOf<WeaponItem>()

    private fun updatePickups(playerPosition: Vector3) {
        val p = player ?: return
        val iterator = pickups.iterator()
        while (iterator.hasNext()) {
            val pickup = iterator.next()
            val dx = playerPosition.x - pickup.mesh.position.x
            val dz = playerPosition.z - pickup.mesh.position.z

            if (dx * dx + dz * dz > PICKUP_RADIUS * PICKUP_RADIUS) continue

            when (val drop = pickup.drop) {
                is Drop.DiskDrop -> {
                    p.techDisks.add(drop.technique to drop.level)
                    showToast("Got ${drop.technique.uiName} Lv.${drop.level} disk")
                }

                is Drop.ToolDrop -> {
                    val held = p.tools[drop.tool] ?: 0
                    when {
                        // A never-empty testing tool is already limitless, so its box is simply
                        // taken. Without this the stack sits permanently at its cap and every
                        // box of it became scenery that could never be picked up.
                        drop.tool in UNLIMITED_TOOLS ->
                            showToast("Got ${drop.tool.uiName}")

                        // A genuinely full stack can't be taken -- but say so, once. Walking
                        // over a box that silently does nothing reads as a broken drop.
                        held >= drop.tool.maxStack -> {
                            if (!pickup.refusalShown) {
                                pickup.refusalShown = true
                                showToast("You can't carry any more ${drop.tool.uiName}")
                            }
                            continue
                        }

                        else -> {
                            p.tools[drop.tool] = held + 1
                            showToast(
                                if (drop.rare) "★ ${drop.tool.uiName}!" else "Got ${drop.tool.uiName}"
                            )
                        }
                    }
                }
                is Drop.MesetaDrop -> {
                    p.meseta = (p.meseta + drop.amount).coerceAtMost(MAX_MESETA)
                    showToast("${drop.amount} Meseta")
                }
                is Drop.TreasureDrop -> {
                    p.treasures.add(drop.treasure)
                    showToast("★ ${drop.treasure.uiName}!")
                }
                is Drop.FrameDrop -> {
                    p.ownedFrames.add(drop.item)
                    showToast(if (drop.rare) "★ ${drop.item.displayName}!" else "Got ${drop.item.displayName}")
                }
                is Drop.BarrierDrop -> {
                    p.ownedBarriers.add(drop.item)
                    showToast(if (drop.rare) "★ ${drop.item.displayName}!" else "Got ${drop.item.displayName}")
                }
                is Drop.UnitDrop -> {
                    p.ownedUnits.add(drop.unit)
                    showToast(if (drop.rare) "★ ${drop.unit.uiName}!" else "Got ${drop.unit.uiName}")
                }
                is Drop.WeaponDrop -> {
                    val item = drop.item
                    val current = equippedItem
                    if (current != null && item.tier.type == current.tier.type &&
                        (item.atpMin > current.atpMin ||
                            (item.specialAttack != null && current.specialAttack == null))
                    ) {
                        inventory.add(current)
                        equipItem(item)
                        showToast("Equipped ${item.displayName}")
                    } else {
                        inventory.add(item)
                        showToast("Got ${item.displayName}")
                    }
                }
            }

            pickup.mesh.parent?.remove(pickup.mesh)
            iterator.remove()
            persistProgress()
        }
    }

    /** Small bottom-centre announcements: pickups, level-ups, a Scape Doll firing. */
    private val toastContainer = (document.createElement("div") as HTMLElement).also { el ->
        el.style.cssText =
            "position:fixed;left:50%;bottom:calc(120px + var(--pw-safe-bottom));" +
                "transform:translateX(-50%);display:flex;flex-direction:column-reverse;" +
                "align-items:center;gap:4px;z-index:55;pointer-events:none;"
        document.body!!.appendChild(el)
    }

    private fun showToast(text: String) {
        val el = document.createElement("div") as HTMLElement
        el.textContent = text
        el.style.cssText =
            "padding:4px 14px;border-radius:12px;background:rgba(4,18,32,.85);" +
                "border:1px solid rgba(90,210,255,.5);color:#e8f6ff;font:12px sans-serif;" +
                "letter-spacing:1px;text-shadow:0 1px 2px black;transition:opacity .4s;"
        toastContainer.appendChild(el)
        window.setTimeout({
            el.style.opacity = "0"
            window.setTimeout({ el.remove() }, 450)
        }, 2600)
    }

    /** Applies an item's stats to combat. Same weapon class only -- see [updatePickups]. */
    private fun equipItem(item: WeaponItem) {
        equippedItem = item
        actionPalette?.setUnusable(GameAction.SPECIAL_ATTACK, item.specialAttack == null)
    }

    /**
     * One connected special swing's effect, per the wiki's family formulas. The status families
     * approximate their real visuals with an inert hold (see EnemyAI.onStatusHeld); durations are
     * this project's own. Reduction (multi-hit weapon types) applies to the chance/drain
     * families, never the elemental ones.
     */
    private fun applySpecialEffect(
        p: Player,
        special: world.phantasmal.web.mobileGame.player.WeaponSpecial,
        enemy: Enemy,
    ) {
        val effectiveness = specialEffectiveness(p.weaponType)

        when (special.family) {
            SpecialFamily.FIRE -> elementalHit(special, enemy, enemy.resistances.fire)
            SpecialFamily.LIGHTNING -> elementalHit(special, enemy, enemy.resistances.thunder)

            SpecialFamily.FREEZE -> {
                val chance = ((special.power - enemy.resistances.special) * effectiveness)
                    .coerceAtMost(FREEZE_CHANCE_CAP)
                if (Random.nextDouble() * 100.0 < chance) enemy.ai?.onStatusHeld(FREEZE_SECONDS)
            }

            SpecialFamily.PARALYSIS -> {
                val chance = (special.power - enemy.resistances.special) * effectiveness
                if (Random.nextDouble() * 100.0 < chance) enemy.ai?.onStatusHeld(PARALYSIS_SECONDS)
            }

            SpecialFamily.CONFUSION -> {
                val chance = (special.power - enemy.resistances.special) * effectiveness
                if (Random.nextDouble() * 100.0 < chance) enemy.ai?.onStatusHeld(CONFUSION_SECONDS)
            }

            SpecialFamily.INSTANT_KILL -> {
                val chance = (special.power - enemy.resistances.dark) * effectiveness
                if (Random.nextDouble() * 100.0 < chance) enemy.hp = 0
            }

            SpecialFamily.HP_DRAIN -> {
                val drained =
                    (minOf(special.power / 100.0 * enemy.maxHp, HP_DRAIN_CAP) * effectiveness)
                        .toInt()
                if (drained > 0 && p.hp > 0) {
                    p.hp = (p.hp + drained).coerceAtMost(p.maxHp)
                    playerStatusPanel.setHealth(p.hp, p.maxHp)
                }
            }

            SpecialFamily.HP_CUT -> {
                // Half the time nothing happens even on a hit -- that's the real activation roll.
                if (Random.nextDouble() < 0.5) {
                    val cut = (enemy.hp * special.power / 100.0 * effectiveness).toInt()
                    enemy.hp -= cut
                    damageNumbers.showDamage(
                        enemy.mesh.position.x, labelHeight(enemy), enemy.mesh.position.z,
                        cut, false,
                    )
                }
            }

            SpecialFamily.BERSERK -> Unit // Its whole effect is the 3.33x modifier, paid in HP.
        }
    }

    /** Flat elemental damage against the matching resistance, shown as its own number. */
    private fun elementalHit(
        special: world.phantasmal.web.mobileGame.player.WeaponSpecial,
        enemy: Enemy,
        resistancePercent: Int,
    ) {
        val extra =
            (special.elementalDamage(player?.level ?: 1) * (100 - resistancePercent) / 100.0).toInt()
        if (extra <= 0) return
        enemy.hp -= extra
        damageNumbers.showDamage(
            enemy.mesh.position.x, labelHeight(enemy), enemy.mesh.position.z, extra, false,
        )
    }

    /** Runs every living hive's production clock -- see [HiveState]. */
    private fun updateHives(deltaTime: Double, playerPosition: Vector3) {
        val spawn = spawnEnemy ?: return

        val iterator = hives.iterator()
        while (iterator.hasNext()) {
            val state = iterator.next()

            // A dead hive never produces again; its surviving brood fights on unaffected.
            if (state.hive.isDead) {
                iterator.remove()
                continue
            }

            state.brood.removeAll { it.isDead }
            state.emitCooldown -= deltaTime
            if (state.emitCooldown > 0 || state.brood.size >= HIVE_MAX_MOTHMANTS) continue

            val hivePosition = state.hive.mesh.position
            val dx = playerPosition.x - hivePosition.x
            val dz = playerPosition.z - hivePosition.z
            val distSq = dx * dx + dz * dz
            if (distSq > hiveProductionRangeSq) continue

            // A hive still hanging in the canopy, dropping, or in the middle of falling over
            // isn't working yet -- it produces only once it's set down, upright or on its side.
            if (state.hive.ai?.hiveCanProduce != true) continue

            state.emitCooldown = HIVE_EMIT_INTERVAL

            // The Mothmant emerges on the player's side of the hive, just clear of its wide
            // cylinder, already facing its target. Upright, they're cast off the *top* -- the
            // brood pours out of the crown, not from underneath it; once the hive has been
            // knocked over they spill out at ground level instead.
            val dist = kotlin.math.sqrt(distSq).coerceAtLeast(0.001)
            val down = state.hive.ai?.hiveIsDown == true
            val emitHeight =
                if (down) HIVE_DOWNED_EMIT_HEIGHT_UNITS * worldUnit
                else HIVE_CROWN_HEIGHT_UNITS * worldUnit

            val mothmant = spawn(
                "Mothmant",
                hivePosition.x + dx / dist * hiveEmitOffset,
                hivePosition.y + emitHeight,
                hivePosition.z + dz / dist * hiveEmitOffset,
                kotlin.math.atan2(dx, dz),
            )

            if (mothmant != null) {
                state.brood.add(mothmant)
                state.hive.ai?.onProduce()
            }
        }
    }

    private val enemies = mutableListOf<Enemy>()
    private val npcMixers = mutableListOf<AnimationMixer>()

    /**
     * Every warp pad in the map: Pioneer 2's hand-transcribed three, plus -- in the field --
     * whatever the area's own object data places (Teleporter/Warp/Boss Teleporter records,
     * appended during setup).
     */
    private val teleporters: MutableList<Pioneer2Teleporter> =
        (if (mapSlug == "pioneer2") PIONEER2_TELEPORTERS else emptyList()).toMutableList()

    /** Animated field props (teleporter beams etc.) whose mixers tick every frame. */
    private val propMixers = mutableListOf<AnimationMixer>()

    /** The layout's authored player spawn (Player Set slot 0), applied after the player exists. */
    private var authoredSpawn: SpawnObject? = null

    /**
     * Energy barriers authored with no door ID (-1): they drop when their own room's wave
     * chain completes -- see RoomWaveDirector.completedSections.
     */
    private val sectionBarriers = mutableListOf<Pair<FieldGates.Gate, Int>>()

    /**
     * True while the player is still standing on the pad they just arrived on. Without this the
     * two Principal warps would fire every frame and bounce the player back and forth, since each
     * one's destination is a few units from the other's pad.
     */
    private var standingOnTeleporter = false

    /** Guards [onAreaTransition] so a pad straddled for several frames only fires once. */
    private var areaTransitionStarted = false

    /** One town door's animation state -- see [updateDoors]. */
    private class DoorState(
        val x: Double,
        val z: Double,
        val mixer: AnimationMixer,
        val action: AnimationAction,
        val openDuration: Double,
    ) {
        /** Seconds into the open clip: 0 is shut, [openDuration] fully open. */
        var openTime: Double = .0
    }

    private val doors = mutableListOf<DoorState>()

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

    /**
     * TESTING AID: the phone has no console, so a runtime exception simply makes things
     * silently not happen. Surfacing every script error and unhandled rejection as a toast
     * turns the tester's device into its own debugger.
     */
    private val windowErrorListener: (org.w3c.dom.events.Event) -> Unit = { event ->
        val detail = event.asDynamic().message ?: event.asDynamic().reason
        showToast("JS error: ${detail.toString().take(140)}")
    }

    init {
        window.addEventListener("error", windowErrorListener)
        window.addEventListener("unhandledrejection", windowErrorListener)

        MainScope().launch {
          try {
            // Static hub stages (currently just Pioneer 2 -- see STAGE_SPECS in
            // :web:assets-generation's StageSpecs.kt) use a structurally different section format
            // than field areas, needing MapAssetLoader's separate loadStage entry point; see
            // Psov2StageGeometry.kt for why.
            // The quest layer wakes before the world loads: the active quest's definition
            // decides the terrain variant and replaces the floor's encounter table below.
            QuestSession.restore()
            if (QuestSession.active) {
                questDef = try {
                    loadQuestDef(assetLoader, QuestSession.slug!!)
                } catch (e: Throwable) {
                    console.warn("Quest ${QuestSession.slug} failed to load: ${e.message}")
                    null
                }
            }

            val mapAssetLoader = MapAssetLoader(assetLoader)
            val map = if (mapSlug in STAGE_SLUGS) {
                mapAssetLoader.loadStage(mapSlug)
            } else {
                // Cave/Mine/Ruins pick a random one of their several layout variants each time,
                // same as the real game -- see randomAreaLayoutSlug's doc comment. The
                // resolved slug is kept: the caves' encounter tables are per-terrain, so the
                // spawn layout pick below must match the geometry that actually loaded.
                mapAssetLoader.loadArea(
                    (geometryOverride
                        ?: questDef?.let { def ->
                            QUEST_FLOOR_FOR_MAP[mapSlug]?.let { floor ->
                                questGeometrySlug(def, floor, mapSlug)
                            }
                        }
                        ?: randomAreaLayoutSlug(mapSlug))
                        .also { resolvedGeometrySlug = it }
                )
            }
            context.scene.add(map.renderObject)
            // Kept for anything built after setup -- the boss-room warp stands on it.
            walkableCollision = map.walkableCollisionObject

            // The map's sky dome, where one ships (the forests). Drawn at the origin exactly
            // as authored -- the dome is modelled around the map.
            SKY_FOR_MAP[mapSlug]?.let { skySlug ->
                val sky = ObjectAssetLoader(assetLoader).loadObject(skySlug)
                context.scene.add(sky)
            }

            // Boss arenas: the stage .rel is only the outer shell. The floor the fight stands
            // on is its own object set, loaded BEFORE any ground search so the player and the
            // boss land on the real arena floor instead of the shell's summit.
            if (bossEncounter != null) {
                val floorLoader = ObjectAssetLoader(assetLoader)
                for (slug in BOSS_ARENA_FLOOR_PARTS[mapSlug].orEmpty()) {
                    context.scene.add(floorLoader.loadObject(slug))
                    // A second, independent copy joins the walkable set -- an Object3D has one
                    // parent, and the ground raycasts walk this hierarchy.
                    map.walkableCollisionObject.add(floorLoader.loadObject(slug))
                }
            }

            val playerMeshData = PlayerAssetLoader(characterClassAssetLoader).loadPlayerMesh(appearance)
            playerBoneCount = playerMeshData.njObject.boneCount()
            val mesh = playerMeshData.mesh
            val bSphereRadius = boundingSphere(mesh).radius

            // Proportions are visual only, applied *after* the bounding sphere is taken so every
            // gameplay scale derived from it (speed, hitboxes, ranges) stays identical for every
            // character regardless of build.
            mesh.scale.set(
                appearance.proportionWidth,
                appearance.proportionHeight,
                appearance.proportionWidth,
            )

            // Real terrain's walkable (near-flat) triangles are a sparse, scattered subset of the
            // whole mesh, so (0,0) itself has no guarantee of landing on one -- search nearby
            // rather than silently defaulting to y=0, see findNearestGroundHeight's doc comment.
            // Stage-format hubs need their own known-good origin (see STAGE_SPAWN_ORIGINS) and the
            // "stable" ground search: Pioneer 2 in particular has several stacked walkable surfaces
            // (an elevated walkway deck, a teleporter dais top, and only then the real street) plus
            // thin decorative details (counter trim, railings) that a single raycast can land on and
            // mistake for solid floor -- see findNearestStableGroundHeight's doc comment. Field/
            // dungeon Room-format maps haven't shown either problem, so they keep the plain search.
            val (originX, originZ) =
                spawnOverride ?: STAGE_SPAWN_ORIGINS[mapSlug] ?: (.0 to .0)
            val isStage = mapSlug in STAGE_SLUGS
            val (spawnX, groundY, spawnZ) = if (spawnYOverride != null) {
                Triple(originX, spawnYOverride, originZ)
            } else (
                if (isStage) {
                    findNearestStableGroundHeight(map.walkableCollisionObject, originX, originZ)
                } else {
                    findNearestGroundHeight(map.walkableCollisionObject, originX, originZ)
                }
                ) ?: Triple(originX, .0, originZ)
            mesh.position.set(spawnX, groundY, spawnZ)
            console.log("Player spawn: $spawnX $groundY $spawnZ ($mapSlug)")
            // Boss arenas: arrive facing the field's centre (the boss waits there) unless a
            // debug facing already chose otherwise. Computed from the spawn so moving the
            // arrival point around the rim keeps the camera opening onto the fight.
            (facingOverride ?: if (bossEncounter != null) atan2(-spawnX, -spawnZ) else null)
                ?.let { mesh.rotation.y = it }
            cameraYawOverride?.let {
                (inputManager as? ThirdPersonCameraController)?.debugSetYawOffset(it)
            }
            context.scene.add(mesh)

            inputManager.setScale(bSphereRadius)
            // The camera's wall clamp collides with the map's *full* collision set -- walls
            // included -- not the walkable subset the ground raycasts use.
            inputManager.collider = collisionGeometryToGroup(map.collisionGeometry)

            // The radar's floor plan: the map's up-facing collision triangles, projected
            // top-down. Sign-independent normal test for the same winding reason isWalkable
            // is (see MapAssetLoader).
            miniMap?.setMapGeometry(
                map.collisionGeometry.meshes.flatMap { collisionMesh ->
                    collisionMesh.triangles.mapNotNull { triangle ->
                        if (abs(triangle.normal.y) < 0.5) return@mapNotNull null
                        val v1 = collisionMesh.vertices[triangle.index1]
                        val v2 = collisionMesh.vertices[triangle.index2]
                        val v3 = collisionMesh.vertices[triangle.index3]
                        doubleArrayOf(
                            v1.x.toDouble(), v1.z.toDouble(),
                            v2.x.toDouble(), v2.z.toDouble(),
                            v3.x.toDouble(), v3.z.toDouble(),
                        )
                    }
                }
            )

            val animator = addDisposable(PlayerAnimator(playerMeshData.njObject, mesh))

            if (showAnimationBrowser) {
                addDisposable(
                    AnimationDebugOverlay(
                        document.body!!,
                        MainScope(),
                        animationAssetLoader,
                        animator,
                    )
                )
            }
            // No combat in town/lobby hubs in real PSO, so no weapon is drawn there.
            val weaponEquipped = !isPeacefulHub

            // Every player clip comes from the equipped weapon's class, so the character stands,
            // walks and swings as though actually holding it -- including holding nothing, which
            // has its own set (see WeaponType/PlayerAnimations).
            val equipped = if (weaponEquipped) weaponType(weaponSlug) else WeaponType.FIST
            val weaponAnimations = equipped.animations
            equippedWeaponAtp = equipped.atp

            // The character's starting weapon as a real item: base tier of its line where one is
            // catalogued (see WeaponItems.kt), no special -- a shop-floor starter. Types without
            // an item line yet keep the old flat class ATP.
            equippedItem = when (equipped) {
                WeaponType.SABER -> WeaponItem(SABER_LINE[0])
                WeaponType.HANDGUN -> WeaponItem(HANDGUN_LINE[0])
                WeaponType.CANE -> WeaponItem(CANE_LINE[0])
                else -> null
            }

            val idleMotion = loadPlayerClip(animationPath(weaponAnimations.idle))
            val walkMotion = loadPlayerClip(animationPath(weaponAnimations.walk))
            val deadMotion = loadPlayerClip(
                // A class with no clip of its own for something falls back to the unarmed set,
                // which is complete -- see WeaponAnimations.
                animationPath(weaponAnimations.death ?: PlayerAnimations.FIST.death!!)
            )
            val hitMotion = loadPlayerClip(
                animationPath(weaponAnimations.hit ?: PlayerAnimations.FIST.hit!!)
            )
            // The guard: played when an incoming attack is evaded rather than absorbed.
            val blockMotion = loadPlayerClip(
                animationPath(weaponAnimations.block ?: PlayerAnimations.FIST.block!!)
            )
            val runMotion = loadPlayerClip(
                animationPath(weaponAnimations.run)
            )
            val knockedDownMotion = weaponAnimations.knockedDown?.let {
                loadPlayerClip(animationPath(it))
            }
            val attackMotions =
                weaponAnimations.attacks.map { loadPlayerClip(animationPath(it)) }
            // The frozen debug pose must be the FIRST clip played: crossfading from an
            // already-running idle over 0 seconds races, and the freeze lands on whichever
            // action happened to win -- the survey instrument has to be deterministic.
            val poseMotion = poseOverride?.let { (clipIndex, _) ->
                loadPlayerClip(animationPath(clipIndex))
            }
            if (poseMotion != null) {
                animator.playClip(poseMotion)
                animator.update(poseOverride!!.second / PSO_FRAME_RATE_DOUBLE)
                animator.timeScale = 0.0
                poseLocked = true
            } else {
                animator.playClip(idleMotion)
            }

            if (boneScan) {
                // One frame late so the posed matrices are what get sampled.
                window.setTimeout({
                    val v = Vector3()
                    console.log(
                        "BONESCAN class=${appearance.characterClass.slug} " +
                            "bones=${mesh.skeleton.bones.size} " +
                            "player=${mesh.position.x},${mesh.position.y},${mesh.position.z} " +
                            "yaw=${mesh.rotation.y}"
                    )
                    for ((i, bone) in mesh.skeleton.bones.withIndex()) {
                        bone.getWorldPosition(v)
                        console.log("BONE $i ${v.x} ${v.y} ${v.z}")
                    }
                    // The hand bone's REST orientation -- per-skeleton grip frames are the
                    // suspect for weapons floating off the hand on non-HUmar bodies.
                    val bindWorld = Matrix4().copy(mesh.skeleton.boneInverses[46]).invert()
                    val bindPos = Vector3()
                    val bindRot = Quaternion()
                    val bindScale = Vector3()
                    bindWorld.decompose(bindPos, bindRot, bindScale)
                    console.log(
                        "BIND46 ${bindRot.x} ${bindRot.y} ${bindRot.z} ${bindRot.w} " +
                            "pos=${bindPos.x},${bindPos.y},${bindPos.z}"
                    )
                }, 500)
            }

            if (weaponEquipped) {
                weaponAttachment = Weapon.attach(assetLoader, mesh, weaponSlug)
            }

            // Every character has a Mag from creation, in town as well as the field -- it isn't a
            // weapon, it's a companion.
            magCompanion = MagCompanion.attach(assetLoader, mesh, bSphereRadius)

            // What a PSO unit is worth in world units. Set here rather than inside the
            // field-only section below, where it used to live: on Pioneer 2 that branch never
            // runs, so this stayed at 1 and every unit-scaled distance in town was wrong by
            // the whole scale factor -- the Mag shrank to a speck inside the player, and NPC
            // speaking range was a fraction of what it read as.
            worldUnit = psoUnit(bSphereRadius)
            techniqueFx = TechniqueFx(context.scene, worldUnit) { name -> effectTexture(name) }

            // Field/dungeon maps only -- hub/lobby/boss-arena/VS-arena stages (STAGE_SLUGS)
            // are meant to be player + friendly-NPC only, matching the real game; monsters
            // belong in their own biome's map, not standing around Pioneer 2.
            if (mapSlug !in STAGE_SLUGS || bossEncounter != null) {
                val enemyLoader = EnemyAssetLoader(assetLoader)
                fieldEnemyLoader = enemyLoader
                val unitScale = psoUnit(bSphereRadius)

                // Shared across every enemy's AI so the per-map wall-triangle list (see
                // WallCollider's own doc comment on why it's a brute-force scan) is only built
                // once, not once per enemy.
                val enemyWallCollider = WallCollider(
                    map.collisionGeometry,
                    // Same authored-versus-synthesized split as CharacterController's own collider:
                    // real walls block regardless of height, so no step-over allowance.
                    stepHeight =
                        if (map.hasAuthoredCollision) 0.0
                        else bSphereRadius * CharacterController.MAX_STEP_HEIGHT_FACTOR,
                    authoredFlags = map.hasAuthoredCollision,
                    // Unlike the player, enemies honour the room-containment rings, so a chased
                    // pack stays in its own room the way the real game's does.
                    blockContainmentPlanes = true,
                )

                // PSO's real encounter for this area: rooms that stay empty until you walk into
                // them, then feed you their waves one at a time (see RoomWaveDirector). Replaces
                // the arc of one-of-every-species that used to be dropped around the spawn point
                // -- that was a load-test sweep over the converted models, never an encounter.
                val spawnTable = loadAreaSpawnTable(assetLoader, mapSlug)
                // During a quest the floor runs the quest's own encounter table -- its
                // authored enemies, objects and wave events -- on the designated terrain.
                val questLayout = questDef?.let { def ->
                    QUEST_FLOOR_FOR_MAP[mapSlug]
                        ?.takeIf { it in 1..10 }
                        ?.let { floor -> questFieldLayout(def, floor) }
                }
                val layout = questLayout
                    ?: spawnTable?.pickSoloLayout(layoutOverride, resolvedGeometrySlug)

                if (spawnTable != null && layout != null) {
                    val clipSets = mutableMapOf<String, EnemyClipSet>()

                    // Every species this layout can produce, loaded while the loading screen is
                    // still up. Waves spawn mid-play, and one that had to fetch and parse its
                    // model first would arrive seconds after the room it belongs to. Monest hives
                    // produce Mothmants at runtime, so a layout with hives needs the Mothmant
                    // loaded too even though no placement ever names it.
                    val speciesToLoad = layout.enemies.mapTo(mutableSetOf()) { it.slug }
                    if ("Monest" in speciesToLoad) speciesToLoad.add("Mothmant")
                    // Every rare twin of anything placed, so a 1-in-512 roll at spawn time
                    // always has its model warm -- a roll that had to fetch first would
                    // silently produce nothing.
                    for ((base, rare) in RARE_TWINS) {
                        if (base in speciesToLoad) speciesToLoad.add(rare)
                    }
                    // The area's boss too: it belongs to the area, not to whichever encounter
                    // layout was rolled, so it is almost never in the layout's own roster. Left
                    // out, its spawn silently fails and the boss room can never be cleared.
                    AREA_BOSSES[mapSlug]?.enemies?.forEach { speciesToLoad.add(it.slug) }

                    for (slug in speciesToLoad) {
                        val kind = AREA_ENEMIES[mapSlug]?.find { it.slug == slug } ?: continue
                        val prototype = enemyLoader.loadPrototype(slug)
                        clipSets[slug] = enemyLoader.loadClipSet(kind, prototype.njObject)
                    }

                    // One enemy, fully assembled and added to the world -- shared by the wave
                    // director's placements and the Monest hives' runtime Mothmant production.
                    fun spawnFieldEnemy(
                        placedSlug: String,
                        x: Double,
                        y: Double,
                        z: Double,
                        yaw: Double,
                        section: Int = -1,
                    ): Enemy? {
                        // The rare roll: every placement of a rare-capable species has a
                        // 1-in-512 chance of arriving as its rare form, the real game's rate.
                        val slug = RARE_TWINS[placedSlug]
                            ?.takeIf { Random.nextInt(RARE_ROLL_ONE_IN) == 0 }
                            ?: placedSlug

                        // The Dubwitch has no model anywhere in the source data, so its pod is
                        // built here: a squat machine column with a red running light. Killing
                        // it is what makes a Dubchic room stay down.
                        if (slug == "Dubwitch") {
                            val stats = enemyStats(slug)
                            val pod = Mesh(
                                CylinderGeometry(
                                    1.1 * worldUnit, 1.4 * worldUnit, 2.4 * worldUnit, 10,
                                ),
                                MeshBasicMaterial(obj { color = Color(0x3a4048) }),
                            )
                            val light = Mesh(
                                SphereGeometry(0.5 * worldUnit, 10, 8),
                                MeshBasicMaterial(obj {
                                    color = Color(0xff3b30)
                                    blending = AdditiveBlending
                                    transparent = true
                                }).also { it.depthWrite = false },
                            )
                            light.position.y = 1.5 * worldUnit
                            pod.add(light)
                            pod.position.set(x, y + 1.2 * worldUnit, z)
                            pod.rotation.y = yaw
                            context.scene.add(pod)

                            val enemy = Enemy(
                                pod.unsafeCast<SkinnedMesh>(),
                                hp = stats.hp,
                                name = enemyDisplayName(slug),
                                hitboxRadius = stats.hitboxRadius * unitScale,
                                maxHp = stats.hp,
                                slug = slug,
                                section = section,
                            )
                            enemy.visualRadius = 1.4 * worldUnit
                            enemy.visualTop = 1.9 * worldUnit
                            enemies.add(enemy)
                            return enemy
                        }

                        val prototype = enemyLoader.prototype(slug)
                        val clips = clipSets[slug]

                        // A species with no roster entry (see AREA_ENEMIES) is skipped rather
                        // than spawned untextured or unanimated.
                        if (prototype == null || clips == null) return null

                        if (debugBindPose) {
                            val poseMesh = prototype.instantiate()
                            val poseScale = enemyStats(slug).modelScale
                            poseMesh.scale.set(poseScale, poseScale, poseScale)
                            poseMesh.position.set(x, y, z)
                            poseMesh.rotation.y = yaw
                            context.scene.add(poseMesh)
                            val enemy = Enemy(
                                poseMesh,
                                hp = enemyStats(slug).hp,
                                name = enemyDisplayName(slug),
                                slug = slug,
                            )
                            enemies.add(enemy)
                            return enemy
                        }

                        val stats = enemyStats(slug)
                        val mesh = prototype.instantiate()
                        // Before the bounding-sphere measurement below, so the AI's derived
                        // radii see the size the player does.
                        mesh.scale.set(stats.modelScale, stats.modelScale, stats.modelScale)
                        // The placement's own height is used as-is. It came out of the same map
                        // data the terrain did, so it's already correct, and raycasting for a
                        // "real" floor here has repeatedly found the wrong surface.
                        mesh.position.set(x, y, z)
                        mesh.rotation.y = yaw
                        context.scene.add(mesh)

                        val mixer = AnimationMixer(mesh)

                        // Filled in below, once the Enemy the shot comes from exists.
                        var firer: (() -> Unit)? = null

                        val ai = EnemyAI(
                            mesh,
                            prototype.njObject,
                            mixer,
                            clips.walk,
                            clips.attack,
                            enemyWallCollider,
                            map.walkableCollisionObject,
                            boundingSphere(mesh).radius,
                            waitMotion = clips.wait,
                            damageMotion = clips.damage,
                            deathMotion = clips.death,
                            runMotion = clips.run,
                            wakeUpMotion = clips.wakeUp,
                            hitboxUnits = stats.hitboxRadius,
                            attackRangeUnits = stats.attackRange,
                            unitScale = unitScale,
                            attackMotionAlt = clips.attackAlt,
                            stunMotion = clips.stun,
                            appearMotion = clips.appear,
                            isStationary = stats.isStationary,
                            strikesWhileRooted = stats.strikesWhileRooted,
                            hoverUnits = stats.hoverUnits,
                            hiveClips = clips.hive,
                            rangedRangeUnits = stats.rangedRangeUnits,
                            fleeRangeUnits = stats.fleeRangeUnits,
                            // The shot itself needs the Enemy, which doesn't exist yet -- the
                            // firer is filled in the moment it does, just below.
                            onRangedAttack =
                                if (stats.rangedRangeUnits > 0) ({ firer?.invoke() })
                                else null,
                        )

                        val enemy = Enemy(
                            mesh,
                            hp = stats.hp,
                            name = enemyDisplayName(slug),
                            animationMixer = mixer,
                            ai = ai,
                            dfp = stats.dfp,
                            atp = stats.atp,
                            lck = stats.lck,
                            hitboxRadius = stats.hitboxRadius * unitScale,
                            evp = stats.evp,
                            maxHp = stats.hp,
                            resistances = stats.resistances,
                            slug = slug,
                            section = section,
                        )
                        // matrixWorld hasn't been refreshed this early, so the measured sphere
                        // is at model scale; apply the species' own multiplier by hand.
                        enemy.visualRadius = boundingSphere(mesh).radius * stats.modelScale
                        enemy.visualTop = (geometryTopY(mesh) ?: 0.0) * stats.modelScale
                        enemy.reviveMotion = clips.revive

                        // Now that the body exists, give its AI something to fire. The Nano
                        // Dragon's nano laser crosses the room; a Lily spits venom.
                        if (stats.rangedRangeUnits > 0) {
                            firer = {
                                when (slug) {
                                    // The nano laser: fast, violet, room-length.
                                    "NanoDragoon" -> fireEnemyShot(
                                        enemy,
                                        speedUnits = NANO_LASER_SPEED_UNITS,
                                        damage = NANO_LASER_DAMAGE,
                                        poisons = false,
                                        colorHex = NANO_LASER_COLOR,
                                        sizeUnits = 0.7,
                                    )
                                    // Garanz's missiles: slower and heavier than any laser.
                                    "Garanz" -> fireEnemyShot(
                                        enemy,
                                        speedUnits = GARANZ_MISSILE_SPEED_UNITS,
                                        damage = GARANZ_MISSILE_DAMAGE,
                                        poisons = false,
                                        colorHex = GARANZ_MISSILE_COLOR,
                                        sizeUnits = 1.1,
                                    )
                                    // The Canadines' airborne zap.
                                    "Canadine", "Canane" -> fireEnemyShot(
                                        enemy,
                                        speedUnits = CANADINE_ZAP_SPEED_UNITS,
                                        damage = CANADINE_ZAP_DAMAGE,
                                        poisons = false,
                                        colorHex = CANADINE_ZAP_COLOR,
                                        sizeUnits = 0.6,
                                    )
                                    // The Sorcerer's technique orb.
                                    "ChaosSorcerer" -> fireEnemyShot(
                                        enemy,
                                        speedUnits = SORCERER_ORB_SPEED_UNITS,
                                        damage = SORCERER_ORB_DAMAGE,
                                        poisons = false,
                                        colorHex = SORCERER_ORB_COLOR,
                                        sizeUnits = 0.9,
                                    )
                                    // A Belra's arm strike crosses half the room.
                                    "DarkBelra" -> fireEnemyShot(
                                        enemy,
                                        speedUnits = BELRA_ARM_SPEED_UNITS,
                                        damage = BELRA_ARM_DAMAGE,
                                        poisons = false,
                                        colorHex = BELRA_ARM_COLOR,
                                        sizeUnits = 1.2,
                                    )
                                    // The Dark Gunner's cannon.
                                    "DarkGunner" -> fireEnemyShot(
                                        enemy,
                                        speedUnits = GUNNER_LASER_SPEED_UNITS,
                                        damage = GUNNER_LASER_DAMAGE,
                                        poisons = false,
                                        colorHex = GUNNER_LASER_COLOR,
                                        sizeUnits = 0.7,
                                    )
                                    // The Lilies' venom spit.
                                    else -> fireEnemyShot(
                                        enemy,
                                        speedUnits = LILY_SPIT_SPEED_UNITS,
                                        damage = LILY_SPIT_DAMAGE,
                                        poisons = true,
                                        colorHex = LILY_SPIT_COLOR,
                                        sizeUnits = 1.0,
                                    )
                                }
                            }
                        }

                        enemies.add(enemy)
                        return enemy
                    }

                    hives.clear()
                    spawnEnemy = ::spawnFieldEnemy
                    spawnFieldBoxes(layout, unitScale)
                    hiveProductionRangeSq =
                        HIVE_PRODUCTION_RANGE_UNITS * unitScale * HIVE_PRODUCTION_RANGE_UNITS * unitScale
                    hiveEmitOffset = HIVE_EMIT_OFFSET_UNITS * unitScale

                    // Hives are part of the room, not part of a wave. Every other species is
                    // spawned by the wave that summons it, which is right for something that
                    // bursts out of the ground -- but a Monest is a nest that was already
                    // hanging there. Spawned on a wave trigger it popped into existence
                    // mid-fight, and its whole opening -- hanging closed in the canopy, then
                    // dropping and setting down as you approach -- was never seen, because the
                    // player was never around before it existed. Placed up front, walking into
                    // the room shows you exactly where it is.
                    for (placement in layout.enemies.filter { it.slug == "Monest" }) {
                        spawnFieldEnemy(
                            placement.slug,
                            placement.x, placement.y, placement.z,
                            placement.yaw,
                            placement.section,
                        )?.let { hives.add(HiveState(it)) }
                    }

                    val triggerVolumes = layout.objects
                        .filter { it.typeId == SpawnObject.TYPE_EVENT_COLLISION }
                        .mapNotNull { obj ->
                            val radius = obj.paramsF.getOrNull(0) ?: return@mapNotNull null
                            val eventId = obj.paramsI.getOrNull(0) ?: return@mapNotNull null
                            TriggerVolume(obj.x, obj.z, radius, eventId)
                        }

                    // The map's fog of war works off the same rooms the wave director does.
                    miniMap?.setRooms(
                        (layout.sections.ifEmpty { spawnTable.sections }).map { section ->
                            MapRoom(section.id, section.x, section.z)
                        }
                    )

                    roomWaveDirector = RoomWaveDirector(spawnTable, layout, volumes = triggerVolumes) { placement ->
                        // Already standing (above) -- and not something a wave should wait on.
                        if (placement.slug == "Monest") return@RoomWaveDirector null

                        val enemy = spawnFieldEnemy(
                            placement.slug,
                            placement.x, placement.y, placement.z,
                            placement.yaw,
                            placement.section,
                        )

                        // A skipped species still counts as cleared, so the room can't dead-end.
                        if (enemy == null) null else SpawnedEnemy { enemy.isDead }
                    }
                }

                // The layout's doors, laser fences and switches -- the visible form of the room
                // system: a shut door marks a room whose waves aren't cleared yet, a laser fence
                // marks a switch puzzle. Placements come from the same object records the real
                // game reads (see SpawnObject).
                if (layout != null && layout.objects.isNotEmpty()) {
                    val gateLoader = ObjectAssetLoader(assetLoader)
                    val gates =
                        FieldGates(bSphereRadius * CharacterController.HITBOX_RADIUS_FACTOR)

                    // One skinned instance per placement: mesh added to the scene, plus its
                    // mixer and (not yet playing) open/press clip.
                    suspend fun animatedPart(
                        slug: String,
                        obj: SpawnObject,
                    ): Triple<SkinnedMesh, AnimationMixer, AnimationAction> {
                        val data = gateLoader.loadAnimatedObject(slug)
                        data.mesh.position.set(obj.x, obj.y, obj.z)
                        data.mesh.rotation.y = obj.yaw
                        context.scene.add(data.mesh)

                        val mixer = AnimationMixer(data.mesh)
                        val action =
                            mixer.clipAction(createAnimationClip(data.njObject, data.motion))
                        return Triple(data.mesh, mixer, action)
                    }

                    val switchPlacements = mutableListOf<SpawnObject>()

                    /**
                     * The Caves' four-button doors, by door ID: each opens only once every
                     * floor panel in its set has been stood on. A panel's Switch ID is its
                     * door's ID plus one through four, which is how a panel finds its door.
                     */
                    val buttonDoors = mutableListOf<Pair<Int, FieldGates.Gate>>()
                    val buttonDoorPanels = mutableMapOf<Int, MutableList<SpawnObject>>()
                    val buttonDoorPressed = mutableMapOf<Int, MutableSet<Int>>()

                    for (obj in layout.objects) {
                        when (obj.typeId) {
                            SpawnObject.TYPE_FOREST_DOOR -> {
                                val frame = animatedPart("ForestDoor", obj)
                                val beam = animatedPart("ForestDoorBeam", obj)

                                gates.doors.add(
                                    FieldGates.Gate(
                                        meshes = listOf(frame.first, beam.first),
                                        mixers = listOf(frame.second, beam.second),
                                        openActions = listOf(frame.third, beam.third),
                                        doorId = obj.doorId,
                                        x = obj.x, y = obj.y, z = obj.z,
                                        halfWidth = FieldGates.DOOR_HALF_WIDTH,
                                        yaw = obj.yaw,
                                        hideOnOpen = false,
                                    )
                                )
                            }

                            SpawnObject.TYPE_LASER_FENCE,
                            SpawnObject.TYPE_SQUARE_LASER_FENCE,
                            SpawnObject.TYPE_RUINS_FENCE_4X2,
                            SpawnObject.TYPE_RUINS_FENCE_6X2,
                            -> {
                                val slug = when (obj.typeId) {
                                    SpawnObject.TYPE_SQUARE_LASER_FENCE -> "SquareLaserFence4M"
                                    SpawnObject.TYPE_RUINS_FENCE_4X2 -> "RuinsFence4x2"
                                    SpawnObject.TYPE_RUINS_FENCE_6X2 -> "RuinsFence6x2"
                                    else -> "LaserFence4M"
                                }
                                val mesh = gateLoader.loadObject(slug)
                                mesh.position.set(obj.x, obj.y, obj.z)
                                mesh.rotation.y = obj.yaw
                                context.scene.add(mesh)

                                gates.fences.add(
                                    FieldGates.Gate(
                                        meshes = listOf(mesh),
                                        mixers = emptyList(),
                                        openActions = emptyList(),
                                        doorId = obj.doorId,
                                        x = obj.x, y = obj.y, z = obj.z,
                                        halfWidth = FieldGates.FENCE_HALF_WIDTH,
                                        yaw = obj.yaw,
                                        hideOnOpen = true,
                                    )
                                )
                            }

                            SpawnObject.TYPE_FOREST_SWITCH,
                            SpawnObject.TYPE_LASER_FENCE_SWITCH,
                            SpawnObject.TYPE_SWITCH_NONE_DOOR,
                            SpawnObject.TYPE_CAVE_FLOOR_PANEL,
                            SpawnObject.TYPE_MINE_FLOOR_PANEL,
                            SpawnObject.TYPE_RUINS_SWITCH,
                            SpawnObject.TYPE_RUINS_DOOR_SWITCH,
                            SpawnObject.TYPE_RUINS_FENCE_SWITCH,
                            -> switchPlacements.add(obj)

                            // The Caves' doors. All three models are plain gates keyed by door
                            // ID: the wave script opens the ordinary ones, a set of floor
                            // panels opens a four-button door, and the switch doors carry IDs
                            // no event names -- the safety net below spawns those open.
                            SpawnObject.TYPE_CAVE_DOOR,
                            SpawnObject.TYPE_CAVE_SWITCH_DOOR,
                            SpawnObject.TYPE_CAVE_4_BUTTON_DOOR,
                            SpawnObject.TYPE_MINE_DOOR,
                            SpawnObject.TYPE_MINE_SWITCH_DOOR,
                            SpawnObject.TYPE_MINE_4_BUTTON_DOOR,
                            SpawnObject.TYPE_RUINS_DOOR_A1,
                            SpawnObject.TYPE_RUINS_DOOR_A2,
                            SpawnObject.TYPE_RUINS_DOOR_A3,
                            SpawnObject.TYPE_RUINS_4_BUTTON_DOOR,
                            SpawnObject.TYPE_RUINS_2_BUTTON_DOOR,
                            -> {
                                // Both twins are loaded: the red one is what stands there
                                // while the door is locked, the green one takes over the
                                // moment it releases. The door never vanishes -- unlocked, it
                                // slides open and shut as you come and go. The Mines and Ruins
                                // run the exact same mechanism on their own models.
                                val slug = when (obj.typeId) {
                                    SpawnObject.TYPE_CAVE_4_BUTTON_DOOR -> "CaveDoor02"
                                    SpawnObject.TYPE_MINE_4_BUTTON_DOOR -> "MineDoor02"
                                    SpawnObject.TYPE_MINE_DOOR,
                                    SpawnObject.TYPE_MINE_SWITCH_DOOR,
                                    -> "MineDoor01"
                                    SpawnObject.TYPE_RUINS_DOOR_A1 -> "RuinsDoor01"
                                    SpawnObject.TYPE_RUINS_DOOR_A2 -> "RuinsDoor02"
                                    SpawnObject.TYPE_RUINS_DOOR_A3 -> "RuinsDoor03"
                                    // The button doors wear the area's own door model.
                                    SpawnObject.TYPE_RUINS_4_BUTTON_DOOR,
                                    SpawnObject.TYPE_RUINS_2_BUTTON_DOOR,
                                    -> when {
                                        mapSlug.startsWith("ruins02") -> "RuinsDoor02"
                                        mapSlug.startsWith("ruins03") -> "RuinsDoor03"
                                        else -> "RuinsDoor01"
                                    }
                                    else -> "CaveDoor01"
                                }

                                val unlockedMesh = gateLoader.loadObject(slug)
                                val lockedMesh = gateLoader.loadObject("${slug}Locked")
                                for (mesh in listOf(unlockedMesh, lockedMesh)) {
                                    mesh.position.set(obj.x, obj.y, obj.z)
                                    mesh.rotation.y = obj.yaw
                                    context.scene.add(mesh)
                                }

                                val gate = FieldGates.Gate(
                                    meshes = listOf(unlockedMesh),
                                    mixers = emptyList(),
                                    openActions = emptyList(),
                                    doorId = obj.doorId,
                                    x = obj.x, y = obj.y, z = obj.z,
                                    halfWidth = FieldGates.DOOR_HALF_WIDTH,
                                    yaw = obj.yaw,
                                    hideOnOpen = false,
                                    lockedMeshes = listOf(lockedMesh),
                                    slideHeight = meshHeightWorld(unlockedMesh)
                                        ?: CAVE_DOOR_SLIDE_FALLBACK,
                                )
                                gates.doors.add(gate)
                                if (obj.typeId == SpawnObject.TYPE_CAVE_4_BUTTON_DOOR ||
                                    obj.typeId == SpawnObject.TYPE_MINE_4_BUTTON_DOOR ||
                                    obj.typeId == SpawnObject.TYPE_RUINS_4_BUTTON_DOOR ||
                                    obj.typeId == SpawnObject.TYPE_RUINS_2_BUTTON_DOOR
                                ) {
                                    buttonDoors.add(obj.doorId to gate)
                                }
                            }

                            // The elemental floor traps: invisible mines that arm and burst.
                            SpawnObject.TYPE_ELEMENTAL_TRAP,
                            SpawnObject.TYPE_MINE_TRAP,
                            SpawnObject.TYPE_LARGE_ELEMENTAL_TRAP,
                            SpawnObject.TYPE_LARGE_ELEMENTAL_TRAP_B,
                            -> spawnFieldTrap(
                                obj,
                                blastScale =
                                    if (obj.typeId == SpawnObject.TYPE_ELEMENTAL_TRAP ||
                                        obj.typeId == SpawnObject.TYPE_MINE_TRAP
                                    ) 1.0 else LARGE_TRAP_BLAST_SCALE,
                            )

                            // The ceiling pillar: hangs at its authored height (paramsF[1],
                            // ~100 units up) over the walkway it guards.
                            SpawnObject.TYPE_RUINS_PILLAR_TRAP -> {
                                val pillar = gateLoader.loadObject("RuinsPillarTrap")
                                val hangY = obj.y + (obj.paramsF.getOrNull(1) ?: 100.0)
                                pillar.position.set(obj.x, hangY, obj.z)
                                pillar.rotation.y = obj.yaw
                                context.scene.add(pillar)
                                fieldPillars.add(
                                    FieldPillar(pillar, obj.x, obj.y, obj.z, hangY)
                                )
                            }

                            // The crystal monument: scenery with presence, nothing more.
                            SpawnObject.TYPE_RUINS_CRYSTAL -> {
                                val crystal = gateLoader.loadObject("RuinsCrystal")
                                crystal.position.set(obj.x, obj.y, obj.z)
                                crystal.rotation.y = obj.yaw
                                context.scene.add(crystal)
                            }

                            // The poison blob's jar: breakable like a crate, but what it
                            // holds is a faceful of venom, not loot.
                            SpawnObject.TYPE_RUINS_POISON_BLOB -> {
                                val jar = gateLoader.loadObject("RuinsBlobJar")
                                jar.position.set(obj.x, obj.y, obj.z)
                                jar.rotation.y = obj.yaw
                                context.scene.add(jar)
                                val extents = boxFootprintExtents(jar)
                                val halfX = extents?.first ?: worldUnit
                                val halfZ = extents?.second ?: worldUnit
                                fieldBoxes.add(
                                    FieldBox(
                                        jar, obj.typeId, obj.x, obj.y, obj.z,
                                        radius = maxOf(halfX, halfZ),
                                        halfX = halfX, halfZ = halfZ, yaw = obj.yaw,
                                        height = extents?.third ?: 0.0,
                                    )
                                )
                            }

                            // A heal ring restores anyone who steps into it, once.
                            SpawnObject.TYPE_HEAL_RING -> {
                                val healSlug = when {
                                    mapSlug.startsWith("mines") -> "MineHealRing"
                                    mapSlug.startsWith("cave") -> "CaveHealRing"
                                    // The forest GSL's kaifuku model, the ring's true look.
                                    else -> "HealRing"
                                }
                                val mesh = gateLoader.loadObject(healSlug)
                                mesh.position.set(obj.x, obj.y, obj.z)
                                mesh.rotation.y = obj.yaw
                                context.scene.add(mesh)
                                healRings.add(Triple(obj.x, obj.y, obj.z))
                            }

                            SpawnObject.TYPE_ENERGY_BARRIER -> {
                                // The decorative posts stand always; the humming beam between
                                // them is the gate. A barrier is door-DRIVEN (its Door ID is
                                // opened by wave events or switches, like a door -- filing
                                // these with the switch-only fences left them permanently shut
                                // and softlocked Forest 1). One authored with no door at all
                                // (-1) drops when its own room's wave chain completes instead.
                                val base = gateLoader.loadObject("EnergyBarrierBase")
                                base.position.set(obj.x, obj.y, obj.z)
                                base.rotation.y = obj.yaw
                                context.scene.add(base)

                                val beam = animatedPart("EnergyBarrier", obj)
                                beam.third.play()

                                val gate = FieldGates.Gate(
                                    meshes = listOf(beam.first),
                                    mixers = listOf(beam.second),
                                    openActions = emptyList(),
                                    doorId = obj.doorId,
                                    x = obj.x, y = obj.y, z = obj.z,
                                    halfWidth = FieldGates.FENCE_HALF_WIDTH,
                                    yaw = obj.yaw,
                                    hideOnOpen = true,
                                )
                                val unassigned =
                                    obj.doorId == 255 || obj.paramsI.getOrNull(0) == -1
                                if (unassigned) {
                                    // Blocking + animation tick ride the fences list (whose
                                    // switch-linking is proximity-based and never reaches
                                    // these); opening is the render loop's section check.
                                    gates.fences.add(gate)
                                    if (layout.events.none { it.section == obj.section }) {
                                        // Same safety net as the doors below: an unassigned
                                        // barrier drops when its own room's wave chain
                                        // completes, but this section runs no events in this
                                        // layout, so no chain can ever complete it. Forest 2's
                                        // third solo table seals its own spawn room this way.
                                        gate.open()
                                    } else {
                                        sectionBarriers.add(gate to obj.section)
                                    }
                                } else {
                                    gates.doors.add(gate)
                                }
                            }

                            SpawnObject.TYPE_RISING_BRIDGE -> {
                                val mesh = gateLoader.loadObject("RisingBridge")
                                mesh.position.set(obj.x, obj.y, obj.z)
                                mesh.rotation.y = obj.yaw
                                context.scene.add(mesh)

                                gates.bridges.add(
                                    FieldGates.Gate(
                                        meshes = listOf(mesh),
                                        mixers = emptyList(),
                                        openActions = emptyList(),
                                        doorId = obj.doorId,
                                        x = obj.x, y = obj.y, z = obj.z,
                                        halfWidth = FieldGates.FENCE_HALF_WIDTH,
                                        yaw = obj.yaw,
                                        hideOnOpen = false,
                                        revealOnOpen = true,
                                        blocking = false,
                                    )
                                )
                            }

                            SpawnObject.TYPE_PLAYER_SET -> {
                                // Two groups per layout: return flag 0 (paramsI[0]) is the
                                // normal entry arrival, flag 1 the set used when coming BACK
                                // from the next area's return pad. Taking whichever came
                                // first in the data spawned the player at the level's exit,
                                // walking Forest 1 backwards. Pick flag 0, slot 0.
                                val slot = (obj.paramsF.getOrNull(0) ?: 0.0).toInt()
                                val returnFlag = obj.paramsI.getOrNull(0) ?: 0
                                val current = authoredSpawn
                                val currentSlot = (current?.paramsF?.getOrNull(0) ?: 99.0).toInt()
                                val currentFlag = current?.paramsI?.getOrNull(0) ?: 99
                                if (current == null ||
                                    returnFlag < currentFlag ||
                                    (returnFlag == currentFlag && slot < currentSlot)
                                ) {
                                    authoredSpawn = obj
                                }
                            }

                            SpawnObject.TYPE_TELEPORTER,
                            SpawnObject.TYPE_RUINS_TELEPORTER,
                            SpawnObject.TYPE_BOSS_TELEPORTER,
                            -> {
                                val isBoss = obj.typeId == SpawnObject.TYPE_BOSS_TELEPORTER
                                val pad = gateLoader.loadObject("TeleporterPad")
                                pad.position.set(obj.x, obj.y, obj.z)
                                pad.rotation.y = obj.yaw
                                context.scene.add(pad)
                                val beam = animatedPart(
                                    if (isBoss) "BossWarp" else "TeleporterPadBeam", obj,
                                )
                                beam.third.play()
                                propMixers.add(beam.second)

                                val destinationFloor = obj.paramsI.getOrNull(0) ?: 0
                                teleporters.add(
                                    Pioneer2Teleporter(
                                        name = if (isBoss) "boss warp" else "area warp",
                                        modelSlug = "",
                                        x = obj.x, y = obj.y, z = obj.z,
                                        rotationYDegrees = 0.0,
                                        destinationMap =
                                            if (isBoss) {
                                                world.phantasmal.web.mobileGame.world
                                                    .BOSS_ARENA_FOR_MAP[mapSlug]
                                                    ?: BOSS_ARENA_PENDING
                                            } else slugForFloor(destinationFloor),
                                    )
                                )
                            }

                            SpawnObject.TYPE_WARP,
                            SpawnObject.TYPE_RUINS_WARP,
                            -> {
                                val pad = gateLoader.loadObject("WarpPad")
                                pad.position.set(obj.x, obj.y, obj.z)
                                pad.rotation.y = obj.yaw
                                context.scene.add(pad)
                                val beam = animatedPart("WarpPadBeam", obj)
                                beam.third.play()
                                propMixers.add(beam.second)

                                teleporters.add(
                                    Pioneer2Teleporter(
                                        name = "warp",
                                        modelSlug = "",
                                        x = obj.x, y = obj.y, z = obj.z,
                                        rotationYDegrees = 0.0,
                                        destX = obj.paramsF.getOrNull(0),
                                        destY = obj.paramsF.getOrNull(1),
                                        destZ = obj.paramsF.getOrNull(2),
                                        destRotationYDegrees =
                                            (obj.paramsI.getOrNull(0) ?: 0) * 360.0 / 65536.0,
                                    )
                                )
                            }

                            SpawnObject.TYPE_RICO_MESSAGE_POD -> {
                                val mesh = gateLoader.loadObject("RicoMessagePod")
                                mesh.position.set(obj.x, obj.y, obj.z)
                                mesh.rotation.y = obj.yaw
                                context.scene.add(mesh)
                                // The pod's client message id (paramsI[2]) resolves against
                                // Rico's transcribed logs -- her own words, area by area.
                                val messageId = obj.paramsI.getOrNull(2) ?: -1
                                fieldInteractables.add(
                                    FieldInteractable(
                                        mesh = mesh,
                                        prompt = "READ",
                                        title = "Rico's Message",
                                        text = RICO_MESSAGES[messageId]
                                            ?: "The capsule flickers -- this recording is too " +
                                            "degraded to play back.",
                                    )
                                )
                            }

                            SpawnObject.TYPE_MONUMENT,
                            SpawnObject.TYPE_PROBE,
                            SpawnObject.TYPE_WEATHER_STATION,
                            -> {
                                val slug = when (obj.typeId) {
                                    SpawnObject.TYPE_MONUMENT -> "Monument"
                                    SpawnObject.TYPE_PROBE -> "Probe"
                                    else -> "WeatherStation"
                                }
                                val mesh = gateLoader.loadObject(slug)
                                mesh.position.set(obj.x, obj.y, obj.z)
                                mesh.rotation.y = obj.yaw
                                context.scene.add(mesh)
                            }
                        }
                    }

                    // Switches second, so fences/doors exist to link against. A fence switch
                    // drops the nearest fence in its section; the plain forest switch opens the
                    // door whose number it carries. (The forest switch's own 3-part model isn't
                    // converted yet, so the fence switch's pedestal stands in for it visually.)
                    val switchDoorIds = mutableSetOf<Int>()

                    // Group each cave/mine floor panel with the four-button door it belongs to.
                    for (panel in switchPlacements) {
                        if (panel.typeId != SpawnObject.TYPE_CAVE_FLOOR_PANEL &&
                            panel.typeId != SpawnObject.TYPE_MINE_FLOOR_PANEL &&
                            panel.typeId != SpawnObject.TYPE_RUINS_SWITCH &&
                            panel.typeId != SpawnObject.TYPE_RUINS_DOOR_SWITCH
                        ) continue
                        val switchId = panel.paramsI.getOrNull(0) ?: panel.doorId
                        // The Caves number their panels doorId+1..4; the Ruins start at the
                        // door's own number (a 2-button door 104 reads switches 104 and 105).
                        val door = buttonDoors.firstOrNull { (doorId, _) ->
                            switchId - doorId in 0..4
                        }
                        if (door != null) {
                            buttonDoorPanels.getOrPut(door.first) { mutableListOf() }.add(panel)
                        }
                    }

                    for (obj in switchPlacements) {
                        // A floor panel is its own kind: the real model, red until stood on.
                        // Cave and Mine panels count toward their four-button door; a Ruins
                        // switch opens the door carrying its own number outright.
                        val panelSlug = when (obj.typeId) {
                            SpawnObject.TYPE_CAVE_FLOOR_PANEL -> "CaveFloorPanel"
                            SpawnObject.TYPE_MINE_FLOOR_PANEL -> "MineFloorPanel"
                            SpawnObject.TYPE_RUINS_SWITCH -> "RuinsFloorPanel"
                            SpawnObject.TYPE_RUINS_DOOR_SWITCH -> "RuinsDoorSwitch"
                            else -> null
                        }
                        if (panelSlug != null) {
                            val switchId = obj.paramsI.getOrNull(0) ?: obj.doorId
                            // Every panel first looks for a button door its number belongs to
                            // -- the Ruins' 2- and 4-button doors count panels exactly like
                            // the Caves' -- and a Ruins panel with no such door simply opens
                            // the door carrying its own number.
                            val door = buttonDoors.firstOrNull { (doorId, _) ->
                                switchId - doorId in 0..4
                            }
                            door?.let { switchDoorIds.add(it.first) }

                            val directDoor =
                                if (door == null &&
                                    (obj.typeId == SpawnObject.TYPE_RUINS_SWITCH ||
                                        obj.typeId == SpawnObject.TYPE_RUINS_DOOR_SWITCH)
                                ) {
                                    switchDoorIds.add(obj.doorId)
                                    gates.doors.find { it.doorId == obj.doorId }
                                } else null

                            // Red until it's stood on, then green -- the same twin-model trick
                            // the doors use. The Ruins' standing switch is one body with no
                            // red variant in the data, so it presses without changing colour.
                            val hasTwin = obj.typeId != SpawnObject.TYPE_RUINS_DOOR_SWITCH
                            val pressedMesh = gateLoader.loadObject(panelSlug)
                            val unpressedMesh =
                                if (hasTwin) gateLoader.loadObject("${panelSlug}Locked")
                                else pressedMesh
                            for (mesh in setOf(pressedMesh, unpressedMesh)) {
                                mesh.position.set(obj.x, obj.y, obj.z)
                                mesh.rotation.y = obj.yaw
                                context.scene.add(mesh)
                            }
                            if (hasTwin) pressedMesh.visible = false

                            gates.switches.add(
                                FieldGates.FloorSwitch(
                                    meshes = listOf(pressedMesh, unpressedMesh),
                                    mixers = emptyList(),
                                    pressActions = emptyList(),
                                    linked = null,
                                    x = obj.x, y = obj.y, z = obj.z,
                                    onPressed = {
                                        if (hasTwin) {
                                            pressedMesh.visible = true
                                            unpressedMesh.visible = false
                                        }
                                        directDoor?.let {
                                            it.open()
                                            showToast("The door grinds open")
                                        }
                                        door?.let { (doorId, gate) ->
                                            val pressed = buttonDoorPressed
                                                .getOrPut(doorId) { mutableSetOf() }
                                            pressed.add(switchId)
                                            val needed =
                                                buttonDoorPanels[doorId]?.size ?: 0
                                            if (pressed.size >= needed) {
                                                gate.open()
                                                showToast("The door grinds open")
                                            } else {
                                                showToast(
                                                    "Panel lit -- ${needed - pressed.size} to go"
                                                )
                                            }
                                        }
                                    },
                                )
                            )
                            continue
                        }

                        val linked = when (obj.typeId) {
                            SpawnObject.TYPE_FOREST_SWITCH -> {
                                switchDoorIds.add(obj.doorId)
                                gates.doors.find { it.doorId == obj.doorId }
                            }

                            // Trips a numbered mechanism rather than a door in front of it:
                            // whatever gate anywhere carries its switch ID -- in the Forest,
                            // the rising bridges.
                            SpawnObject.TYPE_SWITCH_NONE_DOOR -> {
                                val switchId = obj.paramsI.getOrNull(0) ?: obj.doorId
                                switchDoorIds.add(switchId)
                                (gates.bridges + gates.doors + gates.fences)
                                    .find { it.doorId == switchId }
                            }

                            else ->
                                gates.fences
                                    .filter { !it.isOpen }
                                    .minByOrNull {
                                        val dx = it.x - obj.x
                                        val dz = it.z - obj.z
                                        dx * dx + dz * dz
                                    }
                        }

                        if (obj.typeId == SpawnObject.TYPE_RUINS_FENCE_SWITCH) {
                            // The Ruins' fence pedestal switch.
                            val mesh = gateLoader.loadObject("RuinsFenceSwitch").also { m ->
                                m.position.set(obj.x, obj.y, obj.z)
                                m.rotation.y = obj.yaw
                                context.scene.add(m)
                            }
                            gates.switches.add(
                                FieldGates.FloorSwitch(
                                    meshes = listOf(mesh),
                                    mixers = emptyList(),
                                    pressActions = emptyList(),
                                    linked = linked,
                                    x = obj.x, y = obj.y, z = obj.z,
                                )
                            )
                        } else if (obj.typeId == SpawnObject.TYPE_LASER_FENCE_SWITCH) {
                            val base = animatedPart("LaserFenceSwitch", obj)
                            val beam = animatedPart("LaserFenceSwitchBeam", obj)
                            gates.switches.add(
                                FieldGates.FloorSwitch(
                                    meshes = listOf(base.first, beam.first),
                                    mixers = listOf(base.second, beam.second),
                                    pressActions = listOf(base.third, beam.third),
                                    linked = linked,
                                    x = obj.x, y = obj.y, z = obj.z,
                                )
                            )
                        } else {
                            // The forest's own three-part floor switch, now the real model.
                            val parts = listOf(
                                "ForestFloorSwitch", "ForestFloorSwitchButton", "ForestFloorSwitchRing",
                            ).map { slug ->
                                gateLoader.loadObject(slug).also { mesh ->
                                    mesh.position.set(obj.x, obj.y, obj.z)
                                    mesh.rotation.y = obj.yaw
                                    context.scene.add(mesh)
                                }
                            }
                            gates.switches.add(
                                FieldGates.FloorSwitch(
                                    meshes = parts,
                                    mixers = emptyList(),
                                    pressActions = emptyList(),
                                    linked = linked,
                                    x = obj.x, y = obj.y, z = obj.z,
                                )
                            )
                        }
                    }

                    // Safety net: a door neither the wave script nor any switch ever opens would
                    // wall off a route forever, which the real game plainly doesn't do -- spawn
                    // it open rather than guessing what unknown mechanism drives it.
                    val eventDoorIds = layout.events.flatMapTo(mutableSetOf()) { it.doors }

                    for (door in gates.doors) {
                        if (door.doorId !in eventDoorIds && door.doorId !in switchDoorIds) {
                            door.open()
                        }
                    }
                    // Same net for bridges: one no mechanism ever raises would break the route.
                    for (bridge in gates.bridges) {
                        if (bridge.doorId !in eventDoorIds && bridge.doorId !in switchDoorIds) {
                            bridge.open()
                        }
                    }

                    fieldGates = gates
                }

                run {
                    val arcRadius = bSphereRadius * 6.0

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
                    // Bosses, from the same load-test sweep the old flat enemy roster came from --
                    // De Rol Le and Garanz were being dropped into every field area, Forest included.
                    // They belong in their own boss arenas, so nothing spawns them until those are
                    // built out; the loading code stays because it's the only exercise of
                    // EnemyFragment's two sourcing patterns.
                    val multiPartKinds = emptyList<Triple<String, String, String>>()
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

                        val bossWalkMotion =
                            enemyLoader.loadAnimation(slug, walkClipName, bossMeshData.njObject)
                        val bossAttackMotion =
                            enemyLoader.loadAnimation(slug, attackClipName, bossMeshData.njObject)
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
                            Enemy(
                                bossMesh,
                                hp = if (slug == "Garanz") 3 else 30,
                                name = enemyDisplayName(slug),
                                animationMixer = bossMixer,
                                ai = bossAi,
                            )
                        )
                    }
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
                // Teleporter beams -- psov2's own city warp props, each with its looping ring
                // animation (see GSL_OBJECT_SPECS).
                val teleporterObjectLoader = ObjectAssetLoader(assetLoader)

                for (teleporter in PIONEER2_TELEPORTERS) {
                    val beam = teleporterObjectLoader.loadAnimatedObject(teleporter.modelSlug)
                    beam.mesh.position.set(teleporter.x, teleporter.y, teleporter.z)
                    beam.mesh.rotation.y = teleporter.rotationYDegrees * PI / 180.0
                    beam.mesh.asDynamic().scale.set(
                        teleporter.scale, teleporter.scale, teleporter.scale,
                    )

                    // These beams are see-through light effects. Left as-is they render in the
                    // opaque pass and write depth, so the floor drawn behind them fails the depth
                    // test and the ground appears to vanish around the pad. Flagging them
                    // transparent moves them into the transparent pass, and clearing depthWrite
                    // stops them punching a hole in whatever is behind.
                    forEachMaterial(beam.mesh) { material ->
                        material.transparent = true
                        material.depthWrite = false
                        // Authored for additive draw: without it the textures' dark grounds
                        // render as smoky boxes around the light -- the same look the technique
                        // effects had before their alpha was baked.
                        material.asDynamic().blending = AdditiveBlending
                    }

                    context.scene.add(beam.mesh)

                    // 30-frame looping clip. The motion spins the rings about Y, which reads as
                    // a shimmer rather than obvious movement because they're near rotationally
                    // symmetric -- it is genuinely playing, not a no-op.
                    val beamMixer = AnimationMixer(beam.mesh)
                    beamMixer.clipAction(createAnimationClip(beam.njObject, beam.motion)).play()
                    npcMixers.add(beamMixer)
                }

                // Building doors, opened by proximity -- see updateDoors.
                for (door in PIONEER2_DOORS) {
                    val doorData = teleporterObjectLoader.loadAnimatedObject(door.modelSlug)
                    doorData.mesh.position.set(door.x, door.y, door.z)
                    doorData.mesh.rotation.y = door.rotationYDegrees * PI / 180.0
                    context.scene.add(doorData.mesh)

                    val doorMixer = AnimationMixer(doorData.mesh)
                    val action = doorMixer.clipAction(
                        createAnimationClip(doorData.njObject, doorData.motion)
                    )
                    // Held at a playhead this code sets by hand every frame: timeScale 0 stops the
                    // mixer advancing the clip itself, while the action stays active so whatever
                    // pose `time` points at is still applied.
                    action.play()
                    action.timeScale = .0
                    action.time = .0

                    // Trigger on where the door actually appears, not on its transform origin.
                    // These meshes aren't all centred on their own origin (the Medical Center's
                    // is 78 units off along local +Z), so using the raw position would open a
                    // door while the player stands somewhere else entirely.
                    doorData.mesh.geometry.computeBoundingBox()
                    val doorYaw = door.rotationYDegrees * PI / 180.0
                    var triggerX = door.x
                    var triggerZ = door.z

                    doorData.mesh.geometry.boundingBox?.let { bb ->
                        val centreX = (bb.min.x + bb.max.x) / 2
                        val centreZ = (bb.min.z + bb.max.z) / 2
                        triggerX += centreX * cos(doorYaw) + centreZ * sin(doorYaw)
                        triggerZ += -centreX * sin(doorYaw) + centreZ * cos(doorYaw)
                    }

                    doors.add(
                        DoorState(
                            x = triggerX,
                            z = triggerZ,
                            mixer = doorMixer,
                            action = action,
                            openDuration =
                                (doorData.motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE,
                        )
                    )
                }

                val npcLoader = NpcAssetLoader(assetLoader)
                val npcIdleCache = mutableMapOf<Int, NjMotion>()

                // Authored town layout rather than an arbitrary arrangement -- see PIONEER2_NPCS.
                // Only the models that layout actually calls for are loaded; the rest of the
                // converted city roster has no placement and so isn't spawned at all.
                for (npc in PIONEER2_NPCS) {
                    val npcMeshData = npcLoader.loadNpc(npc.modelSlug)
                    val npcMesh = npcMeshData.mesh

                    // The authored Y is used as-is rather than raycasting for ground. Pioneer 2
                    // stacks several walkable surfaces plus a lot of geometry well below the
                    // street, and a downward search happily finds those: snapping put seven of
                    // these NPCs at y=-182.5 and one at y=-425 when the street the player stands
                    // on is y=0. The quest data already says exactly how high each NPC stands
                    // (0 here, or 2.6 for the raised section-30 walkway), and it agrees with the
                    // player's own spawn height, so it is the more trustworthy source.
                    npcMesh.position.set(npc.x, npc.y, npc.z)
                    npcMesh.rotation.y = npc.rotationYDegrees * PI / 180.0
                    context.scene.add(npcMesh)
                    activeNpcs.add(ActiveNpc(npc, npcMesh))

                    // Each NPC gets its own idle from the shared clip set rather than everyone
                    // running the player's -- soldiers hold rifles, counter staff stand hand on
                    // hip, and so on (see PlayerAnimations.Idles). Clips are cached by index so
                    // the repeated ones are only fetched once.
                    val npcIdle = npcIdleCache[npc.idleAnimation]
                        ?: loadPlayerClip(animationPath(npc.idleAnimation))
                            .also { npcIdleCache[npc.idleAnimation] = it }

                    val npcMixer = AnimationMixer(npcMesh)
                    npcMixer.clipAction(createAnimationClip(npcMeshData.njObject, npcIdle)).play()
                    npcMixers.add(npcMixer)
                }
            }

            // The quest layer: the counter's job list, and -- with a job accepted -- the
            // quest's own cast and running script. (The session itself was restored before
            // the world loaded; see setup's opening.)
            questIndex = try {
                loadQuestIndex(assetLoader)
            } catch (e: Throwable) {
                emptyList()
            }
            setupQuestMode()

            val combat = CombatController(mesh.position, bSphereRadius)

            // The equipped weapon's strike zone. PSO states every range against the player's own
            // 1.0-unit cylinder, so this is what the whole table is measured in.
            combat.reach = equipped.effectiveReach
            combat.setAngleDegrees(equipped.angleDegrees)
            combat.maxTargets = equipped.maxTargets

            val playerController = CharacterController(
                    map, mesh.position, bSphereRadius,
                    // The city's doorways are sealed in the authored collision (the real game
                    // warps at them); carve a walk-through at each door.
                    passageZones =
                        if (mapSlug == "pioneer2")
                            PIONEER2_DOORS.map { PassageZone(it.x, it.z, DOOR_PASSAGE_RADIUS) }
                        else emptyList(),
            )
            (facingOverride ?: if (bossEncounter != null) atan2(-spawnX, -spawnZ) else null)
                ?.let { playerController.faceToward(it) }

            player = Player(
                characterClass = appearance.characterClass,
                mesh = mesh,
                controller = playerController,
                animator = animator,
                combat = combat,
                idleMotion = idleMotion,
                walkMotion = walkMotion,
                runMotion = runMotion,
                deadMotion = deadMotion,
                hitMotion = hitMotion,
                blockMotion = blockMotion,
                knockedDownMotion = knockedDownMotion,
                weaponType = equipped,
                attackMotions = attackMotions,
                spawnX = spawnX,
                spawnY = groundY,
                spawnZ = spawnZ,
            )
            restoreProgress(player!!)

            // Arriving in a field area lands on the layout's own authored spawn (Player Set
            // slot 0) unless a debug/spawn override already chose somewhere -- or the player
            // came back through their Telepipe, which arrives beside the pipe instead.
            if (spawnOverride == null && spawnYOverride == null) {
                if (ActiveTelepipe.returnRequested && mapSlug == ActiveTelepipe.fieldMap) {
                    ActiveTelepipe.returnRequested = false
                    val arriveX = ActiveTelepipe.x + TELEPIPE_ARRIVAL_OFFSET
                    val arriveZ = ActiveTelepipe.z
                    val ground =
                        findNearestGroundHeight(map.walkableCollisionObject, arriveX, arriveZ)
                    val px = ground?.first ?: arriveX
                    val py = ground?.second ?: .0
                    val pz = ground?.third ?: arriveZ
                    player!!.controller.teleportTo(px, py, pz, 0.0)
                    player!!.mesh.position.set(px, py, pz)
                    inputManager.targetPosition.copy(player!!.controller.position)
                } else authoredSpawn?.let { spawn ->
                    player!!.controller.teleportTo(spawn.x, spawn.y, spawn.z, spawn.yaw)
                    player!!.mesh.position.set(spawn.x, spawn.y, spawn.z)
                    player!!.mesh.rotation.y = spawn.yaw
                    inputManager.targetPosition.copy(player!!.controller.position)
                }
            }

            // The authentic technique effect sheets (data 7's technic archives), parsed with
            // the same XVM path the enemies use and seeded into the effect-texture cache under
            // technic_* names -- every sprite/quad/particle helper then uses them like any
            // ripped PNG. The seven-frame golden ring drives the cast stamp below.
            if (!isPeacefulHub) {
                try {
                    val technic = parseXvm(
                        assetLoader.loadArrayBuffer("/fx/technic.xvm").cursor(Endianness.Little)
                    ).unwrap()
                    val technicPt = parseXvm(
                        assetLoader.loadArrayBuffer("/fx/technic_pt.xvm").cursor(Endianness.Little)
                    ).unwrap()
                    technic.textures.getOrNull(0)
                        ?.let { effectTextures["technic_fire"] = xvrTextureToThree(it) }
                    technic.textures.getOrNull(1)
                        ?.let { effectTextures["technic_megid"] = xvrTextureToThree(it) }
                    technic.textures.getOrNull(2)
                        ?.let { effectTextures["technic_ice"] = xvrTextureToThree(it) }
                    technic.textures.getOrNull(5)
                        ?.let { effectTextures["technic_bolt"] = xvrTextureToThree(it) }
                    technicRingFrames = technicPt.textures.take(7).map { xvrTextureToThree(it) }
                    technicRingFrames.firstOrNull()?.let { effectTextures["technic_ring"] = it }

                    // effect_nt.xvm: the general effect sheet -- explosions, lightning, the
                    // casting glyphs. Indices read off the fxsheet contact pages.
                    val effectNt = parseXvm(
                        assetLoader.loadArrayBuffer("/fx/effect_nt.xvm").cursor(Endianness.Little)
                    ).unwrap()
                    fun seedNt(name: String, index: Int) {
                        effectNt.textures.getOrNull(index)
                            ?.let { effectTextures[name] = xvrTextureToThree(it) }
                    }
                    seedNt("nt_spark_blue", 18)
                    seedNt("nt_seal_hex", 22)
                    seedNt("nt_plasma", 23)
                    seedNt("nt_explosion_purple", 26)
                    seedNt("nt_explosion_white", 27)
                    seedNt("nt_explosion_gold", 28)
                    seedNt("nt_explosion_cyan", 29)
                    seedNt("nt_dust", 31)
                    seedNt("nt_glyphs", 37)
                    seedNt("nt_circle_gold", 38)
                    seedNt("nt_bolts", 41)
                    seedNt("nt_shard", 48)

                    // bm_eff_ice's crystal model (extracted from the BML): the formations the
                    // ice techniques encase their victims in.
                    iceXjObject = parseXj(
                        assetLoader.loadArrayBuffer("/fx/IceBreak.xj").cursor(Endianness.Little)
                    ).unwrap().firstOrNull()
                } catch (e: Throwable) {
                    console.warn("technic fx archives failed to load: ${e.message}")
                }
            }

            fxSheet?.let { sheet ->
                val xvm = parseXvm(
                    assetLoader.loadArrayBuffer("/fx/$sheet.xvm").cursor(Endianness.Little)
                ).unwrap()
                val perPage = FX_SHEET_COLUMNS * 2
                val start = fxSheetPage * perPage
                console.log("FXSHEET $sheet: ${xvm.textures.size} textures, page $fxSheetPage from $start")
                xvm.textures.drop(start).take(perPage).forEachIndexed { i, xvr ->
                    val plane = Mesh(
                        PlaneGeometry(FX_SHEET_CELL * 0.9, FX_SHEET_CELL * 0.9),
                        MeshBasicMaterial(obj {
                            // Qualified: the bare name resolves to setup's own GameMap local.
                            this.map = xvrTextureToThree(xvr)
                            transparent = true
                            side = DoubleSide
                        }),
                    )
                    plane.rotation.x = -PI / 2
                    plane.position.set(
                        spawnX - FX_SHEET_CELL * (FX_SHEET_COLUMNS / 2.0) + (i % FX_SHEET_COLUMNS) * FX_SHEET_CELL,
                        groundY + 0.5,
                        spawnZ - 12.0 - (i / FX_SHEET_COLUMNS) * FX_SHEET_CELL,
                    )
                    context.scene.add(plane)
                }
            }

            // The standing Telepipe pair, if one is up: the field half where it was opened,
            // the town half beside the Ragol teleporter's dais.
            ActiveTelepipe.fieldMap?.let { pipeMap ->
                when (mapSlug) {
                    pipeMap -> spawnTelepipe(ActiveTelepipe.x, ActiveTelepipe.z, "pioneer2")
                    "pioneer2" ->
                        spawnTelepipe(TELEPIPE_CITY_X, TELEPIPE_CITY_Z, destination = pipeMap)
                    else -> Unit
                }
            }
            refreshMagMesh()
            playerStatusPanel.setTp(player!!.tp, player!!.stats.tp)

            // No action palette in town/lobby hubs -- there's nothing to fight there.
            if (!isPeacefulHub) {
                val palette = addDisposable(
                    ActionPalette(document.body!!, paletteConfig) { action -> performAction(action) }
                )
                actionPalette = palette
                actionBar = addDisposable(
                    ActionBar(document.body!!, barConfig) { action -> performAction(action) }
                )
                photonBlastRadiusSq =
                    PHOTON_BLAST_RADIUS_UNITS * psoUnit(bSphereRadius) *
                        PHOTON_BLAST_RADIUS_UNITS * psoUnit(bSphereRadius)
                palette.setUnusable(
                    GameAction.SPECIAL_ATTACK,
                    equippedItem?.specialAttack == null,
                )

                // Which weapons have a special lives in PSO's item parameter table, which isn't
                // parsed yet, so none do. The hex stays in place and stays assignable -- it just
                // reads as unusable rather than disappearing.
                palette.setUnusable(GameAction.SPECIAL_ATTACK, true)
            }
          } catch (e: Throwable) {
            console.error("Failed to build the world for $mapSlug: ${e.message}")
            console.error(e.stackTraceToString())
            onSetupError?.invoke(e)
          }
        }
    }

    /**
     * Warps the player when they step onto a pad that has a destination. Pads without one (the
     * Main Ragol Teleporter, which leaves Pioneer 2 entirely) are scenery and are skipped.
     *
     * Vertical distance is part of the proximity test, not just x/z: both Principal warps sit at
     * x=0 and the office is directly below the plaza in stage space, so an x/z-only check would
     * treat them as the same spot.
     */
    private fun updateTeleporters(p: Player) {
        if (teleporters.isEmpty()) return

        var onPad = false

        for (teleporter in teleporters) {
            val dx = p.controller.position.x - teleporter.x
            val dy = p.controller.position.y - teleporter.y
            val dz = p.controller.position.z - teleporter.z

            if (dx * dx + dz * dz > TELEPORTER_RADIUS * TELEPORTER_RADIUS) continue
            if (dy > TELEPORTER_HEIGHT_TOLERANCE || dy < -TELEPORTER_HEIGHT_TOLERANCE) continue

            // Pads that leave the map entirely always confirm, exactly like the real game --
            // even a single destination gets its dialog. Stepping on opens it once; stepping
            // off (handled below) closes it.
            val destination = teleporter.destinationMap
            if (destination != null) {
                if (!standingOnTeleporter && !npcDialog.isOpen) {
                    when {
                        teleporter.opensAreaMenu -> openRagolDestinationMenu()
                        destination == BOSS_ARENA_PENDING ->
                            showToast("The boss beyond stirs... (arena not built yet)")
                        destination !in OPEN_MAPS -> showToast("That area hasn't opened yet")
                        else -> openTeleportConfirm(destination, teleporter.isTelepipe)
                    }
                }
                standingOnTeleporter = true
                return
            }

            val destX = teleporter.destX ?: continue
            val destY = teleporter.destY ?: continue
            val destZ = teleporter.destZ ?: continue

            onPad = true

            if (!standingOnTeleporter) {
                val destYaw =
                    teleporter.destRotationYDegrees?.let { it * PI / 180.0 } ?: p.controller.yaw
                p.controller.teleportTo(destX, destY, destZ, destYaw)
                p.mesh.rotation.y = destYaw
                inputManager.targetPosition.copy(p.controller.position)
            }

            break
        }

        // Stepped off every pad this frame: a teleporter-owned dialog goes with it.
        if (standingOnTeleporter && teleporterDialogOpen) npcDialog.close()
        standingOnTeleporter = onPad
    }

    /** Starts the map swap, once. */
    private fun transitionTo(destination: String) {
        if (areaTransitionStarted) return
        areaTransitionStarted = true
        onAreaTransition?.invoke(destination)
    }

    /**
     * Uses a Telepipe: raises the pipe pair -- one here, one in Pioneer 2 -- and records the
     * spot so the return trip arrives beside it (see ActiveTelepipe). Field areas only: town
     * has nowhere to pipe to, and the real game refuses them in boss arenas.
     */
    private fun openTelepipe(p: Player): Boolean {
        if (isPeacefulHub) {
            showToast("You're already in town")
            return false
        }
        if (bossEncounter != null) {
            showToast("The Telepipe fizzles out -- not in here")
            return false
        }
        ActiveTelepipe.open(mapSlug, p.mesh.position.x, p.mesh.position.z)
        spawnTelepipe(p.mesh.position.x, p.mesh.position.z, destination = "pioneer2")
        showToast("Telepipe opened")
        return true
    }

    /**
     * Raises one pipe: the city warp beam, grounded at ([x], [z]), registered as a working
     * teleporter to [destination]. Both halves of a pipe pair come through here -- the field
     * pipe to town, and the town pipe back out.
     */
    private fun spawnTelepipe(x: Double, z: Double, destination: String) {
        MainScope().launch {
            val beam = ObjectAssetLoader(assetLoader).loadAnimatedObject("CityBeam")
            val grounded = walkableCollision?.let { findNearestGroundHeight(it, x, z) }
            val pipeX = grounded?.first ?: x
            val pipeY = grounded?.second ?: player?.mesh?.position?.y ?: .0
            val pipeZ = grounded?.third ?: z
            beam.mesh.position.set(pipeX, pipeY, pipeZ)
            beam.mesh.asDynamic().scale.set(TELEPIPE_SCALE, TELEPIPE_SCALE, TELEPIPE_SCALE)
            forEachMaterial(beam.mesh) { material ->
                material.transparent = true
                material.depthWrite = false
                material.asDynamic().blending = AdditiveBlending
            }
            context.scene.add(beam.mesh)

            val mixer = AnimationMixer(beam.mesh)
            mixer.clipAction(createAnimationClip(beam.njObject, beam.motion)).play()
            npcMixers.add(mixer)

            teleporters.add(
                Pioneer2Teleporter(
                    name = "Telepipe",
                    modelSlug = "",
                    x = pipeX, y = pipeY, z = pipeZ,
                    rotationYDegrees = 0.0,
                    destinationMap = destination,
                    isTelepipe = true,
                )
            )
        }
    }

    /** The single-destination confirm every field teleporter shows, per the real game. */
    private fun openTeleportConfirm(destination: String, viaTelepipe: Boolean = false) {
        teleporterDialogOpen = true
        npcDialog.open(
            NpcDialogState(
                npcName = if (viaTelepipe) "Telepipe" else "Teleporter",
                text = "Transport to ${areaDisplayName(destination)}?",
                rows = listOf(
                    DialogRow("GO", areaDisplayName(destination)) {
                        npcDialog.close()
                        // Leaving town through the return pipe arrives beside the field pipe
                        // -- see the arrival override in setup.
                        if (viaTelepipe && destination != "pioneer2") {
                            ActiveTelepipe.returnRequested = true
                        }
                        transitionTo(destination)
                    },
                ),
            )
        )
    }

    /**
     * Slides each door's clip toward open while the player is within [DOOR_OPEN_RADIUS] and back
     * toward shut once they leave, taking [DOOR_TRAVEL_SECONDS] either way.
     *
     * The clip is scrubbed by hand rather than played: these are single open/close animations, so
     * letting the mixer run one would slam the door open once and leave it there. Driving the
     * playhead means the same clip covers both directions, and a door caught half-open reverses
     * from wherever it is instead of snapping.
     *
     * Distance is measured in XZ only -- the Principal's office sits far below the plaza in stage
     * space, and including Y would just add cost without separating anything that matters here.
     */
    private fun updateDoors(p: Player, deltaTime: Double) {
        if (doors.isEmpty()) return

        val step = deltaTime / DOOR_TRAVEL_SECONDS

        for (door in doors) {
            val dx = p.controller.position.x - door.x
            val dz = p.controller.position.z - door.z
            val near = dx * dx + dz * dz <= DOOR_OPEN_RADIUS * DOOR_OPEN_RADIUS

            val target = if (near) door.openDuration else .0
            val travel = door.openDuration * step

            door.openTime = if (door.openTime < target) {
                (door.openTime + travel).coerceAtMost(target)
            } else {
                (door.openTime - travel).coerceAtLeast(target)
            }

            door.action.time = door.openTime
            door.mixer.update(deltaTime)
        }
    }

    /**
     * Applies [block] to a mesh's material, whether Three.js gave it a single one or an array
     * (`Mesh.material` is `dynamic` precisely because it can be either -- these converted Ninja
     * meshes get one material per texture, so beams do come through as arrays).
     */
    private fun forEachMaterial(mesh: SkinnedMesh, block: (Material) -> Unit) {
        val material = mesh.material

        if (js("Array.isArray")(material) as Boolean) {
            @Suppress("UNCHECKED_CAST")
            for (m in material as Array<Material>) block(m)
        } else {
            block(material as Material)
        }
    }

    /**
     * Gathers the menu's contents and shows it. Everything comes from live state -- the character's
     * own statline, what's actually in their hand, and the rooms the director has seen them walk
     * into -- so nothing here is invented for display.
     */
    private fun openMenu() {
        val p = player ?: return
        val director = roomWaveDirector

        val rooms = director?.allSections.orEmpty().map { section ->
            MenuRoom(
                id = section.id,
                x = section.x,
                z = section.z,
                visited = director?.visitedSections?.contains(section.id) == true,
                current = director?.currentSectionId == section.id,
            )
        }

        gameMenu.open(
            MenuState(
                characterName = characterName,
                characterClass = appearance.characterClass,
                sectionId = appearance.sectionId,
                stats = p.stats,
                hp = p.hp,
                maxHp = p.maxHp,
                // Shown everywhere: a character doesn't stop owning their weapon by walking
                // into town, they just don't walk around with it drawn.
                weaponSlug = weaponSlug,
                weaponType = weaponType(weaponSlug),
                areaName = areaDisplayName(mapSlug),
                rooms = rooms,
                paletteConfig = if (isPeacefulHub) null else paletteConfig,
                barConfig = if (isPeacefulHub) null else barConfig,
                mag = p.mag,
                photonBlast = p.photonBlast.value,
                playerX = p.mesh.position.x,
                playerZ = p.mesh.position.z,
                level = p.level,
                totalExp = p.totalExp,
                toNextLevel = if (p.level >= MAX_LEVEL) 0 else totalExpForLevel(p.level + 1) - p.totalExp,
                meseta = p.meseta,
                tools = ToolType.entries.mapNotNull { t -> p.tools[t]?.let { c -> t to c } },
                weaponsInventory = inventory.toList(),
                treasures = p.treasures.toList(),
                techDisks = p.techDisks.map { "${it.first.uiName} Lv.${it.second}" },
                onUseDisk = { index -> learnDisk(index); openMenu() },
                onUseTool = { tool -> if (useTool(tool)) openMenu() },
                onEquipWeapon = { item -> if (equipFromMenu(item)) openMenu() },
                tp = p.tp,
                inTown = isPeacefulHub,
                availableActions = availableActionsFor(p.characterClass),
                magFeedsLeft = currentMagFeedsLeft(p),
                magNextWindowSeconds = magNextWindowSeconds(p),
                magFormName =
                    if (p.mag.level >= Mag.FIRST_EVOLUTION_LEVEL) firstEvolutionOf(p.characterClass)
                    else "Mag",
                onFeedMag = { tool -> if (feedMag(tool)) openMenu() },
                frameLabel = p.equippedFrame?.displayName,
                frameDetail = p.equippedFrame?.detail,
                barrierLabel = p.equippedBarrier?.displayName,
                barrierDetail = p.equippedBarrier?.detail,
                unitLabels = (0 until (p.equippedFrame?.slots ?: 0)).map { i ->
                    p.equippedUnits.getOrNull(i)?.let { it.uiName to it.detail }
                },
                // Only what fits each slot, in the order the choose-callbacks index.
                frameChoices = p.ownedFrames.map { it.displayName to it.detail },
                barrierChoices = p.ownedBarriers.map { it.displayName to it.detail },
                unitChoices = p.ownedUnits.map { it.uiName to it.detail },
                // Every carried weapon, whatever its class -- choosing one hot-swaps.
                weaponChoices = inventory
                    .map { it.displayName to "${it.atpMin}-${it.atpMin + it.atpSpread} ATP" },
                onChooseFrame = { index -> equipFrameAt(index); openMenu() },
                onChooseBarrier = { index -> equipBarrierAt(index); openMenu() },
                onChooseUnit = { slot, index -> equipUnitAt(slot, index); openMenu() },
                onChooseWeapon = { index ->
                    inventory.getOrNull(index)?.let { if (equipFromMenu(it)) openMenu() }
                },
                shopStock = armsShopStock(p.level),
                onBuyTool = { tool, price -> if (buyTool(tool, price)) openMenu() },
                onBuyWeapon = { tier, price -> if (buyWeapon(tier, price)) openMenu() },
                onSellWeapon = { item -> if (sellWeapon(item)) openMenu() },
                onSellTreasure = { t -> if (sellTreasure(t)) openMenu() },
            )
        )
    }

    /**
     * Uses one carried tool from the Items pane. Returns whether one was consumed -- a mate at
     * full health stays in the pack, and nothing spends TP yet (no techniques), so the fluids
     * wait rather than burn for nothing.
     */
    private fun useTool(tool: ToolType): Boolean {
        val p = player ?: return false
        val count = p.tools[tool] ?: return false

        var used = false
        tool.hpRestored(p.maxHp)?.let { heal ->
            if (p.hp in 1 until p.maxHp) {
                p.hp = (p.hp + heal).coerceAtMost(p.maxHp)
                playerStatusPanel.setHealth(p.hp, p.maxHp)
                used = true
            }
        }
        tool.tpRestored(p.stats.tp)?.let { restore ->
            if (p.stats.tp > 0 && p.tp < p.stats.tp) {
                p.tp = (p.tp + restore).coerceAtMost(p.stats.tp)
                playerStatusPanel.setTp(p.tp, p.stats.tp)
                used = true
            }
        }
        when (tool) {
            ToolType.POWER_MATERIAL -> { p.materialPower++; used = true }
            ToolType.MIND_MATERIAL -> { p.materialMind++; used = true }
            ToolType.HP_MATERIAL -> { p.materialHp++; used = true }
            ToolType.EVADE_MATERIAL -> { p.materialEvade++; used = true }
            ToolType.DEF_MATERIAL -> { p.materialDef++; used = true }
            ToolType.LUCK_MATERIAL -> { p.materialLuck++; used = true }
            ToolType.TP_MATERIAL -> { p.materialTp++; used = true }

            // The grinders: each point is +2 ATP on the weapon in hand, up to its own cap.
            ToolType.MONOGRINDER, ToolType.DIGRINDER, ToolType.TRIGRINDER -> {
                val item = equippedItem
                if (item == null) {
                    showToast("Nothing is equipped to grind")
                } else if (item.grind >= item.tier.maxGrind) {
                    showToast("${item.displayName} can't be ground further")
                } else {
                    val add = when (tool) {
                        ToolType.MONOGRINDER -> 1
                        ToolType.DIGRINDER -> 2
                        else -> 3
                    }
                    val ground = WeaponItem(
                        item.tier,
                        grind = (item.grind + add).coerceAtMost(item.tier.maxGrind),
                        specialAttack = item.specialAttack,
                    )
                    equippedItem = ground
                    showToast("${ground.displayName}!")
                    used = true
                }
            }
            ToolType.TELEPIPE -> used = openTelepipe(p)

            // The status cures. Each spends itself only when there is something to cure --
            // matching the heal items above, which don't burn a Monomate at full health.
            ToolType.ANTIDOTE -> {
                if (p.poisonRemaining > 0) {
                    p.poisonRemaining = 0.0
                    showToast("Poison cured")
                    used = true
                }
            }
            ToolType.ANTIPARALYSIS -> {
                if (p.paralysisRemaining > 0) {
                    p.paralysisRemaining = 0.0
                    showToast("Paralysis cured")
                    used = true
                }
            }
            // The Sol Atomizer clears every status abnormality at once -- the wiki's own advice
            // for humans facing Lilies.
            ToolType.SOL_ATOMIZER -> {
                if (p.poisonRemaining > 0 || p.paralysisRemaining > 0 ||
                    p.confusedRemaining > 0
                ) {
                    p.poisonRemaining = 0.0
                    p.paralysisRemaining = 0.0
                    p.confusedRemaining = 0.0
                    showToast("Status cured")
                    used = true
                }
            }
            else -> Unit
        }

        if (used) {
            if (tool !in UNLIMITED_TOOLS) {
                if (count <= 1) p.tools.remove(tool) else p.tools[tool] = count - 1
            }
            persistProgress()
        }
        return used
    }

    /**
     * Equip from the Items pane or the Equip dropdown. Any weapon class: equipping across
     * classes hot-swaps the model in hand and the whole motion set (see [switchWeaponClass]).
     */
    /** The Tekker's work: the fee, then the reveal. */
    private fun tekkWeapon(item: WeaponItem) {
        val p = player ?: return
        if (p.meseta < TEKKER_FEE) {
            showToast("Not enough Meseta")
            return
        }
        val index = inventory.indexOf(item)
        if (index < 0) return
        p.meseta -= TEKKER_FEE
        val revealed = item.identified()
        inventory[index] = revealed
        persistProgress()
        showToast("It's a ${revealed.displayName}!")
    }

    private fun equipFromMenu(item: WeaponItem): Boolean {
        if (!inventory.remove(item)) return false
        if (item.unidentified) {
            inventory.add(item)
            showToast("It must be appraised by the Tekker first")
            return false
        }
        equippedItem?.let { inventory.add(it) }
        val previousType = player?.weaponType
        equipItem(item)
        if (item.tier.type != previousType || item.tier.modelSlug != weaponSlug) {
            switchWeaponClass(item)
        }
        persistProgress()
        return true
    }

    /**
     * Re-outfits the live character for a weapon of another class: the full motion set (stance,
     * walk, run, swings, everything is per-class in PSO), the attack timing that keys off
     * [Player.weaponType], and the model on the hand bone. Clips all load before anything flips,
     * so a slow fetch can never leave the character half-switched.
     *
     * In town the character walks unarmed (PSO holsters weapons in the hub) and animates from
     * the FIST set, so only the slug is re-pointed -- the next field map draws the new weapon.
     */
    private fun switchWeaponClass(item: WeaponItem) {
        weaponSlug = item.tier.modelSlug
        if (isPeacefulHub) return
        val p = player ?: return

        MainScope().launch {
            val anims = item.tier.type.animations
            val idle = loadPlayerClip(animationPath(anims.idle))
            val walk = loadPlayerClip(animationPath(anims.walk))
            val run = loadPlayerClip(animationPath(anims.run))
            val dead = loadPlayerClip(
                animationPath(anims.death ?: PlayerAnimations.FIST.death!!)
            )
            val hit = loadPlayerClip(animationPath(anims.hit ?: PlayerAnimations.FIST.hit!!))
            val block = loadPlayerClip(
                animationPath(anims.block ?: PlayerAnimations.FIST.block!!)
            )
            val knockedDown = anims.knockedDown?.let { loadPlayerClip(animationPath(it)) }
            val attacks = anims.attacks.map { loadPlayerClip(animationPath(it)) }
            val attachment = Weapon.attach(assetLoader, p.mesh, item.tier.modelSlug)

            // Guard against a stale switch finishing after a newer one started.
            if (equippedItem !== item) {
                attachment.parent?.remove(attachment)
                return@launch
            }

            p.idleMotion = idle
            p.walkMotion = walk
            p.runMotion = run
            p.deadMotion = dead
            p.hitMotion = hit
            p.blockMotion = block
            p.knockedDownMotion = knockedDown
            p.attackMotions = attacks
            p.weaponType = item.tier.type
            p.currentAttackMotion = null
            equippedWeaponAtp = item.tier.type.atp

            weaponAttachment?.let { it.parent?.remove(it) }
            weaponAttachment = attachment
        }
    }

    /** Restores a save's progression onto the freshly spawned player. */
    private fun restoreProgress(p: Player) {
        save?.let { s ->
            restoreFromSave(p, s)
        }
        // A Force starts knowing the roster at level 1 -- a brand-new character no less than
        // a legacy save from before disks existed. Disks only ever raise from here. Everyone
        // else learns from what drops; androids never.
        if (p.techLevels.isEmpty() && professionOf(p.characterClass) == Profession.FORCE) {
            for (technique in Technique.entries) p.techLevels[technique] = 1
        }
    }

    private fun restoreFromSave(p: Player, s: CharacterSave) {
        run {
            p.totalExp = s.totalExp
            p.level = levelForTotalExp(s.totalExp)
            p.meseta = s.meseta
            for ((name, count) in s.tools) {
                ToolType.entries.find { it.name == name }?.let { p.tools[it] = count }
            }
            for (name in s.treasures) {
                val treasure = TreasureType.entries.find { it.name == name }
                when {
                    treasure != null -> p.treasures.add(treasure)
                    // Saves from before the armor system stored these rares as trophies;
                    // they come back as the real equipment they always were.
                    name == "RECOVERY_BARRIER" -> p.ownedBarriers.add(rollBarrier(RECOVERY_BARRIER_SPEC))
                    else -> unitByName(name)?.let { p.ownedUnits.add(it) }
                }
            }
            p.ownedFrames.addAll(s.ownedFrames.mapNotNull { it.toItem() })
            p.ownedBarriers.addAll(s.ownedBarriers.mapNotNull { it.toItem() })
            for (name in s.ownedUnits) unitByName(name)?.let { p.ownedUnits.add(it) }
            p.equippedFrame = s.equippedFrame?.toItem()
            p.equippedBarrier = s.equippedBarrier?.toItem()
            for (name in s.equippedUnits) unitByName(name)?.let { p.equippedUnits.add(it) }
            p.bankFrames.addAll(s.bankFrames.mapNotNull { it.toItem() })
            p.bankBarriers.addAll(s.bankBarriers.mapNotNull { it.toItem() })
            for (name in s.bankUnits) unitByName(name)?.let { p.bankUnits.add(it) }
            if (!s.bankKitGranted) grantBankKit(p)

            // TESTING: keep the never-empty tools topped up, so a fresh load starts stocked.
            for (tool in UNLIMITED_TOOLS) p.tools[tool] = tool.maxStack
            p.materialPower = s.materialPower
            p.materialEvade = s.materialEvade
            p.materialDef = s.materialDef
            p.materialLuck = s.materialLuck
            p.materialTp = s.materialTp
            for ((name, level) in s.techLevels) {
                Technique.entries.find { it.name == name }?.let { p.techLevels[it] = level }
            }
            for (encoded in s.techDisks) {
                val parts = encoded.split(":")
                val technique = Technique.entries.find { it.name == parts.getOrNull(0) }
                val level = parts.getOrNull(1)?.toIntOrNull()
                if (technique != null && level != null) p.techDisks.add(technique to level)
            }
            p.materialMind = s.materialMind
            p.materialHp = s.materialHp
            p.mag = Mag(
                s.magDefExp, s.magPowExp, s.magDexExp, s.magMindExp, s.magSynchro, s.magIq,
                form = s.magForm.ifBlank {
                    // Pre-evolution saves: derive what the old build displayed.
                    val level = (s.magDefExp + s.magPowExp + s.magDexExp + s.magMindExp) / Mag.EXP_PER_LEVEL
                    if (level >= Mag.FIRST_EVOLUTION_LEVEL) firstEvolutionOf(appearance.characterClass)
                    else Mag.BASE_FORM
                },
            )
            p.magFeedsLeft = s.magFeedsLeft
            p.magWindowEndMs = s.magWindowEndMs
            p.bankMeseta = s.bankMeseta
            for ((name, count) in s.bankTools) {
                ToolType.entries.find { it.name == name }?.let { p.bankTools[it] = count }
            }
            p.defeatedBosses.addAll(s.defeatedBosses)
            p.bankWeapons.addAll(s.bankWeapons.mapNotNull { it.toWeaponItem() })
            for (name in s.bankTreasures) {
                TreasureType.entries.find { it.name == name }?.let { p.bankTreasures.add(it) }
            }
            p.hp = p.maxHp
            p.tp = p.stats.tp

            inventory.clear()
            inventory.addAll(s.weapons.mapNotNull { it.toWeaponItem() })
            val savedEquipped = s.equippedWeapon?.toWeaponItem()
            if (savedEquipped != null) {
                equipItem(savedEquipped)
            } else {
                // Characters saved before starter weapons existed get their class's own -- the
                // Force has always been holding that Cane; now the item exists too.
                weaponTierByName(starterWeaponSlug(appearance.characterClass))
                    ?.takeIf { it.type == weaponType(weaponSlug) }
                    ?.let { equipItem(WeaponItem(it, grind = 0, specialAttack = null)) }
            }
        }
        playerStatusPanel.setHealth(p.hp, p.maxHp)
        playerStatusPanel.setLevel(p.level)
    }

    /** Writes the player's whole progression state back into the save. */
    private fun persistProgress() {
        val s = save ?: return
        val p = player ?: return
        onProgress(
            s.copy(
                totalExp = p.totalExp,
                meseta = p.meseta,
                tools = p.tools.entries.associate { it.key.name to it.value },
                weapons = inventory.map { it.toSaved() },
                equippedWeapon = equippedItem?.toSaved(),
                treasures = p.treasures.map { it.name },
                materialPower = p.materialPower,
                materialEvade = p.materialEvade,
                materialDef = p.materialDef,
                materialLuck = p.materialLuck,
                materialTp = p.materialTp,
                techLevels = p.techLevels.entries.associate { it.key.name to it.value },
                techDisks = p.techDisks.map { "${it.first.name}:${it.second}" },
                materialMind = p.materialMind,
                materialHp = p.materialHp,
                magDefExp = p.mag.defExp,
                magPowExp = p.mag.powExp,
                magDexExp = p.mag.dexExp,
                magMindExp = p.mag.mindExp,
                magSynchro = p.mag.synchro,
                magIq = p.mag.iq,
                magForm = p.mag.form,
                magFeedsLeft = p.magFeedsLeft,
                magWindowEndMs = p.magWindowEndMs,
                defeatedBosses = p.defeatedBosses.toList(),
                bankMeseta = p.bankMeseta,
                bankTools = p.bankTools.entries.associate { it.key.name to it.value },
                bankWeapons = p.bankWeapons.map { it.toSaved() },
                bankTreasures = p.bankTreasures.map { it.name },
                ownedFrames = p.ownedFrames.map { it.toSaved() },
                ownedBarriers = p.ownedBarriers.map { it.toSaved() },
                ownedUnits = p.ownedUnits.map { it.name },
                equippedFrame = p.equippedFrame?.toSaved(),
                equippedBarrier = p.equippedBarrier?.toSaved(),
                equippedUnits = p.equippedUnits.map { it.name },
                bankFrames = p.bankFrames.map { it.toSaved() },
                bankBarriers = p.bankBarriers.map { it.toSaved() },
                bankUnits = p.bankUnits.map { it.name },
                bankKitGranted = true,
            )
        )
    }

    /**
     * A one-time outfitting waiting with the checkroom clerk, so the armor system can be tried
     * the moment it exists: a 3-slot Frame wearable at level 1 and a 4-slot Armor for level 4,
     * two barriers, a spread of units, and pocket money. Max rolls -- it's a gift.
     */
    private fun grantBankKit(p: Player) {
        p.bankFrames.add(FrameItem(FRAME_SPECS[0], dfp = 7, evp = 7, slots = 3))
        p.bankFrames.add(FrameItem(FRAME_SPECS[1], dfp = 9, evp = 9, slots = 4))
        p.bankBarriers.add(rollBarrier(BARRIER_SPECS[0]))
        p.bankBarriers.add(rollBarrier(BARRIER_SPECS[1]))
        p.bankUnits.addAll(
            listOf(
                UnitType.KNIGHT_POWER, UnitType.GENERAL_POWER, UnitType.MARKSMAN_ARM,
                UnitType.GENERAL_BODY, UnitType.GENERAL_HP, UnitType.GENERAL_LEGS,
            )
        )
        p.bankMeseta = (p.bankMeseta + 2_000).coerceAtMost(MAX_MESETA)
        showToast("The checkroom is holding a delivery for you")
    }

    /**
     * Everything a kill pays out: the death-clip hold, the species' EXP -- with any level-ups
     * applied on the spot, the gained max HP arriving as healing like the real game -- and the
     * drop roll.
     */
    private fun onEnemyKilled(enemy: Enemy) {
        // Hold the body until its death clip finishes; an enemy with no death clip has
        // duration 0 and is dropped on the next frame. The Dragon's death runs through its
        // own controller -- its generic AI came off when the fight was installed.
        val dragon = dragonFight?.takeIf { it.enemy === enemy }
        if (dragon != null) {
            enemy.dyingRemaining = dragon.deathDuration
            dragon.onDeath()
        } else {
            enemy.dyingRemaining = enemy.ai?.deathDuration ?: .0
            enemy.ai?.onDeath()
        }

        player?.let { p ->
            p.totalExp += enemyStats(enemy.slug).experience
            val newLevel = levelForTotalExp(p.totalExp)
            if (newLevel > p.level) {
                val before = p.maxHp
                p.level = newLevel
                p.hp = (p.hp + (p.maxHp - before)).coerceAtMost(p.maxHp)
                playerStatusPanel.setHealth(p.hp, p.maxHp)
                playerStatusPanel.setLevel(newLevel)
                // TP grows with the level too, so the bar's ceiling moves with it.
                playerStatusPanel.setTp(p.tp, p.stats.tp)
                showToast("LEVEL UP!  Lv.$newLevel")
            }
        }

        // A Lily's last act: it pumps itself so full of venom that it bursts, and anything
        // standing over the corpse wears it.
        if (enemy.slug == "PoisonLily" || enemy.slug == "NarLily") {
            val burst = effectSprite("burst_bright", LILY_BURST_RADIUS_UNITS * 1.4, colorHex = LILY_SPIT_COLOR)
            burst.position.set(
                enemy.mesh.position.x, centerMassHeight(enemy), enemy.mesh.position.z,
            )
            addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 3.0))

            player?.let { p ->
                val dx = p.mesh.position.x - enemy.mesh.position.x
                val dz = p.mesh.position.z - enemy.mesh.position.z
                val reach = LILY_BURST_RADIUS_UNITS * worldUnit
                if (dx * dx + dz * dz <= reach * reach && p.hp > 0) {
                    hurtPlayerFlat(p, LILY_BURST_DAMAGE)
                    applyPoison(p)
                }
            }
        }

        // A Dubchic with its room's Dubwitch still standing doesn't die -- it collapses, and
        // the pod puts it back on its feet shortly. Experience is paid per down (the collapse
        // is real), but the drop waits for the death that sticks.
        if (enemy.slug == "Dubchic" && dubwitchAlive(enemy.section)) {
            enemy.reviveRemaining = DUBCHIC_REVIVE_SECONDS
            persistProgress()
            return
        }

        // Breaking the Dubwitch kills every Dubchic it was keeping alive, on the spot. The
        // ones already lying down simply never get up: their revival check finds no pod.
        if (enemy.slug == "Dubwitch") {
            for (other in enemies) {
                if (other.slug == "Dubchic" && other.section == enemy.section && !other.isDead) {
                    other.hp = 0
                    onEnemyKilled(other)
                }
            }
            showToast("The Dubwitch is destroyed!")
        }

        maybeDrop(enemy)
        persistProgress()
    }

    /** Whether this room's revival pod still stands -- what keeps its Dubchics coming back. */
    private fun dubwitchAlive(section: Int): Boolean =
        section >= 0 && enemies.any {
            it.slug == "Dubwitch" && !it.isDead && it.section == section
        }

    /**
     * Tapping the HUD's PB dial: with a full gauge it arms the blast (the overlay covers the
     * attack palette until fired); tapping again disarms. With the gauge still filling it does
     * nothing -- the dial's pulse is the "ready" signal.
     */
    private fun activatePhotonBlast() {
        val p = player ?: return
        if (p.hp <= 0 || gameMenu.isOpen) return

        if (photonBlastOverlay.isShowing) {
            photonBlastOverlay.hide()
            return
        }

        if (p.photonBlast.isFull && actionPalette != null) {
            photonBlastOverlay.show(startingPhotonBlast(p.characterClass))
        }
    }

    /**
     * Unleashes the blast: the character's own photon-blast clip plays (held like an emote),
     * everything nearby takes the hit, the gauge empties, and the overlay clears so the attack
     * palette returns. The blast itself is a placeholder for real per-Mag blasts (Farlla and
     * friends need their own creature models/effects): flat damage in a radius, with the real
     * game's invulnerability during the animation.
     */
    private fun firePhotonBlast() {
        val p = player ?: return
        if (!p.photonBlast.isFull) return

        photonBlastOverlay.hide()
        p.photonBlast.spend()

        MainScope().launch {
            val clipId = p.weaponType.animations.photonBlast ?: PlayerAnimations.FIST.photonBlast!!
            val motion = emoteMotions.getOrPut(clipId) {
                loadPlayerClip(animationPath(clipId))
            }
            p.emoteMotion = motion
            p.emoteRemaining = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
            p.invulnerableRemaining = p.emoteRemaining
        }

        for (enemy in enemies) {
            if (enemy.isDead) continue
            val dx = enemy.mesh.position.x - p.mesh.position.x
            val dz = enemy.mesh.position.z - p.mesh.position.z
            if (dx * dx + dz * dz > photonBlastRadiusSq) continue

            enemy.hp -= PHOTON_BLAST_DAMAGE
            damageNumbers.showDamage(
                enemy.mesh.position.x, labelHeight(enemy), enemy.mesh.position.z,
                PHOTON_BLAST_DAMAGE, true,
            )

            if (enemy.isDead) {
                onEnemyKilled(enemy)
            } else {
                enemy.ai?.onPushedBack(p.mesh.position)
            }
        }
    }

    // --- Techniques ---

    private class TechProjectile(
        val sprite: Sprite,
        val frames: List<Texture>,
        val dirX: Double,
        val dirZ: Double,
        val power: Int,
        var remaining: Double,
        var age: Double = 0.0,
    )

    private val techProjectiles = mutableListOf<TechProjectile>()

    /** The blueprint-driven effect engine -- see TechniqueFx. Built once worldUnit is known. */
    private var techniqueFx: TechniqueFx? = null

    /** Projectiles that shed a particle trail as they fly, by kind ("foie"/"megid"). */
    private val projectileTrails = HashMap<Sprite, String>()

    /** Megid in flight: rolls its kill on contact rather than dealing damage. */
    private val megidShots = mutableListOf<TechProjectile>()

    // --- Gunfire ---

    /** A round in flight: psov2's own gun_bullet prop, purely visual. */
    private class Bullet(
        val mesh: Object3D,
        val dirX: Double,
        val dirY: Double,
        val dirZ: Double,
        var remaining: Double,
    )

    private val bullets = mutableListOf<Bullet>()

    /**
     * Sends a round down the barrel when a firearm goes off. The shot itself is resolved by the
     * combat system on the swing's contact frame (guns are hitscan here, as in the real game),
     * so this is purely the visual that was missing: without it a Ranger's gun animated but
     * nothing ever came out of it.
     */
    private fun fireBullet(p: Player) {
        MainScope().launch {
            val mesh = ObjectAssetLoader(assetLoader).loadObject("GunBullet")
            val yaw = p.mesh.rotation.y
            val dirX = sin(yaw)
            val dirZ = cos(yaw)

            // The round's own geometry may not be centred on its origin, so placing the origin
            // at the muzzle drew the visible round somewhere else -- it read as coming out well
            // below the gun. Measuring the model and cancelling that offset puts what you can
            // see exactly where the barrel is.
            val muzzleY = p.mesh.position.y + BULLET_MUZZLE_HEIGHT_UNITS * worldUnit
            val visualOffsetY = geometryCenterY(mesh) ?: 0.0

            // The gun is held in the right hand, so the barrel isn't on the character's
            // centre line. Right of facing is forward turned a quarter clockwise -- the mirror
            // of the left-shoulder offset the Mag rides on.
            val rightX = -dirZ
            val rightZ = dirX
            mesh.position.set(
                p.mesh.position.x + dirX * BULLET_MUZZLE_FORWARD_UNITS * worldUnit +
                    rightX * BULLET_MUZZLE_RIGHT_UNITS * worldUnit,
                muzzleY - visualOffsetY,
                p.mesh.position.z + dirZ * BULLET_MUZZLE_FORWARD_UNITS * worldUnit +
                    rightZ * BULLET_MUZZLE_RIGHT_UNITS * worldUnit,
            )
            // Level flight unless there's something to aim at: a round that drifts toward the
            // floor on its own looks like it was dropped, not fired.
            mesh.rotation.set(.0, yaw, .0)

            // The round flies at the lock's aim point -- the focused Dragon part, or head
            // level on an ordinary enemy -- so what you see leave the barrel goes where the
            // reticle says it will.
            val target = focusedTarget?.takeIf { focusTargetValid(it) }
            var flightX = dirX
            var flightY = 0.0
            var flightZ = dirZ
            if (target != null) {
                focusTargetAimPoint(target, reticleAimScratch)
                val dx = reticleAimScratch.x - mesh.position.x
                val dy = reticleAimScratch.y - mesh.position.y
                val dz = reticleAimScratch.z - mesh.position.z
                val length = sqrt(dx * dx + dy * dy + dz * dz)
                if (length > 1e-3) {
                    flightX = dx / length
                    flightY = dy / length
                    flightZ = dz / length
                }
            }

            context.scene.add(mesh)
            bullets.add(Bullet(mesh, flightX, flightY, flightZ, BULLET_LIFETIME_SECONDS))
        }
    }

    /**
     * How tall a model actually stands in the world, its own scale included. Anything that has
     * to sit "at the shoulder" or "above the head" should measure the body rather than trust a
     * figure in PSO units: those are tied to the *hitbox*, which doesn't shrink when a
     * character's proportion sliders make them shorter -- so a unit-based height parked the Mag
     * over a short character's head while looking right on a tall one.
     */
    private fun meshHeightWorld(mesh: Object3D): Double? {
        val geometry = mesh.asDynamic().geometry ?: return null
        if (geometry.boundingBox == null) geometry.computeBoundingBox()
        val box = geometry.boundingBox ?: return null
        val height = ((box.max.y as Double) - (box.min.y as Double)) * (mesh.scale.y)
        return height.takeIf { it > 0.0 }
    }

    /** The model's bounding-box top above its origin (unscaled) -- where the head is. */
    private fun geometryTopY(mesh: Object3D): Double? {
        val geometry = mesh.asDynamic().geometry ?: return null
        if (geometry.boundingBox == null) geometry.computeBoundingBox()
        val box = geometry.boundingBox ?: return null
        return (box.max.y as Double).takeIf { it > 0.0 }
    }

    /** Where a model's visible middle sits relative to its origin, in world units. */
    private fun geometryCenterY(mesh: Object3D): Double? {
        val geometry = mesh.asDynamic().geometry ?: return null
        if (geometry.boundingBox == null) geometry.computeBoundingBox()
        val box = geometry.boundingBox ?: return null
        return ((box.max.y as Double) + (box.min.y as Double)) / 2
    }

    /** Flies the rounds out, clearing them when they reach something or run out of range. */
    private fun updateBullets(deltaTime: Double) {
        val iterator = bullets.iterator()
        while (iterator.hasNext()) {
            val bullet = iterator.next()
            bullet.remaining -= deltaTime

            val step = BULLET_SPEED_UNITS * worldUnit * deltaTime
            bullet.mesh.position.x += bullet.dirX * step
            bullet.mesh.position.y += bullet.dirY * step
            bullet.mesh.position.z += bullet.dirZ * step

            // Stops at whatever it reaches -- a body or a crate -- so rounds don't sail on
            // through the thing that was just shot.
            var stopped = false
            for (enemy in enemies) {
                if (enemy.isDead) continue
                val dx = enemy.mesh.position.x - bullet.mesh.position.x
                val dz = enemy.mesh.position.z - bullet.mesh.position.z
                if (dx * dx + dz * dz <= enemy.hitboxRadius * enemy.hitboxRadius) {
                    stopped = true
                    break
                }
            }
            if (!stopped) {
                for (box in fieldBoxes) {
                    if (box.broken) continue
                    val dx = box.x - bullet.mesh.position.x
                    val dz = box.z - bullet.mesh.position.z
                    if (dx * dx + dz * dz <= box.radius * box.radius) {
                        stopped = true
                        break
                    }
                }
            }

            if (stopped || bullet.remaining <= 0) {
                bullet.mesh.parent?.remove(bullet.mesh)
                iterator.remove()
            }
        }
    }

    /**
     * A short-lived cast visual built from the game's own effect art (assets/skin/effects,
     * ripped from technic.xvm / technic_pt.xvm / effect_nt.xvm): optionally a flipbook, rising
     * and growing, always fading out over its life.
     */
    private class TimedEffect(
        val root: Object3D,
        val duration: Double,
        var remaining: Double,
        val frames: List<Texture> = emptyList(),
        val frameRate: Double = 24.0,
        val riseUnits: Double = 0.0,
        val growPerSecond: Double = 0.0,
        /** Radians per second about Y -- Gifoie's circling fire. */
        val spinPerSecond: Double = 0.0,
    )

    private val techEffects = mutableListOf<TimedEffect>()

    /** A gameplay consequence scheduled mid-spell -- Gifoie's ring reaching each enemy. */
    private class DelayedAction(var remaining: Double, val action: () -> Unit)

    private val delayedTechActions = mutableListOf<DelayedAction>()

    private val effectTextures = mutableMapOf<String, Texture>()

    /** technic_pt.xvm's seven golden ring frames -- the cast stamp every technique fires. */
    private var technicRingFrames: List<Texture> = emptyList()

    /** bm_eff_ice's crystal model, parsed once; every ice burst builds fresh meshes from it. */
    private var iceXjObject: world.phantasmal.psolib.fileFormats.ninja.XjObject? = null

    /**
     * A clutch of the real ice-crystal models bursting out of the ground -- the encasing
     * formations the reference captures show swallowing whole enemies. Each crystal is the
     * model at a random yaw and an uneven scale, in an additive frost blue.
     */
    private fun spawnIceCrystals(
        x: Double, y: Double, z: Double,
        count: Int,
        radiusWorld: Double,
        scale: Double,
    ) {
        val xj = iceXjObject ?: return
        for (k in 0 until count) {
            val material = MeshBasicMaterial(obj {
                color = Color(ICE_CRYSTAL_COLOR)
                blending = AdditiveBlending
                transparent = true
                side = DoubleSide
                opacity = 0.85
            }).also { it.depthWrite = false }
            val crystal = ninjaObjectToMesh(xj, emptyList(), defaultMaterial = material)
            val angle = Random.nextDouble() * 2 * PI
            val r = Random.nextDouble() * radiusWorld
            crystal.position.set(x + sin(angle) * r, y, z + cos(angle) * r)
            crystal.rotation.y = Random.nextDouble() * 2 * PI
            val s = scale * (0.7 + Random.nextDouble() * 0.6)
            crystal.scale.set(s, s * (0.8 + Random.nextDouble() * 0.6), s)
            addEffect(
                TimedEffect(
                    crystal, ICE_CRYSTAL_SECONDS, ICE_CRYSTAL_SECONDS,
                    riseUnits = 0.5, growPerSecond = 0.35,
                )
            )
        }
    }

    /** The huge additive blast sphere the Ra-explosions swell into -- the reference's dome. */
    private fun spawnExplosionDome(x: Double, y: Double, z: Double, radiusWorld: Double, colorHex: Int) {
        val dome = Mesh(
            SphereGeometry(1.0, 20, 14),
            MeshBasicMaterial(obj {
                color = Color(colorHex)
                blending = AdditiveBlending
                transparent = true
                opacity = 0.8
            }).also { it.depthWrite = false },
        )
        dome.position.set(x, y, z)
        val start = radiusWorld * 0.35
        dome.scale.set(start, start, start)
        addEffect(TimedEffect(dome, DOME_SECONDS, DOME_SECONDS, growPerSecond = 4.5))
    }

    /** Jagged forks scattered flat on the ground -- lightning crawling away from a strike. */
    private fun spawnLightningCrawl(x: Double, y: Double, z: Double, count: Int, spreadWorld: Double) {
        if ("nt_bolts" !in effectTextures) return
        for (k in 0 until count) {
            val fork = effectGroundQuad(
                "nt_bolts", 7.0, 2.8, Random.nextDouble() * 2 * PI, ZONDE_COLOR,
            )
            fork.position.set(
                x + (Random.nextDouble() - 0.5) * spreadWorld,
                y + 0.35,
                z + (Random.nextDouble() - 0.5) * spreadWorld,
            )
            addEffect(
                TimedEffect(fork, CRAWL_SECONDS + Random.nextDouble() * 0.15, CRAWL_SECONDS)
            )
        }
    }

    /** The column of light Grants brings down on the judged. */
    private fun spawnLightPillar(x: Double, y: Double, z: Double, colorHex: Int) {
        val height = PILLAR_HEIGHT_UNITS * worldUnit
        val pillar = Mesh(
            CylinderGeometry(PILLAR_RADIUS_UNITS * worldUnit, PILLAR_RADIUS_UNITS * worldUnit, height, 18),
            MeshBasicMaterial(obj {
                color = Color(colorHex)
                blending = AdditiveBlending
                transparent = true
                opacity = 0.65
            }).also { it.depthWrite = false },
        )
        pillar.position.set(x, y + height / 2, z)
        addEffect(TimedEffect(pillar, PILLAR_SECONDS, PILLAR_SECONDS))
    }

    /** The down-pointing pyramid markers a debuff hangs over its victims. */
    private fun spawnDebuffMarker(enemy: Enemy, colorHex: Int) {
        val size = DEBUFF_MARKER_UNITS * worldUnit
        val marker = Mesh(
            CylinderGeometry(0.0, size * 0.6, size, 4, 1),
            MeshBasicMaterial(obj {
                color = Color(colorHex)
                blending = AdditiveBlending
                transparent = true
                opacity = 0.9
            }).also { it.depthWrite = false },
        )
        // Cone points DOWN at the victim.
        marker.rotation.x = PI
        marker.position.set(
            enemy.mesh.position.x,
            enemy.mesh.position.y + (if (enemy.visualTop > 0) enemy.visualTop else enemy.visualRadius) + size,
            enemy.mesh.position.z,
        )
        addEffect(
            TimedEffect(marker, DEBUFF_MARKER_SECONDS, DEBUFF_MARKER_SECONDS, spinPerSecond = 3.2)
        )
    }

    private fun effectTexture(name: String): Texture =
        effectTextures.getOrPut(name) { TextureLoader().load("assets/skin/effects/$name.png") }

    /**
     * A camera-facing sprite of one effect texture. Additive blending is what makes the ripped
     * art read as light: the textures' black grounds vanish and the glows stack.
     */
    private fun effectSprite(
        name: String,
        widthUnits: Double,
        heightUnits: Double = widthUnits,
        colorHex: Int = 0xffffff,
    ): Sprite {
        val material = SpriteMaterial(obj {
            map = effectTexture(name)
            color = Color(colorHex)
            blending = AdditiveBlending
            transparent = true
            depthWrite = false
        })
        return Sprite(material).also {
            it.scale.set(widthUnits * worldUnit, heightUnits * worldUnit, 1.0)
        }
    }

    /** A flat ground quad of one effect texture, yawed to [yaw] -- for waves and rings. */
    private fun effectGroundQuad(
        name: String,
        widthUnits: Double,
        lengthUnits: Double,
        yaw: Double,
        colorHex: Int = 0xffffff,
    ): Object3D {
        val plane = Mesh(
            PlaneGeometry(widthUnits * worldUnit, lengthUnits * worldUnit),
            MeshBasicMaterial(obj {
                map = effectTexture(name)
                color = Color(colorHex)
                blending = AdditiveBlending
                transparent = true
                side = DoubleSide
            }).also { it.depthWrite = false },
        )
        plane.rotation.x = -PI / 2
        // Parent-child keeps the lie-flat and face-the-yaw rotations composing predictably.
        return Object3D().also { root ->
            root.rotation.y = yaw
            root.add(plane)
        }
    }

    private fun addEffect(effect: TimedEffect) {
        context.scene.add(effect.root)
        techEffects.add(
            if (!fxSlowMotion) effect
            else TimedEffect(
                effect.root,
                effect.duration * FX_SLOW_FACTOR,
                effect.remaining * FX_SLOW_FACTOR,
                effect.frames,
                effect.frameRate / FX_SLOW_FACTOR,
                effect.riseUnits / FX_SLOW_FACTOR,
                effect.growPerSecond / FX_SLOW_FACTOR,
                effect.spinPerSecond / FX_SLOW_FACTOR,
            )
        )
    }

    /** Foie's burst on connection: the game's own 16-frame flame flipbook. */
    private fun spawnFoieImpact(x: Double, y: Double, z: Double) {
        // effect_nt's gold starburst underneath the flames, blowing outward.
        if ("nt_explosion_gold" in effectTextures) {
            val burst = effectSprite("nt_explosion_gold", 2.2)
            burst.position.set(x, y, z)
            addEffect(TimedEffect(burst, FOIE_BURST_SECONDS, FOIE_BURST_SECONDS, growPerSecond = 4.2))
        }
        val frames = (0 until 16).map { effectTexture("foie_flame_$it") }
        val impact = effectSprite("foie_flame_0", 2.6)
        impact.position.set(x, y, z)
        addEffect(
            TimedEffect(
                impact, FOIE_IMPACT_SECONDS, FOIE_IMPACT_SECONDS,
                frames = frames, frameRate = 16 / FOIE_IMPACT_SECONDS,
            )
        )
    }

    /**
     * The nearest living enemy within focus range -- the weapon's own reach plus a generous
     * margin, so melee locks what's engaging you and a gun locks down its sightline. No facing
     * requirement: locking is what *gives* the facing.
     */
    private fun findFocusTarget(p: Player): FocusTarget? {
        // A caster's engagement range is their techniques, not the cane in their hand -- a
        // Force locks at spell distance the way a Ranger locks down a gun's sightline. And
        // nobody's lock stops at their weapon's edge: the real game's reticle finds targets
        // well before a saber can touch them, which is what lets you pick a fight on your own
        // terms -- hence the floor under every class.
        val engagementReach = maxOf(
            p.weaponType.effectiveReach,
            if (professionOf(p.characterClass) == Profession.FORCE) TECH_FOCUS_RANGE_UNITS else 0.0,
            FOCUS_RANGE_FLOOR_UNITS,
        )
        val rangeWorld = (engagementReach + FOCUS_MARGIN_UNITS) * worldUnit
        var best: FocusTarget? = null
        var bestD2 = Double.MAX_VALUE

        fun consider(target: FocusTarget, x: Double, z: Double, radius: Double) {
            val dx = x - p.mesh.position.x
            val dz = z - p.mesh.position.z
            val d2 = dx * dx + dz * dz
            val reach = rangeWorld + radius
            if (d2 <= reach * reach && d2 < bestD2) {
                best = target
                bestD2 = d2
            }
        }

        // Enemies get a head start in the race for the lock: standing between a crate and a
        // Booma, the fight matters more than the loot. (An earlier strict-nearest pick kept
        // stealing the lock onto scenery mid-combat.) The bias is on the compared distance
        // only; range checks stay honest.
        for (enemy in enemies) {
            if (enemy.isDead || enemy.untargetable) continue
            val dx = enemy.mesh.position.x - p.mesh.position.x
            val dz = enemy.mesh.position.z - p.mesh.position.z
            val d2 = (dx * dx + dz * dz) * FOCUS_ENEMY_BIAS
            val reach = rangeWorld + enemy.hitboxRadius
            if (dx * dx + dz * dz <= reach * reach && d2 < bestD2) {
                best = FocusTarget(enemy = enemy)
                bestD2 = d2
            }
        }
        for (box in fieldBoxes) {
            if (box.broken) continue
            consider(FocusTarget(box = box), box.x, box.z, box.radius)
        }
        for (trap in fieldTraps) {
            if (trap.spent || !trapVisibleTo(p, trap)) continue
            consider(
                FocusTarget(trap = trap),
                trap.x, trap.z, TRAP_MARKER_RADIUS_UNITS * worldUnit,
            )
        }
        for (pickup in pickups) {
            consider(
                FocusTarget(pickup = pickup),
                pickup.mesh.position.x, pickup.mesh.position.z,
                PICKUP_FOCUS_RADIUS_UNITS * worldUnit,
            )
        }
        return best
    }

    /** True while this lock still points at something that exists. */
    private fun focusTargetValid(target: FocusTarget): Boolean = when {
        target.enemy != null -> !target.enemy.isDead && !target.enemy.untargetable
        target.box != null -> !target.box.broken
        target.trap != null -> !target.trap.spent
        target.pickup != null -> target.pickup in pickups
        else -> false
    }

    private fun focusTargetX(target: FocusTarget): Double =
        target.enemy?.mesh?.position?.x ?: target.box?.x ?: target.trap?.x
            ?: target.pickup!!.mesh.position.x

    private fun focusTargetZ(target: FocusTarget): Double =
        target.enemy?.mesh?.position?.z ?: target.box?.z ?: target.trap?.z
            ?: target.pickup!!.mesh.position.z

    /** The lock's aim point and silhouette radius for any target kind -- see [focusAimPoint]. */
    private fun focusTargetAimPoint(target: FocusTarget, out: Vector3): Double = when {
        target.enemy != null -> focusAimPoint(target.enemy, out)
        target.box != null -> {
            out.set(target.box.x, target.box.y + target.box.height * 0.55, target.box.z)
            target.box.radius
        }
        target.trap != null -> {
            out.set(target.trap.x, target.trap.y + TRAP_MARKER_RADIUS_UNITS * worldUnit, target.trap.z)
            TRAP_MARKER_RADIUS_UNITS * worldUnit * 1.4
        }
        else -> {
            val mesh = target.pickup!!.mesh
            out.set(mesh.position.x, mesh.position.y + PICKUP_FOCUS_RADIUS_UNITS * worldUnit, mesh.position.z)
            PICKUP_FOCUS_RADIUS_UNITS * worldUnit
        }
    }

    /**
     * Every attackable target the equipped weapon's sweep would reach right now, nearest
     * first, capped at its target count -- what the extra locks of a sword or shot sit on.
     * Measured with the weapon's own cone from the yaw the swing would actually use (facing
     * the primary lock), so the reticles show exactly what one tap would hit.
     */
    private fun swingTargets(p: Player, primary: FocusTarget): List<FocusTarget> {
        if (p.combat.maxTargets <= 1) return listOf(primary)

        val yaw = atan2(
            focusTargetX(primary) - p.mesh.position.x,
            focusTargetZ(primary) - p.mesh.position.z,
        )
        val forwardX = sin(yaw)
        val forwardZ = cos(yaw)
        val reach = (p.combat.reach + PLAYER_HITBOX_UNITS_FOR_BOXES) * worldUnit
        val angleTan = tan(p.weaponType.angleDegrees * PI / 180.0)

        fun coneDistance(x: Double, z: Double, radius: Double): Double? {
            val dx = x - p.mesh.position.x
            val dz = z - p.mesh.position.z
            val along = dx * forwardX + dz * forwardZ
            if (along < 0 || along > reach + radius) return null
            val lateral = dx * forwardZ - dz * forwardX
            val halfWidth = angleTan * along + radius
            if (lateral < -halfWidth || lateral > halfWidth) return null
            return dx * dx + dz * dz
        }

        val found = mutableListOf<Pair<Double, FocusTarget>>()
        for (enemy in enemies) {
            if (enemy.isDead || enemy.untargetable) continue
            coneDistance(enemy.mesh.position.x, enemy.mesh.position.z, enemy.hitboxRadius)
                ?.let { found.add(it to FocusTarget(enemy = enemy)) }
        }
        for (box in fieldBoxes) {
            if (box.broken) continue
            coneDistance(box.x, box.z, box.radius)?.let { found.add(it to FocusTarget(box = box)) }
        }
        for (trap in fieldTraps) {
            if (trap.spent || !trapVisibleTo(p, trap)) continue
            coneDistance(trap.x, trap.z, TRAP_MARKER_RADIUS_UNITS * worldUnit)
                ?.let { found.add(it to FocusTarget(trap = trap)) }
        }

        val primaryIdentity = { t: FocusTarget ->
            t.enemy === primary.enemy && t.box === primary.box && t.trap === primary.trap &&
                t.pickup === primary.pickup
        }
        val extras = found.sortedBy { it.first }.map { it.second }.filterNot(primaryIdentity)
        return (listOf(primary) + extras).take(p.combat.maxTargets)
    }

    /** Snaps the character to face the focus target; returns its yaw, or null with no lock. */
    private fun faceFocusTarget(p: Player): Double? {
        val target = focusedTarget?.takeIf { focusTargetValid(it) } ?: return null
        val dx = focusTargetX(target) - p.mesh.position.x
        val dz = focusTargetZ(target) - p.mesh.position.z
        val yawToTarget = atan2(dx, dz)
        p.mesh.rotation.y = yawToTarget
        p.controller.faceToward(yawToTarget)
        return yawToTarget
    }

    /**
     * Projects the reticle over the lock's aim point (see [focusAimPoint]) and squeezes the
     * darts down so their tips just clear the target's silhouette: the lock's world radius is
     * projected into pixels by measuring a second point one radius to the camera's right.
     */
    private fun updateFocusReticle() {
        val p = player
        val primary = focusedTarget?.takeIf { focusTargetValid(it) }
        if (primary == null || p == null || p.hp <= 0 || gameMenu.isOpen) {
            for (reticle in reticlePool) reticle.root.style.display = "none"
            return
        }

        // The primary lock, plus one lock per extra target a sweeping weapon would reach.
        val targets =
            if (primary.attackable) swingTargets(p, primary)
            else listOf(primary)

        var shown = 0
        for (target in targets) {
            if (drawReticleAt(shown, target)) shown++
        }
        for (index in shown until reticlePool.size) {
            reticlePool[index].root.style.display = "none"
        }
    }

    /** Projects one lock onto [target]; false if it's behind the camera. */
    private fun drawReticleAt(index: Int, target: FocusTarget): Boolean {
        val reticle = reticleElement(index)
        val radiusWorld = focusTargetAimPoint(target, reticleAimScratch)
        reticleProjection.copy(reticleAimScratch)
        reticleProjection.project(context.camera)
        if (reticleProjection.z > 1.0) {
            reticle.root.style.display = "none"
            return false
        }
        val x = (reticleProjection.x + 1) / 2 * context.canvas.clientWidth
        val y = (1 - reticleProjection.y) / 2 * context.canvas.clientHeight

        // One lock-radius along the camera's own right vector (matrixWorld's first column),
        // projected the same way: the pixel distance between the two is the on-screen radius.
        val elements = context.camera.matrixWorld.asDynamic().elements
        reticleSideScratch.set(
            reticleAimScratch.x + (elements[0] as Double) * radiusWorld,
            reticleAimScratch.y + (elements[1] as Double) * radiusWorld,
            reticleAimScratch.z + (elements[2] as Double) * radiusWorld,
        )
        reticleSideScratch.project(context.camera)
        val sideX = (reticleSideScratch.x + 1) / 2 * context.canvas.clientWidth
        val sideY = (1 - reticleSideScratch.y) / 2 * context.canvas.clientHeight
        val dx = sideX - x
        val dy = sideY - y
        val radiusPx = (sqrt(dx * dx + dy * dy) + RETICLE_GAP_PX)
            .coerceIn(RETICLE_MIN_RADIUS_PX, RETICLE_MAX_RADIUS_PX)

        for ((corner, ux, uy) in reticle.corners) {
            corner.style.left = "${ux * radiusPx}px"
            corner.style.top = "${uy * radiusPx}px"
        }

        reticle.root.style.display = "block"
        reticle.root.style.left = "${x}px"
        reticle.root.style.top = "${y}px"
        return true
    }

    /** A player or NPC clip, parsed against the player skeleton's real bone count. */
    private suspend fun loadPlayerClip(path: String): NjMotion =
        animationAssetLoader.loadAnimation(path, playerBoneCount)

    /** One tap on any hex -- the cluster's four or the bar's nine. */
    private fun performAction(action: GameAction) {
        when {
            action == GameAction.NORMAL_ATTACK -> swing(AttackType.NORMAL)
            action == GameAction.HEAVY_ATTACK -> swing(AttackType.HEAVY)
            action == GameAction.SPECIAL_ATTACK -> swing(AttackType.SPECIAL)
            action == GameAction.CHAT || action == GameAction.EMOTE -> openChat()
            action.technique != null -> castTechnique(action.technique)
            action.tool != null -> useToolInField(action.tool)
        }
    }

    /** Bar-tap tool use: the Items pane's effect with field-appropriate feedback. */
    private fun useToolInField(tool: ToolType) {
        val p = player ?: return
        if (p.hp <= 0 || gameMenu.isOpen) return
        if ((p.tools[tool] ?: 0) <= 0) {
            showToast("No ${tool.uiName} in the pack")
            return
        }
        if (useTool(tool)) {
            showToast("${tool.uiName}  (${p.tools[tool] ?: 0} left)")
        } else {
            showToast("It would do nothing right now")
        }
    }

    /**
     * What the nine bar slots hold before the player edits them: Forces get their techniques
     * within thumb's reach, everyone gets the field tools.
     */
    private fun defaultBarActions(): List<GameAction> =
        if (professionOf(appearance.characterClass) == Profession.FORCE) listOf(
            GameAction.FOIE, GameAction.BARTA, GameAction.ZONDE, GameAction.RESTA,
            GameAction.USE_MONOMATE, GameAction.USE_MONOFLUID, GameAction.USE_DIFLUID,
            GameAction.USE_ANTIDOTE, GameAction.CHAT,
        ) else listOf(
            GameAction.USE_MONOMATE, GameAction.USE_DIMATE, GameAction.USE_TRIMATE,
            GameAction.USE_MONOFLUID, GameAction.USE_ANTIDOTE, GameAction.USE_ANTIPARALYSIS,
            GameAction.HEAVY_ATTACK, GameAction.SPECIAL_ATTACK, GameAction.CHAT,
        )

    /**
     * Casts one of the known techniques. Only Forces cast for now -- in the real game the other
     * classes learn from technique disks, and disks don't drop yet, so a hunter tapping a
     * technique hex gets told exactly that. Costs the wiki's TP, deals the wiki's formula.
     */
    private fun castTechnique(technique: Technique) {
        val p = player ?: return
        if (isPeacefulHub) {
            // Silence here read as "spells are broken" -- casting is blocked in town, and the
            // real game says so rather than eating the input.
            showToast("Techniques can't be used on Pioneer 2")
            return
        }
        if (p.hp <= 0 || gameMenu.isOpen) return
        if (isAndroid(p.characterClass)) {
            showToast("Androids can't use techniques")
            return
        }
        val techLevel = p.techLevel(technique)
        if (techLevel < 1) {
            showToast("You haven't learned ${technique.uiName}")
            return
        }

        val cost = technique.tpCost(techLevel)
        if (!freeCasting) {
            if (p.tp < cost) {
                showToast("Not enough TP")
                return
            }
            p.tp -= cost
            playerStatusPanel.setTp(p.tp, p.stats.tp)
        }

        // The weapon set's own casting clip, held like a short emote.
        p.weaponType.animations.cast?.let { clipId ->
            MainScope().launch {
                val motion = emoteMotions.getOrPut(clipId) {
                    loadPlayerClip(animationPath(clipId))
                }
                p.emoteMotion = motion
                p.emoteRemaining = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
            }
        }

        val power = technique.power(techLevel)
        val mst = p.stats.mst
        val yaw = faceFocusTarget(p) ?: p.mesh.rotation.y
        val dirX = sin(yaw)
        val dirZ = cos(yaw)

        // The golden cast ring, stamped at the caster's feet on every technique -- the
        // authentic seven-frame sequence from technic_pt.xvm, expanding as it plays out.
        if (technicRingFrames.isNotEmpty()) {
            val ring = effectGroundQuad("technic_ring", CAST_RING_UNITS, CAST_RING_UNITS, 0.0)
            ring.position.set(
                p.mesh.position.x,
                p.mesh.position.y + 0.25 * worldUnit,
                p.mesh.position.z,
            )
            addEffect(
                TimedEffect(
                    ring, CAST_RING_SECONDS, CAST_RING_SECONDS,
                    frames = technicRingFrames,
                    frameRate = technicRingFrames.size / CAST_RING_SECONDS,
                    growPerSecond = 1.6,
                )
            )
        }

        // The floating cast glyphs -- effect_nt's rune sheet rising beside the caster, the
        // original game's most recognisable casting tell.
        if ("nt_glyphs" in effectTextures) {
            for (side in intArrayOf(-1, 1)) {
                val glyphs = effectSprite("nt_glyphs", 2.2, 3.2, colorHex = CAST_GLYPH_COLOR)
                val angle = p.mesh.rotation.y + side * 1.1
                glyphs.position.set(
                    p.mesh.position.x + sin(angle) * 1.7 * worldUnit,
                    p.mesh.position.y + 1.1 * worldUnit,
                    p.mesh.position.z + cos(angle) * 1.7 * worldUnit,
                )
                addEffect(
                    TimedEffect(glyphs, CAST_GLYPH_SECONDS, CAST_GLYPH_SECONDS, riseUnits = 2.4)
                )
            }
        }

        when (technique) {
            Technique.FOIE -> {
                // The fireball is technic_pt.xvm's own 8-frame orb, cycled in flight.
                val frames = (0 until 8).map { effectTexture("foie_orb_$it") }
                val orb = effectSprite("foie_orb_0", FOIE_SPRITE_UNITS, colorHex = FOIE_COLOR)
                orb.position.set(
                    p.mesh.position.x + dirX * worldUnit,
                    p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit,
                    p.mesh.position.z + dirZ * worldUnit,
                )
                techniqueFx?.foieCore()?.let { orb.add(it) }
                projectileTrails[orb] = "foie"
                context.scene.add(orb)
                techProjectiles.add(
                    TechProjectile(orb, frames, dirX, dirZ, power, FOIE_LIFETIME_SECONDS)
                )
            }

            Technique.ZONDE -> {
                // The focus lock is the promise: whatever the reticle is on is what the bolt
                // hits. Only with no lock does it fall back to searching its own range -- an
                // earlier version searched only 13 units while the lock reached 26, so a
                // perfectly aimed cast on a locked enemy silently did nothing at all.
                val target = focusedEnemy?.takeIf { !it.isDead }
                    ?: enemies.filter { !it.isDead }.minByOrNull {
                        val dx = it.mesh.position.x - p.mesh.position.x
                        val dz = it.mesh.position.z - p.mesh.position.z
                        dx * dx + dz * dz
                    }?.takeIf {
                        val dx = it.mesh.position.x - p.mesh.position.x
                        val dz = it.mesh.position.z - p.mesh.position.z
                        dx * dx + dz * dz <= ZONDE_RANGE_UNITS * worldUnit * ZONDE_RANGE_UNITS * worldUnit
                    }
                if (target == null) {
                    // Nothing alive in reach: the bolt still has to land on something, and a
                    // crate is exactly what a player aiming at one expects it to hit.
                    val nearestBox = fieldBoxes
                        .filter { !it.broken }
                        .minByOrNull {
                            val dx = it.x - p.mesh.position.x
                            val dz = it.z - p.mesh.position.z
                            dx * dx + dz * dz
                        }
                        ?.takeIf {
                            val dx = it.x - p.mesh.position.x
                            val dz = it.z - p.mesh.position.z
                            val reach = ZONDE_RANGE_UNITS * worldUnit
                            dx * dx + dz * dz <= reach * reach
                        }

                    if (nearestBox != null) {
                        techniqueFx?.zonde(nearestBox.x, nearestBox.y, nearestBox.z)
                        val bolt = effectSprite("zonde_bolt", 3.4, 11.0, colorHex = ZONDE_COLOR)
                        bolt.position.set(
                            nearestBox.x,
                            nearestBox.y + 4.0 * worldUnit,
                            nearestBox.z,
                        )
                        addEffect(TimedEffect(bolt, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS))
                        smashBox(nearestBox)
                    }
                }
                if (target != null) {
                    // technic_pt.xvm's lightning bolt, dropped from above the target, with the
                    // effect sheet's starburst at the strike point.
                    val strikeY = centerMassHeight(target)
                    techniqueFx?.zonde(
                        target.mesh.position.x, target.mesh.position.y, target.mesh.position.z,
                    )
                    val bolt = effectSprite("zonde_bolt", 3.4, 11.0, colorHex = ZONDE_COLOR)
                    bolt.position.set(
                        target.mesh.position.x,
                        strikeY + 3.5 * worldUnit,
                        target.mesh.position.z,
                    )
                    addEffect(TimedEffect(bolt, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS))

                    // Lightning crawls away from the strike along the ground.
                    spawnLightningCrawl(
                        target.mesh.position.x, target.mesh.position.y, target.mesh.position.z,
                        count = 4, spreadWorld = 9.0 * worldUnit,
                    )
                    // effect_nt's own forked-lightning sheet crossed over the strike, with its
                    // plasma ball at the point of contact.
                    if ("nt_bolts" in effectTextures) {
                        val forks = effectSprite("nt_bolts", 4.6, 7.0, colorHex = ZONDE_COLOR)
                        forks.position.set(
                            target.mesh.position.x,
                            strikeY + 1.6 * worldUnit,
                            target.mesh.position.z,
                        )
                        addEffect(TimedEffect(forks, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS))
                        val plasma = effectSprite("nt_plasma", 2.8)
                        plasma.position.set(
                            target.mesh.position.x, strikeY, target.mesh.position.z,
                        )
                        addEffect(
                            TimedEffect(
                                plasma, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS,
                                growPerSecond = 2.6,
                            )
                        )
                    }

                    val flash = effectSprite("zonde_flash", 4.2, colorHex = ZONDE_COLOR)
                    flash.position.set(
                        target.mesh.position.x,
                        strikeY,
                        target.mesh.position.z,
                    )
                    addEffect(
                        TimedEffect(flash, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 1.5)
                    )
                    spawnZondeSparks(target.mesh.position.x, strikeY, target.mesh.position.z)

                    hurtEnemy(target, techniqueDamage(power, mst, target.resistances.thunder))
                }
            }

            Technique.BARTA -> {
                techniqueFx?.let { fx ->
                    for (step in 1..4) {
                        val along = step * BARTA_RANGE_UNITS / 4.0 * worldUnit
                        fx.bartaSpike(
                            p.mesh.position.x + dirX * along,
                            p.mesh.position.y,
                            p.mesh.position.z + dirZ * along,
                        )
                    }
                }
                // The freezing line: everything within the wave's length and half-width ahead
                // takes the hit; the wave itself is barta_lv1hontai's own recipe -- forty small
                // shards erupting one after another down the line (spawnIceWake).
                for (enemy in enemies.filter { !it.isDead }) {
                    val dx = enemy.mesh.position.x - p.mesh.position.x
                    val dz = enemy.mesh.position.z - p.mesh.position.z
                    val along = dx * dirX + dz * dirZ
                    val lateral = dx * dirZ - dz * dirX
                    val halfWidth = BARTA_HALF_WIDTH_UNITS * worldUnit + enemy.hitboxRadius
                    if (along in 0.0..(BARTA_RANGE_UNITS * worldUnit) && lateral in -halfWidth..halfWidth) {
                        val burst = effectSprite("barta_burst", 3.2, colorHex = BARTA_COLOR)
                        burst.position.set(
                            enemy.mesh.position.x,
                            centerMassHeight(enemy),
                            enemy.mesh.position.z,
                        )
                        addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS))
                        hurtEnemy(enemy, techniqueDamage(power, mst, enemy.resistances.ice))
                        spawnIceCrystals(
                            enemy.mesh.position.x, enemy.mesh.position.y, enemy.mesh.position.z,
                            count = 2, radiusWorld = enemy.hitboxRadius * 0.8,
                            scale = 0.8 + enemy.hitboxRadius / (8.0 * worldUnit),
                        )
                    }
                }
                breakBoxesInLine(
                    p.mesh.position.x, p.mesh.position.z, dirX, dirZ,
                    BARTA_RANGE_UNITS * worldUnit, BARTA_HALF_WIDTH_UNITS * worldUnit,
                )
                spawnIceWake(
                    p.mesh.position.x, p.mesh.position.y, p.mesh.position.z,
                    dirX, dirZ, BARTA_RANGE_UNITS,
                )
            }

            Technique.RESTA -> {
                val heal = restaHeal(power, mst)
                p.hp = (p.hp + heal).coerceAtMost(p.maxHp)
                playerStatusPanel.setHealth(p.hp, p.maxHp)
                techniqueFx?.supportPulse(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, 0x00ff7f)
                supportRing(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, "resta_ring", RESTA_COLOR)
                spawnHealLights(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, RESTA_COLOR)
                // The reference's green glitter falling around the healed from above.
                for (k in 0 until RESTA_GLINT_COUNT) {
                    spawnParticle(
                        if ("nt_spark_blue" in effectTextures) "nt_spark_blue" else "burst_bright",
                        p.mesh.position.x + (Random.nextDouble() - 0.5) * 7.0,
                        p.mesh.position.y + 8.0 + Random.nextDouble() * 4.0,
                        p.mesh.position.z + (Random.nextDouble() - 0.5) * 7.0,
                        sizeWorld = 1.4,
                        colorHex = RESTA_COLOR,
                        vy = -7.0,
                        seconds = 1.0,
                        delaySeconds = Random.nextDouble() * 0.5,
                    )
                }
            }

            Technique.GIFOIE -> {
                // The fire SPREADS: the spiral starts at the caster's feet and winds outward,
                // wider with every turn, and each enemy burns when the ring actually reaches
                // them -- not all at once inside a fixed circle.
                for (enemy in enemiesWithin(p.mesh.position.x, p.mesh.position.z, GIFOIE_RADIUS_UNITS)) {
                    val dx = enemy.mesh.position.x - p.mesh.position.x
                    val dz = enemy.mesh.position.z - p.mesh.position.z
                    val arrival =
                        sqrt(dx * dx + dz * dz) / (GIFOIE_RADIUS_UNITS * worldUnit) * GIFOIE_SECONDS
                    delayedTechActions.add(DelayedAction(arrival) {
                        if (!enemy.isDead) {
                            hurtEnemy(enemy, techniqueDamage(power, mst, enemy.resistances.fire))
                        }
                    })
                }
                // Three arms of fire spiralling outward for the spell's whole turn: a train of
                // short-lived flames whose orbit radius grows with time, so the eye sees fire
                // travelling AND spreading.
                // The wheel itself is TechniqueFx's orbiters: solid flames visibly circling
                // outward -- one clear visual instead of two competing ones.
                techniqueFx?.gifoie(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z)
            }

            Technique.RAFOIE -> {
                // An explosion on the target. Without one the cast still spends its TP -- the
                // wiki is explicit that this is how Rafoie behaves.
                val target = focusedEnemy?.takeIf { !it.isDead }
                if (target == null) {
                    showToast("Rafoie needs a target")
                } else {
                    val tx = target.mesh.position.x
                    val tz = target.mesh.position.z
                    for (enemy in enemiesWithin(tx, tz, RAFOIE_RADIUS_UNITS)) {
                        hurtEnemy(enemy, techniqueDamage(power, mst, enemy.resistances.fire))
                    }
                    techniqueFx?.rafoie(tx, centerMassHeight(target), tz)
                    spawnFoieImpact(tx, centerMassHeight(target), tz)
                    // The whole blast zone swells as one molten dome -- the reference's giant
                    // orange sphere -- with the starburst inside it.
                    spawnExplosionDome(
                        tx, centerMassHeight(target), tz,
                        RAFOIE_RADIUS_UNITS * worldUnit, FOIE_COLOR,
                    )
                    val burst = effectSprite("burst_orange", RAFOIE_RADIUS_UNITS * 1.6, colorHex = FOIE_COLOR)
                    burst.position.set(tx, centerMassHeight(target), tz)
                    addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 2.0))

                    // The blast throws embers: arcs of flame falling back out of the explosion.
                    for (k in 0 until RAFOIE_EMBER_COUNT) {
                        val angle = Random.nextDouble() * 2 * PI
                        val speed = 10.0 + Random.nextDouble() * 14.0
                        spawnParticle(
                            "foie_flame_0", tx, centerMassHeight(target), tz,
                            sizeWorld = 2.5, colorHex = FOIE_COLOR,
                            vx = cos(angle) * speed,
                            vy = 8.0 + Random.nextDouble() * 10.0,
                            vz = sin(angle) * speed,
                            gravity = 40.0,
                            seconds = 0.6,
                        )
                    }
                }
            }

            Technique.GIBARTA -> {
                techniqueFx?.gibarta(
                    p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, dirX, dirZ,
                )
                // A freezing breath: wider and shorter than Barta's line, and it can freeze.
                for (enemy in enemies.filter { !it.isDead }) {
                    val dx = enemy.mesh.position.x - p.mesh.position.x
                    val dz = enemy.mesh.position.z - p.mesh.position.z
                    val along = dx * dirX + dz * dirZ
                    val lateral = dx * dirZ - dz * dirX
                    val halfWidth = GIBARTA_HALF_WIDTH_UNITS * worldUnit + enemy.hitboxRadius
                    if (along in 0.0..(GIBARTA_RANGE_UNITS * worldUnit) &&
                        lateral in -halfWidth..halfWidth
                    ) {
                        hurtEnemy(enemy, techniqueDamage(power, mst, enemy.resistances.ice))
                        if (!enemy.isDead) maybeFreeze(enemy)
                        // The breath leaves its victims encased -- the real crystal model
                        // bursting up around each body caught in the cone.
                        spawnIceCrystals(
                            enemy.mesh.position.x, enemy.mesh.position.y, enemy.mesh.position.z,
                            count = 3, radiusWorld = enemy.hitboxRadius,
                            scale = 1.0 + enemy.hitboxRadius / (6.0 * worldUnit),
                        )
                    }
                }
            }

            Technique.RABARTA -> {
                techniqueFx?.rabarta(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z)
                // Ice bursting in a circle around the caster, freezing what it catches.
                for (enemy in enemiesWithin(p.mesh.position.x, p.mesh.position.z, RABARTA_RADIUS_UNITS)) {
                    hurtEnemy(enemy, techniqueDamage(power, mst, enemy.resistances.ice))
                    if (!enemy.isDead) maybeFreeze(enemy)
                    spawnIceCrystals(
                        enemy.mesh.position.x, enemy.mesh.position.y, enemy.mesh.position.z,
                        count = 3, radiusWorld = enemy.hitboxRadius,
                        scale = 1.0 + enemy.hitboxRadius / (6.0 * worldUnit),
                    )
                    val burst = effectSprite("barta_burst", 3.2, colorHex = BARTA_COLOR)
                    burst.position.set(
                        enemy.mesh.position.x, centerMassHeight(enemy), enemy.mesh.position.z,
                    )
                    addEffect(TimedEffect(burst, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS))
                }
                // Barta's shard wake bent into a circle: a ring of ice racing outward along
                // the ground from the caster's feet.
                for (k in 0 until RABARTA_SHARD_COUNT) {
                    val angle = k * 2 * PI / RABARTA_SHARD_COUNT
                    val speed = RABARTA_RADIUS_UNITS * worldUnit / 0.35
                    spawnParticle(
                        "barta_burst",
                        p.mesh.position.x, p.mesh.position.y + 0.8, p.mesh.position.z,
                        sizeWorld = BARTA_SHARD_SIZE_WORLD * (0.8 + Random.nextDouble() * 0.4),
                        colorHex = BARTA_COLOR,
                        vx = sin(angle) * speed, vz = cos(angle) * speed,
                        seconds = 0.35, growPerSecond = 1.2,
                    )
                }
            }

            Technique.GIZONDE -> {
                // Chain lightning: the lock's target first, then leaping to whatever stands
                // nearest the last struck, up to ten links.
                var current = focusedEnemy?.takeIf { !it.isDead }
                    ?: enemiesWithin(p.mesh.position.x, p.mesh.position.z, ZONDE_RANGE_UNITS)
                        .minByOrNull { centerDistanceSq(it, p) }
                val struck = mutableSetOf<Enemy>()
                var links = 0
                // The chain is drawn as it travels: caster's hand to the first body, then each
                // body to the next -- not bolts out of the sky.
                var fromX = p.mesh.position.x
                var fromY = p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit
                var fromZ = p.mesh.position.z
                val chainPoints = mutableListOf(doubleArrayOf(fromX, fromY, fromZ))
                while (current != null && links < GIZONDE_MAX_TARGETS) {
                    struck.add(current)
                    links++
                    val toX = current.mesh.position.x
                    val toY = centerMassHeight(current)
                    val toZ = current.mesh.position.z
                    boltBetween(fromX, fromY, fromZ, toX, toY, toZ)
                    chainPoints.add(doubleArrayOf(toX, toY, toZ))
                    spawnLightningCrawl(
                        toX, current.mesh.position.y, toZ,
                        count = 2, spreadWorld = 6.0 * worldUnit,
                    )
                    val flash = effectSprite("zonde_flash", 3.4, colorHex = ZONDE_COLOR)
                    flash.position.set(toX, toY, toZ)
                    addEffect(
                        TimedEffect(flash, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 1.2)
                    )
                    fromX = toX
                    fromY = toY
                    fromZ = toZ
                    hurtEnemy(current, techniqueDamage(power, mst, current.resistances.thunder))

                    val from = current
                    current = enemiesWithin(from.mesh.position.x, from.mesh.position.z, GIZONDE_CHAIN_UNITS)
                        .filter { it !in struck && !it.isDead }
                        .minByOrNull {
                            val dx = it.mesh.position.x - from.mesh.position.x
                            val dz = it.mesh.position.z - from.mesh.position.z
                            dx * dx + dz * dz
                        }
                }
                if (links == 0) showToast("Nothing in reach")
                if (chainPoints.size > 1) techniqueFx?.gizonde(chainPoints)

            }

            Technique.RAZONDE -> {
                techniqueFx?.razonde(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z)
                // The storm around the caster -- the one lightning technique that needs no target.
                // The whole zone becomes the reference's crawling lightning field.
                spawnLightningCrawl(
                    p.mesh.position.x, p.mesh.position.y, p.mesh.position.z,
                    count = 10, spreadWorld = RAZONDE_RADIUS_UNITS * worldUnit * 1.6,
                )
                val caught = enemiesWithin(p.mesh.position.x, p.mesh.position.z, RAZONDE_RADIUS_UNITS)
                for (enemy in caught) {
                    val bolt = effectSprite("zonde_bolt", 2.6, 9.0, colorHex = ZONDE_COLOR)
                    bolt.position.set(
                        enemy.mesh.position.x,
                        centerMassHeight(enemy) + 3.0 * worldUnit,
                        enemy.mesh.position.z,
                    )
                    addEffect(TimedEffect(bolt, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS))
                    hurtEnemy(enemy, techniqueDamage(power, mst, enemy.resistances.thunder))
                }
                val flare = effectSprite("flare_blue", RAZONDE_RADIUS_UNITS * 1.4, colorHex = ZONDE_COLOR)
                flare.position.set(
                    p.mesh.position.x,
                    p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit,
                    p.mesh.position.z,
                )
                addEffect(TimedEffect(flare, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS, growPerSecond = 1.8))

                // zonde_tame's release: sparks spiralling up and out as the storm fires.
                for (k in 0 until 12) {
                    val angle = k * PI / 6
                    spawnParticle(
                        "burst_bright",
                        p.mesh.position.x + cos(angle) * 4.0,
                        p.mesh.position.y + 1.0,
                        p.mesh.position.z + sin(angle) * 4.0,
                        sizeWorld = 6.0, colorHex = ZONDE_COLOR,
                        vx = -sin(angle) * SWIRL_SPEED_WORLD, vy = 22.0, vz = cos(angle) * SWIRL_SPEED_WORLD,
                        seconds = 0.5,
                        delaySeconds = k * 0.02,
                    )
                }
            }

            Technique.GRANTS -> {
                val target = focusedEnemy?.takeIf { !it.isDead }
                if (target == null) {
                    showToast("Grants needs a target")
                } else {
                    hurtEnemy(target, techniqueDamage(power, mst, target.resistances.light))
                    techniqueFx?.grants(
                        target.mesh.position.x, target.mesh.position.y, target.mesh.position.z,
                    )
                    // The reference's column of light dropped on the judged.
                    spawnLightPillar(
                        target.mesh.position.x, target.mesh.position.y, target.mesh.position.z,
                        GRANTS_COLOR,
                    )
                    // effect_nt's golden magic circle turning under the judged -- the seal the
                    // real Grants stamps its target with.
                    if ("nt_circle_gold" in effectTextures) {
                        val seal = effectGroundQuad("nt_circle_gold", 3.8, 3.8, 0.0)
                        seal.position.set(
                            target.mesh.position.x,
                            target.mesh.position.y + 0.3 * worldUnit,
                            target.mesh.position.z,
                        )
                        addEffect(
                            TimedEffect(
                                seal, GRANTS_SEAL_SECONDS, GRANTS_SEAL_SECONDS,
                                spinPerSecond = 2.6,
                            )
                        )
                    }
                    // Light gathers before it strikes: rays converging on the mark from a ring.
                    val cy = centerMassHeight(target)
                    for (k in 0 until GRANTS_RAY_COUNT) {
                        val angle = k * 2 * PI / GRANTS_RAY_COUNT
                        val radius = 12.0
                        spawnParticle(
                            "burst_bright",
                            target.mesh.position.x + cos(angle) * radius,
                            cy + 8.0,
                            target.mesh.position.z + sin(angle) * radius,
                            sizeWorld = 4.0, colorHex = GRANTS_COLOR,
                            vx = -cos(angle) * radius / 0.3,
                            vy = -8.0 / 0.3,
                            vz = -sin(angle) * radius / 0.3,
                            seconds = 0.3,
                        )
                    }
                    val flare = effectSprite("flare_gold", 5.0, colorHex = GRANTS_COLOR)
                    flare.position.set(
                        target.mesh.position.x, centerMassHeight(target), target.mesh.position.z,
                    )
                    addEffect(TimedEffect(flare, GRANTS_FLASH_SECONDS, GRANTS_FLASH_SECONDS))
                    val burst = effectSprite("burst_bright", 3.4, colorHex = GRANTS_COLOR)
                    burst.position.set(
                        target.mesh.position.x, centerMassHeight(target), target.mesh.position.z,
                    )
                    addEffect(
                        TimedEffect(burst, GRANTS_FLASH_SECONDS, GRANTS_FLASH_SECONDS, growPerSecond = 1.2)
                    )
                }
            }

            Technique.MEGID -> {
                // The curse of death travels like Foie but is violet, and doesn't wound -- its
                // power is a straight percent chance to kill, resisted by dark (EDK). The orb
                // face is the archive's own violet star sheet where it loaded.
                val megidTexture =
                    if ("technic_megid" in effectTextures) "technic_megid" else "megid_orb"
                val orb = effectSprite(megidTexture, MEGID_SPRITE_UNITS, colorHex = MEGID_COLOR)
                orb.position.set(
                    p.mesh.position.x + dirX * worldUnit,
                    p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit,
                    p.mesh.position.z + dirZ * worldUnit,
                )
                techniqueFx?.megidCore()?.let { orb.add(it) }
                projectileTrails[orb] = "megid"
                context.scene.add(orb)
                megidShots.add(
                    TechProjectile(orb, emptyList(), dirX, dirZ, power, FOIE_LIFETIME_SECONDS)
                )
            }

            Technique.SHIFTA -> {
                p.shiftaBoost = supportBoostFraction(techLevel)
                p.shiftaRemaining = supportDurationSeconds(techLevel)
                techniqueFx?.supportPulse(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, 0xffa500)
                supportRing(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, "ring_red", SHIFTA_COLOR)
                supportSwirl(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, SHIFTA_COLOR)
                showToast("ATP up ${(p.shiftaBoost * 100).toInt()}%")
            }

            Technique.DEBAND -> {
                p.debandBoost = supportBoostFraction(techLevel)
                p.debandRemaining = supportDurationSeconds(techLevel)
                techniqueFx?.supportPulse(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, 0x6495ed)
                supportRing(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, "ring_blue", DEBAND_COLOR)
                supportSwirl(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, DEBAND_COLOR)
                showToast("DFP up ${(p.debandBoost * 100).toInt()}%")
            }

            Technique.JELLEN -> {
                val caught = enemiesWithin(p.mesh.position.x, p.mesh.position.z, SUPPORT_RADIUS_UNITS)
                for (enemy in caught) {
                    enemy.jellenFactor = 1.0 - supportBoostFraction(techLevel)
                    enemy.jellenRemaining = supportDurationSeconds(techLevel)
                    supportRing(
                        enemy.mesh.position.x, enemy.mesh.position.y, enemy.mesh.position.z,
                        "ring_purple", JELLEN_COLOR,
                    )
                    supportSwirl(
                        enemy.mesh.position.x, enemy.mesh.position.y, enemy.mesh.position.z,
                        JELLEN_COLOR,
                    )
                    spawnDebuffMarker(enemy, JELLEN_COLOR)
                }
                if (caught.isEmpty()) showToast("Nothing in reach")
            }

            Technique.ZALURE -> {
                val caught = enemiesWithin(p.mesh.position.x, p.mesh.position.z, SUPPORT_RADIUS_UNITS)
                for (enemy in caught) {
                    enemy.zalureFactor = 1.0 - supportBoostFraction(techLevel)
                    enemy.zalureRemaining = supportDurationSeconds(techLevel)
                    supportRing(
                        enemy.mesh.position.x, enemy.mesh.position.y, enemy.mesh.position.z,
                        "ring_purple", ZALURE_COLOR,
                    )
                    supportSwirl(
                        enemy.mesh.position.x, enemy.mesh.position.y, enemy.mesh.position.z,
                        ZALURE_COLOR,
                    )
                    spawnDebuffMarker(enemy, ZALURE_COLOR)
                }
                if (caught.isEmpty()) showToast("Nothing in reach")
            }

            Technique.ANTI -> {
                // Cures the status set: the Lilies' poison and paralysis, the Mines' confusion.
                p.poisonRemaining = 0.0
                p.paralysisRemaining = 0.0
                p.confusedRemaining = 0.0
                techniqueFx?.supportPulse(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, 0x87ceeb)
                supportRing(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, "resta_ring", RESTA_COLOR)
                spawnHealLights(p.mesh.position.x, p.mesh.position.y, p.mesh.position.z, RESTA_COLOR)
            }

            Technique.REVERSER -> {
                // Revives a fallen ally. There are no allies to fall yet.
                showToast("No one needs reviving")
            }

            Technique.RYUKER -> {
                // The telepipe: a warp home rises where the caster stands.
                openReturnWarp(p.mesh.position.x, p.mesh.position.z)
                showToast("A telepipe opens")
            }
        }
    }

    /**
     * Keeps enemy bodies out of one another. Run once after every enemy has moved, so the whole
     * pack settles together rather than each one resolving against stale positions.
     *
     * Pairwise, which is fine at the handful of live enemies a room holds; if a later area
     * fields far more at once this wants a spatial grid rather than a second look at the
     * approach.
     */
    private fun separateEnemies() {
        for (i in enemies.indices) {
            val a = enemies[i]
            if (a.isDead) continue

            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                if (b.isDead) continue

                val dx = b.mesh.position.x - a.mesh.position.x
                val dz = b.mesh.position.z - a.mesh.position.z
                val minimum = (a.hitboxRadius + b.hitboxRadius) * BODY_SEPARATION_FACTOR
                val distanceSq = dx * dx + dz * dz
                if (distanceSq >= minimum * minimum) continue

                // Exactly co-located: pick an arbitrary axis rather than divide by zero.
                val distance = sqrt(distanceSq)
                val (pushX, pushZ) = if (distance < 1e-6) {
                    1.0 to 0.0
                } else {
                    dx / distance to dz / distance
                }

                // Each gives way by half, so neither body is privileged over the other.
                val overlap = (minimum - distance) / 2
                a.ai?.separate(-pushX * overlap, -pushZ * overlap)
                b.ai?.separate(pushX * overlap, pushZ * overlap)
            }
        }
    }

    // ------------------------------------------------------------------
    //  Particles.
    //
    //  Sized and counted from the game's own particleentry.dat (the player supplied the file):
    //  barta_lv1hontai runs ~40 shards of 2 world units, gibartalv1star 10 crystals at speed
    //  50, zonde_ryuusi 20 sparks of 8-10 units, anti_hikari 7 rising lights. Those figures are
    //  in psov2 world units -- the same space this renderer draws in -- so they are used raw.
    // ------------------------------------------------------------------

    private class Particle(
        val sprite: Sprite,
        var vx: Double, var vy: Double, var vz: Double,
        /** World units per second squared, straight down. */
        val gravity: Double,
        val duration: Double,
        var remaining: Double,
        val growPerSecond: Double,
        val frames: List<Texture> = emptyList(),
        val frameRate: Double = 0.0,
        /** Seconds to wait unseen before living -- sequential eruption sells a wake. */
        var delay: Double = 0.0,
        var age: Double = 0.0,
    )

    private val particles = mutableListOf<Particle>()

    private fun spawnParticle(
        texture: String,
        x: Double, y: Double, z: Double,
        sizeWorld: Double,
        colorHex: Int,
        vx: Double = 0.0, vy: Double = 0.0, vz: Double = 0.0,
        gravity: Double = 0.0,
        seconds: Double = 0.6,
        growPerSecond: Double = 0.0,
        frames: List<Texture> = emptyList(),
        frameRate: Double = 0.0,
        delaySeconds: Double = 0.0,
    ) {
        val material = SpriteMaterial(obj {
            map = effectTexture(texture)
            color = Color(colorHex)
            blending = AdditiveBlending
            transparent = true
            depthWrite = false
        })
        val sprite = Sprite(material)
        sprite.scale.set(sizeWorld, sizeWorld, 1.0)
        sprite.position.set(x, y, z)
        sprite.visible = delaySeconds <= 0
        context.scene.add(sprite)
        particles.add(
            Particle(
                sprite, vx, vy, vz, gravity, seconds, seconds, growPerSecond, frames, frameRate,
                delay = delaySeconds,
            )
        )
    }

    private fun updateParticles(deltaTime: Double) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            if (particle.delay > 0) {
                particle.delay -= deltaTime
                if (particle.delay > 0) continue
                particle.sprite.visible = true
            }
            particle.remaining -= deltaTime
            if (particle.remaining <= 0) {
                particle.sprite.parent?.remove(particle.sprite)
                iterator.remove()
                continue
            }
            particle.age += deltaTime
            particle.vy -= particle.gravity * deltaTime
            particle.sprite.position.x += particle.vx * deltaTime
            particle.sprite.position.y += particle.vy * deltaTime
            particle.sprite.position.z += particle.vz * deltaTime
            if (particle.growPerSecond != 0.0) {
                val f = 1.0 + particle.growPerSecond * deltaTime
                particle.sprite.scale.set(
                    particle.sprite.scale.x * f, particle.sprite.scale.y * f, 1.0,
                )
            }
            if (particle.frames.isNotEmpty()) {
                val frame = (particle.age * particle.frameRate).toInt() % particle.frames.size
                particle.sprite.material.map = particle.frames[frame]
            }
            particle.sprite.material.opacity = (particle.remaining / particle.duration).coerceIn(0.0, 1.0)
        }
    }

    /**
     * A lightning segment drawn *between* two points -- Gizonde's chain is enemy to enemy, not
     * bolts from the sky. A vertical plane stretched along the line, double-sided and additive.
     */
    private fun boltBetween(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
    ) {
        val dx = x2 - x1
        val dz = z2 - z1
        val length = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        val plane = Mesh(
            PlaneGeometry(length, BOLT_THICKNESS_WORLD),
            MeshBasicMaterial(obj {
                map = effectTexture("zonde_bolt")
                color = Color(ZONDE_COLOR)
                blending = AdditiveBlending
                transparent = true
                side = DoubleSide
            }).also { it.depthWrite = false },
        )
        plane.position.set((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2)
        plane.rotation.y = atan2(dx, dz) - PI / 2
        addEffect(TimedEffect(plane, TECH_FLASH_SECONDS, TECH_FLASH_SECONDS))
    }

    /** The orbit swirl the support casts wrap a body in -- six motes climbing a helix. */
    private fun supportSwirl(x: Double, baseY: Double, z: Double, colorHex: Int) {
        for (k in 0 until 6) {
            val angle = k * PI / 3
            spawnParticle(
                "burst_bright", x + cos(angle) * SWIRL_RADIUS_WORLD, baseY, z + sin(angle) * SWIRL_RADIUS_WORLD,
                sizeWorld = 4.0, colorHex = colorHex,
                vx = -sin(angle) * SWIRL_SPEED_WORLD, vy = SWIRL_RISE_WORLD, vz = cos(angle) * SWIRL_SPEED_WORLD,
                seconds = 0.9,
            )
        }
    }

    /**
     * Barta's wake the way barta_lv1hontai builds it: forty two-unit shards erupting one after
     * another down the line -- ice racing along the ground, not a glow strip hovering over it.
     */
    private fun spawnIceWake(
        px: Double, py: Double, pz: Double,
        dirX: Double, dirZ: Double,
        rangeUnits: Double,
    ) {
        val rangeWorld = rangeUnits * worldUnit
        for (k in 0 until BARTA_SHARD_COUNT) {
            val t = (k + 1).toDouble() / BARTA_SHARD_COUNT
            val lateral = (Random.nextDouble() - 0.5) * 2.5
            spawnParticle(
                // The real ice-crystal sheet where the archive loaded; the ripped burst if not.
                if ("technic_ice" in effectTextures) "technic_ice" else "barta_burst",
                px + dirX * rangeWorld * t + dirZ * lateral,
                py + 0.8,
                pz + dirZ * rangeWorld * t - dirX * lateral,
                sizeWorld = BARTA_SHARD_SIZE_WORLD * (0.8 + Random.nextDouble() * 0.4),
                colorHex = BARTA_COLOR,
                vy = 3.0,
                seconds = BARTA_SHARD_LIFE_SECONDS,
                growPerSecond = 1.2,
                delaySeconds = t * BARTA_WAVE_TRAVEL_SECONDS,
            )
            // Splinters of real ice kicked out of the wave as it passes.
            if ("nt_shard" in effectTextures && k % 2 == 0) {
                spawnParticle(
                    "nt_shard",
                    px + dirX * rangeWorld * t + dirZ * lateral,
                    py + 0.6,
                    pz + dirZ * rangeWorld * t - dirX * lateral,
                    sizeWorld = 1.5,
                    colorHex = 0xdff4ff,
                    vx = dirZ * (Random.nextDouble() - 0.5) * 14.0,
                    vy = 8.0 + Random.nextDouble() * 5.0,
                    vz = -dirX * (Random.nextDouble() - 0.5) * 14.0,
                    gravity = 34.0,
                    seconds = 0.55,
                    delaySeconds = t * BARTA_WAVE_TRAVEL_SECONDS,
                )
            }
        }
    }

    /** zonde_ryuusi's streaming sparks: twenty glows flung out of the strike point. */
    private fun spawnZondeSparks(x: Double, y: Double, z: Double) {
        for (k in 0 until ZONDE_SPARK_COUNT) {
            val angle = Random.nextDouble() * 2 * PI
            val speed = ZONDE_SPARK_SPEED_WORLD * (0.5 + Random.nextDouble() * 0.5)
            spawnParticle(
                "burst_bright", x, y, z,
                sizeWorld = ZONDE_SPARK_SIZE_WORLD * (0.8 + Random.nextDouble() * 0.2),
                colorHex = ZONDE_COLOR,
                vx = cos(angle) * speed,
                vy = Random.nextDouble() * 12.0,
                vz = sin(angle) * speed,
                gravity = 40.0,
                seconds = 0.4,
            )
        }
    }

    /** anti_hikari's climb: seven broad lights rising around the body, one beat apart. */
    private fun spawnHealLights(x: Double, baseY: Double, z: Double, colorHex: Int) {
        for (k in 0 until HEAL_LIGHT_COUNT) {
            val angle = k * 2 * PI / HEAL_LIGHT_COUNT
            spawnParticle(
                "burst_bright",
                x + cos(angle) * 2.5, baseY + 1.0, z + sin(angle) * 2.5,
                sizeWorld = HEAL_LIGHT_SIZE_WORLD, colorHex = colorHex,
                vy = HEAL_LIGHT_RISE_WORLD,
                seconds = 0.9,
                delaySeconds = k * 0.05,
            )
        }
    }

    /** An enemy held frozen, with the ice-crystal sprite that shows it. */
    private class FrozenEnemy(val enemy: Enemy, val sprite: Sprite, var remaining: Double)

    private val frozenEnemies = mutableListOf<FrozenEnemy>()

    /**
     * Rolls a freeze from an ice technique. The held-in-place behaviour rides the same
     * status-hold the weapon specials use; the ice over the body is eff_freeze.pvm's own
     * crystal texture ("koori" -- ice), the visual the real game wraps a frozen enemy in.
     */
    private fun maybeFreeze(enemy: Enemy) {
        if (Random.nextDouble() >= ICE_FREEZE_CHANCE) return
        if (frozenEnemies.any { it.enemy === enemy }) return

        enemy.ai?.onStatusHeld(FREEZE_SECONDS)
        val ice = effectSprite("freeze", FREEZE_SPRITE_UNITS)
        ice.material.opacity = 0.7
        ice.position.set(
            enemy.mesh.position.x,
            centerMassHeight(enemy),
            enemy.mesh.position.z,
        )
        context.scene.add(ice)
        frozenEnemies.add(FrozenEnemy(enemy, ice, FREEZE_SECONDS))
    }

    /** Keeps the ice on its enemy and melts it when the hold ends or the enemy dies. */
    private fun updateFreezeOverlays(deltaTime: Double) {
        val iterator = frozenEnemies.iterator()
        while (iterator.hasNext()) {
            val frozen = iterator.next()
            frozen.remaining -= deltaTime
            if (frozen.remaining <= 0 || frozen.enemy.isDead) {
                frozen.sprite.parent?.remove(frozen.sprite)
                iterator.remove()
                continue
            }
            frozen.sprite.position.set(
                frozen.enemy.mesh.position.x,
                centerMassHeight(frozen.enemy),
                frozen.enemy.mesh.position.z,
            )
        }
    }

    private fun centerDistanceSq(enemy: Enemy, p: Player): Double {
        val dx = enemy.mesh.position.x - p.mesh.position.x
        val dz = enemy.mesh.position.z - p.mesh.position.z
        return dx * dx + dz * dz
    }

    /** Every living enemy within [radiusUnits] of a point, for the area techniques. */
    private fun enemiesWithin(x: Double, z: Double, radiusUnits: Double): List<Enemy> {
        val radius = radiusUnits * worldUnit
        return enemies.filter { enemy ->
            if (enemy.isDead || enemy.untargetable) return@filter false
            val dx = enemy.mesh.position.x - x
            val dz = enemy.mesh.position.z - z
            dx * dx + dz * dz <= (radius + enemy.hitboxRadius) * (radius + enemy.hitboxRadius)
        }
    }

    /** A rising ring on a body -- the shared visual language of the support casts. */
    private fun supportRing(x: Double, y: Double, z: Double, texture: String, colorHex: Int) {
        val ring = effectGroundQuad(texture, 3.2, 3.2, 0.0, colorHex)
        ring.position.set(x, y + 0.2 * worldUnit, z)
        addEffect(
            TimedEffect(ring, RESTA_RING_SECONDS, RESTA_RING_SECONDS, riseUnits = 2.2, growPerSecond = 0.6)
        )
    }

    /**
     * A Lily's answer to being shot at: a screech that seizes whoever is doing it. Only fires
     * against attacks from outside its own reach -- close in, the plant is defenceless, which
     * is exactly the trade the wiki describes.
     */
    private fun maybeLilyScreech(enemy: Enemy) {
        if (enemy.slug != "PoisonLily" && enemy.slug != "NarLily") return
        val p = player ?: return
        if (enemy.isDead) return

        val dx = p.mesh.position.x - enemy.mesh.position.x
        val dz = p.mesh.position.z - enemy.mesh.position.z
        val melee = (enemyStats(enemy.slug).attackRange + 1.0) * worldUnit
        if (dx * dx + dz * dz <= melee * melee) return
        if (Random.nextDouble() > LILY_SCREECH_CHANCE) return

        applyParalysis(p)
    }

    /**
     * Learning from a disk: androids never can, and a disk at or below the learned level
     * teaches nothing. Success consumes the disk and the technique casts at its level from
     * then on.
     */
    private fun learnDisk(index: Int) {
        val p = player ?: return
        val (technique, level) = p.techDisks.getOrNull(index) ?: return
        if (isAndroid(p.characterClass)) {
            showToast("Androids can't use technique disks")
            return
        }
        val current = p.techLevel(technique)
        if (level <= current) {
            showToast("${technique.uiName} is already Lv.$current")
            return
        }
        p.techLevels[technique] = level
        p.techDisks.removeAt(index)
        persistProgress()
        showToast("Learned ${technique.uiName} Lv.$level!")
    }

    /** The drop tables level their technique disks to the zone: Forest 1 through Ruins 4. */
    private val areaTier: Int = when {
        mapSlug.startsWith("cave") || mapSlug == "bossArea2" -> 2
        mapSlug.startsWith("mines") || mapSlug == "bossArea3" -> 3
        mapSlug.startsWith("ruins") || mapSlug == "bossArea4" -> 4
        else -> 1
    }

    /** Technique damage lands exactly like a weapon hit: numbers, flinch, and the kill payout. */
    private fun hurtEnemy(enemy: Enemy, damage: Int) {
        // The Dragon under the arena floor can't be hurt -- the wiki's own rule: wait for it
        // to resurface and become targetable again.
        if (enemy.untargetable) return
        enemy.hp -= damage
        damageNumbers.showDamage(
            enemy.mesh.position.x, labelHeight(enemy), enemy.mesh.position.z,
            damage, false,
        )
        maybeLilyScreech(enemy)
        if (enemy.isDead) onEnemyKilled(enemy) else {
            enemy.ai?.onDamaged()
            trySplitSlime(enemy)
        }
    }

    private fun updateTechEffects(deltaTime: Double) {
        val actions = delayedTechActions.iterator()
        while (actions.hasNext()) {
            val delayed = actions.next()
            delayed.remaining -= deltaTime
            if (delayed.remaining <= 0) {
                actions.remove()
                delayed.action()
            }
        }

        val projectiles = techProjectiles.iterator()
        while (projectiles.hasNext()) {
            val proj = projectiles.next()
            proj.remaining -= deltaTime
            proj.age += deltaTime
            proj.sprite.material.map =
                proj.frames[(proj.age * FOIE_FRAME_RATE).toInt() % proj.frames.size]
            proj.sprite.position.x += proj.dirX * FOIE_SPEED_UNITS * worldUnit * deltaTime
            proj.sprite.position.z += proj.dirZ * FOIE_SPEED_UNITS * worldUnit * deltaTime
            projectileTrails[proj.sprite]?.let { kind ->
                val sp = proj.sprite.position
                if (kind == "foie") techniqueFx?.foieTrail(sp.x, sp.y, sp.z, proj.dirX, proj.dirZ)
                else techniqueFx?.megidTrail(sp.x, sp.y, sp.z, proj.dirX, proj.dirZ)
            }

            // The flame it drags: a short-lived ember shed behind every frame of flight.
            spawnParticle(
                "foie_flame_0",
                proj.sprite.position.x, proj.sprite.position.y, proj.sprite.position.z,
                sizeWorld = 3.0, colorHex = FOIE_COLOR,
                vy = 2.0, seconds = 0.25,
            )

            var hit = false
            for (enemy in enemies) {
                if (enemy.isDead) continue
                val dx = enemy.mesh.position.x - proj.sprite.position.x
                val dz = enemy.mesh.position.z - proj.sprite.position.z
                val reach = enemy.hitboxRadius + FOIE_RADIUS_UNITS * worldUnit
                if (dx * dx + dz * dz <= reach * reach) {
                    hurtEnemy(enemy, techniqueDamage(proj.power, player?.stats?.mst ?: 0, enemy.resistances.fire))
                    hit = true
                    break
                }
            }

            // A fireball that sails through a crate looks broken -- it bursts on those too.
            if (!hit && breakBoxNear(
                    proj.sprite.position.x,
                    proj.sprite.position.z,
                    FOIE_RADIUS_UNITS * worldUnit,
                )
            ) {
                hit = true
            }

            if (hit) {
                spawnFoieImpact(proj.sprite.position.x, proj.sprite.position.y, proj.sprite.position.z)
            }
            if (hit || proj.remaining <= 0) {
                proj.sprite.parent?.remove(proj.sprite)
                projectiles.remove()
            }
        }

        val shots = megidShots.iterator()
        while (shots.hasNext()) {
            val shot = shots.next()
            shot.remaining -= deltaTime
            val step = FOIE_SPEED_UNITS * worldUnit * deltaTime
            shot.sprite.position.x += shot.dirX * step
            shot.sprite.position.z += shot.dirZ * step
            projectileTrails[shot.sprite]?.let { kind ->
                val sp = shot.sprite.position
                if (kind == "foie") techniqueFx?.foieTrail(sp.x, sp.y, sp.z, shot.dirX, shot.dirZ)
                else techniqueFx?.megidTrail(sp.x, sp.y, sp.z, shot.dirX, shot.dirZ)
            }

            // The curse's wake: violet residue hanging in the air where it passed.
            spawnParticle(
                "megid_orb",
                shot.sprite.position.x, shot.sprite.position.y, shot.sprite.position.z,
                sizeWorld = 2.6, colorHex = MEGID_COLOR, seconds = 0.35,
            )

            var hit = false
            for (enemy in enemies) {
                if (enemy.isDead) continue
                val dx = enemy.mesh.position.x - shot.sprite.position.x
                val dz = enemy.mesh.position.z - shot.sprite.position.z
                val reach = enemy.hitboxRadius + FOIE_RADIUS_UNITS * worldUnit
                if (dx * dx + dz * dz <= reach * reach) {
                    hit = true
                    // Success chance = power - dark resist (the wiki's EDK), a straight roll:
                    // the curse takes them or does nothing at all.
                    val chance = shot.power - enemy.resistances.dark
                    if (Random.nextDouble() * 100.0 < chance) {
                        enemy.hp = 0
                        damageNumbers.showDamage(
                            enemy.mesh.position.x, labelHeight(enemy), enemy.mesh.position.z,
                            enemy.maxHp, true,
                        )
                        onEnemyKilled(enemy)
                    } else {
                        damageNumbers.showMiss(
                            enemy.mesh.position.x, labelHeight(enemy), enemy.mesh.position.z,
                        )
                    }
                    break
                }
            }

            if (hit || shot.remaining <= 0) {
                shot.sprite.parent?.remove(shot.sprite)
                shots.remove()
            }
        }

        val effects = techEffects.iterator()
        while (effects.hasNext()) {
            val effect = effects.next()
            effect.remaining -= deltaTime
            if (effect.remaining <= 0) {
                effect.root.parent?.remove(effect.root)
                effects.remove()
                continue
            }

            val age = effect.duration - effect.remaining
            if (effect.frames.isNotEmpty()) {
                val frame = (age * effect.frameRate).toInt().coerceAtMost(effect.frames.size - 1)
                setEffectMap(effect.root, effect.frames[frame])
            }
            if (effect.riseUnits != 0.0) {
                effect.root.position.y += effect.riseUnits * worldUnit * deltaTime
            }
            if (effect.growPerSecond != 0.0) {
                val f = 1.0 + effect.growPerSecond * deltaTime
                effect.root.scale.set(
                    effect.root.scale.x * f,
                    effect.root.scale.y * f,
                    effect.root.scale.z * f,
                )
            }
            if (effect.spinPerSecond != 0.0) {
                effect.root.rotation.y += effect.spinPerSecond * deltaTime
            }
            setEffectOpacity(effect.root, effect.remaining / effect.duration)
        }
    }

    private fun setEffectOpacity(root: Object3D, opacity: Double) {
        root.traverse { child ->
            val material = child.asDynamic().material
            if (material != null) material.opacity = opacity
        }
    }

    private fun setEffectMap(root: Object3D, texture: Texture) {
        root.traverse { child ->
            val material = child.asDynamic().material
            if (material != null) material.map = texture
        }
    }

    // --- Mag feeding ---

    private fun currentMagFeedsLeft(p: Player): Int =
        if (Date.now() >= p.magWindowEndMs) Mag.FEEDS_PER_WINDOW else p.magFeedsLeft

    private fun magNextWindowSeconds(p: Player): Int =
        if (currentMagFeedsLeft(p) > 0) 0
        else (((p.magWindowEndMs - Date.now()) / 1000.0).coerceAtLeast(0.0)).toInt()

    /**
     * Feeds one item from the pack: three feeds per 3:30 window (the wiki's rule -- unspent
     * feeds don't carry over), Table 0 gains, and the level-10 evolution announced when the
     * feeding crosses it.
     */
    private fun feedMag(tool: ToolType): Boolean {
        val p = player ?: return false
        val held = p.tools[tool] ?: return false

        val now = Date.now()
        if (now >= p.magWindowEndMs) {
            p.magFeedsLeft = Mag.FEEDS_PER_WINDOW
            p.magWindowEndMs = now + Mag.FEED_WINDOW_SECONDS * 1000.0
        }
        if (p.magFeedsLeft <= 0) {
            showToast("The Mag isn't hungry yet")
            return false
        }

        val fed = p.mag.fed(tool) ?: return false
        val levelBefore = p.mag.level
        p.mag = fed
        p.magFeedsLeft--
        if (tool !in UNLIMITED_TOOLS) {
            if (held <= 1) p.tools.remove(tool) else p.tools[tool] = held - 1
        }

        checkMagEvolution(p)?.let { evolved ->
            showToast("The Mag evolved into $evolved!")
            refreshMagMesh()
        }
        persistProgress()
        return true
    }

    /**
     * The evolution ladder, in order: the class's own level-10 form first; at 35, whichever
     * stat the feeding favoured picks the permanent second form (see Mag.secondEvolutionForm).
     * Returns the new form when one happened.
     */
    private fun checkMagEvolution(p: Player): String? {
        if (p.mag.form == Mag.BASE_FORM && p.mag.level >= Mag.FIRST_EVOLUTION_LEVEL) {
            p.mag = p.mag.withForm(firstEvolutionOf(appearance.characterClass))
            return p.mag.form
        }
        if (p.mag.form in Mag.FIRST_FORMS && p.mag.level >= Mag.SECOND_EVOLUTION_LEVEL) {
            p.mag.secondEvolutionForm()?.let { evolved ->
                p.mag = p.mag.withForm(evolved)
                return evolved
            }
        }
        // Third evolutions happen only ON the multiples of five from 50 up -- a Mag sitting at
        // 52 waits for 55, per the wiki's cadence.
        if (p.mag.form in Mag.SECOND_FORMS &&
            p.mag.level >= Mag.THIRD_EVOLUTION_LEVEL &&
            p.mag.level % Mag.THIRD_EVOLUTION_STEP == 0
        ) {
            p.mag.thirdEvolutionForm(
                professionOf(appearance.characterClass),
                isFemaleCharacter(appearance.characterClass),
                appearance.sectionId in MAG_SECTION_GROUP_A,
            )?.let { evolved ->
                p.mag = p.mag.withForm(evolved)
                return evolved
            }
        }
        return null
    }

    // --- Shops ---

    private fun buyTool(tool: ToolType, price: Int): Boolean {
        val p = player ?: return false
        val held = p.tools[tool] ?: 0
        if (held >= tool.maxStack) {
            showToast("You can't carry any more")
            return false
        }
        if (p.meseta < price) {
            showToast("Not enough Meseta")
            return false
        }
        p.meseta -= price
        p.tools[tool] = held + 1
        persistProgress()
        return true
    }

    private fun buyWeapon(tier: WeaponTier, price: Int): Boolean {
        val p = player ?: return false
        if (p.meseta < price) {
            showToast("Not enough Meseta")
            return false
        }
        p.meseta -= price
        inventory.add(WeaponItem(tier, grind = 0, specialAttack = null))
        persistProgress()
        return true
    }

    private fun sellWeapon(item: WeaponItem): Boolean {
        val p = player ?: return false
        if (!inventory.remove(item)) return false
        p.meseta = (p.meseta + weaponSellPrice(item)).coerceAtMost(MAX_MESETA)
        persistProgress()
        return true
    }

    private fun sellTreasure(treasure: TreasureType): Boolean {
        val p = player ?: return false
        if (!p.treasures.remove(treasure)) return false
        p.meseta = (p.meseta + TREASURE_SELL_PRICE).coerceAtMost(MAX_MESETA)
        persistProgress()
        return true
    }

    /** Which palette actions this class can hold: techniques are the Forces' alone for now. */
    private fun availableActionsFor(characterClass: CharacterClass): List<GameAction> =
        if (professionOf(characterClass) == Profession.FORCE) GameAction.entries.toList()
        else GameAction.entries.filter { it.technique == null }

    /**
     * Throws one swing in the given style. Shared by all three attack buttons -- they differ only
     * in the damage/accuracy/wind-up trade [AttackStyle] carries, not in what they do.
     */
    private fun swing(type: AttackType) {
        val p = player ?: return
        if (p.hp <= 0) return

        // A tap that lands while the current swing still has input locked is *buffered*, not
        // dropped: it fires on the exact frame the lockout ends. This is how the real game
        // combos -- the press registers during the swing and the next attack starts at the
        // earliest frame -- and it's what makes mashing produce clean three-hit combos. Dropping
        // these taps instead meant most of a touchscreen player's inputs did nothing unless they
        // hit the narrow post-lockout gap, which read as unresponsive, arrhythmic combat.
        if (p.combat.isAttacking) {
            bufferedAttack = type
            return
        }

        // The focus lock aims the swing before anything else reads the yaw.
        faceFocusTarget(p)

        // The clip for this combo step, chosen before the swing so it matches the step the
        // frame data is about to be looked up for.
        val comboMotion = p.attackMotions[p.combat.comboStep.coerceAtMost(p.attackMotions.size - 1)]

        val item = equippedItem
        val special = item?.specialAttack

        // A special tap with no special on the weapon does nothing, exactly like the greyed-out
        // palette slot says.
        if (type == AttackType.SPECIAL && special == null) return

        // Berserk's price is paid on the swing, hit or miss -- a quarter of max health, never
        // taking the character below 3.
        val berserk = type == AttackType.SPECIAL && special?.family == SpecialFamily.BERSERK

        val started = p.combat.tryAttack(
            p.mesh.rotation.y,
            enemies.filter { !it.untargetable }.toMutableList(),
            p.weaponType,
            // A fresh profession roll on every swing, on top of the class's hidden base -- see
            // effectiveAtp. The weapon contributes its own per-swing roll across its ATP spread
            // when an item is equipped (see WeaponItem), or the old flat class figure otherwise.
            attackPower = effectiveAtp(
                p.stats,
                professionOf(p.characterClass),
                item?.rollAtp(Random.nextDouble()) ?: equippedWeaponAtp,
                Random.nextDouble(),
            ),
            luck = p.stats.lck,
            // The wiki's ATA total: the class's own figure plus the equipped weapon's.
            totalAta = p.stats.ata + (item?.tier?.ata ?: 0),
            type = type,
            damageModifierOverride = if (berserk) SACRIFICIAL_DAMAGE_MODIFIER else null,
            onKnockdown = { it.ai?.onKnockedDown() },
            onMiss = { damageNumbers.showMiss(it.mesh.position.x, labelHeight(it), it.mesh.position.z) },
            // Whatever the blade didn't spend on enemies goes to crates and traps, at the
            // same contact frame -- one budget for everything a swing touches.
            onStrikeResolved = { reached ->
                breakBoxesInSwing(p, p.combat.maxTargets - reached)
            },
            onHit = { enemy, damage, critical ->
            // Special attacks don't feed the blast gauge -- their payoff is the weapon's own
            // effect (wiki: "Special attacks also do not contribute to the Photon Blast gauge").
            if (type != AttackType.SPECIAL) {
                p.photonBlast.onDamageDealt(damage, p.level)
            }

            damageNumbers.showDamage(
                enemy.mesh.position.x,
                labelHeight(enemy),
                enemy.mesh.position.z,
                damage,
                critical,
            )

            if (p.weaponType.isRanged) maybeLilyScreech(enemy)
            if (!enemy.isDead) trySplitSlime(enemy)

            // The weapon's special effect, applied on a connected special swing before the death
            // check so an effect kill flows into the ordinary death handling below.
            if (type == AttackType.SPECIAL && special != null && !enemy.isDead) {
                applySpecialEffect(p, special, enemy)
            }

            if (enemy.isDead) {
                onEnemyKilled(enemy)
            } else {
                // A normal hit makes the target flinch; a heavy one shoves it back as well. A
                // special does neither -- its payload is the weapon's own effect, not impact.
                when (type) {
                    AttackType.NORMAL -> enemy.ai?.onDamaged()
                    AttackType.HEAVY -> enemy.ai?.onPushedBack(p.mesh.position)
                    AttackType.SPECIAL -> Unit
                }
            }
        },
        )

        // A firearm puts a round down the barrel on every swing that actually starts.
        if (started && p.weaponType.itemIcon == ItemIcon.RANGED) {
            fireBullet(p)
        }

        if (started && berserk) {
            p.hp = (p.hp - p.maxHp / 4).coerceAtLeast(BERSERK_HP_FLOOR)
            playerStatusPanel.setHealth(p.hp, p.maxHp)
        }

        // Only advance the combo/animation if a swing actually started -- a spammed tap while
        // already mid-swing must be dropped entirely, not queued, or it fights the current
        // swing's animation and timer.
        if (started) {
            p.currentAttackMotion = comboMotion

            // Play the clip at whatever speed makes it last exactly as long as the frame data
            // says the swing does. Without this the animation and the timing drift apart -- the
            // character would still be mid-swing when the next attack is already allowed, or
            // stand idle waiting for a lockout that has outlived its animation.
            val clipSeconds = (comboMotion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
            val target = p.combat.currentSwingOccupancy.takeIf { it > 0.0 }
                ?: p.combat.currentAttackDuration
            // Clamped so a clip whose authored length is far from the frame data can't come out
            // as either a crawl or a blur.
            p.swingTimeScale =
                if (clipSeconds > 0 && target > 0) (clipSeconds / target).coerceIn(0.85, 2.6)
                else 1.0
            // Held on its first frame through the charge, then released at full speed. That
            // pause *is* the heavy attack's weight -- the stroke itself is no slower.
            p.animator.timeScale = if (p.combat.isCharging) 0.0 else p.swingTimeScale

            // Start the swing clip right here rather than waiting for the render loop's state
            // machine: with restart, so a weapon whose combo reuses one clip for every step (the
            // guns) visibly fires on each tap instead of silently continuing the previous shot's
            // clip, and with the attack crossfade, which is near-instant -- PSO cuts into a swing
            // hard, and the locomotion blend here read as mushy, delayed frames.
            p.animator.playClip(
                comboMotion,
                fadeDuration = ATTACK_CROSSFADE_DURATION,
                restart = true,
                oneShot = true,
            )
        }
    }

    private fun openChat() {
        if (player == null) return
        chatPanel.open()
    }

    /**
     * Performs an emote: loads its clip if this is the first time, plays it once, then lets the
     * normal idle/walk state take back over. Emotes are one-shot, so nothing needs to cancel it --
     * moving simply overrides it on the next frame.
     */
    private fun playEmote(emote: Emote) {
        val p = player ?: return

        MainScope().launch {
            val motion = emoteMotions.getOrPut(emote.clip) {
                loadPlayerClip(animationPath(emote.clip))
            }
            p.emoteMotion = motion
            p.emoteRemaining = (motion.frameCount - 1) / PSO_FRAME_RATE_DOUBLE
        }
    }

    /**
     * Where a damage figure should sit: above the enemy's head rather than at its feet, which is
     * where its position actually is. Scaled off the species' cylinder so a Monest's number
     * clears the hive while a Mothmant's stays close to it.
     *
     * Deliberately not the mesh's bounding sphere: on an animated skinned mesh that radius is
     * computed over the whole skeleton and comes out enormous, which threw labels tens of
     * thousands of pixels off screen.
     */
    private fun labelHeight(enemy: Enemy): Double =
        enemy.mesh.position.y + enemy.hitboxRadius * LABEL_HEIGHT_FACTOR

    /**
     * Where the lock aims: the target's centre of mass. Estimated from the hitbox radius with a
     * floor and a cap, since species height only loosely follows width -- wide Boomas and narrow
     * Mothmants both land near mid-body this way.
     */
    private fun centerMassHeight(enemy: Enemy): Double =
        enemy.mesh.position.y +
            (enemy.hitboxRadius * CENTER_MASS_RADIUS_FACTOR + CENTER_MASS_BASE_UNITS * worldUnit)
                .coerceAtMost(CENTER_MASS_CAP_UNITS * worldUnit)

    private fun toggleFlying(): Boolean {
        val controller = player?.controller ?: return false
        controller.flying = !controller.flying
        flightVerticalControls.setVisible(controller.flying)
        return controller.flying
    }

    /**
     * Chat commands. Returns what to write back into the log, or null when the text was just
     * something the player said.
     */
    /**
     * TESTING AID: casting spends no TP while on. Preferred over handing out a huge TP pool or
     * levels for technique testing -- a high-level Force one-shots the Forest, and a freeze or a
     * debuff can't be observed on something that's already dead.
     */
    private var freeCasting = false

    private fun runChatCommand(text: String): String? {
        // `?lv N?` sets the character's level outright -- up or down -- for testing scaling.
        Regex("""\?lv (\d+)\?""").matchEntire(text.lowercase())?.let { match ->
            val p = player ?: return "No character."
            val level = match.groupValues[1].toInt().coerceIn(1, MAX_LEVEL)
            p.totalExp = totalExpForLevel(level)
            p.level = level
            p.hp = p.maxHp
            p.tp = p.stats.tp
            playerStatusPanel.setHealth(p.hp, p.maxHp)
            playerStatusPanel.setTp(p.tp, p.stats.tp)
            playerStatusPanel.setLevel(level)
            persistProgress()
            return "Level set to $level."
        }
        // `?feed <item> <n>?` TESTING AID: force-feeds the Mag n of an item instantly, no
        // window, no inventory -- the real 3 feeds per 3:30 makes reaching an evolution take
        // hours of wall-clock time neither tester has. Runs the exact evolution ladder a real
        // feeding does. Items: mono/di/tri (mates), fluid, anti, sol, star.
        Regex("""\?feed (\w+) (\d+)\?""").matchEntire(text.lowercase())?.let { match ->
            val p = player ?: return "No character."
            val tool = when (match.groupValues[1]) {
                "mono" -> ToolType.MONOMATE
                "di" -> ToolType.DIMATE
                "tri" -> ToolType.TRIMATE
                "fluid" -> ToolType.MONOFLUID
                "anti" -> ToolType.ANTIDOTE
                "sol" -> ToolType.SOL_ATOMIZER
                "star" -> ToolType.STAR_ATOMIZER
                else -> return "Unknown food. Try mono/di/tri/fluid/anti/sol/star."
            }
            val count = match.groupValues[2].toInt().coerceIn(1, 500)
            var evolutions = ""
            repeat(count) {
                p.mag.fed(tool)?.let { p.mag = it }
                checkMagEvolution(p)?.let { evolutions += " -> $it" }
            }
            refreshMagMesh()
            persistProgress()
            val mag = p.mag
            return "Mag L${mag.level} ${mag.form}: DEF ${mag.def} POW ${mag.pow} " +
                "DEX ${mag.dex} MIND ${mag.mind}$evolutions"
        }
        // `?wield <name>?` equips the first carried weapon whose tier name matches -- the
        // fastest way to cycle the armory while testing, and it exercises the exact runtime
        // equip path the menu uses.
        Regex("""\?wield (.+)\?""").matchEntire(text.lowercase())?.let { match ->
            val query = match.groupValues[1].trim()
            val item = inventory.find { it.tier.name.lowercase().contains(query) }
                ?: return "Nothing in the pack matches \"$query\"."
            return if (equipFromMenu(item)) "Drawn: ${item.displayName}." else "Couldn't equip it."
        }

        return runSimpleChatCommand(text)
    }

    private fun runSimpleChatCommand(text: String): String? = when (text.lowercase()) {
        FLY_COMMAND ->
            if (toggleFlying()) "Flight enabled -- walls no longer stop you."
            else "Flight disabled."

        TP_COMMAND -> {
            freeCasting = !freeCasting
            player?.let { p ->
                p.tp = p.stats.tp
                playerStatusPanel.setTp(p.tp, p.stats.tp)
            }
            if (freeCasting) "Free casting on -- techniques cost no TP."
            else "Free casting off."
        }

        DIAG_COMMAND -> {
            val p = player
            val nearest = p?.let { player ->
                enemies.minByOrNull {
                    val dx = it.mesh.position.x - player.mesh.position.x
                    val dz = it.mesh.position.z - player.mesh.position.z
                    dx * dx + dz * dz
                }
            }
            when {
                nearest == null -> "No enemies nearby."
                else -> "${nearest.slug} hp=${nearest.hp}/${nearest.maxHp} " +
                    "dying=${((nearest.dyingRemaining * 100).toInt() / 100.0)} :: " +
                    (nearest.ai?.debugState() ?: "no ai")
            }
        }

        ARMS_COMMAND -> {
            if (player == null) "No character." else {
                var granted = 0
                for (tier in TESTING_ARMORY) {
                    if (inventory.none { it.tier === tier }) {
                        inventory.add(WeaponItem(tier))
                        granted++
                    }
                }
                persistProgress()
                "Armory delivered: $granted weapons, one of every class. Menu > Equip to draw one."
            }
        }

        FX_COMMAND -> {
            val p = player
            if (p == null) "No character." else {
                val yaw = p.mesh.rotation.y
                val dirX = sin(yaw)
                val dirZ = cos(yaw)
                val px = p.mesh.position.x
                val py = p.mesh.position.y
                val pz = p.mesh.position.z
                val chest = py + PLAYER_CENTER_MASS_UNITS * worldUnit

                // The control: the sprite path the already-working spells use.
                val control = effectSprite("zonde_flash", 4.2, colorHex = ZONDE_COLOR)
                control.position.set(px + dirX * 6.0, chest, pz + dirZ * 6.0)
                addEffect(TimedEffect(control, 3.0, 3.0))

                // Raw particles at three sizes, hanging still down the sightline for three
                // seconds -- whichever of these show calibrates the whole size table.
                for ((i, size) in listOf(2.0, 8.0, 20.0).withIndex()) {
                    spawnParticle(
                        "barta_burst",
                        px + dirX * (10.0 + i * 10.0), chest, pz + dirZ * (10.0 + i * 10.0),
                        sizeWorld = size, colorHex = BARTA_COLOR, seconds = 3.0,
                    )
                }

                // One of each particle build the techniques use.
                spawnIceWake(px, py, pz, dirX, dirZ, BARTA_RANGE_UNITS)
                spawnZondeSparks(px + dirX * 8.0, chest, pz + dirZ * 8.0)
                spawnHealLights(px, py, pz, RESTA_COLOR)
                supportSwirl(px, py, pz, SHIFTA_COLOR)
                boltBetween(px, chest, pz, px + dirX * 30.0, chest, pz + dirZ * 30.0)

                "FX test fired. worldUnit=${(worldUnit * 100).toInt() / 100.0} " +
                    "live=${particles.size} effects=${techEffects.size}"
            }
        }

        else -> null
    }

    override fun render() {
        val deltaTime = clock.getDelta()
        val p = player

        if (p != null) {
            // The HUD's Photon Blast dial tracks the live gauge (0-100). Cheap per-frame: the
            // panel only touches the DOM when the displayed arc actually changes.
            playerStatusPanel.setPhotonBlast(p.photonBlast.value / 100.0)

            p.combat.update(deltaTime)

            // Release a buffered combo tap the moment input unlocks -- exactly the cadence the
            // real game's own input queue produces. Being floored swallows it: a knockdown ends
            // the combo, and firing a stale tap on getting up would feel like a ghost input.
            if (!p.combat.isAttacking) {
                val buffered = bufferedAttack
                bufferedAttack = null

                if (buffered != null && p.knockedDownRemaining <= 0 && !gameMenu.isOpen) {
                    swing(buffered)
                }
            }

            val alive = p.hp > 0

            if (alive) {
                // Movement direction is computed relative to the camera's *current* facing, so the
                // camera's yaw must not be re-derived from the character's facing in this same
                // pass -- doing so previously created a feedback loop (character yaw -> camera
                // yaw -> next frame's movement basis -> character yaw...) that spiralled into
                // circles for any input with a sideways component. The camera's facing is driven
                // purely by the user's manual drag now; the character's facing is a one-way
                // visual output.
                // The menu pauses the world: the stick is ignored, and the enemy/room updates
                // below are skipped, so reading it can't get you killed.
                // Paralysis freezes the stick as surely as the menu does; confusion turns
                // it around instead -- push forward and the character backs away.
                val movementLocked = gameMenu.isOpen || p.paralysisRemaining > 0
                val stickSign = if (p.confusedRemaining > 0) -1.0 else 1.0
                p.controller.update(
                    deltaTime,
                    if (movementLocked) .0 else joystick.x * stickSign,
                    if (movementLocked) .0 else joystick.y * stickSign,
                    inputManager.effectiveYaw,
                    p.combat.isAttacking,
                    flightVerticalControls.ascending,
                    flightVerticalControls.descending,
                )
                resolveBoxCollisions(p)
                p.mesh.rotation.y = p.controller.yaw

                if (!gameMenu.isOpen) {
                    updateTeleporters(p)
                    updateDoors(p, deltaTime)
                }

                // Gates come before the wave director so a shut door has already pushed the
                // player back out of its plane by the time room entry is decided from position.
                if (!gameMenu.isOpen) {
                    fieldGates?.update(
                        deltaTime,
                        p.mesh.position,
                        roomWaveDirector?.unlockedDoors ?: emptySet(),
                    )
                    updatePickups(p.mesh.position)
                    updateTechEffects(deltaTime)
                    updateParticles(deltaTime)
                    updateBoxShards(deltaTime)
                    updateBullets(deltaTime)
                }

                // Walking into a room is what starts its first wave, so this needs the position
                // from *after* movement was applied, not the one the frame started on.
                roomWaveDirector?.update(deltaTime, p.mesh.position.x, p.mesh.position.z)
                roomWaveDirector?.let { director ->
                    for ((gate, section) in sectionBarriers) {
                        if (!gate.isOpen && section in director.completedSections) gate.open()
                    }
                }
                updateHealRings(p)
                questVm?.update(p.mesh.position.x, p.mesh.position.z)
                techniqueFx?.update(deltaTime)
                updateSlimes(p, deltaTime)
                updateFieldTraps(p, deltaTime)
                updateFieldPillars(p, deltaTime)
                updatePlayerStatuses(p, deltaTime)
                updateEnemyShots(deltaTime)
                // PSO wakes a room when you walk into it: everything placed there comes for
                // you, and loses interest once you've left.
                roomWaveDirector?.currentSectionId?.let { room ->
                    for (enemy in enemies) {
                        enemy.ai?.roomAggro = enemy.section >= 0 && enemy.section == room
                    }
                }
                updateBossRoom()
                // The Dragon runs its own fight script -- same pause rules as the enemy pass.
                if (p.hp > 0 && !gameMenu.isOpen) {
                    dragonFight?.update(deltaTime, p.mesh.position)
                    deRolLeFight?.update(deltaTime, p.mesh.position)
                    volOptFight?.update(deltaTime, p.mesh.position)
                    darkFalzFight?.update(deltaTime, p.mesh.position)
                    updateDivineStrikes(p, deltaTime)
                    updateBossDeckHazards(p, deltaTime)
                    updateVolPrison(p, deltaTime)
                }
                if (dragonPartOverrideRemaining > 0) dragonPartOverrideRemaining -= deltaTime
                updateBossProgress()
                updateReturnWarp(p)

                focusedTarget = if (isPeacefulHub) null else findFocusTarget(p)
                focusedEnemy = focusedTarget?.enemy
                focusedEnemy.let { target ->
                    if (target != null) targetInfoPanel?.setTarget(target.name, target.hp, target.maxHp)
                    else targetInfoPanel?.clear()
                }

                miniMap?.update(
                    p.mesh.position.x,
                    p.mesh.position.z,
                    p.mesh.rotation.y,
                    enemies.mapNotNull { enemy ->
                        if (enemy.isDead) null
                        else enemy.mesh.position.x to enemy.mesh.position.z
                    },
                    // Doors read red while locked and green once open -- on a cave map that's
                    // the difference between a route and a dead end.
                    doors = fieldGates?.doors.orEmpty().map { door ->
                        // The gate's own blocking span: the bar drawn on the map is exactly
                        // the doorway it fills.
                        MapDoor(door.ax, door.az, door.bx, door.bz, door.isOpen)
                    },
                    visitedRooms = roomWaveDirector?.visitedSections ?: emptySet(),
                )
            } else {
                targetInfoPanel?.clear()
            }

            for (mixer in propMixers) mixer.update(deltaTime)

            p.emoteRemaining -= deltaTime

            val stateMotion = when {
                !alive -> p.deadMotion
                // Being floored overrides everything short of dying: you can't swing or run
                // out of it, you get up first.
                p.knockedDownMotion.let { _ -> p.knockedDownRemaining > 0 } ->
                    p.knockedDownMotion ?: p.hitMotion
                // Taking a blow, or turning one aside, outranks the player's own swing. These
                // used to sit *below* it, so a character who was attacking -- which is most of
                // a fight -- never visibly reacted to anything: health simply dropped.
                p.hitReactionRemaining > 0 -> p.hitMotion
                p.blockRemaining > 0 -> p.blockMotion
                // The swing's animation runs to its full length even after input has
                // unlocked, so the first two hits of a combo finish instead of being cut off
                // at the point the next attack merely *became* possible. Moving cancels it,
                // which is the natural way out of the tail of a swing.
                p.combat.isSwinging -> p.currentAttackMotion ?: p.idleMotion
                // An emote holds only while the player stands still; moving cancels it, which
                // is what makes it feel like an action rather than a lock.
                p.emoteRemaining > 0 && !p.controller.isMoving ->
                    p.emoteMotion ?: p.idleMotion
                p.controller.isRunning -> p.runMotion
                p.controller.isMoving -> p.walkMotion
                else -> p.idleMotion
            }

            // Attack clips keep the swing()-set one-shot/fast-cut treatment even when this state
            // machine is what (re-)enters them, e.g. resuming a swing after a hit reaction.
            if (poseLocked) {
                // The frozen debug pose wins over the whole state machine.
            } else if (p.combat.isSwinging && stateMotion === p.currentAttackMotion) {
                // Re-applied every frame so the clip unfreezes the moment the charge ends.
                p.animator.timeScale = if (p.combat.isCharging) 0.0 else p.swingTimeScale
                p.animator.playClip(
                    stateMotion,
                    fadeDuration = ATTACK_CROSSFADE_DURATION,
                    oneShot = true,
                )
            } else {
                p.animator.playClip(stateMotion)
            }
            // Walking out of the tail of a swing cancels it rather than sliding along with the
            // clip still playing.
            if (p.combat.isSwinging && !p.combat.isAttacking && p.controller.isMoving) {
                p.combat.cancelSwing()
            }

            // Restore normal playback only once the swing is genuinely over. Resetting it at the
            // input-unlock point instead snapped the still-playing clip to a different speed
            // mid-swing, which read as a stutter just before the character returned to idle.
            if (!p.combat.isSwinging && !poseLocked) p.animator.timeScale = 1.0

            p.animator.update(deltaTime)
            magCompanion?.update(deltaTime)

            inputManager.targetPosition.copy(p.controller.position)
        }

        if (p != null) {
            p.invulnerableRemaining -= deltaTime
            p.hitReactionRemaining -= deltaTime
            p.blockRemaining -= deltaTime
            if (p.shiftaRemaining > 0) {
                p.shiftaRemaining -= deltaTime
                if (p.shiftaRemaining <= 0) showToast("Shifta wore off")
            }
            if (p.debandRemaining > 0) {
                p.debandRemaining -= deltaTime
                if (p.debandRemaining <= 0) showToast("Deband wore off")
            }
            p.knockedDownRemaining -= deltaTime

            if (p.hp <= 0) {
                // Started the moment hp first hit zero (see below); once it counts down, drop the
                // player back at their original spawn point rather than leaving the game in a dead
                // end that only a page reload can get out of.
                p.respawnRemaining -= deltaTime

                if (p.respawnRemaining <= 0) {
                    // Dying costs the Mag 5 synchro, which is what makes deaths sting beyond
                    // the walk back.
                    p.mag = p.mag.afterDeath()
                    p.mesh.position.set(p.spawnX, p.spawnY, p.spawnZ)
                    p.hp = p.maxHp
                    // A moment of post-respawn grace so materializing back in doesn't just walk
                    // straight into another hit from whatever was already standing on the spot.
                    p.invulnerableRemaining = INVULNERABILITY_DURATION
                    playerStatusPanel.setHealth(p.hp, p.maxHp)
                }
            }
        }

        val deadEnemies = enemies.iterator()
        while (deadEnemies.hasNext()) {
            val enemy = deadEnemies.next()
            if (!enemy.isDead) continue

            // Death clip still running: keep animating it, just don't let the AI drive it.
            enemy.dyingRemaining -= deltaTime
            enemy.animationMixer?.update(deltaTime)

            if (enemy.dyingRemaining <= 0) {
                // A downed Dubchic lies where it fell until its revival timer answers: pod
                // still standing means back on its feet at full health; pod broken means the
                // death finally sticks, drop and all.
                if (enemy.reviveRemaining > 0) {
                    enemy.reviveRemaining -= deltaTime
                    if (enemy.reviveRemaining <= 0) {
                        if (dubwitchAlive(enemy.section)) {
                            enemy.hp = enemy.maxHp
                            enemy.ai?.onRevived(enemy.reviveMotion)
                        } else {
                            maybeDrop(enemy)
                            enemy.mesh.parent?.remove(enemy.mesh)
                            deadEnemies.remove()
                        }
                    }
                    continue
                }
                enemy.mesh.parent?.remove(enemy.mesh)
                deadEnemies.remove()
            }
        }

        // Hive production runs on the same conditions as enemy AI: a live player and no menu.
        if (p != null && p.hp > 0 && !gameMenu.isOpen) {
            updateHives(deltaTime, p.mesh.position)
        }

        for (enemy in enemies) {
            // Handled by the death pass above -- letting the AI run here would immediately
            // overwrite the death clip with walk or attack.
            if (enemy.isDead) continue

            if (p != null && p.hp > 0 && !gameMenu.isOpen) {
                val landed = enemy.ai?.update(
                    deltaTime,
                    p.mesh.position,
                    healthFraction =
                        if (enemy.maxHp > 0) enemy.hp.toDouble() / enemy.maxHp else 1.0,
                ) ?: false

                // Each attacking enemy runs its own independent cooldown, so with several enemies
                // surrounding the player their landed hits land on different frames rather than
                // neatly taking turns -- without a shared cooldown on the *player's* side, several
                // of those hits can stack within the same second or two and chain straight through
                // the whole health bar. A brief invulnerability window after any hit (standard
                // action-game i-frames) caps the effective damage rate regardless of how many
                // enemies are attacking at once.
                if (landed && p.invulnerableRemaining <= 0) {
                    // Evasion first: a monster's accuracy against the character's EVP, the same
                    // formula the player's own swings use against an enemy's. An evaded attack
                    // costs nothing but plays the guard -- which is what "blocking" is in PSO,
                    // rather than a button you hold.
                    val hitChance = accuracyPercent(
                        // The species' own accuracy, from the monster table.
                        totalAta = enemyStats(enemy.slug).ata,
                        type = AttackType.NORMAL,
                        comboStep = 0,
                        targetEvp = p.stats.evp,
                    )
                    if (Random.nextDouble() * 100.0 >= hitChance) {
                        p.blockRemaining = BLOCK_REACTION_DURATION
                        p.invulnerableRemaining = INVULNERABILITY_DURATION
                        damageNumbers.showMiss(
                            p.mesh.position.x,
                            p.mesh.position.y + PLAYER_CENTER_MASS_UNITS * worldUnit,
                            p.mesh.position.z,
                        )
                        continue
                    }

                    // Monsters crit at half their luck as a percentage -- twice the player's
                    // rate, which is why a Mothmant's luck of 20 makes it bite surprisingly hard
                    // for something with 8 health.
                    val base = physicalDamage(enemy.effectiveAtp, p.stats.dfp)
                    val critical = Random.nextDouble() < criticalChance(enemy.lck, monster = true)
                    val damage = (if (critical) base * CRITICAL_MULTIPLIER else base.toDouble())
                        .toInt()
                        .coerceAtLeast(1)

                    p.hp = (p.hp - damage).coerceAtLeast(0)
                    // Taking damage fills the blast gauge twenty times faster than dealing it.
                    p.photonBlast.onDamageTaken(damage, p.level)

                    // A single blow taking a quarter of the player's health puts them on the
                    // floor, which holds far longer than an ordinary flinch.
                    if (isKnockdown(damage, p.maxHp)) {
                        p.knockedDownRemaining = KNOCKDOWN_DURATION
                    }
                    p.invulnerableRemaining = INVULNERABILITY_DURATION
                    p.hitReactionRemaining = HIT_REACTION_DURATION
                    playerStatusPanel.setHealth(p.hp, p.maxHp)

                    if (p.hp <= 0) handlePlayerDowned(p)
                }
            }

            enemy.animationMixer?.update(deltaTime)

            if (enemy.jellenRemaining > 0) {
                enemy.jellenRemaining -= deltaTime
                if (enemy.jellenRemaining <= 0) enemy.jellenFactor = 1.0
            }
            if (enemy.zalureRemaining > 0) {
                enemy.zalureRemaining -= deltaTime
                if (enemy.zalureRemaining <= 0) enemy.zalureFactor = 1.0
            }
        }

        updateFreezeOverlays(deltaTime)

        separateEnemies()

        for (npcMixer in npcMixers) {
            npcMixer.update(deltaTime)
        }

        // Re-projected every frame rather than positioned once, so a number tracks its target as
        // the camera swings around it.
        updateMag(deltaTime)
        updateTalkPrompt()
        updateFocusReticle()
        damageNumbers.update(
            deltaTime,
            context.camera,
            context.canvas.clientWidth.toDouble(),
            context.canvas.clientHeight.toDouble(),
        )

        inputManager.update(deltaTime)

        super.render()
    }

    private fun animationPath(index: Int): String =
        "/player/animation/animation_${index.toString().padStart(3, '0')}.njm"

    /** "SavageWolf" -> "Savage Wolf", "DeRolLe" -> "De Rol Le" -- slugs are PascalCase with no
     * separators, so a space before each internal capital reads as a display name with no need
     * for a separate per-enemy display-name table. */
    private fun enemyDisplayName(slug: String): String = when (slug) {
        // The converted assets split Bulclaw into body forms; the player just sees a Bulclaw.
        "BulclawOpen", "BulclawClosed" -> "Bulclaw"
        // Both forms wear the boss's own name; the pillar reads as the machinery it is.
        "VolOptForm1", "VolOpt" -> "Vol Opt"
        "VolOptPillar" -> "Pillar"
        else -> slug.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    }

    private class Player(
        val characterClass: CharacterClass,
        val mesh: SkinnedMesh,
        val controller: CharacterController,
        val animator: PlayerAnimator,
        val combat: CombatController,
        var idleMotion: NjMotion,
        var walkMotion: NjMotion,
        var runMotion: NjMotion,
        var deadMotion: NjMotion,
        var hitMotion: NjMotion,
        var blockMotion: NjMotion,
        var knockedDownMotion: NjMotion?,
        /** The equipped weapon's class, which decides this character's attack timing. */
        var weaponType: WeaponType,
        var attackMotions: List<NjMotion>,
        val spawnX: Double,
        val spawnY: Double,
        val spawnZ: Double,
    ) {
        var currentAttackMotion: NjMotion? = null

        /** The equipped Mag. Every character starts with a level 5 one holding 5 DEF. */
        var mag: Mag = Mag()

        /** Fills from damage dealt and taken -- see PhotonBlastGauge. */
        val photonBlast = PhotonBlastGauge()

        /**
         * This character's statline with the Mag's contribution folded in. A Mag is the main
         * source of stats in PSO, so this is what combat should read, not the naked figures.
         */
        val stats: BaseStats
            get() {
                val base = statsAtLevel(characterClass, level)
                val armorDfp = (equippedFrame?.dfp ?: 0) + (equippedBarrier?.dfp ?: 0)
                val shifta = if (shiftaRemaining > 0) 1.0 + shiftaBoost else 1.0
                val deband = if (debandRemaining > 0) 1.0 + debandBoost else 1.0
                return BaseStats(
                    hp = base.hp + materialHp * 2 + equippedUnits.sumOf { it.hp },
                    tp = base.tp + materialTp * 2 + equippedUnits.sumOf { it.tp },
                    atp = ((base.atp + mag.bonusAtp + materialPower * 2 +
                        equippedUnits.sumOf { it.atp }) * shifta).toInt(),
                    dfp = ((base.dfp + mag.bonusDfp + armorDfp + materialDef * 2 +
                        equippedUnits.sumOf { it.dfp }) * deband).toInt(),
                    mst = base.mst + mag.bonusMst + materialMind * 2 + equippedUnits.sumOf { it.mst },
                    ata = base.ata + mag.bonusAta + equippedUnits.sumOf { it.ata },
                    lck = base.lck + materialLuck * 2,
                    // A barrier is mostly evasion -- this is what makes one worth raising.
                    evp = base.evp + materialEvade * 2 + (equippedFrame?.evp ?: 0) + (equippedBarrier?.evp ?: 0) +
                        equippedUnits.sumOf { it.evp },
                )
            }

        /** The naked statline at the current level, for showing the Mag's cut separately. */
        val baseStats: BaseStats get() = statsAtLevel(characterClass, level)

        /** Derived from [totalExp] on every gain -- see Leveling.kt. */
        var level: Int = 1
        var totalExp: Int = 0
        var meseta: Int = 0

        /** Tool stacks in the pack; an absent key means none carried. */
        val tools: MutableMap<ToolType, Int> = mutableMapOf()

        /** Rare trophies from the drop charts -- see TreasureType. */
        val treasures: MutableList<TreasureType> = mutableListOf()

        /** Stat Materials consumed: each a permanent +2 ATP / +2 MST / +2 max HP. */
        var materialPower: Int = 0
        var materialMind: Int = 0
        var materialHp: Int = 0
        var materialEvade: Int = 0
        var materialDef: Int = 0
        var materialLuck: Int = 0
        var materialTp: Int = 0

        /** Learned techniques and their levels; absent means not learned. */
        val techLevels: MutableMap<Technique, Int> = mutableMapOf()

        /** Unused technique disks in the pack. */
        val techDisks: MutableList<Pair<Technique, Int>> = mutableListOf()

        fun techLevel(technique: Technique): Int = techLevels[technique] ?: 0

        var hp: Int = statsAtLevel(characterClass, 1).hp
        val maxHp: Int get() = stats.hp

        /** Technique points. Spent casting, restored only by fluids -- PSO has no TP regen. */
        var tp: Int = statsAtLevel(characterClass, 1).tp

        /** The Mag feeding window -- see feedMag. */
        var magFeedsLeft: Int = Mag.FEEDS_PER_WINDOW
        var magWindowEndMs: Double = 0.0

        /** Bosses this character has felled -- the area-unlock progression. */
        val defeatedBosses: MutableSet<String> = mutableSetOf()

        /** The checkroom's ledger -- see the bank NPC. */
        var bankMeseta: Int = 0
        val bankTools: MutableMap<ToolType, Int> = mutableMapOf()
        val bankWeapons: MutableList<WeaponItem> = mutableListOf()
        val bankTreasures: MutableList<TreasureType> = mutableListOf()

        /** The armor closet -- owned pieces not currently worn, and what is. See Armor.kt. */
        val ownedFrames: MutableList<FrameItem> = mutableListOf()
        val ownedBarriers: MutableList<BarrierItem> = mutableListOf()
        val ownedUnits: MutableList<UnitType> = mutableListOf()
        var equippedFrame: FrameItem? = null
        var equippedBarrier: BarrierItem? = null
        val equippedUnits: MutableList<UnitType> = mutableListOf()
        val bankFrames: MutableList<FrameItem> = mutableListOf()
        val bankBarriers: MutableList<BarrierItem> = mutableListOf()
        val bankUnits: MutableList<UnitType> = mutableListOf()
        var invulnerableRemaining: Double = 0.0

        /** Counts down the brief flinch after taking a hit -- see HIT_REACTION_DURATION. */
        var hitReactionRemaining: Double = 0.0

        /** Counts down the guard played when an attack is evaded -- see the enemy strike. */
        var blockRemaining: Double = 0.0

        /** Playback speed for the swing clip once its charge (if any) has finished. */
        var swingTimeScale: Double = 1.0

        /** Shifta and Deband: fraction boosts and their countdowns. */
        var shiftaBoost: Double = 0.0
        var shiftaRemaining: Double = 0.0
        var debandBoost: Double = 0.0
        var debandRemaining: Double = 0.0

        /** Counts down being floored by a heavy blow -- see isKnockdown. */
        var knockedDownRemaining: Double = 0.0
        var respawnRemaining: Double = 0.0

        /**
         * Poison: the Lilies' venom, ticking damage until it wears off or an Antidote/Sol
         * Atomizer cures it. It can't kill on its own -- PSO's poison always leaves 1 HP.
         */
        var poisonRemaining: Double = 0.0
        var poisonTickRemaining: Double = 0.0

        /**
         * Paralysis: a Lily's screech at whoever shoots it from range. Locks movement while it
         * runs. Androids are immune (see applyParalysis) exactly as the wiki says.
         */
        var paralysisRemaining: Double = 0.0

        /**
         * Confusion: the Mines' confuse traps. Reverses the stick while it runs -- PSO's
         * confusion inverts your controls rather than freezing you. Androids are immune.
         */
        var confusedRemaining: Double = 0.0

        /** The emote currently playing, and how long is left of it -- see playEmote. */
        var emoteMotion: NjMotion? = null
        var emoteRemaining: Double = 0.0

    }

    override fun dispose() {
        window.removeEventListener("error", windowErrorListener)
        window.removeEventListener("unhandledrejection", windowErrorListener)
        toastContainer.remove()
        for (reticle in reticlePool) reticle.root.remove()
        talkBubble.remove()
        super.dispose()
    }

    companion object {

        /** Foie's fireball: modest at level 1, per its page ("travels slowly at lower levels"). */
        private const val FOIE_SPEED_UNITS = 22.0
        private const val FOIE_RADIUS_UNITS = 0.55
        private const val FOIE_LIFETIME_SECONDS = 2.2

        /** Where a round leaves the character, how fast it flies, and how far it carries. */
        private const val BULLET_MUZZLE_FORWARD_UNITS = 1.2
        /**
         * Barrel height, measured against the character's own ~6.7-unit height: a raised gun
         * sits around the chest, not the waist, which is where 3.0 was putting it.
         */
        private const val BULLET_MUZZLE_HEIGHT_UNITS = 4.75

        /** How far right of the character's centre line the barrel sits. */
        private const val BULLET_MUZZLE_RIGHT_UNITS = 0.6
        private const val BULLET_SPEED_UNITS = 70.0
        private const val BULLET_LIFETIME_SECONDS = 0.45

        private const val FOIE_SPRITE_UNITS = 1.0
        private const val FOIE_FRAME_RATE = 18.0
        private const val FOIE_IMPACT_SECONDS = 0.5

        /**
         * Technique reach. Both cover the focus lock's own range (weapon reach or a caster's
         * TECH_FOCUS_RANGE_UNITS, plus FOCUS_MARGIN_UNITS) so anything the reticle can hold is
         * something the spell can actually touch.
         */
        private const val ZONDE_RANGE_UNITS = 35.0
        private const val BARTA_RANGE_UNITS = 20.0
        private const val BARTA_HALF_WIDTH_UNITS = 2.2
        private const val RESTA_RING_SECONDS = 0.7

        /**
         * Element colours. The ripped effect art is largely greyscale glow -- multiplying it by
         * these is what makes a fireball read as fire rather than the grey orb it was.
         */
        private const val FOIE_COLOR = 0xff7a20
        private const val ZONDE_COLOR = 0xffe94a
        private const val BARTA_COLOR = 0x63d8ff
        private const val RESTA_COLOR = 0x6dffa0

        private const val TECH_FLASH_SECONDS = 0.5

        /** The new techniques' shapes, in PSO units, from the wiki's behaviour descriptions. */
        private const val GIFOIE_RADIUS_UNITS = 15.0
        private const val GIFOIE_SECONDS = 2.2
        private const val GIFOIE_SPIN = 6.0
        private const val RAFOIE_RADIUS_UNITS = 6.0
        private const val GIBARTA_RANGE_UNITS = 12.0
        private const val GIBARTA_HALF_WIDTH_UNITS = 4.0
        private const val RABARTA_RADIUS_UNITS = 7.0
        private const val GIZONDE_MAX_TARGETS = 10
        private const val GIZONDE_CHAIN_UNITS = 9.0
        private const val RAZONDE_RADIUS_UNITS = 9.0
        private const val SUPPORT_RADIUS_UNITS = 10.0
        private const val GRANTS_FLASH_SECONDS = 0.7
        private const val MEGID_SPRITE_UNITS = 0.9
        private const val FREEZE_SPRITE_UNITS = 3.4

        // --- Particle figures, in raw world units (particleentry.dat's own space). Counts,
        //     sizes and speeds credited to an entry are the game's own numbers; the pacing and
        //     placement around them are judgment calls. ---
        private const val BOLT_THICKNESS_WORLD = 3.0
        private const val SWIRL_RADIUS_WORLD = 5.0
        private const val SWIRL_SPEED_WORLD = 8.0
        private const val SWIRL_RISE_WORLD = 10.0

        /** barta_lv1hontai: 40 shards of ~2 world units. */
        private const val BARTA_SHARD_COUNT = 40
        private const val BARTA_SHARD_SIZE_WORLD = 2.0
        private const val BARTA_WAVE_TRAVEL_SECONDS = 0.4
        private const val BARTA_SHARD_LIFE_SECONDS = 0.35

        /** gibartalv1star: 10 crystals launched at speed 50. */
        private const val GIBARTA_STAR_COUNT = 10
        private const val GIBARTA_STAR_SPEED_WORLD = 50.0
        private const val GIBARTA_STAR_SIZE_WORLD = 3.0
        private const val GIBARTA_SPREAD_RADIANS = 0.35

        /** zonde_ryuusi: 20 sparks of 8-10 world units streaming from the strike. */
        private const val ZONDE_SPARK_COUNT = 20
        private const val ZONDE_SPARK_SIZE_WORLD = 8.0
        private const val ZONDE_SPARK_SPEED_WORLD = 26.0

        /** anti_hikari: 7 lights of ~10 world units climbing the body. */
        private const val HEAL_LIGHT_COUNT = 7
        private const val HEAL_LIGHT_SIZE_WORLD = 10.0
        private const val HEAL_LIGHT_RISE_WORLD = 9.0

        private const val RABARTA_SHARD_COUNT = 24
        private const val GIFOIE_ARMS = 3
        private const val GIFOIE_STEPS = 24
        private const val RAFOIE_EMBER_COUNT = 12
        private const val GRANTS_RAY_COUNT = 6

        private const val GRANTS_COLOR = 0xffe9a0
        private const val MEGID_COLOR = 0xb050ff
        private const val SHIFTA_COLOR = 0xff6a4a
        private const val DEBAND_COLOR = 0x5a9aff
        private const val JELLEN_COLOR = 0xc060e0
        private const val ZALURE_COLOR = 0xe0c040

        /**
         * How much of their combined width two enemies keep between them. Slightly under 1 so
         * a pack can still crowd shoulder to shoulder around the player without shoving each
         * other out of reach of them.
         */
        private const val BODY_SEPARATION_FACTOR = 0.9

        /**
         * Where a hive casts its brood from: the crown while it stands, ground level once it
         * has been knocked over.
         */
        private const val HIVE_CROWN_HEIGHT_UNITS = 5.5
        private const val HIVE_DOWNED_EMIT_HEIGHT_UNITS = 1.0

        /** Focus-lock reach beyond the weapon's own, in PSO units. */
        private const val FOCUS_MARGIN_UNITS = 6.0

        /** No class's lock stops at its weapon's edge -- the reticle sees this far minimum. */
        private const val FOCUS_RANGE_FLOOR_UNITS = 26.0

        /** How close the player stands before an NPC offers to talk, and the prompt's height. */
        /**
         * Speaking distance. Generous on purpose: the shop and checkroom staff stand *behind*
         * counters, so the closest a player can physically get is the far side of the desk --
         * a tighter range left those two impossible to talk to at all.
         */
        private const val TALK_RANGE_UNITS = 12.0
        /** Just clear of an NPC's head, as a fraction of their measured height. */
        private const val TALK_PROMPT_HEIGHT_FRACTION = 1.12
        private const val TALK_PROMPT_HEIGHT_UNITS = 7.6

        /**
         * TESTING AID: tools that never run out. Monofluid is here so techniques can be
         * exercised without a shop run between every cast -- the stack still shows and still
         * feeds the Mag, it simply doesn't decrement on use. Empty this set to restore normal
         * consumption before this is a real build.
         */
        private val UNLIMITED_TOOLS: Set<ToolType> = setOf(ToolType.MONOFLUID)

        /** PSO's rare box red. */
        private const val RARE_BOX_COLOR = 0xd42a2a

        /** Typed in chat to toggle noclip flight. */
        private const val FLY_COMMAND = "?fly?"

        /** Reports the nearest enemy's animation state into the chat log. */
        private const val DIAG_COMMAND = "?diag?"

        /** Toggles free casting for technique testing. */
        private const val TP_COMMAND = "?tp?" 

        /** Fires one of every particle primitive at the player -- visual smoke test. */
        private const val FX_COMMAND = "?fx?"

        /** Grants one weapon of every class, for exercising every motion set. */
        private const val ARMS_COMMAND = "?arms?"

        /** How far above the ground a dropped box floats, in PSO units. */
        private const val DROP_HOVER_UNITS = 0.25

        /** Each map's sky dome object, where the rips ship one. */
        private val SKY_FOR_MAP: Map<String, String> = mapOf(
            "forest01" to "Forest01Sky",
            "forest02" to "Forest02Sky",
        )

        /** The floor set each boss arena stands its fight on -- see the loose object specs. */
        private val BOSS_ARENA_FLOOR_PARTS: Map<String, List<String>> = mapOf(
            "bossArea1" to listOf(
                "BossArena1Floor", "BossArena1FloorPlate", "BossArena1Vent",
                "BossArena1Rock1", "BossArena1Rock2",
            ),
        )

        /** Marks a boss teleporter whose arena fight isn't built yet. */
        private const val BOSS_ARENA_PENDING = "boss-arena-pending"

        /** The maps a field teleporter may actually leave for today. */
        private val OPEN_MAPS = setOf(
            "pioneer2", "forest01", "forest02", "bossArea1",
            "cave01", "cave02", "cave03",
            "mines01", "mines02",
            "ruins01", "ruins02", "ruins03",
        )

        /**
         * PSO Episode 1's floor numbering, as the Teleporter objects' destination Floor ID
         * uses it: 0 Pioneer 2, 1-2 Forest, 3-5 Caves, 6-7 Mines, 8-10 Ruins.
         */
        private fun slugForFloor(floor: Int): String = when (floor) {
            0 -> "pioneer2"
            1 -> "forest01"
            2 -> "forest02"
            3 -> "cave01"; 4 -> "cave02"; 5 -> "cave03"
            6 -> "mines01"; 7 -> "mines02"
            8 -> "ruins01"; 9 -> "ruins02"; 10 -> "ruins03"
            else -> "pioneer2"
        }

        /** The boss-room warp home: how big it stands and how close you must step. */
        private const val RETURN_WARP_SCALE = 1.0
        private const val RETURN_WARP_RADIUS_UNITS = 3.5

        /**
         * Fallback crate radius, used only if the model can't be measured -- see
         * boxFootprintRadius. The player's cylinder is added to a weapon's reach, the same
         * "reach measures to the target's edge" rule enemies use.
         */
        private const val BOX_HIT_RADIUS_UNITS = 1.1
        private const val PLAYER_HITBOX_UNITS_FOR_BOXES = 1.0

        /** How the fragments of a smashed crate scatter. */
        private const val SHARD_SPREAD_UNITS = 6.0
        private const val SHARD_RISE_UNITS = 5.0
        private const val SHARD_GRAVITY_UNITS = 16.0
        private const val SHARD_SPIN = 7.0
        private const val SHARD_SECONDS = 0.9

        /** How wide a doorway's carve through the collision is, in world units. */
        private const val DOOR_PASSAGE_RADIUS = 26.0

        /** How far out a caster's lock reaches -- their spells engage well past the cane. */
        private const val TECH_FOCUS_RANGE_UNITS = 35.0

        /** Centre-of-mass estimate: radius-scaled with a floor and a cap, in PSO units. */
        private const val CENTER_MASS_RADIUS_FACTOR = 1.4
        private const val CENTER_MASS_BASE_UNITS = 0.6
        private const val CENTER_MASS_CAP_UNITS = 3.2

        /** The caster's own chest height, where projectiles leave from. */
        private const val PLAYER_CENTER_MASS_UNITS = 3.2

        /**
         * The Mag's size and station: behind the left shoulder, bobbing gently. Heights are in
         * PSO units, and one unit is small against the body -- the character stands about 6.7
         * units tall (a unit is 30% of the bounding-sphere radius, see psoUnit), so shoulder
         * height is ~5. The first attempt used 1.55 and parked the Mag at the hips.
         */
        private const val MAG_RADIUS_UNITS = 0.55
        private const val MAG_SIDE_UNITS = 0.9
        private const val MAG_BACK_UNITS = 0.7
        /**
         * Shoulder height as a fraction of the character's measured height, with the old
         * unit-based figure kept only as a fallback for a model that can't be measured.
         */
        private const val MAG_HEIGHT_FRACTION = 0.86
        private const val MAG_HEIGHT_UNITS = 5.9
        private const val MAG_BOB_RATE = 2.4
        private const val MAG_BOB_UNITS = 0.15

        /** Radius of a teleporter pad, and the distance at which stepping on one triggers it. */
        /** How close the player gets before a town door opens, and how long it takes. */
        private const val DOOR_OPEN_RADIUS = 34.0
        private const val DOOR_TRAVEL_SECONDS = 0.6

        private const val TELEPORTER_RADIUS = 12.0

        /** How far above/below a pad the player can be and still trigger it. */
        private const val TELEPORTER_HEIGHT_TOLERANCE = 30.0

        private const val ENEMY_DAMAGE = 10

        /** How far above an enemy's own origin its damage figures appear, in cylinder radii. */
        private const val LABEL_HEIGHT_FACTOR = 1.7

        /** How long being floored lasts. This project's own pacing. */
        private const val KNOCKDOWN_DURATION = 1.4
        private const val INVULNERABILITY_DURATION = 1.0

        /** Flinch length. Shorter than the i-frames, so back-to-back hits still read. */
        private const val HIT_REACTION_DURATION = 0.45

        /** How long the guard plays when an incoming attack is evaded. */
        private const val BLOCK_REACTION_DURATION = 0.4
        private const val RESPAWN_DELAY = 3.0

        /**
         * Crossfade into an attack swing: roughly two frames at PSO's 30fps, versus the soft
         * locomotion blend PlayerAnimator defaults to. Attacks have to cut -- blending a swing in
         * over 0.15s swallowed its wind-up and made every hit feel a beat late.
         */
        private const val ATTACK_CROSSFADE_DURATION = 0.06

        /**
         * Monest hive production -- this project's own pacing around the real behaviour (the
         * hive opens and releases while a player is near, keeping a small brood in the air).
         * Range and offset are in PSO units; the offset clears the hive's own 3.0-unit cylinder.
         */
        /**
         * Placeholder blast tuning until real per-Mag blasts exist: enough to erase a Booma pack
         * outright, felt across a wide ring. Radius in PSO units.
         */
        private const val PHOTON_BLAST_DAMAGE = 90
        private const val PHOTON_BLAST_RADIUS_UNITS = 9.0

        /** Sacrificial specials (here: Berserk) swing at 3.33x where a plain special is 0.56x. */
        private const val SACRIFICIAL_DAMAGE_MODIFIER = 3.33
        private const val BERSERK_HP_FLOOR = 3

        /** Walk-over collection distance for dropped weapons, in world units. */
        private const val PICKUP_RADIUS = 8.0

        /** Special-status tuning: the wiki's Normal-difficulty freeze cap; durations our own. */
        private const val FREEZE_CHANCE_CAP = 40.0
        private const val FREEZE_SECONDS = 5.0
        private const val PARALYSIS_SECONDS = 4.0
        private const val CONFUSION_SECONDS = 2.5
        private const val HP_DRAIN_CAP = 30.0

        private const val HIVE_EMIT_INTERVAL = 3.0
        private const val HIVE_MAX_MOTHMANTS = 3
        private const val HIVE_PRODUCTION_RANGE_UNITS = 14.0
        private const val HIVE_EMIT_OFFSET_UNITS = 3.5
        private val STAGE_SLUGS = setOf(
            "pioneer2",
            "lobbyBlack", "lobbyBlue", "lobbyBluegreen", "lobbyGreen", "lobbyOrange",
            "lobbyPurple", "lobbyRed", "lobbyWhite", "lobbyYellow", "lobbyYellowGreen",
            "bossArea1", "bossArea2", "bossArea3", "bossArea4",
            "ultimateBossArea1", "ultimateBossArea2", "ultimateBossArea3",
            "spaceship00", "spaceship01", "spaceship02",
            "temple00", "temple01", "temple02",
        )

        /** Town/lobby hubs only, unlike [STAGE_SLUGS] -- excludes boss arenas/spaceship/temple
         * stages, which are also Stage-format but where you do fight with a weapon drawn. */
        private val PEACEFUL_HUB_SLUGS = setOf(
            "pioneer2",
            "lobbyBlack", "lobbyBlue", "lobbyBluegreen", "lobbyGreen", "lobbyOrange",
            "lobbyPurple", "lobbyRed", "lobbyWhite", "lobbyYellow", "lobbyYellowGreen",
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
            // The real floor object is a flat plane at y = 0 spanning past +-200 in both axes
            // (ground-probe grid), with the vent's crater dipping to -11 at the exact origin --
            // which is where the old default (0, 0) spawn landed the player, under the lip of
            // the crater. Arrival belongs at the field's edge by the entry doors -- the gate
            // structure sits in the -Z wall (rig-confirmed) -- facing the centre the Dragon
            // holds.
            "bossArea1" to (.0 to -145.0),
            // The raft: arrival at its south end, facing up the deck the worm patrols.
            "bossArea2" to (.0 to 60.0),
            // The altar: arrival at the rim, the swarm pours in from all sides.
            "bossArea4" to (.0 to -70.0),
        )

        /** How far from the arena's centre the Dragon fight roams, in world units. */
        private const val DRAGON_ARENA_RADIUS = 190.0

        /** Corner distance of the lock-on's three triangles from the target, in pixels. */
        private const val RETICLE_RADIUS_PX = 26.0

        /** Breathing room between the target's silhouette and the dart tips, in pixels. */
        private const val RETICLE_GAP_PX = 6.0
        private const val RETICLE_MIN_RADIUS_PX = 16.0
        private const val RETICLE_MAX_RADIUS_PX = 150.0

        /** Where "head level" sits on the model's own measured top -- just under the crown. */
        private const val HEAD_LEVEL_FACTOR = 0.85

        /** How long a tapped Dragon part holds the lock before nearest-part resumes. */
        private const val DRAGON_PART_OVERRIDE_SECONDS = 6.0
        private const val DRAGON_PART_TAP_RANGE_PX = 100.0

        /** How close the player must come to a Caves heal ring for it to fire. */
        private const val HEAL_RING_RADIUS_UNITS = 3.0

        /** Slide distance for a Caves door whose model can't be measured. */
        private const val CAVE_DOOR_SLIDE_FALLBACK = 30.0

        // Enemy fire: how long a shot lives, how close it must pass, and the laser's stretch.
        private const val ENEMY_SHOT_LIFETIME = 4.0
        private const val ENEMY_SHOT_HIT_UNITS = 1.6
        private const val ENEMY_LASER_STRETCH = 3.0

        /** The Nano Dragon's nano laser: a fast bolt that crosses a whole cave room. */
        private const val NANO_LASER_SPEED_UNITS = 55.0
        private const val NANO_LASER_DAMAGE = 22
        private const val NANO_LASER_COLOR = 0x8a5cff

        /** The mines' and ruins' ranged species, in the same terms as the nano laser. */
        private const val GARANZ_MISSILE_SPEED_UNITS = 30.0
        private const val GARANZ_MISSILE_DAMAGE = 30
        private const val GARANZ_MISSILE_COLOR = 0xffa040
        private const val CANADINE_ZAP_SPEED_UNITS = 40.0
        private const val CANADINE_ZAP_DAMAGE = 12
        private const val CANADINE_ZAP_COLOR = 0xffe95c
        private const val SORCERER_ORB_SPEED_UNITS = 34.0
        private const val SORCERER_ORB_DAMAGE = 25
        private const val SORCERER_ORB_COLOR = 0x66aaff
        private const val BELRA_ARM_SPEED_UNITS = 45.0
        private const val BELRA_ARM_DAMAGE = 35
        private const val BELRA_ARM_COLOR = 0xffffff
        private const val GUNNER_LASER_SPEED_UNITS = 60.0
        private const val GUNNER_LASER_DAMAGE = 20
        private const val GUNNER_LASER_COLOR = 0x4de8ff

        /** A Lily's venom: slower, arcing spit that poisons where it lands. */
        private const val LILY_SPIT_SPEED_UNITS = 26.0
        private const val LILY_SPIT_DAMAGE = 12
        private const val LILY_SPIT_COLOR = 0x9cff5c

        /** Venom's clock: how long it runs, how often it bites, and for how much. */
        private const val POISON_SECONDS = 12.0
        private const val POISON_TICK_SECONDS = 1.5
        private const val POISON_TICK_DAMAGE = 4

        /** How often a Lily answers a ranged hit with its paralysing screech. */
        private const val LILY_SCREECH_CHANCE = 0.35

        /** The rare roll: each placement of a rare-capable species, at the real game's rate. */
        private const val RARE_ROLL_ONE_IN = 512
        private val RARE_TWINS = mapOf(
            "Rappy" to "AlRappy",
            "Hildebear" to "Hildeblue",
            "PoisonLily" to "NarLily",
            "PofuillySlimeBlue" to "PouillySlimeRed",
        )

        /** How long a downed Dubchic lies before its pod puts it back up. */
        private const val DUBCHIC_REVIVE_SECONDS = 6.0

        /** Quest NPC types to the converted city models that portray them. */
        private val QUEST_NPC_MODELS: Map<String, String> = mapOf(
            "Principal" to "Soutoku",
            "Irene" to "Hisyo",
            "GuildLady" to "Hisyo",
            "Tekker" to "GovStaff3",
            "Nurse" to "Nurse",
            "Scientist" to "Hakase",
            "RedSoldier" to "GuildStaff2",
            "BlueSoldier" to "GuildStaff1",
            "FemaleFat" to "CitizenWoman2",
            "FemaleMacho" to "CitizenWoman3",
            "FemaleTall" to "CitizenWoman4",
            "MaleDwarf" to "CitizenMan2",
            "MaleOld" to "CitizenMan5",
            "MaleMacho" to "CitizenMan1",
            "MaleFat" to "CitizenMan3",
        )

        /** Story NPCs spawned from the quest -- the ones the base hub roster doesn't cover. */
        private val QUEST_STORY_NPCS = setOf("Principal", "Irene")

        private val QUEST_NPC_NAMES: Map<String, String> = mapOf(
            "Principal" to "Principal Tyrell",
            "Irene" to "Irene",
            "GuildLady" to "Guild Receptionist",
            "Tekker" to "Tekker",
            "Nurse" to "Nurse",
            "Scientist" to "Scientist",
            "RedSoldier" to "Soldier",
            "BlueSoldier" to "Soldier",
        )

        /** The Tekker's flat appraisal fee. */
        private const val TEKKER_FEE = 100

        /** How much closer scenery must be than an enemy to steal the lock -- see findFocusTarget. */
        private const val FOCUS_ENEMY_BIAS = 0.6

        /** A dropped item's silhouette for the lock, in PSO units. */
        private const val PICKUP_FOCUS_RADIUS_UNITS = 1.0

        // The elemental floor traps. Fixed damage, PSO style: no defense roll softens a trap.
        private const val TRAP_SUBTYPE_FREEZE = 17
        private const val TRAP_SUBTYPE_CONFUSE = 18
        private const val TRAP_MARKER_RADIUS_UNITS = 1.1
        private const val TRAP_BLAST_RADIUS_UNITS = 7.0
        private const val TRAP_FIRE_DAMAGE = 30
        private const val TRAP_FREEZE_SECONDS = 3.0
        private const val TRAP_CONFUSE_SECONDS = 8.0

                /** The Lily's dying act: how far its burst reaches and what it costs to be there. */
        private const val LILY_BURST_RADIUS_UNITS = 6.0
        private const val LILY_BURST_DAMAGE = 30

        // The ceiling pillar: how close underneath sets it off, how hard it comes down and
        // returns, how wide the crush lands, and what standing under it costs.
        private const val PILLAR_TRIGGER_UNITS = 3.5
        private const val PILLAR_FALL_UNITS_PER_SECOND = 90.0
        private const val PILLAR_RISE_UNITS_PER_SECOND = 12.0
        private const val PILLAR_REST_SECONDS = 2.5
        private const val PILLAR_CRUSH_RADIUS_UNITS = 4.5
        private const val PILLAR_CRUSH_DAMAGE = 40

        // Dark Falz's circuit and weapons.
        private const val FALZ_ORBIT_RADIUS = 65.0
        private const val FALZ_VOLLEY_COUNT = 5
        private const val FALZ_VOLLEY_SPREAD = 0.16
        private const val FALZ_VOLLEY_SPEED_UNITS = 20.0
        private const val FALZ_VOLLEY_DAMAGE = 30
        private const val FALZ_DIVINE_RADIUS_UNITS = 6.0
        private const val FALZ_DIVINE_DELAY_SECONDS = 1.3

        // Vol Opt's room and weapons.
        private const val VOLOPT_MONITOR_RADIUS = 95.0
        private const val VOLOPT_MONITOR_HEIGHT = 20.0
        private const val VOLOPT_MISSILE_SPEED_UNITS = 14.0
        private const val VOLOPT_MISSILE_HOMING = 1.6
        private const val VOLOPT_PRISON_SPEED_UNITS = 12.0
        private const val VOLOPT_PRISON_CATCH_UNITS = 2.2
        private const val VOLOPT_CAGE_SECONDS = 2.4
        private const val VOLOPT_CAGE_RADIUS_UNITS = 3.0

        // De Rol Le's raft and deck hazards. The raft half-extents were measured off the
        // arena's own collision; damage figures live in DeRolLeFight (the wiki's Normal
        // values).
        private const val DEROLLE_RAFT_HALF_X = 55.0
        private const val DEROLLE_RAFT_HALF_Z = 80.0
        private const val DEROLLE_ORB_SPEED_UNITS = 26.0
        private const val DEROLLE_MINE_RADIUS_UNITS = 1.2
        private const val DEROLLE_MINE_FUSE_SECONDS = 2.6
        private const val DEROLLE_MINE_BLAST_UNITS = 6.0
        private const val DEROLLE_ROCK_RADIUS_UNITS = 2.2
        private const val DEROLLE_ROCK_DROP_HEIGHT = 60.0
        private const val DEROLLE_ROCK_FALL_UNITS_PER_SECOND = 55.0
        private const val DEROLLE_ROCK_BLAST_UNITS = 4.0
        private const val DEROLLE_BEAM_HALF_WIDTH = 7.0
        private const val DEROLLE_BEAM_TELEGRAPH_SECONDS = 0.8
        private const val DEROLLE_BEAM_BURN_SECONDS = 0.5

        /** How far the blob jar's venom splashes when it breaks, in PSO units. */
        private const val BLOB_SPLASH_RADIUS_UNITS = 5.0

        /** How much wider the large floor traps (types 12/13) blast than the standard ones. */
        private const val LARGE_TRAP_BLAST_SCALE = 1.5

        // The slimes' cycle: how flat the puddle lies, how close prey must come before it
        // rises, how long it stays up, and how thickly a spot can crowd with splits.
        private const val SLIME_PUDDLE_FLATTEN = 0.16
        private const val SLIME_RISE_RANGE_UNITS = 7.0
        private const val SLIME_RISEN_SECONDS = 3.2
        private const val SLIME_CAP = 5
        private const val SLIME_CAP_RANGE_UNITS = 40.0
        private const val SLIME_SPLIT_OFFSET_UNITS = 2.5

        /** The Telepipe beam, at a fraction of the city pads' size. */
        private const val TELEPIPE_SCALE = 0.55

        /** Where the town half of a pipe stands: beside the Main Ragol Teleporter's dais. */
        private const val TELEPIPE_CITY_X = 300.0
        private const val TELEPIPE_CITY_Z = 75.0

        /** Return trips arrive this far beside the field pipe, not inside it. */
        private const val TELEPIPE_ARRIVAL_OFFSET = 14.0

        /** The fx contact sheet's grid: columns per row and world units per cell. */
        private const val FX_SHEET_COLUMNS = 8
        private const val FX_SHEET_CELL = 7.0

        /** The golden cast ring: footprint in PSO units and how long the stamp plays. */
        private const val CAST_RING_UNITS = 4.4
        private const val CAST_RING_SECONDS = 0.8

        /** The rising rune glyphs beside a caster, and their pale-blue tint. */
        private const val CAST_GLYPH_SECONDS = 0.8
        private const val CAST_GLYPH_COLOR = 0x9db8ff

        private const val FOIE_BURST_SECONDS = 0.45
        private const val GRANTS_SEAL_SECONDS = 0.9

        /** ?fxslow=1's stretch factor -- see fxSlowMotion. */
        private const val FX_SLOW_FACTOR = 20.0

        /**
         * The Section ID split the Mag third-evolution tables run on: these five against the
         * other five (Greenill/Bluefull/Pinkal/Oran/Whitill).
         */
        private val MAG_SECTION_GROUP_A = setOf(
            SectionId.Viridia, SectionId.Skyly, SectionId.Purplenum,
            SectionId.Redria, SectionId.Yellowboze,
        )

        // The reference-scale spell furniture: ice formations, blast domes, ground lightning,
        // light pillars, debuff markers.
        private const val ICE_CRYSTAL_COLOR = 0xbfe8ff
        private const val ICE_CRYSTAL_SECONDS = 0.8
        private const val DOME_SECONDS = 0.5
        private const val CRAWL_SECONDS = 0.32
        private const val PILLAR_HEIGHT_UNITS = 22.0
        private const val PILLAR_RADIUS_UNITS = 2.4
        private const val PILLAR_SECONDS = 0.65
        private const val DEBUFF_MARKER_UNITS = 1.6
        private const val DEBUFF_MARKER_SECONDS = 1.4
        private const val RESTA_GLINT_COUNT = 16
    }
}
