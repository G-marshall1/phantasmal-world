package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.viewer.models.CharacterClass

/**
 * The six Photon Blasts, with the hex icon-tile each one shows on the armed palette (see
 * PhotonBlastOverlay and HudSprites.hexTile). Tiles were identified by pixel-matching the
 * sheet's glyphs against the wiki's labelled icon images (glyph-colour histogram + shape mask,
 * uniquely assigned): Leilla and Farlla matched strongly; Golla via its X mark; the remaining
 * three are the best fits among the leftover rainbow tiles and carry the most residual doubt --
 * eyeball them in game against the wiki's Photon Blasts page if one looks off.
 */
enum class PhotonBlast(val displayName: String, val iconCol: Int, val iconRow: Int) {
    FARLLA("Farlla", 4, 3),
    ESTLLA("Estlla", 2, 4),
    GOLLA("Golla", 3, 3),
    PILLA("Pilla", 4, 4),
    LEILLA("Leilla", 2, 3),
    MYLLA_YOULLA("Mylla&Youlla", 3, 4),
}

/**
 * The nineteen techniques' icon tiles, same identification method. The grid's own structure was
 * the cross-check: each element line occupies one column with its single/Gi-/Ra- tiers down the
 * rows (Zonde col 0, Foie col 1, Barta col 2), the support line col 3 (Reverser/Resta/Anti),
 * Grants/Megid/Ryuker col 4, and the Shifta/Deband/Jellen/Zalure chevron pairs in rows 3-4.
 * Ready for the technique system; nothing reads these yet besides future menu/palette icons.
 */
/**
 * Which tile of the UI sheet's hex grid carries each technique's glyph, on the measured 50x43
 * grid (see HudSprites.hexTile). All nineteen were pixel-matched against the wiki's own glyph
 * images, each with an order-of-magnitude score margin over the runner-up: columns are the
 * families, rows the tiers, Deband/Shifta the buff row and Zalure/Jellen the debuff row.
 */
enum class TechniqueIcon(val iconCol: Int, val iconRow: Int) {
    ZONDE(0, 0), GIZONDE(0, 1), RAZONDE(0, 2),
    FOIE(1, 0), GIFOIE(1, 1), RAFOIE(1, 2),
    BARTA(2, 0), GIBARTA(2, 1), RABARTA(2, 2),
    RESTA(3, 0), REVERSER(3, 1), ANTI(3, 2),
    GRANTS(4, 0), MEGID(4, 1), RYUKER(4, 2),
    DEBAND(0, 3), SHIFTA(1, 3),
    ZALURE(0, 4), JELLEN(1, 4),
}

/**
 * Which blast a character's starting Mag carries. In the real game the blast comes from the
 * Mag's first evolution around level 10, shaped by the classes that fed it; there's no Mag
 * feeding/evolution system yet, so the profession's typical first blast stands in: the melee
 * classes' Mags roll toward Farlla, Rangers' toward Estlla, Forces' toward Golla.
 */
fun startingPhotonBlast(characterClass: CharacterClass): PhotonBlast =
    when (professionOf(characterClass)) {
        Profession.HUNTER -> PhotonBlast.FARLLA
        Profession.RANGER -> PhotonBlast.ESTLLA
        Profession.FORCE -> PhotonBlast.GOLLA
    }
