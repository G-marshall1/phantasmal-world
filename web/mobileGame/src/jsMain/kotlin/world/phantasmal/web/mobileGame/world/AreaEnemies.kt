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
/**
 * The Caves' roster. The shark family runs on the Booma rig -- identical clip names, each
 * species shipping its own copies -- and the Lilies are rooted strikers whose "walk" is their
 * closed idle sway.
 */
private val CAVES: List<AreaEnemy> = listOf(
    AreaEnemy("EvilShark", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("PalShark", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("GuilShark", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("PoisonLily", "waitc_re2_b_root.njm", "attack_re2_b_root.njm"),
    AreaEnemy("NarLily", "waitc_re2_b_root.njm", "attack_re2_b_root.njm"),
    AreaEnemy("GrassAssasin", "walk_re1_b_base.njm", "lattack_re1_b_base.njm"),
    AreaEnemy("NanoDragoon", "walk_bm6_s_drc_body.njm", "beam_bm6_s_drc_body.njm"),
    AreaEnemy("PofuillySlimeBlue", "wait_bm4_ps_ma_body.njm", "atack_bm4_ps_ma_body.njm"),
    AreaEnemy("PouillySlimeRed", "wait_bm4_ps_ma_body.njm", "atack_bm4_ps_ma_body.njm"),
    AreaEnemy("PanArms", "walk_bm7_s_paa_body.njm", "beamdwn_bm7_s_paa_body.njm"),
)

/**
 * The Mines' roster: all machines. The Sinows cloak and leap; the Canadines fly; Garanz is a
 * walking missile battery. Dubchic and Gilchic share one rig (Gilchic is the gray recolor).
 */
private val MINES: List<AreaEnemy> = listOf(
    AreaEnemy("Dubchic", "walk01_me2_y_me2.njm", "scratch01_me2_me2.njm"),
    AreaEnemy("Gilchic", "walk01_me2_y_me2.njm", "scratch01_me2_me2.njm"),
    AreaEnemy("Garanz", "walk01_me4_y_me4.njm", "attack_me4_y_me4.njm"),
    // The Canadines have no walk at all -- they fly. Their hover loop stands in, and the mode
    // change is the closest thing the rig has to an attack wind-up.
    AreaEnemy("Canadine", "wait01_me1_y_mb.njm", "change01_me1_y_mb.njm"),
    AreaEnemy("Canane", "wait01_me1_y_mb.njm", "change01_me1_y_mb.njm"),
    AreaEnemy("SinowBeat", "walk_me3_y_me3.njm", "sword_me3_y_me3.njm"),
    AreaEnemy("SinowGold", "walk_me3_y_me3.njm", "sword_me3_y_me3.njm"),
)

/**
 * The Ruins' roster. The Dimenians run on the Booma rig (identical clip names, own copies);
 * everything else has its own. "BulclawOpen" is the fighting form of the Bulclaw body pair.
 */
private val RUINS: List<AreaEnemy> = listOf(
    AreaEnemy("Dimenian", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("LaDimenian", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("SoDimenian", BOOMA_WALK, BOOMA_ATTACK),
    AreaEnemy("Delsaber", "walk_df1_s_kil_body.njm", "atack_df1_s_kil_body.njm"),
    AreaEnemy("ChaosSorcerer", "wait_re4_b_body.njm", "attack1_re4_b_body.njm"),
    // The Sorcerer's Bees. Registered here even though no placement names one, because the
    // preloader only loads species this table knows: without a row the Bee is skipped, its
    // spawn returns null, and the Sorcerer fights alone. It has no clips of its own, so its
    // master's idle stands in for both -- the case this file's own note describes.
    AreaEnemy("SorcererBee", "wait_re4_b_body.njm", "wait_re4_b_body.njm"),
    AreaEnemy("DarkBelra", "walk_re7_b_body.njm", "rattack_re7_b_body.njm"),
    AreaEnemy("DarkGunner", "move_re5_b_body.njm", "attack_re5_b_body.njm"),
    AreaEnemy("ChaosBringer", "walk_bm8_s_kb_body.njm", "kiri_bm8_s_kb_body.njm"),
    AreaEnemy("BulclawOpen", "balwait_re6_b_bal_body.njm", "balattack_re6_b_bal_body.njm"),
    AreaEnemy("Claw", "clwait_re6_b_claw_body.njm", "clattack_re6_b_claw_body.njm"),
)

val AREA_ENEMIES: Map<String, List<AreaEnemy>> = mapOf(
    "forest01" to FOREST01,
    // Forest 2 shares Forest 1's species in the real game.
    "forest02" to FOREST01,
    "cave01" to CAVES,
    "cave02" to CAVES,
    "cave03" to CAVES,
    "mines01" to MINES,
    "mines02" to MINES,
    "ruins01" to RUINS,
    "ruins02" to RUINS,
    "ruins03" to RUINS,
    // The Dragon's arena: one species, the boss. "kiri" is its claw slash; the rest of its
    // 25-clip moveset (fire breath, flight, charges) arrives as the fight gains its phases.
    "bossArea1" to listOf(
        AreaEnemy("Dragon", "walk_boss1_s_nb_dragon.njm", "kiri_boss1_s_nb_dragon.njm"),
    ),
    // De Rol Le's raft: one species, the worm. Its full moveset drives through DeRolLeFight.
    "bossArea2" to listOf(
        AreaEnemy("DeRolLe", "forward_boss2_b_body.njm", "l_bite_boss2_b_body.njm"),
    ),
    // Vol Opt's control room: the core, the machine beneath the hatch, and the pillars.
    "bossArea3" to listOf(
        AreaEnemy("VolOptForm1", "wait_me5p01_y_all.njm", "attack_me5p01_y_all.njm"),
        AreaEnemy("VolOpt", "wait_me5p02_y_all.njm", "f_attack_me5p02_y_all.njm"),
        AreaEnemy("VolOptPillar", "fs_obj_hiraishin_a.njm", "fs_obj_hiraishin_a.njm"),
    ),
    // Dark Falz: the mounted form, the humanoid beneath it, and the swarm both call up.
    "bossArea4" to listOf(
        AreaEnemy("DarkFalzForm1Body", "wait_df1_s_body.njm", "hoe_df1_s_body.njm"),
        AreaEnemy("DarkFalzForm2Body", "wait_df2_s_body.njm", "hoe_df2_s_body.njm"),
        AreaEnemy("Darvant", "wait_df1_s_root.njm", "wait_df1_s_root.njm"),
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
    "cave01" -> "Cave 1"
    "cave02" -> "Cave 2"
    "cave03" -> "Cave 3"
    "mines01" -> "Mine 1"
    "mines02" -> "Mine 2"
    "ruins01" -> "Ruins 1"
    "ruins02" -> "Ruins 2"
    "ruins03" -> "Ruins 3"
    "bossArea1" -> "Dragon's Lair"
    "bossArea2" -> "Underground Waterway"
    "bossArea3" -> "Monitor Room"
    "bossArea4" -> "The Dark Altar"
    else -> mapSlug
}
