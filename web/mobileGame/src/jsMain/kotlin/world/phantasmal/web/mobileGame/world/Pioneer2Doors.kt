package world.phantasmal.web.mobileGame.world

/**
 * One building door in Pioneer 2, transcribed from quest-editor readouts.
 *
 * Positions are **world** space (the editor's "World Position"). All four doors sit in section 10,
 * whose transform is a pure translation of (+228, +291) with no rotation -- verified against every
 * entry here, so the authored rotation is already the world rotation. Y is used exactly as
 * authored (0 for all of them); the ground raycast is not trusted on this map, having twice
 * returned surfaces well below the real floor (see PIONEER2_NPCS and PIONEER2_TELEPORTERS).
 */
class Pioneer2Door(
    val name: String,
    /** Slug from GSL_OBJECT_SPECS in :web:assets-generation's ObjectSpecs.kt. */
    val modelSlug: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val rotationYDegrees: Double,
)

/**
 * The town's four doors. Models come from `gsl_city.gsl` -> `bm_obj_city_common.bml`, the same
 * city-common archive the teleporter beams use.
 *
 * psov2 never loads these itself -- they sit in the archive but have no entry in its
 * AssetObjects.js -- so each was matched by name and then confirmed by eye through the
 * `?viewObject=` debug route: the medical door carries a red cross, the shop and guild doors are
 * panelled entrances, and the teleporter's is a two-piece shutter (Japanese "tobira" = door, with
 * "ue"/"sita" upper and lower halves) which is why it takes two entries at one position.
 *
 * Each door ships a single open/close clip, driven by proximity rather than left looping -- see
 * GameRenderer.updateDoors, which scrubs the clip's playhead toward open or shut depending on how
 * close the player is.
 */
val PIONEER2_DOORS: List<Pioneer2Door> = listOf(
    // Authored at (193.501, -93.495), same as every other door here -- but this mesh is the only
    // one whose geometry isn't centred on its own origin: its bounding-box centre sits 78.46 units
    // along local +Z (measured via ?viewObject=; the other four are within 6 units of zero). Yaw
    // 315 degrees turns that into a world offset of (-55.48, +55.48), which is why placing it on
    // the authored point drew the door about 55 units away towards the plaza.
    //
    // So the position below is the authored point minus that offset -- it looks wrong in isolation
    // but puts the door's visible centre exactly on the doorway. Recompute it if the model is ever
    // re-exported with a different origin.
    Pioneer2Door("Medical Center Door", "MedicalCenterDoor", 248.981, 0.0, -148.975, 315.0),
    Pioneer2Door("Shop Door", "ShopDoor", -165.003, 0.0, 147.5, 180.0),
    Pioneer2Door("Hunter's Guild Door", "HuntersGuildDoor", 184.998, 0.0, 172.999, 180.0),
    Pioneer2Door("Teleporter Door (upper)", "TeleporterDoorUpper", 254.001, 0.0, 25.002, 180.0),
    Pioneer2Door("Teleporter Door (lower)", "TeleporterDoorLower", 254.001, 0.0, 25.002, 180.0),
)
