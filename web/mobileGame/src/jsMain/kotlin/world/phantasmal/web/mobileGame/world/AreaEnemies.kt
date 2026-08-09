package world.phantasmal.web.mobileGame.world

/**
 * One enemy species that spawns in an area, with the two clips its AI always needs.
 *
 * Clip names vary per family with no universal convention, so they're spelled out rather than
 * derived. A few species have no real walk clip at all -- Monest is a rooted plant -- and reuse a
 * standing clip as "walk", which EnemyAI is fine with: it just plays while the thing sits still.
 */
class AreaEnemy(
    val slug: String,
    val walkClip: String,
    val attackClip: String,
)

private val BOOMA_WALK = "walk_bm1_s_wala_body.njm"
private val BOOMA_ATTACK = "atackl_bm1_s_wala_body.njm"
private val RAPPY_WALK = "walk_re3_b_base.njm"
private val RAPPY_ATTACK = "attack_re3_b_base.njm"
private val WOLF_WALK = "walk_bm5_s_kem_body.njm"
private val WOLF_ATTACK = "okil_bm5_s_kem_body.njm"
private val HILDEBEAR_WALK = "walk_bm2f_s_moj_body.njm"
private val HILDEBEAR_ATTACK = "punch_bm2f_s_moj_body.njm"

/** Forest 1's real roster. */
private val FOREST01: List<AreaEnemy> = listOf(
    AreaEnemy("Booma", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("GoBooma", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("GigaBooma", BOOMA_WALK, BOOMA_ATTACK),
    // "Rappy" is the Rag Rappy; Al Rappy is its rare counterpart and shares the same clips.
    AreaEnemy("Rappy", RAPPY_WALK, RAPPY_ATTACK),
    AreaEnemy("AlRappy", RAPPY_WALK, RAPPY_ATTACK),
    AreaEnemy("SavageWolf", WOLF_WALK, WOLF_ATTACK),
    AreaEnemy("BarbarousWolf", WOLF_WALK, WOLF_ATTACK),
    AreaEnemy("Hildebear", HILDEBEAR_WALK, HILDEBEAR_ATTACK),
    AreaEnemy("Hildeblue", HILDEBEAR_WALK, HILDEBEAR_ATTACK),
    // Monest is rooted: it never walks, it opens up and sends Mothmants out. Its standing clip
    // stands in for "walk" so the AI has something to play while it sits there, and its "exit"
    // (release) clip rides in the attack slot for the hive-production logic to play per emission.
    AreaEnemy("Monest", "wait_bm3_s_nest.njm", "exit_bm3_s_nest.njm"),
    // Never placed by the spawn tables -- Monest hives produce them at runtime (see
    // GameRenderer's hive production), which is why the roster still needs the entry: a species
    // without one can't be spawned at all.
    AreaEnemy("Mothmant", "fly_bm3_fly_body.njm", "atack_bm3_fly_body.njm"),
)

/**
 * Which species belong in which area.
 *
 * Before this existed every map spawned the same 26-species arc regardless of where it was -- that
 * roster was a deliberate load-test sweep over the converted enemies, not a real encounter list,
 * and it put Ruins and Mines monsters in the Forest.
 *
 * Only the Forest rosters are known so far. An area with no entry spawns nothing, which is the
 * safer default: an empty area reads as unfinished, whereas the old behaviour looked broken.
 */
val AREA_ENEMIES: Map<String, List<AreaEnemy>> = mapOf(
    "forest01" to FOREST01,
    // Forest 2 shares Forest 1's species in the real game.
    "forest02" to FOREST01,
    // The Dragon's arena: one species, the boss. "kiri" is its claw slash; the rest of its
    // 25-clip moveset (fire breath, flight, charges) arrives as the fight gains its phases.
    "bossArea1" to listOf(
        AreaEnemy("Dragon", "walk_boss1_s_nb_dragon.njm", "kiri_boss1_s_nb_dragon.njm"),
    ),
)

/**
 * An area's name as the player should see it. Falls back to the slug for maps that don't have one
 * yet, which reads as unfinished rather than wrong.
 */
fun areaDisplayName(mapSlug: String): String = when (mapSlug) {
    "pioneer2" -> "Pioneer 2"
    "forest01" -> "Forest 1"
    "forest02" -> "Forest 2"
    "bossArea1" -> "Dragon's Lair"
    else -> mapSlug
}
