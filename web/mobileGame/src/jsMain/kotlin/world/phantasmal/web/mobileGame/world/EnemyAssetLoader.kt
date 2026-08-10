package world.phantasmal.web.mobileGame.world

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.psolib.fileFormats.ninja.XvrTexture
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseNjm
import world.phantasmal.psolib.fileFormats.ninja.parseXvm
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.rendering.conversion.ninjaObjectToSkinnedMesh
import world.phantasmal.web.externals.three.SkinnedMesh

/**
 * One extra static piece rendered alongside an enemy's main body -- mirrors EnemyFragment in
 * :web:assets-generation's EnemySpecs.kt (see its doc comment for the two sourcing patterns).
 * [pvmName] null means the fragment shares the main body's own texture pack; set means it was
 * generated with its own "${njName-without-extension}"-adjacent .xvm, named after [pvmName].
 */
class EnemyFragmentRef(val njName: String, val pvmName: String? = null)

/**
 * The skinned mesh plus the source [NjObject] its animation clips are built from. [fragments] are
 * a multi-part enemy's decorative/component pieces (see [EnemyFragmentRef]) -- empty for every
 * regular enemy. They're plain meshes, not bone-attached to [mesh]'s skeleton; the caller is
 * expected to add them as children of [mesh] so they at least track its root position/rotation.
 */
class EnemyMeshData(val mesh: SkinnedMesh, val njObject: NjObject, val fragments: List<SkinnedMesh> = emptyList())

/**
 * One species' parsed model and textures, ready to stamp out as many separate meshes as an area's
 * waves call for -- see [EnemyAssetLoader.loadPrototype].
 */
class EnemyPrototype(val njObject: NjObject, private val textures: List<XvrTexture?>) {
    fun instantiate(): SkinnedMesh = ninjaObjectToSkinnedMesh(njObject, textures, boundingVolumes = true)
}

/**
 * Loads any of the converted enemy models (see ENEMY_SPECS in :web:assets-generation's
 * EnemySpecs.kt for the full list of 77 slugs) as a combat test dummy. NjObject-based NPC models
 * use the same skinned-mesh pipeline as player characters (see the Viewer's MeshRenderer, which
 * branches on NjObject vs XjObject the same way).
 */
class EnemyAssetLoader(private val assetLoader: AssetLoader) {
    suspend fun loadEnemy(slug: String, fragments: List<EnemyFragmentRef> = emptyList()): EnemyMeshData {
        val njObject = parseNj(
            assetLoader.loadArrayBuffer("/npcs/$slug.nj").cursor(Endianness.Little)
        ).unwrap().first()

        val xvm = parseXvm(
            assetLoader.loadArrayBuffer("/npcs/$slug.xvm").cursor(Endianness.Little)
        ).unwrap()

        val mesh = ninjaObjectToSkinnedMesh(njObject, xvm.textures, boundingVolumes = true)

        val fragmentMeshes = fragments.map { fragment ->
            val fragmentObject = parseNj(
                assetLoader.loadArrayBuffer("/npcs/$slug/${fragment.njName}").cursor(Endianness.Little)
            ).unwrap().first()

            val fragmentTextures = if (fragment.pvmName == null) {
                xvm.textures
            } else {
                parseXvm(
                    assetLoader.loadArrayBuffer("/npcs/$slug/${fragment.pvmName}.xvm")
                        .cursor(Endianness.Little)
                ).unwrap().textures
            }

            ninjaObjectToSkinnedMesh(fragmentObject, fragmentTextures, boundingVolumes = true)
        }

        return EnemyMeshData(mesh, njObject, fragmentMeshes)
    }

    /**
     * [clipFileName] is psov2's own clip name, e.g. "walk_bm1_s_wala_body.njm".
     *
     * [njObject] is the model the clip animates, and passing it matters enormously. Enemy clips
     * are in the v2 NJM format, which doesn't record how many bones it covers -- the real client
     * always knew from the model. Without that, psolib has to guess where the per-bone offset
     * table ends, and the guess stops short: a Booma's walk covers 51 bones but yields 1, leaving
     * the whole body frozen in its bind pose while only the root shifted. Every enemy in the game
     * was affected. See parseNjm's own doc comment, and ObjectAssetLoader, which hit this first
     * with the city teleporter beams.
     */
    suspend fun loadAnimation(slug: String, clipFileName: String, njObject: NjObject): NjMotion {
        var boneCount = 0
        while (njObject.getBone(boneCount) != null) boneCount++

        return parseNjm(
            assetLoader.loadArrayBuffer("/npcs/$slug/$clipFileName").cursor(Endianness.Little),
            boneCount = boneCount.takeIf { it > 0 },
        )
    }

    /**
     * A species loaded once so that many of them can be put on the map. [loadEnemy] is fine for a
     * fixed handful of one-off models, but an area's encounter table spawns the same few species
     * over and over as its waves come round, and every one of those would otherwise re-fetch and
     * re-parse the model from scratch.
     *
     * Each instance still needs its own mesh -- a skinned mesh carries the skeleton its animation
     * mixer poses, so sharing one between two enemies would have them move as one. Only the
     * fetching and parsing is shared, which is the expensive part; [instantiate] does the rest.
     */
    suspend fun loadPrototype(slug: String): EnemyPrototype =
        prototypes.getOrPut(slug) {
            val njObject = parseNj(
                assetLoader.loadArrayBuffer("/npcs/$slug.nj").cursor(Endianness.Little)
            ).unwrap().first()

            val xvm = parseXvm(
                assetLoader.loadArrayBuffer("/npcs/$slug.xvm").cursor(Endianness.Little)
            ).unwrap()

            EnemyPrototype(njObject, xvm.textures)
        }

    /** An already-[loadPrototype]d species, for callers that can't suspend -- a wave spawning. */
    fun prototype(slug: String): EnemyPrototype? = prototypes[slug]

    private val prototypes = mutableMapOf<String, EnemyPrototype>()
}

/**
 * Every clip one species' AI can play. Loaded as a set so a wave spawning mid-play doesn't have to
 * fetch anything: the whole roster an area can produce is prepared while its loading screen is up.
 *
 * Walk and attack always exist (see [AreaEnemy]); the rest come from [ENEMY_ANIMATIONS] and are
 * null for families not catalogued yet, which EnemyAI already treats as "walk and attack only".
 */
class EnemyClipSet(
    val walk: NjMotion,
    val attack: NjMotion,
    val wait: NjMotion?,
    val damage: NjMotion?,
    val death: NjMotion?,
    val run: NjMotion?,
    val wakeUp: NjMotion?,
    val attackAlt: NjMotion?,
    val stun: NjMotion?,
    val appear: NjMotion?,
    /** Only the hives have these -- see HiveClips. */
    val hive: HiveClips? = null,
    /** The Dubchic's getting-up clip, for the Dubwitch revival loop. Null elsewhere. */
    val revive: NjMotion? = null,
)

/** A hive's deploy-and-collapse clips, loaded only for the species that has them. */
class HiveClips(
    val hang: NjMotion,
    val land: NjMotion,
    val down: NjMotion,
    val downWait: NjMotion,
    val downRelease: NjMotion,
    val downDamage: NjMotion,
)

suspend fun EnemyAssetLoader.loadClipSet(kind: AreaEnemy, njObject: NjObject): EnemyClipSet {
    val extra = ENEMY_ANIMATIONS[kind.slug]

    suspend fun clip(name: String) = loadAnimation(kind.slug, name, njObject)

    return EnemyClipSet(
        walk = clip(kind.walkClip),
        attack = clip(kind.attackClip),
        wait = extra?.let { clip(it.wait) },
        damage = extra?.let { clip(it.damage) },
        death = extra?.let { clip(it.death) },
        run = extra?.run?.let { clip(it) },
        hive = if (kind.slug == "Monest") {
            MONEST_HIVE_ANIMATIONS.let { h ->
                HiveClips(
                    hang = clip(h.hang),
                    land = clip(h.land),
                    down = clip(h.down),
                    downWait = clip(h.downWait),
                    downRelease = clip(h.downRelease),
                    downDamage = clip(h.downDamage),
                )
            }
        } else {
            null
        },
        wakeUp = extra?.wakeUp?.let { clip(it) },
        // Dubchics get back up as long as their room's Dubwitch stands -- the clip ships in
        // their own bml, it was simply never played.
        revive = if (kind.slug == "Dubchic") clip("revival_f_me2_y_me2.njm") else null,
        attackAlt = extra?.attackAlt?.let { clip(it) },
        stun = extra?.stun?.let { clip(it) },
        appear = extra?.appear?.let { clip(it) },
    )
}
