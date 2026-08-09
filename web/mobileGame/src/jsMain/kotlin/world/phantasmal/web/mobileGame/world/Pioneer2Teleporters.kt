package world.phantasmal.web.mobileGame.world

/**
 * One teleporter/warp pad in Pioneer 2, transcribed from quest-editor readouts.
 *
 * Positions are **world** space (the editor's "World Position"), not section-local. All three of
 * these sit in section 10, whose transform is a pure translation of (+228, +291) with no rotation
 * -- confirmed against this section's own NPC entry as well as all three objects here, so world
 * rotation equals the authored rotation.
 *
 * A pad either moves the player within Pioneer 2 ([destX]/[destY]/[destZ]) or leaves for another
 * map entirely ([destinationMap]) -- never both.
 */
class Pioneer2Teleporter(
    val name: String,
    /**
     * Slug of the animated beam prop to draw (see GSL_OBJECT_SPECS in :web:assets-generation's
     * ObjectSpecs.kt). "CityBeam" is psov2's `de_obj_citybeam`, "CityBeamBig" its
     * `de_obj_bigbeam` -- both from `gsl_city.gsl` -> `bm_obj_city_common.bml`.
     */
    val modelSlug: String,
    /**
     * Uniform scale for the beam mesh. 1.0 is psov2's authentic size (CityBeam is 33.7 wide by
     * 17.1 tall; CityBeamBig 49.8 by 26.1 -- measured, with no scaling applied anywhere). The Main
     * Left at 1.0 everywhere. The Main Ragol Teleporter's beam only fills the base of the tall
     * ring shaft the map geometry already draws above its dais, but scaling up is not the fix:
     * the scale is uniform, so raising it enough to reach the canopy also makes the rings far
     * wider than the dais they sit on (tried at 2.6 -- the rings sprawled well past the platform).
     * Filling that shaft properly would need a Y-only stretch or a different source mesh.
     */
    val scale: Double = 1.0,
    val x: Double,
    val y: Double,
    val z: Double,
    val rotationYDegrees: Double,
    val destX: Double? = null,
    val destY: Double? = null,
    val destZ: Double? = null,
    val destRotationYDegrees: Double? = null,
    /**
     * Map slug this pad leaves Pioneer 2 for, if any. Mutually exclusive with the dest* fields:
     * those move the player within the current map, this swaps the map entirely (see
     * GameShell.enterGame).
     */
    val destinationMap: String? = null,
    /**
     * True for the Main Ragol Teleporter: stepping on opens the destination menu (Forest 1 /
     * Cave 1 / Mine 1 / Ruins 1, gated by boss progression) instead of leaving immediately.
     */
    val opensAreaMenu: Boolean = false,
    /**
     * True for a Telepipe's pads: leaving Pioneer 2 through one arrives beside the field pipe
     * rather than at the area entrance -- see ActiveTelepipe.
     */
    val isTelepipe: Boolean = false,
)

/**
 * The two Principal warps (a round trip: plaza -> Principal's office -> plaza) plus the Main Ragol
 * Teleporter.
 *
 * **On the Y values.** Every one of these is used exactly as authored. An earlier version pushed
 * the two office-side values down to y = -194 because raycasting for walkable ground at the
 * office's coordinates returns -194 -- but that is a lower surface under the room, not its floor,
 * and warping to it drops the player a long way below where they belong. The authored y = 0 is
 * correct; the ground search simply isn't trustworthy here, the same way it wasn't for the town
 * NPCs (see PIONEER2_NPCS).
 */
val PIONEER2_TELEPORTERS: List<Pioneer2Teleporter> = listOf(
    // Plaza -> Principal's office. Authored destination y=0 corrected to the office floor.
    Pioneer2Teleporter(
        name = "Principal warp",
        modelSlug = "CityBeam",
        x = 0.0, y = 0.0, z = -60.002,
        rotationYDegrees = 0.0,
        destX = 10.0, destY = 0.0, destZ = -1760.001,
        destRotationYDegrees = 180.0,
    ),
    // Principal's office -> plaza. Pad itself sits on the office floor.
    Pioneer2Teleporter(
        name = "Principal warp (return)",
        modelSlug = "CityBeam",
        x = 0.0, y = 0.0, z = -1730.0,
        rotationYDegrees = 168.75,
        destX = -10.0, destY = 0.0, destZ = -30.0,
        destRotationYDegrees = 0.0,
    ),
    // Leaves Pioneer 2 for Ragol -- Forest 1, the first field area.
    Pioneer2Teleporter(
        name = "Main Ragol Teleporter",
        modelSlug = "CityBeamBig",
        x = 360.003, y = 1.0, z = 25.998,
        rotationYDegrees = 28.125,
        destinationMap = "forest01",
        opensAreaMenu = true,
    ),
)
