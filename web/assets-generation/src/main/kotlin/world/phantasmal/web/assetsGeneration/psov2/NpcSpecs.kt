package world.phantasmal.web.assetsGeneration.psov2

/**
 * One town NPC from psov2's AssetPlayer.js -- Pioneer 2/lobby characters (shop staff, hunters
 * guild, named characters like Red Ring Rico), as distinct from the 9 playable classes
 * PLAYER_CLASS_SPECS covers. Unlike playable classes (separately composited body/head/hair/
 * accessory pieces), each NPC is a single self-contained body model, sourced from a nested bml
 * inside a shared gsl archive (matching the FromGsl sourcing enemies use), and shares the
 * player's own skeleton/animation set closely enough that psov2 itself drives these with the same
 * plymotiondata.rlc (see AnimationAssetLoader) -- so they can play e.g. the idle clip already
 * generated for the player.
 */
class NpcSpec(
    val slug: String,
    val archive: String,
    val bmlEntry: String,
)

val NPC_SPECS: List<NpcSpec> = listOf(
    NpcSpec("RedRingRico", "gsl_forest02.gsl", "rico_body.bml"),
    NpcSpec("Hakase", "gsl_city.gsl", "bm_n_hakase_i_body.bml"),
    NpcSpec("Nurse", "gsl_city.gsl", "bm_n_nurse_i_body.bml"),
    NpcSpec("Hisyo", "gsl_city.gsl", "bm_n_hisyo_i_body.bml"),
    NpcSpec("Soutoku", "gsl_city.gsl", "bm_n_soutoku_i_body.bml"),
    NpcSpec("Citizen", "gsl_city.gsl", "bm_n_ebw_i_body.bml"),

    // Generic townsperson clothing/hair variants -- psov2's own AssetPlayer.js has a
    // "bm_n_ecm_i_body" entry too, but it's a copy-paste bug in the reference implementation
    // itself (it loads "bm_n_efsw_i_body.bml" instead of its own name, verified by checking every
    // other entry in this family is self-consistent), so it's skipped here rather than propagated.
    NpcSpec("CitizenWoman2", "gsl_city.gsl", "bm_n_ecw_i_body.bml"),
    NpcSpec("CitizenWoman3", "gsl_city.gsl", "bm_n_efsw_i_body.bml"),
    NpcSpec("CitizenWoman4", "gsl_city.gsl", "bm_n_efw_i_body.bml"),
    NpcSpec("CitizenWoman5", "gsl_city.gsl", "bm_n_emw_i_body.bml"),
    NpcSpec("CitizenWoman6", "gsl_city.gsl", "bm_n_eow_i_body.bml"),
    NpcSpec("CitizenWoman7", "gsl_city.gsl", "bm_n_etw_i_body.bml"),
    NpcSpec("CitizenMan1", "gsl_city.gsl", "bm_n_ebm_i_body.bml"),
    NpcSpec("CitizenMan2", "gsl_city.gsl", "bm_n_efsm_i_body.bml"),
    NpcSpec("CitizenMan3", "gsl_city.gsl", "bm_n_efm_i_body.bml"),
    NpcSpec("CitizenMan4", "gsl_city.gsl", "bm_n_emm_i_body.bml"),
    NpcSpec("CitizenMan5", "gsl_city.gsl", "bm_n_eom_i_body.bml"),
    NpcSpec("CitizenMan6", "gsl_city.gsl", "bm_n_etm_i_body.bml"),

    // Government building ("kantei") staff.
    NpcSpec("GovStaff1", "gsl_city.gsl", "bm_n_kanteib_i_body.bml"),
    NpcSpec("GovStaff2", "gsl_city.gsl", "bm_n_kanteib2_i_body.bml"),
    NpcSpec("GovStaff3", "gsl_city.gsl", "bm_n_kanteif_i_body.bml"),
    NpcSpec("GovStaff4", "gsl_city.gsl", "bm_n_kanteif2_i_body.bml"),
    NpcSpec("GovStaff5", "gsl_city.gsl", "bm_n_kanteifs_i_body.bml"),
    NpcSpec("GovStaff6", "gsl_city.gsl", "bm_n_kanteifs2_i_body.bml"),
    NpcSpec("GovStaff7", "gsl_city.gsl", "bm_n_kanteio_i_body.bml"),
    NpcSpec("GovStaff8", "gsl_city.gsl", "bm_n_kanteio2_i_body.bml"),
    NpcSpec("GovStaff9", "gsl_city.gsl", "bm_n_kanteit_i_body.bml"),
    NpcSpec("GovStaff10", "gsl_city.gsl", "bm_n_kanteit2_i_body.bml"),

    // Hunters guild staff, plus one more named character.
    NpcSpec("GuildStaff1", "gsl_city.gsl", "bm_n_gunb2_i_body.bml"),
    NpcSpec("GuildStaff2", "gsl_city.gsl", "bm_n_gunm_i_body.bml"),
    NpcSpec("Trunk", "gsl_city.gsl", "bm_n_trunk_i_body.bml"),
)

/**
 * More city NPCs, but sourced completely differently from NPC_SPECS above: instead of a nested
 * bml archive, each is a standalone ".rel" entry directly in gsl_city.gsl, with its NJCM root
 * object graph (the exact same bone/chunk format everywhere else) located via a two-pointer walk
 * from the file's own footer -- seek to 16 bytes before the end for a table pointer, follow that
 * to a second pointer, and *that* points at the root object header. Verified directly against the
 * raw bytes of npc_a00_data.rel before writing this (footer pointer -> second pointer -> a
 * evalFlags/modelOffset pair with small, sane values, right where a NinjaEvaluationFlags/model
 * pair should be). See Psov2NpcGeometry.kt for the runtime parser this requires.
 */
class NpcRelSpec(
    val slug: String,
    val archive: String,
    val relEntry: String,
    val pvmEntry: String,
)

val NPC_REL_SPECS: List<NpcRelSpec> = listOf(
    NpcRelSpec("CityNpcA00", "gsl_city.gsl", "npc_a00_data.rel", "n_a00_w_body.pvm"),
    NpcRelSpec("CityNpcB00", "gsl_city.gsl", "npc_b00_data.rel", "n_b00_body.pvm"),
    NpcRelSpec("CityNpcD00", "gsl_city.gsl", "npc_d00_data.rel", "n_d00_w_body.pvm"),
    NpcRelSpec("CityNpcE00", "gsl_city.gsl", "npc_e00_data.rel", "n_e00_w_body.pvm"),
    NpcRelSpec("CityNpcF00", "gsl_city.gsl", "npc_f00_data.rel", "n_f00_w_body.pvm"),
    NpcRelSpec("CityNpcG00", "gsl_city.gsl", "npc_g00_data.rel", "n_g00_body.pvm"),
    NpcRelSpec("CityNpcH00", "gsl_city.gsl", "npc_h00_data.rel", "n_h00_body.pvm"),
    NpcRelSpec("CityNpcI00", "gsl_city.gsl", "npc_i00_data.rel", "n_i00_body.pvm"),
    NpcRelSpec("CityNpcB01", "gsl_city.gsl", "npc_b01_data.rel", "n_b01_e_body.pvm"),
    NpcRelSpec("CityNpcC01", "gsl_city.gsl", "npc_c01_data.rel", "n_c01_e_body.pvm"),
    NpcRelSpec("CityNpcD01", "gsl_city.gsl", "npc_d01_data.rel", "n_d01_e_body.pvm"),
    NpcRelSpec("CityNpcG01", "gsl_city.gsl", "npc_g01_data.rel", "n_g01_e_body.pvm"),
    NpcRelSpec("CityNpcH01", "gsl_city.gsl", "npc_h01_data.rel", "n_h01_e_body.pvm"),
    NpcRelSpec("CityNpcI01", "gsl_city.gsl", "npc_i01_data.rel", "n_i01_e_body.pvm"),
)
