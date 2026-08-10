package world.phantasmal.web.assetsGeneration.psov2

/**
 * One enemy's psov2 source data. Derived by scanning psov2's AssetEnemies.js for each entry's
 * NinjaFile.API.load/gsl/bml calls -- unlike player classes, enemy texture IDs index directly
 * into their own dedicated pvm/texture pack in file order, so no per-enemy slot remapping is
 * needed (see Booma, the first one converted and visually verified). [animationNames] are the raw
 * psov2 .njm entry names within the same bml archive as the model (PRS-compressed only, no PRC
 * encryption -- that's specific to the player's shared plymotiondata.rlc, see Rlc.kt).
 *
 * Boss1 is left out (psov2 doesn't actually implement it -- points at a raw .bin with no
 * model-loading code). Vol Opt, Dark Falz, and Bulclaw are also still left out: each assembles
 * several fully independent, independently-animated hitboxes (not just decorative fragments) into
 * one encounter, which is closer to "several enemies that happen to move together" than one
 * multi-part model -- a bigger, boss-specific undertaking this doesn't attempt yet.
 *
 * Everything else originally thought to need that same treatment turned out not to: "Dubchic"/
 * "Dubchich" (and their "Damaged" forms) are each a single plain mesh once actually checked against
 * AssetEnemies.js (`setModel(this, mdl, [], tex)` -- an empty fragment list); "Sil Dragon" is just
 * the "Dragon" boss's Ultimate-difficulty archive with no fragments either. De Rol Le, Dal Ral Lie
 * (De Rol Le's own Ultimate reskin, same archive-suffix convention as the other "_a" Ultimate
 * variants elsewhere in this codebase), Garanz, and Baranz (Garanz's Ultimate reskin) round out
 * the genuinely multi-part roster -- see [EnemyFragment]'s doc comment for the two sourcing
 * patterns those four actually use.
 */
sealed class PvmSource {
    class FromBml(val name: String) : PvmSource()
    class FromGsl(val name: String) : PvmSource()
    class Standalone(val fileName: String) : PvmSource()
}

/**
 * One extra static .nj piece rendered alongside an enemy's main body (see
 * EnemyAssetLoader.loadEnemy in :web:mobileGame), not bone-attached to it. Sourced from the same
 * archive/bml entry as the main model. AssetEnemies.js uses this for two different things:
 * - De Rol Le/Dal Ral Lie: purely decorative fins/sting/tentacle/breakable-shell pieces that share
 *   the main body's own texture pack ([pvmName] left null).
 * - Garanz/Baranz: wreckage/mine/missile pieces that each carry their OWN separate texture pack
 *   ([pvmName] set) -- verified against AssetEnemies.js's own "Garanz"/"Baranz" loaders, which
 *   parse a fresh `NinjaTexture.API.parse(bml[key.pvm])` per fragment instead of reusing `tex`.
 */
class EnemyFragment(val njName: String, val pvmName: String? = null)

class EnemySpec(
    val slug: String,
    val archive: String,
    val isGsl: Boolean,
    val bmlEntry: String?,
    val pvmSource: PvmSource,
    val njName: String,
    val animationNames: List<String>,
    val fragments: List<EnemyFragment> = emptyList(),
)

private val VOL_OPT_FRAGMENTS: List<EnemyFragment> = listOf(
    EnemyFragment("fe_obj_hira_kage.nj", "fe_obj_hira_kage.pvm"),
    EnemyFragment("fe_obj_vo_mo_dai_aka.nj", "fe_obj_vo_mo_dai_aka.pvm"),
    EnemyFragment("fe_obj_vo_mo_dai_ao.nj", "fe_obj_vo_mo_dai_ao.pvm"),
    EnemyFragment("fe_obj_vo_mo_dai_hakai.nj", "fe_obj_vo_mo_dai_hakai.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho01_aka.nj", "fe_obj_vo_mo_sho01_aka.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho01_ao.nj", "fe_obj_vo_mo_sho01_ao.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho01_hakai.nj", "fe_obj_vo_mo_sho01_hakai.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho02_aka.nj", "fe_obj_vo_mo_sho02_aka.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho02_ao.nj", "fe_obj_vo_mo_sho02_ao.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho02_hakai.nj", "fe_obj_vo_mo_sho02_hakai.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho03_aka.nj", "fe_obj_vo_mo_sho03_aka.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho03_ao.nj", "fe_obj_vo_mo_sho03_ao.pvm"),
    EnemyFragment("fe_obj_vo_mo_sho03_hakai.nj", "fe_obj_vo_mo_sho03_hakai.pvm"),
    EnemyFragment("fe_obj_vo_futa_moto.nj", "fe_obj_vo_futa_moto.pvm"),
    EnemyFragment("fe_obj_vo_tenjo_hahen01.nj", "fe_obj_vo_tenjo_hahen01.pvm"),
    EnemyFragment("fe_obj_vo_tenjo_hahen02.nj", "fe_obj_vo_tenjo_hahen02.pvm"),
    EnemyFragment("fs_obj_hiraishin_a.nj", "fs_obj_hiraishin_a.pvm"),
    EnemyFragment("me5p02_y_cage.nj", "me5p02_y_cage.pvm"),
    EnemyFragment("me5p02_y_missile.nj", "me5p02_y_missile.pvm"),
    EnemyFragment("me5p02_y_pillar.nj", "me5p02_y_pillar.pvm"),
)

val ENEMY_SPECS: List<EnemySpec> = listOf(
    EnemySpec("Rappy", "gsl_forest01.gsl", true, "bm_ene_lappy.bml", PvmSource.FromBml("re3_b_lappy_base.pvm"), "re3_b_lappy_base.nj", listOf("attack_re3_b_base.njm", "damage_re3_b_base.njm", "die_re3_b_base.njm", "run_re3_b_base.njm", "tumble_re3_b_base.njm", "wait_re3_b_base.njm", "wait2_re3_b_base.njm", "wake_re3_b_base.njm", "wake2_re3_b_base.njm", "walk_re3_b_base.njm")),
    EnemySpec("AlRappy", "gsl_forest01.gsl", true, "bm_ene_lappy.bml", PvmSource.FromGsl("re3_b_lappy_base_ao.pvm"), "re3_b_lappy_base.nj", listOf("attack_re3_b_base.njm", "damage_re3_b_base.njm", "die_re3_b_base.njm", "run_re3_b_base.njm", "tumble_re3_b_base.njm", "wait_re3_b_base.njm", "wait2_re3_b_base.njm", "wake_re3_b_base.njm", "wake2_re3_b_base.njm", "walk_re3_b_base.njm")),
    EnemySpec("Booma", "gsl_forest01.gsl", true, "bm_ene_re8_b_beast.bml", PvmSource.FromBml("re8_b_beast_wola_body.pvm"), "re8_b_beast_wola_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("GoBooma", "gsl_forest01.gsl", true, "bm_ene_re8_b_beast.bml", PvmSource.FromBml("re8_b_srdbeast_wola_body.pvm"), "re8_b_srdbeast_wola_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("GigaBooma", "gsl_forest01.gsl", true, "bm_ene_re8_b_beast.bml", PvmSource.FromBml("re8_b_rdbeast_wola_body.pvm"), "re8_b_rdbeast_wola_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("SavageWolf", "gsl_forest01.gsl", true, "bm_ene_bm5_wolf.bml", PvmSource.FromBml("bm5_s_kem_body.pvm"), "bm5_s_kem_body.nj", listOf("dams_bm5_s_kem_body.njm", "deadr_bm5_s_kem_body.njm", "eat_bm5_s_kem_body.njm", "hoe_bm5_s_kem_body.njm", "hunt_bm5_s_kem_body.njm", "okil_bm5_s_kem_body.njm", "okir_bm5_s_kem_body.njm", "run_bm5_s_kem_body.njm", "runb_bm5_s_kem_body.njm", "sleep_bm5_s_kem_body.njm", "stdup_bm5_s_kem_body.njm", "wait_bm5_s_kem_body.njm", "walk_bm5_s_kem_body.njm")),
    EnemySpec("BarbarousWolf", "gsl_forest01.gsl", true, "bm_ene_bm5_wolf.bml", PvmSource.FromBml("bm5_s_kem_body.pvm"), "bm5_s_keml_body.nj", listOf("dams_bm5_s_kem_body.njm", "deadr_bm5_s_kem_body.njm", "eat_bm5_s_kem_body.njm", "hoe_bm5_s_kem_body.njm", "hunt_bm5_s_kem_body.njm", "okil_bm5_s_kem_body.njm", "okir_bm5_s_kem_body.njm", "run_bm5_s_kem_body.njm", "runb_bm5_s_kem_body.njm", "sleep_bm5_s_kem_body.njm", "stdup_bm5_s_kem_body.njm", "wait_bm5_s_kem_body.njm", "walk_bm5_s_kem_body.njm")),
    EnemySpec("Monest", "gsl_forest01.gsl", true, "bm_ene_bm3_fly.bml", PvmSource.FromBml("bm3_fly_body.pvm"), "bm3_s_nest.nj", listOf("dam_bm3_s_nest.njm", "dead_bm3_s_nest.njm", "down_bm3_s_nest.njm", "dwndam_bm3_s_nest.njm", "dwnexit_bm3_s_nest.njm", "dwnwait_bm3_s_nest.njm", "exit_bm3_s_nest.njm", "land_bm3_s_nest.njm", "trance_bm3_s_nest.njm", "wait_bm3_s_nest.njm")),
    EnemySpec("Mothmant", "gsl_forest01.gsl", true, "bm_ene_bm3_fly.bml", PvmSource.FromBml("bm3_fly_body.pvm"), "bm3_fly_body.nj", listOf("atack_bm3_fly_body.njm", "damage_bm3_fly_body.njm", "dead_bm3_fly_body.njm", "fly_bm3_fly_body.njm", "move_bm3_fly_body.njm")),
    EnemySpec("Hildebaby", "gsl_forest02.gsl", true, "bm_ene_bm2_moja.bml", PvmSource.FromBml("bm2c_s_moj_body.pvm"), "bm2c_s_moj_body.nj", listOf("cstand_bm2c_s_moj_body.njm", "cwalk_bm2c_s_moj_body.njm", "damage_bm2f_s_moj_body.njm", "dead_bm2f_s_moj_body.njm", "deadb_bm2f_s_moj_body.njm", "giva_bm2f_s_moj_body.njm", "hoe_bm2f_s_moj_body.njm", "jump_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm", "stand_bm2f_s_moj_body.njm", "walk_bm2f_s_moj_body.njm")),
    EnemySpec("Hildebear", "gsl_forest02.gsl", true, "bm_ene_bm2_moja.bml", PvmSource.FromBml("bm2f_s_moj_body.pvm"), "bm2f_s_moj_body.nj", listOf("cstand_bm2c_s_moj_body.njm", "cwalk_bm2c_s_moj_body.njm", "damage_bm2f_s_moj_body.njm", "dead_bm2f_s_moj_body.njm", "deadb_bm2f_s_moj_body.njm", "giva_bm2f_s_moj_body.njm", "hoe_bm2f_s_moj_body.njm", "jump_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm", "stand_bm2f_s_moj_body.njm", "walk_bm2f_s_moj_body.njm")),
    EnemySpec("Hildeblue", "gsl_forest02.gsl", true, "bm_ene_bm2_moja.bml", PvmSource.FromBml("bm2w_s_moj_body.pvm"), "bm2w_s_moj_body.nj", listOf("cstand_bm2c_s_moj_body.njm", "cwalk_bm2c_s_moj_body.njm", "damage_bm2f_s_moj_body.njm", "dead_bm2f_s_moj_body.njm", "deadb_bm2f_s_moj_body.njm", "giva_bm2f_s_moj_body.njm", "hoe_bm2f_s_moj_body.njm", "jump_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm", "stand_bm2f_s_moj_body.njm", "walk_bm2f_s_moj_body.njm")),
    EnemySpec("Dragon", "bm_boss1_dragon.bml", false, null, PvmSource.FromBml("boss1_s_nb_dragon.pvm"), "boss1_s_nb_dragon.nj", listOf("daml_boss1_s_nb_dragon.njm", "dams_boss1_s_nb_dragon.njm", "dead_boss1_s_nb_dragon.njm", "down_boss1_s_nb_dragon.njm", "fire_boss1_s_nb_dragon.njm", "fly_boss1_s_nb_dragon.njm", "flyshot_boss1_s_nb_dragon.njm", "frin_boss1_s_nb_dragon.njm", "frloop_boss1_s_nb_dragon.njm", "frout_boss1_s_nb_dragon.njm", "kiri_boss1_s_nb_dragon.njm", "land_boss1_s_nb_dragon.njm", "lift_boss1_s_nb_dragon.njm", "nkdown_boss1_s_nb_dragon.njm", "nkup_boss1_s_nb_dragon.njm", "nobi_boss1_s_nb_dragon.njm", "stand_boss1_s_nb_dragon.njm", "tatk_boss1_s_nb_dragon.njm", "tobidasi_boss1_s_nb_dragon.njm", "tukomi_boss1_s_nb_dragon.njm", "walk_boss1_s_nb_dragon.njm", "wgwalk_boss1_s_nb_dragon.njm", "wing_boss1_s_nb_dragon.njm", "wngclose_boss1_s_nb_dragon.njm", "wngopn_boss1_s_nb_dragon.njm")),
    EnemySpec("EvilShark", "gsl_cave01.gsl", true, "bm_ene_bm1_shark.bml", PvmSource.FromBml("bm1_s_wala_body.pvm"), "bm1_s_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("PalShark", "gsl_cave01.gsl", true, "bm_ene_bm1_shark.bml", PvmSource.FromBml("bm1f_s_wala_body.pvm"), "bm1f_s_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("GuilShark", "gsl_cave01.gsl", true, "bm_ene_bm1_shark.bml", PvmSource.FromBml("bm1tl_s_wala_body.pvm"), "bm1tl_s_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("PanArms", "gsl_cave01.gsl", true, "bm7_s_paa_body.bml", PvmSource.FromBml("bm7_s_paa_body.pvm"), "bm7_s_paa_body.nj", listOf("beamdwn_bm7_s_paa_body.njm", "beamup_bm7_s_paa_body.njm", "btamed_bm7_s_paa_body.njm", "btameu_bm7_s_paa_body.njm", "cry_bm7_s_paa_body.njm", "damage_bm7_s_paa_body.njm", "deada_bm7_s_paa_body.njm", "deadb_bm7_s_paa_body.njm", "wait_bm7_s_paa_body.njm", "walk_bm7_s_paa_body.njm", "walkl_bm7_s_paa_body.njm", "walkr_bm7_s_paa_body.njm")),
    EnemySpec("Hidoom", "gsl_cave01.gsl", true, "bm7_s_paa_body.bml", PvmSource.FromBml("bm7_s_pal_body.pvm"), "bm7_s_pal_body.nj", listOf("atack_bm7_s_pal_body.njm", "bunri_bm7_s_pal_body.njm", "damage_bm7_s_pal_body.njm", "deada_bm7_s_pal_body.njm", "deadb_bm7_s_pal_body.njm", "gattai_bm7_s_pal_body.njm", "heal_bm7_s_pal_body.njm", "run_bm7_s_pal_body.njm", "wait_bm7_s_pal_body.njm", "walk_bm7_s_pal_body.njm")),
    EnemySpec("Migium", "gsl_cave01.gsl", true, "bm7_s_paa_body.bml", PvmSource.FromBml("bm7_s_par_body.pvm"), "bm7_s_par_body.nj", listOf("atack_bm7_s_par_body.njm", "bunri_bm7_s_par_body.njm", "damage_bm7_s_par_body.njm", "deada_bm7_s_par_body.njm", "deadb_bm7_s_par_body.njm", "gattai_bm7_s_par_body.njm", "run_bm7_s_par_body.njm", "wait_bm7_s_par_body.njm", "walk_bm7_s_par_body.njm")),
    EnemySpec("MiniGrassAssasin", "gsl_cave01.gsl", true, "bm_ene_cgrass.bml", PvmSource.FromBml("re1c_b_cgrass_base.pvm"), "re1c_b_cgrass_base.nj", listOf("re1c_b_cgrass_base.njm")),
    EnemySpec("GrassAssasin", "gsl_cave01.gsl", true, "bm_ene_grass.bml", PvmSource.FromBml("re1_b_grass_base.pvm"), "re1_b_grass_base.nj", listOf("damege_re1_b_base.njm", "die_re1_b_base.njm", "lattack_re1_b_base.njm", "mad_re1_b_base.njm", "rattack_re1_b_base.njm", "spit_re1_b_base.njm", "wait_re1_b_base.njm", "walk_re1_b_base.njm")),
    EnemySpec("NanoDragoon", "gsl_cave01.gsl", true, "bm_ene_nanodrago.bml", PvmSource.FromBml("bm6_s_drc_body.pvm"), "bm6_s_drc_body.nj", listOf("beam_bm6_s_drc_body.njm", "damfly_bm6_s_drc_body.njm", "damgrd_bm6_s_drc_body.njm", "deadg_bm6_s_drc_body.njm", "deads_bm6_s_drc_body.njm", "fly_bm6_s_drc_body.njm", "joy_bm6_s_drc_body.njm", "land_bm6_s_drc_body.njm", "lasfly_bm6_s_drc_body.njm", "lift_bm6_s_drc_body.njm", "wait_bm6_s_drc_body.njm", "walk_bm6_s_drc_body.njm")),
    EnemySpec("PoisonLily", "gsl_cave01.gsl", true, "bm_ene_re2_flower.bml", PvmSource.FromBml("re2_b_flower_root.pvm"), "re2_b_flower_root.nj", listOf("attack_re2_b_root.njm", "damege_re2_b_root.njm", "die_re2_b_root.njm", "laugh_re2_b_root.njm", "waitc_re2_b_root.njm", "waito_re2_b_root.njm", "wake_re2_b_root.njm")),
    EnemySpec("NarLily", "gsl_cave02.gsl", true, "bm_ene_re2_flower.bml", PvmSource.FromBml("re2_b_flower_root.pvm"), "re2_b_flower_root.nj", listOf("attack_re2_b_root.njm", "damege_re2_b_root.njm", "die_re2_b_root.njm", "laugh_re2_b_root.njm", "waitc_re2_b_root.njm", "waito_re2_b_root.njm", "wake_re2_b_root.njm")),
    EnemySpec("PofuillySlimeBlue", "gsl_cave02.gsl", true, "bm4_ps_ma_body.bml", PvmSource.FromBml("bm4_ps_mb_body.pvm"), "bm4_ps_mbr_body.nj", listOf("atack_bm4_ps_ma_body.njm", "damage_bm4_ps_ma_body.njm", "kie_bm4_ps_ma_body.njm", "nodam_bm4_ps_ma_body.njm", "wait_bm4_ps_ma_body.njm", "wait2_bm4_ps_ma_body.njm", "tlatk_bm4_ps_ma_tail.njm", "apear_bm4_ps_mb_body.njm", "kie_bm4_ps_mb_body.njm", "move_bm4_ps_mb_body.njm", "wait_bm4_ps_mb_body.njm")),
    EnemySpec("PouillySlimeRed", "gsl_cave02.gsl", true, "bm4_ps_ma_body.bml", PvmSource.FromBml("bm4_ps_mbr_body.pvm"), "bm4_ps_mbr_body.nj", listOf("atack_bm4_ps_ma_body.njm", "damage_bm4_ps_ma_body.njm", "kie_bm4_ps_ma_body.njm", "nodam_bm4_ps_ma_body.njm", "wait_bm4_ps_ma_body.njm", "wait2_bm4_ps_ma_body.njm", "tlatk_bm4_ps_ma_tail.njm", "apear_bm4_ps_mb_body.njm", "kie_bm4_ps_mb_body.njm", "move_bm4_ps_mb_body.njm", "wait_bm4_ps_mb_body.njm")),
    EnemySpec("Canadine", "gsl_machine01.gsl", true, "bm_ene_me1_mb.bml", PvmSource.FromBml("me1_y_mb.pvm"), "me1n_y_mb.nj", listOf("change02_me1_y_mb.njm", "change01_me1_y_mb.njm", "damage01_me1_y_mb.njm", "damage02_me1_y_mb.njm", "wait01_me1_y_mb.njm", "wait02_me1_y_mb.njm")),
    EnemySpec("Canane", "gsl_machine01.gsl", true, "bm_ene_me1_mb.bml", PvmSource.FromBml("me1_y_mb.pvm"), "me1_y_mb.nj", listOf("change02_me1_y_mb.njm", "change01_me1_y_mb.njm", "damage01_me1_y_mb.njm", "damage02_me1_y_mb.njm", "wait01_me1_y_mb.njm", "wait02_me1_y_mb.njm")),
    EnemySpec("SinowBeat", "bm_ene_me3_shinowa_a.bml", false, null, PvmSource.FromBml("me3_y_me3.pvm"), "me3_y_me3.nj", listOf("apper_me3_y_me3.njm", "backstep_me3_y_me3.njm", "damage_me3_y_me3.njm", "death_me3_y_me3.njm", "f_attack_me3_y_me3.njm", "sword_me3_y_me3.njm", "transform_me3_y_me3.njm", "t_wait_me3_y_me3.njm", "wait_me3_y_me3.njm", "walk_me3_y_me3.njm")),
    EnemySpec("SinowGold", "bm_ene_me3_shinowa_a.bml", false, null, PvmSource.FromBml("me3_y_me3.pvm"), "me3_y_me3.nj", listOf("apper_me3_y_me3.njm", "backstep_me3_y_me3.njm", "damage_me3_y_me3.njm", "death_me3_y_me3.njm", "f_attack_me3_y_me3.njm", "sword_me3_y_me3.njm", "transform_me3_y_me3.njm", "t_wait_me3_y_me3.njm", "wait_me3_y_me3.njm", "walk_me3_y_me3.njm")),
    EnemySpec("Delsaber", "gsl_ancient01.gsl", true, "bm_ene_df1_saver.bml", PvmSource.FromBml("df1_s_kil_body.pvm"), "df1_s_kil_body.nj", listOf("aseri_df1_s_kil_body.njm", "atack_df1_s_kil_body.njm", "damage_df1_s_kil_body.njm", "deadb_df1_s_kil_body.njm", "dead_df1_s_kil_body.njm", "defdam_df1_s_kil_body.njm", "defence_df1_s_kil_body.njm", "jump_df1_s_kil_body.njm", "nail_df1_s_kil_body.njm", "wait_df1_s_kil_body.njm", "walk_df1_s_kil_body.njm")),
    EnemySpec("Dimenian", "gsl_ancient01.gsl", true, "bm_ene_df3_dimedian.bml", PvmSource.FromBml("df3_s_wala_body.pvm"), "df3_s_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("LaDimenian", "gsl_ancient01.gsl", true, "bm_ene_df3_dimedian.bml", PvmSource.FromBml("df3_ssl_wala_body.pvm"), "df3_ssl_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("SoDimenian", "gsl_ancient01.gsl", true, "bm_ene_df3_dimedian.bml", PvmSource.FromBml("df3_sl_wala_body.pvm"), "df3_sl_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("ChaosSorcerer", "gsl_ancient01.gsl", true, "bm_ene_re4_sorcerer.bml", PvmSource.FromBml("re4_b_sorcer_body.pvm"), "re4_b_sorcer_body.nj", listOf("attack2_re4_b_body.njm", "attack3_re4_b_body.njm", "attack1_re4_b_body.njm", "cure_re4_b_body.njm", "damage_re4_b_body.njm", "die_re4_b_body.njm", "enter_re4_b_body.njm", "wait_re4_b_body.njm")),
    EnemySpec("DarkBelra", "gsl_ancient01.gsl", true, "bm_ene_re7_berura.bml", PvmSource.FromBml("re7_b_bell_body.pvm"), "re7_b_bell_body.nj", listOf("attack_re7_b_body.njm", "damege_re7_b_body.njm", "die_re7_b_body.njm", "lattack_re7_b_body.njm", "memai_re7_b_body.njm", "rattack_re7_b_body.njm", "wait_re7_b_body.njm", "walk_re7_b_body.njm")),
    EnemySpec("DarkGunner", "gsl_ancient03.gsl", true, "bm_ene_darkgunner.bml", PvmSource.FromBml("re5_b_gunner_body.pvm"), "re5_b_gunner_body.nj", listOf("attack_re5_b_body.njm", "await_re5_b_body.njm", "damage2_re5_b_body.njm", "damage_re5_b_body.njm", "die_re5_b_body.njm", "drop_re5_b_body.njm", "duckdame_re5_b_body.njm", "duckdie_re5_b_body.njm", "duckroop_re5_b_body.njm", "duckwake_re5_b_body.njm", "duck_re5_b_body.njm", "move_re5_b_body.njm", "pullback_re5_b_body.njm", "wait_re5_b_body.njm")),
    EnemySpec("ChaosBringer", "gsl_ancient03.gsl", true, "bm_ene_df2_bringer.bml", PvmSource.FromBml("bm8_s_kb_body.pvm"), "bm8_s_kb_body.nj", listOf("beam_bm8_s_kb_body.njm", "cold_bm8_s_kb_body.njm", "damage_bm8_s_kb_body.njm", "dead_bm8_s_kb_body.njm", "hoe_bm8_s_kb_body.njm", "kamae_bm8_s_kb_body.njm", "kiri_bm8_s_kb_body.njm", "run_bm8_s_kb_body.njm", "tpkyu_bm8_s_kb_body.njm", "wait_bm8_s_kb_body.njm", "walk_bm8_s_kb_body.njm")),
    EnemySpec("Vulmer", "bm_ene_bm1_shark_a.bml", false, null, PvmSource.FromBml("bm1_s_wala_body.pvm"), "bm1_s_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("GoVulmer", "bm_ene_bm1_shark_a.bml", false, null, PvmSource.FromBml("bm1f_s1_wala_body.pvm"), "bm1f_s1_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("Melqueek", "bm_ene_bm1_shark_a.bml", false, null, PvmSource.FromBml("bm1tl_s_wala_body.pvm"), "bm1tl_s_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("HideltBaby", "bm_ene_bm2_moja_a.bml", false, null, PvmSource.FromBml("bm2c_s_moj_body.pvm"), "bm2c_s_moj_body.nj", listOf("cstand_bm2c_s_moj_body.njm", "cwalk_bm2c_s_moj_body.njm", "damage_bm2f_s_moj_body.njm", "dead_bm2f_s_moj_body.njm", "deadb_bm2f_s_moj_body.njm", "giva_bm2f_s_moj_body.njm", "hoe_bm2f_s_moj_body.njm", "jump_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm", "stand_bm2f_s_moj_body.njm", "walk_bm2f_s_moj_body.njm")),
    EnemySpec("Hidelt", "bm_ene_bm2_moja_a.bml", false, null, PvmSource.FromBml("bm2f_s_moj_body.pvm"), "bm2f_s_moj_body.nj", listOf("cstand_bm2c_s_moj_body.njm", "cwalk_bm2c_s_moj_body.njm", "damage_bm2f_s_moj_body.njm", "dead_bm2f_s_moj_body.njm", "deadb_bm2f_s_moj_body.njm", "giva_bm2f_s_moj_body.njm", "hoe_bm2f_s_moj_body.njm", "jump_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm", "stand_bm2f_s_moj_body.njm", "walk_bm2f_s_moj_body.njm")),
    EnemySpec("Hildetor", "bm_ene_bm2_moja_a.bml", false, null, PvmSource.FromBml("bm2w_s_moj_body.pvm"), "bm2w_s_moj_body.nj", listOf("cstand_bm2c_s_moj_body.njm", "cwalk_bm2c_s_moj_body.njm", "damage_bm2f_s_moj_body.njm", "dead_bm2f_s_moj_body.njm", "deadb_bm2f_s_moj_body.njm", "giva_bm2f_s_moj_body.njm", "hoe_bm2f_s_moj_body.njm", "jump_bm2f_s_moj_body.njm", "punch_bm2f_s_moj_body.njm", "stand_bm2f_s_moj_body.njm", "walk_bm2f_s_moj_body.njm")),
    EnemySpec("Mothvist", "bm_ene_bm3_fly_a.bml", false, null, PvmSource.FromBml("bm3_fly_body.pvm"), "bm3_s_nest.nj", listOf("dam_bm3_s_nest.njm", "dead_bm3_s_nest.njm", "down_bm3_s_nest.njm", "dwndam_bm3_s_nest.njm", "dwnexit_bm3_s_nest.njm", "dwnwait_bm3_s_nest.njm", "exit_bm3_s_nest.njm", "land_bm3_s_nest.njm", "trance_bm3_s_nest.njm", "wait_bm3_s_nest.njm")),
    EnemySpec("Mothvert", "bm_ene_bm3_fly_a.bml", false, null, PvmSource.FromBml("bm3_fly_body.pvm"), "bm3_fly_body.nj", listOf("atack_bm3_fly_body.njm", "damage_bm3_fly_body.njm", "dead_bm3_fly_body.njm", "fly_bm3_fly_body.njm", "move_bm3_fly_body.njm")),
    EnemySpec("Gulgus", "bm_ene_bm5_wolf_a.bml", false, null, PvmSource.FromBml("bm5_s_kem_body.pvm"), "bm5_s_kem_body.nj", listOf("dams_bm5_s_kem_body.njm", "deadr_bm5_s_kem_body.njm", "eat_bm5_s_kem_body.njm", "hoe_bm5_s_kem_body.njm", "hunt_bm5_s_kem_body.njm", "okil_bm5_s_kem_body.njm", "okir_bm5_s_kem_body.njm", "run_bm5_s_kem_body.njm", "runb_bm5_s_kem_body.njm", "sleep_bm5_s_kem_body.njm", "stdup_bm5_s_kem_body.njm", "wait_bm5_s_kem_body.njm", "walk_bm5_s_kem_body.njm")),
    EnemySpec("GulgusGue", "bm_ene_bm5_wolf_a.bml", false, null, PvmSource.FromBml("bm5_s_kem_body.pvm"), "bm5_s_keml_body.nj", listOf("dams_bm5_s_kem_body.njm", "deadr_bm5_s_kem_body.njm", "eat_bm5_s_kem_body.njm", "hoe_bm5_s_kem_body.njm", "hunt_bm5_s_kem_body.njm", "okil_bm5_s_kem_body.njm", "okir_bm5_s_kem_body.njm", "run_bm5_s_kem_body.njm", "runb_bm5_s_kem_body.njm", "sleep_bm5_s_kem_body.njm", "stdup_bm5_s_kem_body.njm", "wait_bm5_s_kem_body.njm", "walk_bm5_s_kem_body.njm")),
    EnemySpec("MiniCrimsonAssassin", "bm_ene_cgrass_a.bml", false, null, PvmSource.FromBml("re1c_b_cgrass_base.pvm"), "re1c_b_cgrass_base.nj", listOf("re1c_b_cgrass_base.njm")),
    EnemySpec("CrimsonAssassin", "bm_ene_grass_a.bml", false, null, PvmSource.FromBml("re1_b_grass_base.pvm"), "re1_b_grass_base.nj", listOf("damege_re1_b_base.njm", "die_re1_b_base.njm", "lattack_re1_b_base.njm", "mad_re1_b_base.njm", "rattack_re1_b_base.njm", "spit_re1_b_base.njm", "wait_re1_b_base.njm", "walk_re1_b_base.njm")),
    EnemySpec("DarkBringer", "bm_ene_df2_bringer_a.bml", false, null, PvmSource.FromBml("bm8_s_kb_body.pvm"), "bm8_s_kb_body.nj", listOf("beam_bm8_s_kb_body.njm", "cold_bm8_s_kb_body.njm", "damage_bm8_s_kb_body.njm", "dead_bm8_s_kb_body.njm", "hoe_bm8_s_kb_body.njm", "kamae_bm8_s_kb_body.njm", "kiri_bm8_s_kb_body.njm", "run_bm8_s_kb_body.njm", "tpkyu_bm8_s_kb_body.njm", "wait_bm8_s_kb_body.njm", "walk_bm8_s_kb_body.njm")),
    EnemySpec("Arlan", "bm_ene_df3_dimedian_a.bml", false, null, PvmSource.FromBml("df3_s_wala_body.pvm"), "df3_s_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("Merlan", "bm_ene_df3_dimedian_a.bml", false, null, PvmSource.FromBml("df3_ssl_wala_body.pvm"), "df3_ssl_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("DelD", "bm_ene_df3_dimedian_a.bml", false, null, PvmSource.FromBml("df3_sl_wala_body.pvm"), "df3_sl_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("Canabin", "bm_ene_me1_mb_a.bml", false, null, PvmSource.FromBml("me1_y_mb.pvm"), "me1n_y_mb.nj", listOf("change02_me1_y_mb.njm", "change01_me1_y_mb.njm", "damage01_me1_y_mb.njm", "damage02_me1_y_mb.njm", "wait01_me1_y_mb.njm", "wait02_me1_y_mb.njm")),
    EnemySpec("Canune", "bm_ene_me1_mb_a.bml", false, null, PvmSource.FromBml("me1_y_mb.pvm"), "me1_y_mb.nj", listOf("change02_me1_y_mb.njm", "change01_me1_y_mb.njm", "damage01_me1_y_mb.njm", "damage02_me1_y_mb.njm", "wait01_me1_y_mb.njm", "wait02_me1_y_mb.njm")),
    EnemySpec("SinowBlue", "bm_ene_me3_shinowa_a.bml", false, null, PvmSource.FromBml("me3_y_me3.pvm"), "me3_y_me3.nj", listOf("apper_me3_y_me3.njm", "backstep_me3_y_me3.njm", "damage_me3_y_me3.njm", "death_me3_y_me3.njm", "f_attack_me3_y_me3.njm", "sword_me3_y_me3.njm", "transform_me3_y_me3.njm", "t_wait_me3_y_me3.njm", "wait_me3_y_me3.njm")),
    EnemySpec("SinowRed", "bm_ene_me3_shinowa_a.bml", false, null, PvmSource.FromBml("me3_y_me3.pvm"), "me3_y_me3.nj", listOf("apper_me3_y_me3.njm", "backstep_me3_y_me3.njm", "damage_me3_y_me3.njm", "death_me3_y_me3.njm", "f_attack_me3_y_me3.njm", "sword_me3_y_me3.njm", "transform_me3_y_me3.njm", "t_wait_me3_y_me3.njm", "wait_me3_y_me3.njm")),
    EnemySpec("ObLily", "bm_ene_re2_flower_a.bml", false, null, PvmSource.FromBml("flower_root.pvm"), "flower_root.nj", listOf("attack_re2_b_root.njm", "damege_re2_b_root.njm", "die_re2_b_root.njm", "laugh_re2_b_root.njm", "waitc_re2_b_root.njm", "waito_re2_b_root.njm", "wake_re2_b_root.njm")),
    EnemySpec("MilLily", "bm_ene_re2_flower_a.bml", false, null, PvmSource.FromBml("flower_root.pvm"), "flower_root.nj", listOf("attack_re2_b_root.njm", "damege_re2_b_root.njm", "die_re2_b_root.njm", "laugh_re2_b_root.njm", "waitc_re2_b_root.njm", "waito_re2_b_root.njm", "wake_re2_b_root.njm")),
    EnemySpec("GranSorcerer", "bm_ene_re4_sorcerer_a.bml", false, null, PvmSource.FromBml("re4_b_sorcer_body.pvm"), "re4_b_sorcer_body.nj", listOf("attack2_re4_b_body.njm", "attack3_re4_b_body.njm", "attack1_re4_b_body.njm", "cure_re4_b_body.njm", "damage_re4_b_body.njm", "die_re4_b_body.njm", "enter_re4_b_body.njm", "wait_re4_b_body.njm")),
    EnemySpec("IndiBelra", "bm_ene_re7_berura_a.bml", false, null, PvmSource.FromBml("re7_b_bell_body.pvm"), "re7_b_bell_body.nj", listOf("attack_re7_b_body.njm", "damege_re7_b_body.njm", "die_re7_b_body.njm", "lattack_re7_b_body.njm", "memai_re7_b_body.njm", "rattack_re7_b_body.njm", "wait_re7_b_body.njm", "walk_re7_b_body.njm")),
    EnemySpec("Bartle", "bm_ene_re8_b_beast_a.bml", false, null, PvmSource.FromBml("re8_b_beast_wala_body.pvm"), "re8_b_beast_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("Barble", "bm_ene_re8_b_beast_a.bml", false, null, PvmSource.FromBml("re8_b_srbeast_wala_body.pvm"), "re8_b_srbeast_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("Tollaw", "bm_ene_re8_b_beast_a.bml", false, null, PvmSource.FromBml("re8_rdbeast_wala_body.pvm"), "re8_rdbeast_wala_body.nj", listOf("appear_bm1_s_wala_body.njm", "atackl_bm1_s_wala_body.njm", "atackr_bm1_s_wala_body.njm", "damage_bm1_s_wala_body.njm", "dead_bm1_s_wala_body.njm", "deadb_bm1_s_wala_body.njm", "leader_bm1_s_wala_body.njm", "mihari_bm1_s_wala_body.njm", "run_bm1_s_wala_body.njm", "stund_bm1_s_wala_body.njm", "wakeup_bm1_s_wala_body.njm", "walk_bm1_s_wala_body.njm")),
    EnemySpec("Gillchich", "bm_ene_dubchik_a.bml", false, null, PvmSource.Standalone("me2_y_me2_z_a.pvm"), "me2_y_me2.nj", listOf("damage_b_me2_y_me2.njm", "damage_f_me2_y_me2.njm", "kamae01_me2_y_me2.njm", "kamae02_me2_y_me2.njm", "revival_b_me2_y_me2.njm", "revival_f_me2_y_me2.njm", "scratch01_me2_me2.njm", "scratch02_me2_y_me2.njm", "shoot01_me2_y_me2.njm", "shoot02_me2_me2.njm", "starting_me2_y_me2.njm", "wait01_me2_y_me2.njm", "wait02_me2_y_me2.njm", "walk01_me2_y_me2.njm", "walk02_me2_y_me2.njm")),
    EnemySpec("Gilchic", "gsl_machine01.gsl", true, "bm_ene_dubchik.bml", PvmSource.Standalone("me2_y_me2_z.pvm"), "me2_y_me2.nj", listOf("damage_b_me2_y_me2.njm", "damage_f_me2_y_me2.njm", "kamae01_me2_y_me2.njm", "kamae02_me2_y_me2.njm", "revival_b_me2_y_me2.njm", "revival_f_me2_y_me2.njm", "scratch01_me2_me2.njm", "scratch02_me2_y_me2.njm", "shoot01_me2_y_me2.njm", "shoot02_me2_me2.njm", "starting_me2_y_me2.njm", "wait01_me2_y_me2.njm", "wait02_me2_y_me2.njm", "walk01_me2_y_me2.njm", "walk02_me2_y_me2.njm")),
    EnemySpec("ElRappy", "bm_ene_lappy_ap.bml", false, null, PvmSource.FromBml("re3_b_lappy_base.pvm"), "re3_b_lappy_base.nj", listOf("attack_re3_b_base.njm", "damage_re3_b_base.njm", "die_re3_b_base.njm", "run_re3_b_base.njm", "tumble_re3_b_base.njm", "wait_re3_b_base.njm", "wait2_re3_b_base.njm", "wake_re3_b_base.njm", "wake2_re3_b_base.njm", "walk_re3_b_base.njm")),
    EnemySpec("PalRappy", "bm_ene_lappy_ap.bml", false, null, PvmSource.Standalone("re3_b_lappy_base_niji.pvm"), "re3_b_lappy_base.nj", listOf("attack_re3_b_base.njm", "damage_re3_b_base.njm", "die_re3_b_base.njm", "run_re3_b_base.njm", "tumble_re3_b_base.njm", "wait_re3_b_base.njm", "wait2_re3_b_base.njm", "wake_re3_b_base.njm", "wake2_re3_b_base.njm", "walk_re3_b_base.njm")),

    // Episode 1's Forest boss -- see [EnemyFragment]'s doc comment above for the two multi-part
    // sourcing patterns. De Rol Le/Dal Ral Lie's fragments share the main body's own texture pack
    // (pvmName left null); Garanz/Baranz's each carry their own.
    EnemySpec(
        "DeRolLe", "bm_boss2_de_rol_le.bml", false, null,
        PvmSource.FromBml("boss2_b_derorure_body.pvm"), "boss2_b_derorure_body.nj",
        listOf(
            "beam02_boss2_b_body.njm", "beamwait_boss2_b_body.njm", "beam_a_boss2_b_body.njm",
            "beam_b_boss2_b_body.njm", "beam_c_boss2_b_body.njm", "bite_lloop_boss2_b_body.njm",
            "bite_rloop_boss2_b_body.njm", "die_boss2_b_body.njm", "enter_boss2_b_body.njm",
            "fd02_boss2_b_body.njm", "fjump_boss2_b_body.njm", "forward_boss2_b_body.njm",
            "lrjump_boss2_b_body.njm", "l_bite_boss2_b_body.njm", "rljump_boss2_b_body.njm",
            "r_bite_boss2_b_body.njm", "scatter_boss2_b_body.njm",
        ),
        fragments = listOf(
            EnemyFragment("boss2_b_derorure_fin_b.nj"), EnemyFragment("boss2_b_derorure_fin_a.nj"),
            EnemyFragment("boss2_b_derorure_sting.nj"), EnemyFragment("boss2_b_derorure_tentacle.nj"),
            EnemyFragment("boss2_b_helm_break.nj"), EnemyFragment("boss2_b_shell_break.nj"),
        ),
    ),

    // Ultimate difficulty's reskin of De Rol Le -- same archive-suffix convention ("_a") and
    // exact same fragment set as every other Ultimate reskin in this codebase, just a different
    // source archive (verified against AssetEnemies.js's own "Dal Ral Lie" loader).
    EnemySpec(
        "DalRalLie", "bm_boss2_de_rol_le_a.bml", false, null,
        PvmSource.FromBml("boss2_b_derorure_body.pvm"), "boss2_b_derorure_body.nj",
        listOf(
            "beam02_boss2_b_body.njm", "beamwait_boss2_b_body.njm", "beam_a_boss2_b_body.njm",
            "beam_b_boss2_b_body.njm", "beam_c_boss2_b_body.njm", "bite_lloop_boss2_b_body.njm",
            "bite_rloop_boss2_b_body.njm", "die_boss2_b_body.njm", "enter_boss2_b_body.njm",
            "fd02_boss2_b_body.njm", "fjump_boss2_b_body.njm", "forward_boss2_b_body.njm",
            "lrjump_boss2_b_body.njm", "l_bite_boss2_b_body.njm", "rljump_boss2_b_body.njm",
            "r_bite_boss2_b_body.njm", "scatter_boss2_b_body.njm",
        ),
        fragments = listOf(
            EnemyFragment("boss2_b_derorure_fin_b.nj"), EnemyFragment("boss2_b_derorure_fin_a.nj"),
            EnemyFragment("boss2_b_derorure_sting.nj"), EnemyFragment("boss2_b_derorure_tentacle.nj"),
            EnemyFragment("boss2_b_helm_break.nj"), EnemyFragment("boss2_b_shell_break.nj"),
        ),
    ),

    // Mine-laying flying robot -- main body plus 5 wreckage/ordnance fragments, each with its own
    // separate texture pack (see [EnemyFragment]'s doc comment).
    EnemySpec(
        "Garanz", "gsl_machine01.gsl", true, "bm_ene_gyaranzo.bml",
        PvmSource.FromBml("me4_y_me4.pvm"), "me4_y_me4.nj",
        listOf(
            "attack_me4_y_me4.njm", "damage01_me4_y_me4.njm", "damage02_me4_y_me4.njm",
            "deth_me4_y_me4.njm", "wait_me4_y_me4.njm", "walk01_me4_y_me4.njm",
            "walk02_me4_y_me4.njm", "walk03_me4_y_me4.njm",
        ),
        fragments = listOf(
            EnemyFragment("me4_y_hahen01.nj", "me4_y_hahen01.pvm"),
            EnemyFragment("me4_y_hahen02.nj", "me4_y_hahen02.pvm"),
            EnemyFragment("me4_y_hahen03.nj", "me4_y_hahen03.pvm"),
            EnemyFragment("me4_y_mine.nj", "me4_y_mine.pvm"),
            EnemyFragment("me4_y_missile.nj", "me4_y_missile.pvm"),
        ),
    ),

    // Ultimate difficulty's reskin of Garanz -- same fragment set, different (standalone, not
    // GSL-nested) source archive, matching AssetEnemies.js's own "Baranz" loader.
    EnemySpec(
        "Baranz", "bm_ene_gyaranzo_a.bml", false, null,
        PvmSource.FromBml("me4_y_me4.pvm"), "me4_y_me4.nj",
        listOf(
            "attack_me4_y_me4.njm", "damage01_me4_y_me4.njm", "damage02_me4_y_me4.njm",
            "deth_me4_y_me4.njm", "wait_me4_y_me4.njm", "walk01_me4_y_me4.njm",
            "walk02_me4_y_me4.njm", "walk03_me4_y_me4.njm",
        ),
        fragments = listOf(
            EnemyFragment("me4_y_hahen01.nj", "me4_y_hahen01.pvm"),
            EnemyFragment("me4_y_hahen02.nj", "me4_y_hahen02.pvm"),
            EnemyFragment("me4_y_hahen03.nj", "me4_y_hahen03.pvm"),
            EnemyFragment("me4_y_mine.nj", "me4_y_mine.pvm"),
            EnemyFragment("me4_y_missile.nj", "me4_y_missile.pvm"),
        ),
    ),

    // Ultimate difficulty's reskin of the Forest boss "Dragon" -- single mesh, no fragments,
    // confirmed against AssetEnemies.js's own "Sil Dragon" loader (empty meshList).
    EnemySpec(
        "SilDragon", "bm_boss1_dragon_a.bml", false, null,
        PvmSource.FromBml("boss1_s_nb_dragon.pvm"), "boss1_s_nb_dragon.nj",
        listOf("daml_boss1_s_nb_dragon.njm", "dams_boss1_s_nb_dragon.njm", "dead_boss1_s_nb_dragon.njm", "down_boss1_s_nb_dragon.njm", "fire_boss1_s_nb_dragon.njm", "fly_boss1_s_nb_dragon.njm", "flyshot_boss1_s_nb_dragon.njm", "frin_boss1_s_nb_dragon.njm", "frloop_boss1_s_nb_dragon.njm", "frout_boss1_s_nb_dragon.njm", "kiri_boss1_s_nb_dragon.njm", "land_boss1_s_nb_dragon.njm", "lift_boss1_s_nb_dragon.njm", "nkdown_boss1_s_nb_dragon.njm", "nkup_boss1_s_nb_dragon.njm", "nobi_boss1_s_nb_dragon.njm", "stand_boss1_s_nb_dragon.njm", "tatk_boss1_s_nb_dragon.njm", "tobidasi_boss1_s_nb_dragon.njm", "tukomi_boss1_s_nb_dragon.njm", "walk_boss1_s_nb_dragon.njm", "wgwalk_boss1_s_nb_dragon.njm", "wing_boss1_s_nb_dragon.njm", "wngclose_boss1_s_nb_dragon.njm", "wngopn_boss1_s_nb_dragon.njm"),
    ),

    // "Dubchic" is the combined/healthy state, "Dubchic Damaged" the split-apart low-health state
    // of the same machine enemy -- each a single plain mesh (confirmed against AssetEnemies.js:
    // both call setModel with an empty fragment list), just a different .nj/.pvm pair within the
    // same bml. "Dubchich"/"Dubchich Damaged" are their Ultimate-difficulty reskins.
    EnemySpec(
        "Dubchic", "gsl_machine01.gsl", true, "bm_ene_dubchik.bml",
        PvmSource.FromBml("me2_y_me2.pvm"), "me2_y_me2.nj",
        listOf("damage_b_me2_y_me2.njm", "damage_f_me2_y_me2.njm", "kamae01_me2_y_me2.njm", "kamae02_me2_y_me2.njm", "revival_b_me2_y_me2.njm", "revival_f_me2_y_me2.njm", "scratch01_me2_me2.njm", "scratch02_me2_y_me2.njm", "shoot01_me2_y_me2.njm", "shoot02_me2_me2.njm", "starting_me2_y_me2.njm", "wait01_me2_y_me2.njm", "wait02_me2_y_me2.njm", "walk01_me2_y_me2.njm", "walk02_me2_y_me2.njm"),
    ),
    EnemySpec(
        "DubchicDamaged", "gsl_machine01.gsl", true, "bm_ene_dubchik.bml",
        PvmSource.FromBml("me2_y_me2_2.pvm"), "me2_y_me2_2.nj",
        listOf("damage_b_me2_y_me2.njm", "damage_f_me2_y_me2.njm", "kamae01_me2_y_me2.njm", "kamae02_me2_y_me2.njm", "revival_b_me2_y_me2.njm", "revival_f_me2_y_me2.njm", "scratch01_me2_me2.njm", "scratch02_me2_y_me2.njm", "shoot01_me2_y_me2.njm", "shoot02_me2_me2.njm", "starting_me2_y_me2.njm", "wait01_me2_y_me2.njm", "wait02_me2_y_me2.njm", "walk01_me2_y_me2.njm", "walk02_me2_y_me2.njm"),
    ),
    EnemySpec(
        "Dubchich", "bm_ene_dubchik_a.bml", false, null,
        PvmSource.FromBml("me2_y_me2.pvm"), "me2_y_me2.nj",
        listOf("damage_b_me2_y_me2.njm", "damage_f_me2_y_me2.njm", "kamae01_me2_y_me2.njm", "kamae02_me2_y_me2.njm", "revival_b_me2_y_me2.njm", "revival_f_me2_y_me2.njm", "scratch01_me2_me2.njm", "scratch02_me2_y_me2.njm", "shoot01_me2_y_me2.njm", "shoot02_me2_me2.njm", "starting_me2_y_me2.njm", "wait01_me2_y_me2.njm", "wait02_me2_y_me2.njm", "walk01_me2_y_me2.njm", "walk02_me2_y_me2.njm"),
    ),
    EnemySpec(
        "DubchichDamaged", "bm_ene_dubchik_a.bml", false, null,
        PvmSource.FromBml("me2_y_me2_2.pvm"), "me2_y_me2_2.nj",
        listOf("damage_b_me2_y_me2.njm", "damage_f_me2_y_me2.njm", "kamae01_me2_y_me2.njm", "kamae02_me2_y_me2.njm", "revival_b_me2_y_me2.njm", "revival_f_me2_y_me2.njm", "scratch01_me2_me2.njm", "scratch02_me2_y_me2.njm", "shoot01_me2_y_me2.njm", "shoot02_me2_me2.njm", "starting_me2_y_me2.njm", "wait01_me2_y_me2.njm", "wait02_me2_y_me2.njm", "walk01_me2_y_me2.njm", "walk02_me2_y_me2.njm"),
    ),

    // Bulclaw's crab-like body and its detachable claw are, in psov2's own data, three entirely
    // separate single-mesh enemies rather than one composite -- "(Open)"/"(Closed)" are the
    // body's two health-gated states, "Claw" the claw itself. None carry fragments.
    EnemySpec(
        "BulclawOpen", "gsl_ancient01.gsl", true, "bm_ene_balclaw.bml",
        PvmSource.FromBml("re6_b_bal_body.pvm"), "re6_b_bal_body.nj",
        listOf("balattack_re6_b_bal_body.njm", "balcomb_re6_b_bal_body.njm", "baldamage_re6_b_bal_body.njm", "baldie_re6_b_bal_body.njm", "balshout_re6_b_bal_body.njm", "balwait_re6_b_bal_body.njm"),
    ),
    EnemySpec(
        "BulclawClosed", "gsl_ancient01.gsl", true, "bm_ene_balclaw.bml",
        PvmSource.FromBml("re6_b_bcbody.pvm"), "re6_b_bcbody.nj",
        listOf("bcattack_re6_b_bcbody.njm", "bcdamage_re6_b_bcbody.njm", "bcdie_re6_b_bcbody.njm", "bcwait_re6_b_bcbody.njm"),
    ),
    EnemySpec(
        "Claw", "gsl_ancient01.gsl", true, "bm_ene_balclaw.bml",
        PvmSource.FromBml("re6_b_claw_body.pvm"), "re6_b_claw_body.nj",
        listOf("clattack_re6_b_claw_body.njm", "cldamage_re6_b_claw_body.njm", "cldie_re6_b_claw_body.njm", "cllturn_re6_b_claw_body.njm", "clrturn_re6_b_claw_body.njm", "clwait_re6_b_claw_body.njm"),
    ),

    // Dark Falz's three in-fight forms are likewise each a set of independent single-mesh pieces
    // in psov2's own data (body/head-variant/base/blades/waist per form), not one composite --
    // every one of AssetEnemies.js's 13 "DarkFalz ..." loaders calls setModel with an empty
    // fragment list. Form 1's three head variants (A/B/C) reuse the exact same "_da_heada" clip
    // names for all three despite B/C using their own "_db_heada"/"_dc_heada" model -- preserved
    // as-is rather than "corrected", since that's genuinely what AssetEnemies.js does. Form 3's
    // Body skips "damage"/"df3dead" (commented out in the source, i.e. dead code, not used) and
    // BodyS/WingS ("S" = the form's stationary/inactive intro state) have no animations at all.
    EnemySpec(
        "DarkFalzForm1Body", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df1_s_body.pvm"), "df1_s_body.nj",
        listOf("beaml_df1_s_body.njm", "beamr_df1_s_body.njm", "damage_df1_s_body.njm", "dead1_df1_s_body.njm", "df1op_df1_s_body.njm", "df2op_df1_s_body.njm", "hoe_df1_s_body.njm", "lhassya_df1_s_body.njm", "ltame_df1_s_body.njm", "wait_df1_s_body.njm"),
    ),
    EnemySpec(
        "DarkFalzForm1HeadA", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df1_s_da_heada.pvm"), "df1_s_da_heada.nj",
        listOf("damage_df1_s_da_heada.njm", "dead1_df1_s_da_heada.njm", "df1op_df1_s_da_heada.njm", "df2op_df1_s_da_heada.njm", "haki_df1_s_da_heada.njm", "hakiin_df1_s_da_heada.njm", "hakiout_df1_s_da_heada.njm", "hoe_df1_s_da_heada.njm", "wait_df1_s_da_heada.njm"),
    ),
    EnemySpec(
        "DarkFalzForm1HeadB", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df1_s_db_heada.pvm"), "df1_s_db_heada.nj",
        listOf("damage_df1_s_da_heada.njm", "dead1_df1_s_da_heada.njm", "df1op_df1_s_da_heada.njm", "df2op_df1_s_da_heada.njm", "haki_df1_s_da_heada.njm", "hakiin_df1_s_da_heada.njm", "hakiout_df1_s_da_heada.njm", "hoe_df1_s_da_heada.njm", "wait_df1_s_da_heada.njm"),
    ),
    EnemySpec(
        "DarkFalzForm1HeadC", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df1_s_dc_heada.pvm"), "df1_s_dc_heada.nj",
        listOf("damage_df1_s_da_heada.njm", "dead1_df1_s_da_heada.njm", "df1op_df1_s_da_heada.njm", "df2op_df1_s_da_heada.njm", "haki_df1_s_da_heada.njm", "hakiin_df1_s_da_heada.njm", "hakiout_df1_s_da_heada.njm", "hoe_df1_s_da_heada.njm", "wait_df1_s_da_heada.njm"),
    ),
    EnemySpec(
        "DarkFalzForm1Base", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df1_s_dodai.pvm"), "df1_s_dodai.nj",
        listOf("damage_df1_s_dodai.njm", "wait_df1_s_dodai.njm"),
    ),
    EnemySpec(
        "DarkFalzForm1Blades", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df1_s_simobe.pvm"), "df1_s_simobe.nj",
        emptyList(),
    ),
    EnemySpec(
        "DarkFalzForm1Waist", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df1_s_waist.pvm"), "df1_s_waist.nj",
        emptyList(),
    ),
    EnemySpec(
        "DarkFalzForm2Body", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df2_s_body.pvm"), "df2_s_body.nj",
        listOf("beaml_df2_s_body.njm", "beamr_df2_s_body.njm", "damage_df2_s_body.njm", "dead_df2_s_body.njm", "df2op_df2_s_body.njm", "df3_op_df2_s_body.njm", "hoe_df2_s_body.njm", "jisin_df2_s_body.njm", "lhassya_df2_s_body.njm", "ltame_df2_s_body.njm", "wait_df2_s_body.njm", "wing_df2_s_body.njm"),
    ),
    EnemySpec(
        "DarkFalzForm2Base", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df2_s_dodai1.pvm"), "df2_s_dodai1.nj",
        listOf("beaml_df2_s_dodai1.njm", "beamr_df2_s_dodai1.njm", "damage_df2_s_dodai1.njm", "dead_df2_s_dodai1.njm", "df2op_df2_s_dodai1.njm", "df3_op_df2_s_dodai1.njm", "hoe_df2_s_dodai1.njm", "jisin_df2_s_dodai1.njm", "lhassya_df2_s_dodai1.njm", "wait_df2_s_dodai1.njm", "wing_df2_s_dodai1.njm"),
    ),
    EnemySpec(
        "DarkFalzForm3Body", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df3_s_body.pvm"), "df3_s_body.nj",
        listOf("dead_df3_s_body.njm", "df3_op_df3_s_body.njm", "dwntec_df3_s_body.njm", "jyousyou_df3_s_body.njm", "kakou_df3_s_body.njm", "laser_df3_s_body.njm", "lkiri_df3_s_body.njm", "rkiri_df3_s_body.njm", "wait_df3_s_body.njm", "yubi_df3_s_body.njm"),
    ),
    EnemySpec(
        "DarkFalzForm3Wing", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df3_s_wing.pvm"), "df3_s_wing.nj",
        listOf("damage_df3_s_wing.njm", "df3_op_df3_s_wing.njm", "jyousyou_df3_s_wing.njm", "kakou_df3_s_wing.njm", "laser_df3_s_wing.njm", "lkiri_df3_s_wing.njm", "rkiri_df3_s_wing.njm", "wait_df3_s_wing.njm"),
    ),
    EnemySpec(
        "DarkFalzForm3BodyS", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df3_sl_body.pvm"), "df3_sl_body.nj",
        emptyList(),
    ),
    EnemySpec(
        "DarkFalzForm3WingS", "darkfalz_dat.bml", false, null,
        PvmSource.FromBml("df3_sl_wing.pvm"), "df3_sl_wing.nj",
        emptyList(),
    ),

    // Episode 4's boss -- another main-body-plus-fragments case, but unlike De Rol Le/Garanz its
    // 20 fragments are all purely static arena scenery (shadow decal, pedestals, energy shields,
    // wreckage, a cage, missile, support pillar) rather than parts of Vol Opt's own body, each
    // with its own texture (same per-fragment-.pvm pattern as Garanz). AssetEnemies.js's own
    // "Vol Opt" loader also lists 3 more fragments with their own per-fragment animation clips,
    // but that whole block is commented out (dead code, never actually runs) -- omitted here to
    // match what the reference implementation actually does, not what it has commented out.
    // "Vol Opt Version 2" (the post-transformation second phase) is the exact same body and
    // fragment set, just sourced from the "_ap" ("after phase"?) archive.
    EnemySpec(
        "VolOpt", "bm_boss3_volopt.bml", false, null,
        PvmSource.FromBml("me5p02_y_all.pvm"), "me5p02_y_all.nj",
        listOf("b_attack_me5p02_y_all.njm", "damage_me5p02_y_all.njm", "death_me5p02_y_all.njm", "f_attack_me5p02_y_all.njm", "l_attack_me5p02_y_all.njm", "r_attack_me5p02_y_all.njm", "start_me5p02_y_all.njm", "wait_me5p02_y_all.njm"),
        fragments = VOL_OPT_FRAGMENTS,
    ),
    EnemySpec(
        "VolOptV2", "bm_boss3_volopt_ap.bml", false, null,
        PvmSource.FromBml("me5p02_y_all.pvm"), "me5p02_y_all.nj",
        listOf("b_attack_me5p02_y_all.njm", "damage_me5p02_y_all.njm", "death_me5p02_y_all.njm", "f_attack_me5p02_y_all.njm", "l_attack_me5p02_y_all.njm", "r_attack_me5p02_y_all.njm", "start_me5p02_y_all.njm", "wait_me5p02_y_all.njm"),
        fragments = VOL_OPT_FRAGMENTS,
    ),
)
