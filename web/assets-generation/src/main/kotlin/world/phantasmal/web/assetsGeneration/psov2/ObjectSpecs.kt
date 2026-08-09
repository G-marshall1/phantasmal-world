package world.phantasmal.web.assetsGeneration.psov2

/**
 * One decorative/interactive map prop from psov2's shared item.bml -- the same archive
 * WEAPON_SPECS' item-box-adjacent entries live in, but referenced by name (bml['x.pvm']/
 * bml['x.nj']) rather than by numeric index the way itemmodel.afs/itemtexture.afs are. psov2's
 * own AssetObjects.js catalogs 116 map objects total (doors, switches, monuments, rocks,
 * machines, etc.), but the overwhelming majority pull from per-map GSL archives with multi-part
 * NJCM merges (several .nj fragments assembled into one object) -- a materially different, much
 * messier sourcing pattern this doesn't attempt yet. This covers just the handful that use the
 * same simple single-archive, single-model pattern weapons do (auto-verified: every one of these
 * loads exactly one archive, no GSL nesting, no multi-part NJCM merge).
 */
class ObjectSpec(
    val slug: String,
    val pvmEntry: String,
    val njEntry: String,
)

/**
 * One prop from a per-map GSL archive's nested bml, rather than the shared item.bml [ObjectSpec]
 * covers. Adds an optional animation clip, which item.bml's props don't have.
 *
 * The city's three teleporter beams live in `gsl_city.gsl` -> `bm_obj_city_common.bml` (psov2's
 * own "City Common" entry in AssetObjects.js dumps that bml wholesale) and all share a single
 * texture pack, `de_obj_bigbeam.pvm`, hence [pvmEntry] being named separately from [njEntry].
 */
class GslObjectSpec(
    val slug: String,
    val archive: String,
    val bmlEntry: String,
    val njEntry: String,
    val pvmEntry: String,
    val njmEntry: String? = null,
    /**
     * True when [pvmEntry] names a file in the GSL archive itself rather than inside the nested
     * bml -- the Forest's door/fence/switch models keep their shared texture packs at the GSL
     * level (`fe_obj_door.pvm`, `fe_obj_lazer2.pvm`), unlike the city props whose textures sit
     * next to the models inside the bml.
     */
    val pvmFromGsl: Boolean = false,
)

/**
 * A prop sourced from loose files in the dat directory itself -- a bare .nj (the per-map sky
 * domes ship as `map_<area>s.nj` + `.pvm` siblings) or an entry inside a loose .bml (the Dragon
 * arena's floor set lives in `bm_obj_boss1_common.bml` with `obj_boss1_common.pvm` beside it).
 * Exactly one of [njFile] / ([bmlFile] + [njEntry]) is set.
 */
class LooseObjectSpec(
    val slug: String,
    val pvmFile: String,
    val njFile: String? = null,
    val bmlFile: String? = null,
    val njEntry: String? = null,
)

val LOOSE_OBJECT_SPECS: List<LooseObjectSpec> = listOf(
    // The forest sky domes -- the "s" sibling of each map's own files.
    LooseObjectSpec(slug = "Forest01Sky", pvmFile = "map_forest01s.pvm", njFile = "map_forest01s.nj"),
    LooseObjectSpec(slug = "Forest02Sky", pvmFile = "map_forest02s.pvm", njFile = "map_forest02s.nj"),
    // The Dragon arena's real floor: the ground grid, the volcanic vent, the floor plate and
    // its two rocks. The stage .rel is only the outer shell -- the fight stands on these.
    LooseObjectSpec(
        slug = "BossArena1Floor", pvmFile = "obj_boss1_common.pvm",
        bmlFile = "bm_obj_boss1_common.bml", njEntry = "grid2_jimen.nj",
    ),
    LooseObjectSpec(
        slug = "BossArena1FloorPlate", pvmFile = "obj_boss1_common.pvm",
        bmlFile = "bm_obj_boss1_common.bml", njEntry = "dokutu_fe_obj001_drayuka.nj",
    ),
    LooseObjectSpec(
        slug = "BossArena1Vent", pvmFile = "obj_boss1_common.pvm",
        bmlFile = "bm_obj_boss1_common.bml", njEntry = "dokutu_hunkakou1.nj",
    ),
    LooseObjectSpec(
        slug = "BossArena1Rock1", pvmFile = "obj_boss1_common.pvm",
        bmlFile = "bm_obj_boss1_common.bml", njEntry = "dokutu_fe_obj001_draiwa01.nj",
    ),
    LooseObjectSpec(
        slug = "BossArena1Rock2", pvmFile = "obj_boss1_common.pvm",
        bmlFile = "bm_obj_boss1_common.bml", njEntry = "dokutu_fe_obj001_draiwa02.nj",
    ),
)

val GSL_OBJECT_SPECS: List<GslObjectSpec> = listOf(
    // ---- The Forest's full furniture (see the mobile game's AreaSpawnTable object types):
    //      warp pads for area transitions (Teleporter, type 2) and in-area hops (Warp, type 3),
    //      the boss teleporter, Rico's message capsules, the rising bridge, energy barriers,
    //      the real three-part floor switch, monuments, the probe and the weather terminal. ----
    GslObjectSpec(
        slug = "TeleporterPad",
        archive = "gsl_forest02.gsl",
        bmlEntry = "fs_obj_warp.bml",
        njEntry = "fs_obj_warp_dai.nj",
        pvmEntry = "fs_obj_warp.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "TeleporterPadBeam",
        archive = "gsl_forest02.gsl",
        bmlEntry = "fs_obj_warp.bml",
        njEntry = "fs_obj_warp_dai_beam.nj",
        pvmEntry = "fs_obj_warp.pvm",
        njmEntry = "fs_obj_warp_dai_beam.njm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "WarpPad",
        archive = "gsl_forest02.gsl",
        bmlEntry = "fs_obj_warp.bml",
        njEntry = "fs_obj_warp.nj",
        pvmEntry = "fs_obj_warp.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "WarpPadBeam",
        archive = "gsl_forest02.gsl",
        bmlEntry = "fs_obj_warp.bml",
        njEntry = "fs_obj_warp_beam.nj",
        pvmEntry = "fs_obj_warp.pvm",
        njmEntry = "fs_obj_warp_beam.njm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "BossWarp",
        archive = "gsl_forest02.gsl",
        bmlEntry = "bm_obj_warpboss.bml",
        njEntry = "fs_obj_warp_dai_beam02.nj",
        pvmEntry = "fs_obj_warp_dai_beam02.pvm",
        njmEntry = "fs_obj_warp_dai_beam02.njm",
    ),
    GslObjectSpec(
        slug = "RicoMessagePod",
        archive = "gsl_forest02.gsl",
        bmlEntry = "bm_fe_obj_o_capsule01.bml",
        njEntry = "fe_obj_o_capsule01.nj",
        pvmEntry = "fe_obj_o_capsule01.pvm",
    ),
    GslObjectSpec(
        slug = "RisingBridge",
        archive = "gsl_forest02.gsl",
        bmlEntry = "fe_obj_hashi.bml",
        njEntry = "fe_obj_hashi.nj",
        pvmEntry = "fe_obj_hashi.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "EnergyBarrier",
        archive = "gsl_forest02.gsl",
        bmlEntry = "bm_fs_obj_lazerguard.bml",
        njEntry = "fs_obj_laz_guard.nj",
        pvmEntry = "fs_obj_laz_guard.pvm",
        njmEntry = "fs_obj_laz_guard.njm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "EnergyBarrierBase",
        archive = "gsl_forest02.gsl",
        bmlEntry = "bm_fs_obj_lazerguard.bml",
        njEntry = "fe_obj_laz_guard_moto.nj",
        pvmEntry = "fe_obj_laz_guard_moto.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "Monument",
        archive = "gsl_forest02.gsl",
        bmlEntry = "bm_fs_obj_o_monument01.bml",
        njEntry = "fs_obj_o_monument01.nj",
        pvmEntry = "fs_obj_o_monument01.pvm",
    ),
    GslObjectSpec(
        slug = "Probe",
        archive = "gsl_forest02.gsl",
        bmlEntry = "bm_fs_obj_sensor.bml",
        njEntry = "fs_obj_sensor.nj",
        pvmEntry = "fs_obj_sensor.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "ForestFloorSwitch",
        archive = "gsl_forest02.gsl",
        bmlEntry = "abeno_fs_obj001_fosuno.bml",
        njEntry = "abeno_fs_obj001_fosuno.nj",
        pvmEntry = "fe_obj_switch.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "ForestFloorSwitchButton",
        archive = "gsl_forest02.gsl",
        bmlEntry = "abeno_fs_obj001_fosuno.bml",
        njEntry = "abesu_fs_obj001_fosu.nj",
        pvmEntry = "fe_obj_switch.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "ForestFloorSwitchRing",
        archive = "gsl_forest02.gsl",
        bmlEntry = "abeno_fs_obj001_fosuno.bml",
        njEntry = "abesu_fs_obj001_fotutu.nj",
        pvmEntry = "fe_obj_switch.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "WeatherStation",
        archive = "gsl_forest02.gsl",
        bmlEntry = "fe_obj_tanmatu02.bml",
        njEntry = "fe_obj_tanmatu02.nj",
        pvmEntry = "fe_obj_computer.pvm",
        pvmFromGsl = true,
    ),

    GslObjectSpec(
        slug = "CityBeamBig",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "de_obj_bigbeam.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "de_obj_bigbeam.njm",
    ),
    GslObjectSpec(
        slug = "CityBeam",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "de_obj_citybeam.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "de_obj_citybeam.njm",
    ),
    GslObjectSpec(
        slug = "CityBeamSmall",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "de_obj_smallbeam.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "de_obj_smallbeam.njm",
    ),

    // Pioneer 2's building doors, from the same city-common bml (psov2 itself never loads these --
    // they're in the archive but absent from AssetObjects.js, so the naming is the only guide).
    // Each ships an open/close clip, extracted here but not played: there's no proximity trigger
    // yet, and looping a door open-shut forever would look worse than leaving it posed.
    GslObjectSpec(
        slug = "MedicalCenterDoor",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "fe_obj_o_medical_door01l.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "fe_obj_o_medical_door01l.njm",
    ),
    GslObjectSpec(
        slug = "ShopDoor",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "fd_obj_shopdoor_01.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "fd_obj_shopdoor_01.njm",
    ),
    GslObjectSpec(
        slug = "HuntersGuildDoor",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "hunter_door.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "hunter_door.njm",
    ),
    // "tobira" is Japanese for door, "ue"/"sita" upper/lower -- this one is a shutter in two
    // halves that meet in the middle, so both pieces are needed to form the whole door.
    GslObjectSpec(
        slug = "TeleporterDoorUpper",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "fd_obj_n_tobira_shut_ue.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "fd_obj_n_tobira_shut_ue.njm",
    ),
    GslObjectSpec(
        slug = "TeleporterDoorLower",
        archive = "gsl_city.gsl",
        bmlEntry = "bm_obj_city_common.bml",
        njEntry = "fd_obj_n_tobira_shut_sita.nj",
        pvmEntry = "de_obj_bigbeam.pvm",
        njmEntry = "fd_obj_n_tobira_shut_sita.njm",
    ),

    // The Forest's room gates and switch puzzle, per psov2's own "Forest Door" / "Laser Fence" /
    // "Laser Switch" entries in AssetObjects.js. The door and switch are each two placements of
    // two parts (frame + laser beam) sharing one position; the fences are single meshes in 4M/6M
    // and plain/square variants. All share GSL-level texture packs, hence pvmFromGsl.
    GslObjectSpec(
        slug = "ForestDoor",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_doa_kanban.bml",
        njEntry = "taiki_fe_obj_doa_oya.nj",
        pvmEntry = "fe_obj_door.pvm",
        njmEntry = "fe_obj_doa_oya.njm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "ForestDoorBeam",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_doa_kanban.bml",
        njEntry = "taiki_fe_obj_doa_laz_oya.nj",
        pvmEntry = "fe_obj_door.pvm",
        njmEntry = "taiki_fe_obj_doa_laz_oya.njm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "LaserFence4M",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_lazer2_4m.bml",
        njEntry = "fe_obj_lazer2_4m_moto.nj",
        pvmEntry = "fe_obj_lazer2.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "LaserFence6M",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_lazer2_4m.bml",
        njEntry = "fe_obj_lazer2_6m_moto.nj",
        pvmEntry = "fe_obj_lazer2.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "SquareLaserFence4M",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_lazer4_4m.bml",
        njEntry = "fe_obj_lazer4_4m_moto.nj",
        pvmEntry = "fe_obj_lazer2.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "SquareLaserFence6M",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_lazer4_4m.bml",
        njEntry = "fe_obj_lazer4_6m_moto.nj",
        pvmEntry = "fe_obj_lazer2.pvm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "LaserFenceSwitch",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_switch_laz.bml",
        njEntry = "fs_obj_switch_laz_moto.nj",
        pvmEntry = "fe_obj_switch_laz.pvm",
        njmEntry = "fs_obj_switch_laz_moto.njm",
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "LaserFenceSwitchBeam",
        archive = "gsl_forest01.gsl",
        bmlEntry = "fe_obj_switch_laz.bml",
        njEntry = "fe_obj_switch_laz.nj",
        pvmEntry = "fe_obj_switch_laz.pvm",
        njmEntry = "fe_obj_switch_laz.njm",
        pvmFromGsl = true,
    ),

    // The Forest's breakable box, plus the two fragments it comes apart into.
    GslObjectSpec(
        slug = "ForestBox",
        archive = FOREST_BOX_ARCHIVE,
        bmlEntry = FOREST_BOX_BML,
        njEntry = "fs_obj_hako01.nj",
        pvmEntry = FOREST_BOX_PVM,
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "ForestBoxShardA",
        archive = FOREST_BOX_ARCHIVE,
        bmlEntry = FOREST_BOX_BML,
        njEntry = "fs_obj_hako01_hahen01.nj",
        pvmEntry = FOREST_BOX_PVM,
        pvmFromGsl = true,
    ),
    GslObjectSpec(
        slug = "ForestBoxShardB",
        archive = FOREST_BOX_ARCHIVE,
        bmlEntry = FOREST_BOX_BML,
        njEntry = "fe_obj_hako01_hahen02.nj",
        pvmEntry = FOREST_BOX_PVM,
        pvmFromGsl = true,
    ),
)

/**
 * The Forest's breakable box and the two debris pieces it bursts into. "hako" is box and
 * "hahen" is fragment -- psov2 ships the intact crate and its broken halves in one bml, which is
 * exactly what a box that shatters when struck needs.
 */
private const val FOREST_BOX_ARCHIVE = "gsl_forest01.gsl"
private const val FOREST_BOX_BML = "fe_obj_hako01_hahen02.bml"
private const val FOREST_BOX_PVM = "fs_obj_hako01_n.pvm"

val OBJECT_SPECS: List<ObjectSpec> = listOf(
    ObjectSpec("GunBullet", "gun_bullet.pvm", "gun_bullet.nj"),
    ObjectSpec("ItemBox", "ixm_box03.pvm", "ixm_box03.nj"),
    ObjectSpec("Meseta", "ixm_box04.pvm", "ixm_box04.nj"),
    ObjectSpec("ArmorScan", "armor_scan.pvm", "armor_scan.nj"),
    ObjectSpec("Shockwave", "wxmS01_d_w_bullet.pvm", "wxmS01_d_w_bullet.nj"),
    ObjectSpec("WeaponBox", "ixm_box01.pvm", "ixm_box01.nj"),
    ObjectSpec("ArmorBox", "ixm_box02.pvm", "ixm_box02.nj"),
)
